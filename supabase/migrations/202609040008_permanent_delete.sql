begin;

create or replace function public.delete_financial_transaction(p_transaction_id uuid)
returns void language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_tx public.transactions%rowtype;v_a public.debt_payment_allocations%rowtype;v_applied bigint;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 select * into v_tx from public.transactions where id=p_transaction_id and user_id=v_user for update;
 if not found then raise exception 'Transaction not found';end if;

 -- A transfer is one operation: deleting either leg deletes both legs.
 if v_tx.transfer_group_id is not null then
  perform set_config('app.debt_reversal','allowed',true);
  delete from public.transactions where user_id=v_user and transfer_group_id=v_tx.transfer_group_id;
  return;
 end if;

 select * into v_a from public.debt_payment_allocations
 where transaction_id=v_tx.id and user_id=v_user for update;
 if found then
  if v_a.reversed_at is null then
   v_applied:=v_a.total_paid-v_a.advance_principal;
   update public.debt_installments set
    paid_amount=paid_amount-v_applied,paid_at=null,
    status=case when paid_amount-v_applied=0 then case when due_date<current_date then 'overdue' else 'upcoming' end else 'partially_paid' end
   where id=v_a.installment_id and user_id=v_user;
   update public.debts set
    current_principal=current_principal+v_a.principal_paid+v_a.advance_principal,
    status=case when status='paid' then 'active' else status end,
    next_due_date=(select min(i.due_date) from public.debt_installments i where i.debt_id=v_a.debt_id and i.status<>'paid')
   where id=v_a.debt_id and user_id=v_user;
  end if;
  delete from public.debt_payment_allocations where id=v_a.id and user_id=v_user;
 end if;
 perform set_config('app.debt_reversal','allowed',true);
 delete from public.transactions where id=v_tx.id and user_id=v_user;
end $$;

create or replace function public.delete_debt_permanently(p_debt_id uuid)
returns void language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_transaction_id uuid;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 perform 1 from public.debts where id=p_debt_id and user_id=v_user for update;
 if not found then raise exception 'Debt not found';end if;
 perform set_config('app.debt_reversal','allowed',true);
 for v_transaction_id in select transaction_id from public.debt_payment_allocations where debt_id=p_debt_id and user_id=v_user loop
  delete from public.debt_payment_allocations where transaction_id=v_transaction_id and user_id=v_user;
  delete from public.transactions where id=v_transaction_id and user_id=v_user;
 end loop;
 delete from public.debts where id=p_debt_id and user_id=v_user;
end $$;

create or replace function public.delete_financial_account(p_account_id uuid)
returns void language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_transaction_id uuid;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 perform 1 from public.financial_accounts where id=p_account_id and user_id=v_user for update;
 if not found then raise exception 'Account not found';end if;
 loop
  select id into v_transaction_id from public.transactions where account_id=p_account_id and user_id=v_user limit 1;
  exit when v_transaction_id is null;
  perform public.delete_financial_transaction(v_transaction_id);
  v_transaction_id:=null;
 end loop;
 delete from public.financial_accounts where id=p_account_id and user_id=v_user;
end $$;

grant execute on function public.delete_financial_transaction(uuid) to authenticated;
grant execute on function public.delete_debt_permanently(uuid) to authenticated;
grant execute on function public.delete_financial_account(uuid) to authenticated;
commit;
