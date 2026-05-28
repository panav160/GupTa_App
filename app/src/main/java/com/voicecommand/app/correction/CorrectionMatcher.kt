package com.voicecommand.app.correction

import kotlinx.coroutines.flow.first

class CorrectionMatcher(private val repository: CorrectionRepository) {

    suspend fun apply(input: String): String {
        if (input.isBlank()) return input

        val corrections = repository.all.first()
        if (corrections.isEmpty()) return input

        val trimmed = input.trim()

        for (correction in corrections) {
            val cr = correction.rawText.trim()

            if (trimmed == cr) {
                repository.incrementCount(correction.id)
                return correction.correctedText.trim()
            }

            if (trimmed.contains(cr, ignoreCase = true) ||
                cr.contains(trimmed, ignoreCase = true)
            ) {
                repository.incrementCount(correction.id)
                return correction.correctedText.trim()
            }
        }

        for (correction in corrections) {
            val cr = correction.rawText.trim()
            val dist = levenshtein(trimmed.lowercase(), cr.lowercase())
            val maxLen = maxOf(trimmed.length, cr.length)
            if (maxLen > 0 && dist.toFloat() / maxLen < 0.3f) {
                repository.incrementCount(correction.id)
                return correction.correctedText.trim()
            }
        }

        return input
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = i - 1
            dp[0] = i
            for (j in 1..s2.length) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (s1[i - 1] == s2[j - 1]) 0 else 1
                )
                prev = tmp
            }
        }
        return dp[s2.length]
    }
}
