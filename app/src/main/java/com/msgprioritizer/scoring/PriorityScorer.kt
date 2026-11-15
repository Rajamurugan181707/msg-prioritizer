package com.msgprioritizer.scoring

import kotlin.math.roundToInt
import kotlin.text.RegexOption.IGNORE_CASE

enum class Level { HIGH, MEDIUM, LOW }

/** One incoming message, already pulled out of the notification. */
data class Incoming(
    val chat: String,
    val sender: String,
    val text: String,
    val isGroup: Boolean,
    /** Messages this sender sent in the same chat during the last few minutes. */
    val recentFromSender: Int = 0,
    /** How the user has treated this chat's notifications before; null if never seen. */
    val history: ContactHistory? = null,
)

/** Learned from the user's own behaviour: tapping or swiping away notifications. */
data class ContactHistory(val opens: Int, val ignores: Int, val avgOpenMs: Long)

/** What the user told us in Settings. */
data class ScoringConfig(
    val myName: String = "",
    val vips: Set<String> = emptySet(),
    val muted: Set<String> = emptySet(),
)

data class Scored(val score: Int, val level: Level, val reasons: List<String>)

/**
 * Rule-based scoring that runs entirely on the phone.
 * Every rule adds or removes points and records a human-readable reason,
 * so the user can always see *why* a message was ranked where it is.
 *
 * Pure Kotlin with no Android imports, so it can be unit tested on a laptop.
 */
object PriorityScorer {
    private const val BASE = 20
    const val HIGH_AT = 60
    const val MEDIUM_AT = 30

    private val urgentWords = listOf(
        "urgent", "asap", "emergency", "important", "immediately", "call me",
        "call back", "hospital", "accident", "help", "sos", "deadline",
        "please reply", "pls reply", "need you",
    ).map { it to Regex("""\b${Regex.escape(it)}\b""", IGNORE_CASE) } +
        // Tanglish (Tamil typed in English letters), as students actually write it
        listOf(
            "seekiram" to "s(ee|i)k+i?ram", "avasaram" to "avasara(m|ma)", "udane" to "udan[ae]y?",
            "vegama" to "vega?ma", "ippove" to "ipp[oa]ve",
            "call pannu" to "(call|phone|ring)\\s?(pannu|panu|pannunga|pannuga|pannuda|pannudi)",
            "reply pannu" to "reply\\s?(pannu|panu|pannunga|pannuga)",
        ).map { (word, pattern) -> word to Regex("""\b(?:$pattern)\b""", IGNORE_CASE) } +
        // Tamil script. Java's \\b doesn't treat Tamil letters as word letters, so these match as written.
        listOf("அவசரம்", "உடனே", "சீக்கிரம்", "முக்கியம்", "கால் பண்ணு", "உதவி")
            .map { it to Regex(Regex.escape(it)) }

    /**
     * College life: these decide marks, money and careers, so they rank close to urgent.
     * Each pair is the word shown to the user and the pattern that finds it (plurals, spacing and
     * hyphen variants included). Generic words like "practical", "result" or "internal" are left out
     * because everyday sentences use them too.
     */
    private val collegeWords = listOf(
        // Exams and tests
        "exam" to "exams?", "re-exam" to "re-?exams?", "internals" to "internals|internal\\s?exams?",
        "mid-sem" to "mid\\s?-?sems?", "end-sem" to "end\\s?-?sems?", "CIA" to "cia", "viva" to "vivas?",
        "practicals" to "practicals|practical\\s?exams?", "quiz" to "quiz(zes)?", "question paper" to "question\\s?papers?",
        "syllabus" to "syllabus", "portions" to "portions?", "hall ticket" to "hall\\s?tickets?",
        "admit card" to "admit\\s?cards?", "supplementary" to "supplementary|supply\\s?exams?",
        "arrear" to "arrears?", "backlog" to "backlogs?",
        // Results and records
        "results" to "results", "marks" to "marks", "CGPA" to "[cs]?gpa", "attendance" to "attendance",
        // Coursework
        "assignment" to "assignments?", "submission" to "submissions?", "due date" to "due\\s?dates?",
        "lab record" to "lab\\s?records?|record\\s?notes?", "project review" to "project\\s?reviews?",
        "presentation" to "presentations?|ppt", "seminar" to "seminars?",
        // Admin and money
        "fees" to "fees?", "scholarship" to "scholarships?", "registration" to "registrations?",
        "semester" to "semesters?", "timetable" to "time\\s?tables?", "circular" to "circulars?", "HOD" to "hod",
        // Careers
        "placement" to "placements?", "internship" to "internships?", "interview" to "interviews?",
        "offer letter" to "offer\\s?letters?", "hackathon" to "hackathons?",
    ).map { (word, pattern) -> word to Regex("""\b(?:$pattern)(?:la|ku|ukku|um)?\b""", IGNORE_CASE) } +
        // Tamil script: exam, exam, fee, marks
        listOf("தேர்வு" to "exam", "பரீட்சை" to "exam", "கட்டணம்" to "fees", "மதிப்பெண்" to "marks")
            .map { (tamil, _) -> tamil to Regex(Regex.escape(tamil)) }

