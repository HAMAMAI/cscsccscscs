package app.takt.messenger.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.takt.messenger.CallConnectionState
import app.takt.messenger.HomeDestination
import app.takt.messenger.HomeSection
import app.takt.messenger.MessengerUiState
import app.takt.messenger.MessengerViewModel
import app.takt.messenger.data.ChatFolder
import app.takt.messenger.data.PrivacySettings
import app.takt.messenger.data.UserProfile
import java.io.File

@Composable
fun TaktApp(viewModel: MessengerViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pendingCallVideo by remember { mutableStateOf<Boolean?>(null) }
    var pendingCallTarget by remember { mutableStateOf<UserProfile?>(null) }
    var profileEditor by remember { mutableStateOf<UserProfile?>(null) }
    var reportTarget by remember { mutableStateOf<UserProfile?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.sendUri(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.sendUri(uri)
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording() else viewModel.showError("Для голосового сообщения нужен доступ к микрофону")
    }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val video = pendingCallVideo ?: return@rememberLauncherForActivityResult
        val target = pendingCallTarget
        pendingCallVideo = null
        pendingCallTarget = null
        val microphoneGranted = granted[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = !video || granted[Manifest.permission.CAMERA] == true
        if (microphoneGranted && cameraGranted) {
            if (target == null) viewModel.startCall(video) else viewModel.startDirectCall(target, video)
        } else {
            viewModel.showError(if (video) "Для видеозвонка нужны разрешения на камеру и микрофон" else "Для звонка нужен доступ к микрофону")
        }
    }
    val requestCall: (Boolean, UserProfile?) -> Unit = { video, target ->
        pendingCallVideo = video
        pendingCallTarget = target
        callPermission.launch(
            if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            else arrayOf(Manifest.permission.RECORD_AUDIO),
        )
    }
    val copyLink: (String) -> Unit = { link ->
        (context.getSystemService(ClipboardManager::class.java))?.setPrimaryClip(ClipData.newPlainText("Ссылка Такт", link))
        viewModel.showNotice("Ссылка скопирована")
    }
    val shareLink: (String) -> Unit = { link ->
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link),
                "Поделиться профилем",
            ),
        )
    }

    BackHandler(enabled = state.destination != HomeDestination.None) { viewModel.closeDestination() }

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
                onSearchTargetConsumed = viewModel::clearSearchTarget,
                onUpdateTyping = viewModel::updateTyping,
                onSaveDraft = viewModel::saveCurrentDraft,
                onPin = { viewModel.setPinned(pinned = !(state.activeChat?.settings?.pinned ?: false)) },
                onArchive = { viewModel.setPinned(archived = !(state.activeChat?.settings?.archived ?: false)) },
                onMute = viewModel::muteCurrentChat,
                onUnmute = viewModel::unmuteCurrentChat,
                onAssignFolder = viewModel::assignCurrentChatFolder,
                onClearFolder = viewModel::clearCurrentChatFolder,
                onOpenProfile = { profile -> viewModel.openDestination(HomeDestination.PersonProfile(profile)) },
                onStartCall = { video -> requestCall(video, null) },
                onOpenCall = viewModel::openCallScreen,
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
                onPersonProfile = { profile -> viewModel.openDestination(HomeDestination.PersonProfile(profile)) },
                onOpenDestination = viewModel::openDestination,
                onShowAllChats = { viewModel.showFolderChats(null) },
                onTheme = viewModel::updateTheme,
                onAvatarShape = viewModel::updateAvatarShape,
                onSignOut = viewModel::signOut,
            )
        }

        if (state.callState !is CallConnectionState.Idle && state.activeChat == null) {
            ActiveCallBar(
                muted = state.callMuted,
                onOpen = viewModel::openCallScreen,
                onToggleMute = viewModel::toggleMute,
                onEnd = viewModel::endCall,
            )
        }
    }

    when (val destination = state.destination) {
        HomeDestination.None -> Unit
        HomeDestination.SelfProfile -> state.profile?.let { profile ->
            TaktSelfProfileActionSheet(
                profile = profile.toSelfProfileUi(),
                onDismissRequest = viewModel::closeDestination,
                onChangeColor = { profileEditor = profile },
                onChangeName = { profileEditor = profile },
                onCopyProfileLink = copyLink,
                onShareProfileLink = shareLink,
            )
        }

        HomeDestination.Privacy -> TaktPrivacyScreen(
            privacy = state.privacy.toTaktPrivacyUi(),
            onBack = viewModel::closeDestination,
            onPrivacyChange = { updated -> viewModel.savePrivacy(updated.toPrivacySettings()) },
            onOpenBlockedUsers = { viewModel.openDestination(HomeDestination.BlockedUsers) },
        )

        HomeDestination.BlockedUsers -> TaktBlockedUsersScreen(
            users = state.blockedUsers.map { blocked ->
                TaktBlockedUserUi(
                    id = blocked.profile.id,
                    displayName = blocked.profile.displayName,
                    username = blocked.profile.username,
                    avatarColor = blocked.profile.avatarColor,
                    blockedAtText = blocked.blockedAt?.let { "Заблокирован(а) ${formatChatTime(it)}" }.orEmpty(),
                )
            },
            onBack = viewModel::closeDestination,
            onOpenProfile = { userId ->
                state.blockedUsers.firstOrNull { it.profile.id == userId }?.profile?.let { profile ->
                    viewModel.openDestination(HomeDestination.PersonProfile(profile))
                }
            },
            onUnblock = { userId ->
                state.blockedUsers.firstOrNull { it.profile.id == userId }?.profile?.let { profile -> viewModel.toggleBlock(profile, false) }
            },
            onAddBlock = {
                viewModel.closeDestination()
                viewModel.selectSection(HomeSection.People)
                viewModel.showNotice("Найдите пользователя и откройте его профиль, чтобы заблокировать")
            },
            isLoading = state.loading,
        )

        HomeDestination.Folders -> TaktFoldersScreen(
            folders = state.folders.map { folder -> folder.toTaktFolderUi(state) },
            onBack = viewModel::closeDestination,
            onOpenAllChats = { viewModel.showFolderChats(null) },
            onOpenFolder = viewModel::showFolderChats,
            onCreateFolder = viewModel::createFolder,
            onUpdateFolder = { id, name, color ->
                state.folders.firstOrNull { it.id == id }?.let { original ->
                    viewModel.updateFolder(original.copy(name = name, color = color))
                }
            },
            onDeleteFolder = { id -> state.folders.firstOrNull { it.id == id }?.let(viewModel::deleteFolder) },
        )

        HomeDestination.Search -> TaktGlobalSearchScreen(
            query = state.messageSearchQuery,
            results = state.toSearchResults(),
            recentQueries = state.recentSearchQueries,
            onBack = viewModel::closeDestination,
            onQueryChange = viewModel::updateMessageSearch,
            onOpenResult = { result ->
                when (result.kind) {
                    TaktSearchResultKind.Chat -> viewModel.openChat(result.id)
                    TaktSearchResultKind.Person -> state.globalPeople.firstOrNull { it.id == result.id }?.let { profile ->
                        viewModel.openDestination(HomeDestination.PersonProfile(profile))
                    }
                    TaktSearchResultKind.Message -> state.messageSearchResults.firstOrNull { it.message.id == result.id }?.let(viewModel::openSearchResult)
                    else -> Unit
                }
            },
            onClearRecent = viewModel::clearRecentSearches,
            isLoading = state.loading,
        )

        is HomeDestination.PersonProfile -> {
            val profile = destination.profile
            TaktPersonProfileScreen(
                profile = profile.toPersonProfileUi(state),
                onBack = viewModel::closeDestination,
                onMessage = {
                    viewModel.closeDestination()
                    viewModel.openDirectChat(profile)
                },
                onAudioCall = { requestCall(false, profile) },
                onVideoCall = { requestCall(true, profile) },
                onBlock = { viewModel.toggleBlock(profile, true) },
                onUnblock = { viewModel.toggleBlock(profile, false) },
                onReport = { reportTarget = profile },
                onCopyProfileLink = copyLink,
            )
        }
    }

    val connectedCall = state.callState as? CallConnectionState.Connected
    if (state.callScreenVisible && connectedCall != null) {
        TaktCallScreen(
            call = connectedCall.call,
            room = viewModel.currentCallRoom(),
            muted = state.callMuted,
            cameraEnabled = state.callCameraEnabled,
            onBack = viewModel::closeCallScreen,
            onToggleMute = viewModel::toggleMute,
            onToggleCamera = viewModel::toggleCamera,
            onEnd = viewModel::endCall,
        )
    }

    state.forwarding?.let {
        TaktForwardDialog(
            conversations = state.conversations,
            activeConversationId = state.activeChat?.id,
            onDismiss = { viewModel.setForward(null) },
            onPick = viewModel::forwardMessageTo,
        )
    }

    profileEditor?.let { profile ->
        TaktProfileEditorDialog(
            profile = profile,
            onDismiss = { profileEditor = null },
            onSave = { name, username, about, color ->
                viewModel.saveProfile(name, username, about, color)
                profileEditor = null
            },
        )
    }
    reportTarget?.let { profile ->
        TaktReportDialog(
            profile = profile,
            onDismiss = { reportTarget = null },
            onSend = { reason ->
                viewModel.submitReport(profile, reason)
                reportTarget = null
            },
        )
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

private fun UserProfile.profileLink(): String = "takt://user?id=$id"

private fun UserProfile.toSelfProfileUi() = TaktSelfProfileUi(
    id = id,
    displayName = displayName,
    username = username,
    avatarColor = avatarColor,
    publicLink = profileLink(),
)

private fun UserProfile.toPersonProfileUi(state: MessengerUiState) = TaktPersonProfileUi(
    id = id,
    displayName = displayName,
    username = username,
    about = about,
    avatarColor = avatarColor,
    statusText = if (isOnline) "в сети" else "был(а) недавно",
    publicLink = profileLink(),
    isBlocked = state.blockedUsers.any { it.profile.id == id },
)

private fun String.toTaktAudience(): TaktPrivacyAudience = when (lowercase()) {
    // Contacts is deliberately fail-closed until a real address-book model
    // exists, so surface the honest equivalent in the UI.
    "contacts" -> TaktPrivacyAudience.Nobody
    "nobody" -> TaktPrivacyAudience.Nobody
    else -> TaktPrivacyAudience.Everyone
}

private fun TaktPrivacyAudience.toPrivacyScope(): String = name.lowercase()

private fun PrivacySettings.toTaktPrivacyUi() = TaktPrivacyUi(
    showAvatarTo = showAvatarTo.toTaktAudience(),
    showLastSeenTo = showLastSeenTo.toTaktAudience(),
    allowCallsFrom = allowCallsFrom.toTaktAudience(),
    allowMessagesFrom = allowMessagesFrom.toTaktAudience(),
    allowGroupInvitesFrom = allowGroupInvitesFrom.toTaktAudience(),
)

private fun TaktPrivacyUi.toPrivacySettings() = PrivacySettings(
    showAvatarTo = showAvatarTo.toPrivacyScope(),
    showLastSeenTo = showLastSeenTo.toPrivacyScope(),
    allowCallsFrom = allowCallsFrom.toPrivacyScope(),
    allowMessagesFrom = allowMessagesFrom.toPrivacyScope(),
    allowGroupInvitesFrom = allowGroupInvitesFrom.toPrivacyScope(),
)

private fun ChatFolder.toTaktFolderUi(state: MessengerUiState) = TaktFolderUi(
    id = id,
    name = name,
    color = color,
    position = position,
    chatCount = state.conversations.count { it.settings.folderId == id },
)

private fun MessengerUiState.toSearchResults(): List<TaktSearchResultUi> {
    val normalized = messageSearchQuery.trim()
    if (normalized.length < 2) return emptyList()
    val needle = normalized.lowercase()
    return buildList {
        conversations.filter { chat ->
            chat.title.lowercase().contains(needle) || chat.lastMessage?.body?.lowercase()?.contains(needle) == true
        }.take(20).forEach { chat ->
            add(
                TaktSearchResultUi(
                    id = chat.id,
                    title = chat.title,
                    subtitle = chat.lastMessage?.body?.ifBlank { "Вложение" } ?: "Чат",
                    kind = TaktSearchResultKind.Chat,
                    avatarColor = chat.avatarColor,
                    timeLabel = formatChatTime(chat.updatedAt),
                ),
            )
        }
        globalPeople.take(20).forEach { person ->
            add(
                TaktSearchResultUi(
                    id = person.id,
                    title = person.displayName,
                    subtitle = person.username?.let { "@$it" } ?: person.about.ifBlank { "Пользователь" },
                    kind = TaktSearchResultKind.Person,
                    avatarColor = person.avatarColor,
                ),
            )
        }
        messageSearchResults.take(50).forEach { result ->
            val conversation = conversations.firstOrNull { it.id == result.conversationId }
            add(
                TaktSearchResultUi(
                    id = result.message.id,
                    title = result.message.senderName,
                    subtitle = "${conversation?.title ?: "Чат"}: ${result.message.body.ifBlank { "Вложение" }}",
                    kind = TaktSearchResultKind.Message,
                    avatarColor = result.message.senderColor,
                    timeLabel = formatChatTime(result.message.createdAt),
                ),
            )
        }
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
                        Text("Файл готов к открытию. Формат: $mimeType")
                        Text("Размер: ${formatBytes(bytes.size)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { openPreviewExternally(context, title, mimeType, bytes, share = false) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Открыть") }
                    OutlinedButton(
                        onClick = { openPreviewExternally(context, title, mimeType, bytes, share = true) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Поделиться") }
                }
            }
        }
    }
}

private fun openPreviewExternally(
    context: Context,
    title: String,
    mimeType: String,
    bytes: ByteArray,
    share: Boolean,
) {
    runCatching {
        val directory = File(context.cacheDir, "attachments").apply { mkdirs() }
        val safeTitle = title
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { "attachment" }
        val file = File(directory, "${System.nanoTime()}-$safeTitle").apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val type = mimeType.ifBlank { "application/octet-stream" }
        val intent = if (share) {
            Intent(Intent.ACTION_SEND)
                .setType(type)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .also { it.clipData = ClipData.newRawUri("attachment", uri) }
        } else {
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .also { it.clipData = ClipData.newRawUri("attachment", uri) }
        }
        context.startActivity(Intent.createChooser(intent, if (share) "Поделиться файлом" else "Открыть файл"))
    }
}

internal fun formatBytes(bytes: Int): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format(java.util.Locale.US, "%.1f МБ", bytes / 1024f / 1024f)
}

internal fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
