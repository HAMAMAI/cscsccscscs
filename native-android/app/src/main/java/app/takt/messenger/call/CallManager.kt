package app.takt.messenger.call

import android.content.Context
import app.takt.messenger.data.CallCredentials
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.delay

class CallManager(context: Context) {
    private val applicationContext = context.applicationContext
    private var room: Room? = null

    suspend fun connect(credentials: CallCredentials) {
        disconnect()
        val next = LiveKit.create(applicationContext)
        try {
            next.connect(credentials.serverUrl, credentials.token)
            next.localParticipant.setMicrophoneEnabled(true)
            room = next
        } catch (error: Throwable) {
            next.disconnect()
            throw error
        }
    }

    suspend fun setMuted(muted: Boolean) {
        room?.localParticipant?.setMicrophoneEnabled(!muted)
    }

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
        active.disconnect()
    }
}
