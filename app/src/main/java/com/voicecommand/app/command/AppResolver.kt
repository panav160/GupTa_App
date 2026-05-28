package com.voicecommand.app.command

import com.voicecommand.app.VoiceCommandApp

object AppResolver {

    /** Returns (packageName, appName) for the best matching app, or null if no confident match. */
    fun findBestMatch(spokenName: String): Pair<String, String>? {
        val apps = VoiceCommandApp.instance.installedApps
        if (apps.isEmpty()) return null

        val query = spokenName.lowercase().trim()

        // 1. Exact match
        apps.firstOrNull { it.appName.lowercase() == query }
            ?.let { return it.packageName to it.appName }

        // 2. App name fully contains spoken, or spoken fully contains app name
        apps.firstOrNull { it.appName.lowercase().contains(query) }
            ?.let { return it.packageName to it.appName }
        apps.firstOrNull { query.contains(it.appName.lowercase()) }
            ?.let { return it.packageName to it.appName }

        // 3. Every word in query starts a word in the app name
        //    e.g. "goo map" → "Google Maps"
        val queryWords = query.split(" ").filter { it.isNotBlank() }
        apps.firstOrNull { app ->
            val appWords = app.appName.lowercase().split(" ")
            queryWords.all { qw -> appWords.any { aw -> aw.startsWith(qw) } }
        }?.let { return it.packageName to it.appName }

        // 4. Levenshtein similarity — best match above threshold
        val scored = apps.map { app ->
            app to similarity(app.appName.lowercase(), query)
        }
        val best = scored.maxByOrNull { it.second }
        if (best != null && best.second >= 0.45f) {
            return best.first.packageName to best.first.appName
        }

        return null
    }

    private fun similarity(a: String, b: String): Float {
        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        return if (maxLen == 0) 1f else 1f - dist.toFloat() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else minOf(dp[i - 1][j - 1], dp[i - 1][j], dp[i][j - 1]) + 1
            }
        }
        return dp[a.length][b.length]
    }
}
