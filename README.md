# Run v2 — Android scaffold

Open-source Matrix chat app, Java + XML Views only (no Kotlin, no Compose).
Package `com.takidz.runv2`, applicationId `com.takidz.runv2`, version 1.0.0 (code 1),
compileSdk 36, minSdk 24, targetSdk 34, JDK 21, Gradle 9.3.1, single app module.

Design: Telegram-clean single column, Warm Night (`#171310` charcoal + `#E8A33D` amber)
dark theme, Warm Paper light theme, small chat text, bottom tabs (Rooms / Calls / Account).

## Build

```sh
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
CI (`.github/workflows/android-build.yml`) builds it with JDK 21 and uploads the
artifact named `run-v2-android-debug-apk`. Nothing has been pushed to GitHub.

**Build status on this VM: NOT BUILT.** See "What does not compile / run" below.
The failure is environmental (Gradle daemon IPC broken in this sandbox), not project code.

## Screens

| Screen | File | Notes |
|---|---|---|
| Login | `ui/LoginActivity` + `layout/activity_login.xml` | Username (auto-prefixes `@`), password typed in-app; never logged/stored in plain text. Auto-login after first login. |
| PIN | `ui/PinActivity` + `layout/activity_pin.xml` | 6-digit pad, dots fill, auto-advance; create + confirm, and restore mode for a new phone. PIN ≠ password; only a SHA-256 hash is stored. |
| Rooms (main) | `ui/RoomsActivity` + `layout/activity_rooms.xml` | Room cards (name, last message, member count), search bar, bottom tabs, "+" creates a group room. |
| Search | `ui/SearchActivity` + `layout/activity_search.xml` | Friends / Rooms tabs. Friends by @username AND display name (display name big, @username small, Add button). Rooms by name AND code. Friends list has online indicator. |
| Create room | `ui/CreateRoomActivity` + `layout/activity_create_room.xml` | Name, optional description, add members by search. |
| Room detail | `ui/RoomDetailActivity` + `layout/activity_room_detail.xml` | Invite link `run://join/<code>`, permanent code `RUN-xxxx`, Copy link / Copy code / Share, member list. Handles incoming `run://join/<code>` deep links. |
| Chat | `ui/ChatActivity` + `layout/activity_chat.xml` | Full-screen chat, message list, send box, video-call button in header. |
| Call | `ui/CallActivity` + `layout/activity_call.xml` | Timer, mute / camera / speaker / end. **UI only — see "Stubbed".** |
| Account | `ui/AccountActivity` + `layout/activity_account.xml` | Only @username + "Run account"; encryption & privacy row, log-in-from-another-phone row, logout. Call settings: 480p default, data saver, audio-only. |

## Data layer

- `data/PlaceholderData` — local sample rooms/friends/messages; search by
  username/display name and by room name/code. Screens use this until the Matrix
  client worker wires the real client. **No network calls are invented here.**
- `matrix/MatrixClient` — the UI contract (`login(username, password, callback)`,
  `logout()`, `isLoggedIn()`, `getCurrentUsername()`). Implemented for real by the
  Matrix worker's `MatrixHttpClient`; `MatrixClientStub` is an explicit TODO stub.
- `matrix/MatrixConfig` — internal constants only (never shown in UI). Base URL is
  `https://matrix.tchncs.de`.
- `security/SecureStore` — EncryptedSharedPreferences (Android Keystore) for the
  account password and the PIN hash; auto-login; plus generic string-blob
  get/put/remove/contains used by `matrix/AndroidSecureStore` for the friends
  list and room-code map (non-secret JSON only).

**UI text never shows the homeserver domain, "Matrix", "server" or "protocol".**
Verified by grep over `res/` (clean). The words appear only in code comments and
in internal constants, never in a user-visible string.

## Launcher icon

Converted from the approved WebP
`files/icons/media-generation-run-icon-v2-best-0-89e7ceb0-8464-4452-9802-6033d4a1b711.webp`
(1600×1600, amber R on warm-night charcoal) — no other icon was used.
`mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` (48/72/96/144/192 px),
matching `ic_launcher_round.png`, plus adaptive-icon foregrounds
(108/162/216/324/432 px) with `mipmap-anydpi-v26` XML on a `#171310` background.

## What is stubbed / TODO

- `CallActivity` — UI only. No WebRTC PeerConnection, ICE, or signalling.
  The call layer must add remote/local video views, a PeerConnection using
  `model/TurnCredentials`, and real mute/camera/speaker control. Must not be
  presented as a working call until that wiring lands.
