package com.bitwarden.jetbrains.listeners

import com.intellij.openapi.diagnostic.thisLogger

/**
 * Listener for IDE events. This provides a simple implementation
 * for monitoring application state changes.
 */
class BitwardenApplicationListener {
    private val logger = thisLogger()
    
    fun onApplicationActivated() {
        logger.debug("IDE activated - Bitwarden plugin is active")
        // Here we could trigger background sync or other activities when the IDE becomes active
    }
    
    fun onApplicationDeactivated() {
        logger.debug("IDE deactivated")
        // Here we could pause certain activities when the IDE is not in focus
    }
}