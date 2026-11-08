# Run v2 — Matrix client layer

Pure-Java Matrix Client-Server API layer (`com.takidz.runv2.matrix` +
`com.takidz.runv2.model`). OkHttp 4.12.0 + Gson 2.10.1 only — no Kotlin, no
Compose, no other new libraries. Written 2026-11-08 by the Matrix client worker.

> **E2E is NOT implemented in this layer.** Messages are sent/received as plain
> (unencrypted) `m.room.message` events. Real Olm/Megolm needs the Kotlin
> Matrix SDK or a dedicated Olm implementation. Nothing here fakes it:
> encrypted (`m.room.encrypted`) sync events are skipped, and `PinBackup`
> encrypts one opaque blob only — it is not message encryption.

## Files

| File | Lines | Status |
|---|---|---|
| `app/src/main/java/com/takidz/runv2/matrix/MatrixHttpClient.java` | 1142 | NEW — the real client |
| `app/src/main/java/com/takidz/runv2/matrix/MatrixConfig.java` | 64 | EDITED (scaffold) — URL corrected, constants added |
| `app/src/main/java/com/takidz/runv2/matrix/RoomDirectory.java` | 57 | NEW — search / discovery |
| `app/src/main/java/com/takidz/runv2/matrix/FriendStore.java` | 162 | NEW — friends list + persistence |
| `app/src/main/java/com/takidz/runv2/matrix/PinBackup.java` | 296 | NEW — PIN key-backup crypto |
| `app/src/main/java/com/takidz/runv2/matrix/InviteLinks.java` | 280 | NEW — `run://` links + room-code mapping |
| `app/src/main/java/com/takidz/runv2/matrix/SecureStore.java` | 31 | NEW — generic KV store interface |
| `app/src/main/java/com/takidz/runv2/matrix/InMemorySecureStore.java` | 36 | NEW — test/dev store only |
| `app/src/main/java/com/takidz/runv2/matrix/AndroidSecureStore.java` | 50 | NEW — adapter to the app's encrypted store |
| `app/src/main/java/com/takidz/runv2/matrix/MatrixClient.java` | 26 | UNTOUCHED (scaffold UI contract) |
| `app/src/main/java/com/takidz/runv2/matrix/MatrixClientStub.java` | 32 | UNTOUCHED (scaffold placeholder) |
| `app/src/main/java/com/takidz/runv2/model/Result.java` | 92 | NEW — typed result |
| `app/src/main/java/com/takidz/runv2/model/Message.java` | 82 | NEW |
| `app/src/main/java/com/takidz/runv2/model/SyncResult.java` | 88 | NEW |
| `app/src/main/java/com/takidz/runv2/model/TurnCredentials.java` | 60 | NEW |
| `app/src/main/java/com/takidz/runv2/model/Friend.java` | 87 | EDITED (scaffold) — additive fields only |
| `app/src/main/java/com/takidz/runv2/model/Room.java` | 87 | EDITED (scaffold) — additive fields only |
| `app/src/main/java/com/takidz/runv2/security/SecureStore.java` | — | EDITED (scaffold) — 4 generic KV methods added |
| `app/src/test/java/com/takidz/runv2/matrix/LocalSelfTest.java` | 269 | NEW — offline self-test, 66 checks |

Total new/edited layer code: ~2,850 lines (excluding the untouched scaffold files).
Nothing was pushed to GitHub. Nothing outside `run-v2-android/` was touched.

## Exact API shapes

Base URL: `https://matrix.tchncs.de` (`MatrixConfig.BASE_URL`), API prefix
`/_matrix/client/v3`. The homeserver string appears ONLY in `MatrixConfig`
and in code comments/examples — never in any UI string, layout, or
user-visible text (verified by grep: zero occurrences in `res/`, `ui/`,
`data/`, `security/`).

### `model.Result<T>` — every call returns this, never throws

- `static <T> Result<T> ok(T value)`
- `static <T> Result<T> error(String errorCode, String errorMessage)`
- `static <T> Result<T> error(String errorCode, String errorMessage, int httpStatus)`
- `boolean isSuccess()`, `boolean isError()`, `T getValue()`,
  `String getErrorMessage()`, `String getErrorCode()`, `int getHttpStatus()`

