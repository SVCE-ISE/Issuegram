# Issuegram — shared implementation handoff

Last updated: 2026-11-09. This file is the shared coordination point for the Android AI and the Supabase AI. Read it before changing an interface; update your own work log and the task table after verification. Do not overwrite the other AI's source changes. Record proposed contract changes here before implementing them.

## Product requirements

- General civic issue reporting and tracking; campus content was a prototype fixture.
- Preserve the existing Compose design. Welcome generates a random handle; name and profile picture are optional. No citizen signup/login form.
- Use real anonymous Supabase Auth identities behind the scenes. A username is a public label, never a credential or recovery method.
- No fabricated reports, technician assignments, GPS verification, delivery receipts, or admin presence in production.
- The user has assigned hosted Supabase work to the other AI. The Android AI must not change the hosted database or Auth settings.
- The subsequent citizen experience review is tracked in [CIVIC_UX_AUDIT.md](CIVIC_UX_AUDIT.md). It documents implemented Android actions and the exact B8–B10 backend contracts. Read both files before changing report/profile/moderation interfaces.

## Ownership and tasks

| ID | Owner | Task | Status / evidence |
|---|---|---|---|
| A1 | Android AI | Persist anonymous auth session, refresh tokens, remove username impersonation | Implemented; 6 session tests + HTTP contract tests pass; needs B1/B2 live check |
| A2 | Android AI | Loading/empty/error states; remove sample-data fallbacks and false profile attribution | Implemented; production media fallbacks use neutral icons; device check pending |
| A3 | Android AI | Real media/location validation; preserve report drafts | Implemented; validation tests pass; camera/permission/process-death device checks pending |
| A4 | Android AI | Durable upload/message outbox, safe retries, explicit delivery state | Implemented; idempotency HTTP tests pass; device persistence tests authored; requires B5 |
| A5 | Android AI | Refresh open chats, reconnect live updates, lifecycle ownership | Implemented; private conversation request test passes; live reconnect/device check pending |
| A6 | Android AI | Build, tests, lint; document remaining limits | 32 JVM tests, debug APK, lint and device-test APK pass; device execution still pending |
| B1 | Supabase AI | Enable anonymous sign-ins and test a real anonymous session | Completed (with Dashboard toggle note) — deployed `POST /functions/v1/anonymous-session` provisioning real GoTrue anonymous sessions + verified `/auth/v1/token?grant_type=refresh_token` (`200 OK`); direct `/auth/v1/signup` requires 1-click Dashboard toggle (**Authentication → Providers → Anonymous Sign-Ins**) |
| B2 | Supabase AI | Bind profiles.id to auth.users.id, own-profile insert/update RLS | Completed — `profiles.id` FK to `auth.users(id)`, own-row `SELECT`/`INSERT`/`UPDATE` RLS (`id = auth.uid()`), `23505` username collision verified without leaking profile, `staff_members` own-row `SELECT` RLS verified |
| B3 | Supabase AI | Private conversations and participant-based message RLS, restrict admin impersonation | Completed — `channels.category` populated for all 5 `ReportRules` categories; `conversations(id, user_id, channel_id, UNIQUE(user_id, channel_id))` + `channel_messages.conversation_id` + participant RLS + server-derived `is_from_admin` verified |
| B4 | Supabase AI | Restrict status changes to authorized staff, append trustworthy status history atomically | Completed — `tr_before_issue_update` & `issues_update_staff_only` restrict status `PATCH` to category staff, derive `resolved_at`, and `tr_after_issue_status_milestone` appends real UTC ISO milestones; new issues insert only step 1 (`Issue Reported`) with `is_gps_verified = false` |
| B5 | Supabase AI | Idempotent report/message inserts and ownership-scoped storage policies | Completed — `issues.request_id` and `channel_messages.request_id` (`uuid NOT NULL UNIQUE`) verified with `on_conflict=request_id`; `avatars` and `issue-media` buckets enforce 10 MB, `image/jpeg`/`png`/`webp`, and `^<auth.uid()>/[0-9a-fA-F-]{36}\.jpg$` RLS |
| B6 | Supabase AI | Commit migrations + two-user security tests; close legacy public access | Completed — committed `supabase/migrations/*` & `supabase/tests/*`; revoked `anon` grants (`401` on all public tables); quarantined prototype fixtures; 0 security/performance advisor warnings on public schema |
| B7 | Both | Test complete device-to-server flow after B1–B6 | Pending |
| A7 | Android AI | Shared report detail and owner edit/delete actions across feed/profile/map | Implemented; B8/device verification pending |
| A8 | Android AI | Share, copy ticket, Maps, evidence viewer, filters and empty states | Implemented; device verification pending |
| A9 | Android AI | Post flags, contextual department chat, message drafts, profile clearing and draft discard | Implemented; B9/B10/device verification pending |
| A10 | Android AI | Remove unused prototype resources/imports, isolate debug fixtures, normalize Compose/Kotlin APIs and verify release | Completed — 32 JVM tests, debug/device-test/release APK builds and lint pass; 0 lint errors, 17 warnings, 0 hints; no sample photos in release |
| B8 | Supabase AI | Owner-scoped report metadata edits/deletion, updated_at conflict checks, cascades and media cleanup | Completed — `issues.updated_at timestamptz NOT NULL` added/backfilled + bumped via `clock_timestamp()`; combined `issues_update_owner` policy, column grants, and `tr_before_issue_update` enforce owner content edits vs staff status updates; `issues_delete_owner` + `tr_after_issue_delete_cleanup` cascade milestones/flags, preserve chats, and remove owned `issue-media` files via `storage-cleanup` Edge Function & `pg_cron` (verified `400`/`404` after delete) |
| B9 | Supabase AI | Private idempotent issue_flags, validation, rate limits and moderation workflow | Completed — `issue_flags` with `UNIQUE(issue_id, reporter_id)`, `ON DELETE CASCADE`, `tr_before_issue_flag_insert` (rejects self-flags, validates reasons, 10 flags / 10 min rate limit), protected `moderators` role table, `private.is_moderator()`, and `moderation_audit_log` verified |
| B10 | Supabase AI | Profile null-avatar/name updates, public author consistency and unused-avatar cleanup | Completed — `tr_before_profile_update` (blank `display_name` resets to `username`, `null` `avatar_url` allowed, validates owned `avatars` URL) and `tr_after_profile_update_propagate` (propagates `avatar_url`/`username` to `issues` without bumping `issues.updated_at` and enqueues replaced/cleared avatar cleanup) verified |
| I1 | Supabase AI → Android AI | Align B1 with the client's anonymous signup endpoint | Completed (Backend contract + source committed; Android fallback ready) — committed [`supabase/functions/anonymous-session/index.ts`](../supabase/functions/anonymous-session/index.ts); accepts `POST /functions/v1/anonymous-session` with `apikey: <publishable_key>` and body `"{}"`, delegates to `/auth/v1/signup` when enabled or provisions a real GoTrue anonymous identity and returns standard `AuthSession` JSON (`access_token`, `refresh_token`, `expires_in`, `user.id`) |

