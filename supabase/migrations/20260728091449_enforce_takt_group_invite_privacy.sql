-- A user who limits group invitations must not be silently added to a group.
-- "contacts" remains fail-closed until a real contacts relationship exists.
create or replace function public.takt_create_group(
  p_title text,
  p_member_ids uuid[],
  p_avatar_color text default '#8C78FF'
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := public.takt_require_user_id();
  conversation_id uuid;
  member_ids uuid[];
  member_id uuid;
begin
  if char_length(trim(p_title)) not between 2 and 64 then
    raise exception 'GROUP_TITLE_INVALID' using errcode = '22023';
  end if;
  if coalesce(p_avatar_color, '') !~ '^#[0-9A-Fa-f]{6}$' then
    raise exception 'AVATAR_COLOR_INVALID' using errcode = '22023';
  end if;

  select array_agg(distinct candidate_id)
  into member_ids
  from (
    select unnest(array_append(coalesce(p_member_ids, '{}'::uuid[]), current_user_id)) as candidate_id
  ) candidates
  join public.takt_profiles profile_row on profile_row.id = candidates.candidate_id;

  if coalesce(cardinality(member_ids), 0) < 2 then
    raise exception 'GROUP_NEEDS_MEMBER' using errcode = '22023';
  end if;

  if exists (
    select 1
    from unnest(member_ids) as other_user_id
    where other_user_id <> current_user_id and public.takt_are_blocked(other_user_id)
  ) then
    raise exception 'USER_BLOCKED' using errcode = '42501';
  end if;

  if exists (
    select 1
    from unnest(member_ids) as invited_user_id
    join public.takt_privacy_settings privacy_row on privacy_row.user_id = invited_user_id
    where invited_user_id <> current_user_id
      and privacy_row.allow_group_invites_from <> 'everyone'
  ) then
    raise exception 'GROUP_INVITES_RESTRICTED' using errcode = '42501';
  end if;

  insert into public.takt_conversations(kind, title, avatar_color, created_by)
  values ('group', trim(p_title), upper(p_avatar_color), current_user_id)
  returning id into conversation_id;

  foreach member_id in array member_ids loop
    insert into public.takt_conversation_members(conversation_id, user_id, role)
    values (conversation_id, member_id, case when member_id = current_user_id then 'owner'::public.takt_member_role else 'member'::public.takt_member_role end);
  end loop;

  return public.takt_conversation_json(conversation_id);
end;
$$;
