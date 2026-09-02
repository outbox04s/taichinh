begin;

create table public.app_releases (
 id bigint generated always as identity primary key,
 version_code bigint not null unique check (version_code > 0),
 version_name text not null check (length(trim(version_name)) > 0),
 apk_url text not null check (apk_url ~ '^https://'),
 release_notes text not null default '',
 is_mandatory boolean not null default false,
 is_published boolean not null default false,
 published_at timestamptz not null default now(),
 created_at timestamptz not null default now()
);

alter table public.app_releases enable row level security;
create policy app_releases_public_read on public.app_releases
 for select to anon, authenticated using (is_published);
grant select on public.app_releases to anon, authenticated;

commit;

-- Sau khi tải APK đã ký lên một URL HTTPS, công bố bản mới bằng SQL Editor:
-- insert into public.app_releases(version_code,version_name,apk_url,release_notes,is_mandatory,is_published)
-- values (3,'0.3.0','https://example.com/app-release.apk','Nội dung cập nhật',false,true);
