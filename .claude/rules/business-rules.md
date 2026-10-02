# Nghiệp vụ CLB cầu lông

Áp dụng cho cả backend (service, validation) và frontend (form, hiển thị).

## Các bảng
| Bảng | Vai trò |
|---|---|
| `users`, `roles`, `levels` | Thành viên, quyền (1 user – 1 role), trình độ |
| `courts` | **Địa điểm / nhà thi đấu** (địa chỉ, `hourly_rate` giá 1 sân/giờ), không phải 1 sân đơn lẻ |
| `schedules` | Buổi chơi (`FIXED` cố định / `EXTRA` phát sinh) tại 1 địa điểm, thuê `court_count` sân |
| `registrations`, `attendances` | Thành viên đăng ký buổi chơi và điểm danh (2 bảng riêng) |
| `guests`, `guest_registrations` | Khách vãng lai; đăng ký + phí + thu tiền + điểm danh gộp trong 1 bảng |
| `fee_settings` | Mức phí theo `fee_type`: `MONTHLY` phí tháng (theo giới tính) / `GUEST` phí khách mỗi buổi (như nhau, `gender` = NULL) |
| `payments` | Khoản thu của thành viên: `MONTHLY` / `EXTRA`, hình thức `CASH` / `BANK_TRANSFER` |
| `expenses` | Khoản chi, phân loại `category` (`COURT_RENT`, `SHUTTLECOCK`, `EQUIPMENT`, `OTHER`), có thể gắn `schedule_id` |

## Vai trò
- `ADMIN` quản trị toàn bộ; `TREASURER` quản lý thu chi; `MEMBER` thành viên.

## Quy tắc
- Giới tính **chỉ có `MALE` và `FEMALE`**. `users.gender` NOT NULL; `guests.gender` cho phép NULL.
- Mức phí lấy từ `fee_settings` (admin chỉnh trên giao diện), **không hard-code trong Java**: chọn dòng `active = 1` khớp điều kiện, có `effective_from` gần nhất và ≤ ngày áp dụng.
  - Phí tháng thành viên: `fee_type = MONTHLY` và `gender = users.gender` (bắt buộc có gender).
  - Phí khách: `fee_type = GUEST` và `gender IS NULL` — một mức chung cho mọi khách, không phụ thuộc `guests.gender`. Chép vào `guest_registrations.fee` lúc đăng ký (để đổi giá sau không ảnh hưởng dữ liệu cũ).
- Payment `MONTHLY` phải có `month` (1–12) và `year`; không thu trùng 1 tháng cho 1 user (đã có unique + CHECK trong DB).
- Khi đánh dấu đã thu tiền (`payments.status` hoặc `guest_registrations.payment_status` = `PAID`) phải ghi `paid_at`; với khách ghi thêm `collected_by` (người thu). `invited_by` là thành viên dẫn khách — dùng để truy trách nhiệm khi khách chưa trả.
- Tiền thuê sân 1 buổi = `courts.hourly_rate` × số giờ (`end_time − start_time`) × `schedules.court_count`. Khoản chi tiền sân ghi vào `expenses` với `category = COURT_RENT` và `schedule_id` của buổi đó.
- `schedules`: DB đã CHECK `end_time > start_time`; validate thêm ở tầng Java `court_count ≥ 1`, `max_players ≥ 1`.
- Khi kiểm tra `schedules.max_players`, đếm cả `registrations` (status `REGISTERED`) lẫn `guest_registrations`.
- Báo cáo thu: tổng `payments` + `guest_registrations` đã `PAID`. Báo cáo chi: `expenses` group theo `category`.
- Không xóa cứng user — đổi `status` sang `INACTIVE`/`LOCKED` (các bảng khác tham chiếu `users` bằng FK).
- **Sân**: tên sân không trùng (không phân biệt hoa thường/dấu). Chỉ xóa được sân chưa có lịch chơi; đã có lịch thì **tạm ngưng** (`active = 0`) — sân tạm ngưng không được chọn khi tạo lịch mới, lịch cũ giữ nguyên.
- **Mức phí**: đổi giá bằng cách **thêm dòng mới** với `effective_from` mới (giữ lịch sử), không sửa đè; không xóa mức phí, chỉ ngưng (`active = 0`) — khi ngưng, mức trước đó tự áp dụng lại. Không được có 2 dòng active trùng `fee_type` + `gender` + `effective_from`. Mức đang áp dụng tính bằng `FeeSettingService.findEffective(feeType, gender, date)` — dùng hàm này khi tạo học phí tháng / phí khách.
- **Tài khoản của tôi**: người dùng tự sửa họ tên, SĐT, giới tính, ngày sinh, địa chỉ; email, vai trò, trình độ, trạng thái, ngày tham gia chỉ ADMIN sửa. Đổi mật khẩu phải nhập đúng mật khẩu hiện tại, mật khẩu mới khác mật khẩu cũ.
- Cột `phone` có UNIQUE và cho phép NULL: lưu `NULL` khi trống, không lưu chuỗi rỗng.
