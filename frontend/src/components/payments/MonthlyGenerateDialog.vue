<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { paymentApi } from '@/api/payments'
import { formatCurrency } from '@/utils/format'

// Tạo phí tháng cho mọi thành viên đang hoạt động (người đã có phí tháng đó được bỏ qua) -> emit 'saved' kèm batchId để mở đợt
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const period = ref('')
const error = ref('')
const saving = ref(false)

watch(visible, (open) => {
  if (!open) return
  const now = new Date()
  period.value = `${now.getMonth() + 1}/${now.getFullYear()}`
  error.value = ''
})

async function handleSubmit() {
  error.value = ''
  if (!period.value) {
    error.value = 'Vui lòng chọn tháng'
    return
  }
  const [month, year] = period.value.split('/').map(Number)
  saving.value = true
  try {
    const res = await paymentApi.generateMonthly(month, year)
    const extra = [
      res.skipped && `bỏ qua ${res.skipped} người đã có`,
      res.free && `${res.free} người miễn phí`
    ].filter(Boolean)
    const detail = extra.length ? ` (${extra.join(', ')})` : ''
    if (res.created) {
      ElMessage.success(`Đã tạo ${res.created} khoản phí tháng ${month}/${year}, tổng ${formatCurrency(res.totalAmount)}${detail}`)
    } else {
      ElMessage.info(`Không có khoản mới nào cần tạo cho tháng ${month}/${year}${detail}`)
    }
    visible.value = false
    emit('saved', res)
  } catch (err) {
    error.value = err.errors?.month || err.errors?.year || ''
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="Tạo phí tháng"
    width="440px"
    :close-on-click-modal="false"
    class="monthly-dialog"
  >
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="tip"
      title="Tạo phí tháng cho tất cả thành viên đang hoạt động, số tiền theo Mức phí của từng giới tính. Người đã có phí tháng này sẽ được bỏ qua."
    />
    <el-form label-position="top" @submit.prevent="handleSubmit">
      <el-form-item label="Tháng thu" required :error="error">
        <el-date-picker
          v-model="period"
          type="month"
          format="MM/YYYY"
          value-format="M/YYYY"
          placeholder="Chọn tháng"
          :editable="false"
          :clearable="false"
          class="full-width"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">Tạo phí tháng</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.tip {
  margin-bottom: 16px;
}

:global(.monthly-dialog) {
  max-width: calc(100vw - 32px);
}
</style>
