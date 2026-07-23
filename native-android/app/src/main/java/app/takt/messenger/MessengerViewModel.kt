package app.takt.messenger

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.takt.messenger.call.CallForegroundService
import app.takt.messenger.call.CallManager
import app.takt.messenger.data.ActiveCall
import app.takt.messenger.data.ApiException
import app.takt.messenger.data.AppearanceSettings
import app.takt.messenger.data.AuthSession
import app.takt.messenger.data.AvatarShape
import app.takt.messenger.data.BootstrapData
import app.takt.messenger.data.CallHistoryItem
import app.takt.messenger.data.ChatFolder
import app.takt.messenger.data.ChatState
import app.takt.messenger.data.ConversationSummary
import app.takt.messenger.data.DeliveryStatus
import app.takt.messenger.data.DownloadedAttachment
import app.takt.messenger.data.MessengerMessage
import app.takt.messenger.data.MessengerPreferences
import app.takt.messenger.data.MessengerRepository
import app.takt.messenger.data.SecureSessionStore
import app.takt.messenger.data.SignUpResult
import app.takt.messenger.data.ThemeMode
import app.takt.messenger.data.UserProfile
import app.takt.messenger.media.VoiceRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

enum class HomeSection { Chats, People, Calls, Settings }

sealed interface CallConnectionState {
    data object Idle : CallConnectionState
    data object Connecting : CallConnectionState
    data class Connected(val call: ActiveCall) : CallConnectionState
}

data class MediaPreview(
    val title: String,
    val mimeType: String,
    val bytes: ByteArray,
    val durationSeconds: Int? = null,
)

data class MessengerUiState(
    val session: AuthSession? = null,
    val profile: UserProfile? = null,
    val conversations: List<ConversationSummary> = emptyList(),
    val folders: List<ChatFolder> = emptyList(),
    val calls: List<CallHistoryItem> = emptyList(),
    val activeChat: ChatState? = null,
    val section: HomeSection = HomeSection.Chats,
    val appearance: AppearanceSettings = AppearanceSettings(),
    val people: List<UserProfile> = emptyList(),
    val peopleQuery: String = "",
    val loading: Boolean = false,
    val busy: Boolean = false,
    val recording: Boolean = false,
    val recordingSeconds: Int = 0,
    val error: String? = null,
    val notice: String? = null,
    val confirmationEmail: String? = null,
    val replyingTo: MessengerMessage? = null,
    val forwarding: MessengerMessage? = null,
    val editing: MessengerMessage? = null,
    val mediaPreview: MediaPreview? = null,
    val callState: CallConnectionState = CallConnectionState.Idle,
    val callMuted: Boolean = false,
)

class MessengerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessengerRepository()
    private val sessions = SecureSessionStore(application)
    private val preferences = MessengerPreferences(application)
    private val recorder = VoiceRecorder(application)
    private val calls = CallManager(application)
    private val _state = MutableStateFlow(
        MessengerUiState(
            session = sessions.load(),
            appearance = preferences.loadAppearance(),
        ),
    )
    val state: StateFlow<MessengerUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var recordingJob: Job? = null
    private var searchJob: Job? = null

    init {
        state.value.session?.let { resumeSession(it) }
    }

    private fun resumeSession(previous: AuthSession) = viewModelScope.launch {
        val now = System.currentTimeMillis() / 1000
        val session = if (previous.expiresAtEpochSeconds <= now + 60) repository.refresh(previous) else previous
        if (session == null) {
            sessions.clear()
            _state.update { it.copy(session = null, notice = "Сессия закончилась. Войдите снова.") }
        } else {
            sessions.save(session)
            _state.update { it.copy(session = session) }
            loadBootstrap(session)
        }
    }

    fun signIn(email: String, password: String) = guardedAuth {
        repository.signIn(email, password)
    }

    fun signUp(email: String, password: String, displayName: String) {
        if (email.isBlank() || password.length < 8 || displayName.trim().length < 2) {
            setError("Укажите email, имя от 2 символов и пароль не короче 8 символов")
            return
        }
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null, confirmationEmail = null) }
            runCatching { repository.signUp(email, password, displayName) }
                .onSuccess { result ->
                    when (result) {
                        is SignUpResult.SignedIn -> acceptSession(result.session)
                        is SignUpResult.ConfirmationRequired -> _state.update {
                            it.copy(
                                busy = false,
                                confirmationEmail = result.email,
                                notice = "Письмо подтверждения отправлено. Откройте его, затем войдите в приложение.",
                            )
                        }
                    }
                }
                .onFailure { error -> _state.update { it.copy(busy = false, error = friendlyError(error)) } }
        }
    }

    fun startGoogleOAuth(): String? {
        if (state.value.busy) return null
        val request = repository.createGoogleOAuthRequest()
        sessions.savePendingOAuth(request.verifier, request.state)
        _state.update { it.copy(error = null, notice = "Откройте Google и завершите вход") }
        return request.url
    }

    fun handleDeepLink(uri: Uri?) {
        if (uri?.scheme != "takt" || uri.host != "auth" || uri.path != "/callback") return
        val providerError = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
        if (!providerError.isNullOrBlank()) {
            setError("Google-вход не завершён: $providerError")
            return
        }
        val verifier = sessions.consumePendingOAuth(uri.getQueryParameter("state"))
        val code = uri.getQueryParameter("code")
        if (verifier == null || code.isNullOrBlank()) {
            setError("Не удалось подтвердить Google-вход. Запустите его заново.")
            return
        }
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            runCatching { repository.exchangeGoogleCode(code, verifier) }
                .onSuccess(::acceptSession)
                .onFailure { error -> _state.update { it.copy(busy = false, error = friendlyError(error)) } }
        }
    }

    private fun guardedAuth(block: suspend () -> AuthSession) {
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null, confirmationEmail = null) }
            runCatching { block() }
                .onSuccess(::acceptSession)
                .onFailure { error -> _state.update { it.copy(busy = false, error = friendlyError(error)) } }
        }
    }

    private fun acceptSession(session: AuthSession) {
        sessions.save(session)
        _state.update { it.copy(session = session, busy = false, confirmationEmail = null, error = null) }
        loadBootstrap(session)
    }

    fun refresh() {
        state.value.session?.let(::loadBootstrap)
    }

    private fun loadBootstrap(session: AuthSession) {
        if (state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.bootstrap(session) }
                .onSuccess(::applyBootstrap)
                .onFailure { error -> _state.update { it.copy(loading = false, error = friendlyError(error)) } }
        }
    }

    private fun applyBootstrap(data: BootstrapData) {
        _state.update {
            it.copy(
                profile = data.profile,
                conversations = data.conversations,
                folders = data.folders,
                calls = data.calls,
                loading = false,
                error = null,
            )
        }
    }

    fun selectSection(section: HomeSection) = _state.update { it.copy(section = section, error = null) }

    fun openChat(conversationId: String) {
        val session = state.value.session ?: return
        pollJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.getConversation(session, conversationId) }
                .onSuccess { chat ->
                    _state.update { it.copy(activeChat = chat, loading = false, replyingTo = null, forwarding = null, editing = null) }
                    markRead(chat.id)
                    startPolling(chat.id)
                }
                .onFailure { error -> _state.update { it.copy(loading = false, error = friendlyError(error)) } }
        }
    }

    fun closeChat() {
        pollJob?.cancel()
        _state.update { it.copy(activeChat = null, replyingTo = null, forwarding = null, editing = null, mediaPreview = null) }
        refresh()
    }

    private fun startPolling(conversationId: String) {
        val session = state.value.session ?: return
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(4_000)
                val active = state.value.activeChat?.takeIf { it.id == conversationId } ?: break
                runCatching { repository.getConversation(session, active.id) }
                    .onSuccess { refreshed -> _state.update { it.copy(activeChat = refreshed) } }
            }
        }
    }

    fun updatePeopleQuery(query: String) {
        _state.update { it.copy(peopleQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(people = emptyList()) }
            return
        }
        val session = state.value.session ?: return
        searchJob = viewModelScope.launch {
            delay(250)
            runCatching { repository.searchPeople(session, query) }
                .onSuccess { people -> _state.update { it.copy(people = people) } }
                .onFailure { error -> _state.update { it.copy(error = friendlyError(error)) } }
        }
    }

    fun openDirectChat(profile: UserProfile) {
        val session = state.value.session ?: return
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { repository.openDirectChat(session, profile.id) }
                .onSuccess { chat ->
                    _state.update { it.copy(busy = false, activeChat = chat, people = emptyList(), peopleQuery = "") }
                    startPolling(chat.id)
                    refresh()
                }
                .onFailure { error -> _state.update { it.copy(busy = false, error = friendlyError(error)) } }
        }
    }

    fun createGroup(title: String, members: List<UserProfile>) {
        val session = state.value.session ?: return
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { repository.createGroup(session, title, members.map(UserProfile::id)) }
                .onSuccess { chat ->
                    _state.update { it.copy(busy = false, activeChat = chat) }
                    startPolling(chat.id)
                    refresh()
                }
                .onFailure { error -> _state.update { it.copy(busy = false, error = friendlyError(error)) } }
        }
    }

    fun setReply(message: MessengerMessage?) = _state.update { it.copy(replyingTo = message, forwarding = null, editing = null) }

    fun setForward(message: MessengerMessage?) = _state.update { it.copy(forwarding = message, replyingTo = null, editing = null) }

    fun setEditing(message: MessengerMessage?) = _state.update { it.copy(editing = message, replyingTo = null, forwarding = null) }

    fun sendText(text: String) {
        val session = state.value.session ?: return
        val chat = state.value.activeChat ?: return
        val body = text.trim()
        if (body.isBlank() || state.value.busy) return
        val editing = state.value.editing
        if (editing != null) {
            editText(session, chat, editing, body)
            return
        }
        val reply = state.value.replyingTo
        val forwarded = state.value.forwarding
        val optimistic = MessengerMessage(
            id = "local-${UUID.randomUUID()}",
            conversationId = chat.id,
            senderId = state.value.profile?.id.orEmpty(),
            senderName = state.value.profile?.displayName ?: "Вы",
            senderColor = state.value.profile?.avatarColor ?: "#8C78FF",
            body = body,
            kind = "text",
            createdAt = "",
            replyToId = reply?.id,
            replyPreview = reply?.let { app.takt.messenger.data.ReplyPreview(it.id, it.body, it.kind, it.senderName) },
            status = DeliveryStatus.Sending,
            localSending = true,
        )
        _state.update { state ->
            state.copy(
                busy = true,
                activeChat = state.activeChat?.copy(messages = state.activeChat.messages + optimistic),
                replyingTo = null,
                forwarding = null,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.sendText(session, chat.id, body, reply?.id, forwarded?.id) }
                .onSuccess { serverMessage -> replaceMessage(optimistic.id, serverMessage) }
                .onFailure { error -> removeLocalMessage(optimistic.id, friendlyError(error)) }
            _state.update { it.copy(busy = false) }
        }
    }

    private fun editText(session: AuthSession, chat: ChatState, editing: MessengerMessage, body: String) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.editMessage(session, editing.id, body) }
                .onSuccess { message ->
                    replaceMessage(editing.id, message)
                    _state.update { it.copy(editing = null) }
                }
                .onFailure { error -> _state.update { it.copy(error = friendlyError(error)) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun sendUri(uri: Uri) {
        val session = state.value.session ?: return
        val chat = state.value.activeChat ?: return
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                val details = readUri(uri)
                val kind = if (details.mimeType.startsWith("image/")) "image" else "file"
                repository.sendAttachment(session, chat.id, details.fileName, details.mimeType, details.bytes, kind)
            }.onSuccess(::appendMessage)
                .onFailure { error -> _state.update { it.copy(error = friendlyError(error)) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun startRecording() {
        if (state.value.recording || state.value.busy) return
        runCatching { recorder.start() }
            .onSuccess {
                _state.update { it.copy(recording = true, recordingSeconds = 0, error = null) }
                recordingJob?.cancel()
                recordingJob = viewModelScope.launch {
                    while (state.value.recording) {
                        delay(1_000)
                        _state.update { it.copy(recordingSeconds = (it.recordingSeconds + 1).coerceAtMost(300)) }
                        if (state.value.recordingSeconds >= 300) stopRecording(cancel = false)
                    }
                }
            }
            .onFailure { setError("Не удалось включить микрофон. Проверьте разрешение приложения.") }
    }

    fun stopRecording(cancel: Boolean) {
        val session = state.value.session ?: return
        val chat = state.value.activeChat ?: return
        val voice = recorder.stop(cancel)
        recordingJob?.cancel()
        _state.update { it.copy(recording = false, recordingSeconds = 0) }
        if (voice == null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                repository.sendAttachment(
                    session,
                    chat.id,
                    voice.file.name,
                    "audio/mp4",
                    voice.file.readBytes(),
                    "voice",
                    voice.durationSeconds,
                )
            }.onSuccess(::appendMessage)
                .onFailure { error -> _state.update { it.copy(error = friendlyError(error)) } }
            voice.file.delete()
            _state.update { it.copy(busy = false) }
        }
    }

    fun react(message: MessengerMessage, emoji: String) {
        val session = state.value.session ?: return
        viewModelScope.launch {
            runCatching { repository.toggleReaction(session, message.id, emoji) }
                .onSuccess { replaceMessage(message.id, it) }
                .onFailure { setError(friendlyError(it)) }
        }
    }

    fun deleteMessage(message: MessengerMessage, forEveryone: Boolean) {
        val session = state.value.session ?: return
        viewModelScope.launch {
            runCatching { repository.deleteMessage(session, message.id, forEveryone) }
                .onSuccess { refreshActiveChat() }
                .onFailure { setError(friendlyError(it)) }
        }
    }

    fun setPinned(archived: Boolean? = null, pinned: Boolean? = null, draft: String? = null) {
        val session = state.value.session ?: return
        val chat = state.value.activeChat ?: return
        viewModelScope.launch {
            runCatching { repository.updateChatSettings(session, chat.id, archived, pinned, draft) }
                .onSuccess { updated -> _state.update { it.copy(activeChat = updated) } }
                .onFailure { setError(friendlyError(it)) }
        }
    }

    fun markRead(conversationId: String) {
        val session = state.value.session ?: return
        viewModelScope.launch { runCatching { repository.markRead(session, conversationId) } }
    }

    fun updateTyping(mode: String = "typing") {
        val session = state.value.session ?: return
        val chat = state.value.activeChat ?: return
        viewModelScope.launch { runCatching { repository.setTyping(session, chat.id, mode) } }
    }

    fun openMedia(message: MessengerMessage) {
        val session = state.value.session ?: return
        val attachment = message.attachment ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { repository.getAttachment(session, attachment.id) }
                .onSuccess { download -> _state.update { it.copy(mediaPreview = download.toPreview(), busy = false) } }
                .onFailure { error -> _state.update { it.copy(busy = false, error = friendlyError(error)) } }
        }
    }

    fun closeMediaPreview() = _state.update { it.copy(mediaPreview = null) }

    fun saveProfile(displayName: String, username: String, about: String, avatarColor: String) {
        val session = state.value.session ?: return
        if (state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { repository.updateProfile(session, displayName, username, about, avatarColor) }
                .onSuccess { profile -> _state.update { it.copy(profile = profile, busy = false, notice = "Профиль сохранён") } }
                .onFailure { error -> _state.update { it.copy(busy = false, error = friendlyError(error)) } }
        }
    }

    fun createFolder(name: String) {
        val session = state.value.session ?: return
        viewModelScope.launch {
            runCatching { repository.createFolder(session, name) }
                .onSuccess { folder -> _state.update { it.copy(folders = it.folders + folder, notice = "Папка создана") } }
                .onFailure { setError(friendlyError(it)) }
        }
    }

    fun updateAppearance(settings: AppearanceSettings) {
        preferences.saveAppearance(settings)
        _state.update { it.copy(appearance = settings) }
    }

    fun updateTheme(themeMode: ThemeMode) = updateAppearance(state.value.appearance.copy(themeMode = themeMode))

    fun updateAvatarShape(shape: AvatarShape) = updateAppearance(state.value.appearance.copy(avatarShape = shape))

    fun startCall(video: Boolean) {
        val session = state.value.session ?: return
        val chat = state.value.activeChat ?: return
        if (state.value.callState !is CallConnectionState.Idle) return
        viewModelScope.launch {
            _state.update { it.copy(callState = CallConnectionState.Connecting, error = null) }
            runCatching {
                val call = repository.startCall(session, chat.id, video)
                val token = repository.getLiveKitToken(session, call.id)
                calls.connect(app.takt.messenger.data.CallCredentials(token.serverUrl, token.token))
                CallForegroundService.start(getApplication())
                call
            }.onSuccess { call -> _state.update { it.copy(callState = CallConnectionState.Connected(call)) } }
                .onFailure { error ->
                    _state.update { it.copy(callState = CallConnectionState.Idle, error = friendlyError(error)) }
                }
        }
    }

    fun toggleMute() {
        if (state.value.callState !is CallConnectionState.Connected) return
        val muted = !state.value.callMuted
        viewModelScope.launch { calls.setMuted(muted) }
        _state.update { it.copy(callMuted = muted, notice = if (muted) "Микрофон выключен" else "Микрофон включён") }
    }

    fun endCall() {
        val session = state.value.session
        val active = state.value.callState as? CallConnectionState.Connected
        viewModelScope.launch {
            runCatching { calls.disconnect() }
            CallForegroundService.stop(getApplication())
            if (session != null && active != null) runCatching { repository.endCall(session, active.call.id) }
            _state.update { it.copy(callState = CallConnectionState.Idle, callMuted = false) }
        }
    }

    fun signOut() {
        val session = state.value.session
        pollJob?.cancel()
        recordingJob?.cancel()
        recorder.stop(cancel = true)
        endCall()
        if (session != null) viewModelScope.launch { runCatching { repository.signOut(session) } }
        sessions.clear()
        _state.value = MessengerUiState(appearance = preferences.loadAppearance())
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotice() = _state.update { it.copy(notice = null) }
    fun clearConfirmation() = _state.update { it.copy(confirmationEmail = null) }

    private fun appendMessage(message: MessengerMessage) {
        _state.update { state ->
            state.copy(activeChat = state.activeChat?.copy(messages = state.activeChat.messages + message))
        }
    }

    private fun replaceMessage(localId: String, message: MessengerMessage) {
        _state.update { state ->
            val chat = state.activeChat ?: return@update state
            state.copy(activeChat = chat.copy(messages = chat.messages.map { if (it.id == localId || it.id == message.id) message else it }))
        }
    }

    private fun removeLocalMessage(localId: String, message: String) {
        _state.update { state ->
            val chat = state.activeChat
            state.copy(activeChat = chat?.copy(messages = chat.messages.filterNot { it.id == localId }), error = message)
        }
    }

    private fun refreshActiveChat() {
        state.value.activeChat?.id?.let(::openChat)
    }

    private fun readUri(uri: Uri): UploadPayload {
        val resolver = getApplication<Application>().contentResolver
        var name = "Файл"
        var declaredSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0) ?: name
                declaredSize = if (cursor.isNull(1)) -1 else cursor.getLong(1)
            }
        }
        if (declaredSize > MAX_ATTACHMENT_BYTES) throw IOException("Файл больше 8 МБ")
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes(MAX_ATTACHMENT_BYTES + 1) }
            ?: throw IOException("Не удалось прочитать файл")
        if (bytes.isEmpty() || bytes.size > MAX_ATTACHMENT_BYTES) throw IOException("Файл должен быть не больше 8 МБ")
        return UploadPayload(name.takeLast(120), resolver.getType(uri) ?: "application/octet-stream", bytes)
    }

    private fun DownloadedAttachment.toPreview() = MediaPreview(fileName, mimeType, bytes, durationSeconds)

    private fun setError(message: String) = _state.update { it.copy(error = message) }

    private fun friendlyError(error: Throwable): String {
        val api = error as? ApiException
        val code = api?.errorCode?.lowercase().orEmpty()
        val message = error.message.orEmpty()
        return when {
            code.contains("email_not_confirmed") || message.contains("Email not confirmed", true) -> "Подтвердите email по письму, затем войдите."
            code.contains("invalid_credentials") || message.contains("Invalid login credentials", true) -> "Неверный email или пароль."
            code.contains("user_already_exists") || message.contains("already registered", true) -> "Этот email уже зарегистрирован. Войдите или восстановите пароль."
            code.contains("username_taken") || message.contains("USERNAME_TAKEN") -> "Этот username уже занят. Выберите другой."
            code.contains("username_invalid") || message.contains("USERNAME_INVALID") -> "Username: 5-32 символа, только латинские буквы, цифры и _."
            code.contains("user_blocked") || message.contains("USER_BLOCKED") -> "Действие недоступно: один из пользователей заблокировал другого."
            code.contains("call_server_not_configured") || message.contains("CALL_SERVER_NOT_CONFIGURED") -> "Звонки станут доступны после подключения LiveKit token-сервера."
            message.isNotBlank() -> message
            else -> "Не удалось выполнить действие. Проверьте интернет и повторите."
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        recordingJob?.cancel()
        recorder.stop(cancel = true)
    }

    private data class UploadPayload(val fileName: String, val mimeType: String, val bytes: ByteArray)

    private companion object {
        const val MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024
    }
}
