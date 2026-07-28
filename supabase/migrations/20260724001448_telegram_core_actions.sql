-- Messenger core actions: privacy-safe direct communication, correct delivery
-- state, folders and black-list read models.  All externally callable routines
-- are SECURITY DEFINER only because they compose tables protected by RLS.
begin;

-- Keep the new setting explicit.  "contacts" is intentionally fail-closed in
-- this schema: there is no contacts relationship table yet, so treating it as
-- "everyone" would silently violate the user's privacy choice.
alter table public.takt_privacy_settings
  add column if not exists allow_messages_from text not null default 'everyone';

do $$
begin
  alter table public.takt_privacy_settings
    add constraint takt_privacy_messages_scope
    check (allow_messages_from in ('everyone', 'contacts', 'nobody'));
exception
  when duplicate_object then null;
end;
$$;

create or replace function public.takt_message_json(p_message_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', message_row.id,
    'conversation_id', message_row.conversation_id,
    'sender_id', message_row.sender_id,
    'sender_name', profile_row.display_name,
    'sender_color', profile_row.avatar_color,
    'body', message_row.body,
    'kind', message_row.kind,
    'created_at', message_row.created_at,
    'edited_at', message_row.edited_at,
    'deleted_at', message_row.deleted_at,
    'is_pinned', message_row.is_pinned,
    'reply_to_id', message_row.reply_to_id,
    'reply_preview', (
      select jsonb_build_object(
        'id', reply_row.id,
        'body', left(reply_row.body, 160),
        'kind', reply_row.kind,
        'sender_name', reply_profile.display_name
      )
      from public.takt_messages reply_row
      join public.takt_profiles reply_profile on reply_profile.id = reply_row.sender_id
      where reply_row.id = message_row.reply_to_id
    ),
    'forwarded_from_id', message_row.forwarded_from_id,
    'attachment', (
      select jsonb_build_object(
        'id', attachment_row.id,
        'file_name', attachment_row.file_name,
        'mime_type', attachment_row.mime_type,
        'size_bytes', attachment_row.size_bytes,
        'duration_seconds', attachment_row.duration_seconds
      )
      from public.takt_attachments attachment_row
      where attachment_row.id = message_row.attachment_id
    ),
    'reactions', coalesce((
      select jsonb_agg(jsonb_build_object(
        'emoji', reaction_group.emoji,
        'count', reaction_group.reaction_count,
        'mine', reaction_group.mine
      ) order by reaction_group.emoji)
      from (
        select
          reaction_row.emoji,
          count(*)::integer as reaction_count,
          bool_or(reaction_row.user_id = (select auth.uid())) as mine
        from public.takt_message_reactions reaction_row
        where reaction_row.message_id = message_row.id
        group by reaction_row.emoji
      ) reaction_group
    ), '[]'::jsonb),
    'status', case
      when message_row.deleted_at is not null then 'deleted'
      when message_row.sender_id <> (select auth.uid()) then 'received'
      when not exists (
        select 1
        from public.takt_conversation_members recipient_row
        where recipient_row.conversation_id = message_row.conversation_id
          and recipient_row.user_id <> message_row.sender_id
      ) then 'sent'
      when not exists (
        select 1
        from public.takt_conversation_members recipient_row
        where recipient_row.conversation_id = message_row.conversation_id
          and recipient_row.user_id <> message_row.sender_id
          and (recipient_row.last_read_at is null or recipient_row.last_read_at < message_row.created_at)
      ) then 'read'
      when exists (
        select 1
        from public.takt_conversation_members recipient_row
        where recipient_row.conversation_id = message_row.conversation_id
          and recipient_row.user_id <> message_row.sender_id
          and recipient_row.last_read_at >= message_row.created_at
      ) then 'delivered'
      else 'sent'
    end
  )
  from public.takt_messages message_row
  join public.takt_profiles profile_row on profile_row.id = message_row.sender_id
  where message_row.id = p_message_id
    and public.takt_is_member(message_row.conversation_id);
$$;

