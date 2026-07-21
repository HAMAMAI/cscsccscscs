package app.takt.messenger.data

import android.util.Base64
import app.takt.messenger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class TaktRepository {
    suspend fun createRoom(roomName: String, displayName: String): TaktSession = rpc(
        "create_room",
        JSONObject().put("p_room_name", roomName).put("p_display_name", displayName),
    ) { JSONArray(it).getJSONObject(0).toSession() }

    suspend fun joinRoom(inviteCode: String, displayName: String): TaktSession = rpc(
        "join_room",
        JSONObject().put("p_invite_code", inviteCode).put("p_display_name", displayName),
    ) { JSONArray(it).getJSONObject(0).toSession() }

    suspend fun getRoomState(session: TaktSession): RoomState = rpc(
        "get_room_state",
        authBody(session).put("p_invite_code", session.inviteCode),
    ) { JSONObject(it).toRoomState() }

    suspend fun sendMessage(session: TaktSession, body: String): Message = rpc(
        "send_message",
        authBody(session).put("p_body", body),
    ) { JSONObject(it).toMessage() }

    suspend fun sendAttachment(
        session: TaktSession,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        durationSeconds: Int? = null,
    ): Message = rpc(
        "send_attachment",
        authBody(session)
            .put("p_file_name", fileName.takeLast(120))
            .put("p_mime_type", mimeType)
            .put("p_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("p_duration_seconds", durationSeconds ?: JSONObject.NULL),
    ) { JSONObject(it).toMessage() }

    suspend fun getAttachment(session: TaktSession, attachmentId: String): AttachmentPayload = rpc(
        "get_attachment",
        authBody(session).put("p_attachment_id", attachmentId),
    ) {
        val json = JSONObject(it)
        AttachmentPayload(
            fileName = json.getString("file_name"),
            mimeType = json.getString("mime_type"),
            bytes = Base64.decode(json.getString("base64"), Base64.DEFAULT),
            durationSeconds = if (json.isNull("duration_seconds")) null else json.optInt("duration_seconds"),
        )
    }

    suspend fun markCallStarted(session: TaktSession) = rpc(
        "start_audio_call",
        authBody(session),
    ) { Unit }

    suspend fun markCallEnded(session: TaktSession) = rpc(
        "end_audio_call",
        authBody(session),
    ) { Unit }

    suspend fun getCallCredentials(session: TaktSession): CallCredentials = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("participantId", session.participantId)
            .put("participantToken", session.participantToken)
            .put("roomId", session.roomId)
            .toString()
        val response = request(
            url = "${BuildConfig.TAKT_API_BASE_URL.trimEnd('/')}/api/livekit-token",
            body = body,
            includeSupabaseHeaders = false,
        )
        val json = JSONObject(response)
        CallCredentials(json.getString("serverUrl"), json.getString("token"))
    }

    private fun authBody(session: TaktSession) = JSONObject()
        .put("p_participant_id", session.participantId)
        .put("p_token", session.participantToken)

    private suspend fun <T> rpc(name: String, body: JSONObject, decode: (String) -> T): T =
        withContext(Dispatchers.IO) {
            decode(request("${BuildConfig.SUPABASE_URL}/rest/v1/rpc/$name", body.toString(), true))
        }

    private fun request(url: String, body: String, includeSupabaseHeaders: Boolean): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (includeSupabaseHeaders) {
                setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            }
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                throw IOException(message?.takeIf(String::isNotBlank) ?: "Сервер вернул ошибку $code")
            }
            text
        } finally {
            connection.disconnect()
        }
    }
}