- `LoginActivity` — saves credentials locally after validation; real login via
  `MatrixClient.login()` is a TODO for the Matrix client worker.
- `ChatActivity` send, `SearchActivity` Add-friend, `CreateRoomActivity` create —
  local placeholder behaviour; real calls are TODOs for the Matrix client worker.
- `MatrixClientStub` — every method is an explicit TODO / "not implemented yet".

## Cross-file API shape check (verified 2026-11-08)

The Matrix client worker shares this tree and its files call into the models
I own. The following shapes were checked against every call site (its
`MatrixHttpClient`, `FriendStore`, `RoomDirectory`, `InviteLinks`,
`AndroidSecureStore`, and `app/src/test/.../LocalSelfTest.java`):

| Type | Members | Call sites verified |
|---|---|---|
| `model/Room` | 8-arg Matrix ctor `(roomId, name, canonicalAlias, topic, avatarUrl, numJoinedMembers, lastMessage, lastTimestamp)`; 6-arg legacy ctor `(id, name, lastMessage, memberCount, inviteCode, group)`; `getId()`, `getRoomId()`, `getName()`, `getDisplayName()` (name → alias → roomId), `getCanonicalAlias()`, `getTopic()`, `getAvatarUrl()`, `getNumJoinedMembers()`, `getMemberCount()`, `getLastMessage()`, `getLastTimestamp()`, `getInviteCode()`, `getInviteLink()` (null when no code), `isGroup()` | `MatrixHttpClient` (both ctors, `getDisplayName`), `RoomDirectory`, `LocalSelfTest`, `RoomAdapter`, `ChatActivity`, `RoomDetailActivity`, `PlaceholderData` |
| `model/Friend` | `(userId, displayName, avatarUrl)`, `(userId, displayName, avatarUrl, Presence, lastActiveAgoMs, statusMessage)`, legacy `(userId, displayName, boolean online)`; nested `Presence { UNKNOWN, ONLINE, OFFLINE, UNAVAILABLE }`; `getUserId()`, `getUsername()`, `getDisplayName()`, `getAvatarUrl()`, `getPresence()`, `getLastActiveAgoMs()`, `getStatusMessage()`, `getDisplayLabel()`, `isOnline()`, `withPresence(...)` | `MatrixHttpClient`, `FriendStore` (incl. Gson persistence round-trip), `LocalSelfTest`, `FriendAdapter`, `MemberAdapter`, `PlaceholderData` |
| `model/Member` | `(username, displayName, online)`; getters; `fromFriend(Friend)` | `RoomDetailActivity`, `MemberAdapter` |
| `model/ChatMessage` | `(id, sender, body, timestamp, own)`; getters | `ChatActivity`, `MessageAdapter`, `PlaceholderData` |
| `matrix/MatrixConfig` | `BASE_URL`, `HOMESERVER_URL`, `SERVER_NAME`, `CLIENT_API_PATH`, `USER_AGENT`, `DEVICE_ID`, `DEVICE_DISPLAY_NAME`, `CONNECT_TIMEOUT_SECONDS`, `READ_TIMEOUT_SECONDS`, `SYNC_READ_TIMEOUT_SECONDS`, `SYNC_TIMEOUT_MS`, `DEFAULT_SEARCH_LIMIT`, `KEY_BACKUP_ACCOUNT_DATA_TYPE`, `DEFAULT_VIDEO_QUALITY` | `MatrixHttpClient`, `PinBackup`, `LocalSelfTest` (pins `BASE_URL` + `CLIENT_API_PATH`), `AccountActivity` |
| `matrix/MatrixClient` | `login(String, String, AuthCallback)`, `logout()`, `isLoggedIn()`, `getCurrentUsername()`; nested `AuthCallback` | `MatrixHttpClient implements` (all four are `@Override`), `MatrixClientStub`, `LoginActivity` (comment) |
| `security/SecureStore` | `saveCredentials`, `getUsername`, `getPassword`, `hasCredentials`, `setPin`, `hasPin`, `verifyPin`, `clearAll`, `hashPin`, plus generic `getString`, `putString`, `removeString`, `containsKey` | `LoginActivity`, `PinActivity`, `AccountActivity`, `matrix/AndroidSecureStore` |

