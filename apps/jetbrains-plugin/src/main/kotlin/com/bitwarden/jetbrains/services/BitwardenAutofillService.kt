package com.bitwarden.jetbrains.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.awt.RelativePoint
import java.awt.Component
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.Icon

@Service
class BitwardenAutofillService {
    private val logger = thisLogger()
    
    data class AutofillItem(
        val id: String,
        val name: String,
        val username: String?,
        val password: String?,
        val uri: String?
    )
    
    fun showAutofillPopup(component: Component, x: Int, y: Int, uri: String? = null) {
        val vaultService = BitwardenVaultService()
        val items = if (uri != null) {
            vaultService.findItemsByUri(uri)
        } else {
            vaultService.getLoginItems()
        }
        
        if (items.isEmpty()) {
            logger.info("No autofill items found for URI: $uri")
            return
        }
        
        val autofillItems = items.mapNotNull { vaultItem ->
            vaultItem.login?.let { login ->
                AutofillItem(
                    id = vaultItem.id,
                    name = vaultItem.name,
                    username = login.username,
                    password = login.password,
                    uri = login.uris.firstOrNull()
                )
            }
        }
        
        if (autofillItems.isEmpty()) {
            logger.info("No valid login items found")
            return
        }
        
        val popup = createAutofillPopup(autofillItems)
        popup.show(RelativePoint(component, java.awt.Point(x, y)))
    }
    
    private fun createAutofillPopup(items: List<AutofillItem>): ListPopup {
        val popupStep = object : BaseListPopupStep<AutofillItem>("Bitwarden Autofill", items) {
            override fun getTextFor(value: AutofillItem): String {
                return "${value.name} (${value.username ?: "No username"})"
            }
            
            override fun getIconFor(value: AutofillItem): Icon? {
                // Would use appropriate icons in real implementation
                return null
            }
            
            override fun onChosen(selectedValue: AutofillItem, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    handleAutofillSelection(selectedValue)
                }
                return null
            }
            
            override fun isSpeedSearchEnabled(): Boolean = true
            
            override fun getSpeedSearchFilter(): com.intellij.openapi.ui.popup.SpeedSearchFilter<AutofillItem>? {
                return object : com.intellij.openapi.ui.popup.SpeedSearchFilter<AutofillItem> {
                    override fun canBeHidden(value: AutofillItem): Boolean = false
                    override fun getIndexedString(value: AutofillItem): String {
                        return value.name + " " + (value.username ?: "")
                    }
                }
            }
        }
        