Error codes: `OFFLINE`, `TIMEOUT`, `NETWORK`, `HTTP_xxx`, Matrix `M_*`
(errcode from the server body, e.g. `M_FORBIDDEN`, `M_NOT_FOUND`),
`NOT_LOGGED_IN`, `INVALID_ARGUMENT`, `PARSE_ERROR`, `CRYPTO_ERROR`, `NOT_FOUND`.

### `matrix.MatrixHttpClient` (final class, implements scaffold `MatrixClient`)

Synchronous (call off the UI thread) unless noted. All return `Result<…>`.

| Method | Endpoint | Returns |
|---|---|---|
| `login(String username, char[] password)` | `POST /login` | `Result<LoginResult>` — `LoginResult{String userId, String deviceId}`; password array wiped before return |
| `login(String username, String password, AuthCallback cb)` | — (UI contract) | `void` — runs core login on a background thread, callback on that thread |
| `logoutSync()` | `POST /logout` | `Result<Void>` — local session cleared even on server failure |
| `logout()` | — (UI contract) | `void` — clears local session now, notifies server on a background thread |
| `restoreSession(String userId, String accessToken)` | — (local) | `Result<Void>` |
| `clearLocalSession()` | — (local) | `void` |
| `isLoggedIn()`, `getUserId()`, `getCurrentUsername()`, `getDeviceId()` | — | `boolean` / `String` |
| `getSinceToken()`, `setSinceToken(String)` | — | `String` / `void` |
| `setOffline(boolean)`, `isOfflineFlagSet()` | — | `void` / `boolean` |
| `getAccessTokenForSessionPersistence()` | — | `String` (in-memory only) |
| `sync()` | `GET /sync?timeout=30000[&since=…]` | `Result<SyncResult>` — advances stored `since` to `next_batch` |
| `getJoinedRooms()` | `GET /joined_rooms` | `Result<List<Room>>` — IDs only, names null (see caveat below) |
| `sendMessage(String roomId, String body)` | `PUT /rooms/{id}/send/m.room.message/{txnId}` (random UUID txn) | `Result<String>` event ID |
| `createRoom(String name, boolean isPublic, List<String> inviteUserIds)` | `POST /createRoom` | `Result<String>` room ID |
| `joinRoom(String roomIdOrAlias)` | `POST /join/{idOrAlias}` | `Result<String>` room ID |
| `inviteUser(String roomId, String userId)` | `POST /rooms/{id}/invite` | `Result<Void>` |
| `leaveRoom(String roomId)` | `POST /rooms/{id}/leave` | `Result<Void>` |
| `searchPublicRooms(String query, int limit)` | `POST /publicRooms` | `Result<PublicRoomsPage>` — `PublicRoomsPage{List<Room> rooms, int totalRoomCountEstimate}` |
| `searchUsers(String query, int limit)` | `POST /user_directory/search` | `Result<List<Friend>>` |
| `getProfile(String userId)` | `GET /profile/{userId}` | `Result<Friend>` |
| `getPresence(String userId)` | `GET /presence/{userId}/status` | `Result<PresenceStatus>` — `PresenceStatus{String presence, String statusMessage, long lastActiveAgoMs}` + `toFriendPresence()` |
| `getTurnServer()` | `GET /voip/turnServer` | `Result<TurnCredentials>` |
| `putAccountData(String type, String jsonBody)` | `PUT /user/{id}/account_data/{type}` | `Result<Void>` |
| `getAccountData(String type)` | `GET /user/{id}/account_data/{type}` | `Result<String>` raw JSON (`M_NOT_FOUND` passed through) |
| `close()` | — | `void` — shuts down OkHttp dispatchers |

### `model.Room` (scaffold model, additively extended)

- Original: `Room(String id, String name, String lastMessage, int memberCount, String inviteCode, boolean group)`,
  `getId()`, `getName()`, `getLastMessage()`, `getMemberCount()`, `getInviteCode()`, `isGroup()`, `getInviteLink()`.
- Added: `Room(String roomId, String name, String canonicalAlias, String topic, String avatarUrl, int memberCount, String lastMessage, long lastTimestamp)`,
  `getRoomId()` (alias of `getId()`), `getCanonicalAlias()`, `getTopic()`,
  `getAvatarUrl()`, `getLastTimestamp()`, `getDisplayName()` (name → alias → ID).
