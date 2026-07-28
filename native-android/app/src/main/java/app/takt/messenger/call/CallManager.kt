package app.takt.messenger.call

import android.content.Context
import app.takt.messenger.data.CallCredentials
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.delay

class CallManager(context: Context) {
    private val applicationContext = context.applicationContext
    private var room: Room? = null
    private var videoEnabled = false

    /**
     * Joins a LiveKit call and publishes audio.  Use the overload with
     * [enableVideo] for a video call after the app has been granted CAMERA.
     */
    suspend fun connect(credentials: CallCredentials) = connect(credentials, enableVideo = false)

    suspend fun connect(credentials: CallCredentials, enableVideo: Boolean) {
        disconnect()
        val next = LiveKit.create(applicationContext)
        try {
            next.connect(credentials.serverUrl, credentials.token)
            check(next.localParticipant.setMicrophoneEnabled(true)) {
                "Не удалось включить микрофон для звонка"
            }
            if (enableVideo) {
                check(next.localParticipant.setCameraEnabled(true)) {
                    "Не удалось включить камеру для видеозвонка"
                }
            }
            room = next
            videoEnabled = enableVideo
        } catch (error: Throwable) {
            next.disconnect()
            throw error
        }
    }

    /** Returns the actual microphone state after the LiveKit operation succeeds. */
    suspend fun setMuted(muted: Boolean): Boolean {
        val active = checkNotNull(room) { "Звонок уже завершён" }
        check(active.localParticipant.setMicrophoneEnabled(!muted)) {
            "Не удалось изменить состояние микрофона"
        }
        return !active.localParticipant.isMicrophoneEnabled
    }

    /** Enables or disables the local camera without leaving the call. */
    suspend fun setCameraEnabled(enabled: Boolean) {
        val active = room ?: return
        check(active.localParticipant.setCameraEnabled(enabled)) {
            "Не удалось изменить состояние камеры"
        }
        videoEnabled = enabled
    }

    fun isVideoEnabled(): Boolean = videoEnabled

    /** Exposed for a UI video renderer; callers must not disconnect the returned room. */
    fun currentRoom(): Room? = room

    fun participantCount(): Int = (room?.remoteParticipants?.size ?: 0) + if (room == null) 0 else 1

    suspend fun waitForParticipantCount(onChange: (Int) -> Unit) {
        while (room != null) {
            onChange(participantCount())
            delay(1_000)
        }
    }

    suspend fun disconnect() {
        val active = room ?: return
        room = null
        videoEnabled = false
        active.disconnect()
    }
}