        return JBPopupFactory.getInstance().createListPopup(popupStep)
    }
    
    private fun handleAutofillSelection(item: AutofillItem) {
        logger.info("Autofill selected: ${item.name}")
        
        // Show submenu for username/password selection
        val options = mutableListOf<String>()
        if (item.username != null) options.add("Fill Username")
        if (item.password != null) options.add("Fill Password")
        if (item.username != null && item.password != null) options.add("Fill Both")
        options.add("Copy Username")
        options.add("Copy Password")
        
        if (options.isEmpty()) {
            logger.warn("No autofill options available for item: ${item.name}")
            return
        }
        
        val actionStep = object : BaseListPopupStep<String>("Choose Action", options) {
            override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    executeAutofillAction(item, selectedValue)
                }
                return null
            }
        }
        
        val actionPopup = JBPopupFactory.getInstance().createListPopup(actionStep)
        actionPopup.showInFocusCenter()
    }
    
    private fun executeAutofillAction(item: AutofillItem, action: String) {
        when (action) {
            "Fill Username" -> {
                item.username?.let { username ->
                    simulateTyping(username)
                    logger.info("Username filled for: ${item.name}")
                }
            }
            "Fill Password" -> {
                item.password?.let { password ->
                    simulateTyping(password)
                    logger.info("Password filled for: ${item.name}")
                }
            }
            "Fill Both" -> {
                item.username?.let { username ->
                    simulateTyping(username)
                    simulateTabKey()
                    item.password?.let { password ->
                        simulateTyping(password)
                    }
                    logger.info("Username and password filled for: ${item.name}")
                }
            }
            "Copy Username" -> {
                item.username?.let { username ->
                    copyToClipboard(username)
                    logger.info("Username copied for: ${item.name}")
                }
            }
            "Copy Password" -> {
                item.password?.let { password ->
                    copyToClipboard(password)
                    logger.info("Password copied for: ${item.name}")
                }
            }
        }
    }
    
    private fun simulateTyping(text: String) {
        try {
            val robot = java.awt.Robot()
            robot.autoDelay = 10
            
            for (char in text) {
                val keyCode = getKeyCode(char)
                if (keyCode != -1) {
                    val needsShift = char.isUpperCase() || char in "!@#$%^&*()_+{}|:\"<>?~"
                    
                    if (needsShift) {
                        robot.keyPress(KeyEvent.VK_SHIFT)
                    }
                    
                    robot.keyPress(keyCode)
                    robot.keyRelease(keyCode)
                    
                    if (needsShift) {
                        robot.keyRelease(KeyEvent.VK_SHIFT)
                    }
                    
                    Thread.sleep(5) // Small delay between keystrokes
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to simulate typing", e)
        }
    }
    
    private fun simulateTabKey() {
        try {
            val robot = java.awt.Robot()
            robot.keyPress(KeyEvent.VK_TAB)
            robot.keyRelease(KeyEvent.VK_TAB)
        } catch (e: Exception) {
            logger.error("Failed to simulate tab key", e)
        }
    }
    
    private fun getKeyCode(char: Char): Int {
        return when (char.uppercase().first()) {
            'A' -> KeyEvent.VK_A
            'B' -> KeyEvent.VK_B
            'C' -> KeyEvent.VK_C
            'D' -> KeyEvent.VK_D
            'E' -> KeyEvent.VK_E
            'F' -> KeyEvent.VK_F
            'G' -> KeyEvent.VK_G
            'H' -> KeyEvent.VK_H
            'I' -> KeyEvent.VK_I
            'J' -> KeyEvent.VK_J
            'K' -> KeyEvent.VK_K
            'L' -> KeyEvent.VK_L
            'M' -> KeyEvent.VK_M
            'N' -> KeyEvent.VK_N
            'O' -> KeyEvent.VK_O
            'P' -> KeyEvent.VK_P
            'Q' -> KeyEvent.VK_Q
            'R' -> KeyEvent.VK_R
            'S' -> KeyEvent.VK_S
            'T' -> KeyEvent.VK_T
            'U' -> KeyEvent.VK_U
            'V' -> KeyEvent.VK_V
            'W' -> KeyEvent.VK_W
            'X' -> KeyEvent.VK_X
            'Y' -> KeyEvent.VK_Y
            'Z' -> KeyEvent.VK_Z
            '0' -> KeyEvent.VK_0
            '1' -> KeyEvent.VK_1
            '2' -> KeyEvent.VK_2
            '3' -> KeyEvent.VK_3
            '4' -> KeyEvent.VK_4
            '5' -> KeyEvent.VK_5
            '6' -> KeyEvent.VK_6
            '7' -> KeyEvent.VK_7
            '8' -> KeyEvent.VK_8
            '9' -> KeyEvent.VK_9
            ' ' -> KeyEvent.VK_SPACE
            '.' -> KeyEvent.VK_PERIOD
            ',' -> KeyEvent.VK_COMMA
            '-' -> KeyEvent.VK_MINUS
            '=' -> KeyEvent.VK_EQUALS
            '[' -> KeyEvent.VK_OPEN_BRACKET
            ']' -> KeyEvent.VK_CLOSE_BRACKET
            '\\' -> KeyEvent.VK_BACK_SLASH
            ';' -> KeyEvent.VK_SEMICOLON
            '\'' -> KeyEvent.VK_QUOTE
            '/' -> KeyEvent.VK_SLASH
            '`' -> KeyEvent.VK_BACK_QUOTE
            else -> -1
        }
    }
    
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(text)
            clipboard.setContents(selection, null)
        } catch (e: Exception) {
            logger.error("Failed to copy to clipboard", e)
        }
    }
    
    fun detectCurrentContext(): String? {
        // This would analyze the current IDE context to determine if we're in a login form
        // For now, return null (no context detected)
        return null
    }
    
    fun isAutofillAvailable(): Boolean {
        // Check if autofill is enabled and we have vault items
        val vaultService = BitwardenVaultService()
        return vaultService.getLoginItems().isNotEmpty()
    }
}