- `getInviteLink()` now returns `null` (was `"run://join/null"`) when there is no invite code.

### `model.Message` (new)

`Message(String eventId, String roomId, String sender, String body, long timestamp, String type, String msgType)`
— getters for all seven fields. `type` is normally `m.room.message`;
`msgType` e.g. `m.text`.

### `model.Friend` (scaffold model, additively extended)

- Original: `Friend(String username, String displayName, boolean online)`,
  `getUsername()`, `getDisplayName()`, `isOnline()`.
- Added: `Presence` enum (`ONLINE, OFFLINE, UNAVAILABLE, UNKNOWN`);
  `Friend(String userId, String displayName, String avatarUrl)`;
  `Friend(String userId, String displayName, String avatarUrl, Presence presence, long lastActiveAgoMs, String statusMessage)`;
  `getUserId()` (alias of `getUsername()`), `getAvatarUrl()`, `getPresence()`,
  `getLastActiveAgoMs()`, `getStatusMessage()`, `getDisplayLabel()`,
  `withPresence(Presence, long, String)`.
- `Member.fromFriend()` and all adapters/screens keep working unchanged.

### `model.TurnCredentials` (new)

`TurnCredentials(String username, String password, List<String> uris, int ttlSeconds)`
— `getUsername()`, `getPassword()` (TURN credential, in-memory only),
`getUris()`, `getTtlSeconds()`. `toString()` deliberately omits the password.

### `model.SyncResult` (new)

`SyncResult(String nextBatch, List<RoomUpdate> roomUpdates)` —
`getNextBatch()`, `getRoomUpdates()`.
`SyncResult.RoomUpdate(String roomId, List<Message> messages)` —
`getRoomId()`, `getMessages()`, `getNewLastMessage()`, `getNewLastTimestamp()`.

### `matrix.RoomDirectory` (new)

`RoomDirectory(MatrixHttpClient client)` —
`listJoinedRooms() → Result<List<Room>>`,
`searchPublicRooms(String query, int limit) → Result<List<Room>>`,
`searchUsers(String query, int limit) → Result<List<Friend>>`.

### `matrix.FriendStore` (new)

