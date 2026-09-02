begin;
create extension if not exists pgcrypto;

create function public.set_updated_at() returns trigger language plpgsql set search_path = '' as $$
begin new.updated_at = now(); return new; end; $$;

create table public.profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 full_name text check (full_name is null or char_length(full_name) between 1 and 120),
 currency text not null default 'VND' check (currency ~ '^[A-Z]{3}$'),
 timezone text not null default 'Asia/Ho_Chi_Minh' check (timezone <> ''),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.financial_accounts (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 name text not null check (char_length(name) between 1 and 100),
 type text not null check (type in ('cash','bank','e_wallet','savings')),
 bank_name text, masked_account_number text, opening_balance bigint not null default 0,
 current_balance bigint not null default 0, is_active boolean not null default true, sepay_account_id text,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique (id,user_id)
);
create unique index financial_accounts_sepay_id_unique_idx on public.financial_accounts(user_id,sepay_account_id) where sepay_account_id is not null;
create function public.initialize_account_balance() returns trigger language plpgsql set search_path='' as $$
begin new.current_balance:=new.opening_balance; return new; end $$;
create trigger financial_accounts_initialize_balance before insert on public.financial_accounts for each row execute function public.initialize_account_balance();
create table public.categories (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 name text not null check (char_length(name) between 1 and 80), type text not null check (type in ('income','expense')),
 icon text, color text check (color is null or color ~ '^#[0-9A-Fa-f]{6}$'),
 is_essential boolean not null default false, is_system boolean not null default false, parent_id uuid,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique (id,user_id), unique (user_id,name,type),
 foreign key (parent_id,user_id) references public.categories(id,user_id) on delete restrict
);
create table public.sepay_raw_events (
 id uuid primary key default gen_random_uuid(), user_id uuid references auth.users(id) on delete set null,
 sepay_event_id text not null check (sepay_event_id <> ''), event_type text not null check (event_type <> ''),
 payload jsonb not null check (jsonb_typeof(payload)='object'), payload_hash text not null check (payload_hash ~ '^[0-9a-f]{64}$'),
 received_at timestamptz not null default now(), processed_at timestamptz,
 processing_status text not null default 'pending' check (processing_status in ('pending','processing','processed','failed','ignored')),
 error_message text, unique (sepay_event_id), unique (event_type,payload_hash)
);
create table public.transactions (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 account_id uuid not null, category_id uuid, type text not null check (type in ('income','expense','transfer')),
 amount bigint not null check (amount > 0), transaction_at timestamptz not null, description text, note text,
 source text not null default 'manual' check (source in ('manual','sepay','recurring','debt_payment','adjustment')),
 status text not null default 'confirmed' check (status in ('confirmed','pending','excluded')),
 external_id text, reference_code text, transfer_group_id uuid,
 transfer_direction text check (transfer_direction in ('out','in')),
 raw_event_id uuid references public.sepay_raw_events(id) on delete restrict,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique (id,user_id),
 foreign key (account_id,user_id) references public.financial_accounts(id,user_id) on delete restrict,
 foreign key (category_id,user_id) references public.categories(id,user_id) on delete restrict,
 check ((type='transfer')=(transfer_group_id is not null)),
 check ((type='transfer')=(transfer_direction is not null)), check (type='transfer' or category_id is not null),
 check ((source='sepay')=(raw_event_id is not null))
);
create unique index transactions_external_id_unique_idx on public.transactions(user_id,source,external_id) where external_id is not null;
create unique index transactions_raw_event_unique_idx on public.transactions(raw_event_id) where raw_event_id is not null;
create unique index transactions_transfer_leg_unique_idx on public.transactions(transfer_group_id,transfer_direction) where transfer_group_id is not null;

