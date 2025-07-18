package com.bitwarden.jetbrains.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
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
    
    data class PreloginResponse(
        val kdf: Int = 0, // 0 = PBKDF2, 1 = Argon2id
        val kdfIterations: Int = 100000, // Default PBKDF2 iterations
        val kdfMemory: Int? = null, // For Argon2
        val kdfParallelism: Int? = null // For Argon2
    )
    
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
    
    private suspend fun getPreloginInfo(email: String, serverUrl: String): PreloginResponse {
        val apiUrl = getApiUrl(serverUrl)
        val request = Request.Builder()
            .url("$apiUrl/accounts/prelogin")
            .post("{\"email\":\"${email.trim().lowercase()}\"}".toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Content-Type", "application/json")
            .header("User-Agent", "Bitwarden JetBrains Plugin")
            .build()
        
        return try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            
            if (response.isSuccessful) {
                val jsonNode = objectMapper.readTree(responseBody)
                PreloginResponse(
                    kdf = jsonNode.get("kdf")?.asInt() ?: 0,
                    kdfIterations = jsonNode.get("kdfIterations")?.asInt() ?: 100000,
                    kdfMemory = jsonNode.get("kdfMemory")?.asInt(),
                    kdfParallelism = jsonNode.get("kdfParallelism")?.asInt()
                )
            } else {
                logger.warn("Prelogin failed: ${response.code} ${response.message}, using defaults")
                PreloginResponse() // Use defaults if prelogin fails
            }
        } catch (e: Exception) {
            logger.warn("Prelogin request failed, using defaults", e)
            PreloginResponse() // Use defaults on any error
        }
    }
    
    private fun deriveMasterKey(password: String, email: String, preloginInfo: PreloginResponse): ByteArray {
        val emailBytes = email.trim().lowercase().toByteArray()
        
        return when (preloginInfo.kdf) {
            0 -> { // PBKDF2
                val spec = PBEKeySpec(password.toCharArray(), emailBytes, preloginInfo.kdfIterations, 256)
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                factory.generateSecret(spec).encoded
            }
            1 -> { // Argon2id - not commonly supported in JVM, fallback to PBKDF2
                logger.warn("Argon2 not supported, falling back to PBKDF2")
                val spec = PBEKeySpec(password.toCharArray(), emailBytes, 100000, 256)
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                factory.generateSecret(spec).encoded
            }
            else -> {
                logger.warn("Unknown KDF type ${preloginInfo.kdf}, using PBKDF2")
                val spec = PBEKeySpec(password.toCharArray(), emailBytes, 100000, 256)
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                factory.generateSecret(spec).encoded
            }
        }
    }
    
    private fun hashMasterKeyForServer(masterKey: ByteArray, password: String): String {
        // Hash the master key with password using PBKDF2 with 1 iteration for server
        val spec = PBEKeySpec(password.toCharArray(), masterKey, 1, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(hash)
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
    
    private fun getApiUrl(serverUrl: String): String {
        // Handle different server URL formats to match Bitwarden client behavior
        val baseUrl = when {
            serverUrl.endsWith("/") -> serverUrl.removeSuffix("/")
            else -> serverUrl
        }
        
        return when {
            // For bitwarden.com, use api.bitwarden.com 
            baseUrl.contains("vault.bitwarden.com") -> "https://api.bitwarden.com"
            baseUrl.contains("bitwarden.com") && !baseUrl.contains("api.") -> 
                baseUrl.replace("vault.bitwarden.com", "api.bitwarden.com")
                    .replace("identity.bitwarden.com", "api.bitwarden.com")
            // For custom servers, add /api if not already there
            baseUrl.contains("/api") -> baseUrl
            else -> "$baseUrl/api"
        }
    }
    
    private fun getIdentityUrl(serverUrl: String): String {
        // Handle different server URL formats to match Bitwarden client behavior
        val baseUrl = when {
            serverUrl.endsWith("/") -> serverUrl.removeSuffix("/")
            else -> serverUrl
        }
        
        return when {
            // For bitwarden.com, use identity.bitwarden.com 
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
                logger.info("Starting login process for email: $email to server: $serverUrl")
                
                // Step 1: Get prelogin information (KDF config)
                val preloginInfo = runBlocking { getPreloginInfo(email, serverUrl) }
                logger.info("Got prelogin info: KDF=${preloginInfo.kdf}, iterations=${preloginInfo.kdfIterations}")
                
                // Step 2: Derive master key using the KDF configuration
                val masterKey = deriveMasterKey(masterPassword, email, preloginInfo)
                logger.info("Master key derived successfully")
                
                // Step 3: Hash the master key for server authentication (1 iteration)
                val masterPasswordHash = hashMasterKeyForServer(masterKey, masterPassword)
                logger.info("Master password hash computed for server")
                
                // Step 4: Create login request
                val loginRequest = LoginRequest(email, masterPasswordHash)
                
                // Step 5: Build form data as required by Bitwarden API
                val formData = buildFormData(loginRequest)
                
                // Step 6: Get the correct identity URL for the server
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
                
                logger.info("Login response: ${response.code} ${response.message}")
                if (responseBody.isNotEmpty() && responseBody.length < 500) {
                    logger.info("Response body: $responseBody")
                } else if (responseBody.isNotEmpty()) {
                    logger.info("Response body (truncated): ${responseBody.take(500)}...")
                }
                
                if (response.isSuccessful) {
                    parseLoginResponse(responseBody)
                } else {
                    val errorMessage = "Login failed: ${response.code} ${response.message}"
                    logger.warn(errorMessage)
                    
                    // Try to parse error from response for better error messages
                    if (responseBody.isNotEmpty()) {
                        try {
                            val errorJson = objectMapper.readTree(responseBody)
                            val error = errorJson.get("error")?.asText()
                            val errorDescription = errorJson.get("error_description")?.asText()
                            val validationErrors = errorJson.get("ValidationErrors")
                            
                            when {
                                validationErrors != null && validationErrors.isObject -> {
                                    // Handle validation errors
                                    val firstError = validationErrors.fields().asSequence().firstOrNull()
                                    if (firstError != null) {
                                        val field = firstError.key
                                        val message = firstError.value.firstOrNull()?.asText() ?: "Invalid value"
                                        return@supplyAsync LoginResult(false, errorMessage = "$field: $message")
                                    }
                                }
                                error != null -> {
                                    val detailedError = if (errorDescription != null) "$error: $errorDescription" else error
                                    return@supplyAsync LoginResult(false, errorMessage = detailedError)
                                }
                                else -> {
                                    // Try to extract any error message from response
                                    val message = errorJson.get("message")?.asText()
                                    if (message != null) {
                                        return@supplyAsync LoginResult(false, errorMessage = message)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn("Failed to parse error response as JSON", e)
                            // If it's not JSON, show the response body if it's reasonable length
                            if (responseBody.length < 200) {
                                return@supplyAsync LoginResult(false, errorMessage = "Server error: $responseBody")
                            }
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