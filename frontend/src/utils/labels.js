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

// Chuyển object nhãn thành danh sách cho el-select
export const toOptions = (labels) =>
  Object.entries(labels).map(([value, label]) => ({ value, label }))
