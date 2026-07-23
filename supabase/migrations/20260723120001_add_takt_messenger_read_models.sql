begin;
create or replace function public.takt_require_user_id()
returns uuid
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
begin
  if current_user_id is null then
    raise exception 'AUTH_REQUIRED' using errcode = '28000';
  end if;
  return current_user_id;
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
      when (select count(*) from public.takt_conversation_members where conversation_id = message_row.conversation_id) <= 1 then 'sent'
      when (select count(*) from public.takt_conversation_members where conversation_id = message_row.conversation_id and last_read_at >= message_row.created_at)
        >= (select count(*) from public.takt_conversation_members where conversation_id = message_row.conversation_id) then 'read'
      when (select count(*) from public.takt_conversation_members where conversation_id = message_row.conversation_id and last_read_at >= message_row.created_at) > 1 then 'delivered'
      else 'sent'
    end
  )
  from public.takt_messages message_row
  join public.takt_profiles profile_row on profile_row.id = message_row.sender_id
  where message_row.id = p_message_id
    and public.takt_is_member(message_row.conversation_id);
$$;

create or replace function public.takt_conversation_json(p_conversation_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  conversation_row public.takt_conversations;
  current_member public.takt_conversation_members;
  result jsonb;
begin
  if not public.takt_is_member(p_conversation_id) then
    raise exception 'CHAT_NOT_FOUND' using errcode = '42501';
  end if;

  select * into conversation_row from public.takt_conversations where id = p_conversation_id;
  select * into current_member
  from public.takt_conversation_members
  where conversation_id = p_conversation_id and user_id = current_user_id;

  select jsonb_build_object(
    'id', conversation_row.id,
    'kind', conversation_row.kind,
    'title', case when conversation_row.kind = 'direct' then coalesce((
      select profile_row.display_name
      from public.takt_conversation_members member_row
      join public.takt_profiles profile_row on profile_row.id = member_row.user_id
      where member_row.conversation_id = conversation_row.id and member_row.user_id <> current_user_id
      limit 1
    ), 'Личный чат') else conversation_row.title end,
    'avatar_color', case when conversation_row.kind = 'direct' then coalesce((
      select profile_row.avatar_color
      from public.takt_conversation_members member_row
      join public.takt_profiles profile_row on profile_row.id = member_row.user_id
      where member_row.conversation_id = conversation_row.id and member_row.user_id <> current_user_id
      limit 1
    ), conversation_row.avatar_color) else conversation_row.avatar_color end,
    'created_at', conversation_row.created_at,
    'settings', jsonb_build_object(
      'is_archived', current_member.is_archived,
      'is_pinned', current_member.is_pinned,
      'folder_id', current_member.folder_id,
      'muted_until', current_member.muted_until,
      'draft_text', current_member.draft_text
    ),
    'members', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', profile_row.id,
        'display_name', profile_row.display_name,
        'username', profile_row.username,
        'avatar_color', profile_row.avatar_color,
        'role', member_row.role,
        'is_online', profile_row.is_online and profile_row.last_seen_at > now() - interval '5 minutes',
        'last_seen_at', profile_row.last_seen_at
      ) order by member_row.role, profile_row.display_name)
      from public.takt_conversation_members member_row
      join public.takt_profiles profile_row on profile_row.id = member_row.user_id
      where member_row.conversation_id = conversation_row.id
    ), '[]'::jsonb),
    'messages', coalesce((
      select jsonb_agg(message_payload.payload order by message_payload.created_at)
      from (
        select message_row.created_at, public.takt_message_json(message_row.id) as payload
        from public.takt_messages message_row
        where message_row.conversation_id = conversation_row.id
          and not exists (
            select 1 from public.takt_message_hidden hidden_row
            where hidden_row.message_id = message_row.id and hidden_row.user_id = current_user_id
          )
        order by message_row.created_at desc
        limit 100
      ) message_payload
    ), '[]'::jsonb),
    'typing', coalesce((
      select jsonb_agg(jsonb_build_object(
        'user_id', typing_row.user_id,
        'display_name', profile_row.display_name,
        'mode', typing_row.mode
      ))
      from public.takt_typing_states typing_row
      join public.takt_profiles profile_row on profile_row.id = typing_row.user_id
      where typing_row.conversation_id = conversation_row.id
        and typing_row.user_id <> current_user_id
        and typing_row.expires_at > now()
    ), '[]'::jsonb),
    'active_call', (
      select jsonb_build_object(
        'id', call_row.id,
        'started_by', call_row.started_by,
        'started_by_name', profile_row.display_name,
        'is_video', call_row.is_video,
        'started_at', call_row.started_at
      )
      from public.takt_call_sessions call_row
      join public.takt_profiles profile_row on profile_row.id = call_row.started_by
      where call_row.conversation_id = conversation_row.id and call_row.ended_at is null
      order by call_row.started_at desc
      limit 1
    )
  ) into result;

  return result;
