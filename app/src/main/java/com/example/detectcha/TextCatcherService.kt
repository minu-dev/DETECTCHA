package com.example.detectcha

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.*

@SuppressLint("AccessibilityPolicy")
class TextCatcherService : AccessibilityService() {
    private val TAG = "TextCatcherService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "DetectchaForegroundChannel"
    private val WARNING_CHANNEL_ID = "fraud_warning_channel"

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var pollingJob: Job? = null

    private var latestRawText: String = ""
    private var lastWordsBuffer: List<String> = emptyList()

    private var isFraudSuspected: Boolean = true

    private val remittanceApps = mapOf(
        "viva.republica.toss" to "토스",
        "com.kbstar.kbbank" to "KB국민은행",
        "com.naverfin.payapp" to "네이버페이",
        "com.kakaobank.channel" to "카카오뱅크"
    )
    private var lastWarnedApp: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "[Text Catcher Connected]")

        CatcherController.serviceInstance = this

        createNotificationChannels()

        updateNotificationState(CatcherController.isCatching)
        startPollingTimer()
    }

    fun updateNotificationState(isOn: Boolean) {
        if (isOn) {
            val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

            val notification = notificationBuilder
                .setContentTitle("DETECTCHA")
                .setContentText("이 앱은 백그라운드에서 작동 중입니다.")
                .setSmallIcon(android.R.drawable.ic_secure)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }

            latestRawText = ""
            lastWordsBuffer = emptyList()
            lastWarnedApp = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!CatcherController.isCatching) return
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.google.android.as") return

        val windowInfo = event.source?.window
        if (windowInfo != null && windowInfo.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val sourceNode = event.source ?: return
            val fullRawText = aggregateTextFromNode(sourceNode)
            sourceNode.recycle()

            if (fullRawText.isNotBlank()) {
                latestRawText = fullRawText
            }
        }
    }

    private fun aggregateTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val nodeText = node.text?.toString()
        if (!nodeText.isNullOrBlank()) {
            return nodeText
        }
        var text = ""
        for (i in 0 until node.childCount) {
            val childText = aggregateTextFromNode(node.getChild(i))
            if (childText.isNotBlank()) {
                text += if (text.isEmpty()) childText else " $childText"
            }
        }
        return text
    }

    private fun startPollingTimer() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                if (CatcherController.isCatching) {
                    checkRemittanceAppLaunch()
                    processLatestText()
                } else {
                    lastWordsBuffer = emptyList()
                    latestRawText = ""
                }
                delay(2000L)
            }
        }
    }

    private fun processLatestText() {
        if (latestRawText.isBlank()) return

        var cleanText = latestRawText.replace(Regex("\\[.*?\\]"), "")
            .replace("Live Caption", "")
            .replace("실시간 자막", "")
            .replace("\n", " ")

        cleanText = cleanText.replace(Regex("[^a-zA-Z0-9가-힣\\s]"), "")
        cleanText = cleanText.replace("\\s+".toRegex(), " ").trim()

        val currWords = cleanText.split(" ").filter { it.isNotEmpty() }

        if (currWords.isEmpty()) {
            lastWordsBuffer = emptyList()
            return
        }

        var matchIdx = -1

        if (lastWordsBuffer.isNotEmpty()) {
            val checkLen = minOf(5, lastWordsBuffer.size)
            val buffer = lastWordsBuffer.takeLast(checkLen)

            for (len in checkLen downTo 2) {
                val subSeq = buffer.takeLast(len)
                val seqMatchIdx = currWords.windowed(len).indexOf(subSeq)
                if (seqMatchIdx != -1) {
                    matchIdx = seqMatchIdx + len - 1
                    break
                }
            }

            if (matchIdx == -1) {
                for (i in buffer.indices.reversed()) {
                    val wordToFind = buffer[i]
                    val idx = currWords.lastIndexOf(wordToFind)
                    if (idx != -1) {
                        matchIdx = idx
                        break
                    }
                }
            }
        }

        val newWords = if (matchIdx != -1) {
            currWords.subList(matchIdx + 1, currWords.size)
        } else {
            currWords
        }

        if (newWords.isNotEmpty()) {
            val finalSentence = newWords.joinToString(" ")
            Log.d(TAG, "[Caption]: $finalSentence")

            CoroutineScope(Dispatchers.IO).launch {
                 // TODO: AI 모델에 전송
            }
        }
        lastWordsBuffer = currWords.takeLast(5)
    }

    @SuppressLint("MissingPermission")
    private fun checkRemittanceAppLaunch() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 5000L

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var currentApp: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentApp = event.packageName
            }
        }

        if (currentApp != null) {
            if (remittanceApps.keys.contains(currentApp)) {
                if (isFraudSuspected && lastWarnedApp != currentApp) {
                    sendWarningNotification(currentApp)
                    lastWarnedApp = currentApp
                }
            } else {
                lastWarnedApp = null
            }
        }
    }

    private fun sendWarningNotification(packageName: String) {
        val appName = remittanceApps[packageName] ?: "송금 앱"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, WARNING_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val warningNotification = builder
            .setContentTitle("사기 위험 감지")
            .setContentText("사기 의심 정황이 발견되었습니다. $appName 송금 시 주의하세요!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(Notification.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(202, warningNotification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "탐지 서비스",
                NotificationManager.IMPORTANCE_LOW
            )

            val warningChannel = NotificationChannel(
                WARNING_CHANNEL_ID, "보이스피싱 경고 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "송금 앱 실행 시 사기 의심 경고를 보냅니다."
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(warningChannel)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        CatcherController.serviceInstance = null
    }
}

object CatcherController {
    @SuppressLint("StaticFieldLeak")
    var serviceInstance: TextCatcherService? = null
    var isCatching = false
        set (value) {
            field = value
            serviceInstance?.updateNotificationState(value)
        }
}