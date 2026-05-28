package com.voicecommand.app.command

import android.content.Context
import android.provider.ContactsContract
import kotlin.math.abs

object ContactResolver {

    fun getAllContactNames(context: Context): List<String> {
        return try {
            val uri = ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
            val names = mutableListOf<String>()
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.trim()?.let { if (it.isNotBlank()) names.add(it.lowercase()) }
                }
            }
            android.util.Log.d("ContactResolver", "Loaded ${names.size} contact names: ${names.take(10)}")
            names
        } catch (e: SecurityException) {
            android.util.Log.w("ContactResolver", "No READ_CONTACTS permission")
            emptyList()
        }
    }

    fun findClosestContactName(input: String, contactNames: List<String>): String? {
        val normalized = input.trim().lowercase()
        if (normalized.isBlank() || contactNames.isEmpty()) return null

        val exact = contactNames.firstOrNull { it == normalized }
        if (exact != null) return exact

        val contains = contactNames.firstOrNull { it.contains(normalized) || normalized.contains(it) }
        if (contains != null) return contains

        var best: String? = null
        var bestDist = Int.MAX_VALUE
        for (name in contactNames) {
            val dist = levenshtein(normalized, name)
            if (dist < bestDist) {
                bestDist = dist
                best = name
            }
        }
        val maxLen = maxOf(normalized.length, best?.length ?: 1)
        return if (best != null && bestDist.toFloat() / maxLen < 0.5f) best else null
    }

    fun findNumber(context: Context, name: String): String? {
        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val cursor = context.contentResolver.query(uri, projection, selection, arrayOf("%$name%"), null)
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(0).replace(Regex("[^\\d+]"), "")
                } else null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    fun extractNameAndMessage(text: String): Pair<String, String> {
        val firstSpace = text.indexOf(' ')
        return if (firstSpace > 0) {
            Pair(text.substring(0, firstSpace), text.substring(firstSpace + 1))
        } else {
            Pair(text, "")
        }
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = i - 1
            dp[0] = i
            for (j in 1..s2.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (s1[i - 1] == s2[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[s2.length]
    }
}
