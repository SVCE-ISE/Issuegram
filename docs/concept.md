# Issuegram — Concept

## Summary

Issuegram is a civic issue tracker that works like a social feed. A citizen posts an issue with a photo and a location; a verified admin acknowledges it, assigns a worker, and posts progress updates. Everyone can follow the issue's timeline, and when it's fixed the citizen confirms the resolution (or reopens it).

The differentiator is the **closed loop with public proof**: issue → acknowledgement → assignment → progress → resolution → confirmation, visible to the community at every step.

## Problem

Reporting a civic problem today ends in a void — a form, a helpline, no feedback. Citizens stop reporting because nothing visibly happens; officials can't show progress. Issuegram makes both sides' work visible in one social surface.

## Roles

One app. Firebase Authentication (Google sign-in) for everyone; the role determines what the app shows after sign-in.

| Role | Who | Can do |
| --- | --- | --- |
| **Citizen** | Anyone signed in | Post issues, follow issues, like and share, reply in their own issue's thread, confirm or reopen resolutions |
| **Worker** | Field crew assigned by admins | See assigned tasks, update progress, attach resolution proof |
| **Admin** | Verified municipal/staff accounts (custom claim, granted out of band) | Triage the queue, acknowledge, assign workers, post updates, resolve, reply in issue threads |

Worker is proposed as its own role — if field crews coordinate offline, workers can be folded into admin later without changing the data model.

## Core object: one post = one issue

**Captured at post time** (target: under 30 seconds):

| Field | Required | Detail |
| --- | --- | --- |
| Photo | Yes | 1–4 images, compressed before upload — the evidence |
| Title | Yes | Concrete, 5–100 chars ("Overflowing drain near bus stop") |
| Category | Yes | Single choice chip: pothole, streetlight, garbage, water/sewage, drainage, road, safety, other |
| Location | Yes | Auto-captured coordinates + reverse-geocoded place name; pin is adjustable |
| Description | Optional | Max 500 chars — how bad, since when, who it affects |

**System-managed** (never form fields):

- Human-readable ID (`IG-2481`, sequential) shown in the feed, detail and share text
- Author uid + display name
- `status = Open`, `createdAt`, `updatedAt` (server timestamps)
- `likeCount`, `followCount`
- `assigneeId`, `acknowledgedAt`, `resolvedAt`; resolution note + proof photo; `reopenReason`
- Derived area/city from geocoding — used for feed filtering
- Timeline events for every status transition

**Deliberately excluded:**

- Priority/urgency — citizens self-prioritizing creates noise; admins set priority during triage
- Phone/email — contact happens in the issue thread, publicly
- Anonymous posting — everything is attributed via Firebase Auth
- Due dates — an admin tool, not a poster's field

Any location is supported; the feed filters by area instead of hard-coded wards. Multiple problems = multiple posts.

**Date and time display:**

- Feed card: relative time (`2 days ago`) alongside ticket ID and area
- Issue detail: absolute `Nov 18, 2026 · 10:42 AM` under the title; every timeline event (acknowledged, assigned, progressed, resolved, reopened) carries its own date + time stamp
- All dates and times locale-formatted (per the M3E content rules), never hard-coded

## Lifecycle

```
Open → Acknowledged → Assigned → In Progress → Resolved
  ↑                                                 ↓
  └────────────── Reopened ←── citizen reopens ─────┘
```

| Transition | Actor | Notes |
| --- | --- | --- |
| Open → Acknowledged | Admin | "Seen, it's real, we're on it" — the trust-building step |
| Acknowledged → Assigned | Admin | Picks a worker |
| Assigned → In Progress | Worker / Admin | Worker starts the job |
| In Progress → Resolved | Worker / Admin | Requires resolution note + photo proof |
| Resolved → Reopened | Citizen (author) | With a reason; notifies admins, returns to acknowledged-adjacent queue |

Every transition creates a timeline event (who, when, from → to), so the issue's history is the accountability record.

## Key flows

