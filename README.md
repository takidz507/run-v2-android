# Run v2 — Android

Open-source Matrix chat app, Kotlin + Jetpack Compose.
Package `com.takidz.runv2`, version 2.1.0 (code 210),
compileSdk 34, minSdk 26, targetSdk 34, Kotlin 1.9.23.

Design: Telegram-clean single column, Warm Night (`#171310` charcoal + `#E8A33D` amber)
dark theme, Warm Paper light theme, small chat text, bottom tabs (Rooms / Calls / Account).
The homeserver is hidden in the UI — login is by username + password only.

Homeserver: **unredacted.org** (`https://matrix.unredacted.org`).
Registration is the classic way: username + password only, no MAS, no moderator approval.

## Features

- Login / register on a Matrix homeserver (username + password, typed in-app)
- **Real end-to-end encryption (Olm/Megolm)** via the official `org.matrix.android:olm-sdk`
  (libolm): encrypted rooms send `m.room.encrypted` (Megolm), device keys + one-time keys
  are uploaded, devices are verified by Ed25519 fingerprint, and room keys are shared over
  Olm. Interoperable with Element by design. Encryption is ON by default in new rooms.
- 6-digit PIN backup of the real Megolm sessions (PBKDF2 120k + AES-256-GCM wraps the
  exported session vault, stored in the account)
- Persistent rooms via `/sync`, chat, search friends by @username AND display name,
  friends list with presence, group rooms with permanent invite links (`run://join/<code>`)
- 1-to-1 video calls: real WebRTC, TURN from the server, 480p default + Data saver 240p.
  The app checks `/voip/turnServer` after login and says honestly if the server has no TURN.

## Build

CI (`.github/workflows/build.yml`, JDK 17) builds debug + release and uploads the
verified debug APK artifact with a SHA-256 file. No auto-release.

```sh
./gradlew assembleDebug
```

## Honest status (v2.1.0)

- Encryption is real in code (Olm/Megolm, Element-interoperable by spec), but **no live
  test yet**: no cross-client test with Element has been run, so interop is unproven
  until tested on a device.
- No interactive SAS emoji verification yet (manual fingerprint check + verify button only).
- Megolm sessions rotate every 200 messages, but not yet automatically on member leave.
- Session data on the phone is stored encrypted, and traffic uses HTTPS.

See `IMPLEMENTATION-NOTES.md` for the full file-by-file notes.
