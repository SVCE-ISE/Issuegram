-- Migration: 20261109133140_initial_civic_issuegram_schema.sql
-- Initial public tables, storage buckets, and Realtime publication

create sequence if not exists public.issue_ticket_seq start with 107 increment by 1;

create table if not exists public.profiles (
  id uuid default gen_random_uuid() primary key,
  username text not null unique,
  display_name text not null,
  avatar_url text,
  bio text default 'Civic Citizen Reporter',
  created_at timestamptz not null default now()
);

create index if not exists profiles_username_idx on public.profiles (lower(username));

create table if not exists public.issues (
  id bigint generated always as identity primary key,
  ticket_code text not null unique default ('#IG-' || nextval('public.issue_ticket_seq')::text),
  author_id uuid not null references public.profiles(id) on delete cascade,
  author_username text not null,
  author_avatar_url text,
  title text not null,
  caption text not null,
  category text not null default 'Civic Infrastructure',
  location_name text not null,
  sub_location text not null default 'GPS Verified Zone',
  latitude double precision not null,
  longitude double precision not null,
  is_gps_verified boolean not null default true,
  status text not null default 'IN_PROGRESS' check (status in ('IN_PROGRESS', 'RESOLVED')),
  media_urls text[] not null default '{}',
  media_type text not null default 'PHOTO' check (media_type in ('PHOTO', 'VIDEO')),
  upvotes_count integer not null default 0,
  resolved_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists issues_author_id_idx on public.issues (author_id);
create index if not exists issues_status_idx on public.issues (status);
create index if not exists issues_created_at_idx on public.issues (created_at desc);

create table if not exists public.issue_milestones (
  id bigint generated always as identity primary key,
  issue_id bigint not null references public.issues(id) on delete cascade,
  step_order smallint not null,
  title text not null,
  actor text not null,
  timestamp_label text not null,
  is_completed boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists issue_milestones_issue_id_idx on public.issue_milestones (issue_id, step_order);

create table if not exists public.channels (
  id text primary key,
  name text not null,
  subtitle text not null,
  last_message text not null,
  last_timestamp_label text not null,
  unread_count integer not null default 0,
  is_admin boolean not null default false,
  badge_color_hex text not null default '#0E4B5A',
  icon_key text not null default 'verified',
  sort_order smallint not null default 0
);

create table if not exists public.channel_messages (
  id bigint generated always as identity primary key,
  channel_id text not null references public.channels(id) on delete cascade,
  sender_id uuid references public.profiles(id) on delete set null,
  sender_username text not null,
  message text not null,
  timestamp_label text not null,
  is_from_admin boolean not null default false,
  ticket_badge text,
  created_at timestamptz not null default now()
);

create index if not exists channel_messages_channel_id_idx on public.channel_messages (channel_id, created_at asc);
create index if not exists channel_messages_sender_id_idx on public.channel_messages (sender_id);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('avatars', 'avatars', true, 10485760, array['image/jpeg', 'image/png', 'image/webp']),
  ('issue-media', 'issue-media', true, 10485760, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = true;

alter publication supabase_realtime add table public.issues;
alter publication supabase_realtime add table public.issue_milestones;
alter publication supabase_realtime add table public.channels;
alter publication supabase_realtime add table public.channel_messages;
