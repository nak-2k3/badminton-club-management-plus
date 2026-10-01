import { ElMessage, ElMessageBox } from 'element-plus'
import { memberApi } from '@/api/members'
import { USER_STATUS_LABELS } from '@/utils/labels'

const CONFIRM_TEXT = {
  ACTIVE: 'Kích hoạt lại tài khoản của "{name}"? Thành viên sẽ đăng nhập được bình thường.',
  INACTIVE: 'Chuyển "{name}" sang ngừng hoạt động? Thành viên sẽ không đăng nhập được.',
  LOCKED: 'Khóa tài khoản của "{name}"? Thành viên sẽ không đăng nhập được.'
}

// Thao tác dùng chung cho trang danh sách và trang chi tiết
export function useMemberActions() {
  // Trả về thành viên sau khi cập nhật, hoặc null nếu người dùng hủy / lỗi
  async function changeStatus(member, status) {
    try {
      await ElMessageBox.confirm(
        CONFIRM_TEXT[status].replace('{name}', member.fullName),
        `Đổi trạng thái: ${USER_STATUS_LABELS[status]}`,
        {
          confirmButtonText: 'Xác nhận',
          cancelButtonText: 'Hủy',
          type: status === 'ACTIVE' ? 'info' : 'warning'
        }
      )
    } catch {
      return null
    }

    try {
      const updated = await memberApi.changeStatus(member.id, status)
      ElMessage.success(`Đã chuyển "${member.fullName}" sang ${USER_STATUS_LABELS[status].toLowerCase()}`)
      return updated
    } catch (err) {
      ElMessage.error(err.message)
      return null
    }
  }

  return { changeStatus }
}
