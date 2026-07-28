package app.takt.messenger.call

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import livekit.org.webrtc.SurfaceViewRenderer

/**
 * Displays the first remote camera track in [room], or the local camera while
 * a remote participant has not published video yet. The room owns its media
 * tracks; this composable only attaches and detaches a renderer.
 */
@Composable
fun LiveKitVideoSurface(
    room: Room?,
    modifier: Modifier = Modifier,
) {
    // A renderer belongs to one LiveKit Room/EGL context. Recreate it when a
    // call is replaced instead of reinitialising a view that belongs to the
    // previous room.
    key(room) {
        val context = LocalContext.current
        val renderer = remember(context) {
            SurfaceViewRenderer(context).apply {
                setEnableHardwareScaler(true)
            }
        }
        var selection by remember { mutableStateOf(room?.preferredVideoTrack()) }

        // LiveKit track events are not Compose state. Polling this small in-memory
        // collection keeps the surface current when a peer turns its camera on/off.
        LaunchedEffect(room) {
            if (room != null) {
                while (true) {
                    selection = room.preferredVideoTrack()
                    delay(TRACK_POLL_INTERVAL_MS)
                }
            }
        }
        LaunchedEffect(renderer, selection?.local) {
            renderer.setMirror(selection?.local == true)
        }
        DisposableEffect(renderer, selection?.track) {
            val track = selection?.track
            track?.addRenderer(renderer)
            onDispose { track?.removeRenderer(renderer) }
        }
        DisposableEffect(renderer) {
            onDispose { renderer.release() }
        }

        AndroidView(
            factory = { renderer.also { room?.initVideoRenderer(it) } },
            modifier = modifier,
        )
    }
}

private data class VideoSelection(
    val track: VideoTrack,
    val local: Boolean,
)

private fun Room.preferredVideoTrack(): VideoSelection? {
    remoteParticipants.values.asSequence()
        .flatMap { it.videoTrackPublications.asSequence() }
        .mapNotNull { (_, track) -> track as? VideoTrack }
        .firstOrNull()
        ?.let { return VideoSelection(it, local = false) }

    localParticipant.videoTrackPublications.asSequence()
        .mapNotNull { (_, track) -> track as? VideoTrack }
        .firstOrNull()
        ?.let { return VideoSelection(it, local = true) }

    return null
}

private const val TRACK_POLL_INTERVAL_MS = 300L
