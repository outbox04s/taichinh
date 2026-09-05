-- Keep received income history; replace unpaid expectations when editing a source.
create or replace function public.update_income_source(
 p_id uuid,p_name text,p_amount bigint,p_frequency text,p_pay_day integer,p_next_date date
) returns void language plpgsql security invoker set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if nullif(btrim(p_name),'') is null or p_amount<=0 then raise exception 'Invalid income source'; end if;
 update public.income_sources set name=btrim(p_name),expected_amount=p_amount,
 frequency=p_frequency,pay_day=p_pay_day,next_expected_date=p_next_date
 where id=p_id and user_id=auth.uid();
 if not found then raise exception 'Income source not found'; end if;
 delete from public.income_payments where income_source_id=p_id and user_id=auth.uid()
 and transaction_id is null;
 if p_next_date is not null and not (
  p_frequency='monthly' and exists (
   select 1 from public.income_payments where income_source_id=p_id and user_id=auth.uid()
   and transaction_id is not null and date_trunc('month',expected_date)=date_trunc('month',p_next_date)
  )
 ) then
  insert into public.income_payments(user_id,income_source_id,expected_date,expected_amount)
  values(auth.uid(),p_id,p_next_date,p_amount) on conflict(income_source_id,expected_date) do nothing;
 end if;
end $$;
revoke all on function public.update_income_source(uuid,text,bigint,text,integer,date) from public,anon;
grant execute on function public.update_income_source(uuid,text,bigint,text,integer,date) to authenticated;
