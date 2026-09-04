begin;

alter table public.debts add column if not exists is_archived boolean not null default false;

create or replace function public.archive_financial_account(p_account_id uuid)
returns void language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();
begin
 if v_user is null then raise exception 'Authentication required';end if;
 update public.financial_accounts set is_active=false
 where id=p_account_id and user_id=v_user and is_active=true;
 if not found then raise exception 'Active account not found';end if;
end $$;

create or replace function public.archive_debt(p_debt_id uuid)
returns void language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid();
begin
 if v_user is null then raise exception 'Authentication required';end if;
 update public.debts set is_archived=true
 where id=p_debt_id and user_id=v_user and is_archived=false;
 if not found then raise exception 'Debt not found';end if;
end $$;

grant execute on function public.archive_financial_account(uuid) to authenticated;
grant execute on function public.archive_debt(uuid) to authenticated;
commit;
