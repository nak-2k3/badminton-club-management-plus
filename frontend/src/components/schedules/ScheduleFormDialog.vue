<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { scheduleApi } from '@/api/schedules'
import { courtApi } from '@/api/courts'
import { SCHEDULE_TYPE_LABELS, toOptions } from '@/utils/labels'
import { formatCurrency, formatDuration, minutesBetween, startOfToday } from '@/utils/format'

// schedule = null: thêm mới; schedule = object: sửa
const props = defineProps({
  schedule: { type: Object, default: null }
})
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const isEdit = computed(() => !!props.schedule)
// Đã có người đăng ký: không đổi địa điểm, ngày, giờ (backend cũng chặn)
const locked = computed(() => isEdit.value && props.schedule.registeredCount > 0)

const formRef = ref()
const saving = ref(false)
const typeOptions = toOptions(SCHEDULE_TYPE_LABELS)
const courts = ref([])
const courtsLoading = ref(false)

const emptyForm = () => ({
  title: '',
  scheduleType: 'FIXED',
  courtId: null,
  courtCount: 1,
  playDate: '',
  startTime: '',
  endTime: '',
  maxPlayers: null,
  note: ''
})
const form = reactive(emptyForm())
const serverErrors = reactive({})

const rules = {
  title: [{ required: true, message: 'Vui lòng nhập tên buổi chơi', trigger: 'blur' }],
  scheduleType: [{ required: true, message: 'Vui lòng chọn loại buổi chơi', trigger: 'change' }],
  courtId: [{ required: true, message: 'Vui lòng chọn sân', trigger: 'change' }],
  courtCount: [{ required: true, message: 'Vui lòng nhập số sân thuê', trigger: 'blur' }],
  playDate: [{ required: true, message: 'Vui lòng chọn ngày chơi', trigger: 'change' }],
  startTime: [{ required: true, message: 'Vui lòng chọn giờ bắt đầu', trigger: 'change' }],
  endTime: [
    { required: true, message: 'Vui lòng chọn giờ kết thúc', trigger: 'change' },
    {
      validator: (_rule, value, callback) =>
        value && form.startTime && minutesBetween(form.startTime, value) <= 0
          ? callback(new Error('Giờ kết thúc phải sau giờ bắt đầu'))
          : callback(),
      trigger: 'change'
    }
  ],
  maxPlayers: [{ required: true, message: 'Vui lòng nhập số người tối đa', trigger: 'blur' }]
}

// Sân đang hoạt động; khi sửa buổi ở sân đã tạm ngưng thì vẫn hiện sân đó để giữ lựa chọn
const courtOptions = computed(() => {
  const s = props.schedule
  if (!isEdit.value || courts.value.some((c) => c.id === s.courtId)) return courts.value
  return [{ id: s.courtId, courtName: s.courtName, hourlyRate: s.hourlyRate, active: s.courtActive }, ...courts.value]
})
const selectedCourt = computed(() => courtOptions.value.find((c) => c.id === form.courtId))

const durationMinutes = computed(() => minutesBetween(form.startTime, form.endTime))
// Tiền sân dự kiến = giá 1 sân/giờ × số giờ × số sân (cùng công thức với backend)
const estimatedCost = computed(() => {
  if (!selectedCourt.value || durationMinutes.value <= 0 || !form.courtCount) return null
  return Math.round((Number(selectedCourt.value.hourlyRate) * durationMinutes.value * form.courtCount) / 60)
})

// Không chọn ngày đã qua
const disablePast = (date) => date.getTime() < startOfToday().getTime()

// Chọn giờ bắt đầu: tự gợi ý giờ kết thúc = bắt đầu + 2 giờ nếu chưa chọn hoặc không còn hợp lệ
function handleStartChange(start) {
  if (!start || (form.endTime && minutesBetween(start, form.endTime) > 0)) return
  const [h, m] = start.split(':').map(Number)
  const end = Math.min(h * 60 + m + 120, 23 * 60 + 45)
  if (end > h * 60 + m) {
    form.endTime = `${String(Math.floor(end / 60)).padStart(2, '0')}:${String(end % 60).padStart(2, '0')}`
  }
}

async function loadCourts() {
  courtsLoading.value = true
  try {
    courts.value = await courtApi.list({ active: true })
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    courtsLoading.value = false
  }
}

watch(visible, (open) => {
  if (!open) return
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  Object.assign(form, emptyForm())
  if (props.schedule) {
    const s = props.schedule
    Object.assign(form, {
      title: s.title,
      scheduleType: s.scheduleType,
      courtId: s.courtId,
      courtCount: s.courtCount,
      playDate: s.playDate,
      startTime: s.startTime,
      endTime: s.endTime,
      maxPlayers: s.maxPlayers,
      note: s.note ?? ''
    })
  }
  loadCourts()
})