`FriendStore(SecureStore store)` — loads persisted list on construction.
`addFriend(Friend) → Result<Void>`, `removeFriend(String userId) → Result<Void>`,
`getFriend(String userId) → Friend|null`, `getAllFriends() → List<Friend>`
(sorted by display label), `size() → int`,
`refreshPresence(MatrixHttpClient) → Result<Integer>` (updated count; one
friend's failure is skipped, not fatal). Persists as JSON under
`runv2.friends.v1`. No passwords/PINs/tokens stored. Corrupt stored JSON is
discarded (starts empty), never crashes.

### `matrix.PinBackup` (new)

- Crypto, all JDK-standard, documented in the class Javadoc:
  PBKDF2-HMAC-SHA256, **210,000 iterations**, 256-bit key, 16-byte random salt;
  AES-256-GCM, 12-byte random IV, 128-bit tag. (Note: OWASP 2023 guidance for
  PBKDF2-HMAC-SHA256 is 600k iterations; 210k is the floor used here. A 6-digit
  PIN has ~20 bits of entropy — the iteration count only slows guessing, it
  cannot make the PIN strong. Server rate-limiting is the real protection.
  Losing the PIN = unrecoverable backup, by design.)
- `EncryptedBlob{int version, byte[] salt, byte[] iv, byte[] ciphertext}` —
  `toJson()`, `static fromJson(String json) → Result<EncryptedBlob>`.
  JSON shape v1: `{"v":1,"salt":"<b64>","iv":"<b64>","ct":"<b64>"}`.
- `static validatePin(char[] pin) → Result<Void>` (exactly 6 ASCII digits).
- `encrypt(byte[] plaintext, char[] pin) → Result<EncryptedBlob>`,
  `decrypt(EncryptedBlob blob, char[] pin) → Result<byte[]>`,
  `encryptText(String, char[])`, `decryptText(EncryptedBlob, char[])`.
  The PIN array is wiped before every return; wrong PIN and corrupt blob both
  fail as `CRYPTO_ERROR` ("Wrong PIN or damaged backup") — indistinguishable.
- `storeBackup(MatrixHttpClient, EncryptedBlob) → Result<Void>` and
  `fetchBackup(MatrixHttpClient) → Result<EncryptedBlob>` via account data type
  `run.v2.key_backup` (`M_NOT_FOUND` = "no backup yet").
- The PIN is NOT the account password. The sibling `security.SecureStore` PIN
  hash is a separate local-verification mechanism.

### `matrix.InviteLinks` (new)

- `static buildRoomLink(String roomCode) → String` (`run://join/<code>`)
- `static buildFriendLink(String userId) → String` (`run://friend/<pct-encoded userId>`)
- `static parse(String link) → Result<ParsedInvite>` —
  `ParsedInvite{Kind kind, String roomCode, String userId}`, `Kind{ROOM, FRIEND}`
- `static generateRoomCode() → String` (8 chars, `A-Z0-9`, `SecureRandom`)
- `InviteLinks(SecureStore store)` —
  `registerRoomCode(String roomCode, String roomId) → Result<Void>`,
  `resolveRoomCode(String roomCode) → Result<String>`,
  `getAllMappings() → Map<String,String>`.
- The code→room-ID mapping is LOCAL and EXPLICIT, persisted as JSON under
  `runv2.room_codes.v1`. A code never registered on this device cannot be
  resolved — there is no server lookup for Run codes, by design.

### `matrix.SecureStore` / `InMemorySecureStore` / `AndroidSecureStore` (new)

- Interface: `getString(String)`, `putString(String, String)`,
  `remove(String)`, `contains(String)`. Only non-secret JSON blobs travel here.
- `InMemorySecureStore`: test/dev only (plain map, lost on process death).
- `AndroidSecureStore(SecureStore delegate)`: adapts the app's real encrypted
  store (`security.SecureStore`, Keystore + EncryptedSharedPreferences).
  Wiring: `new AndroidSecureStore(new security.SecureStore(context))`.
  Four generic KV methods (`getString`/`putString`/`removeString`/`containsKey`)
  were added to `security.SecureStore` for this; its credential/PIN methods
  are untouched.

## Implemented vs stubbed vs NOT implemented

- IMPLEMENTED: everything in the table above (login/logout, sync, joined
  rooms, send, create/join/invite/leave, public-room + user-directory search,
  profile, presence, TURN, account data, FriendStore, PinBackup crypto +
  server store/fetch, invite links + local mapping, offline/timeout/HTTP
  error mapping to `Result`).
- STUBBED (deliberately, by design): nothing in this layer is a stub. The
  scaffold's `MatrixClientStub` was left untouched for the app shell to swap
  out; `MatrixHttpClient` is the real implementation.
- EXPLICITLY NOT IMPLEMENTED: Olm/Megolm E2E (no message encryption, no key
  exchange, no device verification); WebRTC / call signalling (only TURN
  credentials are fetched); media upload/download (`mxc://` resolution);
  room state beyond sync timelines (member lists, read receipts, typing);
  push notifications; SSO / token refresh.
- KNOWN HONEST LIMITATIONS:
  - `getJoinedRooms()` returns IDs only — the endpoint returns no names.
    Names/last messages arrive via `sync()`; the UI must tolerate null names
    (`Room.getDisplayName()` falls back to alias, then ID).
  - `sync()` returns only rooms with activity in that response, and only
    readable `m.room.message` events. Encrypted events are skipped.
  - `MatrixHttpClient.login(String, String, AuthCallback)` (UI contract)
    invokes the callback on a background thread — the UI must post to main.
  - The `logout()` UI-contract path notifies the server best-effort on a
    background thread; `logoutSync()` is the reliable one (off UI thread).
  - Java `String` password copies cannot be wiped (only the `char[]` can);
    the copy is method-scoped, never logged/stored.

## What was tested locally (measured, not claimed)

- `javac` (Temurin JDK 21) clean compile, exit 0, of the whole
  `model/` + `matrix/` layer plus `MatrixClientStub`, `LocalSelfTest`, and
  separately `security/SecureStore` + `AndroidSecureStore` against
  `android-36/android.jar` + security-crypto 1.1.0-alpha06 + Tink 1.7.0.
  Jars used (Maven Central, exact): OkHttp 4.12.0, okio 3.6.0 (+ okio-jvm),
  kotlin-stdlib 1.9.10 (OkHttp 4.x is Kotlin-written; our code is Java-only),
  annotations 13.0, Gson 2.10.1.
- `LocalSelfTest` (pure Java, no network, no account): **66 checks, 66 passed,
  0 failed** (one failure found and fixed during the run — see below).
  Covers: config values, `Result`, PinBackup round-trip / wrong PIN / bad PIN /
  malformed blob, invite-link build/parse/encode, room-code register/resolve/
  reload, FriendStore add/remove/sort/persist/corrupt-JSON, model constructors
  (old + new), `TurnCredentials.toString()` secrecy, offline-flag behaviour,
  validation errors, `NOT_LOGGED_IN` paths, username/user-ID normalisation,
  session restore/clear, password-array wiping.
- Cross-file API shape check: **303 call sites** grep-counted across the layer
  (`new Room/Friend/Message/TurnCredentials/SyncResult` 8, `Result.ok/error`
  129, `client.*` calls from FriendStore/RoomDirectory/PinBackup 6,
  `store.*` calls 8, model getter calls 152) — all type-checked by the
  `javac` run above, **0 mismatches**. One real bug was caught by the
  self-test instead of the compiler: `normalizeUserId()` derived the server
  name from the API host and produced `@taki:matrix.tchncs.de`; fixed by
  adding `MatrixConfig.SERVER_NAME = "tchncs.de"` (IDs use the bare server
  name, the API uses the `matrix.` host) — re-run 66/66.
- Secret hygiene verified by grep: no `Log.*` / `System.out` / stack-trace
  prints in main sources; no password/PIN/token flows into any log or
  `toString()` (TurnCredentials test asserts this).

## UNVERIFIED — not tested, do not claim as working

1. Any live network call: no Matrix account exists, and none was created;
   no login, sync, send, search, TURN or account-data call has ever run
   against the real server. Endpoint shapes follow the Matrix spec r1.11 /
   client v3 and must be confirmed with a real account (created by the user).
2. Full Gradle / Android build (`:app:assembleDebug`): NOT run. The sibling's
   build config targets AGP 9.0.0, which needs Gradle 9.x, while only
   Gradle 8.11.1/8.13 are installed here, and AGP 9.0.0 is not in the local
   cache — a full build would need downloads and a Gradle upgrade decision.
   UI code, layouts and the `R` class are therefore unverified by me.
3. Behaviour against the real tchncs.de server: whether user-directory search
   is enabled, whether `publicRooms` needs auth, TURN server availability,
   rate limits, and the exact `m.login.password` identifier acceptance.
4. `AndroidSecureStore` at runtime (EncryptedSharedPreferences on a real
   device) — compiles, never executed.
5. Interop with the sibling's in-progress UI (LoginActivity etc.): no UI code
   calls this layer yet; the swap from `MatrixClientStub` to
   `MatrixHttpClient` is still to be wired by the app shell.

## Integration notes (scaffold reconciliation)

The task brief said the directory was empty; a sibling agent was in fact
scaffolding the same project in parallel. I integrated instead of overwriting:

- `MatrixClient` already existed as a callback-based UI interface — kept as-is;
  the real client is `MatrixHttpClient implements MatrixClient`.
- `MatrixConfig.HOMESERVER_URL` was `"https://tchncs.de"` (bare website domain,
  not the API endpoint) — corrected to `"https://matrix.tchncs.de"`;
  `DEFAULT_VIDEO_QUALITY` kept (used by `AccountActivity`).
- `model.Friend` / `model.Room` were in active use by adapters and
  `PlaceholderData` — extended additively (new constructors/getters, old ones
  untouched). The old constructors/getters they rely on are all still present;
  the adapters themselves were NOT compiled (full app build is still
  UNVERIFIED, item 2 above).
- `security.SecureStore` (concrete, encrypted) already existed — kept; only 4
  generic KV methods added, and `AndroidSecureStore` adapts it to the
  layer's `matrix.SecureStore` interface.
- `app/build.gradle` already declares OkHttp 4.12.0 + Gson 2.10.1 — no change needed.
