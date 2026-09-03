begin;

create or replace function public.claim_debt_reminders()
returns table(installment_id uuid,debt_name text,due_date date,remaining_amount bigint,notification_type text)
language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();v_days integer;
begin
 if v_user is null then raise exception 'Authentication required';end if;
 select n.debt_reminder_days into v_days from public.notification_settings n where n.user_id=v_user;
 update public.debt_installments i set status='overdue' where i.user_id=v_user and i.due_date<current_date and i.paid_amount<i.total_due and i.status in('upcoming','partially_paid');
 update public.debts d set status=case when exists(select 1 from public.debt_installments i where i.debt_id=d.id and i.due_date<current_date and i.paid_amount<i.total_due) then 'overdue' when d.status='overdue' then 'active' else d.status end where d.user_id=v_user and d.status<>'paid';
 return query with candidates as(
  select i.id,i.due_date,i.total_due-i.paid_amount as remaining,d.name,
   case when i.due_date<current_date then 'overdue' when i.due_date=current_date then 'due' else 'before_due' end as kind
  from public.debt_installments i join public.debts d on d.id=i.debt_id and d.user_id=i.user_id
  where i.user_id=v_user and i.paid_amount<i.total_due and (i.due_date<=current_date or i.due_date=current_date+coalesce(v_days,3))
 ),claimed as(
  insert into public.debt_notification_log(user_id,installment_id,notification_date,notification_type)
  select v_user,c.id,current_date,c.kind from candidates c on conflict do nothing returning debt_notification_log.installment_id,debt_notification_log.notification_type
 ) select c.id,c.name,c.due_date,c.remaining,c.kind from candidates c join claimed x on x.installment_id=c.id and x.notification_type=c.kind;
end $$;

create or replace function public.process_sepay_event(
 p_event_id text,p_payload jsonb,p_payload_hash text,p_account_identifier text,p_transfer_type text,p_amount bigint,
 p_transaction_at timestamptz,p_description text,p_reference_code text default null
) returns jsonb language plpgsql security definer set search_path='' as $$
declare v_raw_id uuid;v_user_id uuid;v_account_id uuid;v_transaction_id uuid;v_inserted boolean:=false;
begin
 if p_event_id is null or btrim(p_event_id)='' then raise exception 'Missing SePay event id';end if;
 if p_payload_hash !~ '^[0-9a-f]{64}$' then raise exception 'Invalid payload hash';end if;
 if p_transfer_type not in('in','out') then raise exception 'Invalid transfer type';end if;
 if p_amount<=0 then raise exception 'Invalid amount';end if;
 insert into public.sepay_raw_events(sepay_event_id,event_type,payload,payload_hash,processing_status)
 values(p_event_id,'bank_transaction',p_payload,p_payload_hash,'processing') on conflict(sepay_event_id) do nothing returning id into v_raw_id;
 if v_raw_id is null then select r.id into v_raw_id from public.sepay_raw_events r where r.sepay_event_id=p_event_id;return jsonb_build_object('duplicate',true,'raw_event_id',v_raw_id);end if;
 select a.id,a.user_id into v_account_id,v_user_id from public.financial_accounts a where a.sepay_account_id=p_account_identifier and a.is_active order by a.created_at limit 1 for update;
 if v_account_id is null then
  select p.id into v_user_id from public.profiles p where (select count(*) from public.profiles)=1 limit 1;
  update public.sepay_raw_events set user_id=v_user_id,processing_status='pending_review',error_message='ACCOUNT_NOT_MAPPED' where id=v_raw_id;
  return jsonb_build_object('duplicate',false,'pending_review',true,'raw_event_id',v_raw_id);
 end if;
 update public.sepay_raw_events set user_id=v_user_id where id=v_raw_id;
 insert into public.transactions(user_id,account_id,category_id,type,amount,transaction_at,description,source,status,external_id,reference_code,raw_event_id)
 select v_user_id,v_account_id,c.id,case when p_transfer_type='in' then 'income' else 'expense' end,p_amount,p_transaction_at,nullif(p_description,''),'sepay','pending',p_event_id,nullif(p_reference_code,''),v_raw_id
 from public.categories c where c.user_id=v_user_id and c.type=case when p_transfer_type='in' then 'income' else 'expense' end order by c.is_system desc,c.created_at limit 1 returning id into v_transaction_id;
 v_inserted:=v_transaction_id is not null;
 update public.sepay_raw_events set processing_status=case when v_inserted then 'processed' else 'pending_review' end,processed_at=case when v_inserted then now() else null end,error_message=case when v_inserted then null else 'CATEGORY_NOT_AVAILABLE' end where id=v_raw_id;
 return jsonb_build_object('duplicate',false,'pending_review',not v_inserted,'transaction_id',v_transaction_id,'raw_event_id',v_raw_id);
exception when others then
 if v_raw_id is not null then update public.sepay_raw_events set processing_status='failed',error_message=left(sqlstate||':'||sqlerrm,500) where id=v_raw_id;end if;
 raise;
end $$;

commit;
