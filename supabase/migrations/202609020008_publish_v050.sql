insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 5,
 '0.5.0',
 'https://github.com/outbox04s/taichinh/releases/download/v0.5.0/taichinh-v0.5.0-debug.apk',
 'Thêm popup cập nhật gồm tên phiên bản, mã cập nhật, nội dung và lựa chọn UPDATE hoặc Hủy bỏ.',
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
