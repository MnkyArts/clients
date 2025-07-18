package com.bitwarden.jetbrains.actions

import com.bitwarden.jetbrains.services.BitwardenAuthService
import com.bitwarden.jetbrains.services.BitwardenSyncService
import com.bitwarden.jetbrains.services.BitwardenVaultService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages

class SyncAction : AnAction() {
    private val logger = thisLogger()
    
    override fun actionPerformed(e: AnActionEvent) {
        val authService = ApplicationManager.getApplication().service<BitwardenAuthService>()
        val vaultService = ApplicationManager.getApplication().service<BitwardenVaultService>()
        val syncService = ApplicationManager.getApplication().service<BitwardenSyncService>()
        
        if (!authService.isAuthenticated()) {
            Messages.showWarningDialog(
                "Please log in to Bitwarden first.",
                "Not Authenticated"
            )
            return
        }
        
        if (syncService.isSyncing()) {
            Messages.showInfoMessage(
                "Vault synchronization is already in progress.",
                "Sync In Progress"
            )
            return
        }
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(e.project, "Syncing Bitwarden Vault...") {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Synchronizing vault..."
                indicator.isIndeterminate = true
                
                val success = syncService.syncVaultAsync(authService, vaultService).get()
                
                ApplicationManager.getApplication().invokeLater {
                    if (success) {
                        val itemCount = vaultService.getVaultItems().size
                        Messages.showInfoMessage(
                            "Vault synchronized successfully!\n\nItems in vault: $itemCount\nLast sync: ${syncService.getFormattedLastSyncTime()}",
                            "Sync Successful"
                        )
                        logger.info("Manual vault sync completed successfully")
                    } else {
                        Messages.showErrorDialog(
                            "Failed to synchronize vault. Please check your internet connection and try again.",
                            "Sync Failed"
                        )
                        logger.warn("Manual vault sync failed")
                    }
                }
            }
            
            override fun onCancel() {
                logger.info("Vault sync cancelled by user")
            }
        })
    }
    
    override fun update(e: AnActionEvent) {
        val authService = ApplicationManager.getApplication().service<BitwardenAuthService>()
        val syncService = ApplicationManager.getApplication().service<BitwardenSyncService>()
        
        val isAuthenticated = authService.isAuthenticated()
        val isSyncing = syncService.isSyncing()
        
        e.presentation.isEnabled = isAuthenticated && !isSyncing
        
        when {
            !isAuthenticated -> {
                e.presentation.text = "Sync Vault (Login Required)"
                e.presentation.description = "Log in to Bitwarden to sync your vault"
            }
            isSyncing -> {
                e.presentation.text = "Syncing..."
                e.presentation.description = "Vault synchronization in progress"
            }
            else -> {
                val lastSync = syncService.getFormattedLastSyncTime()
                e.presentation.text = "Sync Vault"
                e.presentation.description = "Last sync: $lastSync"
            }
        }
    }
}