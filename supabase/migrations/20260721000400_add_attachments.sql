create table public.attachments (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms(id) on delete cascade,
  participant_id uuid not null references public.participants(id) on delete cascade,
  file_name text not null check (char_length(file_name) between 1 and 120),
  mime_type text not null check (char_length(mime_type) between 3 and 120),
  size_bytes integer not null check (size_bytes between 1 and 8388608),
  data bytea not null check (octet_length(data) between 1 and 8388608),
  duration_seconds integer check (duration_seconds between 1 and 300),
  created_at timestamptz not null default now(),
  constraint attachment_size_matches check (size_bytes = octet_length(data))
);

alter table public.messages
  add column kind text not null default 'text'
    check (kind in ('text', 'image', 'file', 'audio')),
  add column attachment_id uuid references public.attachments(id) on delete set null;

alter table public.messages drop constraint messages_body_check;
alter table public.messages add constraint messages_content_check check (
  (kind = 'text' and attachment_id is null and char_length(body) between 1 and 2000)
  or
  (kind in ('image', 'file', 'audio') and attachment_id is not null and char_length(body) between 1 and 2000)
);

create index attachments_room_created_idx on public.attachments(room_id, created_at desc);
create index attachments_participant_id_idx on public.attachments(participant_id);
create index messages_attachment_id_idx on public.messages(attachment_id);

alter table public.attachments enable row level security;
create policy "deny direct attachment access" on public.attachments
  for all to anon, authenticated using (false) with check (false);
revoke all on table public.attachments from anon, authenticated;

create or replace function public.get_room_state(
  p_invite_code text,
  p_participant_id uuid,
  p_token text
)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, extensions
as $$
declare
  v_participant public.participants%rowtype;
  v_room public.rooms%rowtype;
  v_result jsonb;
begin
  select * into v_participant
  from public.participants
  where participants.id = p_participant_id
    and participants.participant_key_hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if not found then raise exception 'invalid participant token' using errcode = '28000'; end if;

  select * into v_room from public.rooms
  where rooms.id = v_participant.room_id and rooms.invite_code = upper(trim(p_invite_code));
  if not found then raise exception 'room not found' using errcode = 'P0002'; end if;

  update public.participants set last_seen_at = now() where id = v_participant.id;

  select jsonb_build_object(
    'room', jsonb_build_object('id', v_room.id, 'name', v_room.name, 'invite_code', v_room.invite_code, 'created_at', v_room.created_at),
    'me', jsonb_build_object('id', v_participant.id, 'display_name', v_participant.display_name, 'color', v_participant.color, 'is_owner', v_participant.is_owner),
    'participants', coalesce((
      select jsonb_agg(jsonb_build_object('id', p.id, 'display_name', p.display_name, 'color', p.color, 'is_owner', p.is_owner) order by p.created_at)
      from public.participants p where p.room_id = v_room.id
    ), '[]'::jsonb),
    'messages', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', recent.id, 'room_id', recent.room_id, 'participant_id', recent.participant_id,
        'display_name', recent.display_name, 'color', recent.color, 'body', recent.body,
        'kind', recent.kind, 'attachment_id', recent.attachment_id,
        'attachment', case when recent.attachment_id is null then null else jsonb_build_object(
          'id', recent.attachment_id, 'file_name', recent.file_name, 'mime_type', recent.mime_type,
          'size_bytes', recent.size_bytes, 'duration_seconds', recent.duration_seconds
        ) end,
        'created_at', recent.created_at
      ) order by recent.created_at)
      from (
        select m.id, m.room_id, m.participant_id, p.display_name, p.color, m.body,
          m.kind, m.attachment_id, a.file_name, a.mime_type, a.size_bytes, a.duration_seconds, m.created_at
        from public.messages m
        join public.participants p on p.id = m.participant_id
        left join public.attachments a on a.id = m.attachment_id
        where m.room_id = v_room.id order by m.created_at desc limit 150
      ) recent
    ), '[]'::jsonb)
  ) into v_result;
  return v_result;
end;
$$;

create or replace function public.send_message(
  p_participant_id uuid,
  p_token text,
  p_body text
)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, extensions
as $$
declare
  v_participant public.participants%rowtype;
  v_message public.messages%rowtype;
  v_payload jsonb;
begin
  select * into v_participant
  from public.participants
  where participants.id = p_participant_id
    and participants.participant_key_hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if not found then raise exception 'invalid participant token' using errcode = '28000'; end if;

  p_body := trim(coalesce(p_body, ''));
  if char_length(p_body) not between 1 and 2000 then
    raise exception 'invalid message' using errcode = '22023';
  end if;

  insert into public.messages (room_id, participant_id, body, kind)
  values (v_participant.room_id, v_participant.id, p_body, 'text')
  returning * into v_message;

  update public.participants set last_seen_at = now() where id = v_participant.id;
  insert into public.product_events (event_name, room_id, participant_id, properties)
  values ('message_sent', v_participant.room_id, v_participant.id, jsonb_build_object('length', char_length(p_body), 'kind', 'text'));

  v_payload := jsonb_build_object(
    'id', v_message.id, 'room_id', v_message.room_id, 'participant_id', v_message.participant_id,
    'display_name', v_participant.display_name, 'color', v_participant.color,
    'body', v_message.body, 'kind', v_message.kind, 'attachment_id', null,
    'attachment', null, 'created_at', v_message.created_at
  );
  perform realtime.send(jsonb_build_object('message', v_payload), 'message', 'room:' || v_message.room_id::text, false);
  return v_payload;
