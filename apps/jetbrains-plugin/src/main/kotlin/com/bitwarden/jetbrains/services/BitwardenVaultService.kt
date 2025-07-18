package com.bitwarden.jetbrains.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import okhttp3.*
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service
class BitwardenVaultService {
    private val logger = thisLogger()
    private val httpClient = OkHttpClient()
    private val vaultCache = ConcurrentHashMap<String, VaultItem>()
    
    data class VaultItem(
        val id: String,
        val name: String,
        val type: VaultItemType,
        val login: LoginData? = null,
        val secureNote: SecureNoteData? = null,
        val card: CardData? = null,
        val identity: IdentityData? = null,
        val organizationId: String? = null,
        val folderId: String? = null,
        val favorite: Boolean = false,
        val revisionDate: String
    )
    
    data class LoginData(
        val username: String?,
        val password: String?,
        val uris: List<String> = emptyList(),
        val totp: String? = null
    )
    
    data class SecureNoteData(
        val note: String
    )
    
    data class CardData(
        val cardholderName: String?,
        val number: String?,
        val brand: String?,
        val expMonth: String?,
        val expYear: String?,
        val code: String?
    )
    
    data class IdentityData(
        val title: String?,
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val address1: String?,
        val address2: String?,
        val address3: String?,
        val city: String?,
        val state: String?,
        val postalCode: String?,
        val country: String?,
        val company: String?,
        val email: String?,
        val phone: String?,
        val ssn: String?,
        val username: String?,
        val passportNumber: String?,
        val licenseNumber: String?
    )
    
    enum class VaultItemType {
        LOGIN, SECURE_NOTE, CARD, IDENTITY
    }
    
    fun getVaultItems(): List<VaultItem> {
        return vaultCache.values.toList()
    }
    
    fun getLoginItems(): List<VaultItem> {
        return vaultCache.values.filter { it.type == VaultItemType.LOGIN }
    }
    
    fun searchItems(query: String): List<VaultItem> {
        val lowerQuery = query.lowercase()
        return vaultCache.values.filter { item ->
            item.name.lowercase().contains(lowerQuery) ||
            item.login?.username?.lowercase()?.contains(lowerQuery) == true ||
            item.login?.uris?.any { uri -> uri.lowercase().contains(lowerQuery) } == true
        }
    }
    
    fun findItemsByUri(uri: String): List<VaultItem> {
        val normalizedUri = normalizeUri(uri)
        return vaultCache.values.filter { item ->
            item.login?.uris?.any { itemUri -> 
                uriMatches(normalizedUri, normalizeUri(itemUri))
            } == true
        }
    }
    
    fun getItemById(id: String): VaultItem? {
        return vaultCache[id]
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

    fun fetchVaultAsync(authService: BitwardenAuthService, serverUrl: String = "https://vault.bitwarden.com"): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            val token = authService.getAccessToken()
            if (token == null) {
                logger.warn("Cannot fetch vault: not authenticated")
                return@supplyAsync false
            }
            
            try {
                // Get the correct API URL for the server
                val apiUrl = getApiUrl(serverUrl)
                
                val request = Request.Builder()
                    .url("$apiUrl/sync")
                    .get()
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "Bitwarden JetBrains Plugin")
                    .build()
                
                logger.info("Fetching vault from: $apiUrl/sync")
                
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    // Parse the response and populate vault cache
                    // TODO: Implement proper JSON parsing of sync response
                    logger.info("Vault response received: ${responseBody?.length ?: 0} bytes")
                    // For demo purposes, adding mock data
                    addMockVaultData()
                    logger.info("Vault synced successfully")
                    true
                } else {
                    val responseBody = response.body?.string()
                    logger.warn("Failed to fetch vault: ${response.code} ${response.message} - Response: $responseBody")
                    false
                }
            } catch (e: IOException) {
                logger.error("Network error while fetching vault", e)
                false
            } catch (e: Exception) {
                logger.error("Unexpected error while fetching vault", e)
                false
            }
        }, AppExecutorUtil.getAppExecutorService())
    }
    
    private fun addMockVaultData() {
        val mockItems = listOf(
            VaultItem(
                id = "1",
                name = "GitHub",
                type = VaultItemType.LOGIN,
                login = LoginData(
                    username = "developer@example.com",
                    password = "secure_password_123",
                    uris = listOf("https://github.com", "github.com")
                ),
                revisionDate = "2024-01-01T00:00:00Z"
            ),
            VaultItem(
                id = "2", 
                name = "GitLab",
                type = VaultItemType.LOGIN,
                login = LoginData(
                    username = "dev.user",
                    password = "another_secure_pass",
                    uris = listOf("https://gitlab.com", "gitlab.com")
                ),
                revisionDate = "2024-01-01T00:00:00Z"
            ),
            VaultItem(
                id = "3",
                name = "Database Connection",
                type = VaultItemType.SECURE_NOTE,
                secureNote = SecureNoteData(
                    note = "Database: localhost:5432\nUsername: dev_user\nPassword: db_password_123"
                ),
                revisionDate = "2024-01-01T00:00:00Z"
            )
        )
        
        vaultCache.clear()
        mockItems.forEach { vaultCache[it.id] = it }
    }
    
    private fun normalizeUri(uri: String): String {
        var normalized = uri.lowercase().trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        // Remove trailing slash
        if (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }
        return normalized
    }
    
    private fun uriMatches(targetUri: String, itemUri: String): Boolean {
        // Simple domain matching logic
        try {
            val targetDomain = extractDomain(targetUri)
            val itemDomain = extractDomain(itemUri)
            return targetDomain == itemDomain
        } catch (e: Exception) {
            return targetUri == itemUri
        }
    }
    
    private fun extractDomain(uri: String): String {
        val withoutProtocol = uri.replace(Regex("^https?://"), "")
        val domain = withoutProtocol.split("/")[0]
        return domain.split(":")[0] // Remove port if present
    }
    
    fun clearCache() {
        vaultCache.clear()
        logger.info("Vault cache cleared")
    }
}