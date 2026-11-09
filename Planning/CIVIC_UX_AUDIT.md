# Civic experience review and AI task assignments

Date: 2026-11-09. Requested scope: review everyday citizen actions, fix missing client behavior throughout the app, and assign hosted work to the other AI. Preserve the existing design and anonymous welcome flow. Read [AI_HANDOFF.md](AI_HANDOFF.md) first; its B1–B6 prerequisites still apply.

## What was missing and what changed

| ID | Citizen need / previous gap | Android implementation | Server / device dependency |
|---|---|---|---|
| C1 | Correct a published report | Owner-only Edit report; correct description, category, location name/landmark and map coordinates. Validation matches new reports. Save stays open on failure; cancel checks unsaved changes. | B8; verify stale-edit conflicts and forbidden-field attempts. Original photo stays attached. |
| C2 | Delete an accidental or outdated report | Owner-only Delete report with confirmation and busy state; removes confirmed deletion from feed, map, profile and local caches. Retrying a lost response succeeds only if the row is gone. | B8; ownership enforcement, child-row cascade and media cleanup. |
| C3 | Reach the same report from different screens | Feed headers/action menu, profile report rows and map cards open a shared detail view with the same actions, evidence and timeline. Missing/deleted reports have refresh/back actions. | Device navigation check; server must permit single-report reads. |
| C4 | Share a civic issue and find its location | Android share sheet with ticket/description/status/location; copy ticket; open the real coordinates in Maps. | No new backend. Sharing uses text and a real Maps URL; no invented Issuegram website/deep link. |
| C5 | See photo evidence clearly | Tap a report photo for a full-screen viewer with pinch zoom, pan, reset, previous/next and close. | Device gestures, large font and image-load failure check. |
| C6 | Find relevant reports | Search loaded reports by ticket, description, location and landmark; category and active/resolved filters; clear filters, accurate empty messages and load-more remain available. | Search is explicitly scoped to loaded reports. Global search/geographic querying is separate work. |
| C7 | Ask the right department about a ticket | Contact department opens the directory channel matching the category and prepares ticket context in a private conversation. Existing typed draft is preserved; nothing is auto-sent. Missing department shows feedback. | Existing B3 and channels.category contract; department coverage must be real. |
| C8 | Flag spam, dangerous/private material, false or duplicate posts | Non-owner post menu provides a reason picker and confirmed submission feedback. Owners use edit/delete. Repeated submissions use one issue/reporter key. | B9; requires a real moderation process. Submission does not claim the post was removed. |
| C9 | Correct or clear optional profile information | Name dialog saves only on success, resets cancelled text when reopened and allows blank name to show the random username. Remove profile photo sends explicit null and cancels queued avatar jobs. Own feed/detail avatars use the current profile immediately. | B10 for other readers and old uploaded media. Device check for failed name saves. |
| C10 | Preserve or discard unfinished work | New report drafts can be explicitly discarded with confirmation; Android Back from details returns to the camera while keeping the draft. Camera releases after capture. Unsupported video controls were removed; capture is photo-only. | Device permission/retake/process-death checks. |
| C11 | Leave and return to a conversation safely | Text drafts persist by authenticated user + channel. Failed queueing retains input; successful queueing clears it. Sending is disabled during enqueue. Directory/conversation refresh is available. | Device check that drafts do not leak between users/departments. |
| C12 | Understand what is public and what is happening | Report form states that photo, report and location are public. Profile uses actual status/timestamps. Empty profile/feed offer Create report; empty timeline waits for real updates. Feed/profile/map state is retained when opening a detail view. | Device accessibility/navigation check. Existing server timestamps and staff-history contract still apply. |

Client implementation is complete for this table. Hosted-dependent actions are **not live-verified**. Do not mark the full product ready simply because the APK builds.

## Supabase AI: next tasks

Take B1–B6 from the main handoff first, then B8–B10 below. Keep anonymous Auth and server ownership checks. Do not add a citizen login form or expose privileged credentials. Record migrations, tests and any contract differences in your work log in AI_HANDOFF.md.

### B8 — report corrections, deletion, revision checks and cleanup

Android calls existing `issues` REST endpoints:

