---
paths:
  - "database/**"
  - "src/main/java/**/entity/**"
  - "src/main/resources/application*.properties"
---

# Database MySQL (database-first)

- MySQL 9.7, database `badminton_club_plus` (utf8mb4).
- **Schema là nguồn chuẩn**: `database/schema.sql`. Không để Hibernate tự sinh/sửa bảng — dùng `spring.jpa.hibernate.ddl-auto=validate` (hoặc `none`), không dùng `update`/`create`.
- Thay đổi schema: viết lệnh `ALTER` thành file migration trong `database/`, chạy trên MySQL, rồi xuất lại `schema.sql`:
  ```powershell
  & "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysqldump.exe" -u root -p --no-data --routines --set-gtid-purged=OFF --result-file=database\schema.sql badminton_club_plus
  ```
  Luôn có `--set-gtid-purged=OFF` (nếu không file lỗi khi import trên máy khác) và dùng `--result-file` thay vì `>` (PowerShell `>` ghi UTF-16).
- `database/data.sql` chứa dữ liệu danh mục (`roles`, `levels`, `fee_settings`, `courts`) — bắt buộc phải có: thiếu role `ADMIN` thì `DataInitializer` báo lỗi khi khởi động. Khi đổi dữ liệu danh mục, xuất lại bằng:
  ```powershell
  & "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysqldump.exe" -u root -p --no-create-info --set-gtid-purged=OFF --result-file=database\data.sql badminton_club_plus roles levels fee_settings courts
  ```
- Cài DB trên máy mới: tạo database `badminton_club_plus` (utf8mb4), import `schema.sql` rồi `data.sql`.
