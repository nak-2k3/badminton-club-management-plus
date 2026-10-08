<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { paymentApi } from '@/api/payments'
import { PAYMENT_TYPE_LABELS, toOptions } from '@/utils/labels'
import { formatCurrency } from '@/utils/format'
import MemberPicker from './MemberPicker.vue'

// payment = null: tạo khoản thu cho 1 hoặc nhiều thành viên -> emit 'saved' kèm đợt thu vừa tạo / vừa thêm vào.
// payment = object: sửa 1 khoản chưa thu — số tiền (chỉ thu thêm, vd giảm cho 1 người) và ghi chú; nội dung sửa ở đợt.
const props = defineProps({
  payment: { type: Object, default: null }
})
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const isEdit = computed(() => !!props.payment)
const typeOptions = toOptions(PAYMENT_TYPE_LABELS)

const formRef = ref()
const saving = ref(false)
const thisMonth = () => {
  const now = new Date()
  return `${now.getMonth() + 1}/${now.getFullYear()}`
}
const emptyForm = () => ({ userIds: [], paymentType: 'EXTRA', period: thisMonth(), description: '', amount: null, note: '' })
const form = reactive(emptyForm())
const serverErrors = reactive({})

const isMonthly = computed(() => (isEdit.value ? props.payment.paymentType : form.paymentType) === 'MONTHLY')
const rules = computed(() => ({
  userIds: isEdit.value
    ? []
    : [{ type: 'array', required: true, min: 1, message: 'Vui lòng chọn ít nhất 1 thành viên', trigger: 'change' }],
  period: isMonthly.value ? [{ required: true, message: 'Vui lòng chọn tháng', trigger: 'change' }] : [],
  description: isMonthly.value || isEdit.value
    ? []
    : [{ required: true, message: 'Vui lòng nhập nội dung khoản thu', trigger: 'blur' }],
  amount: isMonthly.value ? [] : [{ required: true, message: 'Vui lòng nhập số tiền', trigger: 'blur' }]
}))

// Danh sách thành viên đang hoạt động để tick chọn
const memberOptions = ref([])
const loadingMembers = ref(false)

async function loadMembers() {
  loadingMembers.value = true
  try {
    memberOptions.value = await paymentApi.memberOptions()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loadingMembers.value = false
  }
}

// Khoản thu thêm: cùng số tiền cho mọi người được chọn
const extraTotal = computed(() => (form.amount ? form.amount * form.userIds.length : 0))

watch(visible, (open) => {
  if (!open) return
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  Object.assign(form, emptyForm())
  if (props.payment) {
    Object.assign(form, { amount: Number(props.payment.amount), note: props.payment.note ?? '' })
  } else {
    loadMembers()
  }
})

async function handleSubmit() {
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  if (!(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    if (isEdit.value) {
      const saved = await paymentApi.update(props.payment.id, {
        amount: isMonthly.value ? null : form.amount,
        note: form.note
      })
      ElMessage.success('Đã cập nhật khoản thu')
      visible.value = false
      emit('saved', saved)
      return
    }
    const [month, year] = isMonthly.value ? form.period.split('/').map(Number) : [null, null]
    const count = form.userIds.length
    const batch = await paymentApi.create({
      userIds: form.userIds,
      paymentType: form.paymentType,
      month,
      year,
      description: isMonthly.value ? null : form.description,
      amount: isMonthly.value ? null : form.amount,
      note: form.note
    })
    ElMessage.success(`Đã tạo ${count} khoản trong đợt "${batch.description}"`)
    visible.value = false
    emit('saved', batch)
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
    :title="isEdit ? 'Sửa khoản thu' : 'Thêm khoản thu'"
    :width="isEdit ? '460px' : '540px'"
    :close-on-click-modal="false"
    destroy-on-close
    class="payment-dialog"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <template v-if="isEdit">
        <el-descriptions :column="1" border class="summary">
          <el-descriptions-item label="Thành viên">
            {{ payment.userName }} <span class="text-secondary">· {{ payment.userEmail }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="Khoản thu">{{ payment.description }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template v-else>
        <el-form-item label="Loại khoản thu" prop="paymentType" :error="serverErrors.paymentType">
          <el-radio-group v-model="form.paymentType">
            <el-radio-button v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="isMonthly"
          label="Tháng thu"
          prop="period"
          :error="serverErrors.month || serverErrors.year"
        >
          <el-date-picker
            v-model="form.period"
            type="month"
            format="MM/YYYY"
            value-format="M/YYYY"
            placeholder="Chọn tháng"
            :editable="false"
            :clearable="false"
            class="full-width"
          />
        </el-form-item>
        <el-form-item v-else label="Nội dung" prop="description" :error="serverErrors.description">
          <el-input v-model="form.description" maxlength="255" placeholder="VD: Tiền áo đồng phục" />
        </el-form-item>
        <el-form-item label="Thành viên" prop="userIds" :error="serverErrors.userIds">
          <MemberPicker v-model="form.userIds" :options="memberOptions" :loading="loadingMembers" />
        </el-form-item>
      </template>

      <el-form-item
        v-if="!isMonthly"
        :label="isEdit ? 'Số tiền' : 'Số tiền mỗi người'"
        prop="amount"
        :error="serverErrors.amount"
      >
        <el-input-number
          v-model="form.amount"
          :min="0"
          :step="10000"
          :precision="0"
          controls-position="right"
          placeholder="VD: 150000"
          class="full-width"
        />
      </el-form-item>
      <p v-if="!isEdit && !isMonthly && form.userIds.length && form.amount" class="text-secondary hint">
        {{ form.userIds.length }} người × {{ formatCurrency(form.amount) }} = <strong>{{ formatCurrency(extraTotal) }}</strong>
      </p>
      <p v-else-if="isMonthly && isEdit" class="text-secondary hint">
        Số tiền {{ formatCurrency(payment.amount) }} lấy theo Mức phí, không sửa tay. Muốn đổi thì xóa khoản này và tạo lại.
      </p>
      <p v-else-if="isMonthly" class="text-secondary hint">
        Số tiền tự lấy theo Mức phí tháng (theo giới tính) áp dụng tại ngày 1 của tháng thu, thêm vào đợt phí tháng đó.
        Muốn tạo cho tất cả thành viên, dùng nút "Tạo phí tháng".
      </p>
      <p v-else-if="isEdit" class="text-secondary hint">
        Chỉ đổi số tiền của riêng người này. Muốn đổi cho cả đợt, dùng "Sửa đợt" ở trang chi tiết đợt.
      </p>

      <el-form-item label="Ghi chú" prop="note" :error="serverErrors.note">
        <el-input v-model="form.note" type="textarea" :rows="2" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ isEdit ? 'Lưu thay đổi' : 'Tạo khoản thu' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.summary {
  margin-bottom: 16px;
}

.hint {
  margin: -4px 0 16px;
  font-size: 13px;
}

:global(.payment-dialog) {
  max-width: calc(100vw - 32px);
}
</style>
