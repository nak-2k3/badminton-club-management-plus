import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

// Mọi request gọi qua '/api' (Vite proxy sang backend)
const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// Tự gắn token vào header
http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

// Chuẩn hóa lỗi theo ErrorResponse của backend: { status, message, errors }
http.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const status = error.response?.status
    let data = error.response?.data ?? {}
    // Tải file (responseType: 'blob'): lỗi cũng về dạng Blob -> đọc lại thành JSON ErrorResponse
    if (data instanceof Blob) {
      try {
        data = JSON.parse(await data.text())
      } catch {
        data = {}
      }
    }
    const apiError = {
      status: status ?? 0,
      message: data.message || (status ? 'Đã có lỗi xảy ra' : 'Không kết nối được máy chủ'),
      errors: data.errors || {}
    }

    // Token hết hạn / không hợp lệ -> đăng xuất, về trang đăng nhập
    const isLoginRequest = error.config?.url?.includes('/auth/login')
    if (status === 401 && !isLoginRequest) {
      useAuthStore().logout()
      // Backend báo rõ lý do: hết hạn phiên, hoặc tài khoản vừa bị khóa / ngừng hoạt động
      ElMessage.warning(data.message || 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại')
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    }

    return Promise.reject(apiError)
  }
)

export default http