end;
$$;

create or replace function public.takt_list_conversations()
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
  select coalesce(jsonb_agg(summary_row.payload order by summary_row.is_pinned desc, summary_row.sort_at desc), '[]'::jsonb)
  into result
  from (
    select
      member_row.is_pinned,
      coalesce(last_message.created_at, conversation_row.created_at) as sort_at,
      jsonb_build_object(
        'id', conversation_row.id,
        'kind', conversation_row.kind,
        'title', case when conversation_row.kind = 'direct' then coalesce((
          select profile_row.display_name
          from public.takt_conversation_members other_member
          join public.takt_profiles profile_row on profile_row.id = other_member.user_id
          where other_member.conversation_id = conversation_row.id and other_member.user_id <> current_user_id
          limit 1
        ), 'Личный чат') else conversation_row.title end,
        'avatar_color', case when conversation_row.kind = 'direct' then coalesce((
          select profile_row.avatar_color
          from public.takt_conversation_members other_member
          join public.takt_profiles profile_row on profile_row.id = other_member.user_id
          where other_member.conversation_id = conversation_row.id and other_member.user_id <> current_user_id
          limit 1
        ), conversation_row.avatar_color) else conversation_row.avatar_color end,
        'is_archived', member_row.is_archived,
        'is_pinned', member_row.is_pinned,
        'folder_id', member_row.folder_id,
        'muted_until', member_row.muted_until,
        'draft_text', member_row.draft_text,
        'updated_at', coalesce(last_message.created_at, conversation_row.created_at),
        'last_message', case when last_message.id is null then null else public.takt_message_json(last_message.id) end,
        'unread_count', (
          select count(*)::integer
          from public.takt_messages unread_message
          where unread_message.conversation_id = conversation_row.id
            and unread_message.sender_id <> current_user_id
            and unread_message.created_at > member_row.last_read_at
            and unread_message.deleted_at is null
            and not exists (
              select 1 from public.takt_message_hidden hidden_row
              where hidden_row.message_id = unread_message.id and hidden_row.user_id = current_user_id
            )
        )
      ) as payload
    from public.takt_conversation_members member_row
    join public.takt_conversations conversation_row on conversation_row.id = member_row.conversation_id
    left join lateral (
      select last_message_row.id, last_message_row.created_at
      from public.takt_messages last_message_row
      where last_message_row.conversation_id = conversation_row.id
        and not exists (
          select 1 from public.takt_message_hidden hidden_row
          where hidden_row.message_id = last_message_row.id and hidden_row.user_id = current_user_id
        )
      order by last_message_row.created_at desc
      limit 1
    ) last_message on true
    where member_row.user_id = current_user_id
  ) summary_row;
  return result;
end;
$$;

create or replace function public.takt_bootstrap()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  result jsonb;
begin
  update public.takt_profiles
  set is_online = true, last_seen_at = now()
  where id = current_user_id;

  select jsonb_build_object(
    'profile', jsonb_build_object(
      'id', profile_row.id,
      'email', profile_row.email,
      'username', profile_row.username,
      'display_name', profile_row.display_name,
      'about', profile_row.about,
      'avatar_color', profile_row.avatar_color,
      'last_seen_at', profile_row.last_seen_at
    ),
    'conversations', public.takt_list_conversations(),
    'folders', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', folder_row.id,
        'name', folder_row.name,
        'color', folder_row.color,
        'position', folder_row.position
      ) order by folder_row.position, folder_row.name)
      from public.takt_chat_folders folder_row
      where folder_row.owner_id = current_user_id
    ), '[]'::jsonb),
    'calls', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', call_row.id,
        'conversation_id', call_row.conversation_id,
        'is_video', call_row.is_video,
        'started_at', call_row.started_at,
        'ended_at', call_row.ended_at,
        'started_by', call_row.started_by
      ) order by call_row.started_at desc)
      from public.takt_call_sessions call_row
      where public.takt_is_member(call_row.conversation_id)
      limit 100
    ), '[]'::jsonb)
  ) into result
  from public.takt_profiles profile_row
  where profile_row.id = current_user_id;

  return result;
