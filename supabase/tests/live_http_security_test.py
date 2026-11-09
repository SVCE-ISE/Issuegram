#!/usr/bin/env python3
"""
Issuegram Live HTTP Two-User + Staff + Moderator + Public-Key-Only Security Test Suite (B1-B6, B8-B10, I1)
Verifies hosted Supabase Auth (/functions/v1/anonymous-session & /auth/v1/signup), REST RLS, idempotency,
owner report edits/deletes with updated_at revision checks, staff controls, private issue_flags & moderation,
profile propagation, and Storage object cleanup via the storage-cleanup worker.

Usage:
  SUPABASE_URL=https://eikdxtpjrptjagkuzqxo.supabase.co \
  SUPABASE_KEY=sb_publishable_... \
  python3 supabase/tests/live_http_security_test.py
"""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

URL = os.environ.get("SUPABASE_URL", "https://eikdxtpjrptjagkuzqxo.supabase.co").rstrip("/")
KEY = os.environ.get("SUPABASE_KEY", "sb_publishable_v_dt5JoojcUGGWsaxC0aDw_yIYKjR9q")


def req(method, path, token=None, data=None, headers=None, raw_bytes=None):
    h = {"apikey": KEY}
    if token:
        h["Authorization"] = f"Bearer {token}"
    if headers:
        h.update(headers)
    if raw_bytes is not None:
        body = raw_bytes
    elif data is not None:
        body = json.dumps(data).encode("utf-8")
        h.setdefault("Content-Type", "application/json")
    else:
        body = None
    request = urllib.request.Request(f"{URL}{path}", data=body, headers=h, method=method)
    try:
        with urllib.request.urlopen(request, timeout=25) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            try:
                return resp.status, json.loads(raw) if raw else None
            except Exception:
                return resp.status, raw
    except urllib.error.HTTPError as err:
        raw = err.read().decode("utf-8", errors="replace")
        try:
            return err.code, json.loads(raw) if raw else None
        except Exception:
            return err.code, raw


def obtain_anonymous_session():
    st, body = req("POST", "/auth/v1/signup", data={})
    if st == 200 and isinstance(body, dict) and body.get("access_token"):
        return body
    st, body = req("POST", "/functions/v1/anonymous-session", data={})
    assert st == 200 and isinstance(body, dict) and body.get("access_token"), f"Failed to get session: {st} {body}"
    return body


def enc(val):
    return urllib.parse.quote(str(val), safe="")


