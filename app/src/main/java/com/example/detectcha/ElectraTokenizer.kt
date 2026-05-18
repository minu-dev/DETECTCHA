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

            val words = normalizedText
                .replace(Regex("([.,!?])"), " $1 ")
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }

            val tokens = mutableListOf<Int>()
            tokens.add(CLS_ID)

            for (word in words) {
                var start = 0
                val subTokens = mutableListOf<Int>()
                var isBad = false
                
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
                        isBad = true
                        break
                    } else {
                        subTokens.add(curMatchId)
                        start = end
                    }
                }

                if (isBad) {
                    tokens.add(UNK_ID)
                } else {
                    tokens.addAll(subTokens)
                }
                
                if (tokens.size >= MAX_LEN - 1) break
            }

            if (tokens.size < MAX_LEN) {
                tokens.add(SEP_ID)
            } else {
                tokens[MAX_LEN - 1] = SEP_ID
            }

            for (i in tokens.indices) {
                if (i < MAX_LEN) {
                    inputIds[i] = tokens[i]
                    attentionMask[i] = 1
                }
            }
        } catch (e: Exception) {
            Log.e("ElectraTokenizer", "Tokenizing error: ${e.message}")
        }

        return Triple(inputIds, attentionMask, tokenTypeIds)
    }
}
