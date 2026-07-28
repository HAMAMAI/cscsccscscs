package app.takt.messenger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.takt.messenger.call.LiveKitVideoSurface
import app.takt.messenger.data.ActiveCall
import app.takt.messenger.data.ConversationSummary
import app.takt.messenger.data.UserProfile
import io.livekit.android.room.Room

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaktCallScreen(
    call: ActiveCall,
    room: Room?,
    muted: Boolean,
    cameraEnabled: Boolean,
    onBack: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onEnd: () -> Unit,
) {
    // Back only minimizes the in-call view; the call keeps running until ended.
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color(0xFF0C131A),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (call.video) "Видеозвонок" else "Аудиозвонок") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Свернуть звонок") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (call.video && (room != null)) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF17212B),
                ) {
                    LiveKitVideoSurface(room = room, modifier = Modifier.fillMaxSize())
                }
            } else {
                Spacer(Modifier.weight(1f))
                Surface(
                    modifier = Modifier.size(112.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (call.video) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(if (call.video) "Подключаем видео…" else "Идёт разговор", style = MaterialTheme.typography.titleMedium)
                Text("Звонок защищён доступом вашей беседы", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onToggleMute) {
                    Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (muted) "Вкл. микрофон" else "Выключить")
                }
                if (call.video) {
                    FilledTonalButton(onClick = onToggleCamera) {
                        Icon(if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (cameraEnabled) "Камера" else "Камера выкл.")
                    }
                }
                FilledIconButton(onClick = onEnd, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Close, "Завершить звонок")
                }
            }
        }
    }
}

@Composable
internal fun TaktProfileEditorDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (name: String, username: String, about: String, color: String) -> Unit,
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.displayName) }
    var username by rememberSaveable(profile.id) { mutableStateOf(profile.username.orEmpty()) }
    var about by rememberSaveable(profile.id) { mutableStateOf(profile.about) }
    var color by rememberSaveable(profile.id) { mutableStateOf(profile.avatarColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Профиль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Имя") }, singleLine = true)
                OutlinedTextField(
                    username,
                    { username = it.lowercase().replace("@", "") },
                    label = { Text("Username для ссылки") },
                    prefix = { Text("@") },
                    singleLine = true,
                )
                OutlinedTextField(about, { about = it }, label = { Text("О себе") }, maxLines = 3)
                OutlinedTextField(color, { color = it.uppercase() }, label = { Text("Цвет (#RRGGBB)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, username, about, color) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
internal fun TaktReportDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var reason by rememberSaveable(profile.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пожаловаться") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Укажите причину жалобы на ${profile.displayName}.")
                OutlinedTextField(reason, { reason = it }, label = { Text("Причина") }, minLines = 3, maxLines = 5)
            }
        },
        confirmButton = { TextButton(onClick = { if (reason.trim().length >= 3) onSend(reason.trim()) }) { Text("Отправить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
internal fun TaktForwardDialog(
    conversations: List<ConversationSummary>,
    activeConversationId: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переслать в чат") },
        text = {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(conversations.filterNot { it.id == activeConversationId }, key = { it.id }) { chat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(chat.title, chat.avatarColor, app.takt.messenger.data.AppearanceSettings(), 38)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chat.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                chat.lastMessage?.body?.ifBlank { "Вложение" } ?: "Нет сообщений",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { onPick(chat.id) }) { Text("Выбрать") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
