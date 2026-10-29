<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { expenseApi } from '@/api/expenses'
import { scheduleApi } from '@/api/schedules'
import { EXPENSE_CATEGORY_LABELS, SCHEDULE_STATUS_LABELS, toOptions } from '@/utils/labels'
import { formatCurrency, sessionExpensePrefill, startOfToday, todayText, weekdayOf } from '@/utils/format'

// expense = null: thêm mới; expense = object: sửa.
// schedule: thêm từ trang chi tiết buổi chơi -> buổi cố định, không đổi được; category: loại chọn sẵn.
// Thêm "Tiền sân + cầu": nhập riêng tiền sân (tự điền số dự kiến khi chọn buổi chưa hủy) và tiền cầu, tổng tự cộng;
// phần chia nhỏ ghi vào ghi chú để lúc sửa vẫn biết. Sửa: chỉ còn 1 ô tổng tiền.
const props = defineProps({
  expense: { type: Object, default: null },
  schedule: { type: Object, default: null },
  category: { type: String, default: null }
})
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const isEdit = computed(() => !!props.expense)
const categoryOptions = toOptions(EXPENSE_CATEGORY_LABELS)

const formRef = ref()
const saving = ref(false)
const emptyForm = () => ({
  description: '',
  category: 'SESSION',
  scheduleId: null,
  amount: null,
  courtFee: null,
  shuttleFee: null,
  expenseDate: todayText(),
  note: ''
})
const form = reactive(emptyForm())
const serverErrors = reactive({})
// Nội dung đang là bản tự điền -> đổi buổi thì điền lại; người dùng đã tự gõ thì giữ nguyên
let autoDescription = ''

const isSession = computed(() => form.category === 'SESSION')
// Thêm mới "Tiền sân + cầu": nhập 2 ô, tổng tự cộng
const splitFees = computed(() => !isEdit.value && isSession.value)
const sessionTotal = computed(() => (Number(form.courtFee) || 0) + (Number(form.shuttleFee) || 0))
const validateSessionTotal = (rule, value, callback) =>
  sessionTotal.value > 0 ? callback() : callback(new Error('Vui lòng nhập tiền sân hoặc tiền cầu'))
const rules = computed(() => ({
  description: [{ required: true, message: 'Vui lòng nhập nội dung khoản chi', trigger: 'blur' }],
  category: [{ required: true, message: 'Vui lòng chọn loại chi', trigger: 'change' }],
  scheduleId: isSession.value ? [{ required: true, message: 'Tiền sân + cầu phải chọn buổi chơi', trigger: 'change' }] : [],
  amount: splitFees.value ? [] : [{ required: true, message: 'Vui lòng nhập số tiền', trigger: 'blur' }],
  courtFee: splitFees.value ? [{ validator: validateSessionTotal, trigger: 'blur' }] : [],
  expenseDate: [{ required: true, message: 'Vui lòng chọn ngày chi', trigger: 'change' }]
}))

// Ô chọn buổi chơi: tìm theo tên buổi / sân, mới nhất trước
const scheduleOptions = ref([])
const searching = ref(false)
let latestRequestId = 0

async function searchSchedules(keyword) {
  const requestId = ++latestRequestId
  searching.value = true
  try {
    const res = await scheduleApi.search({ keyword: keyword || undefined, size: 20, sort: 'playDate', direction: 'desc' })
    if (requestId !== latestRequestId) return
    // Giữ buổi đang chọn trong danh sách (để ô chọn vẫn hiện tên khi kết quả tìm không có nó)
    const current = scheduleOptions.value.find((s) => s.id === form.scheduleId)
    scheduleOptions.value = current && !res.content.some((s) => s.id === current.id) ? [current, ...res.content] : res.content
  } catch (err) {
    if (requestId === latestRequestId) ElMessage.error(err.message)
  } finally {
    if (requestId === latestRequestId) searching.value = false
  }
}

const selectedSchedule = computed(() => scheduleOptions.value.find((s) => s.id === form.scheduleId) ?? null)

// Chọn buổi: điền nội dung, ngày chi; buổi chưa hủy thì điền thêm tiền sân dự kiến
function applySessionPrefill() {
  const s = selectedSchedule.value
  if (!splitFees.value || !s) return
  const prefill = sessionExpensePrefill(s)
  if (!form.description || form.description === autoDescription) {
    form.description = prefill.description
    autoDescription = prefill.description
  }
  if (s.status !== 'CANCELLED' && prefill.courtFee != null) form.courtFee = prefill.courtFee
  form.expenseDate = prefill.expenseDate
}

watch(() => [form.scheduleId, form.category], applySessionPrefill)

watch(visible, (open) => {
  if (!open) return
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  Object.assign(form, emptyForm())
  autoDescription = ''
  scheduleOptions.value = []
  if (props.expense) {
    const e = props.expense
    Object.assign(form, {
      description: e.description,
      category: e.category,
      scheduleId: e.scheduleId,
      amount: Number(e.amount),
      expenseDate: e.expenseDate,
      note: e.note ?? ''
    })
    if (e.scheduleId) {
      scheduleOptions.value = [{ id: e.scheduleId, title: e.scheduleTitle, playDate: e.playDate, status: e.scheduleStatus }]
    }
  } else if (props.schedule) {
    scheduleOptions.value = [props.schedule]
    form.scheduleId = props.schedule.id
    if (props.category) form.category = props.category
  } else if (props.category) {
    form.category = props.category
  }
  // Watch chỉ chạy ở lượt sau -> điền sẵn ngay khi mở từ "Ghi tiền sân + cầu"
  applySessionPrefill()
  if (!props.schedule) searchSchedules('')
})

const disableFuture = (date) => date > startOfToday()