create or replace function public.takt_open_direct_chat(p_other_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  conversation_id uuid;
  direct_key text;
begin
  if p_other_user_id is null or p_other_user_id = current_user_id then
    raise exception 'DIRECT_CHAT_INVALID' using errcode = '22023';
  end if;
  if not exists (select 1 from public.takt_profiles where id = p_other_user_id) then
    raise exception 'USER_NOT_FOUND' using errcode = '22023';
  end if;
  if public.takt_are_blocked(p_other_user_id) then
    raise exception 'USER_BLOCKED' using errcode = '42501';
  end if;

  direct_key := least(current_user_id::text, p_other_user_id::text) || ':' || greatest(current_user_id::text, p_other_user_id::text);
  insert into public.takt_conversations(kind, direct_key, created_by)
  values ('direct', direct_key, current_user_id)
  on conflict (direct_key) do update set updated_at = now()
  returning id into conversation_id;

  insert into public.takt_conversation_members(conversation_id, user_id, role)
  values
    (conversation_id, current_user_id, 'owner'),
    (conversation_id, p_other_user_id, 'member')
  on conflict (conversation_id, user_id) do nothing;

  return public.takt_conversation_json(conversation_id);
end;
$$;

create or replace function public.takt_send_message(
  p_conversation_id uuid,
  p_body text,
  p_reply_to_id uuid default null,
  p_forwarded_from_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  message_id uuid;
  other_user_id uuid;
  conversation_kind public.takt_conversation_kind;
begin
  if not public.takt_is_member(p_conversation_id) then
    raise exception 'CHAT_NOT_FOUND' using errcode = '42501';
  end if;
  if char_length(trim(coalesce(p_body, ''))) not between 1 and 4000 then
    raise exception 'MESSAGE_EMPTY_OR_TOO_LONG' using errcode = '22023';
  end if;
  select kind into conversation_kind from public.takt_conversations where id = p_conversation_id;
  if conversation_kind = 'direct' then
    select user_id into other_user_id
    from public.takt_conversation_members
    where conversation_id = p_conversation_id and user_id <> current_user_id
    limit 1;
    if other_user_id is not null and public.takt_are_blocked(other_user_id) then
      raise exception 'USER_BLOCKED' using errcode = '42501';
    end if;
    if exists (
      select 1
      from public.takt_privacy_settings privacy_row
      where privacy_row.user_id = other_user_id
        and privacy_row.allow_messages_from <> 'everyone'
    ) then
      raise exception 'MESSAGES_RESTRICTED' using errcode = '42501';
    end if;
  end if;
  if p_reply_to_id is not null and not exists (
    select 1 from public.takt_messages where id = p_reply_to_id and conversation_id = p_conversation_id
  ) then
    raise exception 'REPLY_NOT_FOUND' using errcode = '22023';
  end if;
  if p_forwarded_from_id is not null and not public.takt_can_access_message(p_forwarded_from_id) then
    raise exception 'FORWARD_NOT_FOUND' using errcode = '22023';
  end if;

  insert into public.takt_messages(conversation_id, sender_id, body, kind, reply_to_id, forwarded_from_id)
  values (p_conversation_id, current_user_id, trim(p_body), 'text', p_reply_to_id, p_forwarded_from_id)
  returning id into message_id;
  update public.takt_conversations set updated_at = now() where id = p_conversation_id;
  delete from public.takt_typing_states where conversation_id = p_conversation_id and user_id = current_user_id;
  return public.takt_message_json(message_id);
end;
$$;

create or replace function public.takt_send_attachment(
  p_conversation_id uuid,
  p_file_name text,
  p_mime_type text,
  p_base64 text,
  p_kind public.takt_message_kind,
  p_duration_seconds integer default null,
  p_caption text default ''
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  message_id uuid;
  new_attachment_id uuid;
  bytes_value bytea;
  other_user_id uuid;
  conversation_kind public.takt_conversation_kind;
begin
  if not public.takt_is_member(p_conversation_id) then
    raise exception 'CHAT_NOT_FOUND' using errcode = '42501';
  end if;
  select kind into conversation_kind from public.takt_conversations where id = p_conversation_id;
  if conversation_kind = 'direct' then
    select user_id into other_user_id
    from public.takt_conversation_members
    where conversation_id = p_conversation_id and user_id <> current_user_id
    limit 1;
    if other_user_id is not null and public.takt_are_blocked(other_user_id) then
      raise exception 'USER_BLOCKED' using errcode = '42501';
    end if;
    if exists (
      select 1
      from public.takt_privacy_settings privacy_row
      where privacy_row.user_id = other_user_id
        and privacy_row.allow_messages_from <> 'everyone'
    ) then
      raise exception 'MESSAGES_RESTRICTED' using errcode = '42501';
    end if;
  end if;
  if p_kind not in ('image', 'file', 'voice') then
    raise exception 'ATTACHMENT_KIND_INVALID' using errcode = '22023';
  end if;
  if char_length(trim(coalesce(p_file_name, ''))) not between 1 and 120
    or char_length(trim(coalesce(p_mime_type, ''))) not between 3 and 120 then
    raise exception 'ATTACHMENT_METADATA_INVALID' using errcode = '22023';
  end if;
  if char_length(coalesce(p_caption, '')) > 4000 then
    raise exception 'CAPTION_TOO_LONG' using errcode = '22023';
  end if;
  bytes_value := decode(p_base64, 'base64');
  if octet_length(bytes_value) not between 1 and 8388608 then
    raise exception 'ATTACHMENT_TOO_LARGE' using errcode = '22023';
  end if;
  if p_kind = 'voice' and coalesce(p_duration_seconds, 0) not between 1 and 300 then
    raise exception 'VOICE_DURATION_INVALID' using errcode = '22023';
  end if;

  insert into public.takt_messages(conversation_id, sender_id, body, kind)
  values (p_conversation_id, current_user_id, coalesce(p_caption, ''), p_kind)
  returning id into message_id;
  insert into public.takt_attachments(message_id, owner_id, file_name, mime_type, size_bytes, data, duration_seconds)
  values (
    message_id,
    current_user_id,
    trim(p_file_name),
    trim(p_mime_type),
    octet_length(bytes_value),
    bytes_value,
    case when p_kind = 'voice' then p_duration_seconds else null end
  ) returning id into new_attachment_id;
  update public.takt_messages set attachment_id = new_attachment_id where id = message_id;
  update public.takt_conversations set updated_at = now() where id = p_conversation_id;
  delete from public.takt_typing_states where conversation_id = p_conversation_id and user_id = current_user_id;
  return public.takt_message_json(message_id);
end;
$$;

create or replace function public.takt_update_chat_settings(
  p_conversation_id uuid,
  p_is_archived boolean default null,
  p_is_pinned boolean default null,
  p_folder_id uuid default null,
  p_muted_until timestamptz default null,
  p_draft_text text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  if p_folder_id is not null and not exists (
    select 1 from public.takt_chat_folders where id = p_folder_id and owner_id = current_user_id
  ) then
    raise exception 'FOLDER_NOT_FOUND' using errcode = '22023';
  end if;
  if char_length(coalesce(p_draft_text, '')) > 4000 then
    raise exception 'DRAFT_TOO_LONG' using errcode = '22023';
  end if;
  update public.takt_conversation_members
  set is_archived = coalesce(p_is_archived, is_archived),
      is_pinned = coalesce(p_is_pinned, is_pinned),
      folder_id = case when p_folder_id is null then folder_id else p_folder_id end,
      muted_until = coalesce(p_muted_until, muted_until),
      draft_text = coalesce(p_draft_text, draft_text)
  where conversation_id = p_conversation_id and user_id = current_user_id;
  if not found then
    raise exception 'CHAT_NOT_FOUND' using errcode = '42501';
  end if;
  return public.takt_conversation_json(p_conversation_id);
end;
$$;

create or replace function public.takt_patch_chat_settings(
  p_conversation_id uuid,
  p_settings jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  next_folder_id uuid;
  next_muted_until timestamptz;
  next_draft_text text;
begin
  if jsonb_typeof(p_settings) is distinct from 'object' or p_settings = '{}'::jsonb then
    raise exception 'SETTINGS_INVALID' using errcode = '22023';
  end if;
  if exists (
    select 1
    from jsonb_object_keys(p_settings) as settings_key(key_name)
    where key_name not in ('is_archived', 'is_pinned', 'folder_id', 'muted_until', 'draft_text')
  ) then
    raise exception 'SETTINGS_FIELD_INVALID' using errcode = '22023';
  end if;
  if (p_settings ? 'is_archived' and jsonb_typeof(p_settings -> 'is_archived') <> 'boolean')
    or (p_settings ? 'is_pinned' and jsonb_typeof(p_settings -> 'is_pinned') <> 'boolean')
    or (p_settings ? 'folder_id' and jsonb_typeof(p_settings -> 'folder_id') not in ('string', 'null'))
    or (p_settings ? 'muted_until' and jsonb_typeof(p_settings -> 'muted_until') not in ('string', 'null'))
    or (p_settings ? 'draft_text' and jsonb_typeof(p_settings -> 'draft_text') not in ('string', 'null')) then
    raise exception 'SETTINGS_FIELD_INVALID' using errcode = '22023';
  end if;

  if p_settings ? 'folder_id' and jsonb_typeof(p_settings -> 'folder_id') = 'string' then
    next_folder_id := (p_settings ->> 'folder_id')::uuid;
    if not exists (
      select 1
      from public.takt_chat_folders folder_row
      where folder_row.id = next_folder_id and folder_row.owner_id = current_user_id
    ) then
      raise exception 'FOLDER_NOT_FOUND' using errcode = '22023';
    end if;
  end if;
  if p_settings ? 'muted_until' and jsonb_typeof(p_settings -> 'muted_until') = 'string' then
    next_muted_until := (p_settings ->> 'muted_until')::timestamptz;
  end if;
  if p_settings ? 'draft_text' then
    next_draft_text := coalesce(p_settings ->> 'draft_text', '');
    if char_length(next_draft_text) > 4000 then
      raise exception 'DRAFT_TOO_LONG' using errcode = '22023';
    end if;
  end if;

  update public.takt_conversation_members
  set is_archived = case when p_settings ? 'is_archived' then (p_settings ->> 'is_archived')::boolean else is_archived end,
      is_pinned = case when p_settings ? 'is_pinned' then (p_settings ->> 'is_pinned')::boolean else is_pinned end,
      folder_id = case when p_settings ? 'folder_id' then next_folder_id else folder_id end,
      muted_until = case when p_settings ? 'muted_until' then next_muted_until else muted_until end,
      draft_text = case when p_settings ? 'draft_text' then next_draft_text else draft_text end
  where conversation_id = p_conversation_id and user_id = current_user_id;
  if not found then
    raise exception 'CHAT_NOT_FOUND' using errcode = '42501';
  end if;
  return public.takt_conversation_json(p_conversation_id);
end;
$$;

create or replace function public.takt_update_folder(
  p_folder_id uuid,
  p_name text,
  p_color text,
  p_position integer default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  result jsonb;
begin
  if char_length(trim(coalesce(p_name, ''))) not between 1 and 32
    or coalesce(p_color, '') !~ '^#[0-9A-Fa-f]{6}$'
    or (p_position is not null and p_position < 0) then
    raise exception 'FOLDER_INVALID' using errcode = '22023';
  end if;
  update public.takt_chat_folders
  set name = trim(p_name),
      color = upper(p_color),
      position = coalesce(p_position, position)
  where id = p_folder_id and owner_id = current_user_id
  returning jsonb_build_object('id', id, 'name', name, 'color', color, 'position', position) into result;
  if result is null then
    raise exception 'FOLDER_NOT_FOUND' using errcode = '42501';
  end if;
  return result;
end;
$$;

create or replace function public.takt_delete_folder(p_folder_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  delete from public.takt_chat_folders
  where id = p_folder_id and owner_id = current_user_id;
  if not found then
    raise exception 'FOLDER_NOT_FOUND' using errcode = '42501';
  end if;
end;
$$;

create or replace function public.takt_start_call(p_conversation_id uuid, p_is_video boolean default false)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  call_row public.takt_call_sessions;
begin
  if not public.takt_is_member(p_conversation_id) then
    raise exception 'CHAT_NOT_FOUND' using errcode = '42501';
  end if;
  if exists (
    select 1
    from public.takt_conversation_members member_row
    where member_row.conversation_id = p_conversation_id
      and member_row.user_id <> current_user_id
      and public.takt_are_blocked(member_row.user_id)
  ) then
    raise exception 'USER_BLOCKED' using errcode = '42501';
  end if;
  if exists (
    select 1
    from public.takt_conversation_members member_row
    left join public.takt_privacy_settings privacy_row on privacy_row.user_id = member_row.user_id
    where member_row.conversation_id = p_conversation_id
      and member_row.user_id <> current_user_id
      and coalesce(privacy_row.allow_calls_from, 'everyone') <> 'everyone'
  ) then
    raise exception 'CALLS_RESTRICTED' using errcode = '42501';
  end if;
  select * into call_row
  from public.takt_call_sessions
  where conversation_id = p_conversation_id and ended_at is null
  order by started_at desc
  limit 1;
  if call_row.id is null then
    insert into public.takt_call_sessions(conversation_id, started_by, is_video)
    values (p_conversation_id, current_user_id, p_is_video)
    returning * into call_row;
  end if;
  return jsonb_build_object(
    'id', call_row.id,
    'conversation_id', call_row.conversation_id,
    'started_by', call_row.started_by,
    'is_video', call_row.is_video,
    'started_at', call_row.started_at
  );
end;
$$;

create or replace function public.takt_get_privacy()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  result jsonb;
begin
  select jsonb_build_object(
    'show_avatar_to', privacy_row.show_avatar_to,
    'show_last_seen_to', privacy_row.show_last_seen_to,
    'allow_calls_from', privacy_row.allow_calls_from,
    'allow_group_invites_from', privacy_row.allow_group_invites_from,
    'allow_messages_from', privacy_row.allow_messages_from
  ) into result
  from public.takt_privacy_settings privacy_row
  where privacy_row.user_id = current_user_id;
  return coalesce(result, jsonb_build_object(
    'show_avatar_to', 'everyone',
    'show_last_seen_to', 'everyone',
    'allow_calls_from', 'everyone',
    'allow_group_invites_from', 'everyone',
    'allow_messages_from', 'everyone'
  ));
end;
$$;

create or replace function public.takt_update_privacy_settings(
  p_show_avatar_to text,
  p_show_last_seen_to text,
  p_allow_calls_from text,
  p_allow_group_invites_from text,
  p_allow_messages_from text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  result jsonb;
begin
  if coalesce(p_show_avatar_to, '') not in ('everyone', 'contacts', 'nobody')
    or coalesce(p_show_last_seen_to, '') not in ('everyone', 'contacts', 'nobody')
    or coalesce(p_allow_calls_from, '') not in ('everyone', 'contacts', 'nobody')
    or coalesce(p_allow_group_invites_from, '') not in ('everyone', 'contacts', 'nobody')
    or coalesce(p_allow_messages_from, '') not in ('everyone', 'contacts', 'nobody') then
    raise exception 'PRIVACY_SCOPE_INVALID' using errcode = '22023';
  end if;
  insert into public.takt_privacy_settings(
    user_id,
    show_avatar_to,
    show_last_seen_to,
    allow_calls_from,
    allow_group_invites_from,
    allow_messages_from,
    updated_at
  ) values (
    current_user_id,
    p_show_avatar_to,
    p_show_last_seen_to,
    p_allow_calls_from,
    p_allow_group_invites_from,
    p_allow_messages_from,
    now()
  )
  on conflict (user_id) do update
  set show_avatar_to = excluded.show_avatar_to,
      show_last_seen_to = excluded.show_last_seen_to,
      allow_calls_from = excluded.allow_calls_from,
      allow_group_invites_from = excluded.allow_group_invites_from,
      allow_messages_from = excluded.allow_messages_from,
      updated_at = now()
  returning jsonb_build_object(
    'show_avatar_to', show_avatar_to,
    'show_last_seen_to', show_last_seen_to,
    'allow_calls_from', allow_calls_from,
    'allow_group_invites_from', allow_group_invites_from,
    'allow_messages_from', allow_messages_from
  ) into result;
  return result;
end;
$$;

create or replace function public.takt_update_privacy(
  p_show_avatar_to text,
  p_show_last_seen_to text,
  p_allow_calls_from text,
  p_allow_group_invites_from text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  existing_allow_messages_from text;
begin
  select allow_messages_from into existing_allow_messages_from
  from public.takt_privacy_settings
  where user_id = current_user_id;
  return public.takt_update_privacy_settings(
    p_show_avatar_to,
    p_show_last_seen_to,
    p_allow_calls_from,
    p_allow_group_invites_from,
    coalesce(existing_allow_messages_from, 'everyone')
  );
end;
$$;

create or replace function public.takt_list_blocked_users()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', profile_row.id,
      'display_name', profile_row.display_name,
      'username', profile_row.username,
      'about', profile_row.about,
      'avatar_color', profile_row.avatar_color,
      'blocked_at', block_row.created_at
    ) order by block_row.created_at desc)
    from public.takt_user_blocks block_row
    join public.takt_profiles profile_row on profile_row.id = block_row.blocked_user_id
    where block_row.user_id = current_user_id
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.takt_message_json(uuid) from public;
revoke all on function public.takt_message_json(uuid) from anon;
revoke all on function public.takt_open_direct_chat(uuid) from public;
revoke all on function public.takt_open_direct_chat(uuid) from anon;
revoke all on function public.takt_send_message(uuid, text, uuid, uuid) from public;
revoke all on function public.takt_send_message(uuid, text, uuid, uuid) from anon;
revoke all on function public.takt_send_attachment(uuid, text, text, text, public.takt_message_kind, integer, text) from public;
revoke all on function public.takt_send_attachment(uuid, text, text, text, public.takt_message_kind, integer, text) from anon;
revoke all on function public.takt_update_chat_settings(uuid, boolean, boolean, uuid, timestamptz, text) from public;
revoke all on function public.takt_update_chat_settings(uuid, boolean, boolean, uuid, timestamptz, text) from anon;
revoke all on function public.takt_patch_chat_settings(uuid, jsonb) from public;
revoke all on function public.takt_patch_chat_settings(uuid, jsonb) from anon;
revoke all on function public.takt_update_folder(uuid, text, text, integer) from public;
revoke all on function public.takt_update_folder(uuid, text, text, integer) from anon;
revoke all on function public.takt_delete_folder(uuid) from public;
revoke all on function public.takt_delete_folder(uuid) from anon;
revoke all on function public.takt_start_call(uuid, boolean) from public;
revoke all on function public.takt_start_call(uuid, boolean) from anon;
revoke all on function public.takt_get_privacy() from public;
revoke all on function public.takt_get_privacy() from anon;
revoke all on function public.takt_update_privacy_settings(text, text, text, text, text) from public;
revoke all on function public.takt_update_privacy_settings(text, text, text, text, text) from anon;
revoke all on function public.takt_update_privacy(text, text, text, text) from public;
revoke all on function public.takt_update_privacy(text, text, text, text) from anon;
revoke all on function public.takt_list_blocked_users() from public;
revoke all on function public.takt_list_blocked_users() from anon;

grant execute on function public.takt_message_json(uuid) to authenticated;
grant execute on function public.takt_open_direct_chat(uuid) to authenticated;
grant execute on function public.takt_send_message(uuid, text, uuid, uuid) to authenticated;
grant execute on function public.takt_send_attachment(uuid, text, text, text, public.takt_message_kind, integer, text) to authenticated;
grant execute on function public.takt_update_chat_settings(uuid, boolean, boolean, uuid, timestamptz, text) to authenticated;
grant execute on function public.takt_patch_chat_settings(uuid, jsonb) to authenticated;
grant execute on function public.takt_update_folder(uuid, text, text, integer) to authenticated;
grant execute on function public.takt_delete_folder(uuid) to authenticated;
grant execute on function public.takt_start_call(uuid, boolean) to authenticated;
grant execute on function public.takt_get_privacy() to authenticated;
grant execute on function public.takt_update_privacy_settings(text, text, text, text, text) to authenticated;
grant execute on function public.takt_update_privacy(text, text, text, text) to authenticated;
grant execute on function public.takt_list_blocked_users() to authenticated;

commit;
