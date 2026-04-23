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
import java.util.Locale

@SuppressLint("AccessibilityPolicy")
class TextCatcherService : AccessibilityService() {
    private val TAG = "TextCatcherService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "DetectchaForegroundChannel"
    private val WARNING_CHANNEL_ID = "fraud_warning_channel"

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollingJob: Job? = null

    private var latestRawText: String = ""
    private var lastProcessedText: String = ""

    private var isFraudSuspected: Boolean = false
    private var phishingModelManager: PhishingModelManager? = null

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
        try {
            phishingModelManager = PhishingModelManager(this)
        } catch (e: Exception) {
            Log.e(TAG, "모델 매니저 초기화 실패: ${e.message}")
        }
        updateNotificationState(CatcherController.isCatching)
        startPollingTimer()
    }

    fun updateNotificationState(isOn: Boolean) {
        try {
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
                resetState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "알림 상태 업데이트 실패: ${e.message}")
        }
    }

    private fun resetState() {
        latestRawText = ""
        lastProcessedText = ""
        lastWarnedApp = null
        isFraudSuspected = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!CatcherController.isCatching || event == null) return

        try {
            val packageName = event.packageName?.toString() ?: ""
            if (packageName != "com.google.android.as") return

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                val sourceNode = event.source
                if (sourceNode != null) {
                    val fullRawText = aggregateTextFromNode(sourceNode)
                    sourceNode.recycle()

                    if (fullRawText.isNotBlank()) {
                        latestRawText = fullRawText
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "이벤트 처리 중 에러: ${e.message}")
        }
    }

    private fun aggregateTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val collectedTexts = mutableListOf<String>()
        collectTexts(node, collectedTexts)
        return collectedTexts.distinct().joinToString(" ").trim()
    }

    private fun collectTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        val nodeText = node.text?.toString() ?: node.contentDescription?.toString()
        if (!nodeText.isNullOrBlank()) {
            list.add(nodeText.trim())
        }

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (e: Exception) { null }
            if (child != null) {
                collectTexts(child, list)
                try { child.recycle() } catch (e: Exception) {}
            }
        }
    }

    private fun startPollingTimer() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    if (CatcherController.isCatching) {
                        checkRemittanceAppLaunch()
                        processLatestText()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "폴링 루프 에러: ${e.message}")
                }
                delay(2000L)
            }
        }
    }

    private fun processLatestText() {
        if (latestRawText.isBlank()) return

        try {
            var currentCleanText = latestRawText.replace(Regex("\\[.*?\\]"), "")
                .replace("Live Caption", "")
                .replace("실시간 자막", "")
                .replace("\n", " ")
                .replace(Regex("[^a-zA-Z0-9가-힣\\s]"), " ")
                .replace("\\s+".toRegex(), " ").trim()

            if (currentCleanText == lastProcessedText || currentCleanText.isEmpty()) return

            val newText = findNewWords(lastProcessedText, currentCleanText)

            if (newText.isNotBlank()) {
                Log.d(TAG, "[새로 추출된 자막]: $newText")
                lastProcessedText = currentCleanText
                analyzePhishing(newText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "텍스트 처리 에러: ${e.message}")
        }
    }

    private fun findNewWords(old: String, new: String): String {
        val oldWords = old.split(" ").filter { it.isNotEmpty() }.takeLast(10)
        val newWords = new.split(" ").filter { it.isNotEmpty() }.takeLast(50)
        
        if (oldWords.isEmpty()) return newWords.joinToString(" ")
        
        var matchIndexInNew = -1
        for (i in newWords.indices.reversed()) {
            val word = newWords[i]
            if (oldWords.contains(word)) {
                matchIndexInNew = i
                break
            }
        }
        
        return if (matchIndexInNew != -1) {
            newWords.subList(matchIndexInNew + 1, newWords.size).joinToString(" ")
        } else {
            newWords.joinToString(" ")
        }
    }

    private fun analyzePhishing(text: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val result = phishingModelManager?.classifyText(text)
                if (result != null) {
                    val probability = result.probability
                    val label = result.label
                    if (probability >= 0.5f) {
                        isFraudSuspected = true
                        Log.e(TAG, "[의심 감지] 유형: $label (${String.format(Locale.US, "%.2f", probability * 100)}%) | 텍스트: $text")
                    } else {
                        Log.d(TAG, "[정상 대화] 결과: $label | 텍스트: $text")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "추론 에러: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkRemittanceAppLaunch() {
        try {
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

            if (currentApp != null && remittanceApps.containsKey(currentApp)) {
                if (isFraudSuspected && lastWarnedApp != currentApp) {
                    sendWarningNotification(currentApp)
                    lastWarnedApp = currentApp
                }
            } else {
                lastWarnedApp = null
            }
        } catch (e: Exception) {}
    }

    private fun sendWarningNotification(packageName: String) {
        try {
            val appName = remittanceApps[packageName] ?: "송금 앱"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, WARNING_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

            val warningNotification = builder
                .setContentTitle("⚠보이스피싱 위험 감지")
                .setContentText("통화 중 사기 의심 정황이 발견되었습니다. $appName 송금 시 주의하세요!")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(Notification.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(202, warningNotification)
        } catch (e: Exception) {}
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = getSystemService(NotificationManager::class.java)
                val serviceChannel = NotificationChannel(CHANNEL_ID, "탐지 서비스", NotificationManager.IMPORTANCE_LOW)
                val warningChannel = NotificationChannel(WARNING_CHANNEL_ID, "보이스피싱 경고 알림", NotificationManager.IMPORTANCE_HIGH)
                manager.createNotificationChannel(serviceChannel)
                manager.createNotificationChannel(warningChannel)
            } catch (e: Exception) {}
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        serviceScope.cancel()
        phishingModelManager?.close()
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
