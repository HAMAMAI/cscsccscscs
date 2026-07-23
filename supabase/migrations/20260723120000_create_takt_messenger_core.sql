-- Takt Messenger initial schema.  This migration intentionally uses a new
-- takt_ namespace so it can coexist with the earlier invite-room prototype.
begin;

do $$ begin
  create type public.takt_conversation_kind as enum ('direct', 'group', 'saved');
exception when duplicate_object then null;
end $$;

do $$ begin
  create type public.takt_member_role as enum ('owner', 'admin', 'member');
exception when duplicate_object then null;
end $$;

do $$ begin
  create type public.takt_message_kind as enum ('text', 'image', 'file', 'voice', 'system');
exception when duplicate_object then null;
end $$;

create table if not exists public.takt_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text not null,
  username text,
  display_name text not null default 'Новый пользователь',
  about text not null default '',
  avatar_color text not null default '#8C78FF',
  avatar_path text,
  last_seen_at timestamptz not null default now(),
  is_online boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint takt_profiles_username_format check (
    username is null or username ~ '^[a-z0-9_]{5,32}$'
  ),
  constraint takt_profiles_display_name_size check (char_length(trim(display_name)) between 2 and 64),
  constraint takt_profiles_about_size check (char_length(about) <= 160),
  constraint takt_profiles_avatar_color_format check (avatar_color ~ '^#[0-9A-Fa-f]{6}$')
);

create unique index if not exists takt_profiles_username_unique
  on public.takt_profiles (lower(username))
  where username is not null;

create table if not exists public.takt_chat_folders (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.takt_profiles(id) on delete cascade,
  name text not null,
  color text not null default '#8C78FF',
  position integer not null default 0,
  created_at timestamptz not null default now(),
  constraint takt_chat_folders_name_size check (char_length(trim(name)) between 1 and 32),
  constraint takt_chat_folders_color_format check (color ~ '^#[0-9A-Fa-f]{6}$')
);

create table if not exists public.takt_conversations (
  id uuid primary key default gen_random_uuid(),
  kind public.takt_conversation_kind not null,
  title text,
  avatar_color text not null default '#8C78FF',
  direct_key text unique,
  created_by uuid not null references public.takt_profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint takt_conversations_title_size check (
    (kind = 'group' and char_length(trim(coalesce(title, ''))) between 2 and 64)
    or kind <> 'group'
  ),
  constraint takt_conversations_direct_key check (
    (kind = 'direct' and direct_key is not null) or (kind <> 'direct')
  ),
  constraint takt_conversations_avatar_color_format check (avatar_color ~ '^#[0-9A-Fa-f]{6}$')
);

create table if not exists public.takt_conversation_members (
  conversation_id uuid not null references public.takt_conversations(id) on delete cascade,
  user_id uuid not null references public.takt_profiles(id) on delete cascade,
  role public.takt_member_role not null default 'member',
  joined_at timestamptz not null default now(),
  last_read_at timestamptz not null default now(),
  is_archived boolean not null default false,
  is_pinned boolean not null default false,
  folder_id uuid references public.takt_chat_folders(id) on delete set null,
  muted_until timestamptz,
  draft_text text not null default '',
  primary key (conversation_id, user_id),
  constraint takt_member_draft_size check (char_length(draft_text) <= 4000)
);

create table if not exists public.takt_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.takt_conversations(id) on delete cascade,
  sender_id uuid not null references public.takt_profiles(id) on delete restrict,
  body text not null default '',
  kind public.takt_message_kind not null default 'text',
  reply_to_id uuid references public.takt_messages(id) on delete set null,
  forwarded_from_id uuid references public.takt_messages(id) on delete set null,
  attachment_id uuid,
  edited_at timestamptz,
  deleted_at timestamptz,
  is_pinned boolean not null default false,
  created_at timestamptz not null default now(),
  constraint takt_messages_body_size check (char_length(body) <= 4000),
  constraint takt_messages_text_has_body check (kind <> 'text' or char_length(trim(body)) > 0)
);

create index if not exists takt_messages_conversation_created_idx
  on public.takt_messages(conversation_id, created_at desc);

