insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 20,
 '0.12.5',
 'https://github.com/outbox04s/taichinh/releases/download/v0.12.5/taichinh-v0.12.5-debug.apk',
 'Khôi phục hình thức trả lãi cho khoản vay hiện có và tối ưu chuyển động thanh menu dưới mượt hơn.',
 false,
 true
)
on conflict (version_code) do update set
 version_name=excluded.version_name,
 apk_url=excluded.apk_url,
 release_notes=excluded.release_notes,
 is_mandatory=excluded.is_mandatory,
 is_published=excluded.is_published,
 published_at=now();
