package com.example.detectcha

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

@SuppressLint("AccessibilityPolicy")
class TextCatcherService : AccessibilityService() {

    private val TAG = "TextCatcherService"

    // 문장 저장용 변수
    private val printedSentences = mutableSetOf<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!CatcherController.isCatching) return

        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.google.android.as") return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val sourceNode = event.source ?: return

            extractCaptionText(sourceNode)

            sourceNode.recycle()
        }
    }

    private fun extractCaptionText(node: AccessibilityNodeInfo?) {
        if (node == null) return

        val text = node.text?.toString()

        if (!text.isNullOrBlank()) {
            // 1차 문장 전처리 로직
            // 개행 문자 제거, 다중 공백 압축
            val cleanText = text.replace("Live Caption", "")
                .replace("\n", " ")
                .replace("\\s+".toRegex(), " ")
                .trim()

            if (cleanText.isNotEmpty()) {
                // 2차 문장 전처리 로직
                // .이나 ?나 ! 뒤에 공백이 오고, 그 다음 글자가 대문자 혹은 숫자인 곳을 기준으로 자름
                // lookahead/lookbehind를 사용해서 구분자 자체는 날아가지 않게 함
                val regex = "(?<=[.!?])\\s+(?=[A-Z0-9])".toRegex()
                val chunks = cleanText.split(regex)
                // 파싱한 조각 검사
                for (i in chunks.indices) {
                    val sentence = chunks[i].trim()
                    // 마지막 조각은 화자가 아직 말하고 있는 중이므로 무시
                    // 이전 조각은 완성된 문장
                    if (i < chunks.lastIndex) {
                        // 처음 보는 완성된 문장이라면 출력하고 저장
                        if (printedSentences.add(sentence)) {
                            Log.d(TAG, "[Caption]: $sentence")
                            // 저장 문장이 50개가 넘어가면 오래된 것부터 삭제
                            if (printedSentences.size > 50) {
                                printedSentences.clear()
                            }
                        }
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            extractCaptionText(node.getChild(i))
        }
    }

    override fun onInterrupt() { }
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "[Text Catcher Connected]")
    }
}

object CatcherController {
    var isCatching = false
}