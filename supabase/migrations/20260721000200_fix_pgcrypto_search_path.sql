alter function public.create_room(text, text) set search_path = pg_catalog, extensions;
alter function public.join_room(text, text) set search_path = pg_catalog, extensions;
alter function public.get_room_state(text, uuid, text) set search_path = pg_catalog, extensions;
alter function public.send_message(uuid, text, text) set search_path = pg_catalog, extensions;
alter function public.track_event(uuid, text, text, jsonb) set search_path = pg_catalog, extensions;
