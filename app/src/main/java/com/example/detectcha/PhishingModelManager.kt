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

    private val labelMap = mapOf(
        0 to "경고/협박", 1 to "답변 회피", 2 to "약속", 3 to "슬픔/괴로움", 
        4 to "명령", 5 to "사과", 6 to "기타 인사", 7 to "부름", 
        8 to "끝인사", 9 to "칭찬", 10 to "감탄", 11 to "비난/불평", 
        12 to "거절/부정", 13 to "감사", 14 to "선언", 15 to "첫인사", 
        16 to "기타 표출", 17 to "제안/요청", 18 to "수용/긍정", 
        19 to "확인 질문", 20 to "주장", 21 to "단순 질문", 22 to "단순 진술"
    )

    data class AnalysisResult(val probability: Float, val label: String)

    init {
        initModel()
    }

    private fun initModel() {
        try {
            val modelBuffer = loadModelFile(context, MODEL_NAME)
            val options = Interpreter.Options().apply { setNumThreads(4) }
            val interp = Interpreter(modelBuffer, options)
            
            for (i in 0 until interp.inputTensorCount) {
                val name = interp.getInputTensor(i).name()
                when {
                    name.contains("input_ids") -> inputIdsIdx = i
                    name.contains("attention_mask") -> attentionMaskIdx = i
                    name.contains("token_type_ids") -> tokenTypeIdsIdx = i
                }
            }
            interpreter = interp
            tokenizer = ElectraTokenizer(context)
            Log.d(TAG, "모델 및 토크나이저 초기화 완료")
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

    fun classifyText(text: String): AnalysisResult? {
        val interp = interpreter ?: return null

        try {
            val safeText = if (text.length > 200) text.take(200) else text
            val (inputIds, attentionMask, tokenTypeIds) = tokenizer.tokenize(safeText)

            val inputs = arrayOfNulls<Any>(3)
            inputs[attentionMaskIdx] = arrayOf(attentionMask)
            inputs[inputIdsIdx] = arrayOf(inputIds)
            inputs[tokenTypeIdsIdx] = arrayOf(tokenTypeIds)

            val outputArray = arrayOf(FloatArray(23)) 
            val outputs = mutableMapOf<Int, Any>(0 to outputArray)

            interp.runForMultipleInputsOutputs(inputs, outputs)

            val logits = outputArray[0]
            val maxLogit = logits.maxOrNull() ?: 0f
            val exps = logits.map { exp((it - maxLogit).toDouble()) }
            val sumExps = exps.sum()
            val probs = exps.map { (it / sumExps).toFloat() }

            val fullDistribution = probs.withIndex().joinToString(separator = ", ") {
                "${it.index}(${String.format("%.1f", it.value * 100)}%)" 
            }
            Log.v(TAG, "전체 라벨 분포: $fullDistribution")

            val riskProbability = (probs[0] + probs[4]).coerceIn(0f, 1f)

            val maxIdx = logits.indices.maxByOrNull { logits[it] } ?: 22
            val resultLabel = labelMap[maxIdx] ?: "알 수 없음"

            Log.d(TAG, "--- [분석 결과] ---")
            Log.d(TAG, "입력 문장: '$safeText'")
            Log.d(TAG, "의도 분류 결과: $resultLabel (${String.format("%.1f", probs[maxIdx] * 100)}%)")
            Log.d(TAG, "보이스피싱 위험도(0+4): ${String.format(Locale.US, "%.2f", riskProbability * 100)}%")

            return AnalysisResult(riskProbability, resultLabel)
        } catch (e: Exception) {
            Log.e(TAG, "추론 에러: ${e.message}")
            return null
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
