import http from './http'

export const guestApi = {
  // Khách của 1 buổi chơi
  list: (scheduleId) => http.get(`/schedules/${scheduleId}/guests`),
  // data: { fullName, phone, gender, note, invitedById (chỉ ADMIN/TREASURER chọn) }
  add: (scheduleId, data) => http.post(`/schedules/${scheduleId}/guests`, data),
  // Sửa thông tin khách (cùng dạng data với add; người dẫn chỉ ADMIN/TREASURER đổi được)
  update: (scheduleId, id, data) => http.put(`/schedules/${scheduleId}/guests/${id}`, data),
  remove: (scheduleId, id) => http.delete(`/schedules/${scheduleId}/guests/${id}`),
  // status: PAID | UNPAID (ADMIN/TREASURER)
  setPayment: (scheduleId, id, status) => http.patch(`/schedules/${scheduleId}/guests/${id}/payment`, { status }),
  // status: PRESENT | ABSENT | NOT_MARKED (ADMIN)
  markAttendance: (scheduleId, id, status) => http.put(`/schedules/${scheduleId}/guests/${id}/attendance`, { status }),
  // Trang "Phí khách" — params: { keyword, from, to, paymentStatus, includeCancelled, page (từ 0), size, sort, direction }
  searchFees: (params) => http.get('/guest-fees', { params })
}
