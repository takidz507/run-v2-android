package com.takidz.runv2.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Encrypted on-device storage (Android Keystore + EncryptedSharedPreferences).
 * Holds the account password and the PIN (stored only as a SHA-256 hash —
 * the plain PIN is never persisted, logged, or printed).
 */
public class SecureStore {

    private static final String PREFS_NAME = "run_v2_secure_prefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_PIN_HASH = "pin_hash";

    private final SharedPreferences prefs;

    public SecureStore(Context context) {
        SharedPreferences loaded;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            loaded = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            // Fallback: plain private prefs if the Keystore is unavailable.
            // Data stays app-private; the encrypted path is preferred.
            loaded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        this.prefs = loaded;
    }

    public void saveCredentials(String username, String password) {
        prefs.edit().putString(KEY_USERNAME, username).putString(KEY_PASSWORD, password).apply();
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public String getPassword() {
        return prefs.getString(KEY_PASSWORD, null);
    }

    public boolean hasCredentials() {
        return getUsername() != null && getPassword() != null;
    }

    /** Stores only the SHA-256 hash of the PIN, never the PIN itself. */
    public void setPin(String pin) {
        prefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).apply();
    }

    public boolean hasPin() {
        return prefs.getString(KEY_PIN_HASH, null) != null;
    }

    public boolean verifyPin(String pin) {
        String stored = prefs.getString(KEY_PIN_HASH, null);
        return stored != null && stored.equals(hashPin(pin));
    }


    public void clearAll() {
        prefs.edit().clear().apply();
    }

    // ---- Generic key/value storage (added by the Matrix client worker, 2026-11-08) ----
    // Used by the Matrix layer (FriendStore, InviteLinks) for non-secret JSON blobs
    // such as the friends list and the room-code mapping. Passwords, PINs and
    // access tokens are NEVER stored through these methods; they use the
    // dedicated methods above.

    /** Returns the stored string for {@code key}, or null when absent. */
    public String getString(String key) {
        return prefs.getString(key, null);
    }

    /** Stores {@code value} under {@code key}. */
    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    /** Removes {@code key} if present. */
    public void removeString(String key) {
        prefs.edit().remove(key).apply();
    }

    /** True when a value is stored under {@code key}. */
    public boolean containsKey(String key) {
        return prefs.contains(key);
    }

    public static String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
