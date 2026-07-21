create extension if not exists pgcrypto;

create table public.rooms (
  id uuid primary key default gen_random_uuid(),
  invite_code text not null unique check (invite_code ~ '^[A-F0-9]{10}$'),
  name text not null check (char_length(name) between 2 and 48),
  created_at timestamptz not null default now()
);

create table public.participants (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms(id) on delete cascade,
  display_name text not null check (char_length(display_name) between 2 and 32),
  color text not null check (color ~ '^#[0-9A-Fa-f]{6}$'),
  participant_key_hash text not null,
  is_owner boolean not null default false,
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms(id) on delete cascade,
  participant_id uuid not null references public.participants(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now()
);

create table public.product_events (
  id bigint generated always as identity primary key,
  event_name text not null,
  room_id uuid references public.rooms(id) on delete cascade,
  participant_id uuid references public.participants(id) on delete set null,
  properties jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index participants_room_id_idx on public.participants(room_id);
create index messages_room_created_idx on public.messages(room_id, created_at desc);
create index messages_participant_id_idx on public.messages(participant_id);
create index product_events_name_created_idx on public.product_events(event_name, created_at desc);
create index product_events_room_created_idx on public.product_events(room_id, created_at desc);
create index product_events_participant_id_idx on public.product_events(participant_id);

alter table public.rooms enable row level security;
alter table public.participants enable row level security;
alter table public.messages enable row level security;
alter table public.product_events enable row level security;

create policy "deny direct room access" on public.rooms for all to anon, authenticated using (false) with check (false);
create policy "deny direct participant access" on public.participants for all to anon, authenticated using (false) with check (false);
create policy "deny direct message access" on public.messages for all to anon, authenticated using (false) with check (false);
create policy "deny direct event access" on public.product_events for all to anon, authenticated using (false) with check (false);

revoke all on table public.rooms from anon, authenticated;
revoke all on table public.participants from anon, authenticated;
revoke all on table public.messages from anon, authenticated;
revoke all on table public.product_events from anon, authenticated;
revoke all on sequence public.product_events_id_seq from anon, authenticated;

create or replace function public.create_room(p_room_name text, p_display_name text)
returns table (
  room_id uuid,
  invite_code text,
  participant_id uuid,
  participant_token text,
  display_name text,
  room_name text,
  color text
)
language plpgsql
security definer
set search_path = pg_catalog, extensions
as $$
declare
  v_room public.rooms%rowtype;
  v_participant public.participants%rowtype;
  v_token text := encode(gen_random_bytes(32), 'hex');
  v_code text;
  v_colors text[] := array['#826CF0', '#EF7F73', '#4DBF94', '#E09B45', '#4E91D8', '#C865B0'];
begin
  p_room_name := trim(regexp_replace(coalesce(p_room_name, ''), '\s+', ' ', 'g'));
  p_display_name := trim(regexp_replace(coalesce(p_display_name, ''), '\s+', ' ', 'g'));
  if char_length(p_room_name) not between 2 and 48 or char_length(p_display_name) not between 2 and 32 then
    raise exception 'invalid room or display name' using errcode = '22023';
  end if;

  loop
    v_code := upper(substr(encode(gen_random_bytes(8), 'hex'), 1, 10));
    exit when not exists (select 1 from public.rooms where rooms.invite_code = v_code);
  end loop;

  insert into public.rooms (invite_code, name)
  values (v_code, p_room_name)
  returning * into v_room;

  insert into public.participants (room_id, display_name, color, participant_key_hash, is_owner)
  values (v_room.id, p_display_name, v_colors[1], encode(digest(v_token, 'sha256'), 'hex'), true)
  returning * into v_participant;

  insert into public.product_events (event_name, room_id, participant_id, properties)
  values ('room_created', v_room.id, v_participant.id, jsonb_build_object('source', 'web'));

  return query select v_room.id, v_room.invite_code, v_participant.id, v_token,
    v_participant.display_name, v_room.name, v_participant.color;
end;
$$;

create or replace function public.join_room(p_invite_code text, p_display_name text)
returns table (
  room_id uuid,
  invite_code text,
  participant_id uuid,
  participant_token text,
  display_name text,
  room_name text,
  color text
)
language plpgsql
security definer
set search_path = pg_catalog, extensions
as $$
declare
  v_room public.rooms%rowtype;
  v_participant public.participants%rowtype;
  v_token text := encode(gen_random_bytes(32), 'hex');
  v_count integer;
  v_colors text[] := array['#826CF0', '#EF7F73', '#4DBF94', '#E09B45', '#4E91D8', '#C865B0'];
begin
  p_invite_code := upper(trim(coalesce(p_invite_code, '')));
  p_display_name := trim(regexp_replace(coalesce(p_display_name, ''), '\s+', ' ', 'g'));
  if char_length(p_display_name) not between 2 and 32 then
    raise exception 'invalid display name' using errcode = '22023';
  end if;

  select * into v_room from public.rooms where rooms.invite_code = p_invite_code;
  if not found then raise exception 'room not found' using errcode = 'P0002'; end if;

  select count(*) into v_count from public.participants where participants.room_id = v_room.id;
  if v_count >= 50 then raise exception 'room is full' using errcode = '54000'; end if;

  insert into public.participants (room_id, display_name, color, participant_key_hash)
  values (v_room.id, p_display_name, v_colors[1 + (v_count % array_length(v_colors, 1))], encode(digest(v_token, 'sha256'), 'hex'))
  returning * into v_participant;

  insert into public.product_events (event_name, room_id, participant_id, properties)
  values ('invite_accepted', v_room.id, v_participant.id, jsonb_build_object('participant_number', v_count + 1));

  return query select v_room.id, v_room.invite_code, v_participant.id, v_token,
    v_participant.display_name, v_room.name, v_participant.color;
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
        'display_name', recent.display_name, 'color', recent.color, 'body', recent.body, 'created_at', recent.created_at
      ) order by recent.created_at)
      from (
        select m.id, m.room_id, m.participant_id, p.display_name, p.color, m.body, m.created_at
        from public.messages m join public.participants p on p.id = m.participant_id
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

  insert into public.messages (room_id, participant_id, body)
  values (v_participant.room_id, v_participant.id, p_body)
  returning * into v_message;

  update public.participants set last_seen_at = now() where id = v_participant.id;
  insert into public.product_events (event_name, room_id, participant_id, properties)
  values ('message_sent', v_participant.room_id, v_participant.id, jsonb_build_object('length', char_length(p_body)));

  v_payload := jsonb_build_object(
    'id', v_message.id, 'room_id', v_message.room_id, 'participant_id', v_message.participant_id,
    'display_name', v_participant.display_name, 'color', v_participant.color,
    'body', v_message.body, 'created_at', v_message.created_at
  );
  perform realtime.send(jsonb_build_object('message', v_payload), 'message', 'room:' || v_message.room_id::text, false);
  return v_payload;
end;
$$;

create or replace function public.track_event(
  p_participant_id uuid,
  p_token text,
  p_event_name text,
  p_properties jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, extensions
as $$
declare
  v_participant public.participants%rowtype;
  v_allowed constant text[] := array['invite_shared', 'call_started', 'call_connected', 'call_failed', 'call_ended'];
begin
  if not (p_event_name = any(v_allowed)) then raise exception 'invalid event' using errcode = '22023'; end if;
  select * into v_participant from public.participants
  where participants.id = p_participant_id
    and participants.participant_key_hash = encode(digest(coalesce(p_token, ''), 'sha256'), 'hex');
  if not found then raise exception 'invalid participant token' using errcode = '28000'; end if;
  insert into public.product_events (event_name, room_id, participant_id, properties)
  values (p_event_name, v_participant.room_id, v_participant.id, coalesce(p_properties, '{}'::jsonb));
end;
$$;

revoke all on function public.create_room(text, text) from public;
revoke all on function public.join_room(text, text) from public;
revoke all on function public.get_room_state(text, uuid, text) from public;
revoke all on function public.send_message(uuid, text, text) from public;
revoke all on function public.track_event(uuid, text, text, jsonb) from public;

grant execute on function public.create_room(text, text) to anon;
grant execute on function public.join_room(text, text) to anon;
grant execute on function public.get_room_state(text, uuid, text) to anon;
grant execute on function public.send_message(uuid, text, text) to anon;
grant execute on function public.track_event(uuid, text, text, jsonb) to anon;

comment on table public.product_events is 'Product analytics events for activation, chat delivery, and audio call reliability.';
