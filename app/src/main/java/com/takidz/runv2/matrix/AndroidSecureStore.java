package com.takidz.runv2.matrix;

/**
 * Bridges the Matrix layer's generic {@link SecureStore} interface to the
 * app's real encrypted store ({@code com.takidz.runv2.security.SecureStore},
 * Android Keystore + EncryptedSharedPreferences).
 *
 * <p>Wiring (app layer):
 * <pre>
 *   com.takidz.runv2.security.SecureStore real = new com.takidz.runv2.security.SecureStore(context);
 *   SecureStore store = new AndroidSecureStore(real);
 *   FriendStore friends = new FriendStore(store);
 *   InviteLinks links = new InviteLinks(store);
 * </pre>
 *
 * <p>Only non-secret JSON blobs (friends list, room-code mapping) travel
 * through this adapter. Passwords, PINs and tokens keep using the dedicated
 * methods on the real store.
 */
public final class AndroidSecureStore implements SecureStore {

    private final com.takidz.runv2.security.SecureStore delegate;

    public AndroidSecureStore(com.takidz.runv2.security.SecureStore delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public String getString(String key) {
        return delegate.getString(key);
    }

    @Override
    public void putString(String key, String value) {
        delegate.putString(key, value);
    }

    @Override
    public void remove(String key) {
        delegate.removeString(key);
    }

    @Override
    public boolean contains(String key) {
        return delegate.containsKey(key);
    }
}
