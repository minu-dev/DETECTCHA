<div align="center">

# 🛡️ DETECTCHA

### Smart On-device Security (S.O.S)
**온디바이스 AI를 활용한 프라이버시 보호형 금융 사기 방지 엣지 컴퓨팅 솔루션**

*Privacy-Preserving Edge Computing Solution for Financial Fraud Prevention Using On-Device AI*

<br/>

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)
![targetSdk](https://img.shields.io/badge/targetSdk-35-blue)
![Language](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/Jetpack%20Compose-2024.02-4285F4?logo=jetpackcompose&logoColor=white)
![AI](https://img.shields.io/badge/TensorFlow%20Lite-On--Device-FF6F00?logo=tensorflow&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-green)

🏆 **2026 파란학기제 이노베이터상 수상작 (Innovator Award)**

</div>

---

## 📖 Overview

**DETECTCHA**는 통화 중 발생하는 **보이스피싱(Voice Phishing)** 위험을 실시간으로 감지하여 사용자에게 즉시 경고하는 모바일 보안 솔루션입니다.

안드로이드의 **실시간 자막(Live Caption)** 기능으로 통화 내용을 텍스트로 받아오고, **기기 내부(On-Device)** 에 탑재된 경량 AI 모델이 이를 분석합니다. 모든 분석이 단말기 안에서 이루어지는 **엣지 컴퓨팅(Edge Computing)** 방식이므로, 통화 내용이 외부 서버로 전송되지 않아 **사용자의 프라이버시를 완벽하게 보호**합니다.

> 💡 통화 내용은 어디로도 전송되지 않습니다. 분석도, 판단도, 경고도 — 전부 당신의 휴대폰 안에서.

---

## ✨ Key Features

| 기능 | 설명 |
| :--- | :--- |
| 🎙️ **실시간 통화 분석** | 접근성 서비스로 실시간 자막 텍스트를 수집해 새로 등장한 발화만 추려 분석합니다. |
| 🧠 **온디바이스 AI 탐지** | ELECTRA 기반 경량 TFLite 모델이 문맥을 분석하여 보이스피싱 확률을 산출합니다. |
| 🚨 **즉각 경고** | 위험 감지 시 헤드업 알림 · 경고음 · 진동으로 즉시 사용자에게 알립니다. |
| 💸 **2차 송금 보호** | 의심 정황 중 금융·송금 앱(토스, KB국민은행, 네이버페이, 카카오뱅크) 실행을 감지해 한 번 더 경고합니다. |
| 📝 **탐지 내역 기록** | 탐지된 의심 문장과 위험도를 Room 데이터베이스에 저장하여 히스토리로 제공합니다. |
| 🔒 **프라이버시 우선** | 서버 전송 없이 전 과정을 기기 내부에서 처리합니다. |
| 👋 **온보딩 튜토리얼** | 최초 실행 시 앱 사용법과 권한 설정을 단계별로 안내합니다. |

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                         DETECTCHA App                          │
│                                                                │
│   ┌─────────────────┐      ┌──────────────────────────────┐   │
│   │  Live Caption    │ ───► │  TextCatcherService          │   │
│   │ (com.google.     │      │  (AccessibilityService)      │   │
│   │  android.as)     │      │  · 실시간 텍스트 수집/증분 추출 │   │
│   └─────────────────┘      └───────────────┬──────────────┘   │
│                                             │                  │
│                                             ▼                  │
│                            ┌────────────────────────────────┐ │
│                            │  PhishingModelManager           │ │
│                            │  · ElectraTokenizer (vocab)     │ │
│                            │  · TFLite ELECTRA 추론           │ │
│                            └───────────────┬────────────────┘ │
│                                            │ 위험 확률          │
│              ┌─────────────────────────────┼──────────────┐    │
│              ▼                             ▼              ▼    │
│   ┌──────────────────┐   ┌──────────────────┐  ┌────────────┐ │
│   │ 즉각 경고          │   │ Room DB 기록      │  │ 송금앱 감지 │ │
│   │ (알림/소리/진동)    │   │ (PhishingHistory) │  │ 2차 경고   │ │
│   └──────────────────┘   └──────────────────┘  └────────────┘ │
│                                                                │
│   UI: Jetpack Compose (MainActivity · Onboarding · History)    │
└──────────────────────────────────────────────────────────────┘
```

### Core Logic
1. **Live Capture** — 접근성 서비스가 실시간 자막 서비스의 스트리밍 텍스트를 가로채고, 이전 텍스트와 비교해 새로 추가된 발화만 추출합니다.
2. **Lightweight AI** — 추출된 문장을 ELECTRA 토크나이저로 토큰화한 뒤, 모바일 환경에 최적화된 TFLite 모델로 보이스피싱 확률을 추론합니다.
3. **Instant Alert** — 위험도가 임계값을 넘으면 즉시 시청각 경고를 발생시키고, 송금 앱 실행이 함께 감지되면 2차 경고로 강화합니다.

---

## 🧰 Tech Stack

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose (Material 3, Window Size Class)
- **AI/ML**: TensorFlow Lite · TFLite Support (ELECTRA 기반 텍스트 분류 모델)
- **Local DB**: Room (KSP)
- **Media**: AndroidX Media3 (ExoPlayer)
- **System**: AccessibilityService · NotificationListenerService · Foreground Service · UsageStatsManager
- **Min/Target SDK**: 24 / 35

---

## 📲 User Guide (사용자 가이드)

### 1. 앱 설치

#### 방법 A — 배포용 APK 설치 (일반 사용자)
1. 릴리스로 제공된 `app-release.apk` 파일을 휴대폰으로 옮깁니다.
2. 파일을 실행하면 *"출처를 알 수 없는 앱"* 설치 안내가 나타날 수 있습니다.
   - **설정 → 보안 → 출처를 알 수 없는 앱 설치** 에서 해당 파일 관리자/브라우저의 설치를 허용해 주세요.
3. 안내에 따라 설치를 완료합니다.

#### 방법 B — 소스코드 빌드 (개발자)
```bash
# 1. 저장소 클론
git clone https://github.com/minu-dev/DETECTCHA.git

# 2. Android Studio 로 프로젝트 열기 (Giraffe 이상 권장)
# 3. Gradle Sync 후 디버그 빌드/설치
./gradlew installDebug
```

### 2. 최초 실행 및 권한 설정

앱을 처음 실행하면 **온보딩 튜토리얼**이 사용법과 권한을 단계별로 안내합니다. 원활한 실시간 감지를 위해 아래 **3가지 권한**을 허용해 주세요.

| 권한 | 목적 | 설정 위치 |
| :--- | :--- | :--- |
| **① 알림 권한** | 위험 감지 시 경고 알림·소리·진동 전달 | 최초 실행 시 팝업에서 *허용* |
| **② 사용 정보 접근** | 금융·송금 앱 실행 감지(2차 경고) | 설정 → 앱 → 특별한 접근 → 사용 정보 접근 → **DETECTCHA 허용** |
| **③ 접근성 권한** | 실시간 자막 텍스트 수집·분석 | 설정 → 접근성 → 설치된 앱 → **DETECTCHA → 사용 켜기** |

> 앱이 각 단계에서 안내 토스트와 함께 해당 설정 화면을 자동으로 열어줍니다.

### 3. 실시간 자막(Live Caption) 켜기
DETECTCHA는 안드로이드 **실시간 자막** 기능이 변환한 통화 텍스트를 분석합니다.
- **설정 → 접근성 → 실시간 자막**(또는 음량 버튼 → 자막 아이콘)에서 기능을 켜 주세요.
- 기기 제조사에 따라 메뉴 명칭이 다를 수 있습니다. *(삼성: "실시간 자막", 구글 픽셀: "Live Caption")*

### 4. 보호 시작하기
1. 메인 화면 중앙의 **전원 버튼**을 누릅니다.
2. 접근성 권한이 켜져 있으면 감시가 시작되고, 상단 상태바에 포그라운드 알림이 표시됩니다.
3. 통화 중 보이스피싱이 의심되면 **즉시 경고**가 울리고, 탐지 내역은 우측 상단 **메뉴(≡)** 에서 확인할 수 있습니다.

> ⚠️ 본 앱은 보이스피싱 **예방 보조 도구**이며, 100% 탐지를 보장하지 않습니다. 의심스러운 통화는 끊고 해당 기관 대표번호로 직접 확인하세요.

---

## 📜 Open Source Licenses

본 프로젝트는 다음 오픈소스 라이브러리를 사용하며, 각 라이브러리의 라이선스를 준수합니다.

| 라이브러리 | 용도 | 라이선스 |
| :--- | :--- | :--- |
| Kotlin / kotlinx-coroutines | 언어 · 비동기 처리 | Apache License 2.0 |
| AndroidX (Core, AppCompat, Activity, Lifecycle, ConstraintLayout) | 안드로이드 표준 컴포넌트 | Apache License 2.0 |
| Jetpack Compose · Material 3 · Window Size Class | 선언형 UI | Apache License 2.0 |
| AndroidX Room | 로컬 데이터베이스 | Apache License 2.0 |
| AndroidX Media3 (ExoPlayer) | 미디어 재생 | Apache License 2.0 |
| Google Android Material Components | UI 컴포넌트 | Apache License 2.0 |
| TensorFlow Lite · TFLite Support | 온디바이스 AI 추론 | Apache License 2.0 |
| JUnit 4 | 단위 테스트 | Eclipse Public License 1.0 |
| AndroidX Test · Espresso | 계측 테스트 | Apache License 2.0 |

**AI 모델**: 온디바이스 추론 모델은 사전 학습된 한국어 ELECTRA 계열 언어 모델을 보이스피싱 탐지용으로 파인튜닝하여 사용합니다. *(베이스 모델: `[※ 실제 사용한 사전학습 모델명/출처와 해당 라이선스를 기입해 주세요 — 예: monologg/koelectra-base-v3, Apache 2.0]`)*

> 전체 라이선스 원문은 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 및 [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html) 을 참고하세요.

---

## 📂 Project Structure

```
app/src/main/
├── java/com/example/detectcha/
│   ├── MainActivity.kt              # 메인 화면(Compose) · 권한 흐름 · 온보딩 연동
│   ├── OnboardingManager.kt         # 최초 실행 여부(온보딩 완료) 저장
│   ├── TextCatcherService.kt        # 접근성 기반 실시간 텍스트 수집 · 탐지 · 경고
│   ├── NotificationCatcherService.kt# 알림 리스너 서비스
│   ├── PhishingModelManager.kt      # TFLite 모델 로드 및 추론
│   ├── ElectraTokenizer.kt          # ELECTRA 토크나이저
│   ├── data/                        # Room: AppDatabase · PhishingHistory · DAO
│   └── ui/
│       ├── OnboardingScreen.kt      # 온보딩 튜토리얼(HorizontalPager)
│       ├── PhishingTestScreen.kt    # 모델 테스트 화면
│       └── PhishingHistoryViewModel.kt / PhishingTestViewModel.kt
├── assets/                          # tflite 모델 · vocab · tokenizer 설정
└── res/                             # 리소스(레이아웃, 드로어블, raw 등)
```

---

## 👥 Team S.O.S

소프트웨어학과 파란학기제 프로젝트 팀 **S.O.S (Smart On-device Security)**

| 역할 | 담당 |
| :--- | :--- |
| Android 앱 개발 | 백민우 |
| AI 모델 개발 | 손예원 |

---

<div align="center">

**🏆 2026 파란학기제 이노베이터상 수상 🏆**

Made with 💙 by Team S.O.S

</div>
