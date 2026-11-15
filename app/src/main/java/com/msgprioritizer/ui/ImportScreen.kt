package com.msgprioritizer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msgprioritizer.importing.Importer
import kotlinx.coroutines.launch

/** Bring in real messages without notification access: a chat export file, or pasted text. */
@Composable
fun ImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pasted by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { result = Importer.importUri(context, uri, null) }
    }

    BackHandler(onBack = onBack)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Add your messages", style = MaterialTheme.typography.headlineSmall)

        result?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) }

        Text("Share a WhatsApp chat (easiest)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "1. In WhatsApp, open a chat.\n" +
                "2. Tap ⋮ → More → Export chat → Without media.\n" +
                "3. Choose this app in the share list.\n" +
                "Its newest messages appear in your inbox, ranked.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Text("Or import a saved chat file", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(
            onClick = { pickFile.launch(arrayOf("text/*", "application/zip")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Choose chat file (.txt or .zip)") }

        Text("Or paste messages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it },
            label = { Text("Paste one message, or copied chat lines") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                scope.launch {
                    result = Importer.importText(context, pasted, null)
                    if (!result.orEmpty().startsWith("Paste")) pasted = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Rank these messages") }

        Text(
            "Your messages stay on this phone. The app has no internet permission.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
