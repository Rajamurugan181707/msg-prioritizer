package com.msgprioritizer.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.msgprioritizer.MainActivity
import com.msgprioritizer.R
import com.msgprioritizer.data.Message

object Alerts {
    /**
     * Android freezes a channel's sound and vibration when it is first created, so changing them
     * means a new channel ID. "high_priority" (v1.0 to 1.2) was created without vibration forced on.
     */
    private const val CHANNEL = "high_priority_v2"
    private val OLD_CHANNELS = listOf("high_priority")
    private const val TEST_ID = 1

    /** Buzz, pause, buzz, pause, buzz: easy to tell apart from WhatsApp's single buzz. */
    private val PATTERN = longArrayOf(0, 350, 150, 350, 150, 350)

    /** At most one sound and vibration in this window; later alerts still appear, silently. */
    private const val QUIET_WINDOW_MS = 15_000L
    @Volatile private var lastBuzzAt = 0L

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        OLD_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
        val channel = NotificationChannel(CHANNEL, "High priority messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Buzzes you when a message is ranked HIGH"
            enableVibration(true)
            vibrationPattern = PATTERN
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    /** False when the user denied notifications or switched off the HIGH alerts channel. */
    fun canAlert(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** The system page where the user can allow notifications and pop-ups for this app. */
    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun notifyHigh(context: Context, m: Message) {
        val title = if (m.isGroup) "Important: ${m.sender} in ${m.chat}" else "Important: ${m.sender}"
        post(context, m.dedupeKey.hashCode(), title, m.text)
    }

    /** A deadline reminder: same channel and buzz as a HIGH message. */
    fun notifyReminder(context: Context, title: String, text: String) =
        post(context, (title + text).hashCode(), title, text)

    /** Lets the user check from Settings that alerts really reach them. */
    fun sendTest(context: Context): Boolean {
        if (!canAlert(context)) return false
        lastBuzzAt = 0L // a test should always buzz
        post(context, TEST_ID, "Important: test alert", "This is how HIGH messages will reach you.")
        return true
    }

    // Permission is checked by canAlert. The Lite edition has no permission but never posts:
    // its MessageListener is removed from the manifest and its Settings hide the alert controls.
    @SuppressLint("MissingPermission", "NotificationPermission")
    private fun post(context: Context, id: Int, title: String, text: String) {
        if (!canAlert(context)) return
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val quiet = now - lastBuzzAt < QUIET_WINDOW_MS
        if (!quiet) lastBuzzAt = now
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_priority)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(PATTERN)
            .setSilent(quiet)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        if (!quiet) buzzDirect(context)
    }

    /**
     * Backup vibration. Some phones (OnePlus among them) skip a notification channel's vibration
     * because of their own sound settings, so the alert also vibrates the motor directly with the
     * same pattern. When both play they start together and feel like one buzz. Silent mode and
     * Do Not Disturb still win: in either one the phone stays still.
     */
    private fun buzzDirect(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        val vibrator = vibrator(context)
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(PATTERN, -1)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        }
    }

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** One line of the "Check alerts" list in Settings. */
    data class Check(val label: String, val ok: Boolean, val detail: String, val fix: ((Context) -> Unit)?)

    /** Everything on the phone that can stop the HIGH buzz, in the order a user should fix it. */
    fun check(context: Context): List<Check> {
        val nm = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val allowed = canAlert(context)
        val channel = if (allowed) {
            ensureChannel(context)
            nm.getNotificationChannel(CHANNEL)
        } else null
        val openChannel: (Context) -> Unit = { openChannelSettings(it) }
        val mode = audio.ringerMode
        val dndOff = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        return listOf(
            Check("Notifications allowed", allowed,
                if (allowed) "This app may show alerts." else "Alerts are blocked for this app.",
                { openSettings(it) }),
            Check("Pop-up and sound", channel != null && channel.importance >= NotificationManager.IMPORTANCE_HIGH,
                if (channel != null && channel.importance >= NotificationManager.IMPORTANCE_HIGH) "HIGH alerts pop up with sound."
                else "HIGH alerts were turned down to silent. Set them to Alert / pop up.",
                openChannel),
            Check("Vibration", channel?.shouldVibrate() == true,
                if (channel?.shouldVibrate() == true) "Vibration is on for HIGH alerts." else "Vibration is off for HIGH alerts. Turn it on.",
                openChannel),
            Check("Sound mode", mode != AudioManager.RINGER_MODE_SILENT,
                when (mode) {
                    AudioManager.RINGER_MODE_NORMAL -> "Ring: sound and buzz."
                    AudioManager.RINGER_MODE_VIBRATE -> "Vibrate: buzz only."
                    else -> "Silent stops every buzz. On a OnePlus, move the alert slider down to Vibrate or Ring."
                },
                null),
            Check("Do Not Disturb", dndOff,
                if (dndOff) "Off." else "On. It stops alerts. Turn it off, or allow this app in its exceptions.",
                { open(it, Settings.ACTION_SOUND_SETTINGS) }),
            Check("Battery", power.isIgnoringBatteryOptimizations(context.packageName),
                if (power.isIgnoringBatteryOptimizations(context.packageName)) "Allowed to run in the background."
                else "The phone may pause the app. Find Msg Prioritizer in the list and choose Don't optimise / Allow.",
                { open(it, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }),
            Check("Reading messages", MessageListener.connected.value,
                if (MessageListener.connected.value) "Connected to your notifications." else "Not connected right now. Tap Fix to reconnect.",
                { MessageListener.rebind(it) }),
        )
    }

    /** The system page for just the HIGH alerts category: pop-up, sound and vibration live here. */
    fun openChannelSettings(context: Context) {
        ensureChannel(context)
        context.startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun open(context: Context, action: String) =
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
