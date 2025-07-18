package com.bitwarden.jetbrains.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

class SettingsAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        // In a full implementation, this would open a custom settings page
        // For now, just show a placeholder message
        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            "Bitwarden settings would be shown here.\n\nThis would include:\n" +
            "- Auto-sync settings\n" +
            "- Server configuration\n" +
            "- Autofill preferences\n" +
            "- Security options",
            "Bitwarden Settings"
        )
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }
}