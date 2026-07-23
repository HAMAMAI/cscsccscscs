-- Keep helper functions out of the public PostgREST surface. They are called
-- by secured RPCs and policies as the function owner, not by the mobile app.
begin;

revoke all on function public.takt_touch_updated_at() from authenticated;
revoke all on function public.takt_handle_new_user() from authenticated;
revoke all on function public.takt_require_user_id() from authenticated;
revoke all on function public.takt_are_blocked(uuid) from authenticated;
revoke all on function public.takt_message_json(uuid) from authenticated;
revoke all on function public.takt_conversation_json(uuid) from authenticated;
revoke all on function public.takt_list_conversations() from authenticated;

commit;
