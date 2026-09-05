insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 21,
 '0.12.6',
 'https://github.com/outbox04s/taichinh/releases/download/v0.12.6/taichinh-v0.12.6-debug.apk',
 'Bổ sung chọn ngày trả lãi hàng tháng từ 1 đến 31 và tự tính kỳ trả lãi tiếp theo.',
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
