-- Migration: 20261109143200_b2_b6_auth_rls_idempotency_conversations_staff.sql
-- Implements B2-B6 contract:
-- 1. Bind profiles.id to auth.users(id), enforce own-profile RLS, quarantine legacy fixtures
-- 2. Private conversations(id, user_id, channel_id) + participant-scoped channel_messages RLS
-- 3. Protected staff_members(user_id, channel_id) + category-matched channels directory
-- 4. Server-enforced report validation, idempotency (request_id), staff-only status transitions, and atomic milestone history
-- 5. Ownership-scoped storage policies (<auth.uid()>/<request_id>.jpg) and full anon role lockdown

-- 1. Bind profiles.id to auth.users(id)
alter table public.profiles
  add constraint profiles_id_fkey foreign key (id) references auth.users(id) on delete cascade,
  add constraint profiles_username_check check (char_length(btrim(username)) between 3 and 36),
  add constraint profiles_display_name_check check (char_length(btrim(display_name)) between 1 and 36);

-- 2. Update channels directory with exact ReportRules categories (5 departments)
alter table public.channels
  add column if not exists category text not null default '';

update public.channels set
  name = 'Civic Dispatch & Public Works',
  subtitle = 'municipal & public infrastructure',
  category = 'Public Works',
  last_message = 'Contact this department',
  last_timestamp_label = '',
  unread_count = 0
where id = 'admin';

update public.channels set
  name = 'Smart City & IT Desk',
  subtitle = 'digital kiosks, wi-fi & telemetry',
  category = 'Smart City / IT',
  last_message = 'Contact this department',
  last_timestamp_label = '',
  unread_count = 0
where id = 'it_helpdesk';

update public.channels set
  name = 'Water & Sanitation',
  subtitle = 'drinking water, drainage & sanitation',
  category = 'Water & Sanitation',
  last_message = 'Contact this department',
  last_timestamp_label = '',
  unread_count = 0
where id = 'facilities';

update public.channels set
  name = 'Power & Street Lighting',
  subtitle = 'streetlights, grid & electrical safety',
  category = 'Power & Lighting',
  last_message = 'Contact this department',
  last_timestamp_label = '',
  unread_count = 0
where id = 'safety';

insert into public.channels (id, name, subtitle, category, last_message, last_timestamp_label, unread_count, is_admin, badge_color_hex, icon_key, sort_order)
values
  ('roads', 'Roads & Public Transit', 'pavements, signals & transit corridors', 'Roads & Transit', 'Contact this department', '', 0, false, '#2C3E50', 'map_pin', 5)
on conflict (id) do update set
  category = excluded.category,
  last_message = excluded.last_message,
  last_timestamp_label = excluded.last_timestamp_label,
  unread_count = 0;

create unique index if not exists channels_category_key on public.channels (category);

-- 3. Protected staff_members table
create table if not exists public.staff_members (
  user_id uuid not null references auth.users(id) on delete cascade,
  channel_id text not null references public.channels(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, channel_id)
);

create index if not exists staff_members_channel_id_idx on public.staff_members (channel_id);

-- 4. Private conversations table
create table if not exists public.conversations (
  id uuid default gen_random_uuid() primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  channel_id text not null references public.channels(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (user_id, channel_id)
);

create index if not exists conversations_user_id_idx on public.conversations (user_id);
create index if not exists conversations_channel_id_idx on public.conversations (channel_id);

-- 5. Idempotency columns on issues and channel_messages
alter table public.issues
  add column if not exists request_id uuid default gen_random_uuid() not null;

create unique index if not exists issues_request_id_key on public.issues (request_id);
create index if not exists issues_feed_order_idx on public.issues (created_at desc, id desc);

alter table public.channel_messages
  add column if not exists conversation_id uuid references public.conversations(id) on delete cascade,
  add column if not exists request_id uuid default gen_random_uuid() not null;

create unique index if not exists channel_messages_request_id_key on public.channel_messages (request_id);
create index if not exists channel_messages_conversation_order_idx on public.channel_messages (conversation_id, created_at desc, id desc);

-- 6. Quarantine flag for legacy prototype fixtures
alter table public.profiles add column if not exists is_fixture boolean not null default false;
alter table public.issues add column if not exists is_fixture boolean not null default false;
alter table public.issue_milestones add column if not exists is_fixture boolean not null default false;
alter table public.channel_messages add column if not exists is_fixture boolean not null default false;
alter table public.conversations add column if not exists is_fixture boolean not null default false;

-- 7. Private schema security-definer helpers for RLS
create schema if not exists private;

create or replace function private.is_channel_staff(target_channel_id text)
returns boolean
language sql
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.staff_members sm
    where sm.user_id = (select auth.uid())
      and sm.channel_id = target_channel_id
  );
