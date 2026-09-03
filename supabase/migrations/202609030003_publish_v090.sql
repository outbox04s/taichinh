insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 9,
 '0.9.0',
 'https://github.com/outbox04s/taichinh/releases/download/v0.9.0/taichinh-v0.9.0-debug.apk',
 'Nâng cấp toàn bộ trang Tổng quan theo phong cách Liquid Glass: bố cục mới, biểu đồ dòng tiền, sức khỏe tài chính, empty state, thanh điều hướng nổi và cải thiện cập nhật APK trên Android 8–16.',
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
