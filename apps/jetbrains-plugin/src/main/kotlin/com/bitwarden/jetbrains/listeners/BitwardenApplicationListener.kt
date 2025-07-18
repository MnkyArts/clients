package com.bitwarden.jetbrains.listeners

import com.intellij.ide.ApplicationActivationListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.wm.IdeFrame

class BitwardenApplicationListener : ApplicationActivationListener {
    private val logger = thisLogger()
    
    override fun applicationActivated(ideFrame: IdeFrame) {
        logger.debug("IDE activated - Bitwarden plugin is active")
        // Here we could trigger background sync or other activities when the IDE becomes active
    }
    
    override fun applicationDeactivated(ideFrame: IdeFrame) {
        logger.debug("IDE deactivated")
        // Here we could pause certain activities when the IDE is not in focus
    }
}