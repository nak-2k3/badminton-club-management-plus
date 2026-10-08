<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Edit, Lock, Unlock, CircleCheck, CircleClose, Delete, Check } from '@element-plus/icons-vue'
import { scheduleApi } from '@/api/schedules'
import { useAuthStore } from '@/stores/auth'
import { useBreakpoint } from '@/composables/useBreakpoint'
import {
  useScheduleActions,
  isFinished,
  canComplete,
  canRegister,
  canSelfCancel,
  registerBlockedReason
} from '@/composables/useScheduleActions'
import ScheduleFormDialog from '@/components/schedules/ScheduleFormDialog.vue'
import ScheduleParticipants from '@/components/schedules/ScheduleParticipants.vue'
import ScheduleGuests from '@/components/schedules/ScheduleGuests.vue'
import { SCHEDULE_STATUS_LABELS, SCHEDULE_STATUS_TAG_TYPES, SCHEDULE_TYPE_LABELS } from '@/utils/labels'
import { formatCurrency, formatDuration, minutesBetween, weekdayOf } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { changeStatus, remove, register, cancelRegistration } = useScheduleActions()
const { isSmall } = useBreakpoint(768)

const schedule = ref(null)
const loading = ref(false)
const error = ref(null)
const formVisible = ref(false)

const isAdmin = computed(() => auth.hasRole('ADMIN'))
// Tiền sân chỉ hiện cho người quản lý thu chi
const canSeeCost = computed(() => auth.hasRole('ADMIN', 'TREASURER'))
const duration = computed(() =>
  schedule.value ? minutesBetween(schedule.value.startTime, schedule.value.endTime) : 0
)
const slotsLeft = computed(() =>
  schedule.value ? Math.max(schedule.value.maxPlayers - schedule.value.registeredCount, 0) : 0
)

async function fetchSchedule() {
  loading.value = true
  error.value = null
  try {
    schedule.value = await scheduleApi.getById(route.params.id)
  } catch (err) {
    schedule.value = null
    error.value = err
  } finally {
    loading.value = false
  }
}

// Tải lại buổi chơi không hiện vòng xoay toàn trang (sau khi thêm/hủy khách đổi số chỗ)
async function refreshSchedule() {
  try {
    schedule.value = await scheduleApi.getById(route.params.id)
  } catch (err) {
    ElMessage.error(err.message)
  }
}

// Phối hợp 2 thẻ: "Tất cả có mặt" ở thẻ người tham gia cũng điểm danh khách -> tải lại thẻ khách
const guestNotMarked = ref(0)
const guestsReloadKey = ref(0)

async function handleStatus(status) {
  const updated = await changeStatus(schedule.value, status)
  if (updated) schedule.value = updated
}

const registering = ref(false)
async function toggleRegistration() {
  registering.value = true
  try {
    const updated = schedule.value.registeredByMe
      ? await cancelRegistration(schedule.value)
      : await register(schedule.value)
    if (updated) schedule.value = updated
  } finally {
    registering.value = false
  }
}

async function handleDelete() {
  if (await remove(schedule.value)) router.replace({ name: 'schedules' })
}

function goBack() {
  // Quay lại danh sách (giữ bộ lọc) nếu đi từ danh sách; mở link trực tiếp thì về danh sách mặc định
  if (window.history.state?.back) router.back()
  else router.push({ name: 'schedules' })
}

watch(() => route.params.id, (id) => id && fetchSchedule())
onMounted(fetchSchedule)
</script>

