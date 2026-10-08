// Nhãn tiếng Việt cho các giá trị enum từ backend
export const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  TREASURER: 'Thủ quỹ',
  MEMBER: 'Thành viên'
}

export const GENDER_LABELS = {
  MALE: 'Nam',
  FEMALE: 'Nữ'
}

export const LEVEL_LABELS = {
  BEGINNER: 'Mới bắt đầu',
  INTERMEDIATE: 'Trung bình',
  ADVANCED: 'Nâng cao'
}

export const USER_STATUS_LABELS = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
  LOCKED: 'Bị khóa'
}

// Màu el-tag theo trạng thái
export const USER_STATUS_TAG_TYPES = {
  ACTIVE: 'success',
  INACTIVE: 'info',
  LOCKED: 'danger'
}

export const FEE_TYPE_LABELS = {
  MONTHLY: 'Phí tháng',
  GUEST: 'Phí khách'
}

export const SCHEDULE_TYPE_LABELS = {
  FIXED: 'Cố định',
  EXTRA: 'Phát sinh'
}

export const SCHEDULE_STATUS_LABELS = {
  OPEN: 'Đang mở đăng ký',
  CLOSED: 'Đã đóng đăng ký',
  COMPLETED: 'Đã hoàn thành',
  CANCELLED: 'Đã hủy'
}

export const SCHEDULE_STATUS_TAG_TYPES = {
  OPEN: 'success',
  CLOSED: 'warning',
  COMPLETED: 'info',
  CANCELLED: 'danger'
}

export const ATTENDANCE_STATUS_LABELS = {
  NOT_MARKED: 'Chưa điểm danh',
  PRESENT: 'Có mặt',
  ABSENT: 'Vắng'
}

export const ATTENDANCE_STATUS_TAG_TYPES = {
  NOT_MARKED: 'info',
  PRESENT: 'success',
  ABSENT: 'danger'
}

export const PAYMENT_STATUS_LABELS = {
  UNPAID: 'Chưa thu',
  PAID: 'Đã thu'
}

export const PAYMENT_STATUS_TAG_TYPES = {
  UNPAID: 'warning',
  PAID: 'success'
}

// Chuyển object nhãn thành danh sách cho el-select
export const toOptions = (labels) =>
  Object.entries(labels).map(([value, label]) => ({ value, label }))

export const PAYMENT_TYPE_LABELS = {
  MONTHLY: 'Phí tháng',
  EXTRA: 'Thu thêm'
}

export const PAYMENT_TYPE_TAG_TYPES = {
  MONTHLY: 'primary',
  EXTRA: 'info'
}

export const PAYMENT_METHOD_LABELS = {
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản'
}
