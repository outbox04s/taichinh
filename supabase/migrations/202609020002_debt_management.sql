begin;

create table public.debt_payment_allocations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  debt_id uuid not null,
  installment_id uuid not null,
  transaction_id uuid not null,
  total_paid bigint not null check (total_paid > 0),
  principal_paid bigint not null check (principal_paid >= 0),
  interest_paid bigint not null check (interest_paid >= 0),
  fee_paid bigint not null check (fee_paid >= 0),
  advance_principal bigint not null default 0 check (advance_principal >= 0),
  reversed_at timestamptz,
  created_at timestamptz not null default now(),
  unique (transaction_id),
  foreign key (debt_id,user_id) references public.debts(id,user_id) on delete restrict,
  foreign key (installment_id,user_id) references public.debt_installments(id,user_id) on delete restrict,
  foreign key (transaction_id,user_id) references public.transactions(id,user_id) on delete restrict,
  check (total_paid = principal_paid + interest_paid + fee_paid + advance_principal)
);
create index debt_allocations_debt_date_idx on public.debt_payment_allocations(user_id,debt_id,created_at desc);
create index debt_allocations_installment_idx on public.debt_payment_allocations(installment_id) where reversed_at is null;
alter table public.debt_payment_allocations enable row level security;
create policy debt_payment_allocations_owner on public.debt_payment_allocations for select to authenticated
using ((select auth.uid())=user_id);
grant select on public.debt_payment_allocations to authenticated;

create table public.debt_notification_log (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 installment_id uuid not null, notification_date date not null, notification_type text not null check(notification_type in ('before_due','due','overdue')),
 created_at timestamptz not null default now(),
 foreign key(installment_id,user_id) references public.debt_installments(id,user_id) on delete cascade,
 unique(user_id,installment_id,notification_date,notification_type)
);
alter table public.debt_notification_log enable row level security;
-- No Android table grants: reminders are claimed only through the RPC below.

insert into public.categories(user_id,name,type,icon,color,is_essential,is_system)
select id,'Trả nợ','expense','credit_score','#B3261E',true,true from auth.users
on conflict(user_id,name,type) do nothing;
create function public.add_debt_category_for_new_user() returns trigger language plpgsql security definer set search_path='' as $$
begin insert into public.categories(user_id,name,type,icon,color,is_essential,is_system) values(new.id,'Trả nợ','expense','credit_score','#B3261E',true,true) on conflict(user_id,name,type) do nothing;return new;end $$;
create trigger on_auth_user_created_debt_category after insert on auth.users for each row execute function public.add_debt_category_for_new_user();

create function public.debt_period_rate(p_interest_rate numeric,p_interest_type text,p_frequency text)
returns numeric language sql immutable set search_path='' as $$
 select case when p_interest_type='none' then 0
  when p_interest_type='yearly' then p_interest_rate/100/case p_frequency when 'weekly' then 52 when 'monthly' then 12 when 'quarterly' then 4 when 'yearly' then 1 else 12 end
  else p_interest_rate/100/case p_frequency when 'weekly' then 4.345 when 'monthly' then 1 when 'quarterly' then 0.333333 when 'yearly' then 0.083333 else 1 end end
$$;
create function public.next_debt_due_date(p_date date,p_frequency text)
returns date language sql immutable set search_path='' as $$
 select case p_frequency when 'weekly' then p_date+7 when 'monthly' then (p_date+interval '1 month')::date
 when 'quarterly' then (p_date+interval '3 months')::date when 'yearly' then (p_date+interval '1 year')::date else p_date end
$$;

