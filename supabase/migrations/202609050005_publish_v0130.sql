insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 22,
 '0.13.0',
 'https://github.com/outbox04s/taichinh/releases/download/v0.13.0/taichinh-v0.13.0-debug.apk',
 'Thêm quản lý chi phí cố định hàng tháng: thêm, sửa, xóa, tạm dừng và gán danh mục. Báo cáo kế hoạch 12 tháng dựa trên nguồn thu, chi phí cố định và lịch trả vay, cảnh báo tháng thiếu tiền và số còn cho chi tiêu khác. Xem tất cả cơ cấu chi tiêu mở tổng hợp theo danh mục, giữ bộ lọc thời gian và tài khoản.',
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