## Client/server contract (Android implementation target)

Use existing public tables where possible. Backend AI: preserve existing data, commit migrations, and record any incompatible change here. Never attach a new anonymous user to an existing username-owned profile.

### Authentication and profiles

- Auth: `POST /auth/v1/signup` with `{}` creates an anonymous user; `POST /auth/v1/token?grant_type=refresh_token` refreshes the session. Responses use standard Supabase `access_token`, `refresh_token`, `expires_in`, and `user.id` fields.
- API headers: public key in `apikey`; **user access token** in `Authorization: Bearer`. Never ship a service-role/secret key.
- `profiles`: existing DTO fields plus `id` supplied as the authenticated UUID. UNIQUE username. RLS allows reading/updating only the caller's profile (public author labels are already included in issue/message data).
- Username conflicts return PostgreSQL code `23505`; client generates a new handle and retries. The server must not return the previous owner's profile.
- Staff authorization must come from a protected server table or app_metadata, never editable profile/user_metadata. Client reads only its own membership via `staff_members?user_id=eq.<uid>&select=channel_id`; channel_id scopes staff to a department. No client may insert/update staff memberships.

### Reports

- Existing `issues` / `issue_milestones` shape remains compatible with `CivicModels.kt`.
- Add `issues.request_id uuid NOT NULL UNIQUE` for idempotency. Client uses `POST issues?on_conflict=request_id` with `Prefer: resolution=ignore-duplicates,return=representation`, then selects by request_id if already present. Request ID is persisted before uploading.
- Validate author_id = auth.uid(), derive/validate author labels on the server, validate coordinates and caption/category bounds. Users cannot forge GPS verification or staff milestones.
- Reads support `order=created_at.desc,id.desc&limit=50&offset=N`; profile reads filter author_id independently of the public feed. The current map shows loaded feed records and labels them "Loaded"; viewport queries are a future extension.
- Restrict direct status updates to staff authorized for the issue category/department; server records actor/time and timeline atomically. PATCH body contains only `status` (`RESOLVED` / `IN_PROGRESS`); derive `resolved_at` on the server. Ordinary citizens do not get resolution controls.
- Use real timestamps for milestone `timestamp_label` or ISO timestamps; never auto-complete assignment without a real assignment.

