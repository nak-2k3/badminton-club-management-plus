<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Select } from '@element-plus/icons-vue'
import { scheduleApi } from '@/api/schedules'
import { useAuthStore } from '@/stores/auth'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { isFinished, canMarkAttendance } from '@/composables/useScheduleActions'
import AddParticipantDialog from '@/components/schedules/AddParticipantDialog.vue'
import {
  ATTENDANCE_STATUS_LABELS,
  ATTENDANCE_STATUS_TAG_TYPES,
  GENDER_LABELS,
  LEVEL_LABELS
} from '@/utils/labels'

// Thẻ "Người tham gia" trong trang chi tiết buổi chơi: danh sách đăng ký + điểm danh
const props = defineProps({
  schedule: { type: Object, required: true }
})
// changed: thêm/bớt người làm đổi số chỗ -> trang cha cập nhật buổi chơi
const emit = defineEmits(['changed'])

const auth = useAuthStore()
// Điện thoại: hiện dạng thẻ (nút điểm danh to, không phải cuộn ngang bảng)
const { isSmall } = useBreakpoint(768)
// Màu nút đang chọn theo ý nghĩa: có mặt xanh, vắng đỏ, chưa điểm danh xám
const ATTENDANCE_FILL = {
  PRESENT: 'var(--el-color-success)',
  ABSENT: 'var(--el-color-danger)',
  NOT_MARKED: 'var(--el-color-info)'
}
const isAdmin = computed(() => auth.hasRole('ADMIN'))

const participants = ref([])
const loading = ref(false)
const loadError = ref(false)
const addVisible = ref(false)
// userId đang được lưu điểm danh / xóa (khóa đúng dòng đó)
const busyUserId = ref(null)
const markingAll = ref(false)

const canMark = computed(() => isAdmin.value && canMarkAttendance(props.schedule))
// Admin thêm hộ: cùng điều kiện với tự đăng ký
const canAdd = computed(
  () =>
    isAdmin.value &&
    props.schedule.status === 'OPEN' &&
    !props.schedule.started &&
    props.schedule.registeredCount < props.schedule.maxPlayers
)
// registeredCount gồm cả khách; danh sách này chỉ có thành viên
const guestCount = computed(() => Math.max(props.schedule.registeredCount - participants.value.length, 0))
const summary = computed(() => {
  const count = { PRESENT: 0, ABSENT: 0, NOT_MARKED: 0 }
  participants.value.forEach((p) => count[p.attendanceStatus]++)
  return count
})
const showAttendance = computed(() => canMark.value || summary.value.PRESENT + summary.value.ABSENT > 0)
const isSelf = (p) => p.userId === auth.user?.id

