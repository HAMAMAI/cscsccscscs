package app.takt.messenger.data

import org.json.JSONArray
import org.json.JSONObject

data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long,
)

data class UserProfile(
    val id: String,
    val email: String = "",
    val username: String? = null,
    val displayName: String,
    val about: String = "",
    val avatarColor: String = "#8C78FF",
    val lastSeenAt: String? = null,
    val isOnline: Boolean = false,
)

data class ChatFolder(
    val id: String,
    val name: String,
    val color: String,
    val position: Int,
)

data class ChatSettings(
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val folderId: String? = null,
    val mutedUntil: String? = null,
    val draft: String = "",
)

data class MediaAttachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Int,
    val durationSeconds: Int? = null,
)

data class ReplyPreview(
    val id: String,
    val body: String,
    val kind: String,
    val senderName: String,
)

data class MessageReaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
)

enum class DeliveryStatus { Sending, Sent, Delivered, Read, Received, Deleted }

data class MessengerMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val senderColor: String,
    val body: String,
    val kind: String,
    val createdAt: String,
    val editedAt: String? = null,
    val deletedAt: String? = null,
    val pinned: Boolean = false,
    val replyToId: String? = null,
    val replyPreview: ReplyPreview? = null,
    val forwardedFromId: String? = null,
    val attachment: MediaAttachment? = null,
    val reactions: List<MessageReaction> = emptyList(),
    val status: DeliveryStatus = DeliveryStatus.Sent,
    val localSending: Boolean = false,
)

data class ChatMember(
    val id: String,
    val displayName: String,
    val username: String? = null,
    val avatarColor: String,
    val role: String,
    val isOnline: Boolean = false,
    val lastSeenAt: String? = null,
)

data class TypingState(
    val userId: String,
    val displayName: String,
    val mode: String,
)

data class ActiveCall(
    val id: String,
    val conversationId: String,
    val startedBy: String,
    val startedByName: String,
    val video: Boolean,
    val startedAt: String,
)

data class ConversationSummary(
    val id: String,
    val kind: String,
    val title: String,
    val avatarColor: String,
    val settings: ChatSettings = ChatSettings(),
    val updatedAt: String,
    val lastMessage: MessengerMessage? = null,
    val unreadCount: Int = 0,
)

data class ChatState(
    val id: String,
    val kind: String,
    val title: String,
    val avatarColor: String,
    val createdAt: String,
    val settings: ChatSettings = ChatSettings(),
    val members: List<ChatMember> = emptyList(),
    val messages: List<MessengerMessage> = emptyList(),
    val typing: List<TypingState> = emptyList(),
    val activeCall: ActiveCall? = null,
)

data class CallHistoryItem(
    val id: String,
    val conversationId: String,
    val video: Boolean,
    val startedBy: String,
    val startedAt: String,
    val endedAt: String? = null,
)

data class BootstrapData(
    val profile: UserProfile,
    val conversations: List<ConversationSummary>,
    val folders: List<ChatFolder>,
    val calls: List<CallHistoryItem>,
)

enum class ThemeMode { Dark, Light, Midnight }
enum class AvatarShape { Circle, Rounded, Square }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.Dark,
    val accentHex: String = "#8C78FF",
    val avatarShape: AvatarShape = AvatarShape.Circle,
    val compactChats: Boolean = false,
)

private fun JSONObject.stringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

private fun JSONObject.objectOrNull(name: String): JSONObject? = if (isNull(name)) null else optJSONObject(name)

private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) add(getJSONObject(index))
}

fun JSONObject.toAuthSession(): AuthSession {
    val user = optJSONObject("user") ?: throw ApiException("AUTH_USER_MISSING", 500, "Сервер не вернул пользователя")
    val token = optString("access_token").ifBlank {
        optJSONObject("session")?.optString("access_token").orEmpty()
    }
    if (token.isBlank()) throw ApiException("AUTH_SESSION_MISSING", 401, "Войдите через подтверждённый аккаунт")
    val session = optJSONObject("session") ?: this
    return AuthSession(
        userId = user.getString("id"),
        email = user.optString("email"),
        accessToken = token,
        refreshToken = session.stringOrNull("refresh_token") ?: stringOrNull("refresh_token"),
        expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + session.optLong("expires_in", optLong("expires_in", 3600)),
    )
}

fun JSONObject.toProfile(): UserProfile = UserProfile(
    id = getString("id"),
    email = optString("email"),
    username = stringOrNull("username"),
    displayName = optString("display_name", "Пользователь"),
    about = optString("about"),
    avatarColor = optString("avatar_color", "#8C78FF"),
    lastSeenAt = stringOrNull("last_seen_at"),
    isOnline = optBoolean("is_online", false),
)

fun JSONObject.toFolder(): ChatFolder = ChatFolder(
    id = getString("id"),
    name = optString("name", "Папка"),
    color = optString("color", "#8C78FF"),
    position = optInt("position", 0),
)

fun JSONObject.toChatSettings(): ChatSettings = ChatSettings(
    archived = optBoolean("is_archived", false),
    pinned = optBoolean("is_pinned", false),
    folderId = stringOrNull("folder_id"),
    mutedUntil = stringOrNull("muted_until"),
    draft = optString("draft_text"),
)

