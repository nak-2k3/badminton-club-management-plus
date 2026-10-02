<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search, View, Edit, MoreFilled } from '@element-plus/icons-vue'
import { scheduleApi } from '@/api/schedules'
import { courtApi } from '@/api/courts'
import { useAuthStore } from '@/stores/auth'
import {
  useScheduleActions,
  isFinished,
  canComplete,
  canRegister,
  canSelfCancel,
  registerBlockedReason
} from '@/composables/useScheduleActions'
import ScheduleFormDialog from '@/components/schedules/ScheduleFormDialog.vue'
import {
  SCHEDULE_STATUS_LABELS,
  SCHEDULE_STATUS_TAG_TYPES,
  SCHEDULE_TYPE_LABELS,
  toOptions
} from '@/utils/labels'
import { parseDate, todayText, weekdayOf } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { changeStatus, remove, register, cancelRegistration } = useScheduleActions()

const isAdmin = computed(() => auth.hasRole('ADMIN'))
const statusOptions = toOptions(SCHEDULE_STATUS_LABELS)
const typeOptions = toOptions(SCHEDULE_TYPE_LABELS)
const courts = ref([])

// Bộ lọc đồng bộ lên URL. "Từ ngày" mặc định là hôm nay (xem các buổi sắp tới);
// người dùng xóa ô này thì URL giữ from='' để tải lại trang vẫn xem được cả buổi đã qua.
const filters = reactive({
  keyword: route.query.keyword ?? '',
  from: 'from' in route.query ? route.query.from : todayText(),
  to: route.query.to ?? '',
  status: route.query.status ?? '',
  scheduleType: route.query.scheduleType ?? '',
  courtId: route.query.courtId ? Number(route.query.courtId) : '',
  // Chỉ các buổi tôi đã đăng ký
  mine: route.query.mine === 'true'
})
const pagination = reactive({
  page: Number(route.query.page) || 1, // giao diện đếm từ 1, API đếm từ 0
  size: Number(route.query.size) || 10,
  total: 0
})
const sorting = reactive({
  sort: route.query.sort ?? 'playDate',
  direction: route.query.direction ?? 'asc'
})

const schedules = ref([])
const loading = ref(false)
let latestRequestId = 0

const formVisible = ref(false)
const editingSchedule = ref(null)