$$;

revoke execute on function private.is_channel_staff(text) from public, anon;
grant execute on function private.is_channel_staff(text) to authenticated;

create or replace function private.is_category_staff(target_category text)
returns boolean
language sql
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.staff_members sm
    join public.channels c on c.id = sm.channel_id
    where sm.user_id = (select auth.uid())
      and c.category = target_category
  );
$$;

revoke execute on function private.is_category_staff(text) from public, anon;
grant execute on function private.is_category_staff(text) to authenticated;

create or replace function private.is_conversation_participant(target_conversation_id uuid)
returns boolean
language sql
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.conversations conv
    where conv.id = target_conversation_id
      and (
        conv.user_id = (select auth.uid())
        or exists (
          select 1
          from public.staff_members sm
          where sm.user_id = (select auth.uid())
            and sm.channel_id = conv.channel_id
        )
      )
  );
$$;

revoke execute on function private.is_conversation_participant(uuid) from public, anon;
grant execute on function private.is_conversation_participant(uuid) to authenticated;

-- 8. Server-side validation & atomic milestone triggers
create or replace function public.before_issue_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  caller_uid uuid := (select auth.uid());
  prof record;
begin
  if caller_uid is null or new.author_id is distinct from caller_uid then
    raise exception 'Unauthorized issue author' using errcode = '42501';
  end if;

  select username, avatar_url into prof
  from public.profiles
  where id = caller_uid;

  if prof.username is not null then
    new.author_username := prof.username;
    new.author_avatar_url := prof.avatar_url;
  end if;

  if char_length(btrim(new.caption)) < 5 or char_length(btrim(new.caption)) > 500 then
    raise exception 'Caption must be between 5 and 500 characters' using errcode = '22023';
  end if;

  if new.category not in ('Public Works', 'Power & Lighting', 'Water & Sanitation', 'Smart City / IT', 'Roads & Transit') then
    raise exception 'Invalid issue category' using errcode = '22023';
  end if;

  if char_length(btrim(new.location_name)) = 0 then
    raise exception 'Location name is required' using errcode = '22023';
  end if;

  if new.latitude < -90.0 or new.latitude > 90.0 or new.longitude < -180.0 or new.longitude > 180.0 then
    raise exception 'Invalid coordinates' using errcode = '22023';
  end if;

  if coalesce(array_length(new.media_urls, 1), 0) < 1 or new.media_urls::text like '%drawable://%' then
    raise exception 'A real photo upload URL is required' using errcode = '22023';
  end if;

  new.is_gps_verified := false;
  new.status := 'IN_PROGRESS';
  new.resolved_at := null;
  new.created_at := now();
  new.is_fixture := false;

  return new;
end;
$$;