fun JSONObject.toAttachment(): MediaAttachment = MediaAttachment(
    id = getString("id"),
    fileName = optString("file_name", "Файл"),
    mimeType = optString("mime_type", "application/octet-stream"),
    sizeBytes = optInt("size_bytes", 0),
    durationSeconds = if (isNull("duration_seconds")) null else optInt("duration_seconds"),
)

private fun JSONObject.toReplyPreview(): ReplyPreview = ReplyPreview(
    id = getString("id"),
    body = optString("body"),
    kind = optString("kind", "text"),
    senderName = optString("sender_name", "Пользователь"),
)

private fun JSONObject.toReaction(): MessageReaction = MessageReaction(
    emoji = optString("emoji"),
    count = optInt("count", 1),
    mine = optBoolean("mine", false),
)

private fun String.toDeliveryStatus(): DeliveryStatus = when (lowercase()) {
    "sending" -> DeliveryStatus.Sending
    "delivered" -> DeliveryStatus.Delivered
    "read" -> DeliveryStatus.Read
    "received" -> DeliveryStatus.Received
    "deleted" -> DeliveryStatus.Deleted
    else -> DeliveryStatus.Sent
}

fun JSONObject.toMessengerMessage(): MessengerMessage = MessengerMessage(
    id = getString("id"),
    conversationId = getString("conversation_id"),
    senderId = getString("sender_id"),
    senderName = optString("sender_name", "Пользователь"),
    senderColor = optString("sender_color", "#8C78FF"),
    body = optString("body"),
    kind = optString("kind", "text"),
    createdAt = optString("created_at"),
    editedAt = stringOrNull("edited_at"),
    deletedAt = stringOrNull("deleted_at"),
    pinned = optBoolean("is_pinned", false),
    replyToId = stringOrNull("reply_to_id"),
    replyPreview = objectOrNull("reply_preview")?.toReplyPreview(),
    forwardedFromId = stringOrNull("forwarded_from_id"),
    attachment = objectOrNull("attachment")?.toAttachment(),
    reactions = optJSONArray("reactions")?.objects()?.map(JSONObject::toReaction).orEmpty(),
    status = optString("status", "sent").toDeliveryStatus(),
)

fun JSONObject.toConversationSummary(): ConversationSummary = ConversationSummary(
    id = getString("id"),
    kind = optString("kind", "direct"),
    title = optString("title", "Чат"),
    avatarColor = optString("avatar_color", "#8C78FF"),
    settings = ChatSettings(
        archived = optBoolean("is_archived", false),
        pinned = optBoolean("is_pinned", false),
        folderId = stringOrNull("folder_id"),
        mutedUntil = stringOrNull("muted_until"),
        draft = optString("draft_text"),
    ),
    updatedAt = optString("updated_at"),
    lastMessage = objectOrNull("last_message")?.toMessengerMessage(),
    unreadCount = optInt("unread_count", 0),
)

private fun JSONObject.toMember(): ChatMember = ChatMember(
    id = getString("id"),
    displayName = optString("display_name", "Пользователь"),
    username = stringOrNull("username"),
    avatarColor = optString("avatar_color", "#8C78FF"),
    role = optString("role", "member"),
    isOnline = optBoolean("is_online", false),
    lastSeenAt = stringOrNull("last_seen_at"),
)

private fun JSONObject.toTyping(): TypingState = TypingState(
    userId = getString("user_id"),
    displayName = optString("display_name", "Пользователь"),
    mode = optString("mode", "typing"),
)

fun JSONObject.toActiveCall(): ActiveCall = ActiveCall(
    id = getString("id"),
    conversationId = optString("conversation_id"),
    startedBy = optString("started_by"),
    startedByName = optString("started_by_name", "Пользователь"),
    video = optBoolean("is_video", false),
    startedAt = optString("started_at"),
)

fun JSONObject.toChatState(): ChatState = ChatState(
    id = getString("id"),
    kind = optString("kind", "direct"),
    title = optString("title", "Чат"),
    avatarColor = optString("avatar_color", "#8C78FF"),
    createdAt = optString("created_at"),
    settings = objectOrNull("settings")?.toChatSettings() ?: ChatSettings(),
    members = optJSONArray("members")?.objects()?.map(JSONObject::toMember).orEmpty(),
    messages = optJSONArray("messages")?.objects()?.map(JSONObject::toMessengerMessage).orEmpty(),
    typing = optJSONArray("typing")?.objects()?.map(JSONObject::toTyping).orEmpty(),
    activeCall = objectOrNull("active_call")?.toActiveCall(),
)

private fun JSONObject.toCallHistory(): CallHistoryItem = CallHistoryItem(
    id = getString("id"),
    conversationId = getString("conversation_id"),
    video = optBoolean("is_video", false),
    startedBy = optString("started_by"),
    startedAt = optString("started_at"),
    endedAt = stringOrNull("ended_at"),
)

fun JSONObject.toBootstrapData(): BootstrapData = BootstrapData(
    profile = getJSONObject("profile").toProfile(),
    conversations = optJSONArray("conversations")?.objects()?.map(JSONObject::toConversationSummary).orEmpty(),
    folders = optJSONArray("folders")?.objects()?.map(JSONObject::toFolder).orEmpty(),
    calls = optJSONArray("calls")?.objects()?.map(JSONObject::toCallHistory).orEmpty(),
)
