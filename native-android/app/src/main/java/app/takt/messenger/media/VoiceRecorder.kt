package app.takt.messenger.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start() {
        check(recorder == null) { "Запись уже идёт" }
        val directory = File(context.cacheDir, "voice").apply { mkdirs() }
        val file = File(directory, "voice-${System.currentTimeMillis()}.m4a")
        val next = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        next.setAudioSource(MediaRecorder.AudioSource.MIC)
        next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        next.setAudioEncodingBitRate(96_000)
        next.setAudioSamplingRate(48_000)
        next.setMaxDuration(300_000)
        next.setOutputFile(file.absolutePath)
        next.prepare()
        next.start()
        recorder = next
        output = file
    }

    fun stop(cancel: Boolean = false): RecordedVoice? {
        val active = recorder ?: return null
        recorder = null
        val file = output
        output = null
        runCatching { active.stop() }
        active.release()
        if (file == null || cancel || !file.exists() || file.length() == 0L) {
            file?.delete()
            return null
        }
        val retriever = MediaMetadataRetriever()
        val duration = runCatching {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }.getOrNull()?.div(1000)?.toInt()?.coerceIn(1, 300) ?: 1
        retriever.release()
        return RecordedVoice(file, duration)
    }
}

data class RecordedVoice(val file: File, val durationSeconds: Int)