async function load() {
  loading.value = true
  loadError.value = false
  try {
    participants.value = await scheduleApi.participants(props.schedule.id)
  } catch (err) {
    loadError.value = true
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

// Lưu ngay khi bấm; lỗi thì trả lại trạng thái cũ
async function mark(participant, status) {
  const previous = participant.attendanceStatus
  participant.attendanceStatus = status
  busyUserId.value = participant.userId
  try {
    const updated = await scheduleApi.markAttendance(props.schedule.id, participant.userId, status)
    Object.assign(participant, updated)
  } catch (err) {
    participant.attendanceStatus = previous
    ElMessage.error(err.message)
  } finally {
    busyUserId.value = null
  }
}

async function markAllPresent() {
  try {
    await ElMessageBox.confirm(
      `Đánh dấu ${summary.value.NOT_MARKED} người chưa điểm danh là "Có mặt"? Người đã điểm danh giữ nguyên.`,
      'Điểm danh nhanh',
      { confirmButtonText: 'Xác nhận', cancelButtonText: 'Hủy', type: 'info' }
    )
  } catch {
    return
  }
  markingAll.value = true
  try {
    participants.value = await scheduleApi.markAllAttendance(props.schedule.id, 'PRESENT')
    ElMessage.success('Đã điểm danh')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    markingAll.value = false
  }
}

async function removeParticipant(participant) {
  try {
    await ElMessageBox.confirm(
      `Bỏ "${participant.fullName}" khỏi buổi chơi? Chỗ này sẽ trống để người khác đăng ký.`,
      'Bỏ khỏi buổi chơi',
      { confirmButtonText: 'Bỏ ra', cancelButtonText: 'Hủy', type: 'warning', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  busyUserId.value = participant.userId
  try {
    const updated = await scheduleApi.removeParticipant(props.schedule.id, participant.userId)
    ElMessage.success(`Đã bỏ "${participant.fullName}" khỏi buổi chơi`)
    emit('changed', updated)
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    busyUserId.value = null
  }
}

// Người đã điểm danh phải bỏ điểm danh trước mới bỏ ra được (backend cũng chặn)
const canRemove = (p) =>
  isAdmin.value && !isFinished(props.schedule) && !isSelf(p) && p.attendanceStatus === 'NOT_MARKED'

// Tải lại khi đổi buổi hoặc số người thay đổi (tự đăng ký / hủy ở trang cha)
watch(
  () => [props.schedule.id, props.schedule.registeredCount, props.schedule.registeredByMe],
  load,
  { immediate: true }
)
</script>

<template>
  <el-card shadow="never" class="participants">
    <template #header>
      <div class="header">
        <span>
          Người tham gia
          <span class="text-secondary">
            ({{ participants.length }} thành viên<template v-if="guestCount"> + {{ guestCount }} khách</template>)
          </span>
        </span>
        <div class="header-actions">
          <el-button
            v-if="canMark && summary.NOT_MARKED > 0"
            :icon="Select"
            size="small"
            :loading="markingAll"
            @click="markAllPresent"
          >
            Tất cả có mặt
          </el-button>
          <el-button v-if="canAdd" type="primary" :icon="Plus" size="small" @click="addVisible = true">
            Thêm thành viên
          </el-button>
        </div>
      </div>
    </template>

    <div v-if="showAttendance && participants.length" class="summary">
      <el-tag type="success" effect="plain" round>Có mặt: {{ summary.PRESENT }}</el-tag>
      <el-tag type="danger" effect="plain" round>Vắng: {{ summary.ABSENT }}</el-tag>
      <el-tag type="info" effect="plain" round>Chưa điểm danh: {{ summary.NOT_MARKED }}</el-tag>
    </div>
    <p v-else-if="isAdmin && !canMarkAttendance(schedule) && schedule.status !== 'CANCELLED' && participants.length"
       class="text-secondary note">
      Điểm danh được mở từ ngày chơi ({{ schedule.playDate }}).
    </p>

    <el-result v-if="loadError" icon="error" title="Không tải được danh sách">
      <template #extra><el-button @click="load">Thử lại</el-button></template>
    </el-result>
    <el-empty
      v-else-if="!loading && !participants.length"
      :image-size="80"
      :description="schedule.status === 'OPEN' && !schedule.started ? 'Chưa có ai đăng ký — hãy là người đầu tiên!' : 'Không có thành viên nào đăng ký'"
    />
    <div v-else-if="isSmall" v-loading="loading" class="p-list">
      <div v-for="(row, index) in participants" :key="row.userId" class="p-item">
        <div class="p-head">
          <div class="p-info">
            <div class="name">
              {{ index + 1 }}. {{ row.fullName }}
              <el-tag v-if="isSelf(row)" size="small" round disable-transitions>Bạn</el-tag>
            </div>
            <div class="text-secondary">
              {{ GENDER_LABELS[row.gender] }} · {{ LEVEL_LABELS[row.levelName] ?? row.levelName ?? 'Chưa xếp trình độ' }}
              · Đăng ký {{ row.registeredAt }}
            </div>
          </div>
          <el-tag
            v-if="showAttendance && !canMark"
            :type="ATTENDANCE_STATUS_TAG_TYPES[row.attendanceStatus]"
            size="small"
            round
            disable-transitions
          >
            {{ ATTENDANCE_STATUS_LABELS[row.attendanceStatus] }}
          </el-tag>
          <el-button
            v-if="canRemove(row)"
            :icon="Delete"
            circle
            plain
            type="danger"
            size="small"
            :loading="busyUserId === row.userId"
            :aria-label="`Bỏ ${row.fullName} khỏi buổi chơi`"
            @click="removeParticipant(row)"
          />
        </div>
        <template v-if="canMark">
          <el-radio-group
            :model-value="row.attendanceStatus"
            :fill="ATTENDANCE_FILL[row.attendanceStatus]"
            :disabled="busyUserId === row.userId"
            :aria-label="`Điểm danh ${row.fullName}`"
            class="p-attendance"
            @change="(value) => mark(row, value)"
          >
            <el-radio-button value="PRESENT">Có mặt</el-radio-button>
            <el-radio-button value="ABSENT">Vắng</el-radio-button>
            <el-radio-button value="NOT_MARKED">Chưa</el-radio-button>
          </el-radio-group>
          <div v-if="row.checkedByName" class="text-secondary">
            Điểm danh bởi {{ row.checkedByName }} · {{ row.checkedAt }}
          </div>
        </template>
      </div>
    </div>
    <div v-else class="table-wrap">
      <el-table v-loading="loading" :data="participants" row-key="userId" stripe>
        <el-table-column label="STT" width="56" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="Thành viên" min-width="180">
          <template #default="{ row }">
            <div class="name">
              {{ row.fullName }}
              <el-tag v-if="isSelf(row)" size="small" round disable-transitions>Bạn</el-tag>
            </div>
            <div class="text-secondary">
              {{ GENDER_LABELS[row.gender] }} · {{ LEVEL_LABELS[row.levelName] ?? row.levelName ?? 'Chưa xếp trình độ' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Đăng ký lúc" prop="registeredAt" width="140" />
        <el-table-column v-if="showAttendance" label="Điểm danh" :width="canMark ? 236 : 130">
          <template #default="{ row }">
            <el-radio-group
              v-if="canMark"
              :model-value="row.attendanceStatus"
              :fill="ATTENDANCE_FILL[row.attendanceStatus]"
              size="small"
              :disabled="busyUserId === row.userId"
              :aria-label="`Điểm danh ${row.fullName}`"
              @change="(value) => mark(row, value)"
            >
              <el-radio-button value="PRESENT">Có mặt</el-radio-button>
              <el-radio-button value="ABSENT">Vắng</el-radio-button>
              <el-radio-button value="NOT_MARKED">Chưa</el-radio-button>
            </el-radio-group>
            <el-tag v-else :type="ATTENDANCE_STATUS_TAG_TYPES[row.attendanceStatus]" size="small" round disable-transitions>
              {{ ATTENDANCE_STATUS_LABELS[row.attendanceStatus] }}
            </el-tag>
            <div v-if="canMark && row.checkedByName" class="text-secondary checked">
              {{ row.checkedByName }} · {{ row.checkedAt }}
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="isAdmin && !isFinished(schedule)" label="" width="56" fixed="right" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="canRemove(row)" content="Bỏ khỏi buổi chơi" :show-after="300">
              <el-button
                :icon="Delete"
                link
                type="danger"
                :loading="busyUserId === row.userId"
                :aria-label="`Bỏ ${row.fullName} khỏi buổi chơi`"
                @click="removeParticipant(row)"
              />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <AddParticipantDialog
      v-model="addVisible"
      :schedule="schedule"
      :participant-ids="participants.map((p) => p.userId)"
      @saved="(updated) => emit('changed', updated)"
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

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.header-actions .el-button {
  margin-left: 0;
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

.table-wrap {
  overflow-x: auto;
}

.name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.checked {
  margin-top: 2px;
}

.p-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.p-item:last-child {
  border-bottom: none;
}

.p-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.p-info {
  min-width: 0;
}

/* 3 nút chia đều chiều ngang: vùng bấm lớn trên điện thoại */
.p-attendance {
  display: flex;
  width: 100%;
}

.p-attendance :deep(.el-radio-button) {
  flex: 1;
}

.p-attendance :deep(.el-radio-button__inner) {
  width: 100%;
}
</style>