create table if not exists public.takt_attachments (
  id uuid primary key default gen_random_uuid(),
  message_id uuid references public.takt_messages(id) on delete cascade,
  owner_id uuid not null references public.takt_profiles(id) on delete restrict,
  file_name text not null,
  mime_type text not null,
  size_bytes integer not null,
  data bytea not null,
  duration_seconds integer,
  created_at timestamptz not null default now(),
  constraint takt_attachments_file_name_size check (char_length(file_name) between 1 and 120),
  constraint takt_attachments_mime_size check (char_length(mime_type) between 3 and 120),
  constraint takt_attachments_size check (size_bytes between 1 and 8388608),
  constraint takt_attachments_data_size check (octet_length(data) between 1 and 8388608),
  constraint takt_attachments_duration check (duration_seconds is null or duration_seconds between 1 and 300)
);

alter table public.takt_messages
  drop constraint if exists takt_messages_attachment_id_fkey;
alter table public.takt_messages
  add constraint takt_messages_attachment_id_fkey
  foreign key (attachment_id) references public.takt_attachments(id) on delete set null;

create table if not exists public.takt_message_reactions (
  message_id uuid not null references public.takt_messages(id) on delete cascade,
  user_id uuid not null references public.takt_profiles(id) on delete cascade,
  emoji text not null,
  created_at timestamptz not null default now(),
  primary key (message_id, user_id, emoji),
  constraint takt_reactions_emoji_size check (char_length(emoji) between 1 and 16)
);

create table if not exists public.takt_message_hidden (
  message_id uuid not null references public.takt_messages(id) on delete cascade,
  user_id uuid not null references public.takt_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (message_id, user_id)
);

create table if not exists public.takt_user_blocks (
  user_id uuid not null references public.takt_profiles(id) on delete cascade,
  blocked_user_id uuid not null references public.takt_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, blocked_user_id),
  constraint takt_user_blocks_not_self check (user_id <> blocked_user_id)
);

create table if not exists public.takt_privacy_settings (
  user_id uuid primary key references public.takt_profiles(id) on delete cascade,
  show_avatar_to text not null default 'everyone',
  show_last_seen_to text not null default 'everyone',
  allow_calls_from text not null default 'everyone',
  allow_group_invites_from text not null default 'everyone',
  updated_at timestamptz not null default now(),
  constraint takt_privacy_avatar_scope check (show_avatar_to in ('everyone', 'contacts', 'nobody')),
  constraint takt_privacy_last_seen_scope check (show_last_seen_to in ('everyone', 'contacts', 'nobody')),
  constraint takt_privacy_calls_scope check (allow_calls_from in ('everyone', 'contacts', 'nobody')),
  constraint takt_privacy_group_scope check (allow_group_invites_from in ('everyone', 'contacts', 'nobody'))
);

create table if not exists public.takt_typing_states (
  conversation_id uuid not null references public.takt_conversations(id) on delete cascade,
  user_id uuid not null references public.takt_profiles(id) on delete cascade,
  mode text not null default 'typing',
  expires_at timestamptz not null,
  updated_at timestamptz not null default now(),
  primary key (conversation_id, user_id),
  constraint takt_typing_mode check (mode in ('typing', 'recording', 'uploading'))
);

create table if not exists public.takt_call_sessions (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.takt_conversations(id) on delete cascade,
  started_by uuid not null references public.takt_profiles(id) on delete restrict,
  is_video boolean not null default false,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  constraint takt_call_session_times check (ended_at is null or ended_at >= started_at)
);

create unique index if not exists takt_one_active_call_per_conversation
  on public.takt_call_sessions(conversation_id) where ended_at is null;

create table if not exists public.takt_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.takt_profiles(id) on delete cascade,
  target_user_id uuid references public.takt_profiles(id) on delete set null,
  target_message_id uuid references public.takt_messages(id) on delete set null,
  reason text not null,
  created_at timestamptz not null default now(),
  constraint takt_reports_reason_size check (char_length(trim(reason)) between 3 and 500),
  constraint takt_reports_target check (target_user_id is not null or target_message_id is not null)
);

create or replace function public.takt_touch_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists takt_profiles_touch_updated_at on public.takt_profiles;
create trigger takt_profiles_touch_updated_at
before update on public.takt_profiles
for each row execute function public.takt_touch_updated_at();

