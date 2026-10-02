<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { scheduleApi } from '@/api/schedules'
import { memberApi } from '@/api/members'
import { LEVEL_LABELS } from '@/utils/labels'

// ADMIN thêm 1 thành viên vào buổi chơi (cùng quy tắc số chỗ / giờ như tự đăng ký)
const props = defineProps({
  schedule: { type: Object, required: true },
  // userId đã có trong danh sách -> làm mờ trong ô chọn
  participantIds: { type: Array, default: () => [] }
})
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const userId = ref(null)
const options = ref([])
const searching = ref(false)
const saving = ref(false)
const error = ref('')
const joined = computed(() => new Set(props.participantIds))
let latestRequestId = 0

// Tìm thành viên đang hoạt động theo tên / email / SĐT
async function searchMembers(keyword) {
  const requestId = ++latestRequestId
  searching.value = true
  try {
    const res = await memberApi.search({
      keyword: keyword || undefined,
      status: 'ACTIVE',
      size: 20,
      sort: 'fullName',
      direction: 'asc'
    })
    if (requestId === latestRequestId) options.value = res.content
  } catch (err) {
    if (requestId === latestRequestId) ElMessage.error(err.message)
  } finally {
    if (requestId === latestRequestId) searching.value = false
  }
}

watch(visible, (open) => {
  if (!open) return
  userId.value = null
  error.value = ''
  searchMembers('')
})

async function handleSubmit() {
  error.value = ''
  if (!userId.value) {
    error.value = 'Vui lòng chọn thành viên'
    return
  }
  saving.value = true
  try {
    const updated = await scheduleApi.addParticipant(props.schedule.id, userId.value)
    const member = options.value.find((m) => m.id === userId.value)
    ElMessage.success(`Đã thêm "${member?.fullName ?? 'thành viên'}" vào buổi chơi`)
    visible.value = false
    emit('saved', updated)
  } catch (err) {
    error.value = err.errors?.userId ?? ''
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="Thêm thành viên vào buổi chơi"
    width="480px"
    :close-on-click-modal="false"
    destroy-on-close
    class="add-participant-dialog"
  >
    <p class="text-secondary intro">
      Còn {{ Math.max(schedule.maxPlayers - schedule.registeredCount, 0) }} chỗ. Chỉ thêm được khi buổi đang mở đăng ký
      và chưa tới giờ bắt đầu.
    </p>
    <el-form label-position="top" @submit.prevent="handleSubmit">
      <el-form-item label="Thành viên" :error="error">
        <el-select
          v-model="userId"
          filterable
          remote
          :remote-method="searchMembers"
          :loading="searching"
          placeholder="Gõ tên, email hoặc SĐT để tìm"
          no-data-text="Không tìm thấy thành viên đang hoạt động"
          class="full-width"
        >
          <el-option
            v-for="m in options"
            :key="m.id"
            :value="m.id"
            :label="m.fullName"
            :disabled="joined.has(m.id)"
          >
            <span>{{ m.fullName }}</span>
            <span class="option-extra">
              {{ joined.has(m.id) ? 'Đã đăng ký' : LEVEL_LABELS[m.levelName] ?? m.levelName ?? m.email }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">Thêm vào buổi</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.intro {
  margin: 0 0 12px;
}

.option-extra {
  float: right;
  margin-left: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

:global(.add-participant-dialog) {
  max-width: calc(100vw - 32px);
}
</style>
