package com.msgprioritizer.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msgprioritizer.BuildConfig
import com.msgprioritizer.data.ContactStatsDao
import com.msgprioritizer.data.Message
import com.msgprioritizer.data.MessageDao
import com.msgprioritizer.data.Prefs
import com.msgprioritizer.data.Samples
import com.msgprioritizer.data.recordOpen
import com.msgprioritizer.scoring.Level
import com.msgprioritizer.service.Alerts
import com.msgprioritizer.service.MessageListener
import com.msgprioritizer.service.SupportedApps
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.Instant
import com.msgprioritizer.scoring.DeadlineParser
import com.msgprioritizer.scoring.Deadline
import com.msgprioritizer.data.Feedback

@Composable
fun InboxScreen(
    dao: MessageDao,
    stats: ContactStatsDao,
    prefs: Prefs,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val messages by dao.active().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Priority inbox", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenImport) { Text("Add") }
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }

        if (BuildConfig.LIVE) ConnectionBanner()
        if (BuildConfig.LIVE && prefs.alertHigh) AlertsOffBanner()

        if (messages.isEmpty()) {
            Text(
                if (BuildConfig.LIVE) "Nothing waiting. New messages will show up here, most important first."
                else "Nothing here yet. Share a WhatsApp chat to this app, or add messages yourself, " +
                    "and they'll show up here, most important first.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onOpenImport,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Add your messages") }
            OutlinedButton(
                onClick = { scope.launch { Samples.load(dao, prefs) } },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Load sample messages") }
            return@Column
        }

        val now = ZonedDateTime.now()
        val deadlines = messages
            .filter { it.dueAt != null && it.dueAt > now.minusHours(12).toInstant().toEpochMilli() }
            .sortedBy { it.dueAt }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (deadlines.isNotEmpty()) {
                item(key = "header-deadlines") {
                    Text(
                        "DEADLINES COMING UP (${deadlines.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(deadlines, key = { "due-${it.id}" }) { m -> DeadlineRow(m, now) }
            }
            Level.entries.forEach { level ->
                val group = messages.filter { it.level == level.name }
                if (group.isNotEmpty()) {
                    item(key = "header-$level") {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${heading(level)} (${group.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { scope.launch { dao.markLevelDone(level.name) } }) {
                                Text("All done")
                            }
                        }
                    }
                    items(group, key = { it.id }) { m ->
                        MessageCard(
                            m,
                            onOpen = {
                                openApp(context, m.app)
                                scope.launch { stats.recordOpen(m.chat, System.currentTimeMillis() - m.receivedAt) }
                            },
                            onDone = { scope.launch { dao.markDone(m.id) } },
                            onRate = { right -> scope.launch { Feedback.rate(context, m, right) } },
                        )
                    }
                }
            }
        }
    }
}

private fun heading(level: Level) = when (level) {
    Level.HIGH -> "!!! HIGH: needs you now"
    Level.MEDIUM -> "!! MEDIUM: when you can"
    Level.LOW -> "! LOW: whenever"
}

@Composable
private fun MessageCard(m: Message, onOpen: () -> Unit, onDone: () -> Unit, onRate: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (m.level == Level.HIGH.name) 3.dp else 1.dp, Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.sender,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(DateUtils.getRelativeTimeSpanString(m.receivedAt).toString(), style = MaterialTheme.typography.bodySmall)
            }
            val where = if (m.isGroup) "in ${m.chat} · " else ""
            Text("${where}via ${SupportedApps.label(m.app)}", style = MaterialTheme.typography.bodySmall)
            Text(m.text, style = MaterialTheme.typography.bodyLarge)
            dueText(m, ZonedDateTime.now())?.let { Text(it, fontWeight = FontWeight.Bold) }
            Text(
                "Why: ${m.reasons.replace("\n", " · ")} (score ${m.score})",
                style = MaterialTheme.typography.bodySmall,
            )
            // The user's verdict feeds the accuracy figures in Settings (the project's evaluation).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (m.feedback) {
                        1 -> "You said this rank is right."
                        -1 -> "You said this rank is wrong."
                        else -> "Is this rank right?"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (m.feedback != 1) TextButton(onClick = { onRate(true) }) { Text("Yes") }
                if (m.feedback != -1) TextButton(onClick = { onRate(false) }) { Text("No") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val context = LocalContext.current
                val canOpen = remember(m.app) { context.packageManager.getLaunchIntentForPackage(m.app) != null }
                if (canOpen) OutlinedButton(onClick = onOpen) { Text("Open ${SupportedApps.label(m.app)}") }
                Button(onClick = onDone) { Text("Done") }
            }
        }
    }
}

private fun openApp(context: Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
}

/**
 * Shown when notification access is on but Android has stopped delivering to us,
 * which battery savers on OnePlus, Xiaomi and others do. Waits a moment first so it
 * doesn't flash while the connection is still starting.
 */
@Composable
private fun ConnectionBanner() {
    val connected by MessageListener.connected.collectAsStateWithLifecycle()
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(3_000)
        settled = true
    }
    if (connected || !settled) return

    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(3.dp, Color.White),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Not receiving new messages", fontWeight = FontWeight.Bold)
            Text(
                "Your phone has paused this app in the background. Tap Reconnect. If this keeps " +
                    "happening, open App info → Battery and allow background activity.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { MessageListener.rebind(context) }) { Text("Reconnect") }
                OutlinedButton(onClick = { openAppInfo(context) }) { Text("App info") }
            }
        }
    }
}

/** Shown when HIGH alerts are switched on in the app but the phone won't let them through. */
@Composable
private fun AlertsOffBanner() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Alerts.canAlert(context)) }
    // Re-check whenever the user comes back, e.g. from the system notification settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = Alerts.canAlert(context) }
    if (allowed) return

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(3.dp, Color.White),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alerts are off", fontWeight = FontWeight.Bold)
            Text(
                "Messages are still ranked here, but your phone won't buzz for HIGH ones. " +
                    "Tap Turn on alerts and allow notifications.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { Alerts.openSettings(context) }) { Text("Turn on alerts") }
        }
    }
}

/** "Due: tomorrow 5:00 pm · in 31 h", or null when the message has no deadline. */
private fun dueText(m: Message, now: ZonedDateTime): String? {
    val d = deadlineOf(m) ?: return null
    return "Due: ${DeadlineParser.describe(d, now)} · ${DeadlineParser.countdown(d, now)}"
}

private fun deadlineOf(m: Message): Deadline? =
    m.dueAt?.let { Deadline(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()), m.dueHasTime) }

/** One compact line in the Deadlines list: when it's due, then who and what. */
@Composable
private fun DeadlineRow(m: Message, now: ZonedDateTime) {
    val d = deadlineOf(m) ?: return
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${DeadlineParser.describe(d, now)} · ${DeadlineParser.countdown(d, now)}", fontWeight = FontWeight.Bold)
            Text("${m.sender}: ${m.text}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