create function public.create_debt_with_schedule(
 p_name text,p_lender_name text,p_debt_type text,p_original_principal bigint,p_current_principal bigint,
 p_interest_rate numeric,p_interest_type text,p_start_date date,p_maturity_date date,p_payment_frequency text,
 p_expected_payment_amount bigint,p_next_due_date date,p_note text default null
) returns uuid language plpgsql security invoker set search_path='' as $$
declare v_user uuid:=auth.uid();v_debt uuid:=gen_random_uuid();v_remaining bigint:=p_current_principal;v_due date:=p_next_due_date;
 v_interest bigint;v_principal bigint;v_rate numeric;v_count integer:=0;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 if p_original_principal<=0 or p_current_principal<0 or p_current_principal>p_original_principal or p_expected_payment_amount<=0 then raise exception 'Invalid debt amount';end if;
 v_rate:=public.debt_period_rate(coalesce(p_interest_rate,0),p_interest_type,p_payment_frequency);
 insert into public.debts(id,user_id,name,lender_name,debt_type,original_principal,current_principal,interest_rate,interest_type,start_date,maturity_date,payment_frequency,expected_payment_amount,next_due_date,status,note)
 values(v_debt,v_user,p_name,p_lender_name,p_debt_type,p_original_principal,p_current_principal,nullif(p_interest_rate,0),p_interest_type,p_start_date,p_maturity_date,p_payment_frequency,p_expected_payment_amount,p_next_due_date,'active',p_note);
 while v_remaining>0 and v_count<600 and (p_maturity_date is null or v_due<=p_maturity_date) loop
  v_interest:=round(v_remaining*v_rate)::bigint;v_principal:=least(v_remaining,p_expected_payment_amount-v_interest);
  if v_principal<=0 then raise exception 'Expected payment must exceed period interest';end if;
  insert into public.debt_installments(user_id,debt_id,due_date,principal_amount,interest_amount,fee_amount,paid_amount,status)
  values(v_user,v_debt,v_due,v_principal,v_interest,0,0,case when v_due<current_date then 'overdue' else 'upcoming' end);
  v_remaining:=v_remaining-v_principal;v_due:=public.next_debt_due_date(v_due,p_payment_frequency);v_count:=v_count+1;
  if p_payment_frequency='irregular' then exit;end if;
 end loop;
 return v_debt;
end $$;

create function public.record_debt_payment(p_installment_id uuid,p_account_id uuid,p_amount bigint,p_paid_at timestamptz,p_allow_advance boolean default false)
returns uuid language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_i public.debt_installments%rowtype;v_d public.debts%rowtype;v_remaining bigint;
 v_apply bigint;v_fee bigint;v_interest bigint;v_principal bigint;v_advance bigint:=0;v_tx uuid:=gen_random_uuid();v_category uuid;
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
 -- Stable allocation order: fee, interest, then principal. Only principal and explicit advance reduce debt principal.
 v_fee:=least(v_apply,greatest(0,v_i.fee_amount-(select coalesce(sum(fee_paid),0) from public.debt_payment_allocations where installment_id=v_i.id and reversed_at is null)));
 v_interest:=least(v_apply-v_fee,greatest(0,v_i.interest_amount-(select coalesce(sum(interest_paid),0) from public.debt_payment_allocations where installment_id=v_i.id and reversed_at is null)));
 v_principal:=v_apply-v_fee-v_interest;
 if v_principal+v_advance>v_d.current_principal then raise exception 'Principal payment exceeds current principal';end if;
 select id into v_category from public.categories where user_id=v_user and name='Trả nợ' and type='expense' limit 1;
 if v_category is null then raise exception 'Debt payment category is missing';end if;
 insert into public.transactions(id,user_id,account_id,category_id,type,amount,transaction_at,description,source,status)
 values(v_tx,v_user,p_account_id,v_category,'expense',p_amount,p_paid_at,'Thanh toán '||v_d.name,'debt_payment','confirmed');
 insert into public.debt_payment_allocations(user_id,debt_id,installment_id,transaction_id,total_paid,principal_paid,interest_paid,fee_paid,advance_principal)
 values(v_user,v_d.id,v_i.id,v_tx,p_amount,v_principal,v_interest,v_fee,v_advance);
 update public.debt_installments set paid_amount=paid_amount+v_apply,paid_at=case when paid_amount+v_apply=total_due then p_paid_at else paid_at end,
  status=case when paid_amount+v_apply=total_due then 'paid' when paid_amount+v_apply>0 then 'partially_paid' when due_date<current_date then 'overdue' else 'upcoming' end where id=v_i.id;
 update public.debts set current_principal=current_principal-v_principal-v_advance,
  next_due_date=(select min(due_date) from public.debt_installments where debt_id=v_d.id and status<>'paid') where id=v_d.id;
 return v_tx;
end $$;

