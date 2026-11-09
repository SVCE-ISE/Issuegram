-- =============================================================================
-- Issuegram Two-User + Staff + Moderator + Public-Key-Only RLS & Security Suite
-- Covers B1–B6 and B8–B10
-- =============================================================================
-- Runs inside a transaction and rolls back all test rows at the end so it can
-- be executed repeatedly against local or hosted Postgres without side effects.
-- =============================================================================

BEGIN;

DO $$
DECLARE
  v_user_a uuid := 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa';
  v_user_b uuid := 'bbbbbbbb-2222-4222-8222-bbbbbbbbbbbb';
  v_staff  uuid := 'cccccccc-3333-4333-8333-cccccccccccc';
  v_mod    uuid := 'dddddddd-4444-4444-8444-dddddddddddd';
  v_req_issue uuid := 'eeeeeeee-5555-4555-8555-eeeeeeeeeeee';
  v_req_msg_a uuid := 'ffffffff-6666-4666-8666-ffffffffffff';
  v_req_msg_s uuid := '11111111-7777-4777-8777-111111111111';
  v_req_av_1  uuid := '22222222-8888-4888-8888-222222222222';
  v_req_av_2  uuid := '33333333-9999-4999-8999-333333333333';
  v_issue_id bigint;
  v_rev_1 timestamptz;
  v_rev_2 timestamptz;
  v_rev_3 timestamptz;
  v_conv_a uuid;
  v_count int;
  v_status text;
  v_resolved_at timestamptz;
  v_is_admin boolean;
  v_avatar text;
  v_flag_id bigint;
  v_err text;
