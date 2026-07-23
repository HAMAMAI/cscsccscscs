-- Supabase projects can carry default privileges that grant EXECUTE directly
-- to anon. Revoke that role explicitly for every Takt RPC without touching
-- the prior invite-room prototype functions.
begin;

do $$
declare
  function_row record;
begin
  for function_row in
    select procedure_row.oid::regprocedure as identity_value
    from pg_proc procedure_row
    join pg_namespace namespace_row on namespace_row.oid = procedure_row.pronamespace
    where namespace_row.nspname = 'public'
      and procedure_row.proname like 'takt_%'
  loop
    execute format('revoke all on function %s from anon', function_row.identity_value);
  end loop;
end;
$$;

commit;
