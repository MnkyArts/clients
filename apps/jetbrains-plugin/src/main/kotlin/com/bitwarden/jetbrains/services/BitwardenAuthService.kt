package com.bitwarden.jetbrains.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.*
import javax.crypto.spec.PBEKeySpec
import javax.crypto.SecretKeyFactory

@Service
class BitwardenAuthService {
    private val logger = thisLogger()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val objectMapper = ObjectMapper()
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var isAuthenticated = false
    
    data class LoginResult(
        val success: Boolean,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val errorMessage: String? = null
    )
    
    data class LoginRequest(
        val email: String,
        val masterPasswordHash: String,
        val deviceIdentifier: String = "jetbrains-plugin-${UUID.randomUUID()}",
        val deviceName: String = "JetBrains IDE",
        val deviceType: Int = 8, // SDK device type
        val grant_type: String = "password",
        val scope: String = "api offline_access",
        val client_id: String = "connector"
    )
    
    fun isAuthenticated(): Boolean = isAuthenticated
    
    fun getAccessToken(): String? = accessToken
    
    private fun hashPassword(password: String, email: String): String {
        try {
            // Use PBKDF2 with SHA-256 for proper Bitwarden password hashing
            val spec = PBEKeySpec(password.toCharArray(), email.lowercase().toByteArray(), 100000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            return Base64.getEncoder().encodeToString(hash)
        } catch (e: Exception) {
            logger.warn("Failed to use PBKDF2, falling back to SHA-256", e)
            // Fallback to simple SHA-256 if PBKDF2 is not available
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest("$password${email.lowercase()}".toByteArray())
            return Base64.getEncoder().encodeToString(hashBytes)
        }
    }
    
    private fun encodeEmailHeader(email: String): String {
        // Convert email to base64url encoding for Auth-Email header (matching Bitwarden spec)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(email.toByteArray())
    }
    
    private fun buildFormData(loginRequest: LoginRequest): String {
        val params = mapOf(
            "grant_type" to loginRequest.grant_type,
            "username" to loginRequest.email,
            "password" to loginRequest.masterPasswordHash,
            "scope" to loginRequest.scope,
            "client_id" to loginRequest.client_id,
            "deviceType" to loginRequest.deviceType.toString(),
            "deviceIdentifier" to loginRequest.deviceIdentifier,
            "deviceName" to loginRequest.deviceName
        )
        
        return params.map { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }.joinToString("&")
    }
    
    private fun getIdentityUrl(serverUrl: String): String {
        // Handle different server URL formats to match Bitwarden client behavior
        val baseUrl = when {
            serverUrl.endsWith("/") -> serverUrl.removeSuffix("/")
            else -> serverUrl
        }
        
        return when {
            // For bitwarden.com, use api.bitwarden.com 
            baseUrl.contains("vault.bitwarden.com") -> "https://identity.bitwarden.com"
            baseUrl.contains("bitwarden.com") && !baseUrl.contains("identity.") -> 
                baseUrl.replace("vault.bitwarden.com", "identity.bitwarden.com")
                    .replace("api.bitwarden.com", "identity.bitwarden.com")
            // For custom servers, add /identity if not already there
            baseUrl.contains("/identity") -> baseUrl
            else -> "$baseUrl/identity"
        }
    }
    
    private fun parseLoginResponse(responseBody: String): LoginResult {
        try {
            val jsonNode = objectMapper.readTree(responseBody)
            
            val accessToken = jsonNode.get("access_token")?.asText()
            val refreshToken = jsonNode.get("refresh_token")?.asText()
            val tokenType = jsonNode.get("token_type")?.asText()
            
            if (accessToken != null && refreshToken != null) {
                this.accessToken = accessToken
                this.refreshToken = refreshToken
                this.isAuthenticated = true
                logger.info("Login successful, token type: $tokenType")
                return LoginResult(true, accessToken, refreshToken)
            } else {
                val error = jsonNode.get("error")?.asText() ?: "Unknown error"
                val errorDescription = jsonNode.get("error_description")?.asText() ?: ""
                val errorMessage = if (errorDescription.isNotEmpty()) "$error: $errorDescription" else error
                logger.warn("Login failed: $errorMessage")
                return LoginResult(false, errorMessage = errorMessage)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse login response", e)
            return LoginResult(false, errorMessage = "Failed to parse server response: ${e.message}")
        }
    }

    fun loginAsync(email: String, masterPassword: String, serverUrl: String = "https://vault.bitwarden.com"): CompletableFuture<LoginResult> {
        return CompletableFuture.supplyAsync({
            try {
                // Hash the password properly using PBKDF2
                val masterPasswordHash = hashPassword(masterPassword, email)
                val loginRequest = LoginRequest(email, masterPasswordHash)
                
                // Build form data as required by Bitwarden API
                val formData = buildFormData(loginRequest)
                
                // Get the correct identity URL for the server
                val identityUrl = getIdentityUrl(serverUrl)
                
                val requestBody = formData.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$identityUrl/connect/token")
                    .post(requestBody)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Auth-Email", encodeEmailHeader(email))
                    .header("User-Agent", "Bitwarden JetBrains Plugin")
                    .build()
                
                logger.info("Attempting login to: $identityUrl/connect/token")
                
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    parseLoginResponse(responseBody)
                } else {
                    val errorMessage = "Login failed: ${response.code} ${response.message}"
                    logger.warn("$errorMessage - Response: $responseBody")
                    
                    // Try to parse error from response
                    if (responseBody.isNotEmpty()) {
                        try {
                            val errorJson = objectMapper.readTree(responseBody)
                            val error = errorJson.get("error")?.asText()
                            val errorDescription = errorJson.get("error_description")?.asText()
                            if (error != null) {
                                val detailedError = if (errorDescription != null) "$error: $errorDescription" else error
                                return@supplyAsync LoginResult(false, errorMessage = detailedError)
                            }
                        } catch (e: Exception) {
                            // Fall through to generic error
                        }
                    }
                    
                    LoginResult(false, errorMessage = errorMessage)
                }
            } catch (e: IOException) {
                val errorMessage = "Network error during login: ${e.message}"
                logger.error(errorMessage, e)
                LoginResult(false, errorMessage = errorMessage)
            } catch (e: Exception) {
                val errorMessage = "Unexpected error during login: ${e.message}"
                logger.error(errorMessage, e)
                LoginResult(false, errorMessage = errorMessage)
            }
        }, AppExecutorUtil.getAppExecutorService())
    }
    
    fun logout() {
        accessToken = null
        refreshToken = null
        isAuthenticated = false
        logger.info("User logged out")
    }
    
    fun refreshTokenAsync(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            refreshToken?.let { token ->
                try {
                    val formData = mapOf(
                        "grant_type" to "refresh_token",
                        "refresh_token" to token,
                        "client_id" to "connector"
                    ).map { (key, value) ->
                        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
                    }.joinToString("&")
                    
                    // Use same identity URL logic (would need server URL stored)
                    val identityUrl = "https://identity.bitwarden.com" // Default for now
                    
                    val requestBody = formData.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url("$identityUrl/connect/token")
                        .post(requestBody)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", "Bitwarden JetBrains Plugin")
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""
                    
                    if (response.isSuccessful) {
                        val result = parseLoginResponse(responseBody)
                        logger.info("Token refreshed successfully")
                        result.success
                    } else {
                        logger.error("Failed to refresh token: ${response.code} ${response.message}")
                        false
                    }
                } catch (e: Exception) {
                    logger.error("Failed to refresh token", e)
                    false
                }
            } ?: false
        }, AppExecutorUtil.getAppExecutorService())
    }
}