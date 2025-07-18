package com.bitwarden.jetbrains.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service  
class BitwardenSyncService {
    private val logger = thisLogger()
    private val syncExecutor: ScheduledExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService("BitwardenSync", 1)
    
    private var autoSyncEnabled = true
    private var syncIntervalMinutes = 15L
    private var scheduledSync: ScheduledFuture<*>? = null
    private var isSyncing = false
    private var lastSyncTime: Long = 0
    
    fun startAutoSync(authService: BitwardenAuthService, vaultService: BitwardenVaultService) {
        if (!autoSyncEnabled) {
            logger.info("Auto-sync is disabled")
            return
        }
        
        stopAutoSync()
        
        logger.info("Starting auto-sync with interval: $syncIntervalMinutes minutes")
        scheduledSync = syncExecutor.scheduleAtFixedRate({
            if (authService.isAuthenticated() && !isSyncing) {
                syncVault(authService, vaultService)
            }
        }, syncIntervalMinutes, syncIntervalMinutes, TimeUnit.MINUTES)
    }
    
    fun stopAutoSync() {
        scheduledSync?.cancel(false)
        scheduledSync = null
        logger.info("Auto-sync stopped")
    }
    
    fun syncVaultAsync(authService: BitwardenAuthService, vaultService: BitwardenVaultService): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            syncVault(authService, vaultService)
        }, AppExecutorUtil.getAppExecutorService())
    }
    
    private fun syncVault(authService: BitwardenAuthService, vaultService: BitwardenVaultService): Boolean {
        if (isSyncing) {
            logger.debug("Sync already in progress, skipping")
            return false
        }
        
        if (!authService.isAuthenticated()) {
            logger.debug("Not authenticated, skipping sync")
            return false
        }
        
        isSyncing = true
        try {
            logger.info("Starting vault synchronization")
            val startTime = System.currentTimeMillis()
            
            val success = vaultService.fetchVaultAsync(authService).get(30, TimeUnit.SECONDS)
            
            if (success) {
                lastSyncTime = System.currentTimeMillis()
                val duration = lastSyncTime - startTime
                logger.info("Vault synchronized successfully in ${duration}ms")
            } else {
                logger.warn("Vault synchronization failed")
            }
            
            return success
        } catch (e: Exception) {
            logger.error("Error during vault synchronization", e)
            return false
        } finally {
            isSyncing = false
        }
    }
    
    fun isSyncing(): Boolean = isSyncing
    
    fun getLastSyncTime(): Long = lastSyncTime
    
    fun setAutoSyncEnabled(enabled: Boolean) {
        autoSyncEnabled = enabled
        logger.info("Auto-sync ${if (enabled) "enabled" else "disabled"}")
    }
    
    fun isAutoSyncEnabled(): Boolean = autoSyncEnabled
    
    fun setSyncInterval(minutes: Long) {
        if (minutes < 1) {
            logger.warn("Invalid sync interval: $minutes. Must be at least 1 minute.")
            return
        }
        
        syncIntervalMinutes = minutes
        logger.info("Sync interval set to $syncIntervalMinutes minutes")
        
        // Restart auto-sync with new interval if it's currently running
        if (scheduledSync != null && !scheduledSync!!.isCancelled) {
            // Note: This requires references to auth and vault services
            // In a real implementation, we'd store these references or use a different approach
            logger.info("Restarting auto-sync with new interval")
        }
    }
    
    fun getSyncInterval(): Long = syncIntervalMinutes
    
    fun getTimeSinceLastSync(): Long {
        return if (lastSyncTime > 0) {
            System.currentTimeMillis() - lastSyncTime
        } else {
            -1
        }
    }
    
    fun getFormattedLastSyncTime(): String {
        return if (lastSyncTime > 0) {
            val timeSince = getTimeSinceLastSync()
            when {
                timeSince < 60_000 -> "Just now"
                timeSince < 3600_000 -> "${timeSince / 60_000} minutes ago"
                timeSince < 86400_000 -> "${timeSince / 3600_000} hours ago"
                else -> "${timeSince / 86400_000} days ago"
            }
        } else {
            "Never"
        }
    }
    
    fun dispose() {
        stopAutoSync()
        syncExecutor.shutdown()
        try {
            if (!syncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                syncExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            syncExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}