end;
$$;

create or replace function public.send_attachment(
  p_participant_id uuid,
  p_token text,
  p_file_name text,
  p_mime_type text,
  p_base64 text,
  p_duration_seconds integer default null
)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, extensions
as $$
declare
  v_participant public.participants%rowtype;
  v_attachment public.attachments%rowtype;
  v_message public.messages%rowtype;
  v_data bytea;
  v_kind text;
  v_payload jsonb;
begin
  select * into v_participant from public.participants
  where participants.id = p_participant_id
    and participants.participant_key_hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if not found then raise exception 'invalid participant token' using errcode = '28000'; end if;

  p_file_name := right(trim(regexp_replace(coalesce(p_file_name, ''), '[\\/]+', '-', 'g')), 120);
  p_mime_type := lower(trim(coalesce(p_mime_type, 'application/octet-stream')));
  if char_length(p_file_name) not between 1 and 120 or char_length(p_mime_type) not between 3 and 120 then
    raise exception 'invalid attachment metadata' using errcode = '22023';
  end if;
  if p_mime_type in ('text/html', 'image/svg+xml', 'application/javascript', 'text/javascript', 'application/x-sh') then
    raise exception 'unsafe attachment type' using errcode = '22023';
  end if;
  if char_length(coalesce(p_base64, '')) > 11184812 then
    raise exception 'attachment is too large' using errcode = '22001';
  end if;
  begin
    v_data := decode(coalesce(p_base64, ''), 'base64');
  exception when others then
    raise exception 'invalid attachment data' using errcode = '22023';
  end;
  if octet_length(v_data) not between 1 and 8388608 then
    raise exception 'attachment is too large' using errcode = '22001';
  end if;

  v_kind := case
    when p_mime_type like 'image/%' then 'image'
    when p_mime_type like 'audio/%' then 'audio'
    else 'file'
  end;
  if v_kind = 'audio' and p_duration_seconds is not null and p_duration_seconds not between 1 and 300 then
    raise exception 'invalid audio duration' using errcode = '22023';
  end if;

  insert into public.attachments (room_id, participant_id, file_name, mime_type, size_bytes, data, duration_seconds)
  values (v_participant.room_id, v_participant.id, p_file_name, p_mime_type, octet_length(v_data), v_data,
    case when v_kind = 'audio' then p_duration_seconds else null end)
  returning * into v_attachment;

  insert into public.messages (room_id, participant_id, body, kind, attachment_id)
  values (v_participant.room_id, v_participant.id, p_file_name, v_kind, v_attachment.id)
  returning * into v_message;

  update public.participants set last_seen_at = now() where id = v_participant.id;
  insert into public.product_events (event_name, room_id, participant_id, properties)
  values (case when v_kind = 'audio' then 'voice_sent' else 'attachment_sent' end,
    v_participant.room_id, v_participant.id,
    jsonb_build_object('kind', v_kind, 'mime_type', p_mime_type, 'size_bytes', v_attachment.size_bytes));

  v_payload := jsonb_build_object(
    'id', v_message.id, 'room_id', v_message.room_id, 'participant_id', v_message.participant_id,
    'display_name', v_participant.display_name, 'color', v_participant.color,
    'body', v_message.body, 'kind', v_kind, 'attachment_id', v_attachment.id,
    'attachment', jsonb_build_object(
      'id', v_attachment.id, 'file_name', v_attachment.file_name, 'mime_type', v_attachment.mime_type,
      'size_bytes', v_attachment.size_bytes, 'duration_seconds', v_attachment.duration_seconds
    ),
    'created_at', v_message.created_at
  );
  perform realtime.send(jsonb_build_object('message', v_payload), 'message', 'room:' || v_message.room_id::text, false);
  return v_payload;
end;
$$;

create or replace function public.get_attachment(
  p_participant_id uuid,
  p_token text,
  p_attachment_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, extensions
as $$
declare
  v_participant public.participants%rowtype;
  v_attachment public.attachments%rowtype;
begin
  select * into v_participant from public.participants
  where participants.id = p_participant_id
    and participants.participant_key_hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if not found then raise exception 'invalid participant token' using errcode = '28000'; end if;

  select * into v_attachment from public.attachments
  where attachments.id = p_attachment_id and attachments.room_id = v_participant.room_id;
  if not found then raise exception 'attachment not found' using errcode = 'P0002'; end if;

  return jsonb_build_object(
    'id', v_attachment.id, 'file_name', v_attachment.file_name, 'mime_type', v_attachment.mime_type,
    'size_bytes', v_attachment.size_bytes, 'duration_seconds', v_attachment.duration_seconds,
    'base64', encode(v_attachment.data, 'base64')
  );
end;
$$;

revoke all on function public.send_attachment(uuid, text, text, text, text, integer) from public;
revoke all on function public.get_attachment(uuid, text, uuid) from public;
grant execute on function public.send_attachment(uuid, text, text, text, text, integer) to anon;
grant execute on function public.get_attachment(uuid, text, uuid) to anon;

comment on table public.attachments is 'Private room attachments. Binary data is only accessible through token-validated RPC functions.';
