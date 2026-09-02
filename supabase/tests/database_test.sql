begin;
create extension if not exists pgtap with schema extensions;
select plan(17);
select is((select count(*)::integer from pg_class where relnamespace='public'::regnamespace and relname in ('profiles','financial_accounts','categories','transactions','income_sources','budgets','debts','debt_installments','recurring_entries','sepay_raw_events','risk_snapshots','notification_settings') and relrowsecurity),12,'RLS is enabled on every application table');
select is((select count(*)::integer from pg_policies where schemaname='public' and tablename='sepay_raw_events'),0,'raw SePay events expose no client policy');

insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_user_meta_data,created_at,updated_at)
values
 ('00000000-0000-0000-0000-000000000000','10000000-0000-0000-0000-000000000001','authenticated','authenticated','a@test.local','x',now(),'{}',now(),now()),
 ('00000000-0000-0000-0000-000000000000','20000000-0000-0000-0000-000000000002','authenticated','authenticated','b@test.local','x',now(),'{}',now(),now());

select is((select count(*)::integer from public.profiles where id in ('10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002')),2,'signup creates profiles');
select is((select count(*)::integer from public.categories where user_id='10000000-0000-0000-0000-000000000001'),8,'signup creates default categories');
select is((select count(*)::integer from public.notification_settings where user_id='10000000-0000-0000-0000-000000000001'),1,'signup creates notification settings');

set local role authenticated;
set local "request.jwt.claims"='{"sub":"10000000-0000-0000-0000-000000000001","role":"authenticated"}';
insert into public.financial_accounts(id,user_id,name,type,opening_balance)
values
 ('a0000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','Ví','cash',1000),
 ('a0000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','Ngân hàng','bank',2000);
select is((select current_balance from public.financial_accounts where id='a0000000-0000-0000-0000-000000000001'),1000::bigint,'opening balance initializes current balance');
select throws_ok($$insert into public.financial_accounts(user_id,name,type) values('20000000-0000-0000-0000-000000000002','Sai chủ','cash')$$,'42501',null,'RLS rejects insert for another user');

reset role;
insert into public.financial_accounts(id,user_id,name,type) values('b0000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002','Ví B','cash');
set local role authenticated;
select is((select count(*)::integer from public.financial_accounts),2,'RLS only returns current user accounts');

insert into public.transactions(id,user_id,account_id,category_id,type,amount,transaction_at,status)
select 'd0000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000001',id,'income',500,now(),'confirmed'
from public.categories where user_id='10000000-0000-0000-0000-000000000001' and name='Lương';
select is((select current_balance from public.financial_accounts where id='a0000000-0000-0000-0000-000000000001'),1500::bigint,'confirmed income updates balance once');
update public.transactions set status='excluded' where id='d0000000-0000-0000-0000-000000000001';
select is((select current_balance from public.financial_accounts where id='a0000000-0000-0000-0000-000000000001'),1000::bigint,'status change reverses balance effect');
delete from public.transactions where id='d0000000-0000-0000-0000-000000000001';
select is((select current_balance from public.financial_accounts where id='a0000000-0000-0000-0000-000000000001'),1000::bigint,'deleting excluded transaction does not alter balance');
select lives_ok($$select public.transfer_between_accounts('a0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000002',300,now(),'Test')$$,'transfer RPC succeeds atomically');
select results_eq($$select current_balance from public.financial_accounts where id in('a0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000002') order by id$$,$$values(700::bigint),(2300::bigint)$$,'transfer applies exactly one debit and credit');
select throws_ok($$update public.financial_accounts set current_balance=999 where id='a0000000-0000-0000-0000-000000000001'$$,'P0001','current_balance is managed by transaction triggers','client cannot edit current balance');
select throws_ok($$insert into public.sepay_raw_events(sepay_event_id,event_type,payload,payload_hash) values('evt-client','transaction','{}','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')$$,'42501',null,'Android role cannot write raw SePay events');

reset role;
insert into public.sepay_raw_events(id,user_id,sepay_event_id,event_type,payload,payload_hash)
values('e0000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','evt-1','transaction','{}','bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb');
select throws_ok($$insert into public.sepay_raw_events(sepay_event_id,event_type,payload,payload_hash) values('evt-1','transaction','{}','cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc')$$,'23505',null,'duplicate SePay event id is rejected');
select throws_ok($$insert into public.transactions(user_id,account_id,type,amount,transaction_at,source,status,transfer_group_id,transfer_direction,raw_event_id) values('10000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000001','transfer',10,now(),'sepay','confirmed',gen_random_uuid(),'in','e0000000-0000-0000-0000-000000000001'),('10000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000002','transfer',10,now(),'sepay','confirmed',gen_random_uuid(),'in','e0000000-0000-0000-0000-000000000001')$$,'23505',null,'one raw event cannot create two transactions');

select * from finish();
rollback;
