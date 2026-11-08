package com.takidz.runv2.matrix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.takidz.runv2.model.Result;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * PIN-based encryption for a small opaque key-backup blob.
 *
 * <p>What this IS: a way to lock a small blob (e.g. future key material) with
 * the user's 6-digit PIN and store the encrypted blob on the server as
 * account data ({@code run.v2.key_backup}) so it can be restored on another
 * phone with the same PIN.
 *
 * <p>What this is NOT: end-to-end encryption. Real E2E (Olm/Megolm) is NOT
 * implemented in this layer — it needs the Kotlin Matrix SDK or a dedicated
 * Olm implementation. This helper encrypts ONE opaque blob; it does not
 * encrypt messages, does not implement ratchets, and must not be presented
 * to the user as "E2E encryption".
 *
 * <p>Cryptography (documented parameters, all standard JDK):
 * <ul>
 *   <li>Key derivation: PBKDF2-HMAC-SHA256, {@value #PBKDF2_ITERATIONS} iterations,
 *       256-bit key, 16-byte random salt (fresh per blob).</li>
 *   <li>Cipher: AES-256-GCM, 12-byte random IV, 128-bit tag
 *       ({@code AES/GCM/NoPadding}).</li>
 *   <li>Blob format v1 (JSON): {@code {"v":1,"salt":"<base64>","iv":"<base64>","ct":"<base64>"}}
 *       where {@code ct} is ciphertext+tag. Base64 is standard, no line wraps.</li>
 * </ul>
 *
 * <p>PIN honesty: a 6-digit PIN has ~20 bits of entropy. PBKDF2 with 210,000
 * iterations only SLOWS offline guessing; it cannot make a 6-digit PIN strong.
 * The real protection is the server (rate limits / lockout on the account) plus
 * the fact that the blob alone is useless without the PIN. If the user forgets
 * the PIN, the backup is UNRECOVERABLE — there is no reset path, by design.
 *
 * <p>The PIN is NOT the account password. The two must never be confused in
 * UI copy: the password logs in, the PIN unlocks the backup. The PIN is
 * accepted as a {@code char[]}, used once, and wiped. It is never logged,
 * never stored (the sibling {@code security.SecureStore} stores only a
 * SHA-256 hash for local PIN verification — a different mechanism).
 */
public final class PinBackup {

    /** PBKDF2 iterations (OWASP 2023 guidance for PBKDF2-HMAC-SHA256 is 600k; 210k is the floor used here — see note in MATRIX_CLIENT.md). */
    public static final int PBKDF2_ITERATIONS = 210_000;
    /** Derived key length, bits (AES-256). */
    public static final int KEY_LENGTH_BITS = 256;
    /** Salt length, bytes. */
    public static final int SALT_BYTES = 16;
    /** GCM IV length, bytes. */
    public static final int IV_BYTES = 12;
    /** GCM authentication tag length, bits. */
    public static final int GCM_TAG_BITS = 128;
    /** Blob format version written into every blob. */
    public static final int BLOB_VERSION = 1;

    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    private final Gson gson = new GsonBuilder().create();

    /** One encrypted blob: version + salt + IV + ciphertext (incl. GCM tag). */
    public static final class EncryptedBlob {
        @SerializedName("v") private final int version;
        @SerializedName("salt") private final String saltBase64;
        @SerializedName("iv") private final String ivBase64;
        @SerializedName("ct") private final String ciphertextBase64;

        public EncryptedBlob(int version, byte[] salt, byte[] iv, byte[] ciphertext) {
            this.version = version;
            this.saltBase64 = Base64.getEncoder().encodeToString(salt);
            this.ivBase64 = Base64.getEncoder().encodeToString(iv);
            this.ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext);
        }

        public int getVersion() {
            return version;
        }

        public byte[] getSalt() {
            return Base64.getDecoder().decode(saltBase64);
        }

        public byte[] getIv() {
            return Base64.getDecoder().decode(ivBase64);
        }

        public byte[] getCiphertext() {
            return Base64.getDecoder().decode(ciphertextBase64);
        }

        /** Serializes the blob to its JSON form (safe to store / send). */
        public String toJson() {
            return new GsonBuilder().create().toJson(this);
        }

        /** Parses a blob from its JSON form. */
        public static Result<EncryptedBlob> fromJson(String json) {
            if (json == null || json.trim().isEmpty()) {
                return Result.error("INVALID_ARGUMENT", "Backup blob is empty.");
            }
            try {
                EncryptedBlob blob = new GsonBuilder().create().fromJson(json, EncryptedBlob.class);
                if (blob == null || blob.saltBase64 == null || blob.ivBase64 == null
                        || blob.ciphertextBase64 == null) {
                    return Result.error("PARSE_ERROR", "Backup blob is incomplete.");
                }
                if (blob.version != BLOB_VERSION) {
                    return Result.error("CRYPTO_ERROR", "Backup blob version is not supported.");
                }
                // Validate the Base64 decodes.
                blob.getSalt();
                blob.getIv();
                blob.getCiphertext();
                return Result.ok(blob);
            } catch (JsonSyntaxException | IllegalArgumentException e) {
                return Result.error("PARSE_ERROR", "Backup blob could not be read.");
            }
        }
    }

    /** Validates a PIN: exactly 6 ASCII digits. Returns an error Result otherwise. */
    public static Result<Void> validatePin(char[] pin) {
        if (pin == null || pin.length != 6) {
            return Result.error("INVALID_ARGUMENT", "PIN must be exactly 6 digits.");
        }
        for (char c : pin) {
            if (c < '0' || c > '9') {
                return Result.error("INVALID_ARGUMENT", "PIN must contain digits only.");
            }
        }
        return Result.ok(null);
    }

    /**
     * Encrypts {@code plaintext} under {@code pin}. A fresh random salt + IV
     * are generated per call. The PIN array is wiped before returning.
     */
    public Result<EncryptedBlob> encrypt(byte[] plaintext, char[] pin) {
        Result<Void> valid = validatePin(pin);
        if (valid.isError()) {
            wipe(pin);
            return Result.error(valid.getErrorCode(), valid.getErrorMessage());
        }
        if (plaintext == null) {
            wipe(pin);
            return Result.error("INVALID_ARGUMENT", "Nothing to encrypt.");
        }
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_BYTES];
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(salt);
            random.nextBytes(iv);
            SecretKey key = deriveKey(pin, salt);
            try {
                Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
                byte[] ciphertext = cipher.doFinal(plaintext);
                return Result.ok(new EncryptedBlob(BLOB_VERSION, salt, iv, ciphertext));
            } finally {
                wipeKey(key);
            }
        } catch (Exception e) {
            return Result.error("CRYPTO_ERROR", "Encryption failed.");
        } finally {
            wipe(pin);
        }
    }

    /**
     * Decrypts {@code blob} under {@code pin}. A wrong PIN (or a corrupt blob)
     * fails with {@code CRYPTO_ERROR} — the two cases are deliberately
     * indistinguishable. The PIN array is wiped before returning.
     */
    public Result<byte[]> decrypt(EncryptedBlob blob, char[] pin) {
        Result<Void> valid = validatePin(pin);
        if (valid.isError()) {
            wipe(pin);
            return Result.error(valid.getErrorCode(), valid.getErrorMessage());
        }
        if (blob == null) {
            wipe(pin);
            return Result.error("INVALID_ARGUMENT", "Backup blob is required.");
        }
        try {
            SecretKey key = deriveKey(pin, blob.getSalt());
            try {
                Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, blob.getIv()));
                return Result.ok(cipher.doFinal(blob.getCiphertext()));
            } finally {
                wipeKey(key);
            }
        } catch (Exception e) {
            // AEAD failure: wrong PIN or tampered blob. Same message either way.
            return Result.error("CRYPTO_ERROR", "Wrong PIN or damaged backup.");
        } finally {
            wipe(pin);
        }
    }

    /** Encrypts a UTF-8 string. Convenience wrapper over {@link #encrypt(byte[], char[])}. */
    public Result<EncryptedBlob> encryptText(String plaintext, char[] pin) {
        if (plaintext == null) {
            wipe(pin);
            return Result.error("INVALID_ARGUMENT", "Nothing to encrypt.");
        }
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), pin);
    }

    /** Decrypts to a UTF-8 string. Convenience wrapper over {@link #decrypt(EncryptedBlob, char[])}. */
    public Result<String> decryptText(EncryptedBlob blob, char[] pin) {
        Result<byte[]> r = decrypt(blob, pin);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage());
        }
        return Result.ok(new String(r.getValue(), StandardCharsets.UTF_8));
    }

    /**
     * Stores an encrypted blob on the server as account data
     * ({@code PUT .../account_data/run.v2.key_backup}).
     */
    public Result<Void> storeBackup(MatrixHttpClient client, EncryptedBlob blob) {
        if (client == null || blob == null) {
            return Result.error("INVALID_ARGUMENT", "Client and backup blob are required.");
        }
        return client.putAccountData(MatrixConfig.KEY_BACKUP_ACCOUNT_DATA_TYPE, blob.toJson());
    }

    /**
     * Fetches the stored blob from the server. When no backup exists yet the
     * server answers {@code M_NOT_FOUND} — that error is passed through
     * unchanged so callers can tell "no backup yet" from a real failure.
     */
    public Result<EncryptedBlob> fetchBackup(MatrixHttpClient client) {
        if (client == null) {
            return Result.error("INVALID_ARGUMENT", "Client is required.");
        }
        Result<String> r = client.getAccountData(MatrixConfig.KEY_BACKUP_ACCOUNT_DATA_TYPE);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        return EncryptedBlob.fromJson(r.getValue());
    }

    /** Derives the AES key from PIN + salt. Package-visible for the self-test only. */
    SecretKey deriveKey(char[] pin, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
    }

    /** Overwrites a derived key's bytes. Best effort — the JCE may hold copies. */
    private static void wipeKey(SecretKey key) {
        if (key != null && key.getEncoded() != null) {
            Arrays.fill(key.getEncoded(), (byte) 0);
        }
    }

    /** Overwrites a char array (PIN / password) in place. */
    public static void wipe(char[] chars) {
        if (chars != null) {
            Arrays.fill(chars, '\0');
        }
    }

    /** Overwrites a byte array in place. */
    public static void wipe(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
