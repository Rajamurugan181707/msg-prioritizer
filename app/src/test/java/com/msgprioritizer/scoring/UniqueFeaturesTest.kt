package com.msgprioritizer.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UniqueFeaturesTest {
    // ---------- Tamil and Tanglish
    private val cfg = ScoringConfig()
    private fun scored(text: String, group: Boolean = false) =
        PriorityScorer.score(Incoming(if (group) "Class" else "Arun", "Arun", text, group), cfg)

    @Test fun tanglishUrgentIsHigh() {
        val r = scored("seekiram call pannu da")
        assertEquals(Level.HIGH, r.level)
        assertTrue(r.reasons.contains("Urgent: \"seekiram\""))
    }

    @Test fun tanglishCollegeAndTimeInGroupIsHigh() {
        val r = scored("naalaiku exam iruku", group = true)
        assertEquals(Level.HIGH, r.level)
        assertTrue(r.reasons.containsAll(listOf("College: \"exam\"", "Time-sensitive")))
    }

    @Test fun tanglishSuffixOnCollegeWordCounts() =
        assertTrue(scored("examla doubt").reasons.contains("College: \"exam\""))

    @Test fun tanglishFeesWithMoneyAndToday() {
        val r = scored("fees kattanum inniku")
        assertEquals(Level.HIGH, r.level)
        assertTrue(r.reasons.containsAll(listOf("College: \"fees\"", "Time-sensitive", "About money")))
    }

    @Test fun tamilScriptUrgentIsHigh() {
        val r = scored("அவசரம், உடனே கால் பண்ணு")
        assertEquals(Level.HIGH, r.level)
        assertTrue(r.reasons.contains("Urgent: \"அவசரம்\""))
    }

    @Test fun tamilScriptExamTomorrowInGroupIsHigh() =
        assertEquals(Level.HIGH, scored("நாளை தேர்வு", group = true).level)

    @Test fun tanglishSmallTalkIsLow() {
        assertEquals(Level.LOW, scored("sari da").level)
        assertEquals(Level.LOW, scored("vanakkam").level)
    }

    @Test fun tanglishQuestionIsCounted() = assertTrue(scored("eppo varuva").reasons.contains("Asks a question"))

    // ---------- Deadlines
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, zone) // a Monday, 10:00
    private fun due(text: String) = DeadlineParser.find(text, now)

    @Test fun dueTomorrowWithTime() {
        val d = due("Assignment due tomorrow 5pm")!!
        assertEquals(ZonedDateTime.of(2026, 11, 17, 17, 0, 0, 0, zone), d.at)
        assertEquals("tomorrow 5:00 pm", DeadlineParser.describe(d, now))
        assertEquals("in 31 h", DeadlineParser.countdown(d, now))
    }

    @Test fun tanglishToday() {
        val d = due("fees kattanum inniku")!!
        assertEquals(16, d.at.dayOfMonth)
        assertFalse(d.hasTime)
        assertEquals("today", DeadlineParser.describe(d, now))
    }

    @Test fun weekdayNamed() = assertEquals(20, due("Viva on Friday")!!.at.dayOfMonth)

    @Test fun numericDateIsDayFirst() {
        val d = due("Submit the record by 25/11")!!
        assertEquals(11, d.at.monthValue)
        assertEquals(25, d.at.dayOfMonth)
    }

    @Test fun monthName() = assertEquals(3, due("Internals start 3rd December")!!.at.dayOfMonth)

    @Test fun timeAlreadyPassedMeansTomorrow() =
        assertEquals(17, due("Pay the fees before 9am")!!.at.dayOfMonth)

    @Test fun tamilTomorrowExam() = assertEquals(17, due("நாளை தேர்வு")!!.at.dayOfMonth)

    @Test fun dateWithoutADueWordIsNotADeadline() = assertNull(due("I reached home today"))

    @Test fun pastDateIsIgnored() = assertNull(due("Exam was on 10/11/2026"))

    // ---------- Suggestions
    private fun hist(opens: Int, ignores: Int, avgMin: Long) = ContactHistory(opens, ignores, avgMin * 60_000)

    @Test fun fastOpenerBecomesVipSuggestion() {
        val s = Suggestions.from(mapOf("Karthik" to hist(6, 0, 3)), emptySet(), emptySet(), emptySet()).single()
        assertEquals(Suggestion.Kind.MAKE_VIP, s.kind)
        assertEquals("You opened 6 of their messages, usually within 3 min.", s.why)
    }

    @Test fun alwaysIgnoredBecomesMuteSuggestion() =
        assertEquals(Suggestion.Kind.MUTE, Suggestions.from(mapOf("Deals" to hist(0, 7, 0)), emptySet(), emptySet(), emptySet()).single().kind)

    @Test fun existingVipAndDismissedAreSkipped() {
        val learned = mapOf("Mom" to hist(9, 0, 1), "Karthik" to hist(6, 0, 3))
        assertTrue(Suggestions.from(learned, setOf("mom"), emptySet(), setOf("MAKE_VIP:Karthik")).isEmpty())
    }

    @Test fun tooFewEventsGiveNoSuggestion() =
        assertTrue(Suggestions.from(mapOf("Priya" to hist(3, 0, 1)), emptySet(), emptySet(), emptySet()).isEmpty())
}
