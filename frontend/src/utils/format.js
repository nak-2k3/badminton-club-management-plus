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

// "dd/MM/yyyy HH:mm" -> Date giờ máy; chuỗi sai trả null
export function parseDateTime(text) {
  const match = /^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2})$/.exec(text ?? '')
  if (!match) return null
  const [, day, month, year, hour, minute] = match.map(Number)
  return new Date(year, month - 1, day, hour, minute)
}

// Ngày hôm nay lúc 00:00 (để so sánh với parseDate)
export function startOfToday() {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return today
}

// Ngày hôm nay dạng "dd/MM/yyyy" (giá trị mặc định cho bộ lọc / date picker)
export function todayText() {
  const today = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(today.getDate())}/${pad(today.getMonth() + 1)}/${today.getFullYear()}`
}

const WEEKDAYS = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7']

// "03/10/2026" -> "Thứ 7"; chuỗi sai trả ''
export function weekdayOf(text) {
  const date = parseDate(text)
  return date ? WEEKDAYS[date.getDay()] : ''
}

// Số phút từ "HH:mm" đến "HH:mm" (dùng tính thời lượng buổi chơi); thiếu giá trị trả 0
export function minutesBetween(start, end) {
  if (!start || !end) return 0
  const toMinutes = (t) => {
    const [h, m] = t.split(':').map(Number)
    return h * 60 + m
  }
  return toMinutes(end) - toMinutes(start)
}

// 90 -> "1 giờ 30 phút"
export function formatDuration(minutes) {
  if (minutes <= 0) return '—'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return [h && `${h} giờ`, m && `${m} phút`].filter(Boolean).join(' ')
}
