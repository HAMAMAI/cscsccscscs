-- Resolves a shared Takt profile link after the recipient has authenticated.
-- Privacy scopes are fail-closed for the currently absent contacts model.
begin;

create or replace function public.takt_get_public_profile(p_user_id uuid)
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
  if p_user_id is null then
    raise exception 'PROFILE_NOT_FOUND' using errcode = '22023';
  end if;

  select jsonb_build_object(
    'id', profile_row.id,
    'username', profile_row.username,
    'display_name', profile_row.display_name,
    'about', profile_row.about,
    'avatar_color', case
      when profile_row.id = current_user_id or coalesce(privacy_row.show_avatar_to, 'everyone') = 'everyone'
        then profile_row.avatar_color
      else '#54606D'
    end,
    'last_seen_at', case
      when profile_row.id = current_user_id or coalesce(privacy_row.show_last_seen_to, 'everyone') = 'everyone'
        then profile_row.last_seen_at
      else null
    end,
    'is_online', case
      when profile_row.id = current_user_id or coalesce(privacy_row.show_last_seen_to, 'everyone') = 'everyone'
        then profile_row.is_online
      else false
    end
  ) into result
  from public.takt_profiles profile_row
  left join public.takt_privacy_settings privacy_row on privacy_row.user_id = profile_row.id
  where profile_row.id = p_user_id;

  if result is null then
    raise exception 'PROFILE_NOT_FOUND' using errcode = '22023';
  end if;
  return result;
end;
$$;

revoke all on function public.takt_get_public_profile(uuid) from public;
revoke all on function public.takt_get_public_profile(uuid) from anon;
grant execute on function public.takt_get_public_profile(uuid) to authenticated;

commit;
