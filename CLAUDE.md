# CLAUDE.md

Hệ thống quản lý câu lạc bộ cầu lông (tiểu luận). Người dùng giao tiếp bằng tiếng Việt — trả lời bằng tiếng Việt.

## Kiến trúc: SPA
- **Backend** (thư mục gốc): REST API JSON, chạy ở `:8080`, mọi endpoint bắt đầu bằng `/api/`.
- **Frontend** (`frontend/`, sẽ tạo): Vue 3 + Vite + Pinia + Vue Router + Element Plus + Axios, chạy ở `:5173`. Chỉ dùng Element Plus (không trộn Vuetify).
- Khi nộp bài có thể build Vue vào `src/main/resources/static` để chạy 1 file jar.

## Công nghệ backend
- Spring Boot **4.1.1** (không phải 3.x), Java 17, Maven (dùng `mvnw` / `mvnw.cmd`)
- Spring Web MVC, Spring Data JPA, Spring Security + JWT (`spring-boot-starter-security-oauth2-resource-server`), Validation, Lombok
- Jackson 3 (package `tools.jackson`, không phải `com.fasterxml.jackson` — trừ annotation)
- MySQL 9.7, database `badminton_club_plus`
- Package gốc: `com.badmintonclub.clubmanagement`

## Lệnh thường dùng (Windows)
```powershell
$env:DB_PASSWORD = "..."        # bắt buộc trước khi chạy
.\mvnw.cmd spring-boot:run      # chạy ứng dụng
.\mvnw.cmd test                 # chạy test (cần DB)
.\mvnw.cmd clean package        # build jar
```
Biến môi trường: `DB_PASSWORD`, `DB_USERNAME` (mặc định root), `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` — giá trị mặc định nằm trong `application.properties`, chỉ dùng cho môi trường phát triển.

## Bảo mật & API
- Đăng nhập bằng **email + mật khẩu** (BCrypt). `POST /api/auth/login` trả `accessToken` (JWT HS256, hạn `app.jwt.expiration-minutes`); `GET /api/auth/me` trả người dùng hiện tại.
- Stateless: không session, không CSRF, không form login. Frontend gửi header `Authorization: Bearer <token>`.
- JWT: `sub` = email, claim `userId`, claim `roles` = [`role_name`] → quyền `ROLE_ADMIN` / `ROLE_TREASURER` / `ROLE_MEMBER`. Phân quyền bằng `@PreAuthorize("hasRole('ADMIN')")` (đã bật `@EnableMethodSecurity`). Lấy người dùng hiện tại trong controller: `@AuthenticationPrincipal Jwt jwt` → `jwt.getSubject()`.
- Vai trò: `ADMIN` quản trị toàn bộ; `TREASURER` quản lý thu chi; `MEMBER` thành viên.
- Endpoint public chỉ có `POST /api/auth/login`; thêm endpoint public mới phải khai báo trong `SecurityConfig`.
- CORS cho `app.cors.allowed-origins` (mặc định `http://localhost:5173`) trên `/api/**`.
- `DataInitializer` tự tạo tài khoản ADMIN khi bảng `users` trống.
- Lỗi trả JSON thống nhất `ErrorResponse { status, message, errors, timestamp }` qua `GlobalExceptionHandler`; `errors` là map lỗi theo trường khi `@Valid` thất bại. Ném `BusinessException` (400) cho vi phạm nghiệp vụ, `ResourceNotFoundException` (404) khi không tìm thấy. Thông báo lỗi viết bằng tiếng Việt.

## Database
- **Schema là nguồn chuẩn**: `database/schema.sql` (database-first). Không để Hibernate tự sinh/sửa bảng — dùng `spring.jpa.hibernate.ddl-auto=validate` (hoặc `none`), không dùng `update`/`create`.
- Thay đổi schema: viết lệnh `ALTER` thành file migration trong `database/`, chạy trên MySQL, rồi xuất lại `schema.sql`:
  ```powershell
  & "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysqldump.exe" -u root -p --no-data --routines --set-gtid-purged=OFF --result-file=database\schema.sql badminton_club_plus
  ```
  Luôn có `--set-gtid-purged=OFF` (nếu không file lỗi khi import trên máy khác) và dùng `--result-file` thay vì `>` (PowerShell `>` ghi UTF-16).
- Dữ liệu danh mục (`roles`, `levels`, `fee_settings`, `courts`) cần có để app chạy được — đặt trong `database/data.sql`.
- Không commit mật khẩu DB. Trong `application.properties` dùng biến môi trường, ví dụ `spring.datasource.password=${DB_PASSWORD}`.

### Các bảng
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

### Quy tắc nghiệp vụ
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
- Cột `phone` có UNIQUE và cho phép NULL: lưu `NULL` khi trống, không lưu chuỗi rỗng.

## Quy ước code
- Phân tầng: `entity` → `repository` → `service` → `controller`, kèm `dto`, `enums`, `config`, `security`, `exception` dưới package gốc.
- Controller là `@RestController` mỏng, nhận/trả DTO; logic nghiệp vụ và `@Transactional` nằm ở service.
- DTO viết bằng Java `record`, đặt theo nhóm chức năng (`dto/auth`, `dto/user`, ...), có hàm tĩnh `from(Entity)` để chuyển đổi; ràng buộc đầu vào bằng Jakarta Validation với `message` tiếng Việt.
- Danh sách lớn (thành viên, thu, chi) trả về có phân trang bằng `Pageable`.
- Giá trị mặc định của cột (status, active, ...) phải gán sẵn trong field entity, vì Hibernate insert `NULL` chứ không dùng DEFAULT của MySQL; cột thời điểm tạo dùng `@CreationTimestamp`.
- Repository cần dữ liệu quan hệ để map DTO thì dùng `@EntityGraph` (vì `open-in-view=false`).
- Entity map đúng tên bảng/cột snake_case của schema (`@Table`, `@Column`); khóa chính `Long` với `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- Cột `ENUM` của MySQL → Java enum với `@Enumerated(EnumType.STRING)`; tên hằng giống hệt giá trị trong DB. Dùng chung một enum `Gender { MALE, FEMALE }`.
- `tinyint(1)` → `Boolean`; `decimal` → `BigDecimal`; `date` → `LocalDate`; `time` → `LocalTime`; `datetime` → `LocalDateTime`.
- Quan hệ `@ManyToOne` dùng `fetch = FetchType.LAZY`. Không trả entity trực tiếp ra controller — dùng DTO.
- Mật khẩu mã hóa bằng `BCryptPasswordEncoder`.
- Lombok: dùng `@Getter`/`@Setter` cho entity, tránh `@Data` (gây lỗi `equals`/`hashCode`/`toString` với quan hệ hai chiều).
