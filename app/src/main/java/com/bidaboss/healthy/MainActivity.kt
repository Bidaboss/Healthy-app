package com.bidaboss.healthy

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.bidaboss.healthy.importexport.ui.ImportViewModel
import com.bidaboss.healthy.today.widget.TodayWidget
import com.bidaboss.healthy.ui.HealthyApp
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.theme.HealthyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val importViewModel: ImportViewModel by viewModels {
        healthyViewModelFactory { ImportViewModel(it.importParser, it.importRepository) }
    }

    /** Augmente à chaque import reçu d'une autre app, pour ouvrir l'écran Importer. */
    private var importRequests by mutableIntStateOf(0)

    /** Collage demandé par le widget : Android ne donne le presse-papiers qu'une fois la fenêtre au premier plan. */
    private var pasteWhenFocused = false

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by (application as HealthyApplication).container.appearance.theme.collectAsState()
            HealthyTheme(theme) {
                HealthyApp(
                    importViewModel = importViewModel,
                    importRequests = importRequests,
                    onPickFile = { pickFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
        }
        if (savedInstanceState == null) handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /** « Ouvrir avec Healthy » (VIEW) ou « Partager vers Healthy » (SEND). */
    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_PASTE_MEAL -> {
                importRequests++
                pasteWhenFocused = true
                if (hasWindowFocus()) pasteClipboard()
            }

            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                importRequests++
                importFromUri(uri)
            }

            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> {
                        importRequests++
                        importFromUri(stream)
                    }
                    !text.isNullOrBlank() -> {
                        importRequests++
                        importViewModel.submitText(text)
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pasteWhenFocused) pasteClipboard()
    }

    private fun pasteClipboard() {
        pasteWhenFocused = false
        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            importViewModel.reportError("Rien à coller : copie d'abord le repas envoyé par Claude, puis réessaie.")
        } else {
            importViewModel.submitText(text)
        }
    }

    override fun onStop() {
        super.onStop()
        // On revient à l'écran d'accueil : le widget montre ce qui vient d'être noté.
        lifecycleScope.launch { runCatching { TodayWidget.refresh(applicationContext) } }
    }

    private fun importFromUri(uri: Uri) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { readText(uri) }
            if (text == null) {
                importViewModel.reportError("Impossible de lire ce fichier.")
            } else {
                importViewModel.submitText(text)
            }
        }
    }

    private fun readText(uri: Uri): String? = try {
        contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
            if (bytes.size > MAX_IMPORT_BYTES) null else bytes.toString(Charsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (buffer.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    companion object {
        /** Bouton « Coller un repas » du widget : ouvre l'import et colle le presse-papiers. */
        const val ACTION_PASTE_MEAL = "com.bidaboss.healthy.action.PASTE_MEAL"

        /** Un import fait quelques kilo-octets, une sauvegarde quelques mégaoctets : au-delà de 30 Mo, on refuse. */
        private const val MAX_IMPORT_BYTES = 30 * 1024 * 1024
    }
}
