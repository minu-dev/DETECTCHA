package com.example.detectcha

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.math.exp

class PhishingModelManager(private val context: Context) {

    private val TAG = "PhishingModelManager"
    private val MODEL_NAME = "voicephishing_checkpoint_1600_fp16.tflite"

    private var interpreter: Interpreter? = null
    private lateinit var tokenizer: ElectraTokenizer
    
    private var inputIdsIdx = 1
    private var attentionMaskIdx = 0
    private var tokenTypeIdsIdx = 2

    private val authRegex = Regex("인증번호|OTP|보안키|계좌 비밀번호|주민등록|주민번호|신분증|본인 확인|개인 정보")
    private val moneyRegex = Regex("이체|송금|입금|현금|인출|잔액|전 재산|안전 계좌|예치|계좌|금액|결제")
    private val appLinkRegex = Regex("앱|설치|링크|클릭|URL|주소")
    private val authorityRegex = Regex("검찰|경찰|금융감독원|금감원|수사|피의자|공범|범죄|조사|법적 절차")
    private val urgencyRegex = Regex("지금|즉시|바로|급하|오늘|절대|전화 끊지|조용한 곳|주변에 사람")

    private val labelMap = mapOf(
        0 to "주장", 1 to "제안/요청", 2 to "첫인사", 3 to "확인 질문",
        4 to "거절/부정", 5 to "단순 진술", 6 to "사과", 7 to "선언",
        8 to "수용/긍정", 9 to "단순 질문", 10 to "끝인사", 11 to "감사",
        12 to "기타 표출", 13 to "비난/불평", 14 to "약속", 15 to "기타 인사",
        16 to "답변 회피", 17 to "감탄", 18 to "칭찬", 19 to "부름",
        20 to "슬픔/괴로움", 21 to "경고/협박", 22 to "명령"
    )

    private val PRIMARY_RISK_LABELS = setOf("경고/협박", "답변 회피", "비난/불평")
    private val DIRECTIVE_RISK_LABELS = setOf("명령", "제안/요청")

    data class AnalysisResult(
        val isFraudSuspected: Boolean,
        val topLabel: String,
        val topProbability: Float
    )

    data class DetailedAnalysisResult(
        val text: String,
        val fullDistribution: List<Pair<String, Float>>,
        val top3: List<Pair<String, Float>>,
        val primaryRiskProb: Float,
        val directiveRiskProb: Float,
        val signalCount: Int,
        val patterns: Map<String, Int>,
        val finalDecision: Boolean
    )

    init {
        initModel()
    }

    private fun initModel() {
        try {
            val modelBuffer = loadModelFile(context, MODEL_NAME)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val interp = Interpreter(modelBuffer, options)

            for (i in 0 until interp.inputTensorCount) {
                val name = interp.getInputTensor(i).name().lowercase(Locale.ROOT)
                when {
                    name.contains("input_ids") -> inputIdsIdx = i
                    name.contains("attention_mask") -> attentionMaskIdx = i
                    name.contains("token_type_ids") -> tokenTypeIdsIdx = i
                }
            }

            interpreter = interp
            tokenizer = ElectraTokenizer(context)
            Log.d(TAG, "모델 초기화 완료 (CPU 모드)")
        } catch (e: Exception) {
            Log.e(TAG, "모델 초기화 실패: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // 4월 23일 방식대로 Mutex 없이 일반 함수로 사용하여 레이턴시 최소화
    fun classifyDetailed(text: String): DetailedAnalysisResult? {
        val interp = interpreter ?: return null

        // 실시간 누적 텍스트 부하 방지를 위해 최신 300자만 사용
        val safeText = if (text.length > 300) text.takeLast(300) else text

        try {
            val (inputIds, attentionMask, tokenTypeIds) = tokenizer.tokenize(safeText)

            // 여러 입력 노드에 데이터를 병렬로 주입
            val inputs = arrayOfNulls<Any>(3)
            inputs[inputIdsIdx] = arrayOf(inputIds)
            inputs[attentionMaskIdx] = arrayOf(attentionMask)
            inputs[tokenTypeIdsIdx] = arrayOf(tokenTypeIds)

            val outputArray = arrayOf(FloatArray(23))
            val outputs = mutableMapOf<Int, Any>(0 to outputArray)

            interp.runForMultipleInputsOutputs(inputs, outputs)

            val logits = outputArray[0]
            val probs = softmax(logits)
            val fullDist = probs.withIndex().map { (labelMap[it.index] ?: "Unknown") to it.value }
            val top3 = fullDist.sortedByDescending { it.second }.take(3)

            var primary_risk_prob = 0f
            var directive_risk_prob = 0f
            for (i in probs.indices) {
                val label = labelMap[i] ?: ""
                if (PRIMARY_RISK_LABELS.contains(label)) primary_risk_prob += probs[i]
                if (DIRECTIVE_RISK_LABELS.contains(label)) directive_risk_prob += probs[i]
            }

            // 시그널 카운트 및 판정 로직
            val patterns = detectPatterns(safeText)
            val signalCount = patterns.auth + patterns.money + patterns.app_link + patterns.authority + patterns.urgency
            
            val isFraud = primary_risk_prob >= 0.45f || (directive_risk_prob >= 0.65f && signalCount >= 1) || signalCount >= 1

            Log.d(TAG, "==========================================")
            Log.d(TAG, "[분석 텍스트]: $safeText")
            Log.d(TAG, "[최종 판단]: ${if (isFraud) "🚨 보이스피싱 의심" else "✅ 정상"}")
            Log.d(TAG, "==========================================")

            return DetailedAnalysisResult(
                text = safeText,
                fullDistribution = fullDist,
                top3 = top3,
                primaryRiskProb = primary_risk_prob,
                directiveRiskProb = directive_risk_prob,
                signalCount = signalCount,
                patterns = mapOf(
                    "인증/정보" to patterns.auth,
                    "금전/이체" to patterns.money,
                    "앱/링크" to patterns.app_link,
                    "기관/수사" to patterns.authority,
                    "긴박/독촉" to patterns.urgency
                ),
                finalDecision = isFraud
            )
        } catch (e: Exception) {
            Log.e(TAG, "추론 실패: ${e.message}")
            return null
        }
    }

    fun classifyText(text: String): AnalysisResult? {
        val result = classifyDetailed(text) ?: return null
        return AnalysisResult(result.finalDecision, result.top3[0].first, result.top3[0].second)
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp((it - maxLogit).toDouble()) }
        val sumExps = exps.sum()
        return exps.map { (it / sumExps).toFloat() }.toFloatArray()
    }

    private data class BasePatterns(val auth: Int, val money: Int, val app_link: Int, val authority: Int, val urgency: Int)

    private fun detectPatterns(text: String): BasePatterns {
        return BasePatterns(
            auth = if (text.contains(authRegex)) 1 else 0,
            money = if (text.contains(moneyRegex)) 1 else 0,
            app_link = if (text.contains(appLinkRegex)) 1 else 0,
            authority = if (text.contains(authorityRegex)) 1 else 0,
            urgency = if (text.contains(urgencyRegex)) 1 else 0
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
