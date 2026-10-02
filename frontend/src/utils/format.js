// Định dạng hiển thị dùng chung

const currencyFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0
})

// 60000 hoặc "60000.00" -> "60.000 ₫"
export function formatCurrency(value) {
  if (value === null || value === undefined || value === '') return '—'
  return currencyFormatter.format(Number(value))
}

// "dd/MM/yyyy" (định dạng backend trả về) -> Date lúc 00:00 giờ máy; chuỗi sai trả null
export function parseDate(text) {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text ?? '')
  if (!match) return null
  const [, day, month, year] = match.map(Number)
  return new Date(year, month - 1, day)
}

// Ngày hôm nay lúc 00:00 (để so sánh với parseDate)
export function startOfToday() {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return today
}
