package com.example.detectcha

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.TextureView
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.Toast
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.PaintCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : ComponentActivity() {
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "알림 권한이 없으면 경고를 받을 수 없습니다.", Toast.LENGTH_LONG).show()
            }
            // 💡 [핵심] 알림 권한 처리가 끝난 직후에 사용 정보 권한을 체크하도록 변경!
            checkAndRequestUsageStatsPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. 앱 실행 시 권한 흐름 제어
        startPermissionCheckFlow()

        setContent {
            DetectchaApp()
        }
    }

    private fun startPermissionCheckFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // 알림 권한이 없으면 팝업부터 띄움 (팝업이 닫히면 launcher 안에서 사용정보 권한을 부름)
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // 이미 알림 권한이 있다면 바로 사용 정보 권한 체크
                checkAndRequestUsageStatsPermission()
            }
        } else {
            // 안드로이드 13 미만은 알림 팝업이 필요 없으므로 바로 사용 정보 권한 체크
            checkAndRequestUsageStatsPermission()
        }
    }

    private fun checkAndRequestUsageStatsPermission() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }

        if (mode != AppOpsManager.MODE_ALLOWED) {
            Toast.makeText(this, "송금 앱 감지를 위해 '사용 정보 접근' 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun DetectchaApp() {
    val backgroundPainter = painterResource(id = R.drawable.background_image)
    val logoPainter = painterResource(id = R.drawable.detectcha_logo)
    val buttonOffPainter = painterResource(id = R.drawable.button_off)
    val buttonOnPainter = painterResource(id = R.drawable.button_on)

    var isActivated by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val videoResId = R.raw.arc_reactor

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = "android.resource://${context.packageName}/$videoResId"
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    LaunchedEffect(isActivated, exoPlayer) {
        if (isActivated) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = backgroundPainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(700f / 1080f)
                    .height(videoHeight),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(unbounded = true)
                        .aspectRatio(1080f / 1180f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                val textureView = TextureView(ctx).apply {
                                    isOpaque = false
                                    exoPlayer.setVideoTextureView(this)
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
                modifier = Modifier
                    .width(180.dp)
                    .height(45.dp)
                    .clickable {
                    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                    val enabledServices = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    )
                    val isServiceEnabled = enabledServices?.contains(context.packageName) == true

                    if (!isServiceEnabled) {
                        Toast.makeText(context, "접근성 권한을 먼저 허용해주세요!", Toast.LENGTH_LONG).show()
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    } else {
                        isActivated = !isActivated
                        CatcherController.isCatching = isActivated

                        // 💡 [추가] 버튼 클릭 시 서비스에 명령 전달
                        val serviceIntent = Intent(context, TextCatcherService::class.java)
                        if (isActivated) {
                            // 버튼 ON: 서비스 시작 및 알림 띄우기 명령
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        } else {
                            // 버튼 OFF: 포그라운드 알림 제거 및 중단 명령 (필요 시)
                            context.stopService(serviceIntent)
                        }
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

        // [하단 로고]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 0.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Image(
                painter = logoPainter,
                contentDescription = "Logo",
                modifier = Modifier.size(width = 140.dp, height = 50.dp)
            )
        }
    }
}