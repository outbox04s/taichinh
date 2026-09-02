insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 3,
 '0.3.0',
 'https://github.com/outbox04s/taichinh/releases/download/v0.3.0/taichinh-v0.3.0-debug.apk',
 'Sửa lỗi tạo khoản nợ và kiểm tra định dạng ngày đáo hạn.',
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
