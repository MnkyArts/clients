package com.bitwarden.jetbrains

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BitwardenPluginTest {
    
    @Test
    fun `test plugin structure is valid`() {
        // Basic structural test to ensure classes can be instantiated
        assertDoesNotThrow {
            // Test that core services can be created
            val authServiceClass = Class.forName("com.bitwarden.jetbrains.services.BitwardenAuthService")
            val vaultServiceClass = Class.forName("com.bitwarden.jetbrains.services.BitwardenVaultService")
            val syncServiceClass = Class.forName("com.bitwarden.jetbrains.services.BitwardenSyncService")
            val autofillServiceClass = Class.forName("com.bitwarden.jetbrains.services.BitwardenAutofillService")
            
            assertNotNull(authServiceClass)
            assertNotNull(vaultServiceClass) 
            assertNotNull(syncServiceClass)
            assertNotNull(autofillServiceClass)
        }
    }
    
    @Test
    fun `test vault item types are defined`() {
        val vaultItemType = com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.LOGIN
        assertEquals("LOGIN", vaultItemType.name)
        
        val allTypes = com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.values()
        assertEquals(4, allTypes.size)
        assertTrue(allTypes.contains(com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.LOGIN))
        assertTrue(allTypes.contains(com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.SECURE_NOTE))
        assertTrue(allTypes.contains(com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.CARD))
        assertTrue(allTypes.contains(com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.IDENTITY))
    }
}