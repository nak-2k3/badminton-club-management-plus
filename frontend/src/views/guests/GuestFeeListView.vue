<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Wallet, CircleCheck, Edit } from '@element-plus/icons-vue'
import { guestApi } from '@/api/guests'
import GuestFormDialog from '@/components/schedules/GuestFormDialog.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { PAYMENT_STATUS_LABELS, PAYMENT_STATUS_TAG_TYPES, SCHEDULE_STATUS_LABELS, toOptions } from '@/utils/labels'
import { formatCurrency, parseDate, weekdayOf } from '@/utils/format'

// Trang "Phí khách" (ADMIN, TREASURER): khách của mọi buổi chơi, thu tiền nhanh, tổng đã thu / chưa thu
const route = useRoute()
const router = useRouter()
const statusOptions = toOptions(PAYMENT_STATUS_LABELS)
// Điện thoại: dạng thẻ để thấy tên khách + số tiền ngay cạnh nút "Thu tiền" (không phải cuộn ngang bảng)
const { isSmall } = useBreakpoint(768)

// Bộ lọc đồng bộ lên URL để quay lại từ trang buổi chơi vẫn giữ nguyên
const filters = reactive({
  keyword: route.query.keyword ?? '',
  from: route.query.from ?? '',
  to: route.query.to ?? '',
  paymentStatus: route.query.paymentStatus ?? '',
  includeCancelled: route.query.includeCancelled === 'true'
})
const pagination = reactive({
  page: Number(route.query.page) || 1, // giao diện đếm từ 1, API đếm từ 0
  size: Number(route.query.size) || 10,
  total: 0
})
const direction = ref(route.query.direction === 'asc' ? 'asc' : 'desc')

const rows = ref([])
const totals = ref({ paidCount: 0, paidAmount: 0, unpaidCount: 0, unpaidAmount: 0 })
const loading = ref(false)
const busyId = ref(null)
// Sửa thông tin khách (vd gõ nhầm tên/SĐT) ngay tại trang này
const editVisible = ref(false)
const editingGuest = ref(null)
const editingSchedule = ref({})

function openEdit(row) {
  editingGuest.value = row
  editingSchedule.value = { id: row.scheduleId, playDate: row.playDate }
  editVisible.value = true
}
let latestRequestId = 0

async function fetchFees() {
  const requestId = ++latestRequestId
  loading.value = true
  const params = {
    ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== '' && v !== null && v !== false)),
    page: pagination.page - 1,
    size: pagination.size,
    sort: 'playDate',
    direction: direction.value
  }
  router.replace({ query: { ...params, page: pagination.page } })
  try {
    const res = await guestApi.searchFees(params)
    if (requestId !== latestRequestId) return
    const page = res.page
    if (page.content.length === 0 && page.totalElements > 0 && pagination.page > 1) {
      pagination.page = page.totalPages
      return fetchFees()
    }
    rows.value = page.content
    pagination.total = page.totalElements
    totals.value = res
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
  fetchFees()
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
  Object.assign(filters, { keyword: '', from: '', to: '', paymentStatus: '', includeCancelled: false })
  search()
}

// Bấm thẻ tổng: lọc nhanh theo trạng thái thu
function filterStatus(status) {
  filters.paymentStatus = filters.paymentStatus === status ? '' : status
  search()
}

const disableBeforeFrom = (date) => !!filters.from && date < parseDate(filters.from)
const disableAfterTo = (date) => !!filters.to && date > parseDate(filters.to)

function handleSortChange({ order }) {
  direction.value = order === 'ascending' ? 'asc' : 'desc'
  search()
}
const defaultSort = { prop: 'playDate', order: direction.value === 'asc' ? 'ascending' : 'descending' }

async function setPayment(row, status) {
  if (status === 'UNPAID') {
    try {
      await ElMessageBox.confirm(
        `Chuyển khách "${row.fullName}" (buổi ${row.playDate}) về "Chưa thu"? Thông tin người thu và thời điểm thu sẽ bị xóa.`,
        'Hoàn tác thu tiền',
        { confirmButtonText: 'Hoàn tác', cancelButtonText: 'Không', type: 'warning' }
      )
    } catch {
      return
    }
  }
  busyId.value = row.id
  try {
    await guestApi.setPayment(row.scheduleId, row.id, status)
    ElMessage.success(status === 'PAID'
      ? `Đã thu ${formatCurrency(row.fee)} của "${row.fullName}"`
      : 'Đã chuyển về chưa thu')
    fetchFees()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    busyId.value = null
  }
}

