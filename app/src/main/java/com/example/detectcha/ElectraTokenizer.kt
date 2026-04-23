package com.example.detectcha

import android.content.Context
import android.util.Log
import java.text.Normalizer

class ElectraTokenizer(context: Context) {
    private val vocab = mutableMapOf<String, Int>()
    private val MAX_LEN = 128

    private val PAD_ID = 0
    private val UNK_ID = 1
    private val CLS_ID = 2
    private val SEP_ID = 3

    init {
        try {
            context.assets.open("vocab.txt").bufferedReader().use { reader ->
                reader.readLines().forEachIndexed { index, line ->
                    val token = Normalizer.normalize(line.trim(), Normalizer.Form.NFC)
                    if (token.isNotEmpty()) {
                        vocab[token] = index
                    }
                }
            }
            Log.d("ElectraTokenizer", "Vocab 로딩 완료: ${vocab.size}")
        } catch (e: Exception) {
            Log.e("ElectraTokenizer", "Vocab 로딩 실패", e)
        }
    }

    fun tokenize(text: String): Triple<IntArray, IntArray, IntArray> {
        val inputIds = IntArray(MAX_LEN) { PAD_ID }
        val attentionMask = IntArray(MAX_LEN) { 0 }
        val tokenTypeIds = IntArray(MAX_LEN) { 0 }

        try {
            val normalizedText = Normalizer.normalize(text, Normalizer.Form.NFC)

            val words = normalizedText.split(Regex("\\s+")).filter { it.isNotEmpty() }

            val tokens = mutableListOf<Int>()
            tokens.add(CLS_ID)

            for (word in words) {
                if (tokens.size >= MAX_LEN - 1) break
                
                var start = 0
                val subTokens = mutableListOf<Int>()
                
                while (start < word.length) {
                    var end = word.length
                    var curMatchId = -1
                    
                    while (start < end) {
                        var substr = word.substring(start, end)
                        if (start > 0) substr = "##$substr"
                        
                        if (vocab.containsKey(substr)) {
                            curMatchId = vocab[substr]!!
                            break
                        }
                        end--
                    }
                    
                    if (curMatchId == -1) {
                        subTokens.add(UNK_ID)
                        start++
                    } else {
                        subTokens.add(curMatchId)
                        start = end
                    }
                }
                tokens.addAll(subTokens)
            }

            val finalTokens = if (tokens.size > MAX_LEN - 1) tokens.subList(0, MAX_LEN - 1) else tokens
            val result = finalTokens.toMutableList()
            result.add(SEP_ID)

            Log.d("ElectraTokenizer", "Text: $text")
            Log.d("ElectraTokenizer", "Tokens: ${result.joinToString(", ")}")

            for (i in result.indices) {
                if (i < MAX_LEN) {
                    inputIds[i] = result[i]
                    attentionMask[i] = 1
                }
            }
        } catch (e: Exception) {
            Log.e("ElectraTokenizer", "Tokenizing error: ${e.message}")
        }

        return Triple(inputIds, attentionMask, tokenTypeIds)
    }
}
