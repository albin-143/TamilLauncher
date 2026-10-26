package com.tamil.launcher

import android.app.Notification
import android.app.admin.DeviceAdminReceiver
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object BadgeStore {
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
}

class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        try {
            BadgeStore.counts = activeNotifications
                .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .groupingBy { it.packageName }.eachCount()
        } catch (_: Exception) {
        }
    }
}

class LockAdmin : DeviceAdminReceiver()
