package app.takt.messenger.data

import android.content.Context
import org.json.JSONObject

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("takt_session_v2", Context.MODE_PRIVATE)

    fun load(): TaktSession? = runCatching {
        val raw = preferences.getString("session", null) ?: return null
        val json = JSONObject(raw)
        TaktSession(
            roomId = json.getString("roomId"),
            inviteCode = json.getString("inviteCode"),
            participantId = json.getString("participantId"),
            participantToken = json.getString("participantToken"),
            displayName = json.getString("displayName"),
            roomName = json.getString("roomName"),
            color = json.getString("color"),
        )
    }.getOrNull()

    fun save(session: TaktSession) {
        val json = JSONObject()
            .put("roomId", session.roomId)
            .put("inviteCode", session.inviteCode)
            .put("participantId", session.participantId)
            .put("participantToken", session.participantToken)
            .put("displayName", session.displayName)
            .put("roomName", session.roomName)
            .put("color", session.color)
        preferences.edit().putString("session", json.toString()).apply()
    }

    fun clear() = preferences.edit().clear().apply()
}
