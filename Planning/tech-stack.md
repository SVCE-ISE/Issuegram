# Tech Stack for an Instagram Clone (Android)

Recommendation aligned with the existing project: Kotlin + Jetpack Compose, AGP 9.5, minSdk 26.

## Client (Android)

| Concern | Choice |
|---|---|
| Language / UI | Kotlin 2.x + Jetpack Compose + Material 3 |
| Architecture | MVVM + unidirectional data flow (StateFlow), multi-module (`:core:ui`, `:core:data`, `:feature:feed`, ...) |
| DI | Hilt |
| Navigation | Navigation Compose (or Voyager for type-safe routes) |
| Networking | Retrofit + OkHttp + kotlinx.serialization (or Ktor client if backend is Ktor) |
| Local cache | Room (feed, drafts, offline profile) |
| Prefs / session | DataStore (proto) |
| Pagination | Paging 3 (feed, explore, comments) |
| Images | Coil 3 (Compose-native, async, memory/disk cache) |
| Media playback (Reels) | Media3 / ExoPlayer with `PlayerView` or Compose interop |
| Camera / stories | CameraX + Media3 Transformer for trimming/compression |
| Background upload | WorkManager (resumable uploads, retry with backoff) |
| Push | Firebase Cloud Messaging |
| Auth | Credentials Manager / Google Sign-In + Firebase Auth or custom OAuth |
| Realtime (chat, likes) | WebSocket (OkHttp) or Firebase Realtime DB / Supabase Realtime |
| Blur / effects | Modifier `blur()` (API 31+, via `RenderEffect`) |
| Testing | JUnit5, Turbine, MockK, Compose UI tests, Roborazzi for screenshot tests |

## Backend — pick one

1. **Supabase (recommended for a college project)** — Postgres + Storage (CDN for images/video) + Realtime + Auth + Row Level Security. Minimal server code, free-tier friendly.
2. **Firebase** — Auth, Firestore, Storage, FCM, Functions. Easiest to start, but Firestore queries for feed ranking get awkward and costs add up.
3. **Custom API** — Ktor (Kotlin, shares models with the app) or Node/NestJS. Necessary for real feed ranking, media transcoding, or a gradable backend deliverable.

### Media note

Supabase Storage has no transcoding — upload originals and compress on-device with `Media3 Transformer`. For HLS / adaptive video (Reels at scale), use a custom backend + S3 + a transcoder (Mux or ffmpeg workers).

## Things that bite people on Instagram clones

- **Feed pagination + stable ordering** — cursor-based (not offset) pagination; offsets shift when new posts arrive.
- **Image memory** — Coil's default cache sizing is usually fine; never decode full-resolution images into a Compose `Image`.
- **Video is the hard part** — compression, thumbnails (`MediaMetadataRetriever`), and upload states (`UPLOADING -> PROCESSING -> READY`) need explicit UI states.
- **Optimistic UI** for likes/follows, with rollback on failure.
- **R8 / ProGuard** — kotlinx.serialization and Retrofit need keep rules; test release builds early.

## Dependencies to add to the current project

`app/build.gradle.kts` currently only has Compose + lifecycle. To get going, add:

- Hilt (with KSP)
- Retrofit / OkHttp / kotlinx.serialization
- Coil
- Room (with KSP)
- Paging 3
- Navigation Compose
- DataStore
- Media3 (ExoPlayer + Transformer)
- CameraX

Plus the KSP and Hilt Gradle plugins.
