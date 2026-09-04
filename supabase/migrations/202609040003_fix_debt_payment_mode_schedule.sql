begin;

create or replace function public.record_debt_payment(p_installment_id uuid,p_account_id uuid,p_amount bigint,p_paid_at timestamptz,p_allow_advance boolean default false)
returns uuid language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_i public.debt_installments%rowtype;v_d public.debts%rowtype;v_remaining bigint;
 v_apply bigint;v_fee bigint;v_interest bigint;v_principal bigint;v_advance bigint:=0;v_tx uuid:=gen_random_uuid();v_category uuid;v_new_principal bigint;v_next date;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 select * into v_i from public.debt_installments where id=p_installment_id and user_id=v_user for update;
 if not found then raise exception 'Installment not found';end if;
 select * into v_d from public.debts where id=v_i.debt_id and user_id=v_user for update;
 perform 1 from public.financial_accounts where id=p_account_id and user_id=v_user and is_active for update;
 if not found then raise exception 'Payment account not found';end if;
 if p_amount<=0 then raise exception 'Payment must be positive';end if;
 v_remaining:=v_i.total_due-v_i.paid_amount;
 if p_amount>v_remaining and not p_allow_advance then raise exception 'Payment exceeds remaining installment';end if;
 v_apply:=least(p_amount,v_remaining);v_advance:=p_amount-v_apply;
 v_fee:=least(v_apply,greatest(0,v_i.fee_amount-(select coalesce(sum(fee_paid),0) from public.debt_payment_allocations where installment_id=v_i.id and reversed_at is null)));
 v_interest:=least(v_apply-v_fee,greatest(0,v_i.interest_amount-(select coalesce(sum(interest_paid),0) from public.debt_payment_allocations where installment_id=v_i.id and reversed_at is null)));
 v_principal:=v_apply-v_fee-v_interest;
 if v_principal+v_advance>v_d.current_principal then raise exception 'Principal payment exceeds current principal';end if;
 select id into v_category from public.categories where user_id=v_user and name='Trả nợ' and type='expense' limit 1;
 insert into public.transactions(id,user_id,account_id,category_id,type,amount,transaction_at,description,source,status)
 values(v_tx,v_user,p_account_id,v_category,'expense',p_amount,p_paid_at,'Thanh toán '||v_d.name,'debt_payment','confirmed');
 insert into public.debt_payment_allocations(user_id,debt_id,installment_id,transaction_id,total_paid,principal_paid,interest_paid,fee_paid,advance_principal)
 values(v_user,v_d.id,v_i.id,v_tx,p_amount,v_principal,v_interest,v_fee,v_advance);
 update public.debt_installments set paid_amount=paid_amount+v_apply,paid_at=case when paid_amount+v_apply=total_due then p_paid_at else paid_at end,
  status=case when paid_amount+v_apply=total_due then 'paid' else 'partially_paid' end where id=v_i.id;
 v_new_principal:=v_d.current_principal-v_principal-v_advance;
 -- Chỉ khoản "trả lãi" mới không có hạn và sinh kỳ lãi kế tiếp.
 if v_i.paid_amount+v_apply=v_i.total_due and v_new_principal>0 and v_d.payment_mode='interest_only' then
  v_next:=public.next_debt_due_date(v_i.due_date,v_d.payment_frequency);
  insert into public.debt_installments(user_id,debt_id,due_date,principal_amount,interest_amount,fee_amount,paid_amount,status)
  values(v_user,v_d.id,v_next,0,v_d.expected_payment_amount,0,0,case when v_next<current_date then 'overdue' else 'upcoming' end)
  on conflict(debt_id,due_date) do nothing;
 end if;
 update public.debts set current_principal=v_new_principal,
  status=case when v_new_principal=0 then 'paid' else status end,
  next_due_date=case when v_new_principal=0 then null else (select min(due_date) from public.debt_installments where debt_id=v_d.id and status<>'paid') end
 where id=v_d.id;
 return v_tx;
end $$;

grant execute on function public.record_debt_payment(uuid,uuid,bigint,timestamptz,boolean) to authenticated;
commit;
