package app.takt.messenger.data

import org.json.JSONArray
import org.json.JSONObject

data class TaktSession(
    val roomId: String,
    val inviteCode: String,
    val participantId: String,
    val participantToken: String,
    val displayName: String,
    val roomName: String,
    val color: String,
)

data class Participant(
    val id: String,
    val displayName: String,
    val color: String,
    val isOwner: Boolean,
    val online: Boolean = false,
)

data class Attachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Int,
    val durationSeconds: Int?,
)

data class Message(
    val id: String,
    val participantId: String,
    val displayName: String,
    val color: String,
    val body: String,
    val kind: String,
    val attachment: Attachment?,
    val createdAt: String,
)

data class ActiveCall(
    val id: String,
    val startedBy: String,
    val startedByName: String,
    val startedAt: String,
)

data class RoomState(
    val roomName: String,
    val inviteCode: String,
    val me: Participant,
    val participants: List<Participant>,
    val messages: List<Message>,
    val activeCall: ActiveCall?,
)

data class CallCredentials(val serverUrl: String, val token: String)

data class AttachmentPayload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val durationSeconds: Int?,
)

internal fun JSONObject.toSession(): TaktSession = TaktSession(
    roomId = getString("room_id"),
    inviteCode = getString("invite_code"),
    participantId = getString("participant_id"),
    participantToken = getString("participant_token"),
    displayName = getString("display_name"),
    roomName = getString("room_name"),
    color = getString("color"),
)

internal fun JSONObject.toParticipant(): Participant = Participant(
    id = getString("id"),
    displayName = getString("display_name"),
    color = optString("color", "#826CF0"),
    isOwner = optBoolean("is_owner", false),
    online = optBoolean("online", false),
)

internal fun JSONObject.toAttachment(): Attachment = Attachment(
    id = getString("id"),
    fileName = getString("file_name"),
    mimeType = getString("mime_type"),
    sizeBytes = getInt("size_bytes"),
    durationSeconds = if (isNull("duration_seconds")) null else optInt("duration_seconds"),
)

internal fun JSONObject.toMessage(): Message = Message(
    id = getString("id"),
    participantId = getString("participant_id"),
    displayName = getString("display_name"),
    color = optString("color", "#826CF0"),
    body = optString("body"),
    kind = optString("kind", "text"),
    attachment = optJSONObject("attachment")?.toAttachment(),
    createdAt = getString("created_at"),
)

internal fun JSONObject.toRoomState(): RoomState {
    val room = getJSONObject("room")
    val people = getJSONArray("participants").objects().map(JSONObject::toParticipant)
    val messages = getJSONArray("messages").objects().map(JSONObject::toMessage)
    val call = optJSONObject("active_call")?.let {
        ActiveCall(
            id = it.getString("id"),
            startedBy = it.getString("started_by"),
            startedByName = it.optString("started_by_name", "Участник"),
            startedAt = it.getString("started_at"),
        )
    }
    return RoomState(
        roomName = room.getString("name"),
        inviteCode = room.getString("invite_code"),
        me = getJSONObject("me").toParticipant(),
        participants = people,
        messages = messages,
        activeCall = call,
    )
}

private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) add(getJSONObject(index))
}
