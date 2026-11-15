package com.msgprioritizer.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class PriorityScorerTest {
    private val cfg = ScoringConfig(myName = "Priya", vips = setOf("Mom"), muted = setOf("Spam Group"))

    private fun level(text: String, sender: String = "Ravi", chat: String = sender, group: Boolean = false, recent: Int = 0) =
        PriorityScorer.score(Incoming(chat, sender, text, group, recent), cfg).level

    @Test fun vipPlainMessageIsHigh() = assertEquals(Level.HIGH, level("I reached home", sender = "Mom"))

    @Test fun urgentQuestionIsHigh() = assertEquals(Level.HIGH, level("Can you call me asap?"))

    @Test fun missedCallIsHigh() = assertEquals(Level.HIGH, level("Missed voice call"))

    @Test fun burstOfQuestionsIsHigh() = assertEquals(Level.HIGH, level("where are you", recent = 3))

    @Test fun groupMentionIsMedium() =
        assertEquals(Level.MEDIUM, level("@Priya are you coming today?", chat = "College", group = true))

    @Test fun directSmallTalkIsLow() = assertEquals(Level.LOW, level("ok"))

    @Test fun groupChatterIsLow() = assertEquals(Level.LOW, level("lol", chat = "College", group = true))

    @Test fun groupStickerIsLow() = assertEquals(Level.LOW, level("Sticker", chat = "College", group = true))

    @Test fun mutedBeatsEverything() =
        assertEquals(Level.LOW, level("URGENT call me", sender = "Mom", chat = "Spam Group", group = true))

    private val loved = ContactHistory(opens = 5, ignores = 0, avgOpenMs = 2 * 60_000L)
    private val ignored = ContactHistory(opens = 0, ignores = 6, avgOpenMs = 0)

    private fun scored(text: String, history: ContactHistory?) =
        PriorityScorer.score(Incoming("Ravi", "Ravi", text, false, history = history), cfg)

    @Test fun plainMessageFromSomeoneYouAlwaysOpenIsHigh() {
        assertEquals(Level.MEDIUM, scored("I reached the station", null).level)
        assertEquals(Level.HIGH, scored("I reached the station", loved).level)
    }

    @Test fun someoneYouAlwaysSwipeAwayDrops() {
        assertEquals(Level.HIGH, scored("Can you send the rent money today?", null).level)
        assertEquals(Level.MEDIUM, scored("Can you send the rent money today?", ignored).level)
    }

    @Test fun learningWaitsForEnoughEvents() {
        val r = scored("I reached the station", ContactHistory(opens = 2, ignores = 0, avgOpenMs = 1000))
        assertEquals(listOf("Direct message"), r.reasons)
    }

    @Test fun learnedReasonsAreShown() {
        assertEquals(
            listOf("Direct message", "You usually open their messages", "You usually open within 5 min"),
            scored("I reached the station", loved).reasons,
        )
    }

    @Test fun collegeWordInDirectChatIsHigh() {
        assertEquals(Level.HIGH, level("Exam timetable is out"))
        assertEquals(Level.HIGH, level("did you pay the fees"))
        assertEquals(Level.HIGH, level("Hackathon team list"))
    }

    @Test fun collegeWordsMatchPluralsAndSpacing() {
        assertEquals(Level.HIGH, level("both assignments are posted"))
        assertEquals(Level.HIGH, level("new time table for semesters"))
    }

    @Test fun collegeWordInGroupNeedsTimeOrQuestionToBuzz() {
        assertEquals(Level.MEDIUM, level("Assignment posted on the portal", chat = "Class", group = true))
        assertEquals(Level.HIGH, level("Assignment due tomorrow?", chat = "Class", group = true))
    }

    @Test fun collegeWordIsExplained() {
        val r = PriorityScorer.score(Incoming("Ravi", "Ravi", "Exams start Monday", false), cfg)
        assertEquals(listOf("Direct message", "College: \"exam\""), r.reasons)
    }

    @Test fun moreCollegeWordsAreHigh() {
        listOf(
            "internals next week", "midsem portions are out", "end-sem hall ticket available",
            "viva on friday", "practicals start", "CGPA list released", "attendance shortage list",
            "submit the lab record", "project review moved", "placement drive registration",
            "internship offer letter came", "I have an arrear", "supply exam form", "HOD called",
            "new circular", "ppt for seminar", "scholarship form", "results are out",
        ).forEach { assertEquals(it, Level.HIGH, level(it)) }
    }

    @Test fun everydayWordsAreNotCollegeWords() {
        // "practical", "result" and "internal" alone are ordinary English.
        assertEquals(Level.MEDIUM, level("that's not practical"))
        assertEquals(Level.MEDIUM, level("as a result I left"))
        assertEquals(Level.MEDIUM, level("internal wiring is fine"))
    }

    @Test fun collegeWordInsideAnotherWordDoesNotCount() {
        assertEquals(Level.MEDIUM, level("I'm examining the photos"))
    }

    @Test fun reasonsExplainTheScore() {
        val r = PriorityScorer.score(Incoming("Mom", "Mom", "Pay the rent today", false), cfg)
        assertEquals(listOf("VIP contact", "Direct message", "Time-sensitive", "About money"), r.reasons)
    }
}
