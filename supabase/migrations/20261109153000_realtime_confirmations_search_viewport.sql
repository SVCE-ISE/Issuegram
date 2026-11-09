-- =============================================================================
-- Migration: Realtime Publication, Issue Confirmations ("I Also See This"),
--            Server-Side Search & Viewport Indexes
-- =============================================================================

-- 1. Ensure Realtime publication includes issues, issue_milestones, channels, channel_messages, conversations, issue_flags
do $$
declare
  t text;
begin
  foreach t in array array['issues', 'issue_milestones', 'channels', 'channel_messages', 'conversations', 'issue_flags'] loop
    if not exists (
      select 1 from pg_publication_tables
      where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;

-- 2. Community Issue Confirmations ("I Also See This")
create table if not exists public.issue_confirmations (
  issue_id bigint not null references public.issues(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (issue_id, user_id)
);

create index if not exists issue_confirmations_user_id_idx
  on public.issue_confirmations (user_id);

alter table public.issue_confirmations enable row level security;
revoke all on table public.issue_confirmations from anon, authenticated;
grant select, insert, delete on table public.issue_confirmations to authenticated;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'issue_confirmations' and policyname = 'issue_confirmations_select_own'
  ) then
    create policy issue_confirmations_select_own
      on public.issue_confirmations
      for select
      to authenticated
      using ((select auth.uid()) = user_id);
  else
    alter policy issue_confirmations_select_own
      on public.issue_confirmations
      to authenticated
      using ((select auth.uid()) = user_id);
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'issue_confirmations' and policyname = 'issue_confirmations_insert_own'
  ) then
    create policy issue_confirmations_insert_own
      on public.issue_confirmations
      for insert
      to authenticated
      with check ((select auth.uid()) = user_id);
  else
    alter policy issue_confirmations_insert_own
      on public.issue_confirmations
      to authenticated
      with check ((select auth.uid()) = user_id);
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'issue_confirmations' and policyname = 'issue_confirmations_delete_own'
  ) then
    create policy issue_confirmations_delete_own
      on public.issue_confirmations
      for delete
      to authenticated
      using ((select auth.uid()) = user_id);
  else
    alter policy issue_confirmations_delete_own
      on public.issue_confirmations
      to authenticated
      using ((select auth.uid()) = user_id);
  end if;
end $$;

-- Update before_issue_update so internal upvotes_count sync bypasses immutable check without advancing updated_at
create or replace function public.before_issue_update()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  caller_uid uuid := (select auth.uid());
  is_owner boolean := (caller_uid is not null and old.author_id = caller_uid);
  is_staff boolean := private.is_category_staff(old.category);
  content_changed boolean;
  status_changed boolean;
  location_changed boolean;
  prof record;
