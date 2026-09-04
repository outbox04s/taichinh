begin;

-- Remove records hidden by the pre-v0.12.3 soft-delete behavior. This is a
-- one-time cleanup; current app versions use permanent-delete RPCs directly.
do $$
declare v_tx public.transactions%rowtype;v_debt record;v_payment_ids uuid[];
begin
 perform set_config('app.debt_reversal','allowed',true);

 for v_tx in select * from public.transactions where deleted_at is not null loop
  delete from public.debt_payment_allocations where transaction_id=v_tx.id;
  if v_tx.transfer_group_id is not null then
   delete from public.transactions where transfer_group_id=v_tx.transfer_group_id;
  else
   delete from public.transactions where id=v_tx.id;
  end if;
 end loop;

 for v_debt in select id,user_id from public.debts where is_archived=true loop
  select array_agg(transaction_id) into v_payment_ids
  from public.debt_payment_allocations where debt_id=v_debt.id and user_id=v_debt.user_id;
  delete from public.debt_payment_allocations where debt_id=v_debt.id and user_id=v_debt.user_id;
  delete from public.transactions where id=any(coalesce(v_payment_ids,array[]::uuid[])) and user_id=v_debt.user_id;
  delete from public.debts where id=v_debt.id and user_id=v_debt.user_id;
 end loop;

 if exists(
  select 1 from public.financial_accounts a
  join public.transactions t on t.account_id=a.id and t.user_id=a.user_id
  where not a.is_active
 ) then raise exception 'Cannot purge a legacy inactive account that still has transactions';end if;
 delete from public.financial_accounts where not is_active;
end $$;

commit;
