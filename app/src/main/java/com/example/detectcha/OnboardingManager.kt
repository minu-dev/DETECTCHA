package com.example.detectcha

import android.content.Context

/**
 * 앱 최초 실행 여부(온보딩 완료 여부)를 관리하는 헬퍼.
 *
 * SharedPreferences 에 단일 boolean 플래그를 저장하여,
 * 사용자가 온보딩(튜토리얼)을 한 번 완료하면 다시 표시되지 않도록 한다.
 */
object OnboardingManager {
    private const val PREFS_NAME = "detectcha_onboarding"
    private const val KEY_COMPLETED = "onboarding_completed"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 아직 온보딩을 완료하지 않은 최초 실행 상태이면 true. */
    fun isFirstLaunch(context: Context): Boolean =
        !prefs(context).getBoolean(KEY_COMPLETED, false)

    /** 온보딩 완료 처리. 이후 [isFirstLaunch] 는 false 를 반환한다. */
    fun setCompleted(context: Context) {
        prefs(context).edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    /** (디버그용) 온보딩 상태 초기화 — 다음 실행 시 튜토리얼이 다시 표시된다. */
    fun reset(context: Context) {
        prefs(context).edit().putBoolean(KEY_COMPLETED, false).apply()
    }
}
