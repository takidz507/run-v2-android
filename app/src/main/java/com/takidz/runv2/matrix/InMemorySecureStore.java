package com.takidz.runv2.matrix;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory {@link SecureStore} for tests and local development only.
 *
 * <p>NOT for production: values live in a plain {@link HashMap} and vanish
 * when the process dies. The production app must supply an
 * EncryptedSharedPreferences-backed implementation instead.
 */
public final class InMemorySecureStore implements SecureStore {

    private final Map<String, String> map = new HashMap<>();

    @Override
    public synchronized String getString(String key) {
        return map.get(key);
    }

    @Override
    public synchronized void putString(String key, String value) {
        map.put(key, value);
    }

    @Override
    public synchronized void remove(String key) {
        map.remove(key);
    }

    @Override
    public synchronized boolean contains(String key) {
        return map.containsKey(key);
    }
}
