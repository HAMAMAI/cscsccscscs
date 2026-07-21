revoke all on function public.start_audio_call(uuid, text) from authenticated;
revoke all on function public.end_audio_call(uuid, text) from authenticated;
revoke all on function public.validate_call_session(uuid, uuid, text) from authenticated;
