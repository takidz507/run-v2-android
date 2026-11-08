package com.takidz.runv2.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encrypted local storage for the active Run session, PIN verification hash,
 * local E2EE room keys, call settings, and call history.
 *
 * SECURITY RULE: Never store or log the user's plaintext password.
 */
class SecureSessionStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "run_v2_secure_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("run_v2_fallback_session", Context.MODE_PRIVATE)
    }

    fun saveSession(
        accessToken: String,
        internalUserId: String,
        deviceId: String,
        displayName: String
    ) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_USER_ID, internalUserId)
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getInternalUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun getDisplayName(): String = prefs.getString(KEY_DISPLAY_NAME, null)
        ?: getInternalUserId()?.let { RunConfig.toPublicHandle(it).removePrefix("@") }
        ?: ""

    fun updateDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name).apply()
    }

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank() && !getInternalUserId().isNullOrBlank()

    fun saveSyncBatchToken(token: String) {
        prefs.edit().putString(KEY_NEXT_BATCH, token).apply()
    }

    fun getSyncBatchToken(): String? = prefs.getString(KEY_NEXT_BATCH, null)

    fun setPinConfigured(configured: Boolean, verificationDigestHex: String? = null) {
        val editor = prefs.edit().putBoolean(KEY_PIN_CONFIGURED, configured)
        if (verificationDigestHex != null) {
            editor.putString(KEY_PIN_DIGEST, verificationDigestHex)
        }
        editor.apply()
    }

    fun isPinConfigured(): Boolean = prefs.getBoolean(KEY_PIN_CONFIGURED, false)
    fun getPinDigest(): String? = prefs.getString(KEY_PIN_DIGEST, null)

    fun saveRoomKeyVaultJson(json: String) {
        prefs.edit().putString(KEY_ROOM_KEYS_JSON, json).apply()
    }

    fun getRoomKeyVaultJson(): String = prefs.getString(KEY_ROOM_KEYS_JSON, "{}") ?: "{}"

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.WARM_NIGHT.name)
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.WARM_NIGHT
    }

    fun saveCallQualityMode(mode: CallQualityMode) {
        prefs.edit().putString(KEY_CALL_QUALITY, mode.name).apply()
    }

    fun getCallQualityMode(): CallQualityMode {
        val raw = prefs.getString(KEY_CALL_QUALITY, CallQualityMode.DEFAULT_480P.name)
        return CallQualityMode.entries.firstOrNull { it.name == raw } ?: CallQualityMode.DEFAULT_480P
    }

    fun saveCallLogEntry(entry: CallLogEntry) {
        val existing = getCallLogs().toMutableList()
        existing.add(0, entry)
        val arr = JSONArray()
        existing.take(50).forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("roomId", item.roomId)
                    put("peerName", item.peerName)
                    put("isVideo", item.isVideo)
                    put("direction", item.direction.name)
                    put("durationSeconds", item.durationSeconds)
                    put("timestamp", item.timestamp)
                    put("avatarSeedColor", item.avatarSeedColor)
                }
            )
        }
        prefs.edit().putString(KEY_CALL_LOGS, arr.toString()).apply()
    }

    fun getCallLogs(): List<CallLogEntry> {
        val raw = prefs.getString(KEY_CALL_LOGS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        CallLogEntry(
                            id = obj.optString("id"),
                            roomId = obj.optString("roomId"),
                            peerName = obj.optString("peerName"),
                            isVideo = obj.optBoolean("isVideo", true),
                            direction = CallDirection.entries.firstOrNull {
                                it.name == obj.optString("direction")
                            } ?: CallDirection.OUTGOING,
                            durationSeconds = obj.optInt("durationSeconds", 0),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            avatarSeedColor = obj.optLong("avatarSeedColor", 0xFF8A6D3BL)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearSession() {
        val theme = getThemeMode()
        val callQuality = getCallQualityMode()
        prefs.edit().clear().apply()
        saveThemeMode(theme)
        saveCallQualityMode(callQuality)
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "internal_user_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_NEXT_BATCH = "sync_next_batch"
        private const val KEY_PIN_CONFIGURED = "pin_configured"
        private const val KEY_PIN_DIGEST = "pin_digest_hex"
        private const val KEY_ROOM_KEYS_JSON = "room_keys_vault_json"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_CALL_QUALITY = "call_quality_mode"
        private const val KEY_CALL_LOGS = "call_logs_json"
    }
}
