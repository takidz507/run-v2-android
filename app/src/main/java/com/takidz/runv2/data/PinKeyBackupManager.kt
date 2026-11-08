package com.takidz.runv2.data

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the 6-digit PIN key backup and room encryption keys.
 *
 * Uses PBKDF2WithHmacSHA256 (120,000 iterations) + AES-256-GCM to encrypt/decrypt
 * the user's room key vault stored in Matrix Account Data (`m.runv2.key_backup.v2`).
 *
 * If the user loses their 6-digit PIN, the encrypted backup on the server cannot
 * be decrypted by anyone (permanent loss by design).
 */
class PinKeyBackupManager(private val sessionStore: SecureSessionStore) {

    private val secureRandom = SecureRandom()

    fun isValidPin(pin: String): Boolean {
        return pin.length == 6 && pin.all { it.isDigit() }
    }

    fun computePinDigestHex(pin: String, userId: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$userId:runv2-pin:$pin".toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Gets or creates a 256-bit room key for a specific roomId in the local vault.
     */
    @Synchronized
    fun getOrCreateRoomSecretKey(roomId: String): ByteArray {
        val vault = JSONObject(sessionStore.getRoomKeyVaultJson())
        val existingB64 = vault.optString(roomId, "")
        if (existingB64.isNotBlank()) {
            return Base64.decode(existingB64, Base64.NO_WRAP)
        }
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        vault.put(roomId, Base64.encodeToString(keyBytes, Base64.NO_WRAP))
        sessionStore.saveRoomKeyVaultJson(vault.toString())
        return keyBytes
    }

    /**
     * Imports a shared room key (e.g., from room state or PIN backup) into the local vault.
     */
    @Synchronized
    fun storeRoomSecretKeyBase64(roomId: String, keyB64: String) {
        if (keyB64.isBlank()) return
        val vault = JSONObject(sessionStore.getRoomKeyVaultJson())
        vault.put(roomId, keyB64)
        sessionStore.saveRoomKeyVaultJson(vault.toString())
    }

    @Synchronized
    fun getRoomSecretKeyBase64(roomId: String): String {
        val bytes = getOrCreateRoomSecretKey(roomId)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Encrypts the entire local room key vault with the user's 6-digit PIN using
     * PBKDF2-HMAC-SHA256 + AES-256-GCM so it can be uploaded to Matrix Account Data.
     */
    fun createEncryptedBackupPayload(pin: String, userId: String): JSONObject {
        require(isValidPin(pin)) { "PIN must be 6 digits" }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val aesKey = deriveAesKeyFromPin(pin, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(userId.toByteArray(StandardCharsets.UTF_8))

        val plaintextBytes = sessionStore.getRoomKeyVaultJson().toByteArray(StandardCharsets.UTF_8)
        val ciphertextBytes = cipher.doFinal(plaintextBytes)

        return JSONObject().apply {
            put("version", 2)
            put("algorithm", "PBKDF2-SHA256-AES256-GCM")
            put("iterations", PBKDF2_ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP))
            put("updated_at", System.currentTimeMillis())
        }
    }

    /**
     * Decrypts a backup payload retrieved from Matrix Account Data using the 6-digit PIN
     * and merges the restored keys into the local vault.
     * Throws an exception if the PIN is wrong (AEADBadTagException).
     */
    fun restoreFromEncryptedBackupPayload(pin: String, userId: String, payload: JSONObject): Int {
        require(isValidPin(pin)) { "PIN must be 6 digits" }
        val salt = Base64.decode(payload.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(payload.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(payload.getString("ciphertext"), Base64.NO_WRAP)

        val aesKey = deriveAesKeyFromPin(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(userId.toByteArray(StandardCharsets.UTF_8))

        val decryptedJson = String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        val restoredVault = JSONObject(decryptedJson)
        val currentVault = JSONObject(sessionStore.getRoomKeyVaultJson())

        var restoredCount = 0
        val keys = restoredVault.keys()
        while (keys.hasNext()) {
            val roomId = keys.next()
            val keyB64 = restoredVault.optString(roomId)
            if (keyB64.isNotBlank()) {
                currentVault.put(roomId, keyB64)
                restoredCount++
            }
        }
        sessionStore.saveRoomKeyVaultJson(currentVault.toString())
        sessionStore.setPinConfigured(true, computePinDigestHex(pin, userId))
        return restoredCount
    }

    /**
     * Encrypts a message body for a room using the room's AES-256-GCM key.
     */
    fun encryptRoomMessage(roomId: String, plaintext: String): JSONObject {
        val keyBytes = getOrCreateRoomSecretKey(roomId)
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(128, iv)
        )
        cipher.updateAAD(roomId.toByteArray(StandardCharsets.UTF_8))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        return JSONObject().apply {
            put("algorithm", RunConfig.ROOM_ENCRYPTION_ALGORITHM)
            put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("run_e2ee_v2", true)
        }
    }

    /**
     * Attempts to decrypt an encrypted room event content JSON.
     * Forwards standard Olm/Megolm events or Run v2 AES-GCM payloads.
     */
    fun decryptRoomMessage(roomId: String, content: JSONObject): Pair<String, Boolean> {
        return try {
            if (content.optBoolean("run_e2ee_v2", false)) {
                val keyBytes = getOrCreateRoomSecretKey(roomId)
                val iv = Base64.decode(content.getString("iv"), Base64.NO_WRAP)
                val cipherBytes = Base64.decode(content.getString("ciphertext"), Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(keyBytes, "AES"),
                    GCMParameterSpec(128, iv)
                )
                cipher.updateAAD(roomId.toByteArray(StandardCharsets.UTF_8))
                val plain = String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
                Pair(plain, false)
            } else {
                // Standard Matrix m.megolm.v1.aes-sha2 event from external client
                Pair("🔒 رسالة مشفرة — أدخل رمز PIN لمزامنة مفاتيح الغرفة", true)
            }
        } catch (_: Exception) {
            Pair("🔒 تعذر فك تشفير الرسالة (تحقق من رمز PIN)", true)
        }
    }

    private fun deriveAesKeyFromPin(pin: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    companion object {
        private const val PBKDF2_ITERATIONS = 120_000
    }
}