- Detail: `GET /rest/v1/issues?id=eq.<id>&select=*,issue_milestones(*)&limit=1`; zero rows means missing/unavailable.
- Edit: `PATCH /rest/v1/issues?id=eq.<id>&author_id=eq.<auth.uid()>&updated_at=eq.<opened_revision>`, `Prefer: return=representation`.
- Allowed edit JSON: `title`, `caption`, `category`, `location_name`, `sub_location`, `latitude`, `longitude`. Client derives title from the first caption line (max 52), caption is 5–500 trimmed characters, location name 1–160, landmark at most 240, category from ReportRules and coordinates finite/in range. Validate on the server too.
- Add/backfill `issues.updated_at timestamptz NOT NULL`; expose it in issue responses; change it on every content or status mutation using a server-generated value. The client keeps the revision it opened and expects an empty update result to mean conflict/unavailable. It preserves unsaved text, fetches the latest row and offers an explicit review/merge step. Untouched fields adopt the latest saved values; the citizen's corrected fields are retained. Legacy rows without a revision are temporarily accepted by the client; finish the migration before release. This is cooperative REST compare-and-set, not an assertion that RLS can require a URL filter from an arbitrary client.
- Preserve original author, request ID, creation time, ticket, status, evidence and staff timeline during content edits. Citizens must not change staff-only fields, resolution timestamps, votes, authorship or verification. Reset GPS verification on location edits. Reconcile department assignment when the category changes; do not invent an assignment or completion.
- **RLS alone is not column authorization.** An owner UPDATE policy must not accidentally let citizens set status or staff fields. Use appropriate column grants and server-side checks; combine owner and staff capabilities carefully. Retain both USING and WITH CHECK ownership conditions and the SELECT access needed for updates. See [Supabase RLS documentation](https://supabase.com/docs/guides/database/postgres/row-level-security).
- Delete: `DELETE /rest/v1/issues?id=eq.<id>&author_id=eq.<auth.uid()>&updated_at=eq.<opened_revision>`, `Prefer: return=representation`. Return the deleted row. If zero rows return, the client verifies with a detail GET: still visible is a failed/conflicting delete; gone is an idempotent success. Do not silently hide a still-existing report on mutation failure.
- Delete only the authenticated author's report. Cascade issue_milestones/issue-dependent records so FK errors do not break deletion. Keep private departmental conversations; deleting a public report must not erase an unrelated private conversation.
- Arrange reliable, idempotent cleanup of deleted issue photos and replaced/removed avatars. The client does not have admin storage privileges. Capture object paths before removing rows, restrict cleanup to referenced owned objects, and retry cleanup jobs. Database deletion does not automatically remove Storage objects; use the supported Storage API. See [Storage object deletion](https://supabase.com/docs/guides/storage/management/delete-objects). Verify real objects are removed, including delayed cleanup/retry.
- Do not allow citizens to mutate media_urls through the metadata endpoint; photo replacement needs a separate coordinated flow. Evidence history and status history must stay trustworthy.

Required tests: owner edits/deletes; second anonymous user and public-key-only caller cannot; forged author/status/verification changes rejected; an outdated revision filter does not overwrite a newer edit; every production row exposes a revision; content edit and staff status update race; resolved reports keep their resolution state; lost delete response retry; cascades and photo cleanup; cache/feed/profile/map reflect the final server state after refresh/relaunch.

Integration note from the new B2–B6 migration: `20261109143200_b2_b6_auth_rls_idempotency_conversations_staff.sql` currently grants only `UPDATE(status)` on issues, and `before_issue_update` rejects non-staff and freezes metadata. B8 must change the grant, policy **and trigger** together. Adding an owner UPDATE policy alone will not make this client's edit flow work. No Android AI changes have been made to those migration files.

### B9 — private flags and real moderation

- Client POST: `/rest/v1/issue_flags?on_conflict=issue_id,reporter_id` with `Prefer: resolution=ignore-duplicates,return=minimal`.
- Body: `issue_id` (existing bigint issue ID), `reporter_id` (authenticated UUID), `reason` (one of `Spam or advertising`, `Harmful or private content`, `Misleading report`, `Duplicate report`).
- Provide issue_flags with a generated primary key, issue foreign key, reporter foreign key, reason, server creation time, protected moderation status and UNIQUE(issue_id,reporter_id). Index ownership/moderation lookups and FK cleanup paths.
- Enforce reporter_id = auth.uid(), validate reason and reject self-flags. Citizens cannot set review status, impersonate reporters, inspect other flags or remove posts through this endpoint. Staff access requires a protected moderator role; ordinary department membership is not automatically global moderation permission.
- Repeated submissions count once. Use sensible server rate limits; flags never auto-delete a post simply because someone reported it. Provide a genuine restricted moderation queue, actions and audit process; assign a separate admin UI task if no staff interface exists.
- Tests: two-user isolation, public caller denial, self-flag denial, invalid reason rejection, duplicate collapse, immutable moderation fields, unauthorized reviewer denial and deletion cascade.

### B10 — profile corrections remain consistent

- Own profile PATCH permits `display_name` and `avatar_url` under the existing UUID ownership policy. Null avatar removes the photo; blank name in the UI becomes the generated username.
- Ensure old issue/message author labels are consistent with the intended public profile behavior. The client updates its own feed/detail avatar immediately, but other citizens must receive the corrected/current avatar too. Prefer a trusted server read model or propagation; never permit a client to bulk rewrite other authors' rows.
- Remove unused replaced/cleared avatar files using the owned cleanup process above. Respect current references so retries do not delete the active avatar.
- Tests: second user sees profile corrections after refresh; null avatar is allowed; only owner can patch; queued old avatar cannot reappear after a successful removal; no staff/identity fields are editable.

## Android AI: after backend readiness

1. Validate B8–B10 with two isolated installs and an assigned staff/moderator account; do not test security only by hiding menus.
2. Run JVM tests, debug build, lint and connected device tests. Exercise edit/delete from feed, profile and map; stale edits; offline and permission failures; cancel; double taps; deleted detail refresh; navigation back and process recreation.
3. Test image viewer, keyboard/large text, clipboard/share intents, Maps availability, draft discard/retake, blank-name reset, photo removal, report-context chat and per-channel draft recovery.
4. Update this table and AI_HANDOFF.md with actual results. If the backend changes the contract, assign the matching Android change explicitly and keep that task pending until tested.

## Separate product work still needed

These are broader features, not silently completed by this patch: global/nearby search and map viewport queries; notifications/subscriptions; account recovery or optional linking; public report deep links; photo replacement after publication; individual queued-upload editing; message edit/delete policy and older chat history; community "I also see this" confirmations; a staff/moderation inbox. Video capture is not offered; reports remain photo-only. Prioritize and coordinate these before adding controls that promise unsupported behavior.

## Verification log

- 2026-11-09 final check: `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lint --no-daemon --console=plain` passed. 32 JVM tests passed with 0 failures/errors/skips, including owner/revision filters, edit field allowlist, stale update rejection, deletion acknowledgments/lost-response retry, idempotent flags, explicit null avatar, search and edit validation. Debug APK and device-test APK built, including the stale-editor review test. Lint: 0 errors, 71 warnings and 4 hints. `git diff --check` passed.
- Device suite now contains 3 SQLite persistence tests and 3 Compose ownership/confirmation/stale-editor tests. No device or emulator is connected, so those 6 tests are compiled, not executed. Hosted database/settings remain unchanged by the Android AI.
- The other AI has now added B2–B6 migration/test files and marked those tasks complete in the main handoff. These are that AI's reported hosted results. Android did not rerun its hosted security tests or overwrite its migrations. B8–B10 still need the implementations above.
- Integration blocker I1 in the main handoff assigns the other AI the direct anonymous signup / newly reported anonymous-session function mismatch. Keep that task visible until the Android welcome flow uses a verified working contract.
- 2026-11-09 (Supabase AI): Implemented and live-verified **B8**, **B9**, **B10**, and **I1** on hosted Supabase (`eikdxtpjrptjagkuzqxo`). Committed migration [`supabase/migrations/20261109150500_b8_b10_report_edits_flags_profile_cleanup.sql`](../supabase/migrations/20261109150500_b8_b10_report_edits_flags_profile_cleanup.sql), Edge Functions [`supabase/functions/anonymous-session/index.ts`](../supabase/functions/anonymous-session/index.ts) and [`supabase/functions/storage-cleanup/index.ts`](../supabase/functions/storage-cleanup/index.ts), and updated security suites [`supabase/tests/two_user_rls_security_test.sql`](../supabase/tests/two_user_rls_security_test.sql) and [`supabase/tests/live_http_security_test.py`](../supabase/tests/live_http_security_test.py) (all 9 live HTTP suites + 10 SQL suites pass, 0 database security/performance advisor warnings). See `### Supabase AI` in [AI_HANDOFF.md](AI_HANDOFF.md) for full details and the `I1` fallback snippet for `SupabaseApiClient.createAnonymousSession()`.

## Cleanup follow-up — 2026-11-09

- Removed unused stock avatar/camera images, unsupported camera icons, stale campus strings, an unused color and unused downloadable-font certificate arrays. Kept the packaged font and current design.
- Moved feed preview fixtures and both sample photos into `app/src/debug/`; release no longer packages those photos. Preview-only image mappings are supplied through a guarded preview composition local.
- Preserved the feed's 736:975 proportions with a fixed ratio instead of decoding a sample photo in every runtime post card.
- Removed obsolete imports/comments; made screen Modifier arguments consistent; used current KTX, platform location cancellation and primitive Compose state APIs. Merged obsolete launcher resource qualifiers while preserving icons. Added ignore rules for local environment files and Supabase CLI scratch data.
- Verification: a clean rebuild and the final `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease lint --no-daemon --console=plain` passed. All 32 JVM tests passed; debug, device-test and unsigned release APKs built. Release archive inspection found zero sample-photo entries. Lint fell from 71 warnings + 4 hints to 17 warnings + 0 hints, with 0 errors; remaining warnings concern dependency versions and the existing wordmark vector. `git diff --check` passed. Device tests remain unrun; B8–B10 and I1 remain separate backend tasks. No hosted settings, migrations or user data were changed during cleanup.
