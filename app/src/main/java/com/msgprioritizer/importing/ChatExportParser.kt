package com.msgprioritizer.importing

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reads the .txt file WhatsApp creates with "Export chat".
 *
 * Handles both layouts:
 *   Android: 10/10/26, 9:41 pm - Mom: Call me
 *   iPhone:  [10/10/26, 9:41:22 PM] Mom: Call me
 * plus 24-hour times, day-first or month-first dates, and messages that run over several lines.
 *
 * Pure Kotlin with no Android imports, so it can be unit tested on a laptop.
 */
object ChatExportParser {
    data class ChatLine(val sender: String, val text: String, val timestamp: Long)

    private const val DATE = """(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4}),?\s+(\d{1,2})[:.](\d{2})(?:[:.](\d{2}))?\s*([AaPp]\.?\s?[Mm]\.?)?"""
    private val androidLine = Regex("""^$DATE\s+-\s+(.*)$""")
    private val iphoneLine = Regex("""^\[$DATE]\s+(.*)$""")

    /** Lines WhatsApp writes for media, deletions and edits. They carry no readable message. */
    private val skippedText = Regex(
        """^(<media omitted>|<attached:.*>|(image|video|audio|sticker|gif|document|contact card) omitted|this message was deleted|you deleted this message|null)$""",
        RegexOption.IGNORE_CASE,
    )
    private val editedSuffix = Regex("""\s*<this message was edited>$""", RegexOption.IGNORE_CASE)

    private class Raw(
        val a: Int, val b: Int, val year: Int,
        val hour: Int, val minute: Int, val second: Int, val ampm: String?,
        val rest: StringBuilder,
    )

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<ChatLine> {
        val raws = mutableListOf<Raw>()
        for (rawLine in text.lineSequence()) {
            val line = clean(rawLine)
            val m = androidLine.matchEntire(line) ?: iphoneLine.matchEntire(line)
            if (m == null) {
                // A message that runs over several lines: glue it onto the previous one.
                if (line.isNotBlank()) raws.lastOrNull()?.rest?.append('\n')?.append(line)
                continue
            }
            val g = m.groupValues
            raws += Raw(
                a = g[1].toInt(), b = g[2].toInt(), year = g[3].toInt().let { if (it < 100) 2000 + it else it },
                hour = g[4].toInt(), minute = g[5].toInt(), second = g[6].toIntOrNull() ?: 0,
                ampm = g[7].ifBlank { null }, rest = StringBuilder(g[8]),
            )
        }

        // Dates are 10/04 in one country and 04/10 in another. Use the whole file to decide.
        val dayFirst = when {
            raws.any { it.a > 12 } -> true
            raws.any { it.b > 12 } -> false
            else -> true
        }

        return raws.mapNotNull { r ->
            val rest = r.rest.toString()
            val colon = rest.indexOf(": ")
            if (colon <= 0) return@mapNotNull null // system line like "Messages are end-to-end encrypted"
            val sender = rest.substring(0, colon).trim()
            val body = rest.substring(colon + 2).replace(editedSuffix, "").trim()
            if (body.isEmpty() || skippedText.matches(body)) return@mapNotNull null

            val day = if (dayFirst) r.a else r.b
            val month = if (dayFirst) r.b else r.a
            val time = runCatching {
                LocalDateTime.of(r.year, month, day, to24h(r.hour, r.ampm), r.minute, r.second)
                    .atZone(zone).toInstant().toEpochMilli()
            }.getOrNull() ?: return@mapNotNull null
            ChatLine(sender, body, time)
        }
    }

    private fun to24h(hour: Int, ampm: String?): Int {
        if (ampm == null) return hour
        val pm = ampm.trim().startsWith("p", ignoreCase = true)
        return when {
            pm && hour < 12 -> hour + 12
            !pm && hour == 12 -> 0
            else -> hour
        }
    }

    /** WhatsApp adds invisible direction marks and narrow spaces that break matching. */
    private fun clean(line: String) = line
        .replace("\u200e", "").replace("\u200f", "").replace("\ufeff", "")
        .replace('\u202f', ' ').replace('\u00a0', ' ')
        .trimEnd()

    /** "WhatsApp Chat with Mom.txt" → "Mom". */
    fun chatNameFromFile(fileName: String?): String? {
        val name = fileName?.substringBeforeLast('.')?.trim() ?: return null
        return name
            .removePrefix("WhatsApp Chat with ")
            .removePrefix("WhatsApp Chat - ")
            .trim()
            .takeIf { it.isNotEmpty() && !it.equals("_chat", ignoreCase = true) }
    }
}
