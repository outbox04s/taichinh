begin;
insert into auth.users(id,aud,role,email,raw_user_meta_data,created_at,updated_at)
values('ee060001-0000-0000-0000-000000000001','authenticated','authenticated','income-management-check@example.invalid','{}',now(),now());
set local role authenticated;
set local "request.jwt.claims"='{"sub":"ee060001-0000-0000-0000-000000000001","role":"authenticated"}';
insert into public.income_sources(id,user_id,name,type,expected_amount,pay_day,frequency,next_expected_date)
values('ee060001-0000-0000-0000-000000000002',auth.uid(),'Test income','salary',1000,5,'monthly','2026-09-05');
select public.update_income_source('ee060001-0000-0000-0000-000000000002','Updated',2000,'monthly',10,'2026-09-10');
do $$ begin
 if (select count(*) from public.income_payments where income_source_id='ee060001-0000-0000-0000-000000000002')<>1
 or not exists(select 1 from public.income_payments where income_source_id='ee060001-0000-0000-0000-000000000002' and expected_date='2026-09-10' and expected_amount=2000)
 then raise exception 'Unpaid schedule was not updated'; end if;
end $$;
insert into public.financial_accounts(id,user_id,name,type,opening_balance)
values('ee060001-0000-0000-0000-000000000003',auth.uid(),'Test','cash',0);
insert into public.transactions(id,user_id,account_id,category_id,type,amount,transaction_at,source,status)
select 'ee060001-0000-0000-0000-000000000004',auth.uid(),'ee060001-0000-0000-0000-000000000003',id,'income',2000,now(),'manual','confirmed'
from public.categories where user_id=auth.uid() and type='income' limit 1;
update public.income_payments set transaction_id='ee060001-0000-0000-0000-000000000004',actual_amount=2000
where income_source_id='ee060001-0000-0000-0000-000000000002';
select public.update_income_source('ee060001-0000-0000-0000-000000000002','Updated again',3000,'monthly',20,'2026-09-20');
do $$ begin
 if (select count(*) from public.income_payments where income_source_id='ee060001-0000-0000-0000-000000000002')<>1
 or not exists(select 1 from public.income_payments where transaction_id='ee060001-0000-0000-0000-000000000004' and actual_amount=2000)
 then raise exception 'Received month was duplicated or modified'; end if;
end $$;
delete from public.income_sources where id='ee060001-0000-0000-0000-000000000002';
do $$ begin
 if not exists(select 1 from public.transactions where id='ee060001-0000-0000-0000-000000000004')
 or (select current_balance from public.financial_accounts where id='ee060001-0000-0000-0000-000000000003')<>2000
 then raise exception 'Delete changed received transaction or balance'; end if;
end $$;
select 'Income management checks passed' as result;
rollback;
