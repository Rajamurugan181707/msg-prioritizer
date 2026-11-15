package com.msgprioritizer.data

import android.content.Context
import com.msgprioritizer.scoring.Level

/** The user's "Rank right?" answers, turned into accuracy figures for the project's evaluation. */
object Feedback {
    suspend fun rate(context: Context, m: Message, right: Boolean) {
        val prefs = Prefs(context)
        m.feedback?.let { old -> prefs.addRating(m.level, old == 1, -1) } // changing an earlier answer
        prefs.addRating(m.level, right, +1)
        AppDatabase.get(context).messages().setFeedback(m.id, if (right) 1 else -1)
    }

    data class Row(val level: String, val right: Int, val wrong: Int) {
        val total get() = right + wrong
        val percent get() = if (total == 0) 0 else Math.round(right * 100f / total)
    }

    fun summary(prefs: Prefs): List<Row> =
        Level.entries.map { Row(it.name, prefs.ratingCount(it.name, true), prefs.ratingCount(it.name, false)) }

    /** Plain CSV-style text to paste into a report. */
    fun export(prefs: Prefs): String {
        val rows = summary(prefs)
        val right = rows.sumOf { it.right }
        val total = rows.sumOf { it.total }
        return buildString {
            appendLine("Msg Prioritizer: accuracy from user ratings")
            appendLine("Level,Rated right,Rated wrong,Accuracy")
            rows.forEach { appendLine("${it.level},${it.right},${it.wrong},${it.percent}%") }
            append("All,$right,${total - right},${if (total == 0) 0 else Math.round(right * 100f / total)}%")
        }
    }
}
