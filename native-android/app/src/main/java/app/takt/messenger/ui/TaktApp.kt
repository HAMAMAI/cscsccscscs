package app.takt.messenger.ui

import android.Manifest
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.takt.messenger.HomeSection
import app.takt.messenger.MessengerViewModel
import app.takt.messenger.data.MediaAttachment
import app.takt.messenger.data.MessengerMessage
import java.io.File

@Composable
fun TaktApp(viewModel: MessengerViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.sendUri(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.sendUri(uri)
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording() else viewModel.clearError()
    }

    BackHandler(enabled = state.activeChat != null) { viewModel.closeChat() }

    Box(Modifier.fillMaxSize()) {
        when {
            state.session == null -> AuthScreen(
                busy = state.busy,
                confirmationEmail = state.confirmationEmail,
                onSignIn = viewModel::signIn,
                onSignUp = viewModel::signUp,
                onGoogle = {
                    viewModel.startGoogleOAuth()?.let { url ->
                        CustomTabsIntent.Builder().build().launchUrl(context, android.net.Uri.parse(url))
                    }
                },
                onDismissConfirmation = viewModel::clearConfirmation,
            )

            state.activeChat != null -> ChatScreen(
                state = state,
                onBack = viewModel::closeChat,
                onSend = viewModel::sendText,
                onAttachImage = { imagePicker.launch("image/*") },
                onAttachFile = { filePicker.launch("*/*") },
                onRecord = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                onStopRecord = { viewModel.stopRecording(cancel = false) },
                onCancelRecord = { viewModel.stopRecording(cancel = true) },
                onReply = viewModel::setReply,
                onForward = viewModel::setForward,
                onEdit = viewModel::setEditing,
                onDelete = viewModel::deleteMessage,
                onReact = viewModel::react,
                onOpenMedia = viewModel::openMedia,
                onUpdateTyping = viewModel::updateTyping,
                onPin = { viewModel.setPinned(pinned = !(state.activeChat?.settings?.pinned ?: false)) },
                onArchive = { viewModel.setPinned(archived = true) },
                onStartCall = viewModel::startCall,
                onToggleMute = viewModel::toggleMute,
                onEndCall = viewModel::endCall,
            )

            else -> HomeScreen(
                state = state,
                onRefresh = viewModel::refresh,
                onSection = viewModel::selectSection,
                onOpenChat = { viewModel.openChat(it.id) },
                onPeopleQuery = viewModel::updatePeopleQuery,
                onOpenPerson = viewModel::openDirectChat,
                onCreateGroup = viewModel::createGroup,
                onSaveProfile = viewModel::saveProfile,
                onTheme = viewModel::updateTheme,
                onAvatarShape = viewModel::updateAvatarShape,
                onCreateFolder = viewModel::createFolder,
                onSignOut = viewModel::signOut,
            )
        }

        if (state.callState !is app.takt.messenger.CallConnectionState.Idle && state.activeChat == null) {
            ActiveCallBar(
                muted = state.callMuted,
                onOpen = { state.activeChat?.let { viewModel.openChat(it.id) } },
                onToggleMute = viewModel::toggleMute,
                onEnd = viewModel::endCall,
            )
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("Понятно") } },
            title = { Text("Не удалось выполнить действие") },
            text = { Text(message) },
        )
    }
    state.notice?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearNotice,
            confirmButton = { TextButton(onClick = viewModel::clearNotice) { Text("Готово") } },
            title = { Text("Такт") },
            text = { Text(message) },
        )
    }
    state.mediaPreview?.let { preview ->
        MediaPreviewDialog(
            title = preview.title,
            mimeType = preview.mimeType,
            bytes = preview.bytes,
            durationSeconds = preview.durationSeconds,
            onClose = viewModel::closeMediaPreview,
        )
    }
}

@Composable
private fun MediaPreviewDialog(
    title: String,
    mimeType: String,
    bytes: ByteArray,
    durationSeconds: Int?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var playing by remember(bytes) { mutableStateOf(false) }
    var player by remember(bytes) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(bytes) {
        onDispose {
            runCatching { player?.release() }
        }
    }
    Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Text(title, modifier = Modifier.align(Alignment.CenterStart), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                when {
                    mimeType.startsWith("image/") -> {
                        val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = title,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else Text("Изображение не удалось прочитать")
                    }

                    mimeType.startsWith("audio/") -> {
                        Text("Голосовое сообщение${durationSeconds?.let { " · ${formatDuration(it)}" }.orEmpty()}")
                        Button(onClick = {
                            if (playing) {
                                player?.pause()
                                playing = false
                            } else {
                                if (player == null) {
                                    val file = File(context.cacheDir, "preview-${System.nanoTime()}")
                                    file.writeBytes(bytes)
                                    player = MediaPlayer().apply {
                                        setDataSource(file.absolutePath)
                                        setOnCompletionListener { playing = false; file.delete() }
                                        prepare()
                                    }
                                }
                                player?.start()
                                playing = true
                            }
                        }) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text(if (playing) "Пауза" else "Слушать")
                        }
                    }

                    else -> {
                        Text("Файл открыт внутри приложения. Формат: $mimeType")
                        Text("Размер: ${formatBytes(bytes.size)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

internal fun formatBytes(bytes: Int): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format(java.util.Locale.US, "%.1f МБ", bytes / 1024f / 1024f)
}

internal fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
