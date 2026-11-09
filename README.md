# Issuegram

Android app for reporting and tracking general civic issues.

- Package: `com.svce.issuegram`
- Language: Kotlin + Jetpack Compose (Material 3)
- Min SDK: 26, target/compile SDK: 37

## Build

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Use JDK 17 or newer and an Android SDK with platform 37. Configure `sdk.dir` in your untracked `local.properties`.

## Install

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Implementation and backend handoff

Start with [Planning/AI_HANDOFF.md](Planning/AI_HANDOFF.md) for task ownership, the exact API contract, verification results and work logs. The Android and Supabase AIs should update that file when taking or completing tasks. Hosted database, Auth and storage changes belong to the Supabase AI.

The [citizen experience audit](Planning/CIVIC_UX_AUDIT.md) records report edit/delete, shared details, evidence viewing, sharing, search/filtering, moderation flags, profile corrections and draft handling, including backend tasks B8–B10 and device acceptance checks.

The app uses anonymous Auth behind a welcome screen with a random handle, optional name and optional photo. A handle cannot restore an identity. Sessions are encrypted with Android Keystore; clearing app data or uninstalling loses the anonymous identity. Backup and device transfer are excluded because the encryption key cannot move with the session.

The client requires the backend contract in the handoff. A successful Android build does not verify hosted permissions or make an incompatible backend ready.

## Architecture

- Compose screens consume lifecycle-aware ViewModel state. Runtime lists start empty, with loading, retry and error states.
- Sample feed previews and photos live in `app/src/debug/` and are excluded from release builds.
- `IssuegramRepository` coordinates authenticated API calls, user-scoped caches and a persistent SQLite outbox. The repository holds application context only.
- `AuthManager` serializes anonymous signup and token refresh, preserves the user's UUID and keeps the saved identity when offline. Profile lookup uses that UUID.
- `UploadWorker` drains queued reports, photos and messages when connected, with exponential retry. Each request keeps a UUID across retries; the server must enforce its uniqueness. Failed items remain visible for retry or explicit removal.
- Report photos are validated, oriented, resized and copied into private durable storage before queueing. Coordinates come from a fresh device lookup or an explicit map choice. Draft details and photos survive leaving the screen.
- Support messages belong to a per-user conversation. UI distinguishes queued, failed and server-confirmed messages. Realtime connections stop in the background, send heartbeats, refresh tokens and reconnect; foreground polling provides a fallback.

## Verification

`testDebugUnitTest` runs session, HTTP contract and report-validation tests against a local mock server. No hosted writes are performed.

With a dedicated emulator or test phone connected:

```sh
./gradlew connectedDebugAndroidTest
```

The device tests verify outbox persistence, owner separation, duplicate protection, report action permissions/confirmation and stale-editor review. Database tests clear the test install's local database. See the handoff for the additional camera, permissions, process-restart and two-user server checks required before release.
