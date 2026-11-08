package com.takidz.runv2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Short-lived TURN credentials for the voice/video call layer.
 *
 * <p>Source: {@code GET /_matrix/client/v3/voip/turnServer}. Field shapes (exact):
 * <ul>
 *   <li>{@code username} — TURN username (time-limited, issued by the homeserver).</li>
 *   <li>{@code password} — TURN password (time-limited). This is a TURN credential,
 *       NOT the Matrix account password. It lives in memory only: it is never
 *       persisted by this layer and must never be logged.</li>
 *   <li>{@code uris} — TURN server URIs, e.g. {@code "turn:turn.example.org:3478?transport=udp"}.</li>
 *   <li>{@code ttlSeconds} — credential lifetime in seconds ({@code ttl} from the server).</li>
 * </ul>
 *
 * <p>This class only carries the credentials. It does NOT implement WebRTC,
 * ICE, or any call signalling — the call layer consumes these values.
 */
public final class TurnCredentials {

    private final String username;
    private final String password;
    private final List<String> uris;
    private final int ttlSeconds;

    public TurnCredentials(String username, String password, List<String> uris, int ttlSeconds) {
        this.username = username;
        this.password = password;
        this.uris = uris == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(uris));
        this.ttlSeconds = ttlSeconds;
    }

    public String getUsername() {
        return username;
    }

    /** TURN password. In-memory only — never persist, never log. */
    public String getPassword() {
        return password;
    }

    public List<String> getUris() {
        return uris;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    @Override
    public String toString() {
        // Deliberately omits the password.
        return "TurnCredentials{username=" + username + ", uris=" + uris + ", ttl=" + ttlSeconds + "}";
    }
}
