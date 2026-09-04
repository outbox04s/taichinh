begin;

alter table public.debts add column if not exists total_periods integer check(total_periods is null or total_periods > 0);
alter table public.debts add column if not exists payment_mode text not null default 'principal_interest'
 check(payment_mode in ('principal_interest','principal','interest_only'));

create or replace function public.create_debt_v3(
 p_name text,p_lender_name text,p_principal bigint,p_is_new_loan boolean,p_has_interest boolean,
 p_payment_mode text,p_total_periods integer,p_remaining_periods integer,p_expected_payment_amount bigint,
 p_first_due_date date,p_payment_frequency text,p_receive_account_id uuid default null,p_note text default null
) returns uuid language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_debt uuid:=gen_random_uuid();v_due date:=p_first_due_date;
 v_left bigint:=p_principal;v_amount bigint;v_index integer:=1;v_category uuid;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 if nullif(trim(p_name),'') is null or p_principal<=0 or p_total_periods<=0 or p_remaining_periods<=0 or p_remaining_periods>p_total_periods or p_expected_payment_amount<=0 then raise exception 'Invalid loan amount';end if;
 if p_payment_mode not in('principal_interest','principal','interest_only') or (p_payment_mode='principal' and p_has_interest) then raise exception 'Invalid payment mode';end if;
 if p_payment_frequency not in('monthly','yearly') then raise exception 'Unsupported payment frequency';end if;
 if p_is_new_loan then
  perform 1 from public.financial_accounts where id=p_receive_account_id and user_id=v_user and is_active for update;
  if not found then raise exception 'Receive account is required';end if;
 end if;
 insert into public.debts(id,user_id,name,lender_name,debt_type,original_principal,current_principal,interest_rate,interest_type,start_date,payment_frequency,expected_payment_amount,next_due_date,status,note,received_amount,is_new_loan,remaining_months,total_periods,payment_mode)
 values(v_debt,v_user,trim(p_name),nullif(trim(p_lender_name),''),'loan',p_principal,p_principal,null,case when p_has_interest then 'monthly' else 'none' end,current_date,p_payment_frequency,p_expected_payment_amount,p_first_due_date,'active',p_note,case when p_is_new_loan then p_principal else 0 end,p_is_new_loan,p_remaining_periods,p_total_periods,p_payment_mode);
 if p_payment_mode='interest_only' then
  insert into public.debt_installments(user_id,debt_id,due_date,principal_amount,interest_amount,fee_amount,paid_amount,status)
  values(v_user,v_debt,v_due,0,p_expected_payment_amount,0,0,case when v_due<current_date then 'overdue' else 'upcoming' end);
 else
  while v_left>0 and v_index<=p_remaining_periods loop
   v_amount:=least(v_left,p_expected_payment_amount);
   insert into public.debt_installments(user_id,debt_id,due_date,principal_amount,interest_amount,fee_amount,paid_amount,status)
   values(v_user,v_debt,v_due,v_amount,0,0,0,case when v_due<current_date then 'overdue' else 'upcoming' end);
   v_left:=v_left-v_amount;v_due:=public.next_debt_due_date(v_due,p_payment_frequency);v_index:=v_index+1;
  end loop;
  if v_left>0 then raise exception 'Monthly amount and remaining months do not cover total payable';end if;
 end if;
 if p_is_new_loan then
  select id into v_category from public.categories where user_id=v_user and name='Khoản vay nhận' and type='income' limit 1;
  insert into public.transactions(user_id,account_id,category_id,type,amount,transaction_at,description,source,status)
  values(v_user,p_receive_account_id,v_category,'income',p_principal,now(),'Thực nhận khoản vay '||trim(p_name),'adjustment','confirmed');
 end if;
 return v_debt;
end $$;

grant execute on function public.create_debt_v3(text,text,bigint,boolean,boolean,text,integer,integer,bigint,date,text,uuid,text) to authenticated;
commit;
