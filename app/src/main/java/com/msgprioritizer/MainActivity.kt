package com.msgprioritizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.msgprioritizer.data.AppDatabase
import com.msgprioritizer.data.Prefs
import com.msgprioritizer.importing.Importer
import com.msgprioritizer.service.Alerts
import com.msgprioritizer.service.MessageListener
import com.msgprioritizer.ui.App
import com.msgprioritizer.ui.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var hasAccess by mutableStateOf(!BuildConfig.LIVE)
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // Always-dark app, so keep light status bar icons whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (BuildConfig.LIVE) Alerts.ensureChannel(this)
        if (BuildConfig.LIVE && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val db = AppDatabase.get(this)
        val dao = db.messages()
        // Live messages expire after a week. Imported chats stay until the user removes them.
        if (BuildConfig.LIVE) {
            lifecycleScope.launch(Dispatchers.IO) {
                dao.deleteOlderThan(System.currentTimeMillis() - KEEP_MS)
            }
        }
        if (savedInstanceState == null) handleShare(intent)

        val prefs = Prefs(this)
        setContent {
            AppTheme {
                App(
                    hasAccess = hasAccess,
                    dao = dao,
                    stats = db.contactStats(),
                    prefs = prefs,
                    onGrantAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A chat exported from WhatsApp (or any text) shared to this app. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        lifecycleScope.launch {
            val summary = when {
                uri != null -> Importer.importUri(this@MainActivity, uri, subject)
                text != null -> Importer.importText(this@MainActivity, text, subject)
                else -> return@launch
            }
            Toast.makeText(this@MainActivity, summary, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check every time the user comes back from the system settings screen.
        // The Lite edition never needs notification access.
        hasAccess = !BuildConfig.LIVE ||
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        // Access is on but Android isn't delivering to us (e.g. after a battery saver): ask for a rebind.
        if (BuildConfig.LIVE && hasAccess && !MessageListener.connected.value) MessageListener.rebind(this)
    }

    companion object {
        private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000
    }
}
