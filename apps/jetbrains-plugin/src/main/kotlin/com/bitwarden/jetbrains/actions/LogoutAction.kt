package com.bitwarden.jetbrains.actions

import com.bitwarden.jetbrains.services.BitwardenAuthService
import com.bitwarden.jetbrains.services.BitwardenSyncService
import com.bitwarden.jetbrains.services.BitwardenVaultService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages

class LogoutAction : AnAction() {
    private val logger = thisLogger()
    
    override fun actionPerformed(e: AnActionEvent) {
        val authService = ApplicationManager.getApplication().service<BitwardenAuthService>()
        val vaultService = ApplicationManager.getApplication().service<BitwardenVaultService>()
        val syncService = ApplicationManager.getApplication().service<BitwardenSyncService>()
        
        if (!authService.isAuthenticated()) {
            Messages.showInfoMessage(
                "You are not currently logged in to Bitwarden.",
                "Not Logged In"
            )
            return
        }
        
        val result = Messages.showYesNoDialog(
            "Are you sure you want to log out of Bitwarden?\n\nThis will clear your vault cache and stop automatic synchronization.",
            "Confirm Logout",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            // Stop auto-sync
            syncService.stopAutoSync()
            
            // Clear vault cache
            vaultService.clearCache()
            
            // Logout
            authService.logout()
            
            Messages.showInfoMessage(
                "Successfully logged out of Bitwarden.",
                "Logout Successful"
            )
            
            logger.info("User logged out successfully")
        }
    }
    
    override fun update(e: AnActionEvent) {
        val authService = ApplicationManager.getApplication().service<BitwardenAuthService>()
        e.presentation.isEnabled = authService.isAuthenticated()
        e.presentation.text = if (authService.isAuthenticated()) "Logout" else "Not Logged In"
    }
}