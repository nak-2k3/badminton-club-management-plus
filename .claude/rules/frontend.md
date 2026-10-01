---
paths:
  - "frontend/**"
---

# Frontend (Vue 3 SPA)

- Vue 3 + Vite 8 + Pinia 4 + Vue Router 5 + Element Plus + Axios, JavaScript (không TypeScript), chạy ở `:5173`. Chỉ dùng Element Plus (không trộn Vuetify).
- Vite proxy `/api` → `http://localhost:8080` (đổi bằng biến môi trường `API_TARGET`), nên frontend luôn gọi đường dẫn tương đối `/api/...`. Proxy xóa header `Origin` nên chạy Vite ở cổng nào cũng không vướng CORS.
- Cấu trúc `src/`: `api/` (mỗi nhóm chức năng 1 file, dùng chung instance `http.js`), `stores/` (Pinia; `catalog.js` cache vai trò/trình độ), `router/`, `layouts/MainLayout.vue` (menu trái + header), `views/<nhóm>/` (mỗi trang 1 file `XxxView.vue`), `components/<nhóm>/` (dialog form dùng lại, vd `MemberFormDialog.vue` dùng chung cho thêm & sửa), `composables/` (thao tác dùng chung nhiều trang, vd `useMemberActions`), `utils/labels.js` (nhãn tiếng Việt + màu tag cho enum, `toOptions()` cho el-select), `styles/main.css` (màu chủ đạo xanh lá `#1d6f42`).
- Ngày: backend gửi/nhận chuỗi `dd/MM/yyyy` → `el-date-picker` dùng `format="DD/MM/YYYY" value-format="DD/MM/YYYY"`, hiển thị thẳng chuỗi nhận được, không tự format lại.
- Trang danh sách: đồng bộ bộ lọc/trang/sắp xếp lên query URL (`router.replace`) để quay lại từ trang chi tiết vẫn giữ trạng thái; `el-pagination` đếm từ 1, API đếm từ 0; ô tìm kiếm debounce 400ms; bảng bọc trong `overflow-x: auto`, cột thao tác `fixed="right"`.
- Trang con không có mục menu riêng (vd chi tiết) khai báo `meta.menu: '/members'` để tô sáng menu cha.
- `api/http.js`: tự gắn `Authorization: Bearer`, trả thẳng `response.data`, lỗi được chuẩn hóa thành `{ status, message, errors }` (khớp `ErrorResponse` backend).
- Trang mới: thêm route con trong `router/index.js` (`meta: { title, roles }`) và mục menu trong `MainLayout.vue` (`roles` để ẩn theo vai trò). Viết component bằng `<script setup>`; lỗi theo trường từ backend gắn vào `el-form-item :error`.
- Giao diện, thông báo đều bằng tiếng Việt; Element Plus đã cấu hình locale `vi`.
- **Sáng/tối (dark mode)**: class `dark` trên `<html>` (đặt sẵn bằng script trong `index.html` để không nháy màu), đổi qua `stores/theme.js` + `components/common/ThemeToggle.vue`, lưu ở `localStorage.theme`, mặc định theo hệ điều hành. Màu xanh chủ đạo cho cả 2 chế độ khai báo trong `styles/main.css` (`:root` và `html.dark`).
- **Không viết mã màu cứng trong component** — dùng biến Element Plus (`--el-bg-color`, `--el-bg-color-page`, `--el-text-color-primary/regular/secondary`, `--el-border-color-light`, `--el-fill-color-light`, `--el-color-primary`...) để tự đổi theo theme. Chữ phụ dùng class toàn cục `.text-secondary`.
- Không dùng avatar (hệ thống chưa có ảnh đại diện).
- Layout: menu trái thu gọn được (nhớ ở `localStorage.sidebarCollapsed`, tự thu gọn khi màn ≤ 768px qua `composables/useBreakpoint`); header ≤ 768px chỉ hiện biểu tượng người dùng. Trang trong layout có hiệu ứng chuyển trang `page` (`<transition mode="out-in">`, key theo `route.name`) ⇒ **mỗi view phải có đúng 1 nút gốc**: bọc dialog vào trong div gốc, và **không đặt chú thích HTML `<!-- -->` ngang hàng với phần tử gốc** (ở chế độ dev Vue giữ chú thích → 2 nút gốc → rời trang đó xong thì trang kế tiếp không hiện, phải F5; bản build production xóa chú thích nên không lộ lỗi). Chú thích đặt bên trong phần tử gốc.
- Kiểm tra tương tác thật (bấm menu, đổi theme, back/forward) bằng `npm run dev` chứ không chỉ `npm run build` + ảnh tĩnh — lỗi trên chỉ xuất hiện ở dev.
- Bảng: giữ tổng độ rộng cột ≲ 920px để vừa màn 1280px khi menu mở; gộp cột phụ (vd "Vai trò / Trình độ"), tag trạng thái `size="small" round`.
- `dist/` là kết quả build tự sinh (đã gitignore) — không sửa tay. Cách chia file cấu hình trong `vite.config.js` → `build`: JS vào `assets/js/` (gộp thành `element-plus`, `vendor`, `pages`, `index`), toàn bộ CSS gộp 1 file `assets/css/style-*.css`.
- Khi nộp bài có thể build Vue vào `src/main/resources/static` để chạy 1 file jar.
