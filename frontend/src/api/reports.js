import http from './http'

export const reportApi = {
  // params: { from, to (dd/MM/yyyy) }
  // -> { openingBalance, totalIncome, totalExpense, closingBalance, income[], expense[], months[], outstanding }
  summary: (params) => http.get('/reports/summary', { params }),
  // File Excel cùng số liệu với màn hình (Blob); tạo file có thể lâu hơn request thường
  exportExcel: (params) => http.get('/reports/export', { params, responseType: 'blob', timeout: 60000 })
}

// Lưu Blob thành file trên máy người dùng
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