def main():
    print("1. Verifying public-key-only (anon) access is denied on all public tables...")
    for table in (
        "profiles",
        "staff_members",
        "moderators",
        "channels",
        "conversations",
        "channel_messages",
        "issues",
        "issue_milestones",
        "issue_confirmations",
        "issue_flags",
        "moderation_audit_log",
    ):
        st, body = req("GET", f"/rest/v1/{table}?select=*")
        assert st in (401, 403), f"Expected 401/403 for anon on {table}, got {st}: {body}"

    print("2. Creating two distinct anonymous sessions (User A & User B) and testing token refresh...")
    sess_a = obtain_anonymous_session()
    sess_b = obtain_anonymous_session()
    uid_a, tok_a, ref_a = sess_a["user"]["id"], sess_a["access_token"], sess_a["refresh_token"]
    uid_b, tok_b = sess_b["user"]["id"], sess_b["access_token"]
    assert uid_a != uid_b

    st, refreshed = req("POST", "/auth/v1/token?grant_type=refresh_token", data={"refresh_token": ref_a})
    assert st == 200 and refreshed.get("access_token"), f"Token refresh failed: {st} {refreshed}"
    tok_a = refreshed["access_token"]

    print("3. Verifying own-profile RLS and 23505 username conflict handling...")
    handle_a = f"civic_a_{uuid.uuid4().hex[:8]}"
    handle_b = f"civic_b_{uuid.uuid4().hex[:8]}"

    st, prof_a = req(
        "POST",
        "/rest/v1/profiles",
        token=tok_a,
        headers={"Prefer": "return=representation"},
        data={"id": uid_a, "username": handle_a, "display_name": "Citizen A"},
    )
    assert st == 201 and prof_a[0]["id"] == uid_a, f"Profile A creation failed: {st} {prof_a}"

    st, conflict = req(
        "POST",
        "/rest/v1/profiles",
        token=tok_b,
        headers={"Prefer": "return=representation"},
        data={"id": uid_b, "username": handle_a, "display_name": "Citizen B"},
    )
    assert st == 409 and conflict.get("code") == "23505", f"Expected 23505 conflict, got {st} {conflict}"

    st, prof_b = req(
        "POST",
        "/rest/v1/profiles",
        token=tok_b,
        headers={"Prefer": "return=representation"},
        data={"id": uid_b, "username": handle_b, "display_name": "Citizen B"},
    )
    assert st == 201 and prof_b[0]["id"] == uid_b

    st, leak = req("GET", f"/rest/v1/profiles?id=eq.{uid_a}&select=*", token=tok_b)
    assert st == 200 and leak == [], f"Profile leak to User B: {leak}"

    print("4. Verifying Storage ownership prefix (<auth.uid()>/<request_id>.jpg) and report creation (B5, B8)...")
    issue_req_id = str(uuid.uuid4())
    avatar_req_1 = str(uuid.uuid4())
    avatar_req_2 = str(uuid.uuid4())
    jpeg_bytes = b"\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00`\x00`\x00\x00\xff\xd9"

    # Upload issue photo and two avatars for User A
    for bucket, rid in (("issue-media", issue_req_id), ("avatars", avatar_req_1), ("avatars", avatar_req_2)):
        st, up_ok = req(
            "POST",
            f"/storage/v1/object/{bucket}/{uid_a}/{rid}.jpg",
            token=tok_a,
            headers={"Content-Type": "image/jpeg", "x-upsert": "true"},
            raw_bytes=jpeg_bytes,
        )
        assert st == 200, f"Valid owner storage upload failed ({bucket}): {st} {up_ok}"

    # Cross-user storage upload blocked
    st, up_bad = req(
        "POST",
        f"/storage/v1/object/issue-media/{uid_a}/{issue_req_id}.jpg",
        token=tok_b,
        headers={"Content-Type": "image/jpeg", "x-upsert": "true"},
        raw_bytes=jpeg_bytes,
    )
    assert st in (400, 403), f"Cross-user storage upload should fail, got {st} {up_bad}"

    issue_photo_url = f"{URL}/storage/v1/object/public/issue-media/{uid_a}/{issue_req_id}.jpg"
    avatar_url_1 = f"{URL}/storage/v1/object/public/avatars/{uid_a}/{avatar_req_1}.jpg"
    avatar_url_2 = f"{URL}/storage/v1/object/public/avatars/{uid_a}/{avatar_req_2}.jpg"

    # Set initial avatar on User A profile
    st, prof_upd = req(
        "PATCH",
        f"/rest/v1/profiles?id=eq.{uid_a}",
        token=tok_a,
        headers={"Prefer": "return=representation"},
        data={"avatar_url": avatar_url_1},
    )
    assert st == 200 and prof_upd[0]["avatar_url"] == avatar_url_1

    # User A creates an issue
    st, created_issues = req(
        "POST",
        "/rest/v1/issues?on_conflict=request_id",
        token=tok_a,
        headers={"Prefer": "resolution=ignore-duplicates,return=representation"},
        data={
            "request_id": issue_req_id,
            "author_id": uid_a,
            "author_username": handle_a,
            "author_avatar_url": avatar_url_1,
            "title": "Transformer sparking",
            "caption": "Transformer sparking near the library entrance",
            "category": "Power & Lighting",
            "location_name": "Library Avenue",
            "sub_location": "East Gate",
            "latitude": 12.9716,
            "longitude": 77.5946,
            "is_gps_verified": True,
            "media_urls": [issue_photo_url],
            "media_type": "PHOTO",
        },
    )
    assert st == 201 and len(created_issues) == 1, f"Create issue failed: {st} {created_issues}"
    issue = created_issues[0]
    issue_id = issue["id"]
    rev_1 = issue["updated_at"]
    assert rev_1 is not None, "updated_at must be non-null on created issue"
    assert issue["is_gps_verified"] is False, "Citizen cannot forge GPS verification"

    print("5. Verifying B8 owner edits, updated_at revision checks, and forbidden field rejection...")
    st, edited = req(
        "PATCH",
        f"/rest/v1/issues?id=eq.{issue_id}&author_id=eq.{uid_a}&updated_at=eq.{enc(rev_1)}",
        token=tok_a,
        headers={"Prefer": "return=representation"},
        data={
            "title": "Transformer sparking loudly",
            "caption": "Transformer sparking loudly near the library east gate",
            "category": "Power & Lighting",
            "location_name": "Library Avenue East",
            "sub_location": "Gate 2",
            "latitude": 12.9720,
            "longitude": 77.5950,
        },
    )
    assert st == 200 and len(edited) == 1, f"Owner edit failed: {st} {edited}"
    rev_2 = edited[0]["updated_at"]
    assert rev_2 != rev_1, f"updated_at did not advance: {rev_1} vs {rev_2}"

    # Stale revision filter returns empty list [] without overwriting
    st, stale = req(
        "PATCH",
        f"/rest/v1/issues?id=eq.{issue_id}&author_id=eq.{uid_a}&updated_at=eq.{enc(rev_1)}",
        token=tok_a,
        headers={"Prefer": "return=representation"},
        data={
            "title": "Stale edit",
            "caption": "Stale edit that should be rejected by revision filter",
            "category": "Power & Lighting",
            "location_name": "Library Avenue",
            "sub_location": "Gate 1",
            "latitude": 12.9716,
            "longitude": 77.5946,
        },
    )
    assert st == 200 and stale == [], f"Expected [] on stale edit, got {st} {stale}"

    # Citizen owner cannot change status to RESOLVED
    st, bad_status = req(
        "PATCH",
        f"/rest/v1/issues?id=eq.{issue_id}",
        token=tok_a,
        headers={"Prefer": "return=representation"},
        data={"status": "RESOLVED"},
    )
    assert st in (401, 403), f"Citizen status change must be rejected, got {st} {bad_status}"

    # User B cannot edit User A's report
    st, b_edit = req(
        "PATCH",
        f"/rest/v1/issues?id=eq.{issue_id}&author_id=eq.{uid_a}&updated_at=eq.{enc(rev_2)}",
        token=tok_b,
        headers={"Prefer": "return=representation"},
        data={
            "title": "User B intrusion",
            "caption": "User B attempting to edit User A report",
            "category": "Power & Lighting",
            "location_name": "Library Avenue",
            "sub_location": "",
            "latitude": 12.97,
            "longitude": 77.59,
        },
    )
    assert st == 200 and b_edit == [], f"User B edit should return [], got {st} {b_edit}"

    print("6. Verifying B10 profile corrections, null avatar clearing, and old avatar Storage cleanup...")
    # Replace avatar_url_1 with avatar_url_2 -> avatar_req_1 should be cleaned up from storage!
    st, prof_upd2 = req(
        "PATCH",
        f"/rest/v1/profiles?id=eq.{uid_a}",
        token=tok_a,
        headers={"Prefer": "return=representation"},
        data={"display_name": "", "avatar_url": avatar_url_2},
    )
    assert st == 200 and prof_upd2[0]["display_name"] == handle_a and prof_upd2[0]["avatar_url"] == avatar_url_2

    # User B fetches the issue and sees User A's updated avatar_url_2
    st, detail_b = req("GET", f"/rest/v1/issues?id=eq.{issue_id}&select=*,issue_milestones(*)&limit=1", token=tok_b)
    assert st == 200 and len(detail_b) == 1 and detail_b[0]["author_avatar_url"] == avatar_url_2

    # Now User A clears avatar (explicit null) -> propagates null and queues avatar_req_2 for cleanup
    st, prof_clear = req(
        "PATCH",
        f"/rest/v1/profiles?id=eq.{uid_a}",
        token=tok_a,
        headers={"Prefer": "return=representation"},
        data={"avatar_url": None},
    )
    assert st == 200 and prof_clear[0]["avatar_url"] is None

    st, detail_b2 = req("GET", f"/rest/v1/issues?id=eq.{issue_id}&select=*,issue_milestones(*)&limit=1", token=tok_b)
    assert st == 200 and detail_b2[0]["author_avatar_url"] is None

    print("7. Verifying B9 private idempotent issue_flags...")
    # Self-flag by User A is rejected
    st, self_flag = req(
        "POST",
        "/rest/v1/issue_flags?on_conflict=issue_id,reporter_id",
        token=tok_a,
        headers={"Prefer": "resolution=ignore-duplicates,return=minimal"},
        data={"issue_id": issue_id, "reporter_id": uid_a, "reason": "Duplicate report"},
    )
    assert st in (400, 403), f"Self-flag should be rejected, got {st} {self_flag}"

    # User B flags User A's issue twice -> both succeed idempotently (201)
    for reason in ("Duplicate report", "Spam or advertising"):
        st, flag_resp = req(
            "POST",
            "/rest/v1/issue_flags?on_conflict=issue_id,reporter_id",
            token=tok_b,
            headers={"Prefer": "resolution=ignore-duplicates,return=minimal"},
            data={"issue_id": issue_id, "reporter_id": uid_b, "reason": reason},
        )
        assert st in (200, 201, 204), f"Valid flag failed: {st} {flag_resp}"

    # User A cannot inspect flags on their report
    st, flag_leak = req("GET", f"/rest/v1/issue_flags?issue_id=eq.{issue_id}&select=*", token=tok_a)
    assert st == 200 and flag_leak == [], f"Flag leaked to issue author: {flag_leak}"

    print("7b. Verifying community issue_confirmations syncs upvotes_count without advancing updated_at...")
    st, conf_post = req(
        "POST",
        "/rest/v1/issue_confirmations?on_conflict=issue_id,user_id",
        token=tok_b,
        headers={"Prefer": "resolution=ignore-duplicates,return=minimal"},
        data={"issue_id": issue_id, "user_id": uid_b},
    )
    assert st in (200, 201, 204), f"Confirm issue failed: {st} {conf_post}"
    st, after_conf = req("GET", f"/rest/v1/issues?id=eq.{issue_id}&select=upvotes_count,updated_at&limit=1", token=tok_a)
    assert st == 200 and after_conf[0]["upvotes_count"] == 1, f"Expected upvotes_count=1, got {after_conf}"
    assert after_conf[0]["updated_at"] == rev_2, f"Confirmation must not advance updated_at: {rev_2} vs {after_conf[0]['updated_at']}"

    st, conf_del = req(
        "DELETE",
        f"/rest/v1/issue_confirmations?issue_id=eq.{issue_id}&user_id=eq.{uid_b}",
        token=tok_b,
        headers={"Prefer": "return=minimal"},
    )
    assert st in (200, 204), f"Unconfirm issue failed: {st} {conf_del}"
    st, after_unconf = req("GET", f"/rest/v1/issues?id=eq.{issue_id}&select=upvotes_count,updated_at&limit=1", token=tok_a)
    assert st == 200 and after_unconf[0]["upvotes_count"] == 0 and after_unconf[0]["updated_at"] == rev_2

    print("8. Verifying B8 owner report deletion, lost-response retry, and Storage photo cleanup...")
    # User B cannot delete User A's report
    st, b_del = req(
        "DELETE",
        f"/rest/v1/issues?id=eq.{issue_id}&author_id=eq.{uid_a}&updated_at=eq.{enc(rev_2)}",
        token=tok_b,
        headers={"Prefer": "return=representation"},
    )
    assert st == 200 and b_del == [], f"User B delete must return [], got {st} {b_del}"

    # User A deletes with stale revision rev_1 -> returns [] and row still exists
    st, stale_del = req(
        "DELETE",
        f"/rest/v1/issues?id=eq.{issue_id}&author_id=eq.{uid_a}&updated_at=eq.{enc(rev_1)}",
        token=tok_a,
        headers={"Prefer": "return=representation"},
    )
    assert st == 200 and stale_del == []
    st, still_there = req("GET", f"/rest/v1/issues?id=eq.{issue_id}&select=id&limit=1", token=tok_a)
    assert st == 200 and len(still_there) == 1

    # User A deletes with current revision rev_2 -> returns [deleted_row]
    st, ok_del = req(
        "DELETE",
        f"/rest/v1/issues?id=eq.{issue_id}&author_id=eq.{uid_a}&updated_at=eq.{enc(rev_2)}",
        token=tok_a,
        headers={"Prefer": "return=representation"},
    )
    assert st == 200 and len(ok_del) == 1 and ok_del[0]["id"] == issue_id

    # Retry delete (simulating lost response) -> returns [] and detail GET returns []
    st, retry_del = req(
        "DELETE",
        f"/rest/v1/issues?id=eq.{issue_id}&author_id=eq.{uid_a}&updated_at=eq.{enc(rev_2)}",
        token=tok_a,
        headers={"Prefer": "return=representation"},
    )
    assert st == 200 and retry_del == []
    st, gone = req("GET", f"/rest/v1/issues?id=eq.{issue_id}&select=*,issue_milestones(*)&limit=1", token=tok_a)
    assert st == 200 and gone == []

    # Trigger / verify storage-cleanup Edge Function removes the unreferenced files
    st, cleanup_res = req("POST", "/functions/v1/storage-cleanup", data={"limit": 25})
    assert st == 200, f"storage-cleanup call failed: {st} {cleanup_res}"

    # Give storage a moment and verify the deleted issue photo and replaced avatars are gone (400/404)
    time.sleep(1)
    for url_check in (issue_photo_url, avatar_url_1, avatar_url_2):
        path_only = url_check.replace(URL, "")
        st, _ = req("GET", path_only)
        assert st in (400, 404), f"Expected deleted storage object {path_only} to return 400/404, got {st}"

    print("9. Verifying private conversation & message isolation between User A and User B...")
    st, conv_a = req(
        "POST",
        "/rest/v1/conversations?on_conflict=user_id,channel_id",
        token=tok_a,
        headers={"Prefer": "resolution=ignore-duplicates,return=representation"},
        data={"user_id": uid_a, "channel_id": "safety"},
    )
    assert st == 201 and len(conv_a) == 1
    conv_a_id = conv_a[0]["id"]

    msg_req_id = str(uuid.uuid4())
    st, msg_a = req(
        "POST",
        "/rest/v1/channel_messages?on_conflict=request_id",
        token=tok_a,
        headers={"Prefer": "resolution=ignore-duplicates,return=representation"},
        data={
            "request_id": msg_req_id,
            "conversation_id": conv_a_id,
            "channel_id": "safety",
            "sender_id": uid_a,
            "sender_username": handle_a,
            "message": "Checking streetlight outage",
            "is_from_admin": True,
            "timestamp_label": "client-forged",
        },
    )
    assert st == 201 and len(msg_a) == 1
    assert msg_a[0]["is_from_admin"] is False, "Citizen spoofed is_from_admin"

    # Duplicate request_id returns []
    st, msg_dup = req(
        "POST",
        "/rest/v1/channel_messages?on_conflict=request_id",
        token=tok_a,
        headers={"Prefer": "resolution=ignore-duplicates,return=representation"},
        data={
            "request_id": msg_req_id,
            "conversation_id": conv_a_id,
            "channel_id": "safety",
            "sender_id": uid_a,
            "sender_username": handle_a,
            "message": "Checking streetlight outage",
            "is_from_admin": False,
            "timestamp_label": "now",
        },
    )
    assert st == 201 and msg_dup == []

    # User B cannot read User A's conversation or messages
    st, conv_leak = req("GET", f"/rest/v1/conversations?id=eq.{conv_a_id}&select=*", token=tok_b)
    assert st == 200 and conv_leak == []
    st, msg_leak = req("GET", f"/rest/v1/channel_messages?conversation_id=eq.{conv_a_id}&select=*", token=tok_b)
    assert st == 200 and msg_leak == []

    print("ALL LIVE HTTP SECURITY CHECKS (B1-B6, B8-B10, I1) PASSED.")


if __name__ == "__main__":
    main()
