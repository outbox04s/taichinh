begin;
alter table public.transactions add column deleted_at timestamptz;

create table public.income_payments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  income_source_id uuid not null,
  expected_date date not null,
  expected_amount bigint not null check (expected_amount > 0),
  transaction_id uuid,
  actual_amount bigint check (actual_amount is null or actual_amount > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id, user_id),
  unique (income_source_id, expected_date),
  foreign key (income_source_id, user_id) references public.income_sources(id, user_id) on delete cascade,
  foreign key (transaction_id, user_id) references public.transactions(id, user_id) on delete set null (transaction_id),
  check ((transaction_id is null) = (actual_amount is null))
);
create index income_payments_due_idx on public.income_payments(user_id, expected_date) where transaction_id is null;
create trigger income_payments_updated_at before update on public.income_payments
for each row execute function public.set_updated_at();
create function public.schedule_first_income_payment() returns trigger language plpgsql set search_path='' as $$
begin
 if new.next_expected_date is not null then insert into public.income_payments(user_id,income_source_id,expected_date,expected_amount) values(new.user_id,new.id,new.next_expected_date,new.expected_amount) on conflict do nothing; end if;
 return new;
end $$;
create trigger income_sources_schedule_first after insert on public.income_sources for each row execute function public.schedule_first_income_payment();
alter table public.income_payments enable row level security;
create policy income_payments_owner on public.income_payments for all to authenticated
using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
grant select, insert, update, delete on public.income_payments to authenticated;

create function public.protect_sepay_transaction_source()
returns trigger language plpgsql set search_path = '' as $$
begin
  if old.source = 'sepay' and (
    new.user_id is distinct from old.user_id or new.account_id is distinct from old.account_id or
    new.type is distinct from old.type or new.amount is distinct from old.amount or
    new.transaction_at is distinct from old.transaction_at or new.description is distinct from old.description or
    new.source is distinct from old.source or new.external_id is distinct from old.external_id or
    new.reference_code is distinct from old.reference_code or new.transfer_group_id is distinct from old.transfer_group_id or
    new.transfer_direction is distinct from old.transfer_direction or new.raw_event_id is distinct from old.raw_event_id or
    new.deleted_at is distinct from old.deleted_at
  ) then raise exception 'SePay source fields are immutable'; end if;
  return new;
end $$;
create trigger transactions_protect_sepay before update on public.transactions
for each row execute function public.protect_sepay_transaction_source();

create function public.create_manual_transaction(
  p_account_id uuid, p_category_id uuid, p_type text, p_amount bigint,
  p_transaction_at timestamptz, p_description text default null, p_note text default null,
  p_recurring boolean default false
) returns uuid language plpgsql security invoker set search_path = '' as $$
declare v_user_id uuid := auth.uid(); v_id uuid := gen_random_uuid();
begin
  if v_user_id is null then raise exception 'Authentication required'; end if;
  if p_type not in ('income','expense') or p_amount <= 0 then raise exception 'Invalid transaction'; end if;
  insert into public.transactions(id,user_id,account_id,category_id,type,amount,transaction_at,description,note,source,status)
  values(v_id,v_user_id,p_account_id,p_category_id,p_type,p_amount,p_transaction_at,p_description,p_note,'manual','confirmed');
  if p_recurring then
    insert into public.recurring_entries(user_id,account_id,category_id,type,amount,title,frequency,start_date,next_run_at)
    values(v_user_id,p_account_id,p_category_id,p_type,p_amount,coalesce(nullif(p_description,''),'Giao dịch định kỳ'),'monthly',
      (p_transaction_at at time zone 'Asia/Ho_Chi_Minh')::date,p_transaction_at + interval '1 month');
  end if;
  return v_id;
end $$;
grant execute on function public.create_manual_transaction(uuid,uuid,text,bigint,timestamptz,text,text,boolean) to authenticated;
revoke all on function public.schedule_first_income_payment() from public,anon,authenticated;
commit;
