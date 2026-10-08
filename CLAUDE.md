# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Hệ thống quản lý câu lạc bộ cầu lông (tiểu luận). Người dùng giao tiếp bằng tiếng Việt — trả lời bằng tiếng Việt.

## Kiến trúc: SPA
- **Backend** (thư mục gốc): Spring Boot 4 REST API JSON, chạy ở `:8080`, mọi endpoint bắt đầu bằng `/api/`.
- **Frontend** (`frontend/`): Vue 3 + Vite + Element Plus, chạy ở `:5173`.
- **Database**: MySQL `badminton_club_plus`, schema chuẩn ở `database/schema.sql`.

## Nguyên tắc giao diện: dễ sử dụng, thân thiện với điện thoại
Giao diện hiện tại đã được người dùng duyệt — tính năng mới phải giữ cùng phong cách và mức độ hoàn thiện:
- **Dễ sử dụng**: mọi chữ, nhãn, thông báo bằng tiếng Việt; thao tác quan trọng (khóa, xóa, đăng xuất...) luôn có hộp xác nhận; lỗi hiện ngay dưới ô nhập; có trạng thái đang tải và trạng thái rỗng; thao tác thường dùng chỉ cần 1–2 lần bấm.
- **Mobile friendly** (kiểm tra ở ~500px và 1280px): không tràn ngang thân trang; bảng nhiều cột cuộn ngang bên trong khung, cột thao tác cố định bên phải; bộ lọc/nút tự xuống hàng; dialog không rộng quá màn hình; vùng bấm đủ lớn.
- **Sáng/tối**: mọi màn hình mới phải đọc rõ ở cả 2 chế độ — chỉ dùng biến màu `--el-*`, không viết mã màu cứng.
- **Nhất quán**: dùng component Element Plus và các mẫu sẵn có làm khuôn cho trang mới (danh sách lớn: `MemberListView`/`MemberDetailView`/`MemberFormDialog`; danh sách nhỏ: `CourtListView`; mức phí: `FeeSettingView` — xem `frontend.md`); giữ hiệu ứng chuyển trang nhẹ và tôn trọng `prefers-reduced-motion`.
- Chi tiết kỹ thuật xem `.claude/rules/frontend.md`.

## Tính nhất quán & an toàn: một hành động — một quy tắc ở mọi lối vào
Đã từng có lỗ hổng do cùng một hành động có 2 lối vào với quy tắc khác nhau (admin tự đổi email/mật khẩu ở Quản lý thành viên không cần mật khẩu, trong khi Tài khoản của tôi thì cần). Khi thêm/sửa bất kỳ chức năng nào:
- **Liệt kê mọi lối vào của cùng một hành động** (grep các endpoint/service cùng ghi một cột: email, password, role, status, số tiền...) và áp **cùng một quy tắc** cho tất cả. Ưu tiên gọi chung một hàm service thay vì viết lại logic (vd `FeeSettingService.findEffective`).
- **"Của mình" khác "của người khác"**: thao tác nhạy cảm trên chính tài khoản mình (email, mật khẩu) chỉ đi qua `/api/account/*` và **bắt buộc mật khẩu hiện tại**. Trang quản trị (`/api/members/*`) phải **chặn khi `id` = người đang đăng nhập** với mọi thao tác nhạy cảm — đổi email, đặt lại mật khẩu, đổi vai trò, khóa/ngừng hoạt động — và báo hướng sang Tài khoản của tôi.
- **Backend là nơi chặn thật** (service ném `BusinessException`); giao diện ẩn/khóa nút chỉ để dễ dùng. Không bao giờ chỉ chặn ở frontend.
- Cùng một dữ liệu thì **cùng kiểm tra và chuẩn hóa** ở mọi nơi nhận vào: dùng chung hằng/DTO trong `dto/common` (`Validation`, `DateFormats`), cùng trim/lowercase/rỗng → null, cùng tên trường lỗi và thông báo tiếng Việt.
- Khi kiểm thử, luôn có ca **"tự làm với chính mình"** và ca **"vai trò khác gọi thẳng API"** cho mỗi hành động, và chạy trên **mọi lối vào** của hành động đó.

## Luồng một request
`views/*.vue` → `frontend/src/api/*.js` (instance `http.js` gắn Bearer token) → Vite proxy `/api` → `SecurityConfig` (giải mã JWT, claim `roles` → `ROLE_*`) → `@RestController` → service (`@Transactional`, ném `BusinessException`/`ResourceNotFoundException`) → repository → MySQL. Lỗi đi ngược lại qua `GlobalExceptionHandler` → `ErrorResponse` → `http.js` chuẩn hóa thành `{ status, message, errors }` → view hiện `ElMessage` / lỗi dưới ô nhập.

## Tiến độ hiện tại
- Đã có entity cho đủ 12 bảng, đã làm xong trọn bộ (service + controller + giao diện) cho: đăng nhập, **thành viên**, **sân**, **mức phí**, **tài khoản của tôi**, **lịch chơi**, **đăng ký tham gia & điểm danh** (thành viên), **khách vãng lai** (dẫn khách, thu phí khách, điểm danh khách, trang Phí khách).
- Chưa làm: khoản thu (`payments`), khoản chi (`expenses`), báo cáo. Khi làm, nhớ thêm quy tắc URL vào `SecurityConfig` và cập nhật ma trận quyền trong `security-auth.md`.
- Đăng nhập thử ở môi trường phát triển: `admin@badmintonclub.local` / `Admin@123` (tạo tự động khi bảng `users` trống).

## Quy tắc chi tiết: `.claude/rules/`
| File | Chủ đề | Nạp khi |
|---|---|---|
| `commands.md` | Lệnh chạy, build, test, biến môi trường | Luôn luôn |
| `business-rules.md` | Các bảng, vai trò, quy tắc nghiệp vụ | Luôn luôn |
| `backend-code.md` | Công nghệ, phân tầng, DTO, xử lý lỗi | Làm việc với `src/**/*.java`, `pom.xml` |
| `security-auth.md` | JWT, đăng nhập, phân quyền, CORS | `config/`, `security/`, `controller/`, `service/`, properties, `frontend/src/{api,stores,router}` |
| `jpa-entities.md` | Map entity, enum, repository | `entity/`, `enums/`, `repository/` |
| `database.md` | Quy trình sửa schema, xuất dump | `database/`, `entity/`, properties |
| `frontend.md` | Cấu trúc & quy ước Vue | `frontend/**` |

## Subagent: `.claude/agents/`
- `code-reviewer` — soát thay đổi chưa commit theo các rule (chỉ đọc).
- `db-schema` — sửa DB theo quy trình: migration → chạy → xuất `schema.sql` → sửa entity → `contextLoads`.
- `api-tester` — chạy backend ở :8081, test API theo từng vai trò, tùy chọn chụp giao diện.
- `thesis-writer` — viết tài liệu tiểu luận vào `docs/` từ code thật.