begin
  if current_user in ('postgres', 'service_role') and (
    caller_uid is null
    or coalesce(current_setting('issuegram.profile_propagation', true), '') = 'on'
    or coalesce(current_setting('issuegram.confirmation_sync', true), '') = 'on'
  ) then
    return new;
  end if;

  if not is_owner and not is_staff then
    raise exception 'Permission denied to update this issue' using errcode = '42501';
  end if;

  if new.id is distinct from old.id
     or new.ticket_code is distinct from old.ticket_code
     or new.request_id is distinct from old.request_id
     or new.author_id is distinct from old.author_id
     or new.author_username is distinct from old.author_username
     or new.author_avatar_url is distinct from old.author_avatar_url
     or new.media_urls is distinct from old.media_urls
     or new.media_type is distinct from old.media_type
     or new.upvotes_count is distinct from old.upvotes_count
     or new.created_at is distinct from old.created_at
     or new.is_fixture is distinct from old.is_fixture
     or new.resolved_at is distinct from old.resolved_at then
    raise exception 'Cannot modify protected issue fields' using errcode = '42501';
  end if;

  if new.is_gps_verified is distinct from old.is_gps_verified then
    raise exception 'Cannot directly modify GPS verification' using errcode = '42501';
  end if;

  content_changed := (
    new.title is distinct from old.title
    or new.caption is distinct from old.caption
    or new.category is distinct from old.category
    or new.location_name is distinct from old.location_name
    or new.sub_location is distinct from old.sub_location
    or new.latitude is distinct from old.latitude
    or new.longitude is distinct from old.longitude
  );

  status_changed := (new.status is distinct from old.status);

  if content_changed and not is_owner then
    raise exception 'Only the report author can edit report details' using errcode = '42501';
  end if;

  if status_changed and not is_staff then
    raise exception 'Only authorized department staff can update issue status' using errcode = '42501';
  end if;

  if content_changed then
    new.caption := btrim(coalesce(new.caption, ''));
    if char_length(new.caption) < 5 or char_length(new.caption) > 500 then
      raise exception 'Caption must be between 5 and 500 characters' using errcode = '22023';
    end if;

    new.title := left(split_part(new.caption, E'\n', 1), 52);

    if new.category not in ('Public Works', 'Power & Lighting', 'Water & Sanitation', 'Smart City / IT', 'Roads & Transit') then
      raise exception 'Invalid issue category' using errcode = '22023';
    end if;

    new.location_name := btrim(coalesce(new.location_name, ''));
    if char_length(new.location_name) < 1 or char_length(new.location_name) > 160 then
      raise exception 'Location name must be 1 to 160 characters' using errcode = '22023';
    end if;

    new.sub_location := btrim(coalesce(new.sub_location, ''));
    if char_length(new.sub_location) > 240 then
      raise exception 'Location detail must be at most 240 characters' using errcode = '22023';
    end if;

    if new.latitude is null or new.longitude is null
       or new.latitude in ('Infinity'::double precision, '-Infinity'::double precision, 'NaN'::double precision)
       or new.longitude in ('Infinity'::double precision, '-Infinity'::double precision, 'NaN'::double precision)
       or new.latitude < -90.0 or new.latitude > 90.0
       or new.longitude < -180.0 or new.longitude > 180.0 then
      raise exception 'Invalid coordinates' using errcode = '22023';
    end if;

    location_changed := (
      new.location_name is distinct from old.location_name
      or new.sub_location is distinct from old.sub_location
      or new.latitude is distinct from old.latitude
      or new.longitude is distinct from old.longitude
    );

    if location_changed then
      new.is_gps_verified := false;
    end if;

    select username, avatar_url into prof
    from public.profiles
    where id = old.author_id;

    if prof.username is not null then
      new.author_username := prof.username;
      new.author_avatar_url := prof.avatar_url;
    end if;
  end if;

  if status_changed then
    if new.status not in ('IN_PROGRESS', 'RESOLVED') then
      raise exception 'Invalid status value' using errcode = '22023';
    end if;

    if new.status = 'RESOLVED' then
      new.resolved_at := clock_timestamp();
    else
      new.resolved_at := null;
    end if;
  end if;

  if content_changed or status_changed then
    new.updated_at := clock_timestamp();
    if new.updated_at <= old.updated_at then
      new.updated_at := old.updated_at + interval '1 millisecond';
    end if;
  else
    new.updated_at := old.updated_at;
  end if;

  return new;
end;
$$;

create or replace function public.sync_issue_confirmations_count()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_target_issue_id bigint := coalesce(new.issue_id, old.issue_id);
  v_total integer;
begin
  select count(*) into v_total
  from public.issue_confirmations
  where issue_id = v_target_issue_id;

  perform set_config('issuegram.confirmation_sync', 'on', true);
  update public.issues
  set upvotes_count = v_total
  where id = v_target_issue_id
    and upvotes_count is distinct from v_total;
  perform set_config('issuegram.confirmation_sync', 'off', true);

  return coalesce(new, old);
end;
$$;

do $$
begin
  if not exists (
    select 1 from pg_trigger
    where not tgisinternal
      and tgrelid = 'public.issue_confirmations'::regclass
      and tgname = 'tr_sync_issue_confirmations_count'
  ) then
    create trigger tr_sync_issue_confirmations_count
      after insert or delete on public.issue_confirmations
      for each row
      execute function public.sync_issue_confirmations_count();
  end if;
end $$;

revoke all on function public.sync_issue_confirmations_count() from public, anon, authenticated;
revoke all on function public.before_issue_update() from public, anon, authenticated;

-- 3. Server-side search & map viewport indexes on public.issues
alter table public.issues
  add column if not exists search_document tsvector
  generated always as (
    to_tsvector(
      'english',
      coalesce(ticket_code, '') || ' ' ||
      coalesce(title, '') || ' ' ||
      coalesce(caption, '') || ' ' ||
      coalesce(location_name, '') || ' ' ||
      coalesce(sub_location, '')
    )
  ) stored;

create index if not exists issues_search_document_gin_idx
  on public.issues using gin (search_document);

create index if not exists issues_viewport_lat_lon_idx
  on public.issues (latitude, longitude)
  where not is_fixture;
