import http from './http'

export const feeApi = {
  // params: { feeType }
  list: (params) => http.get('/fee-settings', { params }),
  current: () => http.get('/fee-settings/current'),
  create: (data) => http.post('/fee-settings', data),
  update: (id, data) => http.put(`/fee-settings/${id}`, data),
  setActive: (id, active) => http.patch(`/fee-settings/${id}/active`, { active })
}
