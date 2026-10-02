import { ElMessage, ElMessageBox } from 'element-plus'
import { scheduleApi } from '@/api/schedules'
import { parseDate, parseDateTime, startOfToday } from '@/utils/format'

const CONFIRM = {
  OPEN: {
    title: 'Mở lại đăng ký',
    text: 'Mở lại đăng ký cho buổi "{title}"? Thành viên sẽ đăng ký được nếu còn chỗ.',
    done: 'Đã mở lại đăng ký',
    type: 'info'
  },
  CLOSED: {
    title: 'Đóng đăng ký',
    text: 'Đóng đăng ký buổi "{title}"? Người đã đăng ký vẫn giữ nguyên, không nhận thêm người mới.',
    done: 'Đã đóng đăng ký',
    type: 'warning'
  },
  COMPLETED: {
    title: 'Hoàn thành buổi chơi',
    text: 'Đánh dấu buổi "{title}" đã hoàn thành? Sau đó không sửa được thông tin buổi nữa.',
    done: 'Đã đánh dấu hoàn thành',
    type: 'info'
  }
}

// Buổi đã hủy / hoàn thành thì không thao tác được nữa (backend cũng chặn)
export const isFinished = (schedule) => ['CANCELLED', 'COMPLETED'].includes(schedule?.status)

// Chỉ đánh dấu hoàn thành khi đã tới ngày chơi
export const canComplete = (schedule) =>
  !isFinished(schedule) && parseDate(schedule.playDate) <= startOfToday()

// Tự đăng ký được: buổi đang mở, chưa tới giờ, còn chỗ, chưa đăng ký (backend kiểm tra lại)
export const canRegister = (schedule) =>
  schedule.status === 'OPEN' &&
  !schedule.started &&
  !schedule.registeredByMe &&
  schedule.registeredCount < schedule.maxPlayers

// Còn trong hạn tự hủy (trước giờ bắt đầu SELF_CANCEL_DEADLINE_HOURS tiếng)
export const canSelfCancel = (schedule) =>
  schedule.registeredByMe && !isFinished(schedule) && new Date() < parseDateTime(schedule.selfCancelDeadline)

// Lý do không tự đăng ký được (hiện cạnh nút bị khóa); '' nếu đăng ký được hoặc đã đăng ký
export function registerBlockedReason(schedule) {
  if (schedule.registeredByMe) return ''
  if (schedule.status === 'CANCELLED') return 'Buổi đã hủy'
  if (schedule.status === 'COMPLETED') return 'Buổi đã kết thúc'
  if (schedule.started) return 'Đã qua giờ bắt đầu'
  if (schedule.status === 'CLOSED') return 'Đã đóng đăng ký'
  if (schedule.registeredCount >= schedule.maxPlayers) return 'Hết chỗ'
  return ''
}

// Điểm danh từ ngày chơi trở đi, trừ buổi đã hủy (cùng quy tắc với backend)
export const canMarkAttendance = (schedule) =>
  schedule?.status !== 'CANCELLED' && parseDate(schedule?.playDate) <= startOfToday()

// Thao tác dùng chung cho trang danh sách và trang chi tiết buổi chơi
export function useScheduleActions() {
  // Trả về buổi chơi sau khi cập nhật, hoặc null nếu người dùng hủy / lỗi
  async function changeStatus(schedule, status) {
    let reason = null
    try {
      if (status === 'CANCELLED') {
        const { value } = await ElMessageBox.prompt(
          `Hủy buổi "${schedule.title}" (${schedule.playDate})? Buổi đã hủy không thể mở lại.` +
            (schedule.registeredCount > 0 ? ` Hiện có ${schedule.registeredCount} người đã đăng ký.` : ''),
          'Hủy buổi chơi',
          {
            confirmButtonText: 'Hủy buổi',
            cancelButtonText: 'Không',
            confirmButtonClass: 'el-button--danger',
            type: 'warning',
            inputPlaceholder: 'Lý do hủy (không bắt buộc), vd: sân sửa chữa',
            inputValidator: (v) => !v || v.trim().length <= 200 || 'Lý do tối đa 200 ký tự'
          }
        )
        reason = value?.trim() || null
      } else {
        const c = CONFIRM[status]
        await ElMessageBox.confirm(c.text.replace('{title}', schedule.title), c.title, {
          confirmButtonText: 'Xác nhận',
          cancelButtonText: 'Hủy',
          type: c.type
        })
      }
    } catch {
      return null
    }

    try {
      const updated = await scheduleApi.changeStatus(schedule.id, status, reason)
      ElMessage.success(status === 'CANCELLED' ? 'Đã hủy buổi chơi' : CONFIRM[status].done)
      return updated
    } catch (err) {
      ElMessage.error(err.message)
      return null
    }
  }

  // Trả về true nếu đã xóa
  async function remove(schedule) {
    try {
      await ElMessageBox.confirm(
        `Xóa buổi "${schedule.title}" (${schedule.playDate})? Chỉ xóa được buổi chưa có ai tham gia ` +
          '(không còn người đang đăng ký, khách, điểm danh, khoản chi); ngược lại hãy hủy buổi.',
        'Xóa buổi chơi',
        { confirmButtonText: 'Xóa', cancelButtonText: 'Hủy', type: 'error', confirmButtonClass: 'el-button--danger' }
      )
    } catch {
      return false
    }
    try {
      await scheduleApi.remove(schedule.id)
      ElMessage.success(`Đã xóa buổi "${schedule.title}"`)
      return true
    } catch (err) {
      ElMessage.error(err.message)
      return false
    }
  }

  // Tự đăng ký / tự hủy: trả về buổi chơi sau khi cập nhật, hoặc null nếu người dùng hủy / lỗi
  async function register(schedule) {
    // Trong khoảng hạn chót -> giờ bắt đầu vẫn đăng ký được nhưng không tự hủy được nữa: báo trước
    const deadlinePassed = new Date() >= parseDateTime(schedule.selfCancelDeadline)
    try {
      await ElMessageBox.confirm(
        `Đăng ký tham gia "${schedule.title}" (${schedule.playDate}, ${schedule.startTime}–${schedule.endTime})?` +
          (deadlinePassed
            ? ' Lưu ý: đã quá hạn tự hủy, sau khi đăng ký bạn chỉ hủy được bằng cách nhờ quản trị viên.'
            : ` Bạn tự hủy được đến ${schedule.selfCancelDeadline}; sau đó cần nhờ quản trị viên.`),
        'Đăng ký tham gia',
        { confirmButtonText: 'Đăng ký', cancelButtonText: 'Hủy', type: 'info' }
      )
    } catch {
      return null
    }
    try {
      const updated = await scheduleApi.register(schedule.id)
      ElMessage.success(`Đã đăng ký buổi "${schedule.title}"`)
      return updated
    } catch (err) {
      ElMessage.error(err.message)
      return null
    }
  }

  async function cancelRegistration(schedule) {
    try {
      await ElMessageBox.confirm(
        `Hủy đăng ký buổi "${schedule.title}" (${schedule.playDate})? Chỗ của bạn sẽ nhường cho người khác.`,
        'Hủy đăng ký',
        {
          confirmButtonText: 'Hủy đăng ký',
          cancelButtonText: 'Không',
          type: 'warning',
          confirmButtonClass: 'el-button--danger'
        }
      )
    } catch {
      return null
    }
    try {
      const updated = await scheduleApi.cancelRegistration(schedule.id)
      ElMessage.success('Đã hủy đăng ký')
      return updated
    } catch (err) {
      ElMessage.error(err.message)
      return null
    }
  }

  return { changeStatus, remove, register, cancelRegistration }
}