async function handleSubmit() {
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  if (!(await formRef.value.validate().catch(() => false))) return
  const payload = { ...form, note: form.note || null }
  saving.value = true
  try {
    const saved = isEdit.value
      ? await scheduleApi.update(props.schedule.id, payload)
      : await scheduleApi.create(payload)
    ElMessage.success(isEdit.value ? 'Đã cập nhật buổi chơi' : `Đã tạo buổi "${saved.title}"`)
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
    :title="isEdit ? 'Sửa buổi chơi' : 'Thêm buổi chơi'"
    width="620px"
    :close-on-click-modal="false"
    destroy-on-close
    class="schedule-dialog"
  >
    <el-alert
      v-if="locked"
      type="info"
      :closable="false"
      show-icon
      class="locked-alert"
      :title="`Buổi đã có ${schedule.registeredCount} người đăng ký nên không đổi được địa điểm, ngày và giờ. Muốn đổi, hãy hủy buổi này và tạo buổi mới.`"
    />
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-row :gutter="16">
        <el-col :xs="24" :sm="16">
          <el-form-item label="Tên buổi chơi" prop="title" :error="serverErrors.title">
            <el-input v-model="form.title" maxlength="150" placeholder="VD: Buổi tối thứ 3" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-form-item label="Loại buổi" prop="scheduleType" :error="serverErrors.scheduleType">
            <el-radio-group v-model="form.scheduleType" class="type-group">
              <el-radio-button v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="16">
          <el-form-item label="Địa điểm" prop="courtId" :error="serverErrors.courtId">
            <el-select
              v-model="form.courtId"
              :loading="courtsLoading"
              :disabled="locked"
              filterable
              placeholder="Chọn sân"
              no-data-text="Chưa có sân nào đang hoạt động"
              class="full-width"
            >
              <el-option v-for="c in courtOptions" :key="c.id" :value="c.id" :label="c.courtName">
                <span>{{ c.courtName }}</span>
                <span class="option-extra">
                  {{ c.active === false ? 'Tạm ngưng' : `${formatCurrency(c.hourlyRate)}/giờ` }}
                </span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-form-item label="Số sân thuê" prop="courtCount" :error="serverErrors.courtCount">
            <el-input-number
              v-model="form.courtCount"
              :min="1"
              :max="50"
              :precision="0"
              controls-position="right"
              class="full-width"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="8">
          <el-form-item label="Ngày chơi" prop="playDate" :error="serverErrors.playDate">
            <el-date-picker
              v-model="form.playDate"
              type="date"
              format="DD/MM/YYYY"
              value-format="DD/MM/YYYY"
              placeholder="Chọn ngày"
              :disabled="locked"
              :disabled-date="disablePast"
              :editable="false"
              class="full-width"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="12" :sm="8">
          <el-form-item label="Giờ bắt đầu" prop="startTime" :error="serverErrors.startTime">
            <el-time-select
              v-model="form.startTime"
              start="05:00"
              step="00:15"
              end="23:30"
              placeholder="Bắt đầu"
              :disabled="locked"
              :editable="false"
              class="full-width"
              @change="handleStartChange"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="12" :sm="8">
          <el-form-item label="Giờ kết thúc" prop="endTime" :error="serverErrors.endTime">
            <el-time-select
              v-model="form.endTime"
              start="05:15"
              step="00:15"
              end="23:45"
              :min-time="form.startTime"
              placeholder="Kết thúc"
              :disabled="locked"
              :editable="false"
              class="full-width"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="8">
          <el-form-item label="Số người tối đa" prop="maxPlayers" :error="serverErrors.maxPlayers">
            <el-input-number
              v-model="form.maxPlayers"
              :min="locked ? Math.max(1, schedule.registeredCount) : 1"
              :max="500"
              :precision="0"
              controls-position="right"
              placeholder="VD: 16"
              class="full-width"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="16">
          <el-form-item label="Tiền sân dự kiến">
            <div class="estimate">
              <span class="money">{{ estimatedCost === null ? '—' : formatCurrency(estimatedCost) }}</span>
              <span v-if="estimatedCost !== null" class="text-secondary">
                {{ formatCurrency(selectedCourt.hourlyRate) }} × {{ formatDuration(durationMinutes) }} × {{ form.courtCount }} sân
              </span>
            </div>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="Ghi chú" prop="note" :error="serverErrors.note">
        <el-input v-model="form.note" type="textarea" :rows="2" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ isEdit ? 'Lưu thay đổi' : 'Thêm buổi chơi' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.type-group {
  flex-wrap: nowrap;
}

.locked-alert {
  margin-bottom: 16px;
}

.option-extra {
  float: right;
  margin-left: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.estimate {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 12px;
  min-height: 32px;
}

.money {
  font-weight: 600;
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

:global(.schedule-dialog) {
  max-width: calc(100vw - 32px);
}
</style>
