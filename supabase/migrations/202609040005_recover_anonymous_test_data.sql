begin;

-- One-time recovery for the test installation that received a new anonymous
-- auth identity after reinstall. Abort unless the source and target are unique.
do $$
declare v_source uuid;v_target uuid;v_source_count integer;v_target_count integer;
begin
 select count(*),(array_agg(user_id))[1] into v_source_count,v_source
 from (
  select p.id as user_id
  from public.profiles p
  where (select count(*) from public.financial_accounts a where a.user_id=p.id)=2
    and (select count(*) from public.debts d where d.user_id=p.id)=2
 ) candidates;

 select count(*),(array_agg(user_id))[1] into v_target_count,v_target
 from (
  select p.id as user_id
  from public.profiles p
  where not exists(select 1 from public.financial_accounts a where a.user_id=p.id)
    and not exists(select 1 from public.debts d where d.user_id=p.id)
    and p.created_at=(select max(p2.created_at) from public.profiles p2)
 ) candidates;

 if v_source_count<>1 or v_target_count<>1 or v_source=v_target then
  raise exception 'Anonymous recovery source/target is ambiguous';
 end if;

 -- The ownership columns participate in composite foreign keys. Disabling
 -- constraint triggers inside this controlled transaction lets every related
 -- row move atomically; final validation below prevents partial recovery.
 set local session_replication_role='replica';
 update public.debt_payment_allocations set user_id=v_target where user_id=v_source;
 update public.debt_notification_log set user_id=v_target where user_id=v_source;
 update public.debt_installments set user_id=v_target where user_id=v_source;
 update public.debts set user_id=v_target where user_id=v_source;
 update public.financial_accounts set user_id=v_target where user_id=v_source;
 set local session_replication_role='origin';

 if (select count(*) from public.financial_accounts where user_id=v_target)<>2
    or (select count(*) from public.debts where user_id=v_target)<>2
    or exists(select 1 from public.financial_accounts where user_id=v_source)
    or exists(select 1 from public.debts where user_id=v_source) then
  raise exception 'Anonymous recovery validation failed';
 end if;
end $$;

commit;
