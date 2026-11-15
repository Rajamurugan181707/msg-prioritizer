package com.msgprioritizer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.msgprioritizer.data.ContactStatsDao
import com.msgprioritizer.data.MessageDao
import com.msgprioritizer.data.Prefs

@Composable
fun App(hasAccess: Boolean, dao: MessageDao, stats: ContactStatsDao, prefs: Prefs, onGrantAccess: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf("inbox") }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when {
            !hasAccess -> SetupScreen(onGrantAccess)
            screen == "settings" -> SettingsScreen(prefs, dao, stats, onBack = { screen = "inbox" })
            screen == "import" -> ImportScreen(onBack = { screen = "inbox" })
            else -> InboxScreen(
                dao, stats, prefs,
                onOpenSettings = { screen = "settings" },
                onOpenImport = { screen = "import" },
            )
        }
        }
    }
}

@Composable
private fun SetupScreen(onGrantAccess: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, alignment = androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("Msg Prioritizer", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Puts your most important messages first, so you can ignore the noise. Works with WhatsApp, Telegram, Signal and SMS.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "To do this it needs to read message notifications. Everything stays on your phone. " +
                "This app has no internet access, so your messages are never sent anywhere.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Tap the button, find \"Msg Prioritizer\" in the list, switch it on, then come back.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onGrantAccess, modifier = Modifier.fillMaxWidth()) {
            Text("Turn on notification access")
        }
        Text(
            "If Android says \"Restricted setting\": tap Open app info below, tap ⋮ at the top right, " +
                "choose Allow restricted settings, then tap the button above again.",
            style = MaterialTheme.typography.bodyMedium,
        )
        val context = LocalContext.current
        OutlinedButton(onClick = { openAppInfo(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open app info")
        }
    }
}

/** The system "App info" page, where restricted settings and battery options live. */
fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
