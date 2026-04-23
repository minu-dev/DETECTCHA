package com.example.detectcha

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationCatcherService : NotificationListenerService() {

    private val TAG = "NotificationCatcher"
    private var lastProcessedText = ""

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "[Notification Listener Connected]")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName
        val notification = sbn?.notification ?: return
        val extras = notification.extras

        val isGroupSummary = (notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            return
        }


        if (notification.category == Notification.CATEGORY_TRANSPORT ||
            notification.category == Notification.CATEGORY_SERVICE) {
            return
        }

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (text.isNullOrBlank()) return


        val title = extras.getString(Notification.EXTRA_TITLE) ?: "No Title"

        val currentMsg = "$title|$text"
        if (currentMsg == lastProcessedText) return
        lastProcessedText = currentMsg
        Log.d("NotificationCatcher", "[알림] 패키지: $packageName | 타이틀: $title | 내용: $text")
    }
}