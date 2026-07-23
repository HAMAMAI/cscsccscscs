begin;
create or replace function public.takt_mark_read(p_conversation_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  update public.takt_conversation_members
  set last_read_at = greatest(last_read_at, now())
  where conversation_id = p_conversation_id and user_id = current_user_id;
  if not found then
    raise exception 'CHAT_NOT_FOUND' using errcode = '42501';
  end if;
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
      muted_until = p_muted_until,
      draft_text = coalesce(p_draft_text, draft_text)
  where conversation_id = p_conversation_id and user_id = current_user_id;
  if not found then
    raise exception 'CHAT_NOT_FOUND' using errcode = '42501';
  end if;
  return public.takt_conversation_json(p_conversation_id);
end;
$$;

create or replace function public.takt_create_folder(p_name text, p_color text default '#8C78FF')
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  folder_row public.takt_chat_folders;
begin
  if char_length(trim(coalesce(p_name, ''))) not between 1 and 32 or coalesce(p_color, '') !~ '^#[0-9A-Fa-f]{6}$' then
    raise exception 'FOLDER_INVALID' using errcode = '22023';
  end if;
  insert into public.takt_chat_folders(owner_id, name, color, position)
  values (
    current_user_id,
    trim(p_name),
    upper(p_color),
    coalesce((select max(position) + 1 from public.takt_chat_folders where owner_id = current_user_id), 0)
  ) returning * into folder_row;
  return jsonb_build_object('id', folder_row.id, 'name', folder_row.name, 'color', folder_row.color, 'position', folder_row.position);
end;
$$;

create or replace function public.takt_set_typing(p_conversation_id uuid, p_mode text default 'typing')
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  if not public.takt_is_member(p_conversation_id) then
    raise exception 'CHAT_NOT_FOUND' using errcode = '42501';
  end if;
  if p_mode not in ('typing', 'recording', 'uploading') then
    raise exception 'TYPING_MODE_INVALID' using errcode = '22023';
  end if;
  insert into public.takt_typing_states(conversation_id, user_id, mode, expires_at)
  values (p_conversation_id, current_user_id, p_mode, now() + interval '12 seconds')
  on conflict (conversation_id, user_id) do update
  set mode = excluded.mode, expires_at = excluded.expires_at, updated_at = now();
end;
$$;

create or replace function public.takt_set_presence(p_online boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  update public.takt_profiles
  set is_online = p_online, last_seen_at = now()
  where id = current_user_id;
end;
$$;

create or replace function public.takt_toggle_block(p_other_user_id uuid, p_blocked boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  if p_other_user_id is null or p_other_user_id = current_user_id then
    raise exception 'BLOCK_INVALID' using errcode = '22023';
  end if;
  if p_blocked then
    insert into public.takt_user_blocks(user_id, blocked_user_id)
    values (current_user_id, p_other_user_id)
    on conflict (user_id, blocked_user_id) do nothing;
  else
    delete from public.takt_user_blocks
    where user_id = current_user_id and blocked_user_id = p_other_user_id;
  end if;
end;
$$;

create or replace function public.takt_submit_report(
  p_target_user_id uuid default null,
  p_target_message_id uuid default null,
  p_reason text default ''
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  if char_length(trim(coalesce(p_reason, ''))) not between 3 and 500
    or (p_target_user_id is null and p_target_message_id is null) then
    raise exception 'REPORT_INVALID' using errcode = '22023';
  end if;
  if p_target_message_id is not null and not public.takt_can_access_message(p_target_message_id) then
    raise exception 'MESSAGE_NOT_FOUND' using errcode = '42501';
  end if;
  insert into public.takt_reports(reporter_id, target_user_id, target_message_id, reason)
  values (current_user_id, p_target_user_id, p_target_message_id, trim(p_reason));
end;
$$;

create or replace function public.takt_search_messages(p_query text, p_conversation_id uuid default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  normalized_query text := trim(p_query);
begin
  if char_length(normalized_query) < 2 then
    return '[]'::jsonb;
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'conversation_id', result_row.conversation_id,
      'message', public.takt_message_json(result_row.id)
    ) order by result_row.created_at desc)
    from (
      select message_row.id, message_row.conversation_id, message_row.created_at
      from public.takt_messages message_row
      where public.takt_is_member(message_row.conversation_id)
        and (p_conversation_id is null or message_row.conversation_id = p_conversation_id)
        and message_row.body ilike '%' || normalized_query || '%'
        and not exists (
          select 1 from public.takt_message_hidden hidden_row
          where hidden_row.message_id = message_row.id and hidden_row.user_id = current_user_id
        )
      order by message_row.created_at desc
      limit 100
    ) result_row
  ), '[]'::jsonb);
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

create or replace function public.takt_end_call(p_call_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
begin
  update public.takt_call_sessions call_row
  set ended_at = now()
  where call_row.id = p_call_id
    and call_row.ended_at is null
    and public.takt_is_member(call_row.conversation_id);
  if not found then
    raise exception 'CALL_NOT_FOUND' using errcode = '42501';
  end if;
end;
$$;

create or replace function public.takt_call_token_context(p_call_id uuid)
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
    'call_id', call_row.id,
    'conversation_id', call_row.conversation_id,
    'room_name', 'takt-call-' || call_row.id::text,
    'identity', current_user_id::text,
    'display_name', profile_row.display_name,
    'is_video', call_row.is_video
  ) into result
  from public.takt_call_sessions call_row
  join public.takt_profiles profile_row on profile_row.id = current_user_id
  where call_row.id = p_call_id
    and call_row.ended_at is null
    and public.takt_is_member(call_row.conversation_id);
  if result is null then
    raise exception 'CALL_NOT_FOUND' using errcode = '42501';
  end if;
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
  result jsonb;
begin
  if p_show_avatar_to not in ('everyone', 'contacts', 'nobody')
    or p_show_last_seen_to not in ('everyone', 'contacts', 'nobody')
    or p_allow_calls_from not in ('everyone', 'contacts', 'nobody')
    or p_allow_group_invites_from not in ('everyone', 'contacts', 'nobody') then
    raise exception 'PRIVACY_SCOPE_INVALID' using errcode = '22023';
  end if;
  update public.takt_privacy_settings
  set show_avatar_to = p_show_avatar_to,
      show_last_seen_to = p_show_last_seen_to,
      allow_calls_from = p_allow_calls_from,
      allow_group_invites_from = p_allow_group_invites_from,
      updated_at = now()
  where user_id = current_user_id
  returning jsonb_build_object(
    'show_avatar_to', show_avatar_to,
    'show_last_seen_to', show_last_seen_to,
    'allow_calls_from', allow_calls_from,
    'allow_group_invites_from', allow_group_invites_from
  ) into result;
  return result;
end;
$$;

revoke all on function public.takt_mark_read(uuid) from public;
revoke all on function public.takt_update_chat_settings(uuid, boolean, boolean, uuid, timestamptz, text) from public;
revoke all on function public.takt_create_folder(text, text) from public;
revoke all on function public.takt_set_typing(uuid, text) from public;
revoke all on function public.takt_set_presence(boolean) from public;
revoke all on function public.takt_toggle_block(uuid, boolean) from public;
revoke all on function public.takt_submit_report(uuid, uuid, text) from public;
revoke all on function public.takt_search_messages(text, uuid) from public;
revoke all on function public.takt_start_call(uuid, boolean) from public;
revoke all on function public.takt_end_call(uuid) from public;
revoke all on function public.takt_call_token_context(uuid) from public;
revoke all on function public.takt_update_privacy(text, text, text, text) from public;
grant execute on function public.takt_mark_read(uuid) to authenticated;
grant execute on function public.takt_update_chat_settings(uuid, boolean, boolean, uuid, timestamptz, text) to authenticated;
grant execute on function public.takt_create_folder(text, text) to authenticated;
grant execute on function public.takt_set_typing(uuid, text) to authenticated;
grant execute on function public.takt_set_presence(boolean) to authenticated;
grant execute on function public.takt_toggle_block(uuid, boolean) to authenticated;
grant execute on function public.takt_submit_report(uuid, uuid, text) to authenticated;
grant execute on function public.takt_search_messages(text, uuid) to authenticated;
grant execute on function public.takt_start_call(uuid, boolean) to authenticated;
grant execute on function public.takt_end_call(uuid) to authenticated;
grant execute on function public.takt_call_token_context(uuid) to authenticated;
grant execute on function public.takt_update_privacy(text, text, text, text) to authenticated;

commit;

