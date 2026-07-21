create policy "deny direct room access" on public.rooms for all to anon, authenticated using (false) with check (false);
create policy "deny direct participant access" on public.participants for all to anon, authenticated using (false) with check (false);
create policy "deny direct message access" on public.messages for all to anon, authenticated using (false) with check (false);
create policy "deny direct event access" on public.product_events for all to anon, authenticated using (false) with check (false);

revoke execute on function public.create_room(text, text) from authenticated;
revoke execute on function public.join_room(text, text) from authenticated;
revoke execute on function public.get_room_state(text, uuid, text) from authenticated;
revoke execute on function public.send_message(uuid, text, text) from authenticated;
revoke execute on function public.track_event(uuid, text, text, jsonb) from authenticated;

create index messages_participant_id_idx on public.messages(participant_id);
create index product_events_participant_id_idx on public.product_events(participant_id);
