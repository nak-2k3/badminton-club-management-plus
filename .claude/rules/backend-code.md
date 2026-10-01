---
paths:
  - "src/**/*.java"
  - "pom.xml"
---

# Quy ước code backend (Spring Boot)

## Công nghệ
- Spring Boot **4.1.1** (không phải 3.x), Java 17, Maven (dùng `mvnw` / `mvnw.cmd`).
- Spring Web MVC, Spring Data JPA, Spring Security + JWT (`spring-boot-starter-security-oauth2-resource-server`), Validation, Lombok.
- Jackson 3 (package `tools.jackson`, không phải `com.fasterxml.jackson` — trừ annotation).
- Package gốc: `com.badmintonclub.clubmanagement`.

## Phân tầng
- `entity` → `repository` → `service` → `controller`, kèm `dto`, `enums`, `config`, `security`, `exception` dưới package gốc.
- Controller là `@RestController` mỏng, mọi endpoint bắt đầu bằng `/api/`, nhận/trả DTO (không trả entity); logic nghiệp vụ và `@Transactional` nằm ở service.
- DTO viết bằng Java `record`, đặt theo nhóm chức năng (`dto/auth`, `dto/user`, ...), có hàm tĩnh `from(Entity)` để chuyển đổi; ràng buộc đầu vào bằng Jakarta Validation với `message` tiếng Việt.
- Danh sách lớn (thành viên, thu, chi) trả về `PageResponse<T>` (`dto/common`, `page` đếm từ 0) — không trả thẳng `Page`. Controller nhận `page`, `size`, `sort`, `direction` bằng `@RequestParam`; service giới hạn `size` ≤ 100 và chỉ cho sắp xếp theo danh sách cột cho phép (xem `MemberService`). Tìm kiếm động dùng `Specification` (repository kế thừa `JpaSpecificationExecutor`, override `findAll(Specification, Pageable)` kèm `@EntityGraph`).
- Tìm kiếm `LIKE`: escape `%`, `_`, `\` trong từ khóa; collation `utf8mb4_unicode_ci` đã không phân biệt hoa thường và dấu nên không cần `lower()`.
- **Ngày trong JSON dùng định dạng `dd/MM/yyyy`** (`@JsonFormat(pattern = "dd/MM/yyyy")` trên field `LocalDate` của DTO cả request lẫn response; `LocalDateTime` dùng `dd/MM/yyyy HH:mm`). `@JsonFormat` import từ `com.fasterxml.jackson.annotation`.
- Danh mục cho ô chọn trả `OptionResponse { id, name, description }` (xem `CatalogController`: `/api/roles`, `/api/levels`).
- Lấy id người đang đăng nhập: `CurrentUser.id(jwt)` (claim `userId`) — dùng id thay vì email vì email có thể bị sửa.
- Chuẩn hóa input trong service: `trim()`, email về chữ thường, chuỗi rỗng → `null` (cột UNIQUE như `phone`).

## Xử lý lỗi
- Lỗi trả JSON thống nhất `ErrorResponse { status, message, errors, timestamp }` qua `GlobalExceptionHandler`; `errors` là map lỗi theo trường khi `@Valid` thất bại.
- Ném `BusinessException` (400) cho vi phạm nghiệp vụ, `ResourceNotFoundException` (404) khi không tìm thấy. Thông báo lỗi viết bằng tiếng Việt.
- Lỗi nghiệp vụ gắn với một ô nhập (email/SĐT trùng, vai trò không tồn tại...) dùng `new BusinessException("tenTruong", "thông báo")` → `errors` có `{ tenTruong: thông báo }` để frontend hiện dưới đúng ô; tên trường trùng tên field trong request DTO.
- `GlobalExceptionHandler` đã xử lý sẵn: JSON sai định dạng/ngày sai/enum lạ (400, gắn lỗi vào đúng trường), tham số URL sai kiểu (400), URL không tồn tại (404), sai phương thức (405), vi phạm ràng buộc DB (409). 401/403 ở tầng filter do `SecurityConfig` ghi cùng định dạng `ErrorResponse`.
