package com.takidz.runv2.matrix;

/**
 * Minimal key/value store used by {@link FriendStore} and {@link InviteLinks}
 * for local persistence (friends list, room-code mapping).
 *
 * <p>Contract:
 * <ul>
 *   <li>Values are plain strings (JSON blobs). No passwords, PINs, access tokens
 *       or TURN passwords are ever written through this interface.</li>
 *   <li>Production implementation on Android MUST be backed by
 *       {@code EncryptedSharedPreferences} (or the platform keystore equivalent).
 *       The app layer supplies that implementation; this layer only defines the shape.</li>
 *   <li>{@link InMemorySecureStore} is a test/dev implementation only — it keeps
 *       values in a plain map and loses them when the process dies.</li>
 * </ul>
 */
public interface SecureStore {

    /** Returns the stored value for {@code key}, or null when absent. */
    String getString(String key);

    /** Stores {@code value} under {@code key}. Implementations persist it. */
    void putString(String key, String value);

    /** Removes {@code key} if present. */
    void remove(String key);

    /** True when a value is stored under {@code key}. */
    boolean contains(String key);
}
