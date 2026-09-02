begin;

alter table public.sepay_raw_events drop constraint if exists sepay_raw_events_processing_status_check;
alter table public.sepay_raw_events add constraint sepay_raw_events_processing_status_check
check (processing_status in ('pending','processing','processed','pending_review','failed','ignored'));

create table public.sepay_reconciliation_runs (
 id uuid primary key default gen_random_uuid(),
 user_id uuid not null references auth.users(id) on delete cascade,
 started_at timestamptz not null default now(), finished_at timestamptz,
 range_from timestamptz not null, range_to timestamptz not null,
 status text not null default 'running' check (status in ('running','completed','failed')),
 fetched_count integer not null default 0 check (fetched_count >= 0),
 inserted_count integer not null default 0 check (inserted_count >= 0),
 error_message text,
 check (range_to >= range_from)
);
create index sepay_reconciliation_runs_user_started_idx
on public.sepay_reconciliation_runs(user_id, started_at desc);

create table public.risk_settings (
 user_id uuid primary key references auth.users(id) on delete cascade,
 attention_debt_ratio numeric(7,4) not null default 0.35 check (attention_debt_ratio between 0 and 10),
 dangerous_debt_ratio numeric(7,4) not null default 0.50 check (dangerous_debt_ratio between 0 and 10),
 safe_emergency_months numeric(7,2) not null default 3 check (safe_emergency_months between 0 and 120),
 dangerous_emergency_months numeric(7,2) not null default 1 check (dangerous_emergency_months between 0 and 120),
 budget_attention_percent smallint not null default 80 check (budget_attention_percent between 1 and 100),
 large_payment_days smallint not null default 7 check (large_payment_days between 1 and 30),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 check (dangerous_debt_ratio >= attention_debt_ratio),
 check (safe_emergency_months >= dangerous_emergency_months)
);

create table public.notification_deliveries (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 notification_type text not null check (notification_type in ('debt_due','debt_overdue','budget','negative_projection','salary_missing')),
 subject_id uuid, delivery_date date not null, created_at timestamptz not null default now(),
 unique nulls not distinct (user_id, notification_type, subject_id, delivery_date)
);

alter table public.sepay_reconciliation_runs enable row level security;
alter table public.risk_settings enable row level security;
alter table public.notification_deliveries enable row level security;
create policy sepay_reconciliation_runs_owner on public.sepay_reconciliation_runs for select using (auth.uid()=user_id);
create policy risk_settings_owner on public.risk_settings for all using (auth.uid()=user_id) with check (auth.uid()=user_id);
create policy notification_deliveries_owner on public.notification_deliveries for select using (auth.uid()=user_id);
create trigger risk_settings_updated_at before update on public.risk_settings
for each row execute function public.set_updated_at();

