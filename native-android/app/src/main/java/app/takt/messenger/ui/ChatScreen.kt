package app.takt.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.takt.messenger.CallConnectionState
import app.takt.messenger.MessengerUiState
import app.takt.messenger.data.DeliveryStatus
import app.takt.messenger.data.MessengerMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: MessengerUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onAttachImage: () -> Unit,
    onAttachFile: () -> Unit,
    onRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onCancelRecord: () -> Unit,
    onReply: (MessengerMessage?) -> Unit,
    onForward: (MessengerMessage?) -> Unit,
    onEdit: (MessengerMessage?) -> Unit,
    onDelete: (MessengerMessage, Boolean) -> Unit,
    onReact: (MessengerMessage, String) -> Unit,
    onOpenMedia: (MessengerMessage) -> Unit,
    onUpdateTyping: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onStartCall: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
) {
    val chat = state.activeChat ?: return
    var draft by rememberSaveable(chat.id) { mutableStateOf(chat.settings.draft) }
    var menuMessage by remember { mutableStateOf<MessengerMessage?>(null) }
    var showChatMenu by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.editing?.id) {
        state.editing?.let { draft = it.body }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        val typing = chat.typing.firstOrNull()
                        Text(
                            when {
                                typing != null -> "${typing.displayName} ${typingText(typing.mode)}"
                                chat.members.any { it.isOnline && it.id != state.profile?.id } -> "в сети"
                                else -> "${chat.members.size} участник${if (chat.members.size == 1) "" else "а"}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = { onStartCall(false) }) { Icon(Icons.Default.Call, "Аудиозвонок") }
                    IconButton(onClick = { onStartCall(true) }) { Icon(Icons.Default.Videocam, "Видеозвонок") }
                    IconButton(onClick = { showChatMenu = true }) { Icon(Icons.Default.MoreVert, "Меню чата") }
                },
            )
        },
        bottomBar = {
            Composer(
                draft = draft,
                recording = state.recording,
                recordingSeconds = state.recordingSeconds,
                busy = state.busy,
                replyingTo = state.replyingTo,
                forwarding = state.forwarding,
                editing = state.editing,
                onDraft = {
                    draft = it
                    if (it.isNotBlank()) onUpdateTyping()
                },
                onSend = { if (draft.isNotBlank()) { onSend(draft); draft = "" } },
                onImage = onAttachImage,
                onFile = onAttachFile,
                onRecord = onRecord,
                onStopRecord = onStopRecord,
                onCancelRecord = onCancelRecord,
                onCancelContext = { onReply(null); onForward(null); onEdit(null); draft = "" },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.callState !is CallConnectionState.Idle) {
                ActiveCallBar(
                    muted = state.callMuted,
                    onOpen = {},
                    onToggleMute = onToggleMute,
                    onEnd = onEndCall,
                )
            } else if (chat.activeCall != null) {
                IncomingCallBar(chat.activeCall.video, onJoin = { onStartCall(chat.activeCall.video) })
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (chat.messages.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxSize().padding(top = 120.dp), contentAlignment = Alignment.TopCenter) {
                            Text("Это начало беседы. Сообщения защищены правилами доступа чата.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(chat.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        mine = message.senderId == state.profile?.id,
                        appearance = state.appearance,
                        onMenu = { menuMessage = message },
                        onOpenMedia = { onOpenMedia(message) },
                        onReaction = { emoji -> onReact(message, emoji) },
                    )
                }
            }
        }
    }

    menuMessage?.let { message ->
        MessageMenuDialog(
            message = message,
            mine = message.senderId == state.profile?.id,
            onReply = { onReply(message); menuMessage = null },
            onForward = { onForward(message); menuMessage = null },
            onEdit = { onEdit(message); menuMessage = null },
            onDeleteMine = { onDelete(message, false); menuMessage = null },
            onDeleteAll = { onDelete(message, true); menuMessage = null },
            onReact = { emoji -> onReact(message, emoji); menuMessage = null },
            onDismiss = { menuMessage = null },
        )
    }
    if (showChatMenu) {
        AlertDialog(
            onDismissRequest = { showChatMenu = false },
            title = { Text(chat.title) },
            text = {
                Column {
                    TextButton(onClick = { onPin(); showChatMenu = false }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PushPin, null); Spacer(Modifier.width(8.dp)); Text(if (chat.settings.pinned) "Открепить чат" else "Закрепить чат")
                    }
                    TextButton(onClick = { onArchive(); showChatMenu = false }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Archive, null); Spacer(Modifier.width(8.dp)); Text("Архивировать чат")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChatMenu = false }) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun IncomingCallBar(video: Boolean, onJoin: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(10.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (video) Icons.Default.Videocam else Icons.Default.Call, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (video) "Идёт видеозвонок" else "Идёт аудиозвонок", fontWeight = FontWeight.Bold)
                Text("Присоединитесь, не выходя из чата", style = MaterialTheme.typography.bodySmall)
            }
            FilledIconButton(onClick = onJoin) { Icon(Icons.Default.Call, "Войти") }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessengerMessage,
    mine: Boolean,
    appearance: app.takt.messenger.data.AppearanceSettings,
    onMenu: () -> Unit,
    onOpenMedia: () -> Unit,
    onReaction: (String) -> Unit,
) {
    val arrangement = if (mine) Arrangement.End else Arrangement.Start
    Row(Modifier.fillMaxWidth(), horizontalArrangement = arrangement, verticalAlignment = Alignment.Bottom) {
        if (!mine) {
            Avatar(message.senderName, message.senderColor, appearance, 32)
            Spacer(Modifier.width(7.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth(.84f)) {
            if (!mine) Text(message.senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            Surface(
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            ) {
                Column(Modifier.padding(10.dp)) {
                    message.replyPreview?.let { reply ->
                        Row(Modifier.fillMaxWidth().background(if (mine) MaterialTheme.colorScheme.onPrimary.copy(alpha = .12f) else MaterialTheme.colorScheme.primary.copy(alpha = .1f)).padding(7.dp)) {
                            Column {
                                Text(reply.senderName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(reply.body.ifBlank { "Вложение" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    when {
                        message.kind == "image" && message.attachment != null -> MediaRow(message, Icons.Default.Image, "Фотография", onOpenMedia)
                        message.kind == "voice" && message.attachment != null -> MediaRow(message, Icons.Default.AudioFile, "Голосовое · ${formatDuration(message.attachment.durationSeconds ?: 0)}", onOpenMedia)
                        message.kind == "file" && message.attachment != null -> MediaRow(message, Icons.Default.FilePresent, message.attachment.fileName, onOpenMedia)
                        else -> Text(message.body, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (message.kind != "text" && message.body.isNotBlank()) {
                        Spacer(Modifier.height(5.dp)); Text(message.body, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (message.editedAt != null) Text("изменено", style = MaterialTheme.typography.labelSmall, color = if (mine) MaterialTheme.colorScheme.onPrimary.copy(alpha = .7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(5.dp))
                        Text(formatChatTime(message.createdAt), style = MaterialTheme.typography.labelSmall, color = if (mine) MaterialTheme.colorScheme.onPrimary.copy(alpha = .7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (mine) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                when (message.status) {
                                    DeliveryStatus.Read -> Icons.Default.DoneAll
                                    DeliveryStatus.Delivered -> Icons.Default.DoneAll
                                    else -> Icons.Default.Check
                                },
                                message.status.name,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        IconButton(onClick = onMenu, modifier = Modifier.size(25.dp)) { Icon(Icons.Default.MoreVert, "Действия", modifier = Modifier.size(16.dp)) }
                    }
                }
            }
            if (message.reactions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                    message.reactions.forEach { reaction ->
                        AssistChip(onClick = { onReaction(reaction.emoji) }, label = { Text("${reaction.emoji} ${reaction.count}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaRow(message: MessengerMessage, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(42.dp), shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .16f)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null) }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            message.attachment?.let { Text(formatBytes(it.sizeBytes), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    recording: Boolean,
    recordingSeconds: Int,
    busy: Boolean,
    replyingTo: MessengerMessage?,
    forwarding: MessengerMessage?,
    editing: MessengerMessage?,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onImage: () -> Unit,
    onFile: () -> Unit,
    onRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onCancelRecord: () -> Unit,
    onCancelContext: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 8.dp, vertical = 7.dp)) {
            val contextMessage = editing ?: replyingTo ?: forwarding
            if (contextMessage != null && !recording) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(when { editing != null -> "Редактирование"; forwarding != null -> "Пересылка"; else -> "Ответ ${contextMessage.senderName}" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(contextMessage.body.ifBlank { "Вложение" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onCancelContext) { Icon(Icons.Default.Close, "Отменить") }
                }
                HorizontalDivider()
            }
            if (recording) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancelRecord) { Icon(Icons.Default.Close, "Отменить", tint = MaterialTheme.colorScheme.error) }
                    Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Запись ${formatDuration(recordingSeconds)}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    FilledIconButton(onClick = onStopRecord) { Icon(Icons.Default.Stop, "Отправить голосовое") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = onImage, enabled = !busy) { Icon(Icons.Default.AddPhotoAlternate, "Фото") }
                    IconButton(onClick = onFile, enabled = !busy) { Icon(Icons.Default.AttachFile, "Файл") }
                    TextField(
                        value = draft,
                        onValueChange = onDraft,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Сообщение") },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    Spacer(Modifier.width(5.dp))
                    FilledIconButton(onClick = if (draft.isBlank()) onRecord else onSend, enabled = !busy) {
                        Icon(if (draft.isBlank()) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send, if (draft.isBlank()) "Голосовое" else "Отправить")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageMenuDialog(
    message: MessengerMessage,
    mine: Boolean,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onDeleteMine: () -> Unit,
    onDeleteAll: () -> Unit,
    onReact: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сообщение") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("👍", "❤️", "😂", "🔥", "👀").forEach { emoji ->
                        OutlinedButton(onClick = { onReact(emoji) }, modifier = Modifier.weight(1f)) { Text(emoji) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onReply, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Reply, null); Spacer(Modifier.width(8.dp)); Text("Ответить") }
                TextButton(onClick = onForward, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("Переслать") }
                if (mine && message.kind == "text" && message.deletedAt == null) {
                    TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("Редактировать") }
                }
                HorizontalDivider()
                TextButton(onClick = onDeleteMine, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Удалить у себя") }
                if (mine) TextButton(onClick = onDeleteAll, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Удалить у всех") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

private fun typingText(mode: String): String = when (mode) {
    "recording" -> "записывает голосовое"
    "uploading" -> "отправляет файл"
    else -> "печатает…"
}
