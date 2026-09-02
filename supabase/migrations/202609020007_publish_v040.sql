insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 4,
 '0.4.0',
 'https://github.com/outbox04s/taichinh/releases/download/v0.4.0/taichinh-v0.4.0-debug.apk',
 'Thay nhập ngày thủ công bằng bộ chọn lịch ngày, tháng và năm.',
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
