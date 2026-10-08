<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { paymentApi } from '@/api/payments'
import { formatCurrency } from '@/utils/format'

// Sửa đợt thu: nội dung, ghi chú; số tiền (chỉ thu thêm) áp dụng cho mọi người chưa nộp, người đã nộp giữ nguyên
const props = defineProps({
  batch: { type: Object, default: null }
})
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const formRef = ref()
const saving = ref(false)
const form = reactive({ description: '', amount: null, note: '' })
const serverErrors = reactive({})

const isMonthly = computed(() => props.batch?.paymentType === 'MONTHLY')
const unpaidCount = computed(() => (props.batch ? props.batch.memberCount - props.batch.paidCount : 0))
const amountChanged = computed(() => !isMonthly.value && form.amount !== null && form.amount !== Number(props.batch?.amount))
const rules = computed(() => ({
  description: [{ required: true, message: 'Vui lòng nhập nội dung khoản thu', trigger: 'blur' }],
  amount: isMonthly.value ? [] : [{ required: true, message: 'Vui lòng nhập số tiền', trigger: 'blur' }]
}))

watch(visible, (open) => {
  if (!open || !props.batch) return
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  Object.assign(form, {
    description: props.batch.description,
    amount: isMonthly.value ? null : Number(props.batch.amount),
    note: props.batch.note ?? ''
  })
})

async function handleSubmit() {
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  if (!(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    const saved = await paymentApi.updateBatch(props.batch.id, {
      description: form.description,
      amount: isMonthly.value ? null : form.amount,
      note: form.note
    })
    ElMessage.success('Đã cập nhật đợt thu')
    visible.value = false
    emit('saved', saved)
  } catch (err) {
    Object.assign(serverErrors, err.errors)
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="Sửa đợt thu"
    width="460px"
    :close-on-click-modal="false"
    destroy-on-close
    class="batch-dialog"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="Nội dung" prop="description" :error="serverErrors.description">
        <el-input v-model="form.description" maxlength="255" />
      </el-form-item>
      <el-form-item v-if="!isMonthly" label="Số tiền mỗi người" prop="amount" :error="serverErrors.amount">
        <el-input-number
          v-model="form.amount"
          :min="0"
          :step="10000"
          :precision="0"
          controls-position="right"
          class="full-width"
        />
      </el-form-item>
      <el-alert
        v-if="amountChanged"
        type="warning"
        :closable="false"
        show-icon
        class="tip"
        :title="`Số tiền mới ${formatCurrency(form.amount)} áp dụng cho ${unpaidCount} người chưa nộp. ${batch.paidCount} người đã nộp giữ nguyên số tiền cũ.`"
      />
      <p v-if="isMonthly" class="text-secondary hint">Số tiền phí tháng lấy theo Mức phí của từng giới tính, không sửa ở đây.</p>
      <el-form-item label="Ghi chú" prop="note" :error="serverErrors.note">
        <el-input v-model="form.note" type="textarea" :rows="2" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">Lưu thay đổi</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.tip {
  margin: -4px 0 16px;
}

.hint {
  margin: -4px 0 16px;
  font-size: 13px;
}

:global(.batch-dialog) {
  max-width: calc(100vw - 32px);
}
</style>