create or replace function public.after_issue_insert_milestone()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.issue_milestones (issue_id, step_order, title, actor, timestamp_label, is_completed)
  values (
    new.id,
    1,
    'Issue Reported',
    'Reported by @' || new.author_username,
    to_char(new.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    true
  );
  return new;
end;
$$;

create or replace function public.before_issue_update()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if current_user in ('postgres', 'service_role') and (select auth.uid()) is null then
    return new;
  end if;

  if not private.is_category_staff(old.category) then
    raise exception 'Only authorized department staff can update issue status' using errcode = '42501';
  end if;

  new.id := old.id;
  new.ticket_code := old.ticket_code;
  new.request_id := old.request_id;
  new.author_id := old.author_id;
  new.author_username := old.author_username;
  new.author_avatar_url := old.author_avatar_url;
  new.title := old.title;
  new.caption := old.caption;
  new.category := old.category;
  new.location_name := old.location_name;
  new.sub_location := old.sub_location;
  new.latitude := old.latitude;
  new.longitude := old.longitude;
  new.is_gps_verified := old.is_gps_verified;
  new.media_urls := old.media_urls;
  new.media_type := old.media_type;
  new.created_at := old.created_at;
  new.is_fixture := old.is_fixture;

  if new.status not in ('REPORTED', 'IN_PROGRESS', 'RESOLVED') then
    raise exception 'Invalid status value' using errcode = '22023';
  end if;

  if new.status = 'RESOLVED' then
    new.resolved_at := now();
  else
    new.resolved_at := null;
  end if;

  return new;
end;
$$;

create or replace function public.after_issue_status_milestone()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  staff_handle text;
  next_step smallint;
begin
  if new.status is distinct from old.status then
    select coalesce('@' || p.username, 'Department Staff')
    into staff_handle
    from public.profiles p
    where p.id = (select auth.uid());

    staff_handle := coalesce(staff_handle, 'Department Staff');

    select coalesce(max(step_order), 1) + 1
    into next_step
    from public.issue_milestones
    where issue_id = new.id;

    insert into public.issue_milestones (issue_id, step_order, title, actor, timestamp_label, is_completed)
    values (
      new.id,
      next_step,
      case
        when new.status = 'RESOLVED' then 'Issue Resolved'
        when new.status = 'IN_PROGRESS' then 'Marked In Progress'
        else 'Status Updated'
      end,
      new.category || ' Staff (' || staff_handle || ')',
      to_char(now() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
      true
    );
  end if;
  return new;
end;
$$;

create or replace function public.before_channel_message_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  caller_uid uuid := (select auth.uid());
  conv record;
  is_staff boolean;
  prof_username text;
begin
  if caller_uid is null or new.sender_id is distinct from caller_uid then
    raise exception 'Unauthorized message sender' using errcode = '42501';
  end if;

  select id, user_id, channel_id into conv
  from public.conversations
  where id = new.conversation_id;

  if conv.id is null then
    raise exception 'Conversation not found' using errcode = '42501';
  end if;

  is_staff := private.is_channel_staff(conv.channel_id);

  if conv.user_id is distinct from caller_uid and not is_staff then
    raise exception 'Not a participant of this conversation' using errcode = '42501';
  end if;

  new.channel_id := conv.channel_id;
  new.is_from_admin := is_staff;

  select username into prof_username
  from public.profiles
  where id = caller_uid;

  if prof_username is not null then
    new.sender_username := prof_username;
  end if;

  if char_length(btrim(new.message)) < 1 or char_length(btrim(new.message)) > 2000 then
    raise exception 'Message must be 1 to 2000 characters' using errcode = '22023';
  end if;

  new.created_at := now();
  new.timestamp_label := to_char(new.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"');
  new.is_fixture := false;

  return new;
end;
$$;

revoke execute on function public.before_issue_insert() from public, anon, authenticated;
revoke execute on function public.after_issue_insert_milestone() from public, anon, authenticated;
revoke execute on function public.before_issue_update() from public, anon, authenticated;
revoke execute on function public.after_issue_status_milestone() from public, anon, authenticated;
revoke execute on function public.before_channel_message_insert() from public, anon, authenticated;

create trigger tr_before_issue_insert
  before insert on public.issues
  for each row execute function public.before_issue_insert();

create trigger tr_after_issue_insert_milestone
  after insert on public.issues
  for each row execute function public.after_issue_insert_milestone();

create trigger tr_before_issue_update
  before update on public.issues
  for each row execute function public.before_issue_update();

create trigger tr_after_issue_status_milestone
  after update on public.issues
  for each row execute function public.after_issue_status_milestone();

create trigger tr_before_channel_message_insert
  before insert on public.channel_messages
  for each row execute function public.before_channel_message_insert();

-- 9. Grants & RLS Policies
alter table public.profiles enable row level security;
alter table public.staff_members enable row level security;
alter table public.channels enable row level security;
alter table public.conversations enable row level security;
alter table public.channel_messages enable row level security;
alter table public.issues enable row level security;
alter table public.issue_milestones enable row level security;

revoke all on table public.profiles from anon;
revoke all on table public.staff_members from anon;
revoke all on table public.channels from anon;
revoke all on table public.conversations from anon;
revoke all on table public.channel_messages from anon;
revoke all on table public.issues from anon;
revoke all on table public.issue_milestones from anon;

revoke all on table public.profiles from authenticated;
grant select, insert, update (display_name, avatar_url, bio) on table public.profiles to authenticated;

revoke all on table public.staff_members from authenticated;
grant select on table public.staff_members to authenticated;

revoke all on table public.channels from authenticated;
grant select on table public.channels to authenticated;

revoke all on table public.conversations from authenticated;
grant select, insert on table public.conversations to authenticated;

revoke all on table public.channel_messages from authenticated;
grant select, insert on table public.channel_messages to authenticated;
grant usage, select on sequence public.channel_messages_id_seq to authenticated;

revoke all on table public.issues from authenticated;
grant select, insert, update (status) on table public.issues to authenticated;
grant usage, select on sequence public.issues_id_seq to authenticated;
grant usage, select on sequence public.issue_ticket_seq to authenticated;

revoke all on table public.issue_milestones from authenticated;
grant select on table public.issue_milestones to authenticated;

create policy "profiles_select_own" on public.profiles
  for select to authenticated
  using (not is_fixture and (select auth.uid()) = id);

create policy "profiles_insert_own" on public.profiles
  for insert to authenticated
  with check ((select auth.uid()) = id);

create policy "profiles_update_own" on public.profiles
  for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

create policy "staff_members_select_own" on public.staff_members
  for select to authenticated
  using ((select auth.uid()) = user_id);

create policy "channels_select_authenticated" on public.channels
  for select to authenticated
  using (true);

create policy "conversations_select_participant" on public.conversations
  for select to authenticated
  using (
    not is_fixture
    and (
      (select auth.uid()) = user_id
      or (select private.is_channel_staff(channel_id))
    )
  );

create policy "conversations_insert_own" on public.conversations
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

create policy "channel_messages_select_participant" on public.channel_messages
  for select to authenticated
  using (
    not is_fixture
    and (select private.is_conversation_participant(conversation_id))
  );

create policy "channel_messages_insert_participant" on public.channel_messages
  for insert to authenticated
  with check (
    (select auth.uid()) = sender_id
    and (select private.is_conversation_participant(conversation_id))
  );

create policy "issues_select_authenticated" on public.issues
  for select to authenticated
  using (not is_fixture);

create policy "issues_insert_own" on public.issues
  for insert to authenticated
  with check ((select auth.uid()) = author_id);

create policy "issues_update_staff" on public.issues
  for update to authenticated
  using ((select private.is_category_staff(category)))
  with check ((select private.is_category_staff(category)));

create policy "issue_milestones_select_authenticated" on public.issue_milestones
  for select to authenticated
  using (not is_fixture);

-- 10. Storage Ownership Policies (<auth.uid()>/<request_id>.jpg)
update storage.buckets
set
  public = true,
  file_size_limit = 10485760,
  allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp']
where id in ('avatars', 'issue-media');

create policy "storage_public_read" on storage.objects
  for select to anon, authenticated
  using (bucket_id in ('avatars', 'issue-media'));

create policy "storage_owner_insert" on storage.objects
  for insert to authenticated
  with check (
    bucket_id in ('avatars', 'issue-media')
    and (storage.foldername(name))[1] = (select auth.uid())::text
    and name ~ ('^' || (select auth.uid())::text || '/[0-9a-fA-F-]{36}\.jpg$')
  );

create policy "storage_owner_update" on storage.objects
  for update to authenticated
  using (
    bucket_id in ('avatars', 'issue-media')
    and (storage.foldername(name))[1] = (select auth.uid())::text
    and name ~ ('^' || (select auth.uid())::text || '/[0-9a-fA-F-]{36}\.jpg$')
  )
  with check (
    bucket_id in ('avatars', 'issue-media')
    and (storage.foldername(name))[1] = (select auth.uid())::text
    and name ~ ('^' || (select auth.uid())::text || '/[0-9a-fA-F-]{36}\.jpg$')
  );
