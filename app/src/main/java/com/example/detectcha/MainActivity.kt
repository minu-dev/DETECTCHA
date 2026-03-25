package com.example.detectcha

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.PaintCompat
import android.view.TextureView
import android.widget.FrameLayout
import android.view.View
import android.graphics.Paint
import android.content.Context
import android.view.accessibility.AccessibilityManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isNotificationPermissionGranted()) {
            Toast.makeText(this, "알림 접근 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        setContent {
            DetectchaApp()
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        val packageName = packageName
        val sets = NotificationManagerCompat.getEnabledListenerPackages(this)
        return sets.contains(packageName)
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