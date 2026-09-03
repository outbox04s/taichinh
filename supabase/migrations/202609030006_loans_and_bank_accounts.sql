begin;

alter table public.financial_accounts add column bank_short_name text;
alter table public.financial_accounts add column bank_logo text;
alter table public.debts add column received_amount bigint not null default 0 check(received_amount>=0);
alter table public.debts add column is_new_loan boolean not null default false;
alter table public.debts add column remaining_months integer check(remaining_months is null or remaining_months>0);

insert into public.categories(user_id,name,type,icon,color,is_essential,is_system)
select id,'Khoản vay nhận','income','account_balance','#1769E0',false,true from auth.users
on conflict(user_id,name,type) do nothing;

create or replace function public.add_loan_income_category_for_new_user() returns trigger language plpgsql security definer set search_path='' as $$
begin
 insert into public.categories(user_id,name,type,icon,color,is_essential,is_system)
 values(new.id,'Khoản vay nhận','income','account_balance','#1769E0',false,true)
 on conflict(user_id,name,type) do nothing;
 return new;
end $$;
create trigger on_auth_user_created_loan_income after insert on auth.users for each row execute function public.add_loan_income_category_for_new_user();

create function public.create_debt_v2(
 p_name text,p_lender_name text,p_total_payable bigint,p_received_amount bigint,p_is_new_loan boolean,p_has_interest boolean,
 p_remaining_months integer,p_expected_payment_amount bigint,p_first_due_date date,p_payment_frequency text,
 p_receive_account_id uuid default null,p_note text default null
) returns uuid language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_debt uuid:=gen_random_uuid();v_due date:=p_first_due_date;v_remaining bigint:=p_total_payable;
 v_amount bigint;v_index integer:=1;v_category uuid;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 if p_total_payable<=0 or p_received_amount<0 or p_remaining_months<=0 or p_expected_payment_amount<=0 then raise exception 'Invalid loan amount';end if;
 if p_payment_frequency not in('monthly','yearly') then raise exception 'Unsupported payment frequency';end if;
 if p_is_new_loan and p_received_amount>0 then
  perform 1 from public.financial_accounts where id=p_receive_account_id and user_id=v_user and is_active for update;
  if not found then raise exception 'Receive account is required';end if;
 end if;
 insert into public.debts(id,user_id,name,lender_name,debt_type,original_principal,current_principal,interest_rate,interest_type,start_date,maturity_date,payment_frequency,expected_payment_amount,next_due_date,status,note,received_amount,is_new_loan,remaining_months)
 values(v_debt,v_user,p_name,nullif(p_lender_name,''),'loan',p_total_payable,p_total_payable,null,case when p_has_interest then 'monthly' else 'none' end,current_date,null,p_payment_frequency,p_expected_payment_amount,p_first_due_date,'active',p_note,p_received_amount,p_is_new_loan,p_remaining_months);
 while v_remaining>0 and v_index<=p_remaining_months loop
  v_amount:=least(v_remaining,p_expected_payment_amount);
  insert into public.debt_installments(user_id,debt_id,due_date,principal_amount,interest_amount,fee_amount,paid_amount,status)
  values(v_user,v_debt,v_due,v_amount,0,0,0,case when v_due<current_date then 'overdue' else 'upcoming' end);
  v_remaining:=v_remaining-v_amount;v_due:=public.next_debt_due_date(v_due,p_payment_frequency);v_index:=v_index+1;
 end loop;
 if v_remaining>0 then raise exception 'Monthly amount and remaining months do not cover total payable';end if;
 if p_is_new_loan and p_received_amount>0 then
  select id into v_category from public.categories where user_id=v_user and name='Khoản vay nhận' and type='income' limit 1;
  insert into public.transactions(user_id,account_id,category_id,type,amount,transaction_at,description,source,status)
  values(v_user,p_receive_account_id,v_category,'income',p_received_amount,now(),'Thực nhận khoản vay '||p_name,'adjustment','confirmed');
 end if;
 return v_debt;
end $$;

create function public.sync_debt_remaining_months() returns trigger language plpgsql security definer set search_path='' as $$
declare v_debt uuid:=coalesce(new.debt_id,old.debt_id);
begin
 update public.debts set remaining_months=greatest(1,(select count(*) from public.debt_installments where debt_id=v_debt and status<>'paid')) where id=v_debt and status<>'paid';
 return coalesce(new,old);
end $$;
create trigger debt_installments_sync_months after insert or update of status or delete on public.debt_installments for each row execute function public.sync_debt_remaining_months();

grant execute on function public.create_debt_v2(text,text,bigint,bigint,boolean,boolean,integer,bigint,date,text,uuid,text) to authenticated;
revoke all on function public.add_loan_income_category_for_new_user() from public,anon,authenticated;
revoke all on function public.sync_debt_remaining_months() from public,anon,authenticated;
commit;
