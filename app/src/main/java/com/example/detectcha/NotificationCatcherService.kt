package com.example.detectcha

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationCatcherService : NotificationListenerService() {

    private val TAG = "NotificationCatcher"
    private var lastProcessedText = ""

    override fun onListenerConnected() {
        try {
            super.onListenerConnected()
            Log.d(TAG, "[Notification Listener Connected]")
        } catch (e: Exception) {
            Log.e(TAG, "Failed on listener connection: ${e.message}", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return

            val packageName = sbn.packageName ?: "unknown"
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            if (isGroupSummary) return

            if (notification.category == Notification.CATEGORY_TRANSPORT ||
                notification.category == Notification.CATEGORY_SERVICE) {
                return
            }

            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (text.isNullOrBlank()) return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "No Title"

            val currentMsg = "$title|$text"
            if (currentMsg == lastProcessedText) return
            lastProcessedText = currentMsg
            
            Log.d(TAG, "[알림] 패키지: $packageName | 타이틀: $title | 내용: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}", e)
        }
    }

    override fun onListenerDisconnected() {
        try {
            super.onListenerDisconnected()
            Log.d(TAG, "[Notification Listener Disconnected]")
        } catch (e: Exception) {
            Log.e(TAG, "Error on listener disconnection: ${e.message}", e)
        }
    }
}