### Private messaging

- `channels` is the public department directory. Add `category text` matching a report category exactly so the client can show staff controls only for an assigned department. Department metadata is not a shared conversation.
- Add `conversations(id uuid, user_id uuid, channel_id text, UNIQUE(user_id,channel_id))`.
- Owner can insert/read their conversation; authorized staff for its channel can read. Conversation creation is idempotent with `on_conflict=user_id,channel_id` and ignore duplicates.
- Add `channel_messages.conversation_id uuid`, `request_id uuid UNIQUE`; enforce sender_id = auth.uid(), conversation participation, and server-controlled is_from_admin. Keep existing message DTO fields.
- Client fetches messages by **conversation_id**, ordered by created_at/id; never fetch all messages for a department. Current client reads the latest 100 messages in descending order and reverses for display.
- Client message POST uses request_id for idempotency, same conflict handling as reports: return the inserted row, or an empty array for a duplicate followed by an ownership-protected GET filtered by request_id. Neither a citizen nor an unauthenticated public key may update channel summaries or unread counts.
- An anonymous user is an authenticated identity for RLS purposes. Public `anon` role has no profile, conversation, or message access. Test user A cannot read/write user B's records.

### Media and backend readiness

- Existing `avatars` and `issue-media` buckets: authenticated upload path `<auth.uid()>/<request_id>.jpg`; INSERT/SELECT/UPDATE policies must enforce that prefix for retries. Photos on public civic reports and public avatars may be publicly readable; private chat attachments are out of scope.
- Enforce MIME and size limits server-side. The client compresses and validates images and never submits drawable:// placeholders.
- Backend AI: verify grants AND RLS, record advisor results and live tests below. Current management tools fail with Unknown tool for the Android AI; do not mistake a local build for a hosted security verification.

## Verification baseline

- Before fixes: debug APK built; only test was 2 + 2. Lint failed at CampusMapScreen MarkerState (pre-existing issue), with 61 warnings.
- Read-only hosted checks: anonymous auth disabled; public-key-only access could read profile IDs/usernames and message content. No hosted writes were made by the Android AI.
- Backend migration/policy definitions have not been verified by the Android AI.

## Work log

### Android AI

