package com.bitwarden.jetbrains.ui

import com.bitwarden.jetbrains.services.BitwardenAuthService
import com.bitwarden.jetbrains.services.BitwardenVaultService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.*
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

class BitwardenToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val bitwardenToolWindow = BitwardenToolWindow(project)
        val content = ContentFactory.getInstance().createContent(bitwardenToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}

class BitwardenToolWindow(private val project: Project) {
    private val authService = ApplicationManager.getApplication().service<BitwardenAuthService>()
    private val vaultService = ApplicationManager.getApplication().service<BitwardenVaultService>()
    
    private val mainPanel = JPanel(BorderLayout())
    private val loginPanel = createLoginPanel()
    private val vaultPanel = createVaultPanel()
    
    init {
        updateUI()
    }
    
    fun getContent(): JComponent = mainPanel
    
    private fun createLoginPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        
        gbc.insets = Insets(10, 10, 10, 10)
        gbc.fill = GridBagConstraints.HORIZONTAL
        
        // Title
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2
        val titleLabel = JLabel("Bitwarden", SwingConstants.CENTER)
        titleLabel.font = titleLabel.font.deriveFont(16f)
        panel.add(titleLabel, gbc)
        
        // Description
        gbc.gridy = 1
        val descLabel = JLabel("<html><center>Please log in to access your<br>Bitwarden vault</center></html>", SwingConstants.CENTER)
        panel.add(descLabel, gbc)
        
        // Login button
        gbc.gridy = 2
        gbc.gridwidth = 1
        val loginButton = JButton("Login")
        loginButton.addActionListener {
            // Trigger login action
            val loginAction = com.bitwarden.jetbrains.actions.LoginAction()
            val actionEvent = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                loginAction, null, "", com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(project)
            )
            loginAction.actionPerformed(actionEvent)
            
            // Update UI after login attempt
            SwingUtilities.invokeLater { updateUI() }
        }
        panel.add(loginButton, gbc)
        
        return panel
    }
    
    private fun createVaultPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // Header with user info and sync button
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        val userLabel = JLabel("Logged in to Bitwarden")
        headerPanel.add(userLabel, BorderLayout.WEST)
        
        val syncButton = JButton("Sync")
        syncButton.addActionListener {
            val syncAction = com.bitwarden.jetbrains.actions.SyncAction()
            val actionEvent = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                syncAction, null, "", com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(project)
            )
            syncAction.actionPerformed(actionEvent)
        }
        headerPanel.add(syncButton, BorderLayout.EAST)
        
        panel.add(headerPanel, BorderLayout.NORTH)
        
        // Vault items list
        val vaultItems = vaultService.getVaultItems()
        val listModel = DefaultListModel<String>()
        
        if (vaultItems.isEmpty()) {
            listModel.addElement("No vault items found")
            listModel.addElement("Click 'Sync' to refresh")
        } else {
            vaultItems.forEach { item ->
                val displayText = when (item.type) {
                    com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.LOGIN -> 
                        "🔐 ${item.name} (${item.login?.username ?: "No username"})"
                    com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.SECURE_NOTE -> 
                        "📝 ${item.name}"
                    com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.CARD -> 
                        "💳 ${item.name}"
                    com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.IDENTITY -> 
                        "👤 ${item.name}"
                }
                listModel.addElement(displayText)
            }
        }
        
        val itemsList = JList(listModel)
        itemsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        
        // Add double-click handler
        itemsList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedIndex = itemsList.selectedIndex
                    if (selectedIndex >= 0 && selectedIndex < vaultItems.size) {
                        val item = vaultItems[selectedIndex]
                        showItemDetails(item)
                    }
                }
            }
        })
        
        val scrollPane = JScrollPane(itemsList)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // Footer with quick actions
        val footerPanel = JPanel()
        footerPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        val quickAccessButton = JButton("Quick Access (Ctrl+Alt+B)")
        quickAccessButton.addActionListener {
            val quickAccessAction = com.bitwarden.jetbrains.actions.QuickAccessAction()
            val actionEvent = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                quickAccessAction, null, "", com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(project)
            )
            quickAccessAction.actionPerformed(actionEvent)
        }
        footerPanel.add(quickAccessButton)
        
        val logoutButton = JButton("Logout")
        logoutButton.addActionListener {
            val logoutAction = com.bitwarden.jetbrains.actions.LogoutAction()
            val actionEvent = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                logoutAction, null, "", com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(project)
            )
            logoutAction.actionPerformed(actionEvent)
            
            // Update UI after logout
            SwingUtilities.invokeLater { updateUI() }
        }
        footerPanel.add(logoutButton)
        
        panel.add(footerPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun showItemDetails(item: com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItem) {
        val details = StringBuilder()
        details.append("Name: ${item.name}\n")
        details.append("Type: ${item.type}\n\n")
        
        when (item.type) {
            com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.LOGIN -> {
                item.login?.let { login ->
                    details.append("Username: ${login.username ?: "None"}\n")
                    details.append("Password: ${"*".repeat(login.password?.length ?: 0)}\n")
                    if (login.uris.isNotEmpty()) {
                        details.append("URLs:\n")
                        login.uris.forEach { uri ->
                            details.append("  - $uri\n")
                        }
                    }
                }
            }
            com.bitwarden.jetbrains.services.BitwardenVaultService.VaultItemType.SECURE_NOTE -> {
                item.secureNote?.let { note ->
                    details.append("Note: ${note.note}\n")
                }
            }
            // Add other types as needed
            else -> {
                details.append("Details not implemented for this item type")
            }
        }
        
        JOptionPane.showMessageDialog(
            mainPanel,
            details.toString(),
            "Item Details: ${item.name}",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
    
    private fun updateUI() {
        mainPanel.removeAll()
        
        if (authService.isAuthenticated()) {
            mainPanel.add(vaultPanel, BorderLayout.CENTER)
        } else {
            mainPanel.add(loginPanel, BorderLayout.CENTER)
        }
        
        mainPanel.revalidate()
        mainPanel.repaint()
    }
}