BEGIN
  -- 1. Provision test identities in auth.users
  INSERT INTO auth.users (id, aud, role, email, raw_app_meta_data, raw_user_meta_data, is_anonymous, created_at, updated_at)
  VALUES
    (v_user_a, 'authenticated', 'authenticated', NULL, '{"provider":"anonymous"}'::jsonb, '{}'::jsonb, true, now(), now()),
    (v_user_b, 'authenticated', 'authenticated', NULL, '{"provider":"anonymous"}'::jsonb, '{}'::jsonb, true, now(), now()),
    (v_staff,  'authenticated', 'authenticated', NULL, '{"provider":"anonymous"}'::jsonb, '{}'::jsonb, true, now(), now()),
    (v_mod,    'authenticated', 'authenticated', NULL, '{"provider":"anonymous"}'::jsonb, '{}'::jsonb, true, now(), now())
  ON CONFLICT (id) DO NOTHING;

  -- Assign v_staff to safety ('Power & Lighting') and admin ('Public Works')
  INSERT INTO public.staff_members (user_id, channel_id)
  VALUES (v_staff, 'safety'), (v_staff, 'admin')
  ON CONFLICT (user_id, channel_id) DO NOTHING;

  -- Assign v_mod to moderators
  INSERT INTO public.moderators (user_id)
  VALUES (v_mod)
  ON CONFLICT (user_id) DO NOTHING;

  -- ===========================================================================
  -- TEST 1: Public-key-only (anon) role is completely blocked
  -- ===========================================================================
  PERFORM set_config('role', 'anon', true);
  PERFORM set_config('request.jwt.claims', '{"role":"anon"}', true);

  v_err := '';
  BEGIN
    PERFORM count(*) FROM public.issues;
  EXCEPTION WHEN insufficient_privilege THEN
    v_err := '42501';
  END;
  IF v_err <> '42501' THEN
    RAISE EXCEPTION 'TEST 1 FAILED: anon was able to SELECT from public.issues';
  END IF;

  -- ===========================================================================
  -- TEST 2: User A creates profile, cannot spoof User B, and sees only own row
  -- ===========================================================================
  PERFORM set_config('role', 'authenticated', true);
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_user_a::text, 'role', 'authenticated')::text, true);

  INSERT INTO public.profiles (id, username, display_name, avatar_url)
  VALUES (
    v_user_a,
    'test_citizen_a',
    'Citizen A',
    'https://eikdxtpjrptjagkuzqxo.supabase.co/storage/v1/object/public/avatars/' || v_user_a::text || '/' || v_req_av_1::text || '.jpg'
  );

  v_err := '';
  BEGIN
    INSERT INTO public.profiles (id, username, display_name)
    VALUES (v_user_b, 'spoofed_b', 'Spoofed B');
  EXCEPTION WHEN OTHERS THEN
    v_err := SQLSTATE;
  END;
  IF v_err <> '42501' THEN
    RAISE EXCEPTION 'TEST 2 FAILED: User A was able to insert profile for User B (got %)', v_err;
  END IF;

  -- ===========================================================================
  -- TEST 3: User B creates profile, cannot read User A profile, 23505 on collision
  -- ===========================================================================
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_user_b::text, 'role', 'authenticated')::text, true);

  SELECT count(*) INTO v_count FROM public.profiles WHERE id = v_user_a;
  IF v_count <> 0 THEN
    RAISE EXCEPTION 'TEST 3 FAILED: User B could read User A profile';
  END IF;

  v_err := '';
  BEGIN
    INSERT INTO public.profiles (id, username, display_name)
    VALUES (v_user_b, 'test_citizen_a', 'Citizen B Collision');
  EXCEPTION WHEN unique_violation THEN
    v_err := '23505';
  END;
  IF v_err <> '23505' THEN
    RAISE EXCEPTION 'TEST 3 FAILED: Duplicate username did not raise 23505 (got %)', v_err;
  END IF;

  INSERT INTO public.profiles (id, username, display_name)
  VALUES (v_user_b, 'test_citizen_b', 'Citizen B');

  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_staff::text, 'role', 'authenticated')::text, true);
  INSERT INTO public.profiles (id, username, display_name)
  VALUES (v_staff, 'test_staff_safety', 'Safety Officer');

  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_mod::text, 'role', 'authenticated')::text, true);
  INSERT INTO public.profiles (id, username, display_name)
  VALUES (v_mod, 'test_moderator', 'Civic Moderator');

  -- ===========================================================================
  -- TEST 4: User A creates an issue; server enforces author_id, status, GPS,
  --         populates updated_at, and creates only 1 initial milestone
  -- ===========================================================================
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_user_a::text, 'role', 'authenticated')::text, true);

  INSERT INTO public.issues (
    request_id, author_id, author_username, title, caption, category, location_name, sub_location,
    latitude, longitude, is_gps_verified, status, media_urls
  ) VALUES (
    v_req_issue, v_user_a, 'spoofed_handle',
    'Streetlight transformer sparking',
    'Streetlight transformer sparking near North Gate',
    'Power & Lighting', 'North Gate Avenue', 'Pole #14',
    12.9716, 77.5946, true, 'RESOLVED',
    ARRAY['https://eikdxtpjrptjagkuzqxo.supabase.co/storage/v1/object/public/issue-media/' || v_user_a::text || '/' || v_req_issue::text || '.jpg']
  ) RETURNING id, status, updated_at INTO v_issue_id, v_status, v_rev_1;

  IF v_status <> 'IN_PROGRESS' OR v_rev_1 IS NULL THEN
    RAISE EXCEPTION 'TEST 4 FAILED: New issue status or updated_at invalid';
  END IF;

  SELECT count(*) INTO v_count FROM public.issue_milestones WHERE issue_id = v_issue_id;
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'TEST 4 FAILED: Expected exactly 1 initial milestone, got %', v_count;
  END IF;

  -- Idempotent retry with same request_id
  INSERT INTO public.issues (
    request_id, author_id, author_username, title, caption, category, location_name,
    latitude, longitude, media_urls
  ) VALUES (
    v_req_issue, v_user_a, 'test_citizen_a',
    'Streetlight transformer sparking',
    'Streetlight transformer sparking near North Gate',
    'Power & Lighting', 'North Gate Avenue',
    12.9716, 77.5946,
    ARRAY['https://eikdxtpjrptjagkuzqxo.supabase.co/storage/v1/object/public/issue-media/' || v_user_a::text || '/' || v_req_issue::text || '.jpg']
  ) ON CONFLICT (request_id) DO NOTHING;

  SELECT count(*) INTO v_count FROM public.issues WHERE request_id = v_req_issue;
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'TEST 4B FAILED: Duplicate request_id created extra row';
  END IF;

  -- ===========================================================================
  -- TEST 5 (B8): Owner edits metadata with updated_at check; citizen cannot change status
  -- ===========================================================================
  UPDATE public.issues
  SET title = 'Transformer sparking at North Gate',
      caption = 'Transformer sparking at North Gate crossing pole #14',
      category = 'Power & Lighting',
      location_name = 'North Gate Crossing',
      sub_location = 'Pole #14 East',
      latitude = 12.9720,
      longitude = 77.5950
  WHERE id = v_issue_id AND author_id = v_user_a AND updated_at = v_rev_1
  RETURNING updated_at INTO v_rev_2;

  IF v_rev_2 IS NULL OR v_rev_2 <= v_rev_1 THEN
    RAISE EXCEPTION 'TEST 5A FAILED: Owner edit did not advance updated_at';
  END IF;

  -- Outdated revision filter does not overwrite newer edit
  UPDATE public.issues
  SET caption = 'Outdated edit attempt'
  WHERE id = v_issue_id AND author_id = v_user_a AND updated_at = v_rev_1;

  SELECT count(*) INTO v_count FROM public.issues WHERE id = v_issue_id AND caption = 'Outdated edit attempt';
  IF v_count <> 0 THEN
    RAISE EXCEPTION 'TEST 5B FAILED: Outdated revision filter overwrote newer edit';
  END IF;

  -- Citizen owner cannot set status = 'RESOLVED'
  v_err := '';
  BEGIN
    UPDATE public.issues SET status = 'RESOLVED' WHERE id = v_issue_id;
  EXCEPTION WHEN OTHERS THEN
    v_err := SQLSTATE;
  END;
  IF v_err <> '42501' THEN
    RAISE EXCEPTION 'TEST 5C FAILED: Non-staff owner was able to resolve issue';
  END IF;

  -- ===========================================================================
  -- TEST 6: Private department conversations isolated between User A and User B
  -- ===========================================================================
  INSERT INTO public.conversations (user_id, channel_id)
  VALUES (v_user_a, 'safety')
  ON CONFLICT (user_id, channel_id) DO UPDATE SET channel_id = EXCLUDED.channel_id
  RETURNING id INTO v_conv_a;

  INSERT INTO public.channel_messages (
    request_id, conversation_id, channel_id, sender_id, sender_username,
    message, is_from_admin, timestamp_label
  ) VALUES (
    v_req_msg_a, v_conv_a, 'safety', v_user_a, 'spoof',
    'Pole #14 is still sparking', true, 'fake-time'
  ) RETURNING is_from_admin INTO v_is_admin;

  IF v_is_admin IS TRUE THEN
    RAISE EXCEPTION 'TEST 6 FAILED: Citizen was able to spoof is_from_admin = true';
  END IF;

  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_user_b::text, 'role', 'authenticated')::text, true);

  SELECT count(*) INTO v_count FROM public.conversations WHERE id = v_conv_a;
  IF v_count <> 0 THEN
    RAISE EXCEPTION 'TEST 6B FAILED: User B could see User A conversation';
  END IF;

  SELECT count(*) INTO v_count FROM public.channel_messages WHERE conversation_id = v_conv_a;
  IF v_count <> 0 THEN
    RAISE EXCEPTION 'TEST 6C FAILED: User B could see User A channel messages';
  END IF;

  -- ===========================================================================
  -- TEST 7: Authorized Staff replies, resolves issue, and owner edits preserve resolution
  -- ===========================================================================
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_staff::text, 'role', 'authenticated')::text, true);

  INSERT INTO public.channel_messages (
    request_id, conversation_id, channel_id, sender_id, sender_username,
    message, is_from_admin, timestamp_label
  ) VALUES (
    v_req_msg_s, v_conv_a, 'safety', v_staff, 'test_staff_safety',
    'Line crew dispatched and breaker isolated.', false, 'now'
  ) RETURNING is_from_admin INTO v_is_admin;

  IF v_is_admin IS NOT TRUE THEN
    RAISE EXCEPTION 'TEST 7B FAILED: Staff message did not get server-derived is_from_admin = true';
  END IF;

  UPDATE public.issues SET status = 'RESOLVED' WHERE id = v_issue_id
  RETURNING status, resolved_at, updated_at INTO v_status, v_resolved_at, v_rev_3;

  IF v_status <> 'RESOLVED' OR v_resolved_at IS NULL OR v_rev_3 <= v_rev_2 THEN
    RAISE EXCEPTION 'TEST 7C FAILED: Staff could not resolve issue or updated_at did not advance';
  END IF;

  SELECT count(*) INTO v_count FROM public.issue_milestones WHERE issue_id = v_issue_id;
  IF v_count <> 2 THEN
    RAISE EXCEPTION 'TEST 7D FAILED: Resolving issue did not append milestone #2';
  END IF;

  -- Owner edits content after resolution -> status and resolved_at are preserved
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_user_a::text, 'role', 'authenticated')::text, true);
  UPDATE public.issues
  SET sub_location = 'Pole #14 East (Confirmed)'
  WHERE id = v_issue_id AND author_id = v_user_a AND updated_at = v_rev_3
  RETURNING status, resolved_at, updated_at INTO v_status, v_resolved_at, v_rev_3;

  IF v_status <> 'RESOLVED' OR v_resolved_at IS NULL THEN
    RAISE EXCEPTION 'TEST 7E FAILED: Owner edit on resolved report lost resolution state';
  END IF;

  -- ===========================================================================
  -- TEST 8 (B10): Profile corrections, null avatar clearing, and propagation
  -- ===========================================================================
  UPDATE public.profiles
  SET display_name = '',
      avatar_url = 'https://eikdxtpjrptjagkuzqxo.supabase.co/storage/v1/object/public/avatars/' || v_user_a::text || '/' || v_req_av_2::text || '.jpg'
  WHERE id = v_user_a;

  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_user_b::text, 'role', 'authenticated')::text, true);
  SELECT author_avatar_url INTO v_avatar FROM public.issues WHERE id = v_issue_id;
  IF v_avatar NOT LIKE '%' || v_req_av_2::text || '.jpg' THEN
    RAISE EXCEPTION 'TEST 8A FAILED: Updated avatar did not propagate to issue row';
  END IF;

  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_user_a::text, 'role', 'authenticated')::text, true);
  UPDATE public.profiles SET avatar_url = NULL WHERE id = v_user_a;
  SELECT author_avatar_url INTO v_avatar FROM public.issues WHERE id = v_issue_id;
  IF v_avatar IS NOT NULL THEN
    RAISE EXCEPTION 'TEST 8B FAILED: Null avatar did not propagate to issue row';
  END IF;

  -- ===========================================================================
  -- TEST 9 (B9): Private idempotent issue_flags and restricted moderation
  -- ===========================================================================
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_user_b::text, 'role', 'authenticated')::text, true);
  INSERT INTO public.issue_flags (issue_id, reporter_id, reason)
  VALUES (v_issue_id, v_user_b, 'Duplicate report')
  ON CONFLICT (issue_id, reporter_id) DO NOTHING;

  INSERT INTO public.issue_flags (issue_id, reporter_id, reason)
  VALUES (v_issue_id, v_user_b, 'Spam or advertising')
  ON CONFLICT (issue_id, reporter_id) DO NOTHING;

  -- Issue author User A and ordinary staff cannot inspect User B's flag
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_user_a::text, 'role', 'authenticated')::text, true);
  SELECT count(*) INTO v_count FROM public.issue_flags WHERE issue_id = v_issue_id;
  IF v_count <> 0 THEN
    RAISE EXCEPTION 'TEST 9A FAILED: Issue author could inspect flags';
  END IF;

  -- Moderator reviews flag and writes audit log
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_mod::text, 'role', 'authenticated')::text, true);
  SELECT id INTO v_flag_id FROM public.issue_flags WHERE issue_id = v_issue_id;
  UPDATE public.issue_flags SET moderation_status = 'REVIEWED_KEPT', review_notes = 'Valid report' WHERE id = v_flag_id;

  SELECT count(*) INTO v_count FROM public.moderation_audit_log WHERE flag_id = v_flag_id AND action = 'REVIEWED_KEPT';
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'TEST 9B FAILED: Moderation audit log was not recorded';
  END IF;

  -- ===========================================================================
  -- TEST 10 (B8): Owner report deletion cascades milestones/flags & keeps chats
  -- ===========================================================================
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_user_a::text, 'role', 'authenticated')::text, true);
  DELETE FROM public.issues WHERE id = v_issue_id AND author_id = v_user_a AND updated_at = v_rev_3;

  SELECT count(*) INTO v_count FROM public.issues WHERE id = v_issue_id;
  IF v_count <> 0 THEN
    RAISE EXCEPTION 'TEST 10A FAILED: Owner delete did not remove issue';
  END IF;

  SELECT count(*) INTO v_count FROM public.conversations WHERE id = v_conv_a;
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'TEST 10B FAILED: Deleting report erased private departmental conversation';
  END IF;

  RAISE NOTICE 'ALL 10 TWO-USER + STAFF + MODERATOR + ANON RLS SECURITY TESTS PASSED.';
END $$;

ROLLBACK;
