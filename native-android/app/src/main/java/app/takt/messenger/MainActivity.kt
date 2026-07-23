package app.takt.messenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.takt.messenger.ui.TaktApp
import app.takt.messenger.ui.theme.TaktTheme

class MainActivity : ComponentActivity() {
    private val messenger: MessengerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        messenger.handleDeepLink(intent?.data)
        setContent {
            val state by messenger.state.collectAsState()
            TaktTheme(settings = state.appearance) {
                TaktApp(viewModel = messenger)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        messenger.handleDeepLink(intent.data)
    }
}