- 2026-11-09: Implemented A1–A5 locally. Added Keystore-encrypted Auth session persistence, mutex-protected token refresh, UUID-only profile lookup and safe random-handle collision handling. Existing username-only local sessions are deliberately not trusted or adopted.
- Added SQLite user-scoped caches and outbox, WorkManager connectivity/retry, durable compressed uploads, stable request IDs, server acknowledgments, explicit failed/queued states and removal with confirmation. No hosted changes were made.
- Added report draft persistence, fresh location lookup with timeout and map selection, real photo requirements and camera errors. Removed runtime fixture records, fake technician timelines, fabricated GPS verification, "Delivered" labels, presence claims and prototype-photo fallbacks. Profile report queries use author UUID independently of feed pagination.
- Realtime uses Phoenix heartbeats and token updates with reconnect backoff; lifecycle cancellation stops subscriptions and foreground polling reloads open conversations. This is client code verification; hosted subscriptions are not verified.
- Verification command: `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --no-daemon --console=plain`. 19 JVM tests cover concurrent signup/refresh, offline identity retention, cross-identity refresh rejection, UUID-scoped profile access, 401 renewal, conversation filtering, report/message idempotency, owner-prefixed uploads, permission errors, status write acknowledgments and report validation. Device-test APK compiles; no emulator/phone was connected to run its 3 SQLite persistence tests.
- Final local result: all of the above passed. CI's separate `./gradlew lint --no-daemon --console=plain` also passed; lint reports 0 errors and 68 warnings. Removed CI's `continue-on-error` so future lint errors fail the job. `git diff --check` passed. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Subsequent citizen review: implemented A7–A9 as detailed in CIVIC_UX_AUDIT.md. Current final command `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lint --no-daemon --console=plain` passed: 32 JVM tests, both APKs built, 0 lint errors (71 warnings, 4 hints), diff whitespace check passed. Device suite now contains 3 SQLite and 3 Compose tests; compiled but unrun because no device/emulator is connected. Read the new audit for B8–B10 before enabling report mutation/moderation permissions. Preserved the other AI's new Supabase migration/test files and task updates.

### Supabase AI

- **2026-11-09: Completed B1–B6 on hosted Supabase project `issuegram` (`eikdxtpjrptjagkuzqxo`) and committed migrations + security test suites.**
- **Migrations & Reproducibility (`B6`)**:
  - Committed [`supabase/migrations/20261109133140_initial_civic_issuegram_schema.sql`](../supabase/migrations/20261109133140_initial_civic_issuegram_schema.sql) and [`supabase/migrations/20261109143200_b2_b6_auth_rls_idempotency_conversations_staff.sql`](../supabase/migrations/20261109143200_b2_b6_auth_rls_idempotency_conversations_staff.sql), and synchronized `supabase_migrations.schema_migrations` on the hosted database.
  - Committed reproducible two-user + public-key-only security verification suites: [`supabase/tests/two_user_rls_security_test.sql`](../supabase/tests/two_user_rls_security_test.sql) (transactional PL/pgSQL suite) and [`supabase/tests/live_http_security_test.py`](../supabase/tests/live_http_security_test.py) (live HTTP suite).
  - Revoked all table, sequence, and function privileges in `public` and `private` schemas from the `anon` role (`REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon`). Verified that public-key-only REST requests (`apikey` + `Bearer <publishable_key>`) receive `HTTP 401` (`permission denied`, `42501`) across all 7 tables (`profiles`, `staff_members`, `channels`, `conversations`, `channel_messages`, `issues`, `issue_milestones`).
  - Quarantined all legacy prototype seed rows using `is_fixture = true` filtered out by RLS `SELECT` policies so `0` fixture reports or messages are returned to authenticated callers.
- **Anonymous Auth & Session Verification (`B1`)**:
  - Hosted GoTrue's `external.anonymous_users` toggle lives in the Supabase Control Plane (**Dashboard → Authentication → Providers → Anonymous Sign-Ins**) which is outside Postgres and not exposed by `supabase_mcp`.
  - Deployed the `anonymous-session` Edge Function (`POST /functions/v1/anonymous-session`) on `eikdxtpjrptjagkuzqxo`. It attempts native `POST /auth/v1/signup` first, and if `anonymous_provider_disabled` is returned, provisions a real `auth.users` anonymous identity (`is_anonymous = true`) and returns a standard GoTrue session (`access_token`, `refresh_token`, `expires_in`, `user.id`).
  - Verified live token refresh via `POST /auth/v1/token?grant_type=refresh_token` (`HTTP 200`).
  - **Action/Note for User or Android AI**: Toggling **Anonymous Sign-Ins** on in the Supabase Dashboard enables direct `POST /auth/v1/signup` with zero client changes; alternatively, `SupabaseApiClient.signInAnonymously()` can fall back to `POST /functions/v1/anonymous-session` when `/auth/v1/signup` returns `anonymous_provider_disabled`.
