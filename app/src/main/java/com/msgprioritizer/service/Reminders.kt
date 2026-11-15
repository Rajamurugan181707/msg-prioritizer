package com.msgprioritizer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.msgprioritizer.data.Message
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reminds the user before a deadline found in a message: 1 hour before a timed deadline, or at
 * 8 am on the day for "due tomorrow". Uses an inexact alarm, which needs no special permission;
 * Android may deliver it a few minutes late to save battery.
 */
object Reminders {
    private const val EARLY_MS = 60 * 60_000L
    private val MORNING = LocalTime.of(8, 0)

    fun schedule(context: Context, m: Message) {
        val due = m.dueAt ?: return
        val zone = ZoneId.systemDefault()
        val at = if (m.dueHasTime) due - EARLY_MS
        else Instant.ofEpochMilli(due).atZone(zone).toLocalDate().atTime(MORNING).atZone(zone).toInstant().toEpochMilli()
        if (at < System.currentTimeMillis() + 60_000) return

        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.TITLE, "Due ${if (m.dueHasTime) "in 1 hour" else "today"}: ${m.sender}")
            .putExtra(ReminderReceiver.TEXT, m.text)
        val pi = PendingIntent.getBroadcast(
            context, m.dedupeKey.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(TITLE) ?: return
        Alerts.notifyReminder(context, title, intent.getStringExtra(TEXT).orEmpty())
    }

    companion object {
        const val TITLE = "title"
        const val TEXT = "text"
    }
}