<template>
  <div v-loading="loading" class="detail-page">
    <el-button :icon="ArrowLeft" text class="back" @click="goBack">Danh sách buổi chơi</el-button>

    <el-result
      v-if="error"
      :icon="error.status === 404 ? 'warning' : 'error'"
      :title="error.status === 404 ? 'Không tìm thấy buổi chơi' : 'Không tải được dữ liệu'"
      :sub-title="error.message"
    >
      <template #extra>
        <el-button type="primary" @click="router.push({ name: 'schedules' })">Về danh sách</el-button>
      </template>
    </el-result>

    <template v-else-if="schedule">
      <el-card shadow="never" class="summary">
        <div class="summary-info">
          <h2>{{ schedule.title }}</h2>
          <p class="text-secondary">
            {{ weekdayOf(schedule.playDate) }}, {{ schedule.playDate }} · {{ schedule.startTime }} – {{ schedule.endTime }}
            · {{ schedule.courtName }}
          </p>
          <div class="tags">
            <el-tag :type="SCHEDULE_STATUS_TAG_TYPES[schedule.status]">{{ SCHEDULE_STATUS_LABELS[schedule.status] }}</el-tag>
            <el-tag type="primary" effect="plain">{{ SCHEDULE_TYPE_LABELS[schedule.scheduleType] }}</el-tag>
            <el-tag v-if="!isFinished(schedule) && schedule.started" type="info" effect="plain">Đã qua giờ bắt đầu</el-tag>
            <el-tag v-else-if="schedule.status === 'OPEN' && slotsLeft === 0" type="danger" effect="plain">Hết chỗ</el-tag>
          </div>
        </div>
        <div v-if="isAdmin" class="actions">
          <template v-if="!isFinished(schedule)">
            <el-button type="primary" :icon="Edit" @click="formVisible = true">Sửa buổi chơi</el-button>
            <el-button v-if="schedule.status === 'OPEN'" :icon="Lock" @click="handleStatus('CLOSED')">
              Đóng đăng ký
            </el-button>
            <el-button v-if="schedule.status === 'CLOSED' && !schedule.started" :icon="Unlock" @click="handleStatus('OPEN')">
              Mở lại đăng ký
            </el-button>
            <el-button v-if="canComplete(schedule)" type="success" plain :icon="CircleCheck" @click="handleStatus('COMPLETED')">
              Hoàn thành
            </el-button>
            <el-button type="danger" plain :icon="CircleClose" @click="handleStatus('CANCELLED')">Hủy buổi</el-button>
          </template>
          <el-button type="danger" text :icon="Delete" @click="handleDelete">Xóa</el-button>
        </div>
      </el-card>

      <!-- Đăng ký của chính mình: mọi vai trò -->
      <el-card shadow="never" class="my-registration" :class="{ joined: schedule.registeredByMe }">
        <div class="my-registration-body">
          <div>
            <div class="my-status">
              <template v-if="schedule.registeredByMe">
                <el-icon class="joined-icon"><Check /></el-icon> Bạn đã đăng ký buổi này
              </template>
              <template v-else>Bạn chưa đăng ký buổi này</template>
            </div>
            <div class="text-secondary">
              <template v-if="schedule.registeredByMe && canSelfCancel(schedule)">
                Bạn tự hủy được đến {{ schedule.selfCancelDeadline }}; sau đó cần nhờ quản trị viên.
              </template>
              <template v-else-if="schedule.registeredByMe && !isFinished(schedule)">
                Đã quá hạn tự hủy ({{ schedule.selfCancelDeadline }}). Liên hệ quản trị viên nếu không tham gia được.
              </template>
              <template v-else-if="canRegister(schedule)">
                Còn {{ slotsLeft }} chỗ. Tự hủy được đến {{ schedule.selfCancelDeadline }}.
              </template>
              <template v-else-if="registerBlockedReason(schedule)">
                {{ registerBlockedReason(schedule) }} — không thể đăng ký.
              </template>
            </div>
          </div>
          <el-button
            v-if="canRegister(schedule)"
            type="primary"
            :loading="registering"
            class="register-btn"
            @click="toggleRegistration"
          >
            Đăng ký tham gia
          </el-button>
          <el-button
            v-else-if="canSelfCancel(schedule)"
            type="danger"
            plain
            :loading="registering"
            class="register-btn"
            @click="toggleRegistration"
          >
            Hủy đăng ký
          </el-button>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>Thông tin buổi chơi</template>
        <el-descriptions :column="isSmall ? 1 : 2" border>
          <el-descriptions-item label="Ngày chơi">
            {{ weekdayOf(schedule.playDate) }}, {{ schedule.playDate }}
          </el-descriptions-item>
          <el-descriptions-item label="Giờ chơi">
            {{ schedule.startTime }} – {{ schedule.endTime }}
            <span class="text-secondary">({{ formatDuration(duration) }})</span>
          </el-descriptions-item>
          <el-descriptions-item label="Địa điểm">
            {{ schedule.courtName }}
            <el-tag v-if="!schedule.courtActive" type="info" size="small" round>Tạm ngưng</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Số sân thuê">{{ schedule.courtCount }} sân</el-descriptions-item>
          <el-descriptions-item label="Địa chỉ" :span="isSmall ? 1 : 2">{{ schedule.courtAddress }}</el-descriptions-item>
          <el-descriptions-item label="Đã đăng ký">
            <span class="count">{{ schedule.registeredCount }} / {{ schedule.maxPlayers }} người</span>
            <span class="text-secondary"> (còn {{ slotsLeft }} chỗ)</span>
          </el-descriptions-item>
          <el-descriptions-item label="Loại buổi">{{ SCHEDULE_TYPE_LABELS[schedule.scheduleType] }}</el-descriptions-item>
          <template v-if="canSeeCost">
            <el-descriptions-item label="Giá thuê">{{ formatCurrency(schedule.hourlyRate) }} / sân / giờ</el-descriptions-item>
            <el-descriptions-item label="Tiền sân dự kiến">
              <span class="money">{{ formatCurrency(schedule.estimatedCourtCost) }}</span>
              <div class="text-secondary">
                {{ formatCurrency(schedule.hourlyRate) }} × {{ formatDuration(duration) }} × {{ schedule.courtCount }} sân
                (theo giá hiện tại của sân)
              </div>
            </el-descriptions-item>
          </template>
          <el-descriptions-item label="Người tạo">{{ schedule.createdByName }}</el-descriptions-item>
          <el-descriptions-item label="Ngày tạo">{{ schedule.createdAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Ghi chú" :span="isSmall ? 1 : 2">{{ schedule.note || '—' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <ScheduleParticipants
        :schedule="schedule"
        :extra-not-marked="guestNotMarked"
        @changed="(updated) => (schedule = updated)"
        @marked-all="guestsReloadKey++"
      />
      <ScheduleGuests
        :schedule="schedule"
        :reload-key="guestsReloadKey"
        @changed="refreshSchedule"
        @summary="(s) => (guestNotMarked = s.notMarked)"
      />
    </template>

    <ScheduleFormDialog v-model="formVisible" :schedule="schedule" @saved="(saved) => (schedule = saved)" />
  </div>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}

.back {
  align-self: flex-start;
  padding-left: 0;
}

.summary {
  border-left: 4px solid var(--el-color-primary);
}

.summary :deep(.el-card__body) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.summary-info {
  min-width: 0;
}

.summary-info h2 {
  margin: 0 0 2px;
  font-size: 22px;
}

.summary-info p {
  margin: 0 0 10px;
  font-size: 13px;
}

.tags,
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.actions .el-button {
  margin-left: 0;
}

.count,
.money {
  font-variant-numeric: tabular-nums;
}

.money {
  font-weight: 600;
}

.my-registration.joined {
  border-color: var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
}

.my-registration-body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.my-status {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-weight: 600;
}

.joined-icon {
  color: var(--el-color-success);
}

@media (max-width: 768px) {
  .actions,
  .register-btn {
    width: 100%;
  }
}
</style>
