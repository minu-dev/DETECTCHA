package com.example.detectcha.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// 디자인 토큰 (앱의 사이버틱한 다크 테마에 맞춘 시안 포인트 컬러)
private val AccentCyan = Color(0xFF00E5FF)
private val TitleWhite = Color(0xFFF5F7FA)
private val BodyGray = Color(0xFFB5BECE)
private val SubGray = Color(0xFF8A93A6)
private val CardBg = Color(0xFF141A28)
private val DotInactive = Color(0xFF2A3346)

/** 온보딩 한 페이지를 표현하는 데이터 모델. */
private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val bullets: List<Pair<String, String>> = emptyList()
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Filled.Warning,
        title = "보이스피싱,\n이제 AI가 막아드립니다",
        description = "DETECTCHA는 통화 속 보이스피싱 위험을 실시간으로 감지해 즉시 경고하는 온디바이스 AI 보안 앱입니다."
    ),
    OnboardingPage(
        icon = Icons.Filled.Lock,
        title = "내 통화는\n내 기기 안에서만",
        description = "모든 분석은 서버로 전송되지 않고 기기 내부에서만 처리됩니다. 통화 내용이 외부로 나가지 않아 프라이버시가 안전하게 보호됩니다."
    ),
    OnboardingPage(
        icon = Icons.Filled.Settings,
        title = "딱 3가지 권한이 필요해요",
        description = "실시간 감지를 위해 아래 권한을 허용해 주세요. 다음 화면에서 순서대로 설정할 수 있습니다.",
        bullets = listOf(
            "알림 권한" to "위험이 감지되면 헤드업 알림과 경고음·진동으로 즉시 알려드립니다.",
            "사용 정보 접근" to "금융·송금 앱 실행을 감지해 한 번 더 보호하는 2차 경고를 제공합니다.",
            "접근성 권한" to "실시간 자막(Live Caption) 텍스트를 읽어 통화 내용을 분석합니다."
        )
    ),
    OnboardingPage(
        icon = Icons.Filled.PlayArrow,
        title = "준비 끝!\n이제 보호를 시작하세요",
        description = "메인 화면의 버튼을 켜면 감시가 시작됩니다. 위험이 감지되면 즉시 경고하고 탐지 내역에 기록하며, 송금 앱 실행 시 한 번 더 보호합니다."
    )
)

/**
 * 앱 최초 실행 시 노출되는 온보딩(튜토리얼) 화면.
 *
 * @param onFinish 마지막 페이지에서 '시작하기' 또는 '건너뛰기' 를 눌렀을 때 호출된다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0E1A), Color(0xFF0B1020), Color(0xFF050608))
                )
            )
    ) {
        // 상단 우측 '건너뛰기' (마지막 페이지에서는 숨김)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            AnimatedVisibility(visible = !isLastPage, modifier = Modifier.align(Alignment.TopEnd)) {
                TextButton(onClick = onFinish) {
                    Text(text = "건너뛰기", color = SubGray, fontSize = 14.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                OnboardingPageContent(onboardingPages[page])
            }

            // 페이지 인디케이터
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                repeat(onboardingPages.size) { index ->
                    val selected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 24.dp else 8.dp,
                        animationSpec = tween(300),
                        label = "DotWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(if (selected) AccentCyan else DotInactive)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = Color(0xFF051018)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = if (isLastPage) "시작하기" else "다음",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = page.title,
            color = TitleWhite,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            color = BodyGray,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp
        )

        if (page.bullets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                page.bullets.forEach { (heading, detail) ->
                    PermissionRow(heading = heading, detail = detail)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(heading: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = heading, color = TitleWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = detail, color = SubGray, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
