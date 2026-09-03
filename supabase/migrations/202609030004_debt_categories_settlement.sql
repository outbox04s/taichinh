begin;

insert into public.categories(user_id,name,type,icon,color,is_essential,is_system)
select u.id,c.name,c.type,c.icon,'#1769E0',c.essential,true
from auth.users u cross join (values
 ('Mua sắm cá nhân','expense','shopping_bag',false),
 ('Công việc','expense','work',false),
 ('Học tập','expense','school',false),
 ('Gia đình','expense','family_restroom',true),
 ('Hóa đơn','expense','receipt_long',true),
 ('Du lịch','expense','flight',false),
 ('Thưởng','income','redeem',false),
 ('Kinh doanh','income','storefront',false),
 ('Đầu tư','income','trending_up',false),
 ('Quà tặng','income','card_giftcard',false)
) as c(name,type,icon,essential)
on conflict(user_id,name,type) do nothing;

create or replace function public.create_debt_with_schedule(
 p_name text,p_lender_name text,p_debt_type text,p_original_principal bigint,p_current_principal bigint,
 p_interest_rate numeric,p_interest_type text,p_start_date date,p_maturity_date date,p_payment_frequency text,
 p_expected_payment_amount bigint,p_next_due_date date,p_note text default null
) returns uuid language plpgsql security invoker set search_path='' as $$
declare v_user uuid:=auth.uid();v_debt uuid:=gen_random_uuid();v_due date:=p_next_due_date;
 v_remaining bigint:=p_current_principal;v_principal bigint;v_count integer:=0;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 if p_original_principal<=0 or p_current_principal<0 or p_current_principal>p_original_principal or p_expected_payment_amount<=0 then raise exception 'Invalid debt amount';end if;
 if p_payment_frequency not in('monthly','yearly') then raise exception 'Unsupported payment frequency';end if;
 insert into public.debts(id,user_id,name,lender_name,debt_type,original_principal,current_principal,interest_rate,interest_type,start_date,maturity_date,payment_frequency,expected_payment_amount,next_due_date,status,note)
 values(v_debt,v_user,p_name,nullif(p_lender_name,''),p_debt_type,p_original_principal,p_current_principal,null,p_interest_type,p_start_date,null,p_payment_frequency,p_expected_payment_amount,p_next_due_date,'active',p_note);
 if p_interest_type<>'none' then
  insert into public.debt_installments(user_id,debt_id,due_date,principal_amount,interest_amount,fee_amount,paid_amount,status)
  values(v_user,v_debt,v_due,0,p_expected_payment_amount,0,0,case when v_due<current_date then 'overdue' else 'upcoming' end);
 else
  while v_remaining>0 and v_count<600 loop
   v_principal:=least(v_remaining,p_expected_payment_amount);
   insert into public.debt_installments(user_id,debt_id,due_date,principal_amount,interest_amount,fee_amount,paid_amount,status)
   values(v_user,v_debt,v_due,v_principal,0,0,0,case when v_due<current_date then 'overdue' else 'upcoming' end);
   v_remaining:=v_remaining-v_principal;v_due:=public.next_debt_due_date(v_due,p_payment_frequency);v_count:=v_count+1;
  end loop;
 end if;
 return v_debt;
end $$;

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
 if v_i.paid_amount+v_apply=v_i.total_due and v_new_principal>0 and v_d.interest_type<>'none' then
  v_next:=public.next_debt_due_date(v_i.due_date,v_d.payment_frequency);
  insert into public.debt_installments(user_id,debt_id,due_date,principal_amount,interest_amount,fee_amount,paid_amount,status)
  values(v_user,v_d.id,v_next,0,v_d.expected_payment_amount,0,0,case when v_next<current_date then 'overdue' else 'upcoming' end)
  on conflict(debt_id,due_date) do nothing;
 end if;
 update public.debts set current_principal=v_new_principal,
  next_due_date=(select min(due_date) from public.debt_installments where debt_id=v_d.id and status<>'paid') where id=v_d.id;
 return v_tx;
end $$;

create function public.settle_debt(p_debt_id uuid,p_account_id uuid,p_settlement_amount bigint,p_penalty_fee bigint default 0,p_paid_at timestamptz default now())
returns uuid language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_d public.debts%rowtype;v_tx uuid:=gen_random_uuid();v_category uuid;v_total bigint;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 select * into v_d from public.debts where id=p_debt_id and user_id=v_user and status<>'paid' for update;
 if not found then raise exception 'Active debt not found';end if;
 if p_settlement_amount<0 or p_penalty_fee<0 then raise exception 'Invalid settlement amount';end if;
 v_total:=p_settlement_amount+p_penalty_fee;
 if v_total<=0 then raise exception 'Settlement total must be positive';end if;
 perform 1 from public.financial_accounts where id=p_account_id and user_id=v_user and is_active for update;
 if not found then raise exception 'Payment account not found';end if;
 select id into v_category from public.categories where user_id=v_user and name='Trả nợ' and type='expense' limit 1;
 insert into public.transactions(id,user_id,account_id,category_id,type,amount,transaction_at,description,note,source,status)
 values(v_tx,v_user,p_account_id,v_category,'expense',v_total,p_paid_at,'Tất toán '||v_d.name,
  case when p_penalty_fee>0 then 'Phí phạt: '||p_penalty_fee::text else null end,'debt_payment','confirmed');
 update public.debts set current_principal=0,status='paid',next_due_date=null where id=v_d.id;
 delete from public.debt_installments where debt_id=v_d.id and paid_amount=0;
 return v_tx;
end $$;

grant execute on function public.settle_debt(uuid,uuid,bigint,bigint,timestamptz) to authenticated;
commit;
