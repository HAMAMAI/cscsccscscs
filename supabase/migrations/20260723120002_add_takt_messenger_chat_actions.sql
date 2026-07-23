begin;
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

create or replace function public.takt_create_group(
  p_title text,
  p_member_ids uuid[],
  p_avatar_color text default '#8C78FF'
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  conversation_id uuid;
  member_ids uuid[];
  member_id uuid;
begin
  if char_length(trim(p_title)) not between 2 and 64 then
    raise exception 'GROUP_TITLE_INVALID' using errcode = '22023';
  end if;
  if coalesce(p_avatar_color, '') !~ '^#[0-9A-Fa-f]{6}$' then
    raise exception 'AVATAR_COLOR_INVALID' using errcode = '22023';
  end if;

  select array_agg(distinct candidate_id)
  into member_ids
  from (
    select unnest(array_append(coalesce(p_member_ids, '{}'::uuid[]), current_user_id)) as candidate_id
  ) candidates
  join public.takt_profiles profile_row on profile_row.id = candidates.candidate_id;

  if coalesce(cardinality(member_ids), 0) < 2 then
    raise exception 'GROUP_NEEDS_MEMBER' using errcode = '22023';
  end if;

  if exists (
    select 1
    from unnest(member_ids) as other_user_id
    where other_user_id <> current_user_id and public.takt_are_blocked(other_user_id)
  ) then
    raise exception 'USER_BLOCKED' using errcode = '42501';
  end if;

  insert into public.takt_conversations(kind, title, avatar_color, created_by)
  values ('group', trim(p_title), upper(p_avatar_color), current_user_id)
  returning id into conversation_id;

  foreach member_id in array member_ids loop
    insert into public.takt_conversation_members(conversation_id, user_id, role)
    values (conversation_id, member_id, case when member_id = current_user_id then 'owner'::public.takt_member_role else 'member'::public.takt_member_role end);
  end loop;

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
begin
  if not public.takt_is_member(p_conversation_id) then
    raise exception 'CHAT_NOT_FOUND' using errcode = '42501';
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

create or replace function public.takt_get_attachment(p_attachment_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  result jsonb;
begin
  select jsonb_build_object(
    'id', attachment_row.id,
    'file_name', attachment_row.file_name,
    'mime_type', attachment_row.mime_type,
    'base64', encode(attachment_row.data, 'base64'),
    'duration_seconds', attachment_row.duration_seconds
  ) into result
  from public.takt_attachments attachment_row
  join public.takt_messages message_row on message_row.id = attachment_row.message_id
  where attachment_row.id = p_attachment_id
    and public.takt_is_member(message_row.conversation_id);
  if result is null then
    raise exception 'ATTACHMENT_NOT_FOUND' using errcode = '42501';
  end if;
  return result;
end;
$$;

create or replace function public.takt_edit_message(p_message_id uuid, p_body text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  if char_length(trim(coalesce(p_body, ''))) not between 1 and 4000 then
    raise exception 'MESSAGE_EMPTY_OR_TOO_LONG' using errcode = '22023';
  end if;
  update public.takt_messages
  set body = trim(p_body), edited_at = now()
  where id = p_message_id and sender_id = current_user_id and deleted_at is null;
  if not found then
    raise exception 'MESSAGE_EDIT_DENIED' using errcode = '42501';
  end if;
  return public.takt_message_json(p_message_id);
end;
$$;

create or replace function public.takt_delete_message(p_message_id uuid, p_for_everyone boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  if p_for_everyone then
    update public.takt_messages
    set body = 'Сообщение удалено', deleted_at = now(), edited_at = null, attachment_id = null
    where id = p_message_id and sender_id = current_user_id;
    if not found then
      raise exception 'MESSAGE_DELETE_DENIED' using errcode = '42501';
    end if;
  else
    if not public.takt_can_access_message(p_message_id) then
      raise exception 'MESSAGE_NOT_FOUND' using errcode = '42501';
    end if;
    insert into public.takt_message_hidden(message_id, user_id)
    values (p_message_id, current_user_id)
    on conflict (message_id, user_id) do nothing;
  end if;
end;
$$;

create or replace function public.takt_toggle_reaction(p_message_id uuid, p_emoji text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  if char_length(trim(coalesce(p_emoji, ''))) not between 1 and 16 then
    raise exception 'REACTION_INVALID' using errcode = '22023';
  end if;
  if not public.takt_can_access_message(p_message_id) then
    raise exception 'MESSAGE_NOT_FOUND' using errcode = '42501';
  end if;
  if exists (
    select 1 from public.takt_message_reactions
    where message_id = p_message_id and user_id = current_user_id and emoji = trim(p_emoji)
  ) then
    delete from public.takt_message_reactions
    where message_id = p_message_id and user_id = current_user_id and emoji = trim(p_emoji);
  else
    insert into public.takt_message_reactions(message_id, user_id, emoji)
    values (p_message_id, current_user_id, trim(p_emoji));
  end if;
  return public.takt_message_json(p_message_id);
end;
$$;

revoke all on function public.takt_open_direct_chat(uuid) from public;
revoke all on function public.takt_create_group(text, uuid[], text) from public;
revoke all on function public.takt_send_message(uuid, text, uuid, uuid) from public;
revoke all on function public.takt_send_attachment(uuid, text, text, text, public.takt_message_kind, integer, text) from public;
revoke all on function public.takt_get_attachment(uuid) from public;
revoke all on function public.takt_edit_message(uuid, text) from public;
revoke all on function public.takt_delete_message(uuid, boolean) from public;
revoke all on function public.takt_toggle_reaction(uuid, text) from public;
grant execute on function public.takt_open_direct_chat(uuid) to authenticated;
grant execute on function public.takt_create_group(text, uuid[], text) to authenticated;
grant execute on function public.takt_send_message(uuid, text, uuid, uuid) to authenticated;
grant execute on function public.takt_send_attachment(uuid, text, text, text, public.takt_message_kind, integer, text) to authenticated;
grant execute on function public.takt_get_attachment(uuid) to authenticated;
grant execute on function public.takt_edit_message(uuid, text) to authenticated;
grant execute on function public.takt_delete_message(uuid, boolean) to authenticated;
grant execute on function public.takt_toggle_reaction(uuid, text) to authenticated;

commit;

