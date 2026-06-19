package com.example.detectcha

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.detectcha.data.AppDatabase
import com.example.detectcha.data.PhishingHistory
import kotlinx.coroutines.*

@SuppressLint("AccessibilityPolicy")
class TextCatcherService : AccessibilityService() {
    private val TAG = "TextCatcherService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "DetectchaForegroundChannel"
    private val WARNING_CHANNEL_ID = "warning_channel"

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollingJob: Job? = null

    private var latestRawText: String = ""
    private var lastProcessedText: String = ""

    @Volatile
    private var isFraudSuspected: Boolean = false
    private var lastWarnedTime: Long = 0L 
    
    private var phishingModelManager: PhishingModelManager? = null
    private lateinit var database: AppDatabase

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
        
        try {
            database = AppDatabase.getDatabase(this)
            createNotificationChannels()
            phishingModelManager = PhishingModelManager(this)
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed: ${e.message}", e)
        }
        
        updateNotificationState(CatcherController.isCatching)
        startPollingTimer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotificationState(CatcherController.isCatching)
        return START_STICKY
    }

    fun updateNotificationState(isOn: Boolean) {
        try {
            val notification = createForegroundNotification(isOn)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            if (!isOn) resetState()
        } catch (e: Exception) {
            Log.e(TAG, "Notification update failed: ${e.message}", e)
        }
    }

    private fun createForegroundNotification(isOn: Boolean): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val msg = if (isOn) "보이스피싱 탐지 중" else "탐지 서비스 대기 중"
        return builder
            .setContentTitle("DETECTCHA")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .build()
    }

    private fun resetState() {
        latestRawText = ""
        lastProcessedText = ""
        lastWarnedApp = null
        isFraudSuspected = false
        lastWarnedTime = 0L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!CatcherController.isCatching || event == null) return

        var sourceNode: AccessibilityNodeInfo? = null
        try {
            val packageName = event.packageName?.toString() ?: ""
            if (packageName != "com.google.android.as") return

            sourceNode = event.source ?: return
            
            val fullRawText = aggregateTextFromNode(sourceNode)
            if (fullRawText.isNotBlank()) {
                latestRawText = fullRawText
            }
        } catch (e: Exception) {
            Log.e(TAG, "Accessibility event processing error: ${e.message}", e)
        } finally {
            sourceNode?.recycle()
        }
    }

    private fun aggregateTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val collectedSet = mutableSetOf<String>()
        val sb = StringBuilder()
        try {
            collectTexts(node, collectedSet, sb, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Text aggregation error", e)
        }
        return sb.toString().trim()
    }

    private fun collectTexts(node: AccessibilityNodeInfo, set: MutableSet<String>, sb: StringBuilder, depth: Int) {
        if (depth > 25) return
        try {
            val nodeText = node.text?.toString() ?: node.contentDescription?.toString()
            if (!nodeText.isNullOrBlank()) {
                val clean = nodeText.trim()
                if (set.add(clean)) {
                    sb.append(clean).append(" ")
                }
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (e: Exception) { null }
                if (child != null) {
                    collectTexts(child, set, sb, depth + 1)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
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
                    Log.e(TAG, "Polling loop error: ${e.message}", e)
                }
                delay(2000L)
            }
        }
    }

    private fun processLatestText() {
        if (latestRawText.isBlank()) return

        try {
            val currentCleanText = latestRawText.replace(Regex("\\[.*?\\]"), "")
                .replace("Live Caption", "")
                .replace("실시간 자막", "")
                .replace("\n", " ")
                .replace(Regex("[^a-zA-Z0-9가-힣\\s]"), " ")
                .replace("\\s+".toRegex(), " ").trim()

            if (currentCleanText == lastProcessedText || currentCleanText.isEmpty()) return

            val newText = findNewWords(lastProcessedText, currentCleanText)

            if (newText.isNotBlank()) {
                lastProcessedText = currentCleanText
                analyzePhishing(newText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text processing error: ${e.message}", e)
        }
    }

    private fun findNewWords(old: String, new: String): String {
        return try {
            val oldWords = old.split(" ").filter { it.isNotEmpty() }.takeLast(5)
            val newWords = new.split(" ").filter { it.isNotEmpty() }
            if (oldWords.isEmpty()) return newWords.joinToString(" ")
            
            var matchIndexInNew = -1
            for (i in newWords.indices.reversed()) {
                if (oldWords.contains(newWords[i])) {
                    matchIndexInNew = i
                    break
                }
            }
            if (matchIndexInNew != -1) {
                newWords.subList(matchIndexInNew + 1, newWords.size).joinToString(" ")
            } else {
                newWords.takeLast(20).joinToString(" ")
            }
        } catch (e: Exception) {
            new
        }
    }

    private fun analyzePhishing(text: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val result = phishingModelManager?.classifyText(text)
                if (result != null && result.isFraudSuspected) {
                    isFraudSuspected = true
                    savePhishingHistory(text, result.topLabel, result.topProbability)
                    
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastWarnedTime >= 4000L) {
                        showImmediateWarning()
                        lastWarnedTime = currentTime
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis error: ${e.message}", e)
            }
        }
    }

    private fun savePhishingHistory(text: String, label: String, probability: Float) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val history = PhishingHistory(
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    label = label,
                    probability = probability
                )
                database.phishingHistoryDao().insert(history)
            } catch (e: Exception) {
                Log.e(TAG, "History save error: ${e.message}", e)
            }
        }
    }

    private fun showImmediateWarning() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val soundUri = android.net.Uri.parse("android.resource://${packageName}/${R.raw.alert_sound}")
            val vibrationPattern = longArrayOf(0, 600, 200, 600)

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, WARNING_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setSound(soundUri)
                    .setVibrate(vibrationPattern)
            }

            val warningNotification = builder
                .setContentTitle("🚨 보이스피싱 위험 감지")
                .setContentText("보이스피싱 의심 상황 감지되었습니다.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(201, warningNotification)
        } catch (e: Exception) {
            Log.e(TAG, "Warning notification error: ${e.message}", e)
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
        } catch (e: Exception) {
            Log.e(TAG, "Usage stats check error", e)
        }
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
                .setContentTitle("⚠ 보이스피싱 위험 감지")
                .setContentText("통화 중 사기 의심 정황이 발견되었습니다. $appName 송금 시 주의하세요!")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(Notification.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_ALL)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(202, warningNotification)
        } catch (e: Exception) {
            Log.e(TAG, "Remittance warning notification error", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = getSystemService(NotificationManager::class.java)

                // 포그라운드 서비스 채널 (기존과 동일)
                val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    "탐지 서비스",
                    NotificationManager.IMPORTANCE_LOW
                )

                // 경고 채널: 진동과 커스텀 사운드 설정
                val warningChannel = NotificationChannel(
                    WARNING_CHANNEL_ID,
                    "보이스피싱 경고 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 200, 600) // 더 강한 패턴
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    description = "보이스피싱 위험 감지 시 상단 팝업 알림과 진동을 보냅니다."

                    // 앱 내 raw 자원 사용: res/raw/alert_sound.ogg
                    val soundUri = android.net.Uri.parse("android.resource://${packageName}/${R.raw.alert_sound}")
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM) // 경고성이라 ALARM 사용 권장
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(soundUri, audioAttributes)
                }

                manager.createNotificationChannel(serviceChannel)
                manager.createNotificationChannel(warningChannel)
            } catch (e: Exception) {
                Log.e(TAG, "Channel creation error", e)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            pollingJob?.cancel()
            serviceScope.cancel()
            phishingModelManager?.close()
            CatcherController.serviceInstance = null
        } catch (e: Exception) {
            Log.e(TAG, "Service destroy error", e)
        }
    }
}

object CatcherController {
    @SuppressLint("StaticFieldLeak")
    var serviceInstance: TextCatcherService? = null
    var isCatching = false
        set (value) {
            field = value
            try {
                serviceInstance?.updateNotificationState(value)
            } catch (e: Exception) {
                Log.e("CatcherController", "State update failed", e)
            }
        }
}
