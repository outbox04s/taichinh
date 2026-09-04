insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 18,
 '0.12.3',
 'https://github.com/outbox04s/taichinh/releases/download/v0.12.3/taichinh-v0.12.3-debug.apk',
 'Bỏ loại khỏi báo cáo và xóa mềm; giao dịch, tài khoản và khoản nợ được xóa hẳn khỏi database sau khi xác nhận; tự xử lý số dư, chuyển khoản và phân bổ trả nợ liên quan.',
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
