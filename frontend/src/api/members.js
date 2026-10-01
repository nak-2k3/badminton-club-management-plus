import http from './http'

export const memberApi = {
  // params: { keyword, status, roleId, levelId, gender, page (từ 0), size, sort, direction }
  search: (params) => http.get('/members', { params }),
  getById: (id) => http.get(`/members/${id}`),
  create: (data) => http.post('/members', data),
  update: (id, data) => http.put(`/members/${id}`, data),
  changeStatus: (id, status) => http.patch(`/members/${id}/status`, { status }),
  resetPassword: (id, newPassword) => http.patch(`/members/${id}/password`, { newPassword })
}
