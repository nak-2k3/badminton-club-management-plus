---
paths:
  - "src/main/java/**/config/**"
  - "src/main/java/**/security/**"
  - "src/main/java/**/controller/**"
  - "src/main/java/**/service/**"
  - "src/main/resources/application*.properties"
  - "frontend/src/api/**"
  - "frontend/src/stores/**"
  - "frontend/src/router/**"
---

# Bảo mật, đăng nhập & phân quyền

- Đăng nhập bằng **email + mật khẩu** (BCrypt). `POST /api/auth/login` trả `accessToken` (JWT HS256, hạn `app.jwt.expiration-minutes`); `GET /api/auth/me` trả người dùng hiện tại.
- Stateless: không session, không CSRF, không form login. Frontend gửi header `Authorization: Bearer <token>`.
- JWT: `sub` = email, claim `userId`, claim `roles` = [`role_name`] (chỉ để tham khảo, **không** dùng để phân quyền).
- Mỗi request có token, `security/JwtUserAuthenticationConverter` đọc DB theo `userId` (projection `UserAuthView`, 1 truy vấn nhỏ): tài khoản `LOCKED`/`INACTIVE`/không còn tồn tại → 401 kèm lý do ("Tài khoản đã bị khóa"...), frontend tự đăng xuất; quyền `ROLE_ADMIN` / `ROLE_TREASURER` / `ROLE_MEMBER` lấy theo vai trò **hiện tại trong DB**. Nhờ vậy khóa tài khoản và đổi vai trò có hiệu lực ngay ở request tiếp theo, không chờ token hết hạn.
- Phân quyền 2 lớp: khai báo nhóm URL trong `SecurityConfig` (vd `.requestMatchers("/api/members/**").hasRole("ADMIN")`) **và** `@PreAuthorize("hasRole('ADMIN')")` trên controller (đã bật `@EnableMethodSecurity`). Lớp URL là bắt buộc: `@Valid @RequestBody` chạy trước `@PreAuthorize`, nên thiếu nó thì người không có quyền nhận 400 kèm chi tiết lỗi thay vì 403. Lấy người dùng hiện tại trong controller: `@AuthenticationPrincipal Jwt jwt` → `CurrentUser.id(jwt)` (không dùng email ở `sub` vì email có thể bị sửa).
- Ma trận quyền hiện tại (khai báo trong `SecurityConfig`, giữ thứ tự: quy tắc GET riêng đặt trước quy tắc chung của cùng đường dẫn):
  | Đường dẫn | Xem (GET) | Thêm/sửa/xóa |
  |---|---|---|
  | `/api/members/**` | ADMIN | ADMIN |
  | `/api/courts/**` | mọi người đã đăng nhập | ADMIN |
  | `/api/fee-settings/**` | ADMIN, TREASURER | ADMIN |
  | `/api/account/**`, `/api/auth/me`, `/api/roles`, `/api/levels` | mọi người đã đăng nhập | chính chủ (`/api/account`) |
- Admin không được tự hạ vai trò hay tự khóa/ngừng hoạt động tài khoản của chính mình (chặn ở `MemberService`).
- Phía frontend: menu và route guard dựa trên `auth.user.role` lấy lúc tải trang; nếu vai trò bị đổi khi đang mở trang, giao diện chỉ cập nhật sau khi tải lại, nhưng backend đã chặn/cho phép đúng ngay.
- Endpoint public chỉ có `POST /api/auth/login`; thêm endpoint public mới phải khai báo trong `SecurityConfig`.
- CORS cho `app.cors.allowed-origins` (mặc định `http://localhost:5173`) trên `/api/**`.
- `DataInitializer` tự tạo tài khoản ADMIN khi bảng `users` trống.
- Phía frontend: token lưu ở `localStorage` (`accessToken`); khi tải lại trang, router guard gọi `/api/auth/me` để lấy lại user; `api/http.js` gặp 401 thì tự đăng xuất về `/login`.
