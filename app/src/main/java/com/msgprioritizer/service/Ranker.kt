package com.msgprioritizer.service

import android.content.Context
import com.msgprioritizer.data.AppDatabase
import com.msgprioritizer.data.Message
import com.msgprioritizer.data.Prefs
import com.msgprioritizer.BuildConfig
import com.msgprioritizer.scoring.DeadlineParser
import com.msgprioritizer.scoring.Incoming
import com.msgprioritizer.scoring.Level
import com.msgprioritizer.scoring.PriorityScorer
import com.msgprioritizer.scoring.Scored
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Scores one message with everything we know and stores it. Shared by live notifications and imports. */
object Ranker {
    private const val BURST_WINDOW_MS = 5 * 60_000L

    /** Outlives any one screen, so a re-rank started from Settings finishes after the user leaves. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun score(context: Context, chat: String, sender: String, text: String, isGroup: Boolean, timestamp: Long): Scored {
        val db = AppDatabase.get(context)
        val recent = db.messages().countBetween(sender, chat, timestamp - BURST_WINDOW_MS, timestamp)
        val history = db.contactStats().get(chat)?.toHistory()
        return PriorityScorer.score(Incoming(chat, sender, text, isGroup, recent, history), Prefs(context).toConfig())
    }

    /** Re-scores everything still in the inbox, e.g. after a new VIP or name is saved. */
    fun rescoreAll(context: Context) {
        val app = context.applicationContext
        background.launch {
            val dao = AppDatabase.get(app).messages()
            dao.activeOnce().forEach { m ->
                val s = score(app, m.chat, m.sender, m.text, m.isGroup, m.receivedAt)
                dao.updateScore(m.id, s.score, s.level.name, s.reasons.joinToString("\n"))
            }
        }
    }

    /** Returns the stored message, or null if it was already in the inbox. */
    suspend fun rankAndStore(
        context: Context,
        app: String,
        chat: String,
        sender: String,
        text: String,
        isGroup: Boolean,
        timestamp: Long,
    ): Message? {
        val dao = AppDatabase.get(context).messages()
        val scored = score(context, chat, sender, text, isGroup, timestamp)
        val due = DeadlineParser.find(text, Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
        val message = Message(
            dedupeKey = "$app|$chat|$sender|$timestamp|${text.hashCode()}",
            app = app,
            chat = chat,
            sender = sender,
            text = text,
            isGroup = isGroup,
            receivedAt = timestamp,
            score = scored.score,
            level = scored.level.name,
            reasons = scored.reasons.joinToString("\n"),
            dueAt = due?.at?.toInstant()?.toEpochMilli(),
            dueHasTime = due?.hasTime == true,
        )
        val id = dao.insert(message)
        if (id == -1L) return null
        val stored = message.copy(id = id)
        // Reminders are part of the live edition's alerts; LOW messages never nag.
        if (BuildConfig.LIVE && stored.dueAt != null && scored.level != Level.LOW) Reminders.schedule(context, stored)
        return stored
    }
}