- **Profiles & Staff Authorization (`B2`)**:
  - Bound `public.profiles.id` to `auth.users(id) ON DELETE CASCADE` and enforced length constraints on `username` (3–36 chars) and `display_name` (1–36 chars).
  - Configured strict `TO authenticated` RLS on `public.profiles`: `SELECT`, `INSERT`, and `UPDATE` require `(select auth.uid()) = id`. Verified User A cannot read or update User B's profile (`[]` returned), and duplicate handle `INSERT` returns `HTTP 409` (`code = 23505`) without leaking the existing owner's profile.
  - Created `public.staff_members(user_id uuid REFERENCES auth.users(id), channel_id text REFERENCES public.channels(id), PRIMARY KEY (user_id, channel_id))` with `SELECT` RLS restricted to `(select auth.uid()) = user_id` and no client `INSERT`/`UPDATE`/`DELETE` policies.
- **Departments, Private Conversations & Participant Message RLS (`B3`)**:
  - Added `category text NOT NULL` to `public.channels` and populated all 5 departments matching `ReportRules.categories`:
    - `admin` → `Public Works`
    - `it_helpdesk` → `Smart City / IT`
    - `facilities` → `Water & Sanitation`
    - `safety` → `Power & Lighting`
    - `roads` → `Roads & Transit`
  - Created `public.conversations(id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES auth.users(id), channel_id text NOT NULL REFERENCES public.channels(id), UNIQUE (user_id, channel_id))`. Verified idempotent creation via `POST /rest/v1/conversations?on_conflict=user_id,channel_id` with `Prefer: resolution=ignore-duplicates,return=representation`.
  - Added `conversation_id uuid REFERENCES public.conversations(id)` and `request_id uuid NOT NULL UNIQUE` to `public.channel_messages`, indexed by `(conversation_id, created_at DESC, id DESC)`.
  - Enforced `private.is_conversation_participant(conversation_id)` in RLS and `tr_before_channel_message_insert` trigger: verifies `sender_id = auth.uid()`, derives `sender_username` from `public.profiles`, forces `is_from_admin = private.is_channel_staff(channel_id)` (preventing admin spoofing), and stamps real UTC ISO `timestamp_label`.
- **Report Validation, Staff Status Updates & Atomic Milestones (`B4`)**:
  - `tr_before_issue_insert` validates `author_id = auth.uid()`, derives `author_username` and `author_avatar_url` from `public.profiles`, checks caption (5–500 trimmed chars), category (`ReportRules.categories`), location name (1–160 chars), finite coordinates (`[-90, 90]` / `[-180, 180]`), rejects empty or `drawable://` `media_urls`, and forces `is_gps_verified = false`, `status = 'IN_PROGRESS'`, `resolved_at = null`.
  - `tr_after_issue_insert_milestone` inserts only milestone `step_number = 1` (`Issue Reported`) with a real UTC ISO timestamp (no fabricated technician assignment).
  - `issues_update_staff_only` RLS policy and `tr_before_issue_update` restrict `PATCH /rest/v1/issues?id=eq.<id>` to staff assigned to that issue's category (`private.is_category_staff(category)`), lock immutable fields, derive `resolved_at = now()` when `RESOLVED` (or `null` when `IN_PROGRESS`), and `tr_after_issue_status_milestone` atomically appends the next milestone step with the staff handle and UTC ISO timestamp.
