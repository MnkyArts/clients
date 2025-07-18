package com.bitwarden.jetbrains.services

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger

@Service
class BitwardenPasswordSafeProvider {
    private val logger = thisLogger()
    
    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(generateServiceName("Bitwarden", key))
    }
    
    fun storeCredentials(key: String, username: String, password: String) {
        try {
            val attributes = createCredentialAttributes(key)
            val credentials = Credentials(username, password)
            PasswordSafe.instance.set(attributes, credentials)
            logger.debug("Credentials stored for key: $key")
        } catch (e: Exception) {
            logger.error("Failed to store credentials for key: $key", e)
        }
    }
    
    fun getCredentials(key: String): Pair<String?, String?> {
        return try {
            val attributes = createCredentialAttributes(key)
            val credentials = PasswordSafe.instance.get(attributes)
            Pair(credentials?.userName, credentials?.getPasswordAsString())
        } catch (e: Exception) {
            logger.error("Failed to retrieve credentials for key: $key", e)
            Pair(null, null)
        }
    }
    
    fun removeCredentials(key: String) {
        try {
            val attributes = createCredentialAttributes(key)
            PasswordSafe.instance.set(attributes, null)
            logger.debug("Credentials removed for key: $key")
        } catch (e: Exception) {
            logger.error("Failed to remove credentials for key: $key", e)
        }
    }
    
    fun storeAccessToken(token: String) {
        storeCredentials("access_token", "bitwarden", token)
    }
    
    fun getAccessToken(): String? {
        return getCredentials("access_token").second
    }
    
    fun removeAccessToken() {
        removeCredentials("access_token")
    }
    
    fun storeRefreshToken(token: String) {
        storeCredentials("refresh_token", "bitwarden", token)
    }
    
    fun getRefreshToken(): String? {
        return getCredentials("refresh_token").second
    }
    
    fun removeRefreshToken() {
        removeCredentials("refresh_token")
    }
}