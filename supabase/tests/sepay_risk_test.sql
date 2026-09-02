begin;
create extension if not exists pgtap with schema extensions;
select plan(9);
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_user_meta_data,created_at,updated_at)
values('00000000-0000-0000-0000-000000000000','30000000-0000-0000-0000-000000000003','authenticated','authenticated','sepay@test.local','x',now(),'{}',now(),now());
insert into public.financial_accounts(id,user_id,name,type,sepay_account_id)
values('c0000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000003','SePay','bank','1017588888');
select is((select count(*)::int from public.risk_settings where user_id='30000000-0000-0000-0000-000000000003'),1,'signup creates risk settings');
select lives_ok($$select public.process_sepay_event('92704','{"id":92704,"transferType":"in","transferAmount":10000}'::jsonb,repeat('a',64),'1017588888','in',10000,now(),'Tien vao','FT1')$$,'incoming webhook is processed');
select is((select count(*)::int from public.transactions where external_id='92704'),1,'incoming webhook creates one transaction');
select is((select type from public.transactions where external_id='92704'),'income','incoming event maps to income');
select lives_ok($$select public.process_sepay_event('92704','{"id":92704,"transferType":"in","transferAmount":10000}'::jsonb,repeat('a',64),'1017588888','in',10000,now(),'Tien vao','FT1')$$,'duplicate webhook returns successfully');
select is((select count(*)::int from public.transactions where external_id='92704'),1,'duplicate does not create another transaction');
select lives_ok($$select public.process_sepay_event('92705','{"id":92705,"transferType":"out","transferAmount":5000}'::jsonb,repeat('b',64),'1017588888','out',5000,now(),'Tien ra','FT2')$$,'outgoing webhook is processed');
select is((select type from public.transactions where external_id='92705'),'expense','outgoing event maps to expense');
select lives_ok($$select public.process_sepay_event('92706','{"id":92706,"transferType":"in","transferAmount":1000}'::jsonb,repeat('c',64),'unmapped','in',1000,now(),'Unknown','FT3')$$,'unmapped account is retained for review');
select * from finish();
rollback;
