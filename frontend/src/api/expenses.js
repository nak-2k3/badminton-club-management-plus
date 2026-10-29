import http from './http'

export const expenseApi = {
  // params: { keyword, category, from, to (dd/MM/yyyy), scheduleId, page (từ 0), size, sort, direction }
  // -> { page, totalCount, totalAmount, byCategory: [{ category, count, amount }] }
  search: (params) => http.get('/expenses', { params }),
  // data: { description, category, scheduleId, amount, expenseDate (dd/MM/yyyy), note }
  create: (data) => http.post('/expenses', data),
  update: (id, data) => http.put(`/expenses/${id}`, data),
  remove: (id) => http.delete(`/expenses/${id}`)
}
