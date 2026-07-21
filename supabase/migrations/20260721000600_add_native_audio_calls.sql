create table public.audio_calls (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null unique references public.rooms(id) on delete cascade,
  started_by uuid not null references public.participants(id) on delete cascade,
  started_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index audio_calls_updated_at_idx on public.audio_calls(updated_at desc);

alter table public.audio_calls enable row level security;
create policy "deny direct audio call access" on public.audio_calls
  for all to anon, authenticated using (false) with check (false);
revoke all on table public.audio_calls from anon, authenticated;

create or replace function public.start_audio_call(
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
  v_call public.audio_calls%rowtype;
begin
  select * into v_participant from public.participants
  where participants.id = p_participant_id
    and participants.participant_key_hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if not found then raise exception 'invalid participant token' using errcode = '28000'; end if;

  delete from public.audio_calls where updated_at < now() - interval '2 hours';
  insert into public.audio_calls (room_id, started_by)
  values (v_participant.room_id, v_participant.id)
  on conflict (room_id) do update set updated_at = now()
  returning * into v_call;

  update public.participants set last_seen_at = now() where id = v_participant.id;
  insert into public.product_events (event_name, room_id, participant_id, properties)
  values ('call_started', v_participant.room_id, v_participant.id, jsonb_build_object('client', 'native_or_livekit'));

  return jsonb_build_object('id', v_call.id, 'room_id', v_call.room_id, 'started_at', v_call.started_at);
end;
$$;

create or replace function public.end_audio_call(
  p_participant_id uuid,
  p_token text
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, extensions
as $$
declare
  v_participant public.participants%rowtype;
begin
  select * into v_participant from public.participants
  where participants.id = p_participant_id
    and participants.participant_key_hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if not found then raise exception 'invalid participant token' using errcode = '28000'; end if;

  delete from public.audio_calls
  where room_id = v_participant.room_id and started_by = v_participant.id;
  insert into public.product_events (event_name, room_id, participant_id, properties)
  values ('call_ended', v_participant.room_id, v_participant.id, jsonb_build_object('client', 'native_or_livekit'));
end;
$$;

create or replace function public.validate_call_session(
  p_room_id uuid,
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
begin
  select * into v_participant from public.participants
  where participants.id = p_participant_id
    and participants.room_id = p_room_id
    and participants.participant_key_hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if not found then raise exception 'invalid participant token' using errcode = '28000'; end if;
  select * into v_room from public.rooms where id = v_participant.room_id;
  update public.participants set last_seen_at = now() where id = v_participant.id;
  return jsonb_build_object(
    'room_id', v_room.id,
    'room_name', v_room.name,
    'participant_id', v_participant.id,
    'display_name', v_participant.display_name
  );
end;
$$;

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
  select * into v_participant from public.participants
  where participants.id = p_participant_id
    and participants.participant_key_hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if not found then raise exception 'invalid participant token' using errcode = '28000'; end if;

  select * into v_room from public.rooms
  where rooms.id = v_participant.room_id and rooms.invite_code = upper(trim(p_invite_code));
  if not found then raise exception 'room not found' using errcode = 'P0002'; end if;

  update public.participants set last_seen_at = now() where id = v_participant.id;
  delete from public.audio_calls where updated_at < now() - interval '2 hours';

  select jsonb_build_object(
    'room', jsonb_build_object('id', v_room.id, 'name', v_room.name, 'invite_code', v_room.invite_code, 'created_at', v_room.created_at),
    'me', jsonb_build_object('id', v_participant.id, 'display_name', v_participant.display_name, 'color', v_participant.color, 'is_owner', v_participant.is_owner, 'online', true),
    'participants', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', p.id, 'display_name', p.display_name, 'color', p.color, 'is_owner', p.is_owner,
        'online', p.last_seen_at > now() - interval '30 seconds'
      ) order by p.created_at)
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
    ), '[]'::jsonb),
    'active_call', (
      select jsonb_build_object(
        'id', c.id, 'started_by', c.started_by, 'started_by_name', starter.display_name,
        'started_at', c.started_at
      )
      from public.audio_calls c
      join public.participants starter on starter.id = c.started_by
      where c.room_id = v_room.id
      limit 1
    )
  ) into v_result;
  return v_result;
end;
$$;

revoke all on function public.start_audio_call(uuid, text) from public;
revoke all on function public.end_audio_call(uuid, text) from public;
revoke all on function public.validate_call_session(uuid, uuid, text) from public;
revoke all on function public.start_audio_call(uuid, text) from authenticated;
revoke all on function public.end_audio_call(uuid, text) from authenticated;
revoke all on function public.validate_call_session(uuid, uuid, text) from authenticated;
grant execute on function public.start_audio_call(uuid, text) to anon;
grant execute on function public.end_audio_call(uuid, text) to anon;
grant execute on function public.validate_call_session(uuid, uuid, text) to anon;

comment on table public.audio_calls is 'Short-lived room-level audio call presence; media is carried by LiveKit, never Postgres.';
