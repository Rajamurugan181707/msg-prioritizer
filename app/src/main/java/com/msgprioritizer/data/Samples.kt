package com.msgprioritizer.data

import com.msgprioritizer.scoring.Incoming
import com.msgprioritizer.scoring.PriorityScorer

/**
 * Realistic fake messages for trying the app on an emulator or without WhatsApp.
 * They go through the real scorer and database, exactly like live messages.
 */
object Samples {
    private data class Sample(val chat: String, val sender: String, val text: String, val isGroup: Boolean, val minutesAgo: Int)

    suspend fun load(dao: MessageDao, prefs: Prefs) {
        val me = prefs.myName.ifBlank { "Priya" }
        val samples = listOf(
            Sample("Mom", "Mom", "Call me when you're free, it's important", false, 2),
            Sample("Ananya", "Ananya", "Missed voice call", false, 5),
            Sample("Ravi", "Ravi", "Can you send the rent money today?", false, 8),
            Sample("Office Team", "Manager", "Deadline moved to 5pm, please update the doc", true, 12),
            Sample("College Friends", "Karthik", "@$me are you coming tonight?", true, 15),
            Sample("Ravi", "Ravi", "I reached the station", false, 20),
            Sample("College Friends", "Divya", "lol", true, 25),
            Sample("Family", "Uncle", "Good morning 🙏", true, 40),
            Sample("Deals Group", "Shop", "📷 Photo", true, 60),
        )
        val now = System.currentTimeMillis()
        samples.forEachIndexed { i, s ->
            val at = now - s.minutesAgo * 60_000L
            val scored = PriorityScorer.score(Incoming(s.chat, s.sender, s.text, s.isGroup), prefs.toConfig())
            dao.insert(
                Message(
                    dedupeKey = "sample|$i|$at",
                    app = "com.whatsapp",
                    chat = s.chat,
                    sender = s.sender,
                    text = s.text,
                    isGroup = s.isGroup,
                    receivedAt = at,
                    score = scored.score,
                    level = scored.level.name,
                    reasons = scored.reasons.joinToString("\n"),
                )
            )
        }
    }
}
