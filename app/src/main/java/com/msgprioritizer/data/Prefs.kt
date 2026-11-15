package com.msgprioritizer.data

import android.content.Context
import androidx.core.content.edit
import com.msgprioritizer.scoring.ScoringConfig

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var myName: String
        get() = p.getString("my_name", "") ?: ""
        set(v) = p.edit { putString("my_name", v) }

    var vips: Set<String>
        get() = readLines("vips")
        set(v) = writeLines("vips", v)

    var muted: Set<String>
        get() = readLines("muted")
        set(v) = writeLines("muted", v)

    var alertHigh: Boolean
        get() = p.getBoolean("alert_high", true)
        set(v) = p.edit { putBoolean("alert_high", v) }

    fun toConfig() = ScoringConfig(myName, vips, muted)

    /** Suggestions the user said "No thanks" to (Suggestion.key), so they never come back. */
    var dismissedSuggestions: Set<String>
        get() = readLines("dismissed_suggestions")
        set(v) = writeLines("dismissed_suggestions", v)

    /**
     * Accuracy counts from the user's "Rank right?" answers, per level. Kept as plain counters so
     * they survive the 7-day message clean-up without keeping any message text.
     */
    fun ratingCount(level: String, right: Boolean): Int = p.getInt("rating_${level}_${if (right) "right" else "wrong"}", 0)

    fun addRating(level: String, right: Boolean, delta: Int) {
        val key = "rating_${level}_${if (right) "right" else "wrong"}"
        p.edit { putInt(key, (p.getInt(key, 0) + delta).coerceAtLeast(0)) }
    }

    fun resetRatings() = p.edit { p.all.keys.filter { it.startsWith("rating_") }.forEach { remove(it) } }

    private fun readLines(key: String) = parseLines(p.getString(key, "") ?: "")

    private fun writeLines(key: String, v: Set<String>) =
        p.edit { putString(key, v.joinToString("\n")) }

    companion object {
        fun parseLines(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
}
