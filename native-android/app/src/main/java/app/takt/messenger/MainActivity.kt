package app.takt.messenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import app.takt.messenger.ui.TaktApp
import app.takt.messenger.ui.theme.TaktTheme

class MainActivity : ComponentActivity() {
    private var inviteCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        inviteCode = extractInvite(intent)
        setContent {
            TaktTheme {
                val messenger: MessengerViewModel = viewModel()
                TaktApp(viewModel = messenger, inviteCode = inviteCode)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inviteCode = extractInvite(intent)
    }

    private fun extractInvite(intent: Intent): String? {
        val uri = intent.data ?: return null
        val code = when {
            uri.scheme == "https" && uri.host == "takt-messenger.vercel.app" -> uri.pathSegments.lastOrNull()
            uri.scheme == "takt" && uri.host == "join" -> uri.pathSegments.lastOrNull()
            else -> null
        }
        return code?.uppercase()?.takeIf { it.matches(Regex("[A-F0-9]{10}")) }
    }
}