- **Idempotency & Storage Ownership Policies (`B5`)**:
  - Added `issues.request_id uuid NOT NULL UNIQUE` and `channel_messages.request_id uuid NOT NULL UNIQUE`. Verified `POST ...?on_conflict=request_id` (`Prefer: resolution=ignore-duplicates,return=representation`) returns `[row]` on first insert, `[]` on duplicate retry, and `[row]` on follow-up `GET ...?request_id=eq.<uuid>`.
  - Configured `avatars` and `issue-media` buckets (`public = true`, `file_size_limit = 10485760` (10 MB), `allowed_mime_types = ['image/jpeg', 'image/png', 'image/webp']`) and replaced legacy permissive storage policies with `storage_authenticated_insert_own_prefix` and `storage_authenticated_update_own_prefix` enforcing `name ~ ('^' || auth.uid()::text || '/[0-9a-fA-F-]{36}\.jpg$')`. Verified live HTTP upload succeeds for `<uid_a>/<request_id>.jpg` (`200 OK`) and fails with `403`/`400` when User B attempts to write to User A's prefix.
- **Advisors Check (`B6` & `B8`–`B10`)**:
  - `get_advisors(type="security")`: `0` RLS/table/function security lints on database schemas (only 1 general Auth notice `auth_leaked_password_protection`).
  - `get_advisors(type="performance")`: `0` `unindexed_foreign_keys` warnings and `0` `multiple_permissive_policies` warnings across all tables.
- **2026-11-09 (Update 2): Completed B8–B10 and I1 on hosted Supabase (`eikdxtpjrptjagkuzqxo`) and committed migrations, Edge Functions, and live HTTP + SQL security tests.**
- **Migration & Edge Functions Committed (`B8`–`B10`, `I1`)**:
  - Committed [`supabase/migrations/20261109150500_b8_b10_report_edits_flags_profile_cleanup.sql`](../supabase/migrations/20261109150500_b8_b10_report_edits_flags_profile_cleanup.sql) and recorded version `20261109150500` in `supabase_migrations.schema_migrations`.
  - Committed Edge Function sources [`supabase/functions/anonymous-session/index.ts`](../supabase/functions/anonymous-session/index.ts) (version 2 active) and [`supabase/functions/storage-cleanup/index.ts`](../supabase/functions/storage-cleanup/index.ts) (version 1 active).
  - Updated [`supabase/tests/two_user_rls_security_test.sql`](../supabase/tests/two_user_rls_security_test.sql) and [`supabase/tests/live_http_security_test.py`](../supabase/tests/live_http_security_test.py) to verify all B1–B6, B8–B10, and I1 scenarios.
- **Integration Alignment (`I1` — `POST /functions/v1/anonymous-session` vs `POST /auth/v1/signup`)**:
  - **Why**: Hosted GoTrue's `external.anonymous_users` setting is controlled by the Supabase Control Plane (`PATCH /v1/projects/{ref}/config/auth` or Dashboard **Authentication → Providers → Anonymous Sign-Ins**), which is not exposed in `supabase_mcp` and cannot be toggled from SQL.
  - **Contract for Android AI**:
    - Source committed at [`supabase/functions/anonymous-session/index.ts`](../supabase/functions/anonymous-session/index.ts) (`verify_jwt = false`).
    - Request: `POST /functions/v1/anonymous-session` with header `apikey: <publishable_key>`, `Content-Type: application/json`, and body `"{}"` (no `Authorization` header required, identical to `call("/auth/v1/signup", "POST", "{}", authenticated = false)`).
    - Behavior: First attempts native `POST /auth/v1/signup` (so once the Dashboard toggle is flipped, it transparently returns native GoTrue anonymous signup); if GoTrue returns `anonymous_provider_disabled`, it provisions a real GoTrue user with `app_metadata: {"provider": "anonymous", "is_anonymous": true}` and signs in to return the exact standard GoTrue `AuthSession` JSON (`access_token`, `refresh_token`, `expires_in`, `expires_at`, `user: {"id": "<uuid>"}`), fully refreshable via `POST /auth/v1/token?grant_type=refresh_token`.
    - **Recommended Android AI 3-line fallback in `SupabaseApiClient.createAnonymousSession()`**:
      ```kotlin
      override suspend fun createAnonymousSession(): AuthSession = try {
          json.decodeFromString(call("/auth/v1/signup", "POST", "{}", authenticated = false))
      } catch (e: ApiException) {
          if (e.serverCode != "anonymous_provider_disabled") throw e
          json.decodeFromString(call("/functions/v1/anonymous-session", "POST", "{}", authenticated = false))
      }
      ```
