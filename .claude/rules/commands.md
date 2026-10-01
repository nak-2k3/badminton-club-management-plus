# Lệnh chạy, build, test (Windows / PowerShell)

## Backend (thư mục gốc)
```powershell
$env:DB_PASSWORD = "..."        # bắt buộc trước khi chạy
.\mvnw.cmd spring-boot:run      # chạy ứng dụng ở :8080
.\mvnw.cmd test                 # chạy test (cần DB đang chạy)
.\mvnw.cmd test "-Dtest=ClubmanagementApplicationTests#contextLoads"   # chạy 1 test
.\mvnw.cmd compile              # kiểm tra biên dịch nhanh, không cần DB
.\mvnw.cmd clean package        # build jar
```
- Biến môi trường: `DB_PASSWORD`, `DB_USERNAME` (mặc định root), `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` — giá trị mặc định nằm trong `application.properties`, chỉ dùng cho môi trường phát triển.
- Test hiện chỉ có `contextLoads` (`@SpringBootTest`), nó kết nối DB thật và chạy `ddl-auto=validate` — cách nhanh nhất để phát hiện entity lệch schema.
- Lỗi `Port 8080 was already in use` thường do một instance cũ còn chạy; có thể chạy thêm ở cổng khác bằng `$env:SERVER_PORT = "8081"` (khi đó frontend dùng `$env:API_TARGET = "http://localhost:8081"`).
- Cảnh báo `sun.misc.Unsafe ... lombok` khi build là vô hại.

## Frontend (`frontend/`)
```powershell
cd frontend
npm install        # lần đầu
npm run dev        # http://localhost:5173 (backend phải đang chạy ở :8080)
npm run build      # kiểm tra lỗi / build ra dist/
```
Frontend chưa có lint hay test tự động — `npm run build` là bước kiểm tra lỗi.
