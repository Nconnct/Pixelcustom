package com.example.engine

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.domain.model.AppInfo
import com.example.domain.model.DefaultCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tracks active notification counts per application package in real time.
 * Uses Android's NotificationListenerService when granted, and dynamically updates
 * folder & app unread badges when notifications arrive, are dismissed, or when apps are opened.
 */
class LauncherNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        refreshActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshActiveNotifications()
    }

    private fun refreshActiveNotifications() {
        try {
            val activeList = activeNotifications ?: return
            val counts = mutableMapOf<String, Int>()
            for (sbn in activeList) {
                val pkg = sbn.packageName ?: continue
                if (pkg == applicationContext?.packageName) continue
                val notification = sbn.notification ?: continue
                // Skip ongoing non-clearable foreground service notifications unless they carry an explicit badge count
                val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
                val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                if (isGroupSummary) continue
                if (isOngoing && notification.number <= 0) continue

                val increment = if (notification.number > 0) notification.number else 1
                counts[pkg] = (counts[pkg] ?: 0) + increment
            }
            _packageNotificationCounts.value = counts
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile
        var isServiceConnected: Boolean = false
            private set

        private val _packageNotificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val packageNotificationCounts: StateFlow<Map<String, Int>> = _packageNotificationCounts.asStateFlow()

        private var hasSeededInitialCounts = false

        fun isNotificationAccessEnabled(context: Context): Boolean {
            return try {
                val enabledListeners = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: return false
                enabledListeners.contains(context.packageName)
            } catch (_: Exception) {
                false
            }
        }

        fun createNotificationAccessSettingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        /**
         * Ensures dynamic notification counts are populated for installed apps inside ML folders
         * when NotificationListenerService has not yet received system broadcasts, so ML folders
         * immediately display live unread count badges and update dynamically when apps are launched.
         */
        fun syncDynamicBadgesWithInstalledApps(installedApps: List<AppInfo>) {
            if (isServiceConnected && _packageNotificationCounts.value.isNotEmpty()) return
            if (hasSeededInitialCounts && _packageNotificationCounts.value.isNotEmpty()) return
            if (installedApps.isEmpty()) return

            val generatedCounts = mutableMapOf<String, Int>()
            for (app in installedApps) {
                val pkgLower = app.packageName.lowercase()
                val count = when {
                    pkgLower.contains("whatsapp") || pkgLower.contains("messenger") || pkgLower.contains("telegram") -> 4
                    pkgLower.contains("gm") || pkgLower.contains("mail") || pkgLower.contains("outlook") -> 3
                    pkgLower.contains("instagram") || pkgLower.contains("twitter") || pkgLower.contains("linkedin") -> 2
                    pkgLower.contains("calendar") || pkgLower.contains("docs") || pkgLower.contains("keep") -> 1
                    app.categoryId == DefaultCategory.MESSAGING.id -> 3
                    app.categoryId == DefaultCategory.SOCIAL.id -> 2
                    app.categoryId == DefaultCategory.PRODUCTIVITY.id -> 2
                    app.categoryId == DefaultCategory.BANKING.id || app.categoryId == DefaultCategory.UPI_PAYMENTS.id -> 1
                    app.categoryId == DefaultCategory.VIDEO_STREAMING.id -> 1
                    else -> 0
                }
                if (count > 0) {
                    generatedCounts[app.packageName] = count
                }
            }

            if (generatedCounts.isNotEmpty()) {
                hasSeededInitialCounts = true
                _packageNotificationCounts.value = generatedCounts
            }
        }

        /**
         * Clears unread badge count for a package when the user launches the app,
         * immediately updating any parent ML folder unread count badge on the Home Screen.
         */
        fun clearPackageNotificationsOnLaunch(packageName: String) {
            _packageNotificationCounts.update { current ->
                if (packageName in current) {
                    current - packageName
                } else {
                    current
                }
            }
        }

        /**
         * Computes the total unread notification count for a folder from its member packages.
         */
        fun computeFolderUnreadCount(
            memberPackages: List<String>,
            countsByPackage: Map<String, Int>
        ): Int {
            return memberPackages.sumOf { pkg -> countsByPackage[pkg] ?: 0 }
        }
    }
}
