import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { paymentApi } from '@/api/payments'
import { PAYMENT_METHOD_LABELS } from '@/utils/labels'
import { formatCurrency } from '@/utils/format'

// Thu tiền / hoàn tác / xóa từng khoản và hàng loạt — dùng chung cho tab "Tất cả khoản" và trang chi tiết đợt thu.
// onDone: tải lại dữ liệu sau khi thao tác xong (kể cả khi lỗi, vì có thể người khác vừa đổi dữ liệu)
export function usePaymentActions(onDone) {
  const busyId = ref(null)
  const bulkBusy = ref(false)

  async function confirm(message, title, confirmButtonText, danger = false) {
    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText,
        cancelButtonText: 'Không',
        type: 'warning',
        ...(danger && { confirmButtonClass: 'el-button--danger' })
      })
      return true
    } catch {
      return false
    }
  }

  // Gọi trước: đặt busyId (1 khoản) hoặc bulkBusy (hàng loạt) để hiện trạng thái đang xử lý
  async function run(action) {
    try {
      await action()
    } catch (err) {
      ElMessage.error(err.message)
    } finally {
      busyId.value = null
      bulkBusy.value = false
      onDone()
    }
  }

  function collect(row, method) {
    busyId.value = row.id
    return run(async () => {
      await paymentApi.setStatus(row.id, 'PAID', method)
      ElMessage.success(`Đã thu ${formatCurrency(row.amount)} của "${row.userName}" (${PAYMENT_METHOD_LABELS[method]})`)
    })
  }

  async function undo(row) {
    const ok = await confirm(
      `Chuyển khoản "${row.description}" của ${row.userName} về "Chưa thu"? Hình thức và thời điểm thu sẽ bị xóa.`,
      'Hoàn tác thu tiền',
      'Hoàn tác'
    )
    if (!ok) return
    busyId.value = row.id
    return run(async () => {
      await paymentApi.setStatus(row.id, 'UNPAID')
      ElMessage.success('Đã chuyển về chưa thu')
    })
  }

  async function remove(row) {
    const ok = await confirm(
      `Xóa khoản "${row.description}" (${formatCurrency(row.amount)}) của ${row.userName}? Không thể hoàn tác.`,
      'Xóa khoản thu',
      'Xóa',
      true
    )
    if (!ok) return
    busyId.value = row.id
    return run(async () => {
      await paymentApi.remove(row.id)
      ElMessage.success('Đã xóa khoản thu')
    })
  }

  // rows: các khoản đang chọn (để tính số lượng / số tiền hiện trong hộp xác nhận)
  async function bulkCollect(rows, method) {
    const unpaid = rows.filter((r) => r.status === 'UNPAID')
    if (!unpaid.length) {
      ElMessage.info('Các khoản đã chọn đều đã thu')
      return
    }
    const total = unpaid.reduce((sum, r) => sum + Number(r.amount), 0)
    const skipped = rows.length - unpaid.length
    const ok = await confirm(
      `Ghi nhận đã thu ${unpaid.length} khoản, tổng ${formatCurrency(total)}, hình thức ${PAYMENT_METHOD_LABELS[method]}?`
        + (skipped ? ` (${skipped} khoản đã thu trước đó được bỏ qua)` : ''),
      'Thu tiền nhiều khoản',
      'Thu tiền'
    )
    if (!ok) return
    bulkBusy.value = true
    return run(async () => {
      const res = await paymentApi.bulkCollect(rows.map((r) => r.id), method)
      ElMessage.success(`Đã thu ${res.affected} khoản, tổng ${formatCurrency(total)}`)
    })
  }

  async function bulkRemove(rows) {
    const unpaid = rows.filter((r) => r.status === 'UNPAID')
    if (!unpaid.length) {
      ElMessage.info('Các khoản đã chọn đều đã thu — hoàn tác trước nếu muốn xóa')
      return
    }
    const kept = rows.length - unpaid.length
    const ok = await confirm(
      `Xóa ${unpaid.length} khoản chưa thu đã chọn? Không thể hoàn tác.`
        + (kept ? ` ${kept} khoản đã thu sẽ được giữ lại.` : ''),
      'Xóa nhiều khoản',
      'Xóa',
      true
    )
    if (!ok) return
    bulkBusy.value = true
    return run(async () => {
      const res = await paymentApi.bulkDelete(rows.map((r) => r.id))
      ElMessage.success(`Đã xóa ${res.affected} khoản` + (res.skipped ? `, giữ lại ${res.skipped} khoản đã thu` : ''))
    })
  }

  return { busyId, bulkBusy, collect, undo, remove, bulkCollect, bulkRemove }
}
