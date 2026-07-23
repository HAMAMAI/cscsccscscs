package app.takt.messenger.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

class SecureSessionStore(context: Context) {
    private val preferences: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "takt_secure_session",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Android Keystore can be unavailable on a damaged device. The app still
        // works, but never writes a service-role or other server secret locally.
        context.getSharedPreferences("takt_secure_session_fallback", Context.MODE_PRIVATE)
    }

    fun load(): AuthSession? = runCatching {
        val json = JSONObject(preferences.getString("session", null) ?: return null)
        AuthSession(
            userId = json.getString("user_id"),
            email = json.optString("email"),
            accessToken = json.getString("access_token"),
            refreshToken = if (json.isNull("refresh_token")) null else json.optString("refresh_token"),
            expiresAtEpochSeconds = json.optLong("expires_at", 0),
        )
    }.getOrNull()

    fun save(session: AuthSession) {
        preferences.edit().putString(
            "session",
            JSONObject()
                .put("user_id", session.userId)
                .put("email", session.email)
                .put("access_token", session.accessToken)
                .put("refresh_token", session.refreshToken ?: JSONObject.NULL)
                .put("expires_at", session.expiresAtEpochSeconds)
                .toString(),
        ).apply()
    }

    fun clear() = preferences.edit().clear().apply()

    fun savePendingOAuth(verifier: String, state: String) {
        preferences.edit()
            .putString("oauth_verifier", verifier)
            .putString("oauth_state", state)
            .apply()
    }

    fun consumePendingOAuth(returnedState: String?): String? {
        val expectedState = preferences.getString("oauth_state", null)
        val verifier = preferences.getString("oauth_verifier", null)
        preferences.edit().remove("oauth_state").remove("oauth_verifier").apply()
        return verifier?.takeIf { !expectedState.isNullOrBlank() && expectedState == returnedState }
    }
}

class MessengerPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("takt_preferences", Context.MODE_PRIVATE)

    fun loadAppearance(): AppearanceSettings = AppearanceSettings(
        themeMode = runCatching {
            ThemeMode.valueOf(preferences.getString("theme_mode", ThemeMode.Dark.name).orEmpty())
        }.getOrDefault(ThemeMode.Dark),
        accentHex = preferences.getString("accent_hex", "#8C78FF") ?: "#8C78FF",
        avatarShape = runCatching {
            AvatarShape.valueOf(preferences.getString("avatar_shape", AvatarShape.Circle.name).orEmpty())
        }.getOrDefault(AvatarShape.Circle),
        compactChats = preferences.getBoolean("compact_chats", false),
    )

    fun saveAppearance(settings: AppearanceSettings) {
        preferences.edit()
            .putString("theme_mode", settings.themeMode.name)
            .putString("accent_hex", settings.accentHex)
            .putString("avatar_shape", settings.avatarShape.name)
            .putBoolean("compact_chats", settings.compactChats)
            .apply()
    }

    fun isBiometricLockEnabled(): Boolean = preferences.getBoolean("biometric_lock", false)

    fun setBiometricLockEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("biometric_lock", enabled).apply()
    }
}

class ChatCache(context: Context) {
    private val preferences = context.getSharedPreferences("takt_chat_cache", Context.MODE_PRIVATE)

    fun save(chatId: String, json: String) {
        if (json.length <= 500_000) preferences.edit().putString("chat_$chatId", json).apply()
    }

    fun load(chatId: String): String? = preferences.getString("chat_$chatId", null)

    fun clear() = preferences.edit().clear().apply()
}
