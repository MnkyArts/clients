package com.bitwarden.jetbrains.actions

import com.bitwarden.jetbrains.services.BitwardenAutofillService
import com.bitwarden.jetbrains.services.BitwardenVaultService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import javax.swing.Icon

class QuickAccessAction : AnAction() {
    private val logger = thisLogger()
    
    override fun actionPerformed(e: AnActionEvent) {
        val vaultService = ApplicationManager.getApplication().service<BitwardenVaultService>()
        val autofillService = ApplicationManager.getApplication().service<BitwardenAutofillService>()
        
        val vaultItems = vaultService.getVaultItems()
        
        if (vaultItems.isEmpty()) {
            // Show empty vault message or login prompt
            val emptyOptions = listOf("Login to Bitwarden", "Sync Vault")
            val emptyStep = object : BaseListPopupStep<String>("Bitwarden Vault", emptyOptions) {
                override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice) {
                        when (selectedValue) {
                            "Login to Bitwarden" -> {
                                val loginAction = LoginAction()
                                loginAction.actionPerformed(e)
                            }
                            "Sync Vault" -> {
                                val syncAction = SyncAction()
                                syncAction.actionPerformed(e)
                            }
                        }
                    }
                    return null
                }
            }
            
            val emptyPopup = JBPopupFactory.getInstance().createListPopup(emptyStep)
            emptyPopup.showInFocusCenter()
            return
        }
        
        // Create quick access popup with vault items
        val displayItems = vaultItems.map { item ->
            QuickAccessItem(
                id = item.id,
                name = item.name,
                type = item.type.name,
                subtitle = when (item.type) {
                    BitwardenVaultService.VaultItemType.LOGIN -> item.login?.username ?: "No username"
                    BitwardenVaultService.VaultItemType.SECURE_NOTE -> "Secure Note"
                    BitwardenVaultService.VaultItemType.CARD -> "Payment Card"
                    BitwardenVaultService.VaultItemType.IDENTITY -> "Identity"
                }
            )
        }
        
        val quickAccessStep = object : BaseListPopupStep<QuickAccessItem>("Bitwarden Quick Access", displayItems) {
            override fun getTextFor(value: QuickAccessItem): String {
                return "${value.name} - ${value.subtitle}"
            }
            
            override fun getIconFor(value: QuickAccessItem): Icon? {
                // Would use appropriate icons based on item type
                return null
            }
            
            override fun onChosen(selectedValue: QuickAccessItem, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    handleQuickAccessSelection(selectedValue, vaultService)
                }
                return null
            }
            
            override fun isSpeedSearchEnabled(): Boolean = true
            
            override fun getSpeedSearchFilter(): com.intellij.util.Processor<String> {
                return com.intellij.util.Processor { pattern ->
                    values.any { item ->
                        item.name.contains(pattern, ignoreCase = true) ||
                        item.subtitle.contains(pattern, ignoreCase = true)
                    }
                }
            }
        }
        
        val popup = JBPopupFactory.getInstance().createListPopup(quickAccessStep)
        popup.showInFocusCenter()
    }
    
    private fun handleQuickAccessSelection(item: QuickAccessItem, vaultService: BitwardenVaultService) {
        val vaultItem = vaultService.getItemById(item.id) ?: return
        
        val actions = mutableListOf<String>()
        
        when (vaultItem.type) {
            BitwardenVaultService.VaultItemType.LOGIN -> {
                vaultItem.login?.let { login ->
                    if (login.username != null) actions.add("Copy Username")
                    if (login.password != null) actions.add("Copy Password")
                    if (login.username != null && login.password != null) {
                        actions.add("Autofill Username")
                        actions.add("Autofill Password")
                        actions.add("Autofill Both")
                    }
                    if (login.uris.isNotEmpty()) actions.add("Copy URL")
                }
            }
            BitwardenVaultService.VaultItemType.SECURE_NOTE -> {
                actions.add("Copy Note")
                actions.add("View Note")
            }
            BitwardenVaultService.VaultItemType.CARD -> {
                actions.add("Copy Card Number")
                actions.add("Copy CVV")
                actions.add("View Details")
            }
            BitwardenVaultService.VaultItemType.IDENTITY -> {
                actions.add("Copy Name")
                actions.add("Copy Email")
                actions.add("View Details")
            }
        }
        
        if (actions.isEmpty()) {
            logger.warn("No actions available for item: ${item.name}")
            return
        }
        
        val actionStep = object : BaseListPopupStep<String>("Choose Action", actions) {
            override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    executeQuickAccessAction(vaultItem, selectedValue)
                }
                return null
            }
        }
        
        val actionPopup = JBPopupFactory.getInstance().createListPopup(actionStep)
        actionPopup.showInFocusCenter()
    }
    
    private fun executeQuickAccessAction(item: BitwardenVaultService.VaultItem, action: String) {
        val autofillService = ApplicationManager.getApplication().service<BitwardenAutofillService>()
        
        // Implementation would depend on the specific action
        // For now, just log the action
        logger.info("Executing action '$action' for item '${item.name}'")
        
        when (action) {
            "Copy Username" -> item.login?.username?.let { /* copy to clipboard */ }
            "Copy Password" -> item.login?.password?.let { /* copy to clipboard */ }
            "Autofill Username" -> item.login?.username?.let { /* simulate typing */ }
            "Autofill Password" -> item.login?.password?.let { /* simulate typing */ }
            "Autofill Both" -> {
                // Implement autofill logic
            }
            // Add other actions as needed
        }
    }
    
    data class QuickAccessItem(
        val id: String,
        val name: String,
        val type: String,
        val subtitle: String
    )
    
    override fun update(e: AnActionEvent) {
        // Always enabled - will show appropriate content based on state
        e.presentation.isEnabled = true
    }
}