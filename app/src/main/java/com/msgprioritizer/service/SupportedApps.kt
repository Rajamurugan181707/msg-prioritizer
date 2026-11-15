package com.msgprioritizer.service

/** Messaging apps we read notifications from, with the name shown to the user. */
object SupportedApps {
    val labels = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "org.telegram.messenger.web" to "Telegram",
        "org.thunderdog.challegram" to "Telegram X",
        "org.thoughtcrime.securesms" to "Signal",
        "com.google.android.apps.messaging" to "Messages",
        "com.samsung.android.messaging" to "Samsung Messages",
        "pasted" to "pasted text",
    )

    fun isSupported(pkg: String) = pkg in labels && pkg != "pasted"

    fun label(pkg: String) = labels[pkg] ?: "app"
}
