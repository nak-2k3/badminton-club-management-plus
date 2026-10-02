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
- **Nhất quán**: dùng component Element Plus và các mẫu sẵn có (`MemberListView`, `MemberDetailView`, `MemberFormDialog`) làm khuôn cho trang mới; giữ hiệu ứng chuyển trang nhẹ và tôn trọng `prefers-reduced-motion`.
- Chi tiết kỹ thuật xem `.claude/rules/frontend.md`.

## Luồng một request
`views/*.vue` → `frontend/src/api/*.js` (instance `http.js` gắn Bearer token) → Vite proxy `/api` → `SecurityConfig` (giải mã JWT, claim `roles` → `ROLE_*`) → `@RestController` → service (`@Transactional`, ném `BusinessException`/`ResourceNotFoundException`) → repository → MySQL. Lỗi đi ngược lại qua `GlobalExceptionHandler` → `ErrorResponse` → `http.js` chuẩn hóa thành `{ status, message, errors }` → view hiện `ElMessage` / lỗi dưới ô nhập.

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
