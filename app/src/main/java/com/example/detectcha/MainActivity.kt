package com.example.detectcha

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.PaintCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.detectcha.data.PhishingHistory
import com.example.detectcha.ui.PhishingHistoryViewModel
import com.example.detectcha.ui.PhishingTestScreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "알림 권한이 없으면 경고를 받을 수 없습니다.", Toast.LENGTH_LONG).show()
            }
            checkAndRequestUsageStatsPermission()
        }

    @kotlin.OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        setupGlobalExceptionHandler()
        
        try {
            startPermissionCheckFlow()
        } catch (e: Exception) {
            Log.e(TAG, "Initial permission flow failed", e)
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            DetectchaApp(windowSizeClass)
        }
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "CRITICAL ERROR in thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun startPermissionCheckFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkAndRequestUsageStatsPermission()
            }
        } else {
            checkAndRequestUsageStatsPermission()
        }
    }

    private fun checkAndRequestUsageStatsPermission() {
        try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            }

            if (mode != AppOpsManager.MODE_ALLOWED) {
                Toast.makeText(this, "사용 정보 접근 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage stats permission", e)
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun DetectchaApp(
    windowSizeClass: WindowSizeClass,
    viewModel: PhishingHistoryViewModel = viewModel()
) {
    val backgroundPainter = painterResource(id = R.drawable.background_image)
    val logoPainter = painterResource(id = R.drawable.detectcha_logo)
    val buttonOffPainter = painterResource(id = R.drawable.button_off)
    val buttonOnPainter = painterResource(id = R.drawable.button_on)

    var isActivated by remember { mutableStateOf(CatcherController.isCatching) }
    var isHistoryOpen by remember { mutableStateOf(false) }
    var isTestScreenOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val videoResId = R.raw.arc_reactor

    val exoPlayer = remember {
        try {
            ExoPlayer.Builder(context).build().apply {
                val uri = "android.resource://${context.packageName}/$videoResId"
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
        } catch (e: Exception) {
            Log.e("DetectchaApp", "Failed to initialize ExoPlayer", e)
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
        }
    }

    LaunchedEffect(isActivated, exoPlayer) {
        if (isActivated) exoPlayer?.play() else exoPlayer?.pause()
    }

    val videoHeight by animateDpAsState(
        targetValue = if (isActivated) 280.dp else 0.dp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "VideoHeightTransition"
    )

    val videoScale by animateFloatAsState(
        targetValue = if (isActivated) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "VideoScaleTransition"
    )

    val spacerHeight by animateDpAsState(
        targetValue = if (isActivated) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "SpacerHeightTransition"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isActivated) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "ContentAlphaTransition"
    )

    val isWideScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    Box(modifier = Modifier.fillMaxSize()) {
        if (isWideScreen) {
            Image(
                painter = backgroundPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f)
                    .align(Alignment.Center)
            ) {
                Image(
                    painter = backgroundPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Image(
                painter = backgroundPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        IconButton(
            onClick = { isHistoryOpen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(32.dp))
        }

        val contentModifier = if (isWideScreen) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(9f / 16f)
                .align(Alignment.Center)
        } else {
            Modifier.fillMaxSize()
        }

        Column(
            modifier = contentModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(700f / 1080f).height(videoHeight),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).aspectRatio(1080f / 1180f)) {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                val textureView = TextureView(ctx).apply {
                                    isOpaque = false
                                    exoPlayer?.setVideoTextureView(this)
                                }
                                addView(textureView, FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                ))

                                val paint = Paint()
                                PaintCompat.setBlendMode(paint, BlendModeCompat.SCREEN)
                                setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                            }
                        },
                        update = { view ->
                            view.alpha = contentAlpha
                            view.scaleX = videoScale
                            view.scaleY = videoScale
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacerHeight))

            Box(
                modifier = Modifier.width(180.dp).height(45.dp).clickable {
                    try {
                        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                        if (enabledServices?.contains(context.packageName) != true) {
                            Toast.makeText(context, "접근성 권한을 먼저 허용해주세요!", Toast.LENGTH_LONG).show()
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } else {
                            isActivated = !isActivated
                            CatcherController.isCatching = isActivated
                            
                            val serviceIntent = Intent(context, TextCatcherService::class.java)
                            if (isActivated) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                            } else {
                                context.stopService(serviceIntent)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DetectchaApp", "Service start/stop failed", e)
                        Toast.makeText(context, "서비스 제어 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = isActivated, animationSpec = tween(300)) { activated ->
                    Image(
                        painter = if (activated) buttonOffPainter else buttonOnPainter,
                        contentDescription = "Button",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Image(painter = logoPainter, contentDescription = "Logo", modifier = Modifier.size(width = 140.dp, height = 50.dp))
        }

        AnimatedVisibility(
            visible = isHistoryOpen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            HistoryOverlay(onClose = { isHistoryOpen = false }, onOpenTest = { isHistoryOpen = false; isTestScreenOpen = true }, viewModel = viewModel)
        }

        AnimatedVisibility(
            visible = isTestScreenOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            PhishingTestScreen(onBack = { isTestScreenOpen = false })
        }
    }
}

@Composable
fun HistoryOverlay(onClose: () -> Unit, onOpenTest: () -> Unit, viewModel: PhishingHistoryViewModel) {
    val historyList by viewModel.historyList.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212).copy(alpha = 0.95f)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "피싱 탐지 내역", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = { 
                        try {
                            viewModel.clearAll() 
                        } catch (e: Exception) {
                            Log.e("HistoryOverlay", "Failed to clear history", e)
                        }
                    }) { Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Gray) }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "탐지 내역이 없습니다.", color = Color.Gray) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(historyList, key = { it.id }) { history ->
                        HistoryItem(history, onDelete = { 
                            try {
                                viewModel.deleteHistory(history.id) 
                            } catch (e: Exception) {
                                Log.e("HistoryOverlay", "Failed to delete item", e)
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(history: PhishingHistory, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = "의심 정황: ${history.label}", color = if (history.probability >= 0.8f) Color.Red else Color.Yellow, fontWeight = FontWeight.Bold)
                    Text(text = String.format(Locale.getDefault(), "위험도: %.1f%%", history.probability * 100), color = Color.LightGray, fontSize = 12.sp)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.DarkGray, modifier = Modifier.size(20.dp)) }
            }
            Text(text = dateFormat.format(Date(history.timestamp)), color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "내용: ${history.text}", color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
}