drop trigger if exists takt_conversations_touch_updated_at on public.takt_conversations;
create trigger takt_conversations_touch_updated_at
before update on public.takt_conversations
for each row execute function public.takt_touch_updated_at();

create or replace function public.takt_is_member(p_conversation_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.takt_conversation_members member_row
    where member_row.conversation_id = p_conversation_id
      and member_row.user_id = (select auth.uid())
  );
$$;

create or replace function public.takt_can_access_message(p_message_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.takt_messages message_row
    where message_row.id = p_message_id
      and public.takt_is_member(message_row.conversation_id)
  );
$$;

create or replace function public.takt_are_blocked(p_other_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.takt_user_blocks block_row
    where (block_row.user_id = (select auth.uid()) and block_row.blocked_user_id = p_other_user_id)
       or (block_row.user_id = p_other_user_id and block_row.blocked_user_id = (select auth.uid()))
  );
$$;

create or replace function public.takt_handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  safe_name text;
begin
  safe_name := left(trim(coalesce(new.raw_user_meta_data ->> 'display_name', '')), 64);
  if char_length(safe_name) < 2 then
    safe_name := 'Новый пользователь';
  end if;

  insert into public.takt_profiles(id, email, display_name)
  values (new.id, coalesce(new.email, ''), safe_name)
  on conflict (id) do nothing;

  insert into public.takt_privacy_settings(user_id)
  values (new.id)
  on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists takt_on_auth_user_created on auth.users;
create trigger takt_on_auth_user_created
after insert on auth.users
for each row execute function public.takt_handle_new_user();

insert into public.takt_profiles(id, email, display_name)
select
  user_row.id,
  coalesce(user_row.email, ''),
  coalesce(nullif(left(trim(user_row.raw_user_meta_data ->> 'display_name'), 64), ''), 'Новый пользователь')
from auth.users user_row
on conflict (id) do nothing;

insert into public.takt_privacy_settings(user_id)
select id from public.takt_profiles
on conflict (user_id) do nothing;

alter table public.takt_profiles enable row level security;
alter table public.takt_chat_folders enable row level security;
alter table public.takt_conversations enable row level security;
alter table public.takt_conversation_members enable row level security;
alter table public.takt_messages enable row level security;
alter table public.takt_attachments enable row level security;
alter table public.takt_message_reactions enable row level security;
alter table public.takt_message_hidden enable row level security;
alter table public.takt_user_blocks enable row level security;
alter table public.takt_privacy_settings enable row level security;
alter table public.takt_typing_states enable row level security;
alter table public.takt_call_sessions enable row level security;
alter table public.takt_reports enable row level security;

drop policy if exists "takt profiles read" on public.takt_profiles;
create policy "takt profiles read" on public.takt_profiles
for select to authenticated using (true);
drop policy if exists "takt profiles update own" on public.takt_profiles;
create policy "takt profiles update own" on public.takt_profiles
for update to authenticated
using ((select auth.uid()) = id)
with check ((select auth.uid()) = id);

drop policy if exists "takt folders own" on public.takt_chat_folders;
create policy "takt folders own" on public.takt_chat_folders
for all to authenticated
using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);

drop policy if exists "takt conversations member read" on public.takt_conversations;
create policy "takt conversations member read" on public.takt_conversations
for select to authenticated using (public.takt_is_member(id));

drop policy if exists "takt members in shared conversation" on public.takt_conversation_members;
create policy "takt members in shared conversation" on public.takt_conversation_members
for select to authenticated using (public.takt_is_member(conversation_id));
drop policy if exists "takt member updates own settings" on public.takt_conversation_members;
create policy "takt member updates own settings" on public.takt_conversation_members
for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "takt messages member read" on public.takt_messages;
create policy "takt messages member read" on public.takt_messages
for select to authenticated using (public.takt_is_member(conversation_id));
drop policy if exists "takt messages member insert" on public.takt_messages;
create policy "takt messages member insert" on public.takt_messages
for insert to authenticated
with check ((select auth.uid()) = sender_id and public.takt_is_member(conversation_id));
drop policy if exists "takt messages sender update" on public.takt_messages;
create policy "takt messages sender update" on public.takt_messages
for update to authenticated
using ((select auth.uid()) = sender_id)
with check ((select auth.uid()) = sender_id);

