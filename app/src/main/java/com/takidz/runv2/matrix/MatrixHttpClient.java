package com.takidz.runv2.matrix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.takidz.runv2.model.Friend;
import com.takidz.runv2.model.Message;
import com.takidz.runv2.model.Result;
import com.takidz.runv2.model.Room;
import com.takidz.runv2.model.SyncResult;
import com.takidz.runv2.model.TurnCredentials;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Real Matrix Client-Server API client (spec r1.11, {@code /_matrix/client/v3}),
 * pure Java over OkHttp 4.12.0 + Gson 2.10.1. Implements the scaffold's
 * {@link MatrixClient} UI contract and adds the full synchronous
 * {@link Result}-based API the rest of the app uses.
 *
 * <p>Threading: every {@link Result}-returning method is SYNCHRONOUS and performs
 * network I/O. Callers MUST run these off the UI thread (e.g. a background
 * executor). The {@link MatrixClient} interface methods ({@code login} with a
 * callback, {@code logout}) are the UI-friendly wrappers: they run on a
 * background thread and invoke the callback there — the UI must post results
 * to the main thread itself.
 *
 * <p>Errors: no {@link Result}-returning method throws. Failures are returned as
 * {@link Result#error(String, String, int)} — see {@link Result} for the
 * error-code vocabulary ({@code OFFLINE}, {@code TIMEOUT}, {@code NETWORK},
 * {@code HTTP_xxx}, Matrix {@code M_*} errcodes, {@code NOT_LOGGED_IN},
 * {@code INVALID_ARGUMENT}, {@code PARSE_ERROR}).
 *
 * <p>Secrets:
 * <ul>
 *   <li>The account password is accepted as a {@code char[]} at login, used once
 *       to build the request body, and wiped ({@link Arrays#fill(char[], char)})
 *       before {@link #login(String, char[])} returns. It is never logged and
 *       never stored. Callers should wipe their own copy too.</li>
 *   <li>The access token lives in memory only (a {@code volatile} field). It is
 *       never written to disk by this class — the app layer decides whether and
 *       how to persist a session, and must use encrypted storage if it does.</li>
 *   <li>Nothing in this class logs a password, PIN, token or TURN password.</li>
 * </ul>
 *
 * <p>E2E: this client sends and receives PLAIN (unencrypted) {@code m.room.message}
 * events only. Real end-to-end encryption (Olm/Megolm) is NOT implemented here
 * and must not be faked — it needs the Kotlin Matrix SDK or a dedicated Olm
 * implementation. See {@code MATRIX_CLIENT.md}.
 */
public final class MatrixHttpClient implements MatrixClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient apiClient;
    private final OkHttpClient syncClient;
    private final Gson gson;
    private final String baseUrl;

    private volatile String accessToken;
    private volatile String userId;
    private volatile String deviceId;
    private volatile String sinceToken;
    private volatile boolean offlineFlag;

    /** Creates a client against the configured homeserver. */
    public MatrixHttpClient() {
        this(MatrixConfig.BASE_URL);
    }

    /** Creates a client against an explicit base URL (tests / future homeserver choice). */
    public MatrixHttpClient(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.isEmpty() ? MatrixConfig.BASE_URL : baseUrl;
        this.gson = new GsonBuilder().create();
        this.apiClient = new OkHttpClient.Builder()
                .connectTimeout(MatrixConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(MatrixConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(MatrixConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.syncClient = new OkHttpClient.Builder()
                .connectTimeout(MatrixConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(MatrixConfig.SYNC_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(MatrixConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    // ------------------------------------------------------------------ state

    /** True when a login has succeeded and no logout has cleared the session. */
    @Override
    public boolean isLoggedIn() {
        return accessToken != null && userId != null;
    }

    /** Full Matrix user ID from login, e.g. {@code "@taki:tchncs.de"}; null when logged out. */
    public String getUserId() {
        return userId;
    }

    /** The logged-in @username, or null. ({@link MatrixClient} UI contract.) */
    @Override
    public String getCurrentUsername() {
        return userId;
    }

    /** Device ID from login; null when logged out. */
    public String getDeviceId() {
        return deviceId;
    }

    /** Current sync token ({@code next_batch} of the last sync); may be null before the first sync. */
    public String getSinceToken() {
        return sinceToken;
    }

    /** Restores a previously saved sync token (e.g. after app restart). */
    public void setSinceToken(String sinceToken) {
        this.sinceToken = sinceToken;
    }

    /**
     * Restores a saved session without a password. The app layer must have
     * obtained the token + user ID from encrypted storage.
     * Returns {@link Result#error} with {@code INVALID_ARGUMENT} on blank input.
     */
    public Result<Void> restoreSession(String savedUserId, String savedAccessToken) {
        if (isBlank(savedUserId) || isBlank(savedAccessToken)) {
            return Result.error("INVALID_ARGUMENT", "Saved session is incomplete.");
        }
        this.userId = savedUserId.trim();
        this.accessToken = savedAccessToken;
        return Result.ok(null);
    }

    /**
     * Manual offline flag. When set, every call fails fast with {@code OFFLINE}
     * without touching the network. The app layer sets this from its connectivity
     * listener; network failures are still mapped to {@code OFFLINE}/{@code NETWORK}
     * even when the flag is clear.
     */
    public void setOffline(boolean offline) {
        this.offlineFlag = offline;
    }

    public boolean isOfflineFlagSet() {
        return offlineFlag;
    }

    /** Access token for the app layer to persist (encrypted) if it manages sessions. In-memory only here. */
    public String getAccessTokenForSessionPersistence() {
        return accessToken;
    }

    /** Clears the in-memory session immediately, without any network call. */
    public void clearLocalSession() {
        accessToken = null;
        userId = null;
        deviceId = null;
        sinceToken = null;
    }

    // ------------------------------------------------------------------ auth

    /**
     * Logs in with username + password.
     * {@code POST /_matrix/client/v3/login}
     * Body: {@code {"type":"m.login.password","identifier":{"type":"m.id.user","user":"<name>"},
     * "password":"...","device_id":"RUNV2ANDROID","initial_device_display_name":"Run v2 Android"}}
     * Accepts a bare username ({@code taki}), {@code @taki}, or a full ID
     * ({@code @taki:tchncs.de}); the server part is stripped for the identifier.
     * The {@code password} array is wiped before this method returns.
     *
     * @return {@link Result} of {@link LoginResult} ({@code userId}, {@code deviceId}).
     *         The access token is kept in memory; read it via
     *         {@link #getAccessTokenForSessionPersistence()} if the app persists sessions.
     */
    public Result<LoginResult> login(String username, char[] password) {
        if (isBlank(username) || password == null || password.length == 0) {
            wipe(password);
            return Result.error("INVALID_ARGUMENT", "Username and password are required.");
        }
        Result<Void> offline = checkOffline();
        if (offline != null) {
            wipe(password);
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        String bareName = normalizeUsername(username);
        String passwordCopy = new String(password);
        wipe(password);
        try {
            LoginRequest body = new LoginRequest(bareName, passwordCopy);
            Request request = postJson("/login", gson.toJson(body), false);
            Result<LoginResponse> r = execute(request, apiClient, LoginResponse.class);
            if (r.isError()) {
                return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
            }
            LoginResponse resp = r.getValue();
            if (resp == null || isBlank(resp.accessToken) || isBlank(resp.userId)) {
                return Result.error("PARSE_ERROR", "Login reply was missing the session.");
            }
            this.accessToken = resp.accessToken;
            this.userId = resp.userId;
            this.deviceId = resp.deviceId;
            return Result.ok(new LoginResult(resp.userId, resp.deviceId));
        } finally {
            // The String copy cannot be wiped reliably; it is scoped to this
            // method, never logged, never stored, and eligible for GC on return.
            passwordCopy = null;
        }
    }

    /**
     * UI-contract login ({@link MatrixClient}): runs {@link #login(String, char[])}
     * on a background thread and invokes the callback on that same thread.
     * Prefer the {@code char[]} overload — this wrapper exists only so screens
     * written against the scaffold interface keep working. The password String
     * is converted to chars and the array is wiped by the core login.
     */
    @Override
    public void login(String username, String password, AuthCallback callback) {
        final char[] chars = password == null ? new char[0] : password.toCharArray();
        new Thread(() -> {
            Result<LoginResult> result = login(username, chars);
            if (result.isSuccess()) {
                callback.onSuccess(result.getValue().getUserId());
            } else {
                callback.onError(result.getErrorMessage());
            }
        }, "runv2-matrix-login").start();
    }

    /**
     * Logs out: tells the server ({@code POST /_matrix/client/v3/logout}) and
     * clears the in-memory session. The local session is cleared even if the
     * server call fails. Synchronous — call off the UI thread.
     */
    public Result<Void> logoutSync() {
        Result<Void> offline = checkOffline();
        Result<Void> serverResult = null;
        if (offline == null && accessToken != null) {
            Request request = postJson("/logout", "{}", true);
            serverResult = execute(request, apiClient, null);
        }
        clearLocalSession();
        if (serverResult != null && serverResult.isError()) {
            return serverResult;
        }
        return Result.ok(null);
    }

    /**
     * UI-contract logout ({@link MatrixClient}): clears the local session
     * immediately (safe to call on the UI thread) and notifies the server on a
     * background thread.
     */
    @Override
    public void logout() {
        // Capture the token BEFORE clearing: the background thread needs it
        // to tell the server. (Clearing first, then reading it, would make
        // the server logout silently never happen.)
        final String token = accessToken;
        final String uid = userId;
        clearLocalSession();
        if (token == null) {
            return;
        }
        new Thread(() -> {
            MatrixHttpClient notifyClient = new MatrixHttpClient(baseUrl);
            notifyClient.accessToken = token;
            notifyClient.userId = uid;
            notifyClient.logoutSync();
            notifyClient.close();
        }, "runv2-matrix-logout").start();
    }

    // ------------------------------------------------------------------ sync

    /**
     * Long-poll sync. {@code GET /_matrix/client/v3/sync?timeout=30000[&since=...]}
     * On success the client's stored {@code since} token advances to
     * {@code next_batch}; callers should persist it via {@link #getSinceToken()}.
     * Only {@code m.room.message} timeline events with a readable body are
     * returned; encrypted events are skipped (no Olm/Megolm in this layer).
     */
    public Result<SyncResult> sync() {
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before syncing.");
        }
        HttpUrl.Builder url = apiUrl("/sync")
                .addQueryParameter("timeout", String.valueOf(MatrixConfig.SYNC_TIMEOUT_MS));
        String since = sinceToken;
        if (since != null && !since.isEmpty()) {
            url.addQueryParameter("since", since);
        }
        Request request = authorized(new Request.Builder().url(url.build()).get()).build();
        Result<SyncResponse> r = execute(request, syncClient, SyncResponse.class);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        SyncResponse resp = r.getValue();
        if (resp == null) {
            return Result.error("PARSE_ERROR", "Sync reply was empty.");
        }
        if (resp.nextBatch != null) {
            sinceToken = resp.nextBatch;
        }
        List<SyncResult.RoomUpdate> updates = new ArrayList<>();
        if (resp.rooms != null && resp.rooms.join != null) {
            for (Map.Entry<String, JoinedRoomSync> e : resp.rooms.join.entrySet()) {
                String roomId = e.getKey();
                JoinedRoomSync jr = e.getValue();
                List<Message> messages = new ArrayList<>();
                if (jr != null && jr.timeline != null && jr.timeline.events != null) {
                    for (SyncEvent ev : jr.timeline.events) {
                        Message m = toMessage(roomId, ev);
                        if (m != null) {
                            messages.add(m);
                        }
                    }
                }
                updates.add(new SyncResult.RoomUpdate(roomId, messages));
            }
        }
        return Result.ok(new SyncResult(resp.nextBatch, updates));
    }

    // ------------------------------------------------------------------ rooms

    /**
     * Lists joined rooms. {@code GET /_matrix/client/v3/joined_rooms}
     *
     * <p>Honest limitation: {@code /joined_rooms} returns room IDs only. This
     * method returns one {@link Room} per ID with name/memberCount unknown
     * (null / 0) rather than inventing names; names and last messages arrive
     * via {@link #sync()}, and public-room metadata via {@link RoomDirectory}.
     * The UI must tolerate null names (use {@link Room#getDisplayName()}).
     */
    public Result<List<Room>> getJoinedRooms() {
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before listing rooms.");
        }
        Request request = authorized(new Request.Builder().url(apiUrl("/joined_rooms").build()).get()).build();
        Result<JoinedRoomsResponse> r = execute(request, apiClient, JoinedRoomsResponse.class);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        List<Room> rooms = new ArrayList<>();
        if (r.getValue() != null && r.getValue().joinedRooms != null) {
            for (String roomId : r.getValue().joinedRooms) {
                rooms.add(new Room(roomId, null, null, null, null, 0, null, 0L));
            }
        }
        return Result.ok(rooms);
    }

    /**
     * Sends a plain text message.
     * {@code PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}}
     * Body: {@code {"msgtype":"m.text","body":"..."}}
     * The transaction ID is a fresh random UUID per call (idempotent retry key).
     *
     * @return the new event ID on success.
     */
    public Result<String> sendMessage(String roomId, String body) {
        if (isBlank(roomId) || body == null) {
            return Result.error("INVALID_ARGUMENT", "Room and message text are required.");
        }
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before sending a message.");
        }
        String txnId = UUID.randomUUID().toString();
        String path = "/rooms/" + encodePath(roomId) + "/send/m.room.message/" + txnId;
        SendMessageRequest reqBody = new SendMessageRequest(body);
        Request request = putJson(path, gson.toJson(reqBody), true);
        Result<SendMessageResponse> r = execute(request, apiClient, SendMessageResponse.class);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        if (r.getValue() == null || isBlank(r.getValue().eventId)) {
            return Result.error("PARSE_ERROR", "Send reply was missing the event ID.");
        }
        return Result.ok(r.getValue().eventId);
    }

    /**
     * Creates a room. {@code POST /_matrix/client/v3/createRoom}
     * Body: {@code {"name":..., "preset":"private_chat"|"public_chat",
     * "visibility":"private"|"public", "invite":[...]}}
     *
     * @param name display name (nullable); {@code isPublic} selects the preset
     *             and visibility; {@code inviteUserIds} may be null/empty.
     * @return the new room ID on success.
     */
    public Result<String> createRoom(String name, boolean isPublic, List<String> inviteUserIds) {
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before creating a room.");
        }
        CreateRoomRequest body = new CreateRoomRequest();
        body.name = name;
        body.preset = isPublic ? "public_chat" : "private_chat";
        body.visibility = isPublic ? "public" : "private";
        body.invite = inviteUserIds == null ? Collections.emptyList() : inviteUserIds;
        Request request = postJson("/createRoom", gson.toJson(body), true);
        Result<CreateRoomResponse> r = execute(request, apiClient, CreateRoomResponse.class);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        if (r.getValue() == null || isBlank(r.getValue().roomId)) {
            return Result.error("PARSE_ERROR", "Create-room reply was missing the room ID.");
        }
        return Result.ok(r.getValue().roomId);
    }

    /**
     * Joins a room by ID ({@code !...:...}) or alias ({@code #...:...}).
     * {@code POST /_matrix/client/v3/join/{roomIdOrAlias}} with an empty JSON body.
     *
     * @return the joined room ID on success.
     */
    public Result<String> joinRoom(String roomIdOrAlias) {
        if (isBlank(roomIdOrAlias)) {
            return Result.error("INVALID_ARGUMENT", "Room ID or alias is required.");
        }
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before joining a room.");
        }
        Request request = postJson("/join/" + encodePath(roomIdOrAlias.trim()), "{}", true);
        Result<JoinRoomResponse> r = execute(request, apiClient, JoinRoomResponse.class);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        if (r.getValue() == null || isBlank(r.getValue().roomId)) {
            return Result.error("PARSE_ERROR", "Join reply was missing the room ID.");
        }
        return Result.ok(r.getValue().roomId);
    }

    /**
     * Invites a user to a room. {@code POST /_matrix/client/v3/rooms/{roomId}/invite}
     * Body: {@code {"user_id":"@user:server"}}. A bare name is expanded to a
     * full ID on this homeserver — prefer full IDs from search results.
     */
    public Result<Void> inviteUser(String roomId, String userIdToInvite) {
        if (isBlank(roomId) || isBlank(userIdToInvite)) {
            return Result.error("INVALID_ARGUMENT", "Room ID and user ID are required.");
        }
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before inviting a user.");
        }
        String target = normalizeUserId(userIdToInvite);
        InviteRequest body = new InviteRequest(target);
        String path = "/rooms/" + encodePath(roomId) + "/invite";
        Request request = postJson(path, gson.toJson(body), true);
        return execute(request, apiClient, null);
    }

    /**
     * Leaves a room. {@code POST /_matrix/client/v3/rooms/{roomId}/leave} with an empty JSON body.
     */
    public Result<Void> leaveRoom(String roomId) {
        if (isBlank(roomId)) {
            return Result.error("INVALID_ARGUMENT", "Room ID is required.");
        }
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before leaving a room.");
        }
        String path = "/rooms/" + encodePath(roomId) + "/leave";
        Request request = postJson(path, "{}", true);
        return execute(request, apiClient, null);
    }

    // ------------------------------------------------------------------ directory / search

    /**
     * Searches public rooms. {@code POST /_matrix/client/v3/publicRooms}
     * Body: {@code {"limit":20,"filter":{"generic_search_term":"..."}}}
     * Used by {@link RoomDirectory}; kept here so the directory class stays thin.
     */
    public Result<PublicRoomsPage> searchPublicRooms(String query, int limit) {
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before searching rooms.");
        }
        PublicRoomsRequest body = new PublicRoomsRequest();
        body.limit = limit > 0 ? limit : MatrixConfig.DEFAULT_SEARCH_LIMIT;
        if (query != null && !query.trim().isEmpty()) {
            body.filter = new PublicRoomsRequest.Filter(query.trim());
        }
        Request request = postJson("/publicRooms", gson.toJson(body), true);
        Result<PublicRoomsResponse> r = execute(request, apiClient, PublicRoomsResponse.class);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        List<Room> rooms = new ArrayList<>();
        if (r.getValue() != null && r.getValue().chunk != null) {
            for (PublicRoomEntry e : r.getValue().chunk) {
                rooms.add(new Room(e.roomId, e.name, e.canonicalAlias, e.topic,
                        e.avatarUrl, e.numJoinedMembers, null, 0L));
            }
        }
        int total = r.getValue() == null ? 0 : r.getValue().totalRoomCountEstimate;
        return Result.ok(new PublicRoomsPage(rooms, total));
    }

    /**
     * Resolves users by @username and/or display name.
     * {@code POST /_matrix/client/v3/user_directory/search}
     * Body: {@code {"search_term":"...","limit":20}}
     * Results are {@link Friend}s with {@link Friend.Presence#UNKNOWN} (presence
     * is fetched separately via {@link #getPresence(String)}).
     */
    public Result<List<Friend>> searchUsers(String query, int limit) {
        if (isBlank(query)) {
            return Result.error("INVALID_ARGUMENT", "Search text is required.");
        }
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before searching users.");
        }
        UserDirectoryRequest body = new UserDirectoryRequest(query.trim(),
                limit > 0 ? limit : MatrixConfig.DEFAULT_SEARCH_LIMIT);
        Request request = postJson("/user_directory/search", gson.toJson(body), true);
        Result<UserDirectoryResponse> r = execute(request, apiClient, UserDirectoryResponse.class);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        List<Friend> out = new ArrayList<>();
        if (r.getValue() != null && r.getValue().results != null) {
            for (UserDirectoryEntry e : r.getValue().results) {
                out.add(new Friend(e.userId, e.displayName, e.avatarUrl));
            }
        }
        return Result.ok(out);
    }

    /**
     * Fetches a user's profile (display name + avatar).
     * {@code GET /_matrix/client/v3/profile/{userId}}
     */
    public Result<Friend> getProfile(String userIdToFetch) {
        if (isBlank(userIdToFetch)) {
            return Result.error("INVALID_ARGUMENT", "User ID is required.");
        }
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before fetching a profile.");
        }
        String path = "/profile/" + encodePath(normalizeUserId(userIdToFetch));
        Request request = authorized(new Request.Builder().url(apiUrl(path).build()).get()).build();
        Result<ProfileResponse> r = execute(request, apiClient, ProfileResponse.class);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        ProfileResponse p = r.getValue();
        return Result.ok(new Friend(normalizeUserId(userIdToFetch),
                p == null ? null : p.displayName, p == null ? null : p.avatarUrl));
    }

    /**
     * Fetches a user's presence. {@code GET /_matrix/client/v3/presence/{userId}/status}
     *
     * @return {@link Result} of {@link PresenceStatus}
     *         ({@code presence} string, {@code statusMessage}, {@code lastActiveAgoMs}).
     */
    public Result<PresenceStatus> getPresence(String userIdToFetch) {
        if (isBlank(userIdToFetch)) {
            return Result.error("INVALID_ARGUMENT", "User ID is required.");
        }
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before fetching presence.");
        }
        String path = "/presence/" + encodePath(normalizeUserId(userIdToFetch)) + "/status";
        Request request = authorized(new Request.Builder().url(apiUrl(path).build()).get()).build();
        Result<PresenceResponse> r = execute(request, apiClient, PresenceResponse.class);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        PresenceResponse p = r.getValue();
        if (p == null) {
            return Result.error("PARSE_ERROR", "Presence reply was empty.");
        }
        return Result.ok(new PresenceStatus(p.presence, p.statusMsg, p.lastActiveAgo));
    }

    // ------------------------------------------------------------------ TURN

    /**
     * Fetches TURN credentials for the call layer.
     * {@code GET /_matrix/client/v3/voip/turnServer}
     * The returned password is a short-lived TURN credential, not the account
     * password; it is held in memory only and never logged or persisted here.
     */
    public Result<TurnCredentials> getTurnServer() {
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before fetching TURN credentials.");
        }
        Request request = authorized(new Request.Builder().url(apiUrl("/voip/turnServer").build()).get()).build();
        Result<TurnServerResponse> r = execute(request, apiClient, TurnServerResponse.class);
        if (r.isError()) {
            return Result.error(r.getErrorCode(), r.getErrorMessage(), r.getHttpStatus());
        }
        TurnServerResponse t = r.getValue();
        if (t == null || isBlank(t.username) || t.password == null || t.uris == null) {
            return Result.error("PARSE_ERROR", "TURN reply was incomplete.");
        }
        return Result.ok(new TurnCredentials(t.username, t.password, t.uris, t.ttl));
    }

    // ------------------------------------------------------------------ account data (PIN key backup)

    /**
     * Stores an opaque JSON blob as account data.
     * {@code PUT /_matrix/client/v3/user/{userId}/account_data/{type}}
     * Used by {@link PinBackup} to store the PIN-encrypted key-backup blob.
     * The blob itself is opaque to this method — it never inspects its contents.
     */
    public Result<Void> putAccountData(String type, String jsonBody) {
        if (isBlank(type) || jsonBody == null) {
            return Result.error("INVALID_ARGUMENT", "Account-data type and body are required.");
        }
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before storing account data.");
        }
        String path = "/user/" + encodePath(userId) + "/account_data/" + encodePath(type);
        Request request = putJson(path, jsonBody, true);
        return execute(request, apiClient, null);
    }

    /**
     * Fetches an opaque account-data blob.
     * {@code GET /_matrix/client/v3/user/{userId}/account_data/{type}}
     *
     * @return the raw JSON body string on success (empty object when absent is
     *         treated as success with {@code "{}"}). A missing key fails with
     *         {@code M_NOT_FOUND} (HTTP 404) so {@link PinBackup} can tell
     *         "no backup yet" apart from a real error.
     */
    public Result<String> getAccountData(String type) {
        if (isBlank(type)) {
            return Result.error("INVALID_ARGUMENT", "Account-data type is required.");
        }
        Result<Void> offline = checkOffline();
        if (offline != null) {
            return Result.error(offline.getErrorCode(), offline.getErrorMessage());
        }
        if (!isLoggedIn()) {
            return Result.error("NOT_LOGGED_IN", "Log in before reading account data.");
        }
        String path = "/user/" + encodePath(userId) + "/account_data/" + encodePath(type);
        Request request = authorized(new Request.Builder().url(apiUrl(path).build()).get()).build();
        Result<String> r = executeRaw(request, apiClient);
        if (r.isError()) {
            return r;
        }
        return Result.ok(r.getValue() == null ? "{}" : r.getValue());
    }

    /** Closes the underlying OkHttp dispatchers. Call when the client is discarded. */
    public void close() {
        apiClient.dispatcher().executorService().shutdown();
        apiClient.connectionPool().evictAll();
        syncClient.dispatcher().executorService().shutdown();
        syncClient.connectionPool().evictAll();
    }

    // ------------------------------------------------------------------ internals

    private Result<Void> checkOffline() {
        if (offlineFlag) {
            return Result.error("OFFLINE", "No connection. Check your internet and try again.");
        }
        return null;
    }

    private HttpUrl.Builder apiUrl(String path) {
        return HttpUrl.parse(baseUrl + MatrixConfig.CLIENT_API_PATH + path).newBuilder();
    }

    private Request postJson(String path, String json, boolean auth) {
        RequestBody body = RequestBody.create(json, JSON);
        Request.Builder b = new Request.Builder().url(apiUrl(path).build()).post(body);
        return auth ? authorized(b).build() : withUserAgent(b).build();
    }

    private Request putJson(String path, String json, boolean auth) {
        RequestBody body = RequestBody.create(json, JSON);
        Request.Builder b = new Request.Builder().url(apiUrl(path).build()).put(body);
        return auth ? authorized(b).build() : withUserAgent(b).build();
    }

    private Request.Builder authorized(Request.Builder b) {
        String token = accessToken;
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return withUserAgent(b);
    }

    private Request.Builder withUserAgent(Request.Builder b) {
        return b.header("User-Agent", MatrixConfig.USER_AGENT);
    }

    /** Executes a request and parses a JSON body into {@code type}; pass null type for empty-body calls. */
    private <T> Result<T> execute(Request request, OkHttpClient client, Class<T> type) {
        Result<String> raw = executeRaw(request, client);
        if (raw.isError()) {
            return Result.error(raw.getErrorCode(), raw.getErrorMessage(), raw.getHttpStatus());
        }
        if (type == null) {
            return Result.ok(null);
        }
        String body = raw.getValue();
        if (body == null || body.isEmpty()) {
            return Result.error("PARSE_ERROR", "Server reply was empty.");
        }
        try {
            return Result.ok(gson.fromJson(body, type));
        } catch (JsonSyntaxException e) {
            return Result.error("PARSE_ERROR", "Server reply could not be read.");
        }
    }

    /** Executes a request and returns the raw body string (or null for empty bodies). */
    private Result<String> executeRaw(Request request, OkHttpClient client) {
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String body = responseBody == null ? null : responseBody.string();
            if (!response.isSuccessful()) {
                return httpError(response.code(), body);
            }
            return Result.ok(body);
        } catch (SocketTimeoutException e) {
            return Result.error("TIMEOUT", "The server took too long to reply. Try again.");
        } catch (UnknownHostException | ConnectException e) {
            return Result.error("OFFLINE", "No connection. Check your internet and try again.");
        } catch (IOException e) {
            return Result.error("NETWORK", "Network error. Try again.");
        }
    }

    /** Maps an HTTP error response to a Result, preferring the Matrix errcode when present. */
    private Result<String> httpError(int httpStatus, String body) {
        if (body != null && !body.isEmpty()) {
            try {
                MatrixError err = gson.fromJson(body, MatrixError.class);
                if (err != null && err.errcode != null && !err.errcode.isEmpty()) {
                    String message = err.error != null && !err.error.isEmpty() ? err.error : "Request failed.";
                    return Result.error(err.errcode, message, httpStatus);
                }
            } catch (JsonSyntaxException ignored) {
                // Fall through to the generic HTTP error below.
            }
        }
        return Result.error("HTTP_" + httpStatus, "Request failed (HTTP " + httpStatus + ").", httpStatus);
    }

    /** Converts one sync timeline event into a Message, or null when it carries no readable body. */
    private Message toMessage(String roomId, SyncEvent ev) {
        if (ev == null || ev.eventId == null || !"m.room.message".equals(ev.type)) {
            return null; // includes m.room.encrypted — skipped, no E2E in this layer
        }
        String body = ev.content == null ? null : ev.content.body;
        if (body == null) {
            return null;
        }
        String msgType = ev.content == null ? null : ev.content.msgtype;
        return new Message(ev.eventId, roomId, ev.sender, body, ev.originServerTs, ev.type, msgType);
    }

    /** "taki" / "@taki" / "@taki:tchncs.de" -> "taki" (bare localpart for the login identifier). */
    static String normalizeUsername(String username) {
        String u = username.trim();
        if (u.startsWith("@")) {
            u = u.substring(1);
        }
        int colon = u.indexOf(':');
        if (colon >= 0) {
            u = u.substring(0, colon);
        }
        return u;
    }

    /** "taki" -> "@taki:tchncs.de" (the server name, NOT the API host); full IDs pass through unchanged. */
    static String normalizeUserId(String userId) {
        String u = userId.trim();
        if (u.startsWith("@") && u.contains(":")) {
            return u;
        }
        if (u.startsWith("@")) {
            u = u.substring(1);
        }
        return "@" + u + ":" + MatrixConfig.SERVER_NAME;
    }

    private static String encodePath(String segment) {
        // Percent-encode every char outside the unreserved set, so ":", "@",
        // "!", "#" in room/user IDs travel safely inside a path segment.
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else {
                out.append(String.format("%%%02X", (int) c));
            }
        }
        return out.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static void wipe(char[] chars) {
        if (chars != null) {
            Arrays.fill(chars, '\0');
        }
    }

    // ------------------------------------------------------------------ value types

    /** Login success: user ID + device ID. The access token stays inside the client. */
    public static final class LoginResult {
        private final String userId;
        private final String deviceId;

        public LoginResult(String userId, String deviceId) {
            this.userId = userId;
            this.deviceId = deviceId;
        }

        public String getUserId() {
            return userId;
        }

        public String getDeviceId() {
            return deviceId;
        }
    }

    /** One page of public-room search results. */
    public static final class PublicRoomsPage {
        private final List<Room> rooms;
        private final int totalRoomCountEstimate;

        public PublicRoomsPage(List<Room> rooms, int totalRoomCountEstimate) {
            this.rooms = rooms == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(rooms));
            this.totalRoomCountEstimate = totalRoomCountEstimate;
        }

        public List<Room> getRooms() {
            return rooms;
        }

        public int getTotalRoomCountEstimate() {
            return totalRoomCountEstimate;
        }
    }

    /** Presence status as returned by {@link #getPresence(String)}. */
    public static final class PresenceStatus {
        private final String presence;
        private final String statusMessage;
        private final long lastActiveAgoMs;

        public PresenceStatus(String presence, String statusMessage, long lastActiveAgoMs) {
            this.presence = presence;
            this.statusMessage = statusMessage;
            this.lastActiveAgoMs = lastActiveAgoMs;
        }

        public String getPresence() {
            return presence;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public long getLastActiveAgoMs() {
            return lastActiveAgoMs;
        }

        /** Maps the raw presence string to {@link Friend.Presence}. */
        public Friend.Presence toFriendPresence() {
            if ("online".equals(presence)) {
                return Friend.Presence.ONLINE;
            }
            if ("unavailable".equals(presence)) {
                return Friend.Presence.UNAVAILABLE;
            }
            if ("offline".equals(presence)) {
                return Friend.Presence.OFFLINE;
            }
            return Friend.Presence.UNKNOWN;
        }
    }

    // ------------------------------------------------------------------ JSON DTOs (wire shapes only)

    private static final class LoginRequest {
        @SerializedName("type") final String type = "m.login.password";
        @SerializedName("identifier") final Identifier identifier;
        @SerializedName("password") final String password;
        @SerializedName("device_id") final String deviceId = MatrixConfig.DEVICE_ID;
        @SerializedName("initial_device_display_name") final String displayName = MatrixConfig.DEVICE_DISPLAY_NAME;

        LoginRequest(String username, String password) {
            this.identifier = new Identifier(username);
            this.password = password;
        }

        private static final class Identifier {
            @SerializedName("type") final String type = "m.id.user";
            @SerializedName("user") final String user;

            Identifier(String user) {
                this.user = user;
            }
        }
    }

    private static final class LoginResponse {
        @SerializedName("user_id") String userId;
        @SerializedName("access_token") String accessToken;
        @SerializedName("device_id") String deviceId;
        @SerializedName("home_server") String homeServer;
    }

    private static final class MatrixError {
        @SerializedName("errcode") String errcode;
        @SerializedName("error") String error;
    }

    private static final class JoinedRoomsResponse {
        @SerializedName("joined_rooms") List<String> joinedRooms;
    }

    private static final class SendMessageRequest {
        @SerializedName("msgtype") final String msgtype = "m.text";
        @SerializedName("body") final String body;

        SendMessageRequest(String body) {
            this.body = body;
        }
    }

    private static final class SendMessageResponse {
        @SerializedName("event_id") String eventId;
    }

    private static final class CreateRoomRequest {
        @SerializedName("name") String name;
        @SerializedName("preset") String preset;
        @SerializedName("visibility") String visibility;
        @SerializedName("invite") List<String> invite;
        @SerializedName("room_alias_name") String roomAliasName;
    }

    private static final class CreateRoomResponse {
        @SerializedName("room_id") String roomId;
    }

    private static final class JoinRoomResponse {
        @SerializedName("room_id") String roomId;
    }

    private static final class InviteRequest {
        @SerializedName("user_id") final String userId;

        InviteRequest(String userId) {
            this.userId = userId;
        }
    }

    private static final class PublicRoomsRequest {
        @SerializedName("limit") int limit;
        @SerializedName("filter") Filter filter;

        private static final class Filter {
            @SerializedName("generic_search_term") final String genericSearchTerm;

            Filter(String term) {
                this.genericSearchTerm = term;
            }
        }
    }

    private static final class PublicRoomsResponse {
        @SerializedName("chunk") List<PublicRoomEntry> chunk;
        @SerializedName("total_room_count_estimate") int totalRoomCountEstimate;
    }

    private static final class PublicRoomEntry {
        @SerializedName("room_id") String roomId;
        @SerializedName("name") String name;
        @SerializedName("canonical_alias") String canonicalAlias;
        @SerializedName("topic") String topic;
        @SerializedName("avatar_url") String avatarUrl;
        @SerializedName("num_joined_members") int numJoinedMembers;
    }

    private static final class UserDirectoryRequest {
        @SerializedName("search_term") final String searchTerm;
        @SerializedName("limit") final int limit;

        UserDirectoryRequest(String searchTerm, int limit) {
            this.searchTerm = searchTerm;
            this.limit = limit;
        }
    }

    private static final class UserDirectoryResponse {
        @SerializedName("results") List<UserDirectoryEntry> results;
        @SerializedName("limited") boolean limited;
    }

    private static final class UserDirectoryEntry {
        @SerializedName("user_id") String userId;
        @SerializedName("display_name") String displayName;
        @SerializedName("avatar_url") String avatarUrl;
    }

    private static final class ProfileResponse {
        @SerializedName("displayname") String displayName;
        @SerializedName("avatar_url") String avatarUrl;
    }

    private static final class PresenceResponse {
        @SerializedName("presence") String presence;
        @SerializedName("status_msg") String statusMsg;
        @SerializedName("last_active_ago") long lastActiveAgo;
    }

    private static final class TurnServerResponse {
        @SerializedName("username") String username;
        @SerializedName("password") String password;
        @SerializedName("uris") List<String> uris;
        @SerializedName("ttl") int ttl;
    }

    private static final class SyncResponse {
        @SerializedName("next_batch") String nextBatch;
        @SerializedName("rooms") SyncRooms rooms;
    }

    private static final class SyncRooms {
        @SerializedName("join") Map<String, JoinedRoomSync> join;
    }

    private static final class JoinedRoomSync {
        @SerializedName("timeline") Timeline timeline;
    }

    private static final class Timeline {
        @SerializedName("events") List<SyncEvent> events;
        @SerializedName("prev_batch") String prevBatch;
    }

    private static final class SyncEvent {
        @SerializedName("event_id") String eventId;
        @SerializedName("type") String type;
        @SerializedName("sender") String sender;
        @SerializedName("origin_server_ts") long originServerTs;
        @SerializedName("content") SyncEventContent content;
    }

    private static final class SyncEventContent {
        @SerializedName("msgtype") String msgtype;
        @SerializedName("body") String body;
    }
}
