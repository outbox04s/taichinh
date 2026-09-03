alter table public.financial_accounts
 add column purpose text check(purpose is null or char_length(purpose) between 1 and 120);