drop policy if exists "takt attachments member read" on public.takt_attachments;
create policy "takt attachments member read" on public.takt_attachments
for select to authenticated using (public.takt_can_access_message(message_id));
drop policy if exists "takt attachments own insert" on public.takt_attachments;
create policy "takt attachments own insert" on public.takt_attachments
for insert to authenticated
with check ((select auth.uid()) = owner_id and public.takt_can_access_message(message_id));

drop policy if exists "takt reactions member read" on public.takt_message_reactions;
create policy "takt reactions member read" on public.takt_message_reactions
for select to authenticated using (public.takt_can_access_message(message_id));
drop policy if exists "takt reactions own write" on public.takt_message_reactions;
create policy "takt reactions own write" on public.takt_message_reactions
for all to authenticated
using ((select auth.uid()) = user_id and public.takt_can_access_message(message_id))
with check ((select auth.uid()) = user_id and public.takt_can_access_message(message_id));

drop policy if exists "takt hidden own" on public.takt_message_hidden;
create policy "takt hidden own" on public.takt_message_hidden
for all to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "takt blocks own" on public.takt_user_blocks;
create policy "takt blocks own" on public.takt_user_blocks
for all to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "takt privacy own" on public.takt_privacy_settings;
create policy "takt privacy own" on public.takt_privacy_settings
for all to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "takt typing member read" on public.takt_typing_states;
create policy "takt typing member read" on public.takt_typing_states
for select to authenticated using (public.takt_is_member(conversation_id));
drop policy if exists "takt typing own write" on public.takt_typing_states;
create policy "takt typing own write" on public.takt_typing_states
for all to authenticated
using ((select auth.uid()) = user_id and public.takt_is_member(conversation_id))
with check ((select auth.uid()) = user_id and public.takt_is_member(conversation_id));

drop policy if exists "takt calls member read" on public.takt_call_sessions;
create policy "takt calls member read" on public.takt_call_sessions
for select to authenticated using (public.takt_is_member(conversation_id));
drop policy if exists "takt calls starter write" on public.takt_call_sessions;
create policy "takt calls starter write" on public.takt_call_sessions
for all to authenticated
using ((select auth.uid()) = started_by and public.takt_is_member(conversation_id))
with check ((select auth.uid()) = started_by and public.takt_is_member(conversation_id));

drop policy if exists "takt reports own" on public.takt_reports;
create policy "takt reports own" on public.takt_reports
for all to authenticated
using ((select auth.uid()) = reporter_id)
with check ((select auth.uid()) = reporter_id);

revoke all on public.takt_profiles, public.takt_chat_folders, public.takt_conversations,
  public.takt_conversation_members, public.takt_messages, public.takt_attachments,
  public.takt_message_reactions, public.takt_message_hidden, public.takt_user_blocks,
  public.takt_privacy_settings, public.takt_typing_states, public.takt_call_sessions,
  public.takt_reports from anon;
grant select, update on public.takt_profiles to authenticated;
grant select, insert, update, delete on public.takt_chat_folders to authenticated;
grant select on public.takt_conversations to authenticated;
grant select, update on public.takt_conversation_members to authenticated;
grant select, insert, update on public.takt_messages to authenticated;
grant select, insert on public.takt_attachments to authenticated;
grant select, insert, delete on public.takt_message_reactions to authenticated;
grant select, insert, delete on public.takt_message_hidden to authenticated;
grant select, insert, delete on public.takt_user_blocks to authenticated;
grant select, insert, update on public.takt_privacy_settings to authenticated;
grant select, insert, update, delete on public.takt_typing_states to authenticated;
grant select, insert, update on public.takt_call_sessions to authenticated;
grant select, insert on public.takt_reports to authenticated;

revoke all on function public.takt_is_member(uuid) from public;
revoke all on function public.takt_can_access_message(uuid) from public;
revoke all on function public.takt_are_blocked(uuid) from public;
revoke all on function public.takt_handle_new_user() from public;
revoke all on function public.takt_touch_updated_at() from public;
grant execute on function public.takt_is_member(uuid) to authenticated;
grant execute on function public.takt_can_access_message(uuid) to authenticated;
grant execute on function public.takt_are_blocked(uuid) to authenticated;

commit;

