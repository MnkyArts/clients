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
import javax.swing.JOptionPane
import javax.swing.JPasswordField
import javax.swing.JTextField

class LoginAction : AnAction() {
    private val logger = thisLogger()
    
    override fun actionPerformed(e: AnActionEvent) {
        val authService = ApplicationManager.getApplication().service<BitwardenAuthService>()
        val vaultService = ApplicationManager.getApplication().service<BitwardenVaultService>()
        val syncService = ApplicationManager.getApplication().service<BitwardenSyncService>()
        
        if (authService.isAuthenticated()) {
            Messages.showInfoMessage(
                "You are already logged in to Bitwarden.",
                "Already Logged In"
            )
            return
        }
        
        val loginDialog = createLoginDialog()
        if (loginDialog.showConfirmDialog() == JOptionPane.OK_OPTION) {
            val email = loginDialog.email
            val password = loginDialog.password
            val serverUrl = loginDialog.serverUrl.ifEmpty { "https://api.bitwarden.com" }
            
            if (email.isEmpty() || password.isEmpty()) {
                Messages.showErrorDialog(
                    "Please enter both email and password.",
                    "Login Error"
                )
                return
            }
            
            // Perform login in background task
            ProgressManager.getInstance().run(object : Task.Backgroundable(e.project, "Logging in to Bitwarden...") {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Authenticating..."
                    indicator.isIndeterminate = true
                    
                    val loginResult = authService.loginAsync(email, password, serverUrl).get()
                    
                    ApplicationManager.getApplication().invokeLater {
                        if (loginResult.success) {
                            Messages.showInfoMessage(
                                "Successfully logged in to Bitwarden!",
                                "Login Successful"
                            )
                            
                            // Start auto-sync after successful login
                            syncService.startAutoSync(authService, vaultService)
                            
                            // Perform initial sync
                            ProgressManager.getInstance().run(object : Task.Backgroundable(e.project, "Syncing vault...") {
                                override fun run(indicator: ProgressIndicator) {
                                    indicator.text = "Synchronizing vault..."
                                    syncService.syncVaultAsync(authService, vaultService)
                                }
                            })
                        } else {
                            Messages.showErrorDialog(
                                loginResult.errorMessage ?: "Login failed for unknown reason.",
                                "Login Failed"
                            )
                        }
                    }
                }
            })
        }
    }
    
    override fun update(e: AnActionEvent) {
        val authService = ApplicationManager.getApplication().service<BitwardenAuthService>()
        e.presentation.isEnabled = !authService.isAuthenticated()
        e.presentation.text = if (authService.isAuthenticated()) "Already Logged In" else "Login to Bitwarden"
    }
    
    private fun createLoginDialog(): LoginDialog {
        return LoginDialog()
    }
    
    private class LoginDialog {
        private val emailField = JTextField(30)
        private val passwordField = JPasswordField(30)
        private val serverField = JTextField("https://api.bitwarden.com", 30)
        
        val email: String get() = emailField.text.trim()
        val password: String get() = String(passwordField.password)
        val serverUrl: String get() = serverField.text.trim()
        
        fun showConfirmDialog(): Int {
            val panel = javax.swing.JPanel(java.awt.GridBagLayout())
            val gbc = java.awt.GridBagConstraints()
            
            gbc.insets = java.awt.Insets(5, 5, 5, 5)
            gbc.anchor = java.awt.GridBagConstraints.WEST
            
            // Email field
            gbc.gridx = 0
            gbc.gridy = 0
            panel.add(javax.swing.JLabel("Email:"), gbc)
            gbc.gridx = 1
            panel.add(emailField, gbc)
            
            // Password field
            gbc.gridx = 0
            gbc.gridy = 1
            panel.add(javax.swing.JLabel("Master Password:"), gbc)
            gbc.gridx = 1
            panel.add(passwordField, gbc)
            
            // Server field
            gbc.gridx = 0
            gbc.gridy = 2
            panel.add(javax.swing.JLabel("Server URL:"), gbc)
            gbc.gridx = 1
            panel.add(serverField, gbc)
            
            return JOptionPane.showConfirmDialog(
                null,
                panel,
                "Login to Bitwarden",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            )
        }
    }
}