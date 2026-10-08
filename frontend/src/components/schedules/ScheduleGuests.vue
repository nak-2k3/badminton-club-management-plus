<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Edit } from '@element-plus/icons-vue'
import { guestApi } from '@/api/guests'
import { useAuthStore } from '@/stores/auth'
import { isFinished, canMarkAttendance } from '@/composables/useScheduleActions'
import GuestFormDialog from '@/components/schedules/GuestFormDialog.vue'
import {
  ATTENDANCE_STATUS_LABELS,
  ATTENDANCE_STATUS_TAG_TYPES,
  GENDER_LABELS,
  PAYMENT_STATUS_LABELS,
  PAYMENT_STATUS_TAG_TYPES
} from '@/utils/labels'
import { formatCurrency, parseDateTime } from '@/utils/format'

// Thẻ "Khách vãng lai" trong trang chi tiết buổi chơi: dẫn khách, thu phí khách, điểm danh khách
const props = defineProps({
  schedule: { type: Object, required: true },
  // Tăng lên để tải lại (vd sau "Tất cả có mặt" ở thẻ người tham gia)
  reloadKey: { type: Number, default: 0 }
})
// changed: thêm/hủy khách làm đổi số chỗ; summary: số khách chưa điểm danh (cho nút "Tất cả có mặt")
const emit = defineEmits(['changed', 'summary'])

const auth = useAuthStore()
const ATTENDANCE_FILL = {
  PRESENT: 'var(--el-color-success)',
  ABSENT: 'var(--el-color-danger)',
  NOT_MARKED: 'var(--el-color-info)'
}
const isAdmin = computed(() => auth.hasRole('ADMIN'))
// Người quản lý thu chi: thêm hộ, thu tiền, hủy khách của người khác
const isManager = computed(() => auth.hasRole('ADMIN', 'TREASURER'))

const guests = ref([])
const loading = ref(false)
const loadError = ref(false)
const formVisible = ref(false)
// null: dẫn khách mới; object: đang sửa khách đó
const editingGuest = ref(null)
const busyId = ref(null)

const hasSlot = computed(() => props.schedule.registeredCount < props.schedule.maxPlayers)
// Cùng điều kiện với đăng ký thành viên; thành viên phải đang đăng ký buổi này mới dẫn khách được
const canAdd = computed(
  () =>
    props.schedule.status === 'OPEN' &&
    !props.schedule.started &&
    hasSlot.value &&
    (props.schedule.registeredByMe || isManager.value)
)
const addHint = computed(() => {
  if (canAdd.value || isFinished(props.schedule)) return ''
  if (props.schedule.status !== 'OPEN' || props.schedule.started) return ''
  if (!hasSlot.value) return 'Buổi đã đủ người.'
  return 'Đăng ký tham gia buổi này để có thể dẫn khách.'
})
const canMark = computed(() => isAdmin.value && canMarkAttendance(props.schedule))
const showAttendance = computed(() => canMark.value || guests.value.some((g) => g.attendanceStatus !== 'NOT_MARKED'))
const paidSummary = computed(() => {
  const visible = guests.value.filter((g) => g.paymentStatus)
  const paid = visible.filter((g) => g.paymentStatus === 'PAID')
  return {
    paid: paid.length,
    total: visible.length,
    amount: paid.reduce((sum, g) => sum + Number(g.fee), 0)
  }
})

const isMine = (g) => g.invitedById === auth.user?.id
// Sửa: người quản lý thu chi bất kỳ lúc nào; người dẫn khi buổi chưa kết thúc (backend kiểm tra thêm hồ sơ dùng chung)
const canEdit = (g) => isManager.value || (isMine(g) && !isFinished(props.schedule))

function openAdd() {
  editingGuest.value = null
  formVisible.value = true
}

function openEdit(guest) {
  editingGuest.value = guest
  formVisible.value = true
}

// Thêm khách làm đổi số chỗ -> trang cha tải lại buổi; sửa thì chỉ tải lại danh sách khách
function handleSaved() {
  if (editingGuest.value) load()
  else emit('changed')
}
const beforeDeadline = () => new Date() < parseDateTime(props.schedule.selfCancelDeadline)
// Khách của mình: trước hạn chót tự hủy (cả với ADMIN/TREASURER); khách của người khác: chỉ người quản lý
function canRemove(g) {
  if (isFinished(props.schedule) || g.paymentStatus === 'PAID' || g.attendanceStatus !== 'NOT_MARKED') return false
  return isMine(g) ? beforeDeadline() : isManager.value
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    guests.value = await guestApi.list(props.schedule.id)
    emit('summary', { notMarked: guests.value.filter((g) => g.attendanceStatus === 'NOT_MARKED').length })
  } catch (err) {
    loadError.value = true
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function replace(updated) {
  const index = guests.value.findIndex((g) => g.id === updated.id)
  if (index !== -1) guests.value[index] = updated
}

async function setPayment(guest, status) {
  if (status === 'UNPAID') {
    try {
      await ElMessageBox.confirm(
        `Chuyển khách "${guest.fullName}" về "Chưa thu"? Thông tin người thu và thời điểm thu sẽ bị xóa.`,
        'Hoàn tác thu tiền',
        { confirmButtonText: 'Hoàn tác', cancelButtonText: 'Không', type: 'warning' }
      )
    } catch {
      return
    }
  }
  busyId.value = guest.id
  try {
    replace(await guestApi.setPayment(props.schedule.id, guest.id, status))
    ElMessage.success(status === 'PAID'
      ? `Đã thu ${formatCurrency(guest.fee)} của "${guest.fullName}"`
      : 'Đã chuyển về chưa thu')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    busyId.value = null
  }
}

// Lưu ngay khi bấm; lỗi thì trả lại trạng thái cũ
async function mark(guest, status) {
  const previous = guest.attendanceStatus
  guest.attendanceStatus = status
  busyId.value = guest.id
  try {
    replace(await guestApi.markAttendance(props.schedule.id, guest.id, status))
    emit('summary', { notMarked: guests.value.filter((g) => g.attendanceStatus === 'NOT_MARKED').length })
  } catch (err) {
    guest.attendanceStatus = previous
    ElMessage.error(err.message)
  } finally {
    busyId.value = null
  }
}

async function removeGuest(guest) {
  try {
    await ElMessageBox.confirm(
      `Hủy khách "${guest.fullName}" khỏi buổi chơi? Chỗ này sẽ trống để người khác đăng ký.`,
      'Hủy khách',
      { confirmButtonText: 'Hủy khách', cancelButtonText: 'Không', type: 'warning', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  busyId.value = guest.id
  try {
    await guestApi.remove(props.schedule.id, guest.id)
    ElMessage.success(`Đã hủy khách "${guest.fullName}"`)
    emit('changed')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    busyId.value = null
  }
}

watch(
  () => [props.schedule.id, props.schedule.registeredCount, props.reloadKey],
  load,
  { immediate: true }
)
</script>

<template>
  <el-card shadow="never" class="guests">
    <template #header>
      <div class="header">
        <span>
          Khách vãng lai
          <span class="text-secondary">({{ guests.length }} khách)</span>
        </span>
        <el-button v-if="canAdd" type="primary" :icon="Plus" size="small" @click="openAdd">
          Dẫn khách
        </el-button>
      </div>
    </template>

    <div v-if="isManager && guests.length" class="summary">
      <el-tag type="success" effect="plain" round>Đã thu: {{ paidSummary.paid }}/{{ paidSummary.total }}</el-tag>
      <el-tag effect="plain" round>Tiền đã thu: {{ formatCurrency(paidSummary.amount) }}</el-tag>
    </div>
    <p v-if="addHint" class="text-secondary note">{{ addHint }}</p>

    <el-result v-if="loadError" icon="error" title="Không tải được danh sách khách">
      <template #extra><el-button @click="load">Thử lại</el-button></template>
    </el-result>
    <el-empty v-else-if="!loading && !guests.length" :image-size="64" description="Chưa có khách nào trong buổi này" />
    <div v-else v-loading="loading" class="g-list">
      <div v-for="(g, index) in guests" :key="g.id" class="g-item">
        <div class="g-info">
          <div class="name">
            {{ index + 1 }}. {{ g.fullName }}
            <el-tag v-if="isMine(g)" size="small" round disable-transitions>Khách của bạn</el-tag>
          </div>
          <div class="text-secondary">
            <template v-if="g.gender">{{ GENDER_LABELS[g.gender] }} · </template>
            <template v-if="g.phone">{{ g.phone }} · </template>
            {{ g.invitedByName ? `Dẫn bởi ${g.invitedByName}` : 'Khách tự liên hệ CLB' }}
          </div>
          <div v-if="g.note" class="text-secondary">Ghi chú: {{ g.note }}</div>
        </div>

        <div class="g-controls">
          <!-- Phí & thu tiền: chỉ người quản lý thu chi và người dẫn thấy (backend trả null cho người khác) -->
          <div v-if="g.paymentStatus" class="g-payment">
            <span class="money">{{ formatCurrency(g.fee) }}</span>
            <template v-if="isManager">
              <el-button
                v-if="g.paymentStatus === 'UNPAID'"
                type="success"
                size="small"
                plain
                :loading="busyId === g.id"
                :disabled="schedule.status === 'CANCELLED'"
                @click="setPayment(g, 'PAID')"
              >
                Thu tiền
              </el-button>
              <template v-else>
                <el-tag type="success" size="small" round disable-transitions>Đã thu</el-tag>
                <el-button link type="info" size="small" :disabled="busyId === g.id" @click="setPayment(g, 'UNPAID')">
                  Hoàn tác
                </el-button>
              </template>
            </template>
            <el-tag v-else :type="PAYMENT_STATUS_TAG_TYPES[g.paymentStatus]" size="small" round disable-transitions>
              {{ PAYMENT_STATUS_LABELS[g.paymentStatus] }}
            </el-tag>
            <div v-if="g.paidAt" class="text-secondary paid-info">
              {{ g.collectedByName }} thu lúc {{ g.paidAt }}
            </div>
          </div>

          <el-radio-group
            v-if="canMark"
            :model-value="g.attendanceStatus"
            :fill="ATTENDANCE_FILL[g.attendanceStatus]"
            size="small"
            :disabled="busyId === g.id"
            :aria-label="`Điểm danh khách ${g.fullName}`"
            class="g-attendance"
            @change="(value) => mark(g, value)"
          >
            <el-radio-button value="PRESENT">Có mặt</el-radio-button>
            <el-radio-button value="ABSENT">Vắng</el-radio-button>
            <el-radio-button value="NOT_MARKED">Chưa</el-radio-button>
          </el-radio-group>
          <el-tag
            v-else-if="showAttendance"
            :type="ATTENDANCE_STATUS_TAG_TYPES[g.attendanceStatus]"
            size="small"
            round
            disable-transitions
          >
            {{ ATTENDANCE_STATUS_LABELS[g.attendanceStatus] }}
          </el-tag>

          <el-tooltip v-if="canEdit(g)" content="Sửa thông tin khách" :show-after="300">
            <el-button
              :icon="Edit"
              circle
              plain
              type="primary"
              size="small"
              :aria-label="`Sửa thông tin khách ${g.fullName}`"
              @click="openEdit(g)"
            />
          </el-tooltip>
          <el-tooltip v-if="canRemove(g)" content="Hủy khách" :show-after="300">
            <el-button
              :icon="Delete"
              circle
              plain
              type="danger"
              size="small"
              :loading="busyId === g.id"
              :aria-label="`Hủy khách ${g.fullName}`"
              @click="removeGuest(g)"
            />
          </el-tooltip>
        </div>
      </div>
    </div>

    <GuestFormDialog
      v-model="formVisible"
      :schedule="schedule"
      :guest="editingGuest"
      :manager="isManager"
      @saved="handleSaved"
    />
  </el-card>
</template>

<style scoped>
.header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.note {
  margin: 0 0 12px;
}

.g-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.g-item:last-child {
  border-bottom: none;
}

.g-info {
  min-width: 200px;
  flex: 1;
}

.name {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.g-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
}

.g-payment {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
}

.g-payment .el-button {
  margin-left: 0;
}

.paid-info {
  flex-basis: 100%;
}

.money {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .g-controls {
    width: 100%;
  }

  /* 3 nút điểm danh chia đều chiều ngang: vùng bấm lớn trên điện thoại */
  .g-attendance {
    display: flex;
    flex: 1 1 100%;
  }

  .g-attendance :deep(.el-radio-button) {
    flex: 1;
  }

  .g-attendance :deep(.el-radio-button__inner) {
    width: 100%;
  }
}
</style>
