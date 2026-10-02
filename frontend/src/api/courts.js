import http from './http'

export const courtApi = {
  // params: { keyword, active }
  list: (params) => http.get('/courts', { params }),
  getById: (id) => http.get(`/courts/${id}`),
  create: (data) => http.post('/courts', data),
  update: (id, data) => http.put(`/courts/${id}`, data),
  setActive: (id, active) => http.patch(`/courts/${id}/active`, { active }),
  remove: (id) => http.delete(`/courts/${id}`)
}
