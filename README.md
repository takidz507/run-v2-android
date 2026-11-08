# Run v2 — Android

Open-source Matrix chat app, Kotlin + Jetpack Compose.
Package `com.takidz.runv2`, version 2.0.0 (code 200),
compileSdk 34, minSdk 26, targetSdk 34, Kotlin 1.9.23.

Design: Telegram-clean single column, Warm Night (`#171310` charcoal + `#E8A33D` amber)
dark theme, Warm Paper light theme, small chat text, bottom tabs (Rooms / Calls / Account).
The homeserver is hidden in the UI — login is by username + password only.

## Features

- Login / register on a Matrix homeserver (username + password, typed in-app)
- 6-digit PIN backup of the session key (PBKDF2 120k + AES-256-GCM, stored in the account)
- Persistent rooms via `/sync`, chat, search friends by @username AND display name,
  friends list with presence, group rooms with permanent invite links (`run://join/<code>`)
- 1-to-1 video calls: real WebRTC, TURN from the server, 480p default + Data saver 240p

## Build

CI (`.github/workflows/build.yml`, JDK 17) builds debug + release and uploads the
verified APK artifact with a SHA-256 file. No auto-release.

```sh
./gradlew assembleDebug
```

## Honest status (v2.0.0)

- **Messages are sent plain by default** (`m.room.message`, `encryptWithRoomKey=false`).
  The server can read them. Full Olm/Megolm end-to-end encryption is NOT included.
- Session data on the phone is stored encrypted, and traffic uses HTTPS.
- No live test yet: no real account login, no real call, no APK tested on a device.

See `IMPLEMENTATION-NOTES.md` for the full file-by-file notes.
