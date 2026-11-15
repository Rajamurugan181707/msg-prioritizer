package com.msgprioritizer.ui

import com.msgprioritizer.scoring.Suggestions
import com.msgprioritizer.scoring.Suggestion
import com.msgprioritizer.data.Feedback
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.msgprioritizer.BuildConfig
import com.msgprioritizer.data.ContactStats
import com.msgprioritizer.data.ContactStatsDao
import com.msgprioritizer.data.MessageDao
import com.msgprioritizer.data.Prefs
import com.msgprioritizer.scoring.PriorityScorer
import com.msgprioritizer.service.Alerts
import com.msgprioritizer.service.Ranker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(prefs: Prefs, dao: MessageDao, stats: ContactStatsDao, onBack: () -> Unit) {
    var name by remember { mutableStateOf(prefs.myName) }
    var vips by remember { mutableStateOf(prefs.vips.joinToString("\n")) }
    var muted by remember { mutableStateOf(prefs.muted.joinToString("\n")) }
    var alert by remember { mutableStateOf(prefs.alertHigh) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your name (to spot @mentions)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vips,
            onValueChange = { vips = it },
            label = { Text("VIP contacts, one per line") },
            supportingText = { Text("Type names exactly as they appear in your messaging app. Groups work too.") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = muted,
            onValueChange = { muted = it },
            label = { Text("Muted contacts or groups, one per line") },
            supportingText = { Text("These always go to LOW.") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        // Rules the app proposes from what it learned. Accepting one fills in the box above and
        // saves straight away, so the user sees exactly what changed.
        val learned by stats.all().collectAsStateWithLifecycle(initialValue = emptyList())
        var dismissed by remember { mutableStateOf(prefs.dismissedSuggestions) }
        val suggestions = Suggestions.from(
            learned.associate { it.chat to it.toHistory() },
            Prefs.parseLines(vips), Prefs.parseLines(muted), dismissed,
        )
        if (suggestions.isNotEmpty()) {
            Text("Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            suggestions.forEach { s ->
                SuggestionCard(
                    s,
                    onAccept = {
                        if (s.kind == Suggestion.Kind.MAKE_VIP) {
                            vips = (Prefs.parseLines(vips) + s.chat).joinToString("\n"); prefs.vips = Prefs.parseLines(vips)
                        } else {
                            muted = (Prefs.parseLines(muted) + s.chat).joinToString("\n"); prefs.muted = Prefs.parseLines(muted)
                        }
                        Ranker.rescoreAll(context)
                    },
                    onDismiss = { dismissed = dismissed + s.key; prefs.dismissedSuggestions = dismissed },
                )
            }
        }

        if (BuildConfig.LIVE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buzz me for HIGH messages", modifier = Modifier.weight(1f))
                Text(if (alert) "On" else "Off")
                Spacer(Modifier.width(8.dp))
                Switch(checked = alert, onCheckedChange = { alert = it })
            }
            var testResult by remember { mutableStateOf<String?>(null) }
            OutlinedButton(
                onClick = {
                    testResult = if (Alerts.sendTest(context)) {
                        "Test alert sent. If it didn't pop up, tap Notification settings and allow pop-ups."
                    } else {
                        "Notifications are blocked for this app. Tap Notification settings and allow them."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send test alert") }
            OutlinedButton(onClick = { Alerts.openSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Notification settings")
            }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            AlertCheckSection()
        }

        Button(
            onClick = {
                prefs.myName = name.trim()
                prefs.vips = Prefs.parseLines(vips)
                prefs.muted = Prefs.parseLines(muted)
                prefs.alertHigh = alert
                Ranker.rescoreAll(context)
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }

        OutlinedButton(onClick = { scope.launch { dao.clearDone() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear finished messages")
        }

        OutlinedButton(onClick = { scope.launch { dao.deleteAll() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete all messages")
        }

        LearnedSection(stats)

        AccuracySection(prefs)

        OutlinedButton(
            onClick = { scope.launch { stats.reset(); Ranker.rescoreAll(context) } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Forget what it learned")
        }

        Text(
            (if (BuildConfig.LIVE) "Privacy: messages are stored only on this phone and deleted after 7 days. "
            else "Privacy: messages are stored only on this phone until you delete them. ") +
                "The app has no internet permission, so nothing is ever sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LearnedSection(stats: ContactStatsDao) {
    val learned by stats.all().collectAsStateWithLifecycle(initialValue = emptyList())

    Text("What it has learned", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (learned.isEmpty()) {
        Text(
            if (BuildConfig.LIVE) {
                "Nothing yet. When you tap or swipe away message notifications, " +
                    "the app learns whose messages matter to you."
            } else {
                "Nothing yet. When you press Open WhatsApp on someone's message, " +
                    "the app learns whose messages matter to you."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    learned.take(30).forEach { s -> Text(describe(s), style = MaterialTheme.typography.bodyMedium) }
}

private fun describe(s: ContactStats): String {
    val counts = "${s.chat}: opened ${s.opens}, swiped away ${s.ignores}"
    val total = s.opens + s.ignores
    if (total < PriorityScorer.MIN_EVENTS) {
        return "$counts. Still learning (needs ${PriorityScorer.MIN_EVENTS - total} more)"
    }
    val points = PriorityScorer.learnedEffect(s.toHistory()).sumOf { it.first }
    return when {
        points > 0 -> "$counts. Ranked higher (+$points)"
        points < 0 -> "$counts. Ranked lower ($points)"
        else -> "$counts. No change"
    }
}

/**
 * Lists everything on the phone that can stop the HIGH buzz, each marked in words (OK / Fix needed),
 * never by colour alone. Re-checks whenever the user comes back from a system settings page.
 */
@Composable
private fun AlertCheckSection() {
    val context = LocalContext.current
    var checks by remember { mutableStateOf(Alerts.check(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checks = Alerts.check(context) }
    val problems = checks.count { !it.ok }

    Text("Check alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        if (problems == 0) "Everything is set for HIGH alerts to buzz."
        else "$problems thing${if (problems == 1) "" else "s"} can stop the buzz. Fix ${if (problems == 1) "it" else "them"} below.",
        style = MaterialTheme.typography.bodyMedium,
    )
    checks.forEach { c ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(if (c.ok) 1.dp else 3.dp, Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (c.ok) "OK" else "Fix needed", fontWeight = FontWeight.Bold)
                }
                Text(c.detail, style = MaterialTheme.typography.bodyMedium)
                val fix = c.fix
                if (!c.ok && fix != null) OutlinedButton(onClick = { fix(context) }) { Text("Fix") }
            }
        }
    }
}

@Composable
private fun SuggestionCard(s: Suggestion, onAccept: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (s.kind == Suggestion.Kind.MAKE_VIP) "Make ${s.chat} a VIP?" else "Mute ${s.chat}?",
                fontWeight = FontWeight.Bold,
            )
            Text(s.why, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept) { Text(if (s.kind == Suggestion.Kind.MAKE_VIP) "Make VIP" else "Mute") }
                OutlinedButton(onClick = onDismiss) { Text("No thanks") }
            }
        }
    }
}

/**
 * Accuracy from the user's "Is this rank right?" answers: the evaluation numbers for the project,
 * shareable as text for a report.
 */
@Composable
private fun AccuracySection(prefs: Prefs) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf(Feedback.summary(prefs)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { rows = Feedback.summary(prefs) }
    val total = rows.sumOf { it.total }

    Text("Accuracy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (total == 0) {
        Text(
            "No ratings yet. Answer \"Is this rank right?\" on messages in the inbox, and the results show up here.",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val right = rows.sumOf { it.right }
    Text("Overall: $right of $total ranks rated right (${Math.round(right * 100f / total)}%)", fontWeight = FontWeight.Bold)
    rows.filter { it.total > 0 }.forEach {
        Text("${it.level}: ${it.right} of ${it.total} right (${it.percent}%)", style = MaterialTheme.typography.bodyMedium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Feedback.export(prefs)), "Share accuracy results")
            )
        }) { Text("Share results") }
        OutlinedButton(onClick = { prefs.resetRatings(); rows = Feedback.summary(prefs) }) { Text("Reset") }
    }
}
