import http from './http'

export const paymentApi = {
  // ---- Đợt thu
  // params: { keyword, paymentType, month, year, page (từ 0), size }
  // -> { page, paidCount, paidAmount, unpaidCount, unpaidAmount }
  searchBatches: (params) => http.get('/payments/batches', { params }),
  getBatch: (id) => http.get(`/payments/batches/${id}`),
  // Mọi người trong đợt (theo tên)
  batchPayments: (id) => http.get(`/payments/batches/${id}/payments`),
  // data: { description, amount (chỉ thu thêm, áp dụng cho người chưa nộp), note }
  updateBatch: (id, data) => http.put(`/payments/batches/${id}`, data),
  addBatchMembers: (id, userIds) => http.post(`/payments/batches/${id}/members`, { userIds }),
  // Xóa mọi khoản chưa thu; còn người đã nộp thì giữ -> { deleted, keptPaid, batchDeleted }
  removeBatch: (id) => http.delete(`/payments/batches/${id}`),

  // ---- Khoản thu
  // params: { keyword, paymentType, status, month, year, page (từ 0), size, sort, direction }
  search: (params) => http.get('/payments', { params }),
  // Khoản phí của chính mình (mọi vai trò, chỉ xem)
  mine: () => http.get('/payments/me'),
  // Mọi thành viên đang hoạt động để chọn: [{ id, name, description (email) }]
  memberOptions: () => http.get('/payments/member-options'),
  // Mỗi người 1 khoản -> trả đợt thu chứa các khoản vừa tạo
  // data: { userIds: [], paymentType, month, year (MONTHLY), description, amount (EXTRA), note }
  create: (data) => http.post('/payments', data),
  // Tạo phí tháng cho mọi thành viên đang hoạt động -> { batchId, created, skipped, free, totalAmount }
  generateMonthly: (month, year) => http.post('/payments/monthly', { month, year }),
  // data: { amount (chỉ thu thêm), note } — chỉ khoản chưa thu
  update: (id, data) => http.put(`/payments/${id}`, data),
  remove: (id) => http.delete(`/payments/${id}`),
  // status: PAID (kèm paymentMethod CASH | BANK_TRANSFER) | UNPAID
  setStatus: (id, status, paymentMethod) => http.patch(`/payments/${id}/status`, { status, paymentMethod }),
  // Hàng loạt -> { affected, skipped }
  bulkDelete: (ids) => http.post('/payments/bulk-delete', { ids }),
  bulkCollect: (ids, paymentMethod) => http.post('/payments/bulk-collect', { ids, paymentMethod })
}