async function fetchSchedules() {
  const requestId = ++latestRequestId
  loading.value = true
  const params = {
    ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== '' && v !== null && v !== false)),
    page: pagination.page - 1,
    size: pagination.size,
    sort: sorting.sort,
    direction: sorting.direction
  }
  router.replace({ query: { ...params, from: filters.from ?? '', page: pagination.page } })
  try {
    const res = await scheduleApi.search(params)
    if (requestId !== latestRequestId) return
    if (res.content.length === 0 && res.totalElements > 0 && pagination.page > 1) {
      pagination.page = res.totalPages
      return fetchSchedules()
    }
    schedules.value = res.content
    pagination.total = res.totalElements
  } catch (err) {
    if (requestId === latestRequestId) ElMessage.error(err.message)
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

let keywordTimer
let skipNextKeywordWatch = false

function search() {
  clearTimeout(keywordTimer)
  pagination.page = 1
  fetchSchedules()
}

// Gõ từ khóa: chờ 400ms sau lần gõ cuối mới tìm
watch(
  () => filters.keyword,
  () => {
    if (skipNextKeywordWatch) {
      skipNextKeywordWatch = false
      return
    }
    clearTimeout(keywordTimer)
    keywordTimer = setTimeout(search, 400)
  }
)

function resetFilters() {
  if (filters.keyword !== '') skipNextKeywordWatch = true
  Object.assign(filters, {
    keyword: '',
    from: todayText(),
    to: '',
    status: '',
    scheduleType: '',
    courtId: '',
    mine: false
  })
  search()
}

// "Đến ngày" không được trước "Từ ngày" và ngược lại
const disableBeforeFrom = (date) => !!filters.from && date < parseDate(filters.from)
const disableAfterTo = (date) => !!filters.to && date > parseDate(filters.to)

function handleSortChange({ order }) {
  sorting.sort = 'playDate'
  sorting.direction = order === 'descending' ? 'desc' : 'asc'
  search()
}
const defaultSort = { prop: 'playDate', order: sorting.direction === 'desc' ? 'descending' : 'ascending' }

function openCreate() {
  editingSchedule.value = null
  formVisible.value = true
}

function openEdit(schedule) {
  editingSchedule.value = schedule
  formVisible.value = true
}

function openDetail(schedule) {
  router.push({ name: 'schedule-detail', params: { id: schedule.id } })
}

async function handleCommand(command, schedule) {
  const done = command === 'delete' ? await remove(schedule) : await changeStatus(schedule, command)
  if (done) fetchSchedules()
}

// Đăng ký / hủy đăng ký ngay trên dòng; thay dòng bằng dữ liệu mới trả về (số chỗ, cờ đã đăng ký)
const busyId = ref(null)
async function toggleRegistration(schedule) {
  busyId.value = schedule.id
  try {
    const updated = schedule.registeredByMe ? await cancelRegistration(schedule) : await register(schedule)
    if (!updated) return
    // Đang lọc "Tôi đã đăng ký" mà vừa hủy -> tải lại để dòng biến mất
    if (filters.mine && !updated.registeredByMe) return fetchSchedules()
    const index = schedules.value.findIndex((s) => s.id === updated.id)
    if (index !== -1) schedules.value[index] = updated
  } finally {
    busyId.value = null
  }
}

async function loadCourts() {
  try {
    courts.value = await courtApi.list()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

onMounted(() => {
  loadCourts()
  fetchSchedules()
})
</script>

<template>
  <div class="schedule-list">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="Tìm theo tên buổi, tên sân"
          clearable
          class="keyword"
          @keyup.enter="search"
        />
        <el-date-picker
          v-model="filters.from"
          type="date"
          format="DD/MM/YYYY"
          value-format="DD/MM/YYYY"
          placeholder="Từ ngày"
          :disabled-date="disableAfterTo"
          :editable="false"
          class="filter date"
          @change="search"
        />
        <el-date-picker
          v-model="filters.to"
          type="date"
          format="DD/MM/YYYY"
          value-format="DD/MM/YYYY"
          placeholder="Đến ngày"
          :disabled-date="disableBeforeFrom"
          :editable="false"
          class="filter date"
          @change="search"
        />
        <el-select v-model="filters.status" placeholder="Trạng thái" clearable class="filter" @change="search">
          <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-select v-model="filters.scheduleType" placeholder="Loại buổi" clearable class="filter" @change="search">
          <el-option v-for="t in typeOptions" :key="t.value" :value="t.value" :label="t.label" />
        </el-select>
        <el-select v-model="filters.courtId" placeholder="Địa điểm" clearable filterable class="filter" @change="search">
          <el-option v-for="c in courts" :key="c.id" :value="c.id" :label="c.courtName" />
        </el-select>
        <el-checkbox v-model="filters.mine" border class="mine-filter" @change="search">Tôi đã đăng ký</el-checkbox>
        <el-button @click="resetFilters">Xóa lọc</el-button>
        <div class="spacer" />
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">Thêm buổi chơi</el-button>
      </div>

      <div class="table-wrap">
        <el-table
          v-loading="loading"
          :data="schedules"
          :default-sort="defaultSort"
          row-key="id"
          stripe
          empty-text="Không có buổi chơi nào trong khoảng thời gian này"
          @sort-change="handleSortChange"
          @row-dblclick="openDetail"
        >
          <el-table-column label="Ngày & giờ" prop="playDate" width="150" sortable="custom">
            <template #default="{ row }">
              <div class="date">{{ weekdayOf(row.playDate) }}, {{ row.playDate }}</div>
              <div class="text-secondary">{{ row.startTime }} – {{ row.endTime }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Buổi chơi" min-width="190">
            <template #default="{ row }">
              <el-link type="primary" underline="never" class="title-link" @click="openDetail(row)">
                {{ row.title }}
              </el-link>
              <div class="text-secondary">
                {{ SCHEDULE_TYPE_LABELS[row.scheduleType] }}
                <el-tag v-if="row.registeredByMe" type="success" size="small" round disable-transitions class="mine-tag">
                  Đã đăng ký
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Địa điểm" min-width="160">
            <template #default="{ row }">
              <div class="ellipsis">{{ row.courtName }}</div>
              <div class="text-secondary">{{ row.courtCount }} sân</div>
            </template>
          </el-table-column>
          <el-table-column label="Đăng ký" width="112" align="center">
            <template #default="{ row }">
              <div class="count" :class="{ full: row.registeredCount >= row.maxPlayers }">
                {{ row.registeredCount }}/{{ row.maxPlayers }}
              </div>
              <!-- Đã đăng ký nhưng quá hạn tự hủy: nhắc liên hệ admin -->
              <el-tooltip
                v-if="row.registeredByMe && !isFinished(row) && !canSelfCancel(row)"
                :content="`Đã quá hạn tự hủy (trước ${row.selfCancelDeadline}). Liên hệ quản trị viên nếu cần hủy.`"
                :show-after="200"
              >
                <span class="text-secondary hint">Quá hạn tự hủy</span>
              </el-tooltip>
              <span v-else-if="registerBlockedReason(row)" class="text-secondary hint">
                {{ registerBlockedReason(row) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="Trạng thái" width="136">
            <template #default="{ row }">
              <el-tag :type="SCHEDULE_STATUS_TAG_TYPES[row.status]" size="small" round disable-transitions>
                {{ SCHEDULE_STATUS_LABELS[row.status] }}
              </el-tag>
            </template>
          </el-table-column>
          <!-- Nút đăng ký nằm ở cột cố định bên phải để trên điện thoại luôn thấy, không phải cuộn ngang -->
          <el-table-column label="Thao tác" :width="isAdmin ? 200 : 160" fixed="right" align="center">
            <template #default="{ row }">
              <el-button
                v-if="canRegister(row)"
                type="primary"
                size="small"
                class="register-btn"
                :loading="busyId === row.id"
                @click="toggleRegistration(row)"
              >
                Đăng ký
              </el-button>
              <el-button
                v-else-if="canSelfCancel(row)"
                type="danger"
                size="small"
                plain
                class="register-btn"
                :loading="busyId === row.id"
                @click="toggleRegistration(row)"
              >
                Hủy đăng ký
              </el-button>
              <el-tooltip content="Xem chi tiết" :show-after="500">
                <el-button :icon="View" link aria-label="Xem chi tiết" @click="openDetail(row)" />
              </el-tooltip>
              <template v-if="isAdmin">
                <el-tooltip :content="isFinished(row) ? 'Buổi đã kết thúc, không sửa được' : 'Sửa'" :show-after="500">
                  <el-button
                    :icon="Edit"
                    link
                    type="primary"
                    aria-label="Sửa"
                    :disabled="isFinished(row)"
                    @click="openEdit(row)"
                  />
                </el-tooltip>
                <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, row)">
                  <el-button :icon="MoreFilled" link aria-label="Thao tác khác" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="row.status === 'OPEN'" command="CLOSED">Đóng đăng ký</el-dropdown-item>
                      <el-dropdown-item v-if="row.status === 'CLOSED' && !row.started" command="OPEN">
                        Mở lại đăng ký
                      </el-dropdown-item>
                      <el-dropdown-item v-if="canComplete(row)" command="COMPLETED">Đánh dấu hoàn thành</el-dropdown-item>
                      <el-dropdown-item v-if="!isFinished(row)" command="CANCELLED">Hủy buổi</el-dropdown-item>
                      <el-dropdown-item command="delete" :divided="!isFinished(row)" class="danger-item">
                        Xóa
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="fetchSchedules"
          @size-change="search"
        />
      </div>
    </el-card>

    <ScheduleFormDialog v-model="formVisible" :schedule="editingSchedule" @saved="fetchSchedules" />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.keyword {
  width: 240px;
  max-width: 100%;
}

.filter {
  width: 140px;
}

/* el-date-picker không nhận thuộc tính scoped ở phần tử gốc -> dùng :deep để ghi đè width 220px mặc định */
.toolbar :deep(.el-date-editor.filter) {
  width: 140px;
}

.spacer {
  flex: 1;
}

.table-wrap {
  overflow-x: auto;
}

.date,
.title-link {
  font-weight: 500;
}

.count {
  margin-bottom: 4px;
  font-variant-numeric: tabular-nums;
}

.mine-filter {
  margin-right: 0;
}

.hint {
  display: inline-block;
  margin-top: 2px;
}

.register-btn {
  margin-right: 4px;
}

.mine-tag {
  margin-left: 4px;
}

.count.full {
  color: var(--el-color-danger);
  font-weight: 600;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.danger-item {
  color: var(--el-color-danger);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}

@media (max-width: 768px) {
  .keyword {
    width: 100%;
  }

  /* 2 ô lọc mỗi hàng */
  .filter,
  .toolbar :deep(.el-date-editor.filter) {
    width: calc(50% - 4px);
  }

  .spacer {
    display: none;
  }
}
</style>