create or replace function public.process_sepay_event(
 p_event_id text, p_payload jsonb, p_payload_hash text,
 p_account_identifier text, p_transfer_type text, p_amount bigint,
 p_transaction_at timestamptz, p_description text, p_reference_code text default null
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_raw_id uuid; v_user_id uuid; v_account_id uuid; v_transaction_id uuid; v_inserted boolean:=false;
begin
 if p_event_id is null or btrim(p_event_id)='' then raise exception 'Missing SePay event id'; end if;
 if p_payload_hash !~ '^[0-9a-f]{64}$' then raise exception 'Invalid payload hash'; end if;
 if p_transfer_type not in ('in','out') then raise exception 'Invalid transfer type'; end if;
 if p_amount <= 0 then raise exception 'Invalid amount'; end if;

 insert into public.sepay_raw_events(sepay_event_id,event_type,payload,payload_hash,processing_status)
 values(p_event_id,'bank_transaction',p_payload,p_payload_hash,'processing')
 on conflict (sepay_event_id) do nothing returning id into v_raw_id;
 if v_raw_id is null then
  select id into v_raw_id from public.sepay_raw_events where sepay_event_id=p_event_id;
  return jsonb_build_object('duplicate',true,'raw_event_id',v_raw_id);
 end if;

 select id,user_id into v_account_id,v_user_id from public.financial_accounts
 where sepay_account_id=p_account_identifier and is_active order by created_at limit 1 for update;
 if v_account_id is null then
  select min(id) into v_user_id from public.profiles having count(*)=1;
  update public.sepay_raw_events set user_id=v_user_id,processing_status='pending_review',error_message='ACCOUNT_NOT_MAPPED'
  where id=v_raw_id;
  return jsonb_build_object('duplicate',false,'pending_review',true,'raw_event_id',v_raw_id);
 end if;

 update public.sepay_raw_events set user_id=v_user_id where id=v_raw_id;
 insert into public.transactions(user_id,account_id,category_id,type,amount,transaction_at,description,source,status,external_id,reference_code,raw_event_id)
 select v_user_id,v_account_id,c.id,case when p_transfer_type='in' then 'income' else 'expense' end,
 p_amount,p_transaction_at,nullif(p_description,''),'sepay','pending',p_event_id,nullif(p_reference_code,''),v_raw_id
 from public.categories c where c.user_id=v_user_id and c.type=case when p_transfer_type='in' then 'income' else 'expense' end
 order by c.is_system desc,c.created_at limit 1 returning id into v_transaction_id;
 v_inserted:=v_transaction_id is not null;
 update public.sepay_raw_events set processing_status=case when v_inserted then 'processed' else 'pending_review' end,
 processed_at=case when v_inserted then now() else null end,
 error_message=case when v_inserted then null else 'CATEGORY_NOT_AVAILABLE' end where id=v_raw_id;
 return jsonb_build_object('duplicate',false,'pending_review',not v_inserted,'transaction_id',v_transaction_id,'raw_event_id',v_raw_id);
exception when others then
 if v_raw_id is not null then update public.sepay_raw_events set processing_status='failed',error_message=left(sqlstate||':'||sqlerrm,500) where id=v_raw_id; end if;
 raise;
end $$;
revoke all on function public.process_sepay_event(text,jsonb,text,text,text,bigint,timestamptz,text,text) from public,anon,authenticated;
grant execute on function public.process_sepay_event(text,jsonb,text,text,text,bigint,timestamptz,text,text) to service_role;

create or replace function public.assign_pending_sepay_event(p_raw_event_id uuid,p_account_id uuid,p_category_id uuid)
returns uuid language plpgsql security invoker set search_path='' as $$
declare v_uid uuid:=auth.uid(); v_event public.sepay_raw_events%rowtype; v_transaction_id uuid;
begin
 if v_uid is null then raise exception 'Authentication required'; end if;
 select * into v_event from public.sepay_raw_events where id=p_raw_event_id and user_id=v_uid and processing_status='pending_review' for update;
 if not found then raise exception 'Pending event not found'; end if;
 perform 1 from public.financial_accounts where id=p_account_id and user_id=v_uid and is_active;
 if not found then raise exception 'Account not found'; end if;
 perform 1 from public.categories where id=p_category_id and user_id=v_uid;
 if not found then raise exception 'Category not found'; end if;
 insert into public.transactions(user_id,account_id,category_id,type,amount,transaction_at,description,source,status,external_id,reference_code,raw_event_id)
 values(v_uid,p_account_id,p_category_id,case when v_event.payload->>'transferType'='in' then 'income' else 'expense' end,
 (v_event.payload->>'transferAmount')::bigint,(v_event.payload->>'transactionDate')::timestamp at time zone 'Asia/Ho_Chi_Minh',
 v_event.payload->>'content','sepay','pending',v_event.sepay_event_id,v_event.payload->>'referenceCode',v_event.id)
 returning id into v_transaction_id;
 update public.sepay_raw_events set user_id=v_uid,processing_status='processed',processed_at=now(),error_message=null where id=v_event.id;
 update public.financial_accounts set sepay_account_id=coalesce(sepay_account_id,v_event.payload->>'accountNumber') where id=p_account_id;
 return v_transaction_id;
end $$;

create or replace function public.list_pending_sepay_events()
returns table(id uuid,event_id text,transaction_at text,amount bigint,transfer_type text,description text,account_hint text)
language sql security definer set search_path='' stable as $$
 select e.id,e.sepay_event_id,e.payload->>'transactionDate',(e.payload->>'transferAmount')::bigint,
 e.payload->>'transferType',e.payload->>'content',right(e.payload->>'accountNumber',4)
 from public.sepay_raw_events e where e.processing_status='pending_review' and e.user_id=auth.uid()
 order by e.received_at desc limit 100
$$;
revoke all on function public.list_pending_sepay_events() from public,anon;
grant execute on function public.list_pending_sepay_events() to authenticated;

create or replace function public.handle_new_user_risk() returns trigger language plpgsql security definer set search_path='' as $$
begin insert into public.risk_settings(user_id) values(new.id) on conflict do nothing; return new; end $$;
create trigger on_profile_created_risk after insert on public.profiles for each row execute function public.handle_new_user_risk();
insert into public.risk_settings(user_id) select id from public.profiles on conflict do nothing;

commit;
