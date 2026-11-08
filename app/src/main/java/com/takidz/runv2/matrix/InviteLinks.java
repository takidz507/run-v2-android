package com.takidz.runv2.matrix;

import com.takidz.runv2.model.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * App-specific invite links, plus the local mapping from a Run room code to
 * a Matrix room ID / alias.
 *
 * <p>Link formats (exact):
 * <ul>
 *   <li>Room: {@code run://join/<ROOM-CODE>} — e.g. {@code run://join/RUN-AB12CD34}.</li>
 *   <li>Friend: {@code run://friend/<USER-ID>} — e.g. {@code run://friend/%40taki%3Atchncs.de}
 *       (the user ID is percent-encoded because it contains {@code @} and {@code :}).</li>
 * </ul>
 *
 * <p>Room codes are SHORT, human-shareable codes (8 chars, {@code A-Z0-9}) that
 * exist only on this device / in this app's store. The mapping
 * code -&gt; Matrix room ID is LOCAL and EXPLICIT: it is created when the user
 * shares a room (see {@link #registerRoomCode(String, String)}) and resolved
 * when a link is opened (see {@link #resolveRoomCode(String)}). A code that
 * was never registered on this device cannot be resolved — there is no server
 * lookup for Run codes, by design. To join a room from another device, share
 * the Matrix room ID / alias instead, or register the same code there.
 *
 * <p>Persistence: the mapping is stored as JSON in the given {@link SecureStore}
 * under key {@code runv2.room_codes.v1}. No passwords or tokens are involved.
 */
public final class InviteLinks {

    static final String STORE_KEY = "runv2.room_codes.v1";
    static final String SCHEME = "run";
    static final String HOST_JOIN = "join";
    static final String HOST_FRIEND = "friend";

    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;

    private final SecureStore store;
    private final Map<String, String> codeToRoomId = new LinkedHashMap<>();

    public InviteLinks(SecureStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
        load();
    }

    /** Kind of link produced by {@link #parse(String)}. */
    public enum Kind {
        ROOM, FRIEND
    }

    /** A parsed {@code run://...} link. */
    public static final class ParsedInvite {
        private final Kind kind;
        private final String roomCode; // ROOM links only, else null
        private final String userId;   // FRIEND links only, else null

        public ParsedInvite(Kind kind, String roomCode, String userId) {
            this.kind = kind;
            this.roomCode = roomCode;
            this.userId = userId;
        }

        public Kind getKind() {
            return kind;
        }

        public String getRoomCode() {
            return roomCode;
        }

        public String getUserId() {
            return userId;
        }
    }

    /** Builds {@code run://join/<roomCode>}. */
    public static String buildRoomLink(String roomCode) {
        if (roomCode == null) {
            throw new IllegalArgumentException("roomCode must not be null");
        }
        return SCHEME + "://" + HOST_JOIN + "/" + encode(roomCode.trim());
    }

    /** Builds {@code run://friend/<percent-encoded userId>}. */
    public static String buildFriendLink(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return SCHEME + "://" + HOST_FRIEND + "/" + encode(userId.trim());
    }

    /**
     * Parses a {@code run://...} link. Accepts both link forms above.
     * Returns an error ({@code INVALID_ARGUMENT}) for anything else —
     * wrong scheme, unknown host, or an empty code / user ID.
     */
    public static Result<ParsedInvite> parse(String link) {
        if (link == null || link.trim().isEmpty()) {
            return Result.error("INVALID_ARGUMENT", "Link is empty.");
        }
        String text = link.trim();
        String prefix = SCHEME + "://";
        if (!text.startsWith(prefix)) {
            return Result.error("INVALID_ARGUMENT", "Not a Run invite link.");
        }
        String rest = text.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return Result.error("INVALID_ARGUMENT", "Invite link is incomplete.");
        }
        String host = rest.substring(0, slash);
        String payload = decode(rest.substring(slash + 1));
        if (payload == null || payload.isEmpty()) {
            return Result.error("INVALID_ARGUMENT", "Invite link has an empty code.");
        }
        if (HOST_JOIN.equals(host)) {
            return Result.ok(new ParsedInvite(Kind.ROOM, payload, null));
        }
        if (HOST_FRIEND.equals(host)) {
            return Result.ok(new ParsedInvite(Kind.FRIEND, null, payload));
        }
        return Result.error("INVALID_ARGUMENT", "Unknown invite link type.");
    }

    /** Generates a fresh random 8-character room code (A-Z, 0-9). */
    public static String generateRoomCode() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Registers a code -&gt; Matrix room ID mapping (local only) and persists it.
     * The room ID must look like a Matrix room ID ({@code !...:...}) or alias ({@code #...:...}).
     */
    public synchronized Result<Void> registerRoomCode(String roomCode, String roomId) {
        if (!isValidCode(roomCode)) {
            return Result.error("INVALID_ARGUMENT", "Room code must be 4-16 upper-case letters/digits.");
        }
        if (roomId == null || roomId.trim().isEmpty()
                || !(roomId.startsWith("!") || roomId.startsWith("#"))) {
            return Result.error("INVALID_ARGUMENT", "Room ID must start with ! or #.");
        }
        codeToRoomId.put(roomCode.trim().toUpperCase(Locale.ROOT), roomId.trim());
        persist();
        return Result.ok(null);
    }

    /** Resolves a previously registered code to its Matrix room ID / alias. */
    public synchronized Result<String> resolveRoomCode(String roomCode) {
        if (!isValidCode(roomCode)) {
            return Result.error("INVALID_ARGUMENT", "Room code must be 4-16 upper-case letters/digits.");
        }
        String roomId = codeToRoomId.get(roomCode.trim().toUpperCase(Locale.ROOT));
        if (roomId == null) {
            return Result.error("NOT_FOUND", "No room is registered under this code on this device.");
        }
        return Result.ok(roomId);
    }

    /** All registered codes (for settings / debugging). Returns a copy. */
    public synchronized Map<String, String> getAllMappings() {
        return new LinkedHashMap<>(codeToRoomId);
    }

    private static boolean isValidCode(String code) {
        if (code == null) {
            return false;
        }
        String c = code.trim();
        if (c.length() < 4 || c.length() > 16) {
            return false;
        }
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            if (!((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private void persist() {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : codeToRoomId.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
        }
        json.append('}');
        store.putString(STORE_KEY, json.toString());
    }

    private void load() {
        String json = store.getString(STORE_KEY);
        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
            return;
        }
        // Minimal flat-object parse: {"CODE":"!room:server",...}. Values we wrote
        // contain no quotes/backslashes, so this stays correct for our own data.
        String body = json.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            return; // corrupt: start empty rather than crashing
        }
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) {
            return;
        }
        for (String pair : body.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String k = stripQuotes(kv[0].trim());
            String v = stripQuotes(kv[1].trim());
            if (!k.isEmpty() && !v.isEmpty()) {
                codeToRoomId.put(k, v);
            }
        }
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** Percent-encodes a link segment (same unreserved set as the client encoder). */
    static String encode(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else {
                out.append(String.format("%%%02X", (int) c));
            }
        }
        return out.toString();
    }

    /** Percent-decodes a link segment; returns null on malformed input. */
    static String decode(String value) {
        try {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '%' && i + 2 < value.length()) {
                    int hex = Integer.parseInt(value.substring(i + 1, i + 3), 16);
                    out.append((char) hex);
                    i += 2;
                } else if (c == '%') {
                    return null;
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