1. **Report** — FAB → bottom-sheet capture (category chips, photo, description, location picker) → post appears in feed at `Open`.
2. **Triage (admin)** — queue sorted oldest / by category / by area → open issue → acknowledge → assign worker via sheet.
3. **Work (worker)** — task list → open task → mark In Progress, add notes/photos.
4. **Resolve** — worker completes with proof photo → admin or citizen sees `Resolved` → full-screen celebration for the author, then a one-tap "Confirm fixed" or "Still broken" (reopen with reason).
5. **Follow** — feed, likes and sharing spread the issue; every status change notifies the author and anyone who follows it. When staff need to reach the author, they reply in the issue thread — readable by everyone, writeable only by the author and staff.

## Screen map (by role)

Compact phone layout, bottom navigation bar:

| Role | Destinations | Detail screens |
| --- | --- | --- |
| Citizen | Feed · My Issues · Notifications · Profile | Issue detail (identity, status timeline, photos, like/share, thread, confirm/reopen) |
| Worker | My Tasks · Profile | Task detail (issue info, status actions, proof upload) |
| Admin | Queue · Feed · Profile | Issue triage (assign, acknowledge), resolution review |

M3E patterns:
- **Feed / Queue** → *Tab list* (headline title, filter chips, grouped list rows with category shape avatars, status badges, no dividers)
- **Issue detail** → *Detail* (hero photo, title, mono meta, status timeline as grouped rows)
- **Report issue** → *Bottom-sheet capture*
- **Resolution** → *Outcome and celebration* (one celebration per resolved issue — the only expressive moment)
- **Profile / settings** → *Settings* (grouped lists in tonal cards)

Adaptive: medium/expanded windows get a navigation rail; admin queue becomes list + detail side by side.

## Design direction

Calm utility with a single celebration:

- **Motion**: `MotionScheme.standard()` — no bounce on routine actions. One expressive moment: the resolution celebration (spinner shape, emphasized display type, spring in). Reduced motion → simple fade.
- **Colour**: the kit's warm clay / teal / plum brand palette. Status badges carry their own semantics: neutral (Open), secondary (Acknowledged/Assigned), primary (In Progress), positive (Resolved), error (Reopened) — always with a label and icon, never colour alone.
- **Type**: Fraunces for display moments (greeting, celebration), Roboto Flex for UI, Roboto Mono for meta (issue ID, timestamps, location).
- **Shape**: one shape per category (pothole, streetlight, garbage…) used as the category avatar, consistent everywhere; cards `extraLarge`; pressables pills.
- **Voice**: second person, sentence case, real units and plain consequences ("Reported 2 days ago · WC Road").
- **Feeds**: no dividers, tonal surfaces, status shown as a badge on each card, keeping scanning calm.

## Technical foundation

- **Auth**: Firebase Authentication (Google sign-in); roles via custom claims (`citizen` default, `admin` verified out of band, `worker` assigned by admin).
- **Data**: Firestore — `issues`, `issues/{id}/updates`, `issues/{id}/thread`, `users`; Cloud Storage for photos; FCM for notifications.
- **Integrity**: status transitions enforced server-side (callable functions / security rules), so only admins can acknowledge/assign and only the author can reopen. Thread writes are limited to the issue author, admins and the assigned worker; reads are public.
- **Moderation**: no public comment feed to police; reports/hide for issue posts.

## MVP scope

- Google sign-in; citizen/admin roles
- Create issue with photo + location; feed with area/city filter
- Likes, share deep links, follow
- Admin triage: acknowledge, assign, resolve with proof
- Issue thread (staff ↔ author, public read)
- Status timeline + notifications on status change
- Citizen confirm/reopen on resolved issues
- The one resolution celebration

Out of scope for MVP: worker role in-app, map view, analytics dashboards, multi-city tenancy, public comments.

## Open questions

- Do field workers get the app in v1, or do admins input progress on their behalf?
- Is location required or optional (some issues are best described by landmark only)?
- Should the feed be public (logged-out browsing) for civic transparency?
- Any verification detail for admin accounts (email domain, invite code, manual approval)?