async function handleSubmit() {
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  if (!(await formRef.value.validate().catch(() => false))) return
  const { courtFee, shuttleFee, ...payload } = form
  if (splitFees.value) {
    // Lưu tổng; ghi chi tiết sân / cầu vào đầu ghi chú
    payload.amount = sessionTotal.value
    const detail = `Sân ${formatCurrency(courtFee || 0)} + cầu ${formatCurrency(shuttleFee || 0)}`
    payload.note = (form.note.trim() ? `${detail} | ${form.note.trim()}` : detail).slice(0, 255)
  }
  saving.value = true
  try {
    const saved = isEdit.value
      ? await expenseApi.update(props.expense.id, payload)
      : await expenseApi.create(payload)
    ElMessage.success(isEdit.value ? 'Đã cập nhật khoản chi' : `Đã ghi khoản chi ${formatCurrency(saved.amount)}`)
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
    :title="isEdit ? 'Sửa khoản chi' : 'Thêm khoản chi'"
    width="500px"
    :close-on-click-modal="false"
    destroy-on-close
    class="expense-dialog"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="Loại chi" prop="category" :error="serverErrors.category">
        <el-radio-group v-model="form.category">
          <el-radio-button v-for="c in categoryOptions" :key="c.value" :value="c.value">{{ c.label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item
        :label="isSession ? 'Buổi chơi' : 'Buổi chơi (không bắt buộc)'"
        prop="scheduleId"
        :error="serverErrors.scheduleId"
      >
        <el-select
          v-model="form.scheduleId"
          filterable
          remote
          :remote-method="searchSchedules"
          :loading="searching"
          :disabled="!!schedule"
          clearable
          placeholder="Gõ tên buổi hoặc tên sân để tìm"
          no-data-text="Không tìm thấy buổi chơi"
          class="full-width"
        >
          <el-option v-for="s in scheduleOptions" :key="s.id" :value="s.id" :label="`${s.title} (${s.playDate})`">
            <span>{{ s.title }}</span>
            <span class="text-secondary option-meta">
              {{ weekdayOf(s.playDate) }}, {{ s.playDate }}
              <template v-if="s.status === 'CANCELLED'"> · {{ SCHEDULE_STATUS_LABELS.CANCELLED }}</template>
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <p v-if="isSession && selectedSchedule?.status === 'CANCELLED'" class="hint warn">
        Buổi đã hủy: không tự điền tiền sân. Ghi số tiền thực mất (vd tiền cọc sân).
      </p>
      <p v-else-if="splitFees && selectedSchedule?.estimatedCourtCost != null" class="text-secondary hint">
        Tiền sân dự kiến: {{ formatCurrency(selectedSchedule.estimatedCourtCost) }}
        ({{ formatCurrency(selectedSchedule.hourlyRate) }}/sân/giờ × {{ selectedSchedule.courtCount }} sân). Sửa nếu thực tế khác.
      </p>

      <el-form-item label="Nội dung" prop="description" :error="serverErrors.description">
        <el-input
          v-model="form.description"
          maxlength="255"
          :placeholder="isSession ? 'VD: Tiền sân + cầu buổi tối thứ 7' : 'VD: Mua lưới mới'"
        />
      </el-form-item>

      <template v-if="splitFees">
        <div class="row">
          <el-form-item label="Tiền sân" prop="courtFee" :error="serverErrors.amount" class="col">
            <el-input-number
              v-model="form.courtFee"
              :min="0"
              :step="10000"
              :precision="0"
              controls-position="right"
              placeholder="VD: 120000"
              class="full-width"
            />
          </el-form-item>
          <el-form-item label="Tiền cầu" prop="shuttleFee" class="col">
            <el-input-number
              v-model="form.shuttleFee"
              :min="0"
              :step="10000"
              :precision="0"
              controls-position="right"
              placeholder="VD: 350000"
              class="full-width"
            />
          </el-form-item>
        </div>
        <p class="session-total">Tổng chi buổi: <strong>{{ formatCurrency(sessionTotal) }}</strong></p>
      </template>

      <div class="row">
        <el-form-item
          v-if="!splitFees"
          :label="isSession ? 'Tổng tiền sân + cầu' : 'Số tiền'"
          prop="amount"
          :error="serverErrors.amount"
          class="col"
        >
          <el-input-number
            v-model="form.amount"
            :min="0"
            :step="10000"
            :precision="0"
            controls-position="right"
            placeholder="VD: 250000"
            class="full-width"
          />
        </el-form-item>
        <el-form-item label="Ngày chi" prop="expenseDate" :error="serverErrors.expenseDate" class="col">
          <el-date-picker
            v-model="form.expenseDate"
            type="date"
            format="DD/MM/YYYY"
            value-format="DD/MM/YYYY"
            placeholder="dd/mm/yyyy"
            :disabled-date="disableFuture"
            :clearable="false"
            class="full-width"
          />
        </el-form-item>
      </div>

      <el-form-item label="Ghi chú" prop="note" :error="serverErrors.note">
        <el-input v-model="form.note" type="textarea" :rows="2" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ isEdit ? 'Lưu thay đổi' : 'Ghi khoản chi' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.option-meta {
  margin-left: 8px;
  font-size: 12px;
}

.hint {
  margin: -8px 0 16px;
  font-size: 13px;
}

.hint.warn {
  color: var(--el-color-warning);
}

.session-total {
  margin: -8px 0 16px;
  font-variant-numeric: tabular-nums;
}

.row {
  display: flex;
  gap: 12px;
}

.col {
  flex: 1;
  min-width: 0;
}

:global(.expense-dialog) {
  max-width: calc(100vw - 32px);
}

@media (max-width: 480px) {
  .row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