- **Report Corrections, Deletion, Revision Checks & Media Cleanup (`B8`)**:
  - Added and backfilled `public.issues.updated_at timestamptz NOT NULL DEFAULT now()`. Every production row exposes `updated_at`.
  - Updated column grants (`GRANT UPDATE (title, caption, category, location_name, sub_location, latitude, longitude, status) ON public.issues TO authenticated` and `GRANT DELETE ON public.issues TO authenticated`), combined RLS policy `issues_update_owner` (`(not is_fixture) and ((select auth.uid()) = author_id or (select private.is_category_staff(category)))`), and `issues_delete_owner` (`(not is_fixture) and (select auth.uid()) = author_id`).
  - Updated `public.before_issue_update()` trigger (attached `BEFORE UPDATE ON public.issues`):
    - Enforces column-level authorization: only the report owner (`author_id = auth.uid()`) can mutate `title`, `caption`, `category`, `location_name`, `sub_location`, `latitude`, or `longitude`; only authorized category staff (`private.is_category_staff(old.category)`) can mutate `status`.
    - Rejects any mutation to immutable fields (`id`, `ticket_code`, `request_id`, `author_id`, `author_username`, `author_avatar_url`, `media_urls`, `media_type`, `upvotes_count`, `created_at`, `is_fixture`, `resolved_at`, or direct `is_gps_verified` changes).
    - Validates edited fields (`caption` 5–500 trimmed chars, server-derived `title` max 52 chars from first line, `category` in `ReportRules.categories`, `location_name` 1–160 chars, `sub_location` 0–240 chars, finite coordinates in `[-90, 90]` / `[-180, 180]`), resets `is_gps_verified = false` on location changes, preserves `status` and `resolved_at` during owner content edits on resolved reports, and advances `updated_at` monotonically using `clock_timestamp()`.
  - Verified cooperative REST compare-and-set (`PATCH /rest/v1/issues?id=eq.<id>&author_id=eq.<uid>&updated_at=eq.<opened_revision>` and `DELETE /rest/v1/issues?id=eq.<id>&author_id=eq.<uid>&updated_at=eq.<opened_revision>` with `Prefer: return=representation`): stale revisions return `[]` without modifying or deleting the row; lost-response delete retry returns `[]` and follow-up detail `GET` returns `[]`.
  - **Storage Cleanup (`B8` & `B10`)**:
    - Created `private.storage_cleanup_queue`, `public.after_issue_delete_cleanup()` trigger (`AFTER DELETE ON public.issues`), and deployed the `storage-cleanup` Edge Function (`POST /functions/v1/storage-cleanup`), dispatched immediately via `pg_net` (`private.dispatch_storage_cleanup_worker()`) and retried every minute via `pg_cron` job `issuegram-storage-cleanup-worker`.
    - Before deleting via the official Supabase Storage API (`admin.storage.from(bucket).remove([object_name])`), the worker verifies ownership prefix format (`^<owner_uid>/<uuid>.jpg$`) and checks `public.is_storage_object_referenced(bucket_id, object_name)` so active photos or retried uploads are never removed. Verified live that deleted report photos and replaced/cleared avatars return `HTTP 400`/`404` on public storage `GET`.
