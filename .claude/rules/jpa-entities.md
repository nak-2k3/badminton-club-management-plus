---
paths:
  - "src/main/java/**/entity/**"
  - "src/main/java/**/enums/**"
  - "src/main/java/**/repository/**"
---

# Entity & Repository (JPA / Hibernate)

- Entity map đúng tên bảng/cột snake_case của schema (`@Table`, `@Column`); khóa chính `Long` với `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- Cột `ENUM` của MySQL → Java enum với `@Enumerated(EnumType.STRING)`; tên hằng giống hệt giá trị trong DB. Dùng chung một enum `Gender { MALE, FEMALE }`.
- Kiểu dữ liệu: `tinyint(1)` → `Boolean`; `decimal` → `BigDecimal`; `date` → `LocalDate`; `time` → `LocalTime`; `datetime` → `LocalDateTime`. Cột số nhỏ hơn `int` (vd `payments.month` TINYINT, `year` SMALLINT) map sang `Integer` thì kèm `columnDefinition = "TINYINT"/"SMALLINT"` để `validate` so khớp đúng kiểu cột.
- Giá trị mặc định của cột (status, active, ...) phải gán sẵn trong field entity, vì Hibernate insert `NULL` chứ không dùng DEFAULT của MySQL; cột thời điểm tạo dùng `@CreationTimestamp`.
- Quan hệ `@ManyToOne` dùng `fetch = FetchType.LAZY`.
- Repository cần dữ liệu quan hệ để map DTO thì dùng `@EntityGraph` (vì `open-in-view=false`).
- Lombok: dùng `@Getter`/`@Setter` cho entity, tránh `@Data` (gây lỗi `equals`/`hashCode`/`toString` với quan hệ hai chiều).
- Sửa entity xong, chạy `contextLoads` (xem rule commands) để `ddl-auto=validate` kiểm tra khớp schema.
