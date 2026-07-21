package app.takt.messenger

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.takt.messenger.call.CallManager
import app.takt.messenger.call.CallForegroundService
import app.takt.messenger.data.AttachmentPayload
import app.takt.messenger.data.Message
import app.takt.messenger.data.RoomState
import app.takt.messenger.data.SessionStore
import app.takt.messenger.data.TaktRepository
import app.takt.messenger.data.TaktSession
import app.takt.messenger.media.VoiceRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

data class MessengerUiState(
    val session: TaktSession? = null,
    val room: RoomState? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val recording: Boolean = false,
    val recordingSeconds: Int = 0,
    val callState: CallState = CallState.Idle,
    val muted: Boolean = false,
    val callParticipants: Int = 0,
)

sealed interface CallState {
    data object Idle : CallState
    data object Connecting : CallState
    data object Connected : CallState
}

class MessengerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TaktRepository()
    private val store = SessionStore(application)
    private val recorder = VoiceRecorder(application)
    private val calls = CallManager(application)
    private val _state = MutableStateFlow(MessengerUiState(session = store.load()))
    val state: StateFlow<MessengerUiState> = _state.asStateFlow()
    private val _openFile = MutableSharedFlow<Intent>()
    val openFile: SharedFlow<Intent> = _openFile.asSharedFlow()
    private var pollJob: Job? = null
    private var recordingJob: Job? = null
    private var callCountJob: Job? = null

    init {
        _state.value.session?.let { startPolling(it, immediate = true) }
    }

    fun createRoom(roomName: String, displayName: String) = authenticate {
        repository.createRoom(roomName.trim(), displayName.trim())
    }

    fun joinRoom(inviteCode: String, displayName: String) = authenticate {
        repository.joinRoom(inviteCode.trim().uppercase(), displayName.trim())
    }

    private fun authenticate(block: suspend () -> TaktSession) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onSuccess { session ->
                    store.save(session)
                    _state.update { it.copy(session = session, busy = false, loading = true) }
                    startPolling(session, immediate = true)
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, error = friendlyError(error)) }
                }
        }
    }

    fun sendText(text: String) {
        val session = _state.value.session ?: return
        val body = text.trim().take(2_000)
        if (body.isEmpty() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { repository.sendMessage(session, body) }
                .onSuccess { appendMessage(it) }
                .onFailure { _state.update { state -> state.copy(error = friendlyError(it)) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun sendUri(uri: Uri) {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                var name = "Файл"
                var declaredSize = -1L
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0) ?: name
                        declaredSize = if (cursor.isNull(1)) -1 else cursor.getLong(1)
                    }
                }
                if (declaredSize > MAX_ATTACHMENT_BYTES) throw IOException("Файл больше 8 МБ")
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                validateMime(mime)
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes(MAX_ATTACHMENT_BYTES + 1) }
                    ?: throw IOException("Не удалось прочитать файл")
                if (bytes.isEmpty() || bytes.size > MAX_ATTACHMENT_BYTES) throw IOException("Файл должен быть не больше 8 МБ")
                repository.sendAttachment(session, name, mime, bytes)
            }.onSuccess(::appendMessage)
                .onFailure { _state.update { state -> state.copy(error = friendlyError(it)) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun startRecording() {
        if (_state.value.recording) return
        runCatching { recorder.start() }
            .onSuccess {
                _state.update { it.copy(recording = true, recordingSeconds = 0, error = null) }
                recordingJob?.cancel()
                recordingJob = viewModelScope.launch {
                    while (_state.value.recording) {
                        delay(1_000)
                        _state.update { state -> state.copy(recordingSeconds = (state.recordingSeconds + 1).coerceAtMost(300)) }
                        if (_state.value.recordingSeconds >= 300) stopRecording(false)
                    }
                }
            }
            .onFailure { _state.update { state -> state.copy(error = "Не удалось включить микрофон") } }
    }

    fun stopRecording(cancel: Boolean) {
        val session = _state.value.session ?: return
        val voice = recorder.stop(cancel)
        recordingJob?.cancel()
        _state.update { it.copy(recording = false, recordingSeconds = 0) }
        if (voice == null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                repository.sendAttachment(
                    session,
                    "Голосовое-${System.currentTimeMillis()}.m4a",
                    "audio/mp4",
                    voice.file.readBytes(),
                    voice.durationSeconds,
                )
            }.onSuccess(::appendMessage)
                .onFailure { _state.update { state -> state.copy(error = friendlyError(it)) } }
            voice.file.delete()
            _state.update { it.copy(busy = false) }
        }
    }

    fun openAttachment(message: Message) {
        val session = _state.value.session ?: return
        val attachment = message.attachment ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { cacheAttachment(session, attachment.id) }
                .onSuccess { payload ->
                    val file = File(getApplication<Application>().cacheDir, "attachments/${safeFileName(payload.fileName)}")
                    val uri = FileProvider.getUriForFile(getApplication(), "${BuildConfig.APPLICATION_ID}.files", file)
                    _openFile.emit(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, payload.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
                .onFailure { _state.update { state -> state.copy(error = friendlyError(it)) } }
            _state.update { it.copy(busy = false) }
        }
    }

    suspend fun loadAttachment(attachmentId: String): AttachmentPayload? {
        val session = _state.value.session ?: return null
        return runCatching { repository.getAttachment(session, attachmentId) }.getOrNull()
    }

    fun joinCall() {
        val session = _state.value.session ?: return
        if (_state.value.callState != CallState.Idle) return
        viewModelScope.launch {
            _state.update { it.copy(callState = CallState.Connecting, error = null) }
            runCatching {
                repository.markCallStarted(session)
                calls.connect(repository.getCallCredentials(session))
            }.onSuccess {
                CallForegroundService.start(getApplication())
                _state.update { it.copy(callState = CallState.Connected, callParticipants = calls.participantCount()) }
                callCountJob?.cancel()
                callCountJob = viewModelScope.launch {
                    calls.waitForParticipantCount { count -> _state.update { state -> state.copy(callParticipants = count) } }
                }
            }.onFailure { error ->
                runCatching { repository.markCallEnded(session) }
                _state.update { it.copy(callState = CallState.Idle, error = "Звонок не подключился: ${friendlyError(error)}") }
            }
        }
    }

    fun toggleMute() {
        if (_state.value.callState != CallState.Connected) return
        val muted = !_state.value.muted
        viewModelScope.launch {
            calls.setMuted(muted)
            _state.update { it.copy(muted = muted) }
        }
    }

    fun leaveCall() {
        val session = _state.value.session
        viewModelScope.launch {
            callCountJob?.cancel()
            runCatching { calls.disconnect() }
            CallForegroundService.stop(getApplication())
            if (session != null) runCatching { repository.markCallEnded(session) }
            _state.update { it.copy(callState = CallState.Idle, muted = false, callParticipants = 0) }
        }
    }

    fun leaveRoom() {
        leaveCall()
        pollJob?.cancel()
        recordingJob?.cancel()
        recorder.stop(cancel = true)
        store.clear()
        _state.value = MessengerUiState()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun startPolling(session: TaktSession, immediate: Boolean) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            if (!immediate) delay(POLL_INTERVAL)
            while (true) {
                val firstLoad = _state.value.room == null
                if (firstLoad) _state.update { it.copy(loading = true) }
                runCatching { repository.getRoomState(session) }
                    .onSuccess { room -> _state.update { it.copy(room = room, loading = false, error = null) } }
                    .onFailure { error ->
                        _state.update { it.copy(loading = false, error = if (firstLoad) friendlyError(error) else it.error) }
                    }
                delay(POLL_INTERVAL)
            }
        }
    }

    private fun appendMessage(message: Message) {
        _state.update { state ->
            val room = state.room ?: return@update state
            val messages = if (room.messages.any { it.id == message.id }) room.messages else room.messages + message
            state.copy(room = room.copy(messages = messages))
        }
    }

    private suspend fun cacheAttachment(session: TaktSession, id: String): AttachmentPayload {
        val payload = repository.getAttachment(session, id)
        val directory = File(getApplication<Application>().cacheDir, "attachments").apply { mkdirs() }
        File(directory, safeFileName(payload.fileName)).writeBytes(payload.bytes)
        return payload
    }

    private fun validateMime(mime: String) {
        if (mime.startsWith("video/") || mime in BLOCKED_MIME_TYPES) throw IOException("Этот формат не поддерживается")
    }

    private fun safeFileName(value: String): String = value.replace(Regex("[^А-Яа-яA-Za-z0-9._ -]"), "_").takeLast(100)

    private fun friendlyError(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("room not found", true) -> "Чат не найден или ссылка устарела"
            text.contains("invalid participant", true) -> "Ключ доступа больше не действует"
            text.contains("Unable to resolve host", true) -> "Нет подключения к интернету"
            text.isNotBlank() -> text.take(160)
            else -> "Что-то пошло не так. Попробуйте ещё раз"
        }
    }

    override fun onCleared() {
        recorder.stop(cancel = true)
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL = 1_500L
        const val MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024
        val BLOCKED_MIME_TYPES = setOf("text/html", "image/svg+xml", "application/javascript", "text/javascript", "application/x-sh")
    }
}

private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
    val buffer = ByteArray(16 * 1024)
    val output = java.io.ByteArrayOutputStream()
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) throw IOException("Файл должен быть не больше 8 МБ")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
