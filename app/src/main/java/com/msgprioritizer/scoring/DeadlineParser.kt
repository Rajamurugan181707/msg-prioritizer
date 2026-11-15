package com.msgprioritizer.scoring

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.text.RegexOption.IGNORE_CASE

/** A due moment found in a message. [hasTime] is false for "due tomorrow" (no clock time given). */
data class Deadline(val at: ZonedDateTime, val hasTime: Boolean)

/**
 * Finds when something is due in a message: "assignment due tomorrow 5pm", "fees kattanum inniku",
 * "viva on Monday", "submit by 25/11", "நாளை தேர்வு".
 *
 * A date alone is not a deadline ("I reached home today"), so a message also needs a due-word:
 * due, by, before, submit, exam, fees, meeting, starts…
 *
 * Pure Kotlin with no Android imports, so it can be unit tested on a laptop.
 */
object DeadlineParser {
    private const val MAX_DAYS_AHEAD = 60L

    private val cue = Regex(
        """\b(due|deadline|before|by|until|till|last\s?date|submit\w*|submission|exams?|tests?|viva|quiz|internals|mid\s?-?sem|end\s?-?sem|""" +
            """review|presentation|ppt|seminar|assignments?|records?|fees?|pay(ment)?|meeting|class|lab|interview|register|registration|""" +
            """starts?|ends?|deadline|hall\s?ticket|kattanum|kodukkanum|kodukanum|pannanum|venum)\b|தேர்வு|கட்டணம்|கடைசி""",
        IGNORE_CASE,
    )

    private val dayAfterTomorrow = Regex("""\bday after tomorrow\b|\bnaa?lann?[ae]?i?k+u\b""", IGNORE_CASE)
    private val tomorrow = Regex("""\b(tomorrow|tmrw|tmr|naa?laik*u|naalai)\b|நாளை""", IGNORE_CASE)
    private val todayWords = Regex("""\b(today|tonight|inn?a?i?k+u|indru|this (morning|afternoon|evening))\b|இன்று|இன்னைக்கு""", IGNORE_CASE)
    private val weekday = Regex("""\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", IGNORE_CASE)
    private val numericDate = Regex("""\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""")
    private const val MONTHS = "jan(uary)?|feb(ruary)?|mar(ch)?|apr(il)?|may|june?|july?|aug(ust)?|sep(t(ember)?)?|oct(ober)?|nov(ember)?|dec(ember)?"
    private val dayMonth = Regex("""\b(\d{1,2})(st|nd|rd|th)?\s+($MONTHS)\b""", IGNORE_CASE)
    private val monthDay = Regex("""\b($MONTHS)\s+(\d{1,2})(st|nd|rd|th)?\b""", IGNORE_CASE)
    private val clock12 = Regex("""\b(\d{1,2})(?:[:.](\d{2}))?\s?(am|pm)\b""", IGNORE_CASE)
    private val clock24 = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")

    fun find(text: String, now: ZonedDateTime): Deadline? {
        if (!cue.containsMatchIn(text)) return null
        val time = clockTime(text)
        val date = date(text, now.toLocalDate())
        if (date == null && time == null) return null

        var day = date ?: now.toLocalDate()
        // "before 5pm" said at 6pm means tomorrow
        if (date == null && time != null && now.toLocalTime() > time) day = day.plusDays(1)
        val at = day.atTime(time ?: LocalTime.of(23, 59)).atZone(now.zone)

        if (at.isBefore(now.minusHours(1)) || at.isAfter(now.plusDays(MAX_DAYS_AHEAD))) return null
        return Deadline(at, time != null)
    }

    private fun clockTime(text: String): LocalTime? {
        clock12.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h !in 1..12 || min > 59) return null
            val pm = m.groupValues[3].startsWith("p", ignoreCase = true)
            val hour = when {
                pm && h < 12 -> h + 12
                !pm && h == 12 -> 0
                else -> h
            }
            return LocalTime.of(hour, min)
        }
        clock24.find(text)?.let { m -> return LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        return null
    }

    private fun date(text: String, today: LocalDate): LocalDate? {
        if (dayAfterTomorrow.containsMatchIn(text)) return today.plusDays(2)
        if (tomorrow.containsMatchIn(text)) return today.plusDays(1)
        if (todayWords.containsMatchIn(text)) return today
        weekday.find(text)?.let { m ->
            val target = DayOfWeek.valueOf(m.value.uppercase())
            val ahead = (target.value - today.dayOfWeek.value + 7) % 7
            return today.plusDays(ahead.toLong())
        }
        numericDate.find(text)?.let { m ->
            // India writes day first: 25/11 is 25 November
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            return safeDate(y, mo, d, today)
        }
        dayMonth.find(text)?.let { m -> return safeDate(null, month(m.groupValues[3]), m.groupValues[1].toInt(), today) }
        monthDay.find(text)?.let { m -> return safeDate(null, month(m.groupValues[1]), m.groupValues[2].toInt(), today) }
        return null
    }

    private fun month(name: String): Int =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            .indexOf(name.take(3).lowercase()) + 1

    /** A date without a year is this year's, or next year's if it has already passed. */
    private fun safeDate(year: Int?, month: Int, day: Int, today: LocalDate): LocalDate? = runCatching {
        if (year != null) return@runCatching LocalDate.of(year, month, day)
        val d = LocalDate.of(today.year, month, day)
        if (d.isBefore(today)) d.plusYears(1) else d
    }.getOrNull()

    /** "today 5:00 pm", "tomorrow", "Mon 24 Nov" */
    fun describe(d: Deadline, now: ZonedDateTime): String {
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), d.at.toLocalDate())
        val day = when (days) {
            0L -> "today"
            1L -> "tomorrow"
            else -> "${d.at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.at.dayOfMonth} " +
                d.at.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        }
        if (!d.hasTime) return day
        val h = d.at.hour % 12
        val clock = "${if (h == 0) 12 else h}:${"%02d".format(d.at.minute)} ${if (d.at.hour < 12) "am" else "pm"}"
        return "$day $clock"
    }

    /** "in 3 h", "in 2 days", "overdue" */
    fun countdown(d: Deadline, now: ZonedDateTime): String {
        val mins = ChronoUnit.MINUTES.between(now, d.at)
        return when {
            mins < 0 -> "overdue"
            mins < 60 -> "in $mins min"
            mins < 48 * 60 -> "in ${mins / 60} h"
            else -> "in ${mins / (24 * 60)} days"
        }
    }
}