function openSchedule(row) {
  router.push({ name: 'schedule-detail', params: { id: row.scheduleId } })
}

const rowIndex = (index) => (pagination.page - 1) * pagination.size + index + 1

onMounted(fetchFees)
</script>

<template>
  <div class="guest-fees">
    <div class="totals">
      <button
        type="button"
        class="total-card tone-warning"
        :class="{ active: filters.paymentStatus === 'UNPAID' }"
        @click="filterStatus('UNPAID')"
      >
        <span class="total-icon"><el-icon :size="22"><Wallet /></el-icon></span>
        <span class="total-body">
          <span class="total-label">Chưa thu · {{ totals.unpaidCount }} khách</span>
          <span class="total-value">{{ formatCurrency(totals.unpaidAmount) }}</span>
        </span>
      </button>
      <button
        type="button"
        class="total-card tone-success"
        :class="{ active: filters.paymentStatus === 'PAID' }"
        @click="filterStatus('PAID')"
      >
        <span class="total-icon"><el-icon :size="22"><CircleCheck /></el-icon></span>
        <span class="total-body">
          <span class="total-label">Đã thu · {{ totals.paidCount }} khách</span>
          <span class="total-value">{{ formatCurrency(totals.paidAmount) }}</span>
        </span>
      </button>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="Tìm tên khách, SĐT, người dẫn, buổi"
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
          class="filter"
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
          class="filter"
          @change="search"
        />
        <el-select v-model="filters.paymentStatus" placeholder="Trạng thái thu" clearable class="filter" @change="search">
          <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-checkbox v-model="filters.includeCancelled" border class="cancelled-filter" @change="search">
          Gồm buổi đã hủy
        </el-checkbox>
        <el-button @click="resetFilters">Xóa lọc</el-button>
      </div>

      <div v-if="isSmall" v-loading="loading" class="fee-list">
        <el-empty v-if="!loading && !rows.length" :image-size="64" description="Không có khách nào" />
        <div v-for="row in rows" :key="row.id" class="fee-item">
          <div class="fee-head">
            <div class="fee-info">
              <div class="name">{{ row.fullName }}</div>
              <div class="text-secondary">
                {{ row.phone || 'Không có SĐT' }} · {{ row.invitedByName ? `Dẫn bởi ${row.invitedByName}` : 'Tự liên hệ' }}
              </div>
            </div>
            <span class="money strong">{{ formatCurrency(row.fee) }}</span>
          </div>
          <el-link type="primary" underline="never" class="schedule-link" @click="openSchedule(row)">
            {{ row.scheduleTitle }}
          </el-link>
          <div class="text-secondary">
            {{ weekdayOf(row.playDate) }}, {{ row.playDate }} · {{ row.startTime }}
            <template v-if="row.scheduleStatus === 'CANCELLED'">
              · <span class="cancelled">{{ SCHEDULE_STATUS_LABELS.CANCELLED }}</span>
            </template>
          </div>
          <div class="fee-actions">
            <div>
              <el-tag :type="PAYMENT_STATUS_TAG_TYPES[row.paymentStatus]" size="small" round disable-transitions>
                {{ PAYMENT_STATUS_LABELS[row.paymentStatus] }}
              </el-tag>
              <span v-if="row.paidAt" class="text-secondary"> {{ row.collectedByName }} · {{ row.paidAt }}</span>
            </div>
            <div class="fee-buttons">
              <el-button
                :icon="Edit"
                circle
                plain
                type="primary"
                :aria-label="`Sửa thông tin khách ${row.fullName}`"
                @click="openEdit(row)"
              />
              <el-button
                v-if="row.paymentStatus === 'UNPAID'"
                type="success"
                :loading="busyId === row.id"
                :disabled="row.scheduleStatus === 'CANCELLED'"
                @click="setPayment(row, 'PAID')"
              >
                Thu tiền
              </el-button>
              <el-button v-else link type="info" :disabled="busyId === row.id" @click="setPayment(row, 'UNPAID')">
                Hoàn tác
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="table-wrap">
        <el-table
          v-loading="loading"
          :data="rows"
          :default-sort="defaultSort"
          row-key="id"
          stripe
          empty-text="Không có khách nào"
          @sort-change="handleSortChange"
        >
          <el-table-column label="STT" width="56" align="center">
            <template #default="{ $index }">{{ rowIndex($index) }}</template>
          </el-table-column>
          <el-table-column label="Buổi chơi" prop="playDate" min-width="200" sortable="custom">
            <template #default="{ row }">
              <el-link type="primary" underline="never" class="schedule-link" @click="openSchedule(row)">
                {{ row.scheduleTitle }}
              </el-link>
              <div class="text-secondary">
                {{ weekdayOf(row.playDate) }}, {{ row.playDate }} · {{ row.startTime }}
                <template v-if="row.scheduleStatus === 'CANCELLED'">
                  · <span class="cancelled">{{ SCHEDULE_STATUS_LABELS.CANCELLED }}</span>
                </template>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Khách" min-width="150">
            <template #default="{ row }">
              <div class="name">{{ row.fullName }}</div>
              <div class="text-secondary">{{ row.phone || 'Không có SĐT' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Người dẫn" width="160">
            <template #default="{ row }">
              <span :class="{ 'text-secondary': !row.invitedByName }">{{ row.invitedByName || 'Tự liên hệ' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Phí" width="104" align="right">
            <template #default="{ row }">
              <span class="money">{{ formatCurrency(row.fee) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Trạng thái" width="150">
            <template #default="{ row }">
              <el-tag :type="PAYMENT_STATUS_TAG_TYPES[row.paymentStatus]" size="small" round disable-transitions>
                {{ PAYMENT_STATUS_LABELS[row.paymentStatus] }}
              </el-tag>
              <div v-if="row.paidAt" class="text-secondary">{{ row.collectedByName }} · {{ row.paidAt }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Thao tác" width="148" fixed="right" align="center">
            <template #default="{ row }">
              <el-tooltip content="Sửa thông tin khách" :show-after="500">
                <el-button :icon="Edit" link type="primary" aria-label="Sửa thông tin khách" @click="openEdit(row)" />
              </el-tooltip>
              <el-button
                v-if="row.paymentStatus === 'UNPAID'"
                type="success"
                size="small"
                :loading="busyId === row.id"
                :disabled="row.scheduleStatus === 'CANCELLED'"
                @click="setPayment(row, 'PAID')"
              >
                Thu tiền
              </el-button>
              <el-button v-else link type="info" size="small" :disabled="busyId === row.id" @click="setPayment(row, 'UNPAID')">
                Hoàn tác
              </el-button>
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
          @current-change="fetchFees"
          @size-change="search"
        />
      </div>
    </el-card>

    <GuestFormDialog
      v-model="editVisible"
      :schedule="editingSchedule"
      :guest="editingGuest"
      manager
      @saved="fetchFees"
    />
  </div>
</template>

<style scoped>
.guest-fees {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.totals {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.total-card {
  --tone: var(--el-color-primary);
  --tone-bg: var(--el-color-primary-light-9);
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.total-card:hover,
.total-card.active {
  border-color: var(--tone);
  box-shadow: var(--el-box-shadow-light);
}

.total-card:focus-visible {
  outline: 2px solid var(--tone);
  outline-offset: 2px;
}

.tone-warning {
  --tone: var(--el-color-warning);
  --tone-bg: var(--el-color-warning-light-9);
}

.tone-success {
  --tone: var(--el-color-success);
  --tone-bg: var(--el-color-success-light-9);
}

.total-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--tone-bg);
  color: var(--tone);
}

.total-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.total-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.total-value {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.keyword {
  width: 260px;
  max-width: 100%;
}

.filter {
  width: 140px;
}

/* el-date-picker không nhận thuộc tính scoped ở phần tử gốc -> dùng :deep để ghi đè width 220px mặc định */
.toolbar :deep(.el-date-editor.filter) {
  width: 140px;
}

.cancelled-filter {
  margin-right: 0;
}

.table-wrap {
  overflow-x: auto;
}

.schedule-link,
.name {
  font-weight: 500;
}

.cancelled {
  color: var(--el-color-danger);
}

.money {
  font-variant-numeric: tabular-nums;
}

.money.strong {
  font-weight: 600;
  white-space: nowrap;
}

.fee-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.fee-item:last-child {
  border-bottom: none;
}

.fee-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.fee-info {
  min-width: 0;
}

.schedule-link {
  align-self: flex-start;
}

.fee-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fee-buttons .el-button {
  margin-left: 0;
}

.fee-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
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
}

@media (prefers-reduced-motion: reduce) {
  .total-card {
    transition: none;
  }
}
</style>
