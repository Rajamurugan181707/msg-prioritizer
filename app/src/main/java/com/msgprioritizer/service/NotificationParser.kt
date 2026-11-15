package com.msgprioritizer.service

import android.app.Notification
import androidx.core.app.NotificationCompat

/** Pulls individual chat messages out of a messaging app's notification. */
object NotificationParser {
    data class Parsed(
        val chat: String,
        val sender: String,
        val text: String,
        val isGroup: Boolean,
        val timestamp: Long,
    )

    private val countSuffix = Regex("""\s*\(\d+ (new )?messages?\)$""")
    private val systemText = Regex(
        """^(\d+ new messages?( from \d+ chats?)?|checking for new messages|whatsapp web is currently active.*|backup in progress.*)$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(n: Notification, postTime: Long, appLabel: String): List<Parsed> {
        // WhatsApp, Telegram, Signal and Messages all use MessagingStyle, which gives us each message with its real sender.
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        if (style != null && style.messages.isNotEmpty()) {
            val isGroup = style.isGroupConversation
            val title = style.conversationTitle?.toString()?.replace(countSuffix, "")
            val me = style.user.name?.toString()
            return style.messages.mapNotNull { msg ->
                // A null person means the message was sent by the phone's owner.
                val sender = msg.person?.name?.toString() ?: return@mapNotNull null
                if (sender == me) return@mapNotNull null
                val text = msg.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Parsed(if (isGroup && title != null) title else sender, sender, text, isGroup, msg.timestamp)
            }
        }

        // Fallback for apps or older versions without MessagingStyle: plain title + text.
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return emptyList()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return emptyList()
        if (title.equals(appLabel, ignoreCase = true) || systemText.matches(text.trim())) return emptyList()
        val chat = title.replace(countSuffix, "")
        return listOf(Parsed(chat, chat, text, false, postTime))
    }
}