end;
$$;

create or replace function public.takt_get_conversation(p_conversation_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform public.takt_require_user_id();
  return public.takt_conversation_json(p_conversation_id);
end;
$$;

create or replace function public.takt_update_profile(
  p_display_name text,
  p_username text,
  p_about text,
  p_avatar_color text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  next_username text := lower(nullif(trim(replace(p_username, '@', '')), ''));
  result jsonb;
begin
  if char_length(trim(p_display_name)) not between 2 and 64 then
    raise exception 'DISPLAY_NAME_INVALID' using errcode = '22023';
  end if;
  if next_username is not null and next_username !~ '^[a-z0-9_]{5,32}$' then
    raise exception 'USERNAME_INVALID' using errcode = '22023';
  end if;
  if char_length(coalesce(p_about, '')) > 160 then
    raise exception 'ABOUT_TOO_LONG' using errcode = '22023';
  end if;
  if coalesce(p_avatar_color, '') !~ '^#[0-9A-Fa-f]{6}$' then
    raise exception 'AVATAR_COLOR_INVALID' using errcode = '22023';
  end if;

  update public.takt_profiles
  set display_name = trim(p_display_name),
      username = next_username,
      about = coalesce(p_about, ''),
      avatar_color = upper(p_avatar_color),
      last_seen_at = now()
  where id = current_user_id;

  select jsonb_build_object(
    'id', id,
    'email', email,
    'username', username,
    'display_name', display_name,
    'about', about,
    'avatar_color', avatar_color,
    'last_seen_at', last_seen_at
  ) into result
  from public.takt_profiles
  where id = current_user_id;
  return result;
exception
  when unique_violation then
    raise exception 'USERNAME_TAKEN' using errcode = '23505';
end;
$$;

create or replace function public.takt_search_people(p_query text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  normalized_query text := lower(trim(replace(p_query, '@', '')));
begin
  if char_length(normalized_query) < 2 then
    return '[]'::jsonb;
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', profile_row.id,
      'username', profile_row.username,
      'display_name', profile_row.display_name,
      'about', profile_row.about,
      'avatar_color', profile_row.avatar_color,
      'is_online', profile_row.is_online and profile_row.last_seen_at > now() - interval '5 minutes',
      'last_seen_at', profile_row.last_seen_at
    ) order by profile_row.username nulls last, profile_row.display_name)
    from public.takt_profiles profile_row
    where profile_row.id <> current_user_id
      and (profile_row.username ilike normalized_query || '%' or profile_row.display_name ilike '%' || normalized_query || '%')
      and not exists (
        select 1
        from public.takt_user_blocks block_row
        where (block_row.user_id = current_user_id and block_row.blocked_user_id = profile_row.id)
           or (block_row.user_id = profile_row.id and block_row.blocked_user_id = current_user_id)
      )
    limit 30
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.takt_require_user_id() from public;
revoke all on function public.takt_message_json(uuid) from public;
revoke all on function public.takt_conversation_json(uuid) from public;
revoke all on function public.takt_list_conversations() from public;
revoke all on function public.takt_bootstrap() from public;
revoke all on function public.takt_get_conversation(uuid) from public;
revoke all on function public.takt_update_profile(text, text, text, text) from public;
revoke all on function public.takt_search_people(text) from public;
grant execute on function public.takt_require_user_id() to authenticated;
grant execute on function public.takt_message_json(uuid) to authenticated;
grant execute on function public.takt_conversation_json(uuid) to authenticated;
grant execute on function public.takt_list_conversations() to authenticated;
grant execute on function public.takt_bootstrap() to authenticated;
grant execute on function public.takt_get_conversation(uuid) to authenticated;
grant execute on function public.takt_update_profile(text, text, text, text) to authenticated;
grant execute on function public.takt_search_people(text) to authenticated;

commit;