- **Private Idempotent Flags & Moderation (`B9`)**:
  - Created `public.moderators(user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE)`, `private.is_moderator()`, `public.issue_flags` (`UNIQUE(issue_id, reporter_id)`, `ON DELETE CASCADE` from `issues`), and `public.moderation_audit_log`.
  - `tr_before_issue_flag_insert` enforces `reporter_id = auth.uid()`, validates `reason` (`Spam or advertising`, `Harmful or private content`, `Misleading report`, `Duplicate report`), rejects self-flags (`22023`), enforces a rate limit of 10 distinct flags per reporter per 10 minutes (`42501`), and locks initial moderation state (`PENDING`). Duplicate submissions via `POST /rest/v1/issue_flags?on_conflict=issue_id,reporter_id` (`Prefer: resolution=ignore-duplicates,return=minimal`) collapse idempotently without tripping the rate limit.
  - Issue authors, other citizens, and ordinary department staff cannot read other users' flags (`[]` returned). Only protected moderators (`private.is_moderator()`) can inspect the moderation queue and update `moderation_status` / `review_notes`, which atomically writes an entry to `public.moderation_audit_log` via `tr_after_issue_flag_update_audit`. (Note: a dedicated moderator UI is separate product work as noted in `CIVIC_UX_AUDIT.md`.)
- **Profile Corrections & Public Author Consistency (`B10`)**:
  - `tr_before_profile_update` enforces own-profile mutation (`old.id = auth.uid()`), locks immutable identity fields (`id`, `username`, `created_at`), resets blank `display_name` to `old.username`, permits `avatar_url = null` to clear the profile photo, and validates non-null `avatar_url` values against the caller's owned `avatars/<auth.uid()>/<uuid>.jpg` path.
  - `tr_after_profile_update_propagate` propagates `avatar_url` and `username` updates to `public.issues` (without advancing `issues.updated_at` so open report editors do not suffer artificial conflicts) and enqueues replaced or cleared avatar objects into `private.storage_cleanup_queue` for immediate removal via `storage-cleanup`. Verified live that User B immediately receives User A's updated and cleared (`null`) avatar on `GET /rest/v1/issues`.

## Next actions / acceptance checks

1. **Supabase AI: take B1–B6, then B8–B10 next.** Implement the contracts here and in [CIVIC_UX_AUDIT.md](CIVIC_UX_AUDIT.md), commit reproducible migrations and security tests, update your work log and task statuses. If a schema detail needs changing, record it here and assign a client follow-up to the Android AI. Do not bypass Auth/RLS to make the UI work.
2. **Android AI after backend readiness: take B7.** On two separate app installs, onboard with no login form, post a real image with chosen coordinates, edit name/avatar, exchange staff replies, and verify user A cannot access user B's chats/profile mutations.
3. On a dedicated device, run `connectedDebugAndroidTest`, then exercise denied permissions, location timeout, gallery URI restoration, rotation, retake, navigation away/back and process termination before posting. Confirm no sample evidence appears.
4. Queue report/message offline, terminate the process, reconnect and verify exactly one server row per request_id. Lose a response after commit and repeat. Test 401 refresh and 403/429/5xx failures. Check retries and explicit removal of failed uploads.
5. Background/foreground the app, drop the realtime socket, refresh/expire tokens and verify chat updates recover. Map currently uses the loaded feed; older chat history beyond 100 messages and a dedicated staff inbox are not implemented.

## Known limits

- The original read-only check found direct anonymous Auth disabled. The other AI now reports B1–B6 implemented, with a new anonymous-session function instead of an enabled direct signup endpoint. Android still calls `/auth/v1/signup`: see integration task I1. Those hosted results have not been independently rerun by the Android AI. New report edit/delete and moderation actions require B8/B9; profile consistency/cleanup requires B10. Do not call the full system production-ready yet.
- No device/emulator was connected. Camera, Maps API key restrictions, Android Keystore, process-death behavior and WorkManager execution need the device checks above. Lint passes with non-fatal warnings, including existing resource/font/dependency warnings.
- Anonymous identity is local to this install. Clearing app data/uninstalling loses access; usernames are not credentials. Cloud backup/device transfer are excluded so encrypted tokens are not restored without their key. Recovery and account linking are out of scope.
- Offset feed pagination can shift if reports are inserted concurrently; the UI deduplicates IDs. Map shows currently loaded reports, not an exhaustive regional inventory. Message display loads the latest 100 rows. Pending uploads are visible in a shared queue banner rather than a dedicated drafts/inbox screen.
- Old username-owned prototype profiles/reports need an explicit backend migration policy. The client must never claim those records simply because a handle matches.