create function public.reverse_debt_payment(p_transaction_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_a public.debt_payment_allocations%rowtype;v_i public.debt_installments%rowtype;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 select * into v_a from public.debt_payment_allocations where transaction_id=p_transaction_id and user_id=v_user and reversed_at is null for update;
 if not found then raise exception 'Active payment allocation not found';end if;
 select * into v_i from public.debt_installments where id=v_a.installment_id for update;
 perform 1 from public.debts where id=v_a.debt_id for update;
 update public.debt_payment_allocations set reversed_at=now() where id=v_a.id;
 update public.debt_installments set paid_amount=paid_amount-(v_a.total_paid-v_a.advance_principal),paid_at=null,
  status=case when paid_amount-(v_a.total_paid-v_a.advance_principal)=0 then case when due_date<current_date then 'overdue' else 'upcoming' end else 'partially_paid' end where id=v_a.installment_id;
 update public.debts set current_principal=current_principal+v_a.principal_paid+v_a.advance_principal,status=case when status='paid' then 'active' else status end where id=v_a.debt_id;
 perform set_config('app.debt_reversal','allowed',true);
 update public.transactions set deleted_at=now() where id=p_transaction_id and user_id=v_user;
end $$;

create function public.protect_debt_payment_transaction() returns trigger language plpgsql set search_path='' as $$
begin
 if old.source='debt_payment' and current_setting('app.debt_reversal',true)<>'allowed' then raise exception 'Use reverse_debt_payment to undo debt payments';end if;
 return case when tg_op='DELETE' then old else new end;
end $$;
create trigger transactions_protect_debt_payment_update before update or delete on public.transactions for each row execute function public.protect_debt_payment_transaction();

create function public.mark_overdue_installments() returns integer language plpgsql security definer set search_path='' as $$
declare v_count integer;begin update public.debt_installments set status='overdue' where due_date<current_date and paid_amount<total_due and status in('upcoming','partially_paid');get diagnostics v_count=row_count;return v_count;end $$;

create function public.confirm_debt_settlement(p_debt_id uuid) returns void language plpgsql security invoker set search_path='' as $$
begin update public.debts set status='paid',next_due_date=null where id=p_debt_id and user_id=auth.uid() and current_principal=0;
 if not found then raise exception 'Debt must belong to user and have zero principal';end if;end $$;

alter table public.notification_settings drop constraint notification_settings_debt_reminder_days_check;
alter table public.notification_settings add constraint notification_settings_debt_reminder_days_check check(debt_reminder_days in(1,3,5,7));

create function public.claim_debt_reminders()
returns table(installment_id uuid,debt_name text,due_date date,remaining_amount bigint,notification_type text)
language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_days integer;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 select debt_reminder_days into v_days from public.notification_settings where user_id=v_user;
 update public.debt_installments set status='overdue' where user_id=v_user and due_date<current_date and paid_amount<total_due and status in('upcoming','partially_paid');
 update public.debts d set status=case when exists(select 1 from public.debt_installments i where i.debt_id=d.id and i.due_date<current_date and i.paid_amount<i.total_due) then 'overdue' when d.status='overdue' then 'active' else d.status end where d.user_id=v_user and d.status<>'paid';
 return query with candidates as(
  select i.id,i.due_date,i.total_due-i.paid_amount as remaining,d.name,
   case when i.due_date<current_date then 'overdue' when i.due_date=current_date then 'due' else 'before_due' end as kind
  from public.debt_installments i join public.debts d on d.id=i.debt_id and d.user_id=i.user_id
  where i.user_id=v_user and i.paid_amount<i.total_due and (i.due_date<=current_date or i.due_date=current_date+coalesce(v_days,3))
 ),claimed as(
  insert into public.debt_notification_log(user_id,installment_id,notification_date,notification_type)
  select v_user,id,current_date,kind from candidates on conflict do nothing returning debt_notification_log.installment_id,debt_notification_log.notification_type
 ) select c.id,c.name,c.due_date,c.remaining,c.kind from candidates c join claimed x on x.installment_id=c.id and x.notification_type=c.kind;
end $$;

grant execute on function public.create_debt_with_schedule(text,text,text,bigint,bigint,numeric,text,date,date,text,bigint,date,text) to authenticated;
grant execute on function public.record_debt_payment(uuid,uuid,bigint,timestamptz,boolean) to authenticated;
grant execute on function public.reverse_debt_payment(uuid) to authenticated;
grant execute on function public.confirm_debt_settlement(uuid) to authenticated;
grant execute on function public.claim_debt_reminders() to authenticated;
revoke all on function public.mark_overdue_installments() from public,anon,authenticated;
revoke all on function public.add_debt_category_for_new_user() from public,anon,authenticated;
commit;
