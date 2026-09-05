insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 23,
 '0.13.1',
 'https://github.com/outbox04s/taichinh/releases/download/v0.13.1/taichinh-v0.13.1-debug.apk',
 'Báo cáo bắt đầu từ tài sản khả dụng hiện có và cộng dồn số dư cuối tháng sang tháng tiếp theo, kể cả số âm. Không cộng lại tài sản khả dụng ở các tháng sau. Loại khoản thu đã ghi nhận, phần nợ đã thanh toán và chi phí cố định đã đối chiếu khỏi dòng tiền còn lại. Thêm cập nhật số dư và báo cáo.',
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
