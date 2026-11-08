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
 * Manages the 6-digit PIN key backup for real Matrix Megolm E2EE sessions.
 *
 * Uses PBKDF2WithHmacSHA256 (120,000 iterations) + AES-256-GCM to encrypt/decrypt
 * the exported `OlmInboundGroupSession` keys stored in Matrix Account Data
 * (`m.runv2.key_backup.v2`).
 *
 * Strictly uses official Olm/Megolm session exports via `OlmCryptoManager` —
 * zero custom AES encryption is used for chat messages.
 *
 * If the user loses their 6-digit PIN, the encrypted backup on the server cannot
 * be decrypted by anyone (permanent loss by design).
 */
class PinKeyBackupManager(
    private val sessionStore: SecureSessionStore,
    private val olmCryptoManager: OlmCryptoManager
) {

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
     * Exports all real Megolm inbound group sessions (`m.megolm.v1.aes-sha2`) from
     * `OlmCryptoManager` and encrypts them with the user's 6-digit PIN using
     * PBKDF2-HMAC-SHA256 + AES-256-GCM so they can be stored in Matrix Account Data.
     */
    fun createEncryptedBackupPayload(pin: String, userId: String): JSONObject {
        require(isValidPin(pin)) { "رمز PIN يجب أن يتكون من 6 أرقام" }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val aesKey = deriveAesKeyFromPin(pin, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(userId.toByteArray(StandardCharsets.UTF_8))

        val exportedMegolmVault = olmCryptoManager.exportMegolmSessionsForPinBackup()
        val plaintextBytes = exportedMegolmVault.toString().toByteArray(StandardCharsets.UTF_8)
        val ciphertextBytes = cipher.doFinal(plaintextBytes)

        return JSONObject().apply {
            put("version", 2)
            put("algorithm", "PBKDF2-SHA256-AES256-GCM")
            put("megolm_algorithm", RunConfig.ROOM_ENCRYPTION_ALGORITHM)
            put("iterations", PBKDF2_ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP))
            put("updated_at", System.currentTimeMillis())
        }
    }

    /**
     * Decrypts a backup payload retrieved from Matrix Account Data using the 6-digit PIN
     * and imports the restored Megolm inbound group sessions into `OlmCryptoManager`.
     * Throws an exception if the PIN is wrong (`AEADBadTagException`).
     */
    fun restoreFromEncryptedBackupPayload(pin: String, userId: String, payload: JSONObject): Int {
        require(isValidPin(pin)) { "رمز PIN يجب أن يتكون من 6 أرقام" }
        val salt = Base64.decode(payload.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(payload.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(payload.getString("ciphertext"), Base64.NO_WRAP)

        val aesKey = deriveAesKeyFromPin(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(userId.toByteArray(StandardCharsets.UTF_8))

        val decryptedJsonStr = String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        val decryptedBackupObj = JSONObject(decryptedJsonStr)
        val restoredCount = olmCryptoManager.importMegolmSessionsFromPinBackup(decryptedBackupObj)

        sessionStore.setPinConfigured(true, computePinDigestHex(pin, userId))
        return restoredCount
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