Two mismatches found during the check and fixed in this scaffold (not in the
Matrix worker's files):
1. My first `Room`/`Friend` had only the 6-arg / boolean shapes; the Matrix
   worker's client needs the 8-arg Room ctor, `getDisplayName()`, the
   3-arg/6-arg Friend ctors, `getUserId()`, `getDisplayLabel()`,
   `withPresence()`, and `Friend.Presence`. Both shapes now coexist.
2. My first `MatrixConfig` lacked 12 constants the client uses (`BASE_URL`,
   `SERVER_NAME`, `CLIENT_API_PATH`, timeouts, `DEVICE_ID`, etc.). Added.
3. The Matrix worker concurrently appended a duplicate copy of the four generic
   blob methods to `security/SecureStore.java` (same methods I had just added);
   I removed my copy and kept theirs — one copy remains, no duplicates.
   (javac had reported `method getString(String) is already defined` × 4.)

UI code was switched from `Room.getName()` to `Room.getDisplayName()` because
Matrix room names may be null (per `MatrixHttpClient` docs).

## What compiles / what does not

**Verified by running code (2026-11-08, Temurin JDK 21):**

- `javac` over the model + matrix layers (excluding Android-only
  `AndroidSecureStore`) against real gson 2.10.1 / okhttp 4.12.0 jars:
  **compiles, exit 0.**
- The Matrix layer's own `LocalSelfTest` (66 checks: config, Result,
  PinBackup, InviteLinks, FriendStore, models, client offline/validation):
  **66 passed, 0 failed.**
- `javac` over ALL sources: 100 errors, **all** of the form `package android... /
  androidx... / com.google.gson / okhttp3 does not exist` or `cannot find
  symbol` — expected, because the Android SDK and dependency jars are not on a
  plain `javac` classpath. **Zero parse/syntax errors, zero duplicate-method
  errors.**
- All 21 XML resource files parse as well-formed XML.

**NOT verified — no Gradle build has succeeded on this VM:**

```
$ ./gradlew assembleDebug
FAILURE: Build failed with an exception.
* What went wrong:
Could not dispatch a message to the daemon.
```

and, on other attempts:

```
* What went wrong:
Could not receive a message from the daemon.
Caused by: org.gradle.internal.remote.internal.MessageIOException:
  Could not read message from '/127.0.0.1:40651'.
Caused by: java.lang.IllegalArgumentException: Unexpected type tag 109 found.
```

Tried and all failed at the same daemon-IPC step: Gradle 8.11.1, 8.13
(with/without `--no-daemon`, fresh `GRADLE_USER_HOME`), Gradle 9.3.1 direct
binary, `./gradlew` (9.3.1), and even `gradle help` in an empty directory.
The daemon process itself starts and listens (e.g. pid 7466 on
`127.0.0.1:38637`, "Daemon server started") but the client connection is
reset before any build work runs. A plain Python loopback TCP test on
127.0.0.1 works, so this is specific to the Gradle daemon channel in this
sandbox. Per the no-retry-loop rule, no further local Gradle attempts were
made. **The next step is to run the same `./gradlew assembleDebug` in GitHub
Actions (workflow provided) or on another machine.**

Additional local quirk: `./gradlew` initially failed to download the
distribution because the sandbox egress proxy returns `504 Gateway Time-out`
for `services.gradle.org` → GitHub release redirects; the distribution was
instead fetched from `downloads.gradle.org` (137 MB, valid zip) for the direct
binary attempts. The wrapper properties keep the standard
`services.gradle.org` URL, which works in CI.

## UNVERIFIED list (do not claim these)

- No `assembleDebug` has ever succeeded anywhere for this project; **no APK
  exists, no APK hash exists, no install test exists.**
- AGP 9.0.0 + Gradle 9.3.1 compatibility is assumed from version requirements,
  not proven by a build.
- The Gradle wrapper files (`gradlew`, `gradle-wrapper.jar`,
  `gradle-wrapper.properties`) were assembled by hand (wrapper jar extracted
  from the 9.3.1 distribution, scripts fetched from the Gradle repo at
  v9.3.1); they have not executed a successful download+build yet.
- `security/SecureStore` (EncryptedSharedPreferences path) has never run on a
  device or emulator — only its API shape was checked against
  `AndroidSecureStore`.
- Layouts have never been rendered; no on-device UI check.
- `MatrixConfig` values not pinned by the test (`DEVICE_ID = "RUNV2ANDROID"`,
  `DEVICE_DISPLAY_NAME`, timeouts, `KEY_BACKUP_ACCOUNT_DATA_TYPE =
  "run.v2.key_backup"`) are scaffold choices owned by the Matrix client worker.
- `LocalSelfTest` covers the model/matrix layers only; it does not cover the
  UI, the placeholder data, or `SecureStore`.
