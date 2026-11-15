package com.msgprioritizer.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.msgprioritizer.data.AppDatabase
import com.msgprioritizer.data.Prefs
import com.msgprioritizer.data.recordIgnore
import com.msgprioritizer.data.recordOpen
import com.msgprioritizer.scoring.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Android hands us every notification once the user grants "Notification access".
 * We keep only messaging apps' (see SupportedApps), score each message, and store it on the phone.
 * We never touch those apps themselves, so there is no risk to the user's accounts.
 */
class MessageListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        connected.value = true
        // Catch messages that arrived while we weren't listening (app restarted, access just granted).
        val existing = runCatching { activeNotifications }.getOrNull().orEmpty()
        existing.forEach { onNotificationPosted(it) }
    }

    override fun onListenerDisconnected() {
        connected.value = false
        // Ask Android to bind us again; OEM battery savers sometimes cut the connection.
        requestRebind(ComponentName(this, MessageListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!SupportedApps.isSupported(sbn.packageName)) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val parsed = NotificationParser.parse(n, sbn.postTime, SupportedApps.label(sbn.packageName))
        if (parsed.isEmpty()) return
        scope.launch { parsed.forEach { handle(sbn.packageName, it) } }
    }

    /**
     * Learns from how the user dealt with a notification:
     * tapping it (or reading the chat in the app) counts as "opened",
     * swiping it away counts as "ignored".
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (!SupportedApps.isSupported(sbn.packageName)) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val chat = NotificationParser.parse(n, sbn.postTime, SupportedApps.label(sbn.packageName)).firstOrNull()?.chat ?: return

        val stats = AppDatabase.get(this).contactStats()
        when (reason) {
            REASON_CLICK, REASON_APP_CANCEL -> {
                val delay = System.currentTimeMillis() - sbn.postTime
                scope.launch { stats.recordOpen(chat, delay) }
            }
            REASON_CANCEL, REASON_CANCEL_ALL -> scope.launch { stats.recordIgnore(chat) }
            // Timeouts, snoozes, package changes and the like say nothing about the person.
            else -> Unit
        }
    }

    private suspend fun handle(app: String, p: NotificationParser.Parsed) {
        val message = Ranker.rankAndStore(this, app, p.chat, p.sender, p.text, p.isGroup, p.timestamp) ?: return
        val isFresh = System.currentTimeMillis() - p.timestamp < FRESH_MS
        if (isFresh && message.level == Level.HIGH.name && Prefs(this).alertHigh) {
            // WhatsApp buzzes for the same message at this exact moment, and phones often merge two
            // vibrations that start together into one. Waiting lets ours be felt on its own.
            delay(AFTER_APP_BUZZ_MS)
            Alerts.notifyHigh(this, message)
        }
    }

    override fun onDestroy() {
        connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Whether Android is currently delivering notifications to us. Shown in the inbox. */
        val connected = MutableStateFlow(false)

        fun rebind(context: Context) =
            requestRebind(ComponentName(context, MessageListener::class.java))

        private const val FRESH_MS = 10 * 60_000L
        private const val AFTER_APP_BUZZ_MS = 1_500L
    }
}