    private val missedCall = Regex("""missed (voice|video) call""", IGNORE_CASE)
    private val timeWords = Regex(
        """\b(today|tonight|tomorrow|right now|this (morning|afternoon|evening)|inn?a?i?k+u|indru|naa?laik*u|naalai)\b""" +
            """|\b\d{1,2}(:\d{2})?\s?(am|pm)\b|இன்று|இன்னைக்கு|நாளை""",
        IGNORE_CASE,
    )
    private val moneyWords = Regex(
        """\b(pay|paid|payment|money|bank|transfer|invoice|bill|fees?|rent|upi|panam|kaasu|kattanum|kattunga)\b""" +
            """|₹|\$|\brs\.?\s?\d|பணம்|காசு|கட்டணம்""",
        IGNORE_CASE,
    )
    private val questionStart = Regex(
        """^\s*(can|could|will|would|are|did|do|have|is|when|where|what|why|how|enna|eppo|enga|epdi|yen)\b""",
        IGNORE_CASE,
    )
    private val smallTalk = Regex(
        """^\s*(good\s?(morning|night|evening|afternoon)|gm|gn|hi+|hello|hey|ok(ay)?|k|thanks|thank you|thx|lol|haha+|hehe+|nice|cool|sari|seri|hm+|vanakkam|gud\s?m(o)?r?n?i?n?g|👍|🙏|😂|❤️)(\s(da|di|bro|ma|machan))?[\s!.🙏😊😂👍❤️]*$""",
        IGNORE_CASE,
    )
    private val mediaOnly = Regex("""^\s*(\S+\s+)?(photo|sticker|gif|video)\s*$""", IGNORE_CASE)

    fun score(m: Incoming, cfg: ScoringConfig): Scored {
        if (cfg.muted.containsName(m.sender) || cfg.muted.containsName(m.chat)) {
            return Scored(0, Level.LOW, listOf("Muted by you"))
        }

        var score = BASE
        val reasons = mutableListOf<String>()
        fun add(points: Int, why: String) {
            score += points
            reasons += why
        }

        val text = m.text

        if (cfg.vips.containsName(m.sender)) add(40, "VIP contact")
        else if (m.isGroup && cfg.vips.containsName(m.chat)) add(20, "VIP group")

        if (missedCall.containsMatchIn(text)) add(35, "Missed call")

        if (m.isGroup) add(-10, "Group chat") else add(15, "Direct message")

        val name = cfg.myName.trim()
        if (name.isNotEmpty() &&
            Regex("""(^|[\s@])${Regex.escape(name)}\b""", IGNORE_CASE).containsMatchIn(text)
        ) add(20, "Mentions you")

        urgentWords.firstOrNull { (_, re) -> re.containsMatchIn(text) }
            ?.let { (word, _) -> add(25, "Urgent: \"$word\"") }

        collegeWords.firstOrNull { (_, re) -> re.containsMatchIn(text) }
            ?.let { (word, _) -> add(40, "College: \"$word\"") }

        if (timeWords.containsMatchIn(text)) add(10, "Time-sensitive")
        if (moneyWords.containsMatchIn(text)) add(10, "About money")
        if ('?' in text || questionStart.containsMatchIn(text)) add(10, "Asks a question")

        if (m.recentFromSender >= 2) {
            add(minOf(20, m.recentFromSender * 5), "${m.recentFromSender + 1} messages in a row")
        }

        if (smallTalk.matches(text) || mediaOnly.matches(text)) add(-20, "Small talk or media")

        m.history?.let { h -> learnedEffect(h).forEach { (points, why) -> add(points, why) } }

        val final = score.coerceIn(0, 100)
        return Scored(final, levelFor(final), reasons)
    }

    /**
     * Points learned from how the user treats this chat. Needs a few events
     * before it kicks in, so one accidental swipe doesn't bury someone.
     * Ranges from -20 (always swiped away) to +30 (always opened, quickly).
     */
    fun learnedEffect(h: ContactHistory): List<Pair<Int, String>> {
        val total = h.opens + h.ignores
        if (total < MIN_EVENTS) return emptyList()

        val effect = mutableListOf<Pair<Int, String>>()
        val points = ((h.opens.toDouble() / total - 0.5) * 40).roundToInt()
        if (points >= 5) effect += points to "You usually open their messages"
        if (points <= -5) effect += points to "You usually swipe them away"
        if (h.opens >= MIN_EVENTS && h.avgOpenMs <= FAST_OPEN_MS) effect += 10 to "You usually open within 5 min"
        return effect
    }

    const val MIN_EVENTS = 3
    private const val FAST_OPEN_MS = 5 * 60_000L

    fun levelFor(score: Int): Level = when {
        score >= HIGH_AT -> Level.HIGH
        score >= MEDIUM_AT -> Level.MEDIUM
        else -> Level.LOW
    }

    private fun Set<String>.containsName(name: String) =
        any { it.trim().equals(name.trim(), ignoreCase = true) }
}
