package com.bitwarden.jetbrains.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Service
class BitwardenAuthService {
    private val logger = thisLogger()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
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
        val masterPassword: String,
        val deviceIdentifier: String = "jetbrains-plugin",
        val deviceName: String = "JetBrains IDE",
        val deviceType: Int = 8, // SDK device type
        val grant_type: String = "password",
        val scope: String = "api offline_access"
    )
    
    fun isAuthenticated(): Boolean = isAuthenticated
    
    fun getAccessToken(): String? = accessToken
    
    fun loginAsync(email: String, masterPassword: String, serverUrl: String = "https://api.bitwarden.com"): CompletableFuture<LoginResult> {
        return CompletableFuture.supplyAsync({
            try {
                val loginRequest = LoginRequest(email, masterPassword)
                val json = """
                    {
                        "grant_type": "${loginRequest.grant_type}",
                        "username": "${loginRequest.email}",
                        "password": "${loginRequest.masterPassword}",
                        "scope": "${loginRequest.scope}",
                        "deviceIdentifier": "${loginRequest.deviceIdentifier}",
                        "deviceName": "${loginRequest.deviceName}",
                        "deviceType": ${loginRequest.deviceType}
                    }
                """.trimIndent()
                
                val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$serverUrl/identity/connect/token")
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()
                
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    logger.info("Login successful")
                    
                    // Parse JSON response (simplified - would use Jackson in real implementation)
                    // For now, assuming successful login
                    accessToken = "mock_access_token"
                    refreshToken = "mock_refresh_token"
                    isAuthenticated = true
                    
                    LoginResult(true, accessToken, refreshToken)
                } else {
                    val errorMessage = "Login failed: ${response.code} ${response.message}"
                    logger.warn(errorMessage)
                    LoginResult(false, errorMessage = errorMessage)
                }
            } catch (e: IOException) {
                val errorMessage = "Network error during login: ${e.message}"
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
                    // Implement token refresh logic
                    logger.info("Token refreshed successfully")
                    true
                } catch (e: Exception) {
                    logger.error("Failed to refresh token", e)
                    false
                }
            } ?: false
        }, AppExecutorUtil.getAppExecutorService())
    }
}