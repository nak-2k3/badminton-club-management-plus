import http from './http'

export const scheduleApi = {
  // params: { keyword, from, to (dd/MM/yyyy), status, scheduleType, courtId, mine, page (từ 0), size, sort, direction }
  search: (params) => http.get('/schedules', { params }),
  getById: (id) => http.get(`/schedules/${id}`),
  create: (data) => http.post('/schedules', data),
  update: (id, data) => http.put(`/schedules/${id}`, data),
  // reason: lý do hủy (chỉ dùng khi status = CANCELLED)
  changeStatus: (id, status, reason) => http.patch(`/schedules/${id}/status`, { status, reason }),
  remove: (id) => http.delete(`/schedules/${id}`),

  // Người tham gia & điểm danh
  participants: (id) => http.get(`/schedules/${id}/participants`),
  // Tự đăng ký / tự hủy (mọi vai trò) -> trả về buổi chơi sau khi cập nhật
  register: (id) => http.post(`/schedules/${id}/registrations/me`),
  cancelRegistration: (id) => http.delete(`/schedules/${id}/registrations/me`),
  // ADMIN thêm / bớt người khác
  addParticipant: (id, userId) => http.post(`/schedules/${id}/registrations`, { userId }),
  removeParticipant: (id, userId) => http.delete(`/schedules/${id}/registrations/${userId}`),
  // ADMIN điểm danh; status: PRESENT | ABSENT | NOT_MARKED (bỏ điểm danh)
  markAttendance: (id, userId, status, note) => http.put(`/schedules/${id}/attendances/${userId}`, { status, note }),
  markAllAttendance: (id, status) => http.post(`/schedules/${id}/attendances/mark-all`, { status })
}
