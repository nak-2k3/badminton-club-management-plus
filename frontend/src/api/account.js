import http from './http'

// "Tài khoản của tôi" — luôn thao tác trên tài khoản đang đăng nhập
export const accountApi = {
  getProfile: () => http.get('/account'),
  updateProfile: (data) => http.put('/account', data),
  changeEmail: (newEmail, currentPassword) => http.patch('/account/email', { newEmail, currentPassword }),
  changePassword: (currentPassword, newPassword) => http.patch('/account/password', { currentPassword, newPassword })
}
