package app.takt.messenger.data

import android.net.Uri
import android.util.Base64
import app.takt.messenger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

sealed interface SignUpResult {
    data class SignedIn(val session: AuthSession) : SignUpResult
    data class ConfirmationRequired(val email: String) : SignUpResult
}

data class OAuthRequest(
    val url: String,
    val verifier: String,
    val state: String,
)

data class CallToken(
    val serverUrl: String,
    val token: String,
)

class MessengerRepository {
    suspend fun signUp(email: String, password: String, displayName: String): SignUpResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email.trim().lowercase())
            .put("password", password)
            .put("data", JSONObject().put("display_name", displayName.trim()))
        val response = JSONObject(request("/auth/v1/signup", "POST", payload, null))
        val token = response.optString("access_token").ifBlank {
            response.optJSONObject("session")?.optString("access_token").orEmpty()
        }
        if (token.isNotBlank()) {
            SignUpResult.SignedIn(response.toAuthSession())
        } else {
            val registeredEmail = response.optJSONObject("user")?.optString("email")
                ?.takeIf(String::isNotBlank)
                ?: email.trim().lowercase()
            SignUpResult.ConfirmationRequired(registeredEmail)
        }
    }

    suspend fun signIn(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email.trim().lowercase())
            .put("password", password)
        JSONObject(request("/auth/v1/token?grant_type=password", "POST", payload, null)).toAuthSession()
    }

    suspend fun refresh(session: AuthSession): AuthSession? = withContext(Dispatchers.IO) {
        val refreshToken = session.refreshToken ?: return@withContext null
        runCatching {
            JSONObject(
                request(
                    "/auth/v1/token?grant_type=refresh_token",
                    "POST",
                    JSONObject().put("refresh_token", refreshToken),
                    null,
                ),
            ).toAuthSession()
        }.getOrNull()
    }

    suspend fun signOut(session: AuthSession) = withContext(Dispatchers.IO) {
        runCatching { request("/auth/v1/logout", "POST", null, session.accessToken) }
    }

    fun createGoogleOAuthRequest(): OAuthRequest {
        val verifier = randomUrlSafe(48)
        val state = randomUrlSafe(24)
        val challengeBytes = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(
            challengeBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val url = Uri.parse("${BuildConfig.SUPABASE_URL}/auth/v1/authorize")
            .buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", "takt://auth/callback")
            .appendQueryParameter("flow_type", "pkce")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "s256")
            .appendQueryParameter("state", state)
            .build()
            .toString()
        return OAuthRequest(url = url, verifier = verifier, state = state)
    }

    suspend fun exchangeGoogleCode(code: String, verifier: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("auth_code", code)
            .put("code_verifier", verifier)
        JSONObject(request("/auth/v1/token?grant_type=pkce", "POST", payload, null)).toAuthSession()
    }

    suspend fun bootstrap(session: AuthSession): BootstrapData = rpc("takt_bootstrap", JSONObject(), session).toBootstrapData()

    suspend fun getConversation(session: AuthSession, conversationId: String): ChatState = rpc(
        "takt_get_conversation",
        JSONObject().put("p_conversation_id", conversationId),
        session,
    ).toChatState()

    suspend fun updateProfile(
        session: AuthSession,
        displayName: String,
        username: String,
        about: String,
        avatarColor: String,
    ): UserProfile = rpc(
        "takt_update_profile",
        JSONObject()
            .put("p_display_name", displayName)
            .put("p_username", username)
            .put("p_about", about)
            .put("p_avatar_color", avatarColor),
        session,
    ).toProfile()

    suspend fun searchPeople(session: AuthSession, query: String): List<UserProfile> {
        val items = rpcArray("takt_search_people", JSONObject().put("p_query", query), session)
        return buildList {
            for (index in 0 until items.length()) add(items.getJSONObject(index).toProfile())
        }
    }

    suspend fun openDirectChat(session: AuthSession, userId: String): ChatState = rpc(
        "takt_open_direct_chat",
        JSONObject().put("p_other_user_id", userId),
        session,
    ).toChatState()

    suspend fun createGroup(session: AuthSession, title: String, memberIds: List<String>): ChatState = rpc(
        "takt_create_group",
        JSONObject()
            .put("p_title", title)
            .put("p_member_ids", JSONArray(memberIds)),
        session,
    ).toChatState()

    suspend fun sendText(
        session: AuthSession,
        conversationId: String,
        body: String,
        replyToId: String? = null,
        forwardedFromId: String? = null,
    ): MessengerMessage = rpc(
        "takt_send_message",
        JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_body", body)
            .put("p_reply_to_id", replyToId ?: JSONObject.NULL)
            .put("p_forwarded_from_id", forwardedFromId ?: JSONObject.NULL),
        session,
    ).toMessengerMessage()

    suspend fun sendAttachment(
        session: AuthSession,
        conversationId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        kind: String,
        durationSeconds: Int? = null,
        caption: String = "",
    ): MessengerMessage = rpc(
        "takt_send_attachment",
        JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_file_name", fileName)
            .put("p_mime_type", mimeType)
            .put("p_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("p_kind", kind)
            .put("p_duration_seconds", durationSeconds ?: JSONObject.NULL)
            .put("p_caption", caption),
        session,
    ).toMessengerMessage()

    suspend fun getAttachment(session: AuthSession, attachmentId: String): DownloadedAttachment {
        val json = rpc("takt_get_attachment", JSONObject().put("p_attachment_id", attachmentId), session)
        return DownloadedAttachment(
            fileName = json.optString("file_name", "Файл"),
            mimeType = json.optString("mime_type", "application/octet-stream"),
            bytes = Base64.decode(json.getString("base64"), Base64.DEFAULT),
            durationSeconds = if (json.isNull("duration_seconds")) null else json.optInt("duration_seconds"),
        )
    }

    suspend fun editMessage(session: AuthSession, messageId: String, body: String): MessengerMessage = rpc(
        "takt_edit_message",
        JSONObject().put("p_message_id", messageId).put("p_body", body),
        session,
    ).toMessengerMessage()

    suspend fun deleteMessage(session: AuthSession, messageId: String, forEveryone: Boolean) {
        rpc("takt_delete_message", JSONObject().put("p_message_id", messageId).put("p_for_everyone", forEveryone), session)
    }

    suspend fun toggleReaction(session: AuthSession, messageId: String, emoji: String): MessengerMessage = rpc(
        "takt_toggle_reaction",
        JSONObject().put("p_message_id", messageId).put("p_emoji", emoji),
        session,
    ).toMessengerMessage()

    suspend fun markRead(session: AuthSession, conversationId: String) {
        rpc("takt_mark_read", JSONObject().put("p_conversation_id", conversationId), session)
    }

    suspend fun updateChatSettings(
        session: AuthSession,
        conversationId: String,
        archived: Boolean? = null,
        pinned: Boolean? = null,
        draft: String? = null,
    ): ChatState = rpc(
        "takt_update_chat_settings",
        JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_is_archived", archived ?: JSONObject.NULL)
            .put("p_is_pinned", pinned ?: JSONObject.NULL)
            .put("p_draft_text", draft ?: JSONObject.NULL),
        session,
    ).toChatState()

    suspend fun setTyping(session: AuthSession, conversationId: String, mode: String) {
        rpc(
            "takt_set_typing",
            JSONObject().put("p_conversation_id", conversationId).put("p_mode", mode),
            session,
        )
    }

    suspend fun setPresence(session: AuthSession, online: Boolean) {
        rpc("takt_set_presence", JSONObject().put("p_online", online), session)
    }

    suspend fun createFolder(session: AuthSession, name: String): ChatFolder = rpc(
        "takt_create_folder",
        JSONObject().put("p_name", name),
        session,
    ).toFolder()

    suspend fun toggleBlock(session: AuthSession, otherUserId: String, blocked: Boolean) {
        rpc(
            "takt_toggle_block",
            JSONObject().put("p_other_user_id", otherUserId).put("p_blocked", blocked),
            session,
        )
    }

    suspend fun startCall(session: AuthSession, conversationId: String, video: Boolean): ActiveCall = rpc(
        "takt_start_call",
        JSONObject().put("p_conversation_id", conversationId).put("p_is_video", video),
        session,
    ).toActiveCall()

    suspend fun endCall(session: AuthSession, callId: String) {
        rpc("takt_end_call", JSONObject().put("p_call_id", callId), session)
    }

    suspend fun getLiveKitToken(session: AuthSession, callId: String): CallToken = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.LIVEKIT_TOKEN_URL.trimEnd('/')
        if (baseUrl.isBlank()) throw ApiException("CALL_SERVER_NOT_CONFIGURED", 503, "Сервер звонков ещё не настроен")
        val json = JSONObject(
            requestExternal(
                "$baseUrl/api/livekit-token",
                JSONObject().put("callId", callId),
                session.accessToken,
            ),
        )
        CallToken(serverUrl = json.getString("serverUrl"), token = json.getString("token"))
    }

    private suspend fun rpc(name: String, body: JSONObject, session: AuthSession): JSONObject = withContext(Dispatchers.IO) {
        val raw = request("/rest/v1/rpc/$name", "POST", body, session.accessToken)
        if (raw.trim().startsWith("[")) {
            val array = JSONArray(raw)
            if (array.length() == 0) JSONObject() else array.getJSONObject(0)
        } else {
            JSONObject(raw.ifBlank { "{}" })
        }
    }

    private suspend fun rpcArray(name: String, body: JSONObject, session: AuthSession): JSONArray = withContext(Dispatchers.IO) {
        val raw = request("/rest/v1/rpc/$name", "POST", body, session.accessToken).trim()
        when {
            raw.startsWith("[") -> JSONArray(raw)
            raw.isBlank() -> JSONArray()
            else -> JSONObject(raw).optJSONArray("data") ?: JSONArray()
        }
    }

    private fun request(path: String, method: String, body: JSONObject?, accessToken: String?): String =
        requestUrl("${BuildConfig.SUPABASE_URL.trimEnd('/')}$path", method, body, accessToken, true)

    private fun requestExternal(url: String, body: JSONObject, accessToken: String): String =
        requestUrl(url, "POST", body, accessToken, false)

    private fun requestUrl(
        url: String,
        method: String,
        body: JSONObject?,
        accessToken: String?,
        includeSupabaseApiKey: Boolean,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (includeSupabaseApiKey) setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${accessToken ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
            if (body != null) doOutput = true
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw apiError(status, text)
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun apiError(status: Int, raw: String): ApiException {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val code = json?.optString("code")?.takeIf(String::isNotBlank)
            ?: json?.optString("error_code")?.takeIf(String::isNotBlank)
        val message = json?.optString("msg")?.takeIf(String::isNotBlank)
            ?: json?.optString("message")?.takeIf(String::isNotBlank)
            ?: json?.optString("error_description")?.takeIf(String::isNotBlank)
            ?: "Сервер вернул ошибку $status"
        return ApiException(code, status, message)
    }

    private fun randomUrlSafe(bytes: Int): String {
        val raw = ByteArray(bytes)
        SecureRandom().nextBytes(raw)
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}

data class DownloadedAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val durationSeconds: Int?,
)