create table public.income_sources (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 name text not null check (char_length(name) between 1 and 100), type text not null check (type in ('salary','freelance','business','other')),
 expected_amount bigint not null check (expected_amount > 0), pay_day smallint check (pay_day between 1 and 31),
 frequency text not null check (frequency in ('monthly','weekly','irregular')), is_active boolean not null default true,
 next_expected_date date, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique(id,user_id), check (frequency <> 'monthly' or pay_day is not null)
);
create table public.budgets (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 category_id uuid, period text not null check (period in ('weekly','monthly','yearly')),
 limit_amount bigint not null check (limit_amount > 0), start_date date not null, end_date date not null,
 alert_percent smallint not null default 80 check (alert_percent between 1 and 100), is_active boolean not null default true,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 foreign key(category_id,user_id) references public.categories(id,user_id) on delete restrict, check(end_date>=start_date)
);
create table public.debts (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 name text not null check(char_length(name) between 1 and 120), lender_name text,
 debt_type text not null check(debt_type in ('loan','credit_card','mortgage','installment','personal','other')),
 original_principal bigint not null check(original_principal>0), current_principal bigint not null check(current_principal>=0),
 interest_rate numeric(9,6) check(interest_rate is null or interest_rate between 0 and 100),
 interest_type text not null default 'none' check(interest_type in ('none','monthly','yearly')),
 start_date date not null, maturity_date date,
 payment_frequency text not null check(payment_frequency in ('weekly','monthly','quarterly','yearly','irregular')),
 expected_payment_amount bigint not null check(expected_payment_amount>=0), next_due_date date,
 status text not null default 'active' check(status in ('active','paid','overdue','paused')), note text,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(), unique(id,user_id),
 check(current_principal<=original_principal), check(maturity_date is null or maturity_date>=start_date),
 check((interest_type='none' and coalesce(interest_rate,0)=0) or interest_type<>'none')
);
create table public.debt_installments (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 debt_id uuid not null, due_date date not null, principal_amount bigint not null check(principal_amount>=0),
 interest_amount bigint not null default 0 check(interest_amount>=0), fee_amount bigint not null default 0 check(fee_amount>=0),
 total_due bigint generated always as (principal_amount+interest_amount+fee_amount) stored,
 paid_amount bigint not null default 0 check(paid_amount>=0), paid_at timestamptz,
 status text not null default 'upcoming' check(status in ('upcoming','partially_paid','paid','overdue')), transaction_id uuid,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 foreign key(debt_id,user_id) references public.debts(id,user_id) on delete cascade,
 foreign key(transaction_id,user_id) references public.transactions(id,user_id) on delete set null,
 unique(debt_id,due_date), check(principal_amount+interest_amount+fee_amount>0),
 check(paid_amount<=principal_amount+interest_amount+fee_amount),
 check((status='paid')=(paid_amount=principal_amount+interest_amount+fee_amount)), check(paid_at is null or paid_amount>0)
);
create table public.recurring_entries (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 account_id uuid not null, category_id uuid not null, type text not null check(type in ('income','expense')),
 amount bigint not null check(amount>0), title text not null check(char_length(title) between 1 and 120),
 frequency text not null check(frequency in ('daily','weekly','monthly','yearly')), start_date date not null,
 next_run_at timestamptz not null, end_date date, is_active boolean not null default true,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 foreign key(account_id,user_id) references public.financial_accounts(id,user_id) on delete restrict,
 foreign key(category_id,user_id) references public.categories(id,user_id) on delete restrict,
 check(end_date is null or end_date>=start_date)
);
create table public.risk_snapshots (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 calculated_at timestamptz not null default now(), level text not null check(level in ('safe','attention','dangerous')),
 score smallint not null check(score between 0 and 100), projected_cash_30_days bigint not null,
 debt_payment_ratio numeric(9,6) not null check(debt_payment_ratio>=0),
 emergency_coverage_months numeric(9,2) not null check(emergency_coverage_months>=0),
 overdue_debt_count integer not null check(overdue_debt_count>=0),
 reasons jsonb not null default '[]'::jsonb check(jsonb_typeof(reasons)='array')
);
create table public.notification_settings (
 user_id uuid primary key references auth.users(id) on delete cascade,
 debt_reminder_days smallint not null default 3 check(debt_reminder_days between 0 and 90),
 budget_alert_enabled boolean not null default true, risk_alert_enabled boolean not null default true,
 daily_summary_enabled boolean not null default false, preferred_time time not null default '20:00',
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create index financial_accounts_user_active_idx on public.financial_accounts(user_id,is_active);
create index categories_user_type_idx on public.categories(user_id,type);
create index categories_parent_idx on public.categories(parent_id) where parent_id is not null;
create index transactions_user_date_idx on public.transactions(user_id,transaction_at desc);
create index transactions_account_date_idx on public.transactions(account_id,transaction_at desc);
create index transactions_category_date_idx on public.transactions(category_id,transaction_at desc) where category_id is not null;
create index transactions_reference_idx on public.transactions(user_id,reference_code) where reference_code is not null;
create index income_sources_next_idx on public.income_sources(user_id,next_expected_date) where is_active;
create index budgets_active_dates_idx on public.budgets(user_id,start_date,end_date) where is_active;
create index debts_user_status_due_idx on public.debts(user_id,status,next_due_date);
create index debt_installments_due_idx on public.debt_installments(user_id,status,due_date);
create index recurring_entries_next_idx on public.recurring_entries(next_run_at) where is_active;
create index sepay_events_processing_idx on public.sepay_raw_events(processing_status,received_at);
create index risk_snapshots_user_date_idx on public.risk_snapshots(user_id,calculated_at desc);

do $$ declare t text; begin foreach t in array array['profiles','financial_accounts','categories','transactions','income_sources','budgets','debts','debt_installments','recurring_entries','notification_settings'] loop execute format('create trigger %I before update on public.%I for each row execute function public.set_updated_at()',t||'_updated_at',t); end loop; end $$;

create function public.transaction_balance_effect(p_type text,p_direction text,p_amount bigint,p_status text)
returns bigint language sql immutable set search_path='' as $$ select case when p_status<>'confirmed' then 0 when p_type='income' then p_amount when p_type='expense' then -p_amount when p_type='transfer' and p_direction='in' then p_amount when p_type='transfer' and p_direction='out' then -p_amount else 0 end $$;

create function public.apply_transaction_balance() returns trigger language plpgsql security definer set search_path='' as $$
declare old_effect bigint:=0; new_effect bigint:=0;
begin
 if tg_op<>'INSERT' then
  old_effect:=public.transaction_balance_effect(old.type,old.transfer_direction,old.amount,old.status);
  update public.financial_accounts set current_balance=current_balance-old_effect where id=old.account_id and user_id=old.user_id;
  if not found then raise exception 'Old financial account not found'; end if;
 end if;
 if tg_op<>'DELETE' then
  new_effect:=public.transaction_balance_effect(new.type,new.transfer_direction,new.amount,new.status);
  update public.financial_accounts set current_balance=current_balance+new_effect where id=new.account_id and user_id=new.user_id;
  if not found then raise exception 'Financial account not found'; end if;
 end if;
 return null;
end $$;
create trigger transactions_balance_after_change after insert or update of account_id,user_id,type,amount,status,transfer_direction or delete on public.transactions for each row execute function public.apply_transaction_balance();

create function public.prevent_direct_balance_change() returns trigger language plpgsql set search_path='' as $$
begin
 if new.opening_balance<>old.opening_balance then raise exception 'opening_balance is immutable'; end if;
 if new.current_balance<>old.current_balance and pg_trigger_depth()=1 then raise exception 'current_balance is managed by transaction triggers'; end if;
 return new;
end $$;
create trigger financial_accounts_protect_balance before update of opening_balance,current_balance on public.financial_accounts for each row execute function public.prevent_direct_balance_change();

create function public.transfer_between_accounts(p_from_account_id uuid,p_to_account_id uuid,p_amount bigint,p_transaction_at timestamptz default now(),p_description text default null)
returns uuid language plpgsql security invoker set search_path='' as $$
declare v_user_id uuid:=auth.uid(); v_group_id uuid:=gen_random_uuid(); v_count integer;
begin
 if v_user_id is null then raise exception 'Authentication required'; end if;
 if p_amount<=0 then raise exception 'Amount must be greater than zero'; end if;
 if p_from_account_id=p_to_account_id then raise exception 'Accounts must be different'; end if;
 perform 1 from public.financial_accounts where id in(p_from_account_id,p_to_account_id) and user_id=v_user_id and is_active order by id for update;
 get diagnostics v_count=row_count;
 if v_count<>2 then raise exception 'Both active accounts must belong to the current user'; end if;
 insert into public.transactions(user_id,account_id,type,amount,transaction_at,description,source,status,transfer_group_id,transfer_direction) values
 (v_user_id,p_from_account_id,'transfer',p_amount,p_transaction_at,p_description,'manual','confirmed',v_group_id,'out'),
 (v_user_id,p_to_account_id,'transfer',p_amount,p_transaction_at,p_description,'manual','confirmed',v_group_id,'in');
 return v_group_id;
end $$;

create function public.handle_new_user() returns trigger language plpgsql security definer set search_path='' as $$
begin
 insert into public.profiles(id,full_name) values(new.id,nullif(new.raw_user_meta_data->>'full_name',''));
 insert into public.notification_settings(user_id) values(new.id);
 insert into public.categories(user_id,name,type,icon,color,is_essential,is_system) values
 (new.id,'Lương','income','payments','#2E7D32',false,true),(new.id,'Thu nhập khác','income','add_circle','#00897B',false,true),
 (new.id,'Ăn uống','expense','restaurant','#EF6C00',true,true),(new.id,'Nhà ở','expense','home','#5E35B1',true,true),
 (new.id,'Di chuyển','expense','directions_car','#1976D2',true,true),(new.id,'Y tế','expense','medical_services','#C62828',true,true),
 (new.id,'Giải trí','expense','sports_esports','#AD1457',false,true),(new.id,'Khác','expense','category','#546E7A',false,true);
 return new;
end $$;
create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();

alter table public.profiles enable row level security; alter table public.financial_accounts enable row level security;
alter table public.categories enable row level security; alter table public.transactions enable row level security;
alter table public.income_sources enable row level security; alter table public.budgets enable row level security;
alter table public.debts enable row level security; alter table public.debt_installments enable row level security;
alter table public.recurring_entries enable row level security; alter table public.sepay_raw_events enable row level security;
alter table public.risk_snapshots enable row level security; alter table public.notification_settings enable row level security;
create policy profiles_owner on public.profiles for all to authenticated using((select auth.uid())=id) with check((select auth.uid())=id);
do $$ declare t text; begin foreach t in array array['financial_accounts','categories','transactions','income_sources','budgets','debts','debt_installments','recurring_entries','risk_snapshots','notification_settings'] loop execute format('create policy %I on public.%I for all to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id)',t||'_owner',t); end loop; end $$;

grant select,insert,update,delete on all tables in schema public to authenticated;
revoke all on table public.sepay_raw_events from anon,authenticated;
grant all on table public.sepay_raw_events to service_role;
revoke all on function public.apply_transaction_balance() from public,anon,authenticated;
revoke all on function public.handle_new_user() from public,anon,authenticated;
grant execute on function public.transfer_between_accounts(uuid,uuid,bigint,timestamptz,text) to authenticated;
commit;
