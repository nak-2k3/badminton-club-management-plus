<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { expenseApi } from '@/api/expenses'
import ExpenseFormDialog from '@/components/expenses/ExpenseFormDialog.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import {
  EXPENSE_CATEGORY_LABELS,
  EXPENSE_CATEGORY_TAG_TYPES,
  SCHEDULE_STATUS_LABELS,
  toOptions
} from '@/utils/labels'
import { formatCurrency, parseDate } from '@/utils/format'

// Trang "Khoản chi" (ADMIN, TREASURER): danh sách theo bộ lọc + tổng chi theo từng loại (bấm thẻ để lọc)
const route = useRoute()
const router = useRouter()
const categoryOptions = toOptions(EXPENSE_CATEGORY_LABELS)
const { isSmall } = useBreakpoint(768)

const filters = reactive({
  keyword: route.query.keyword ?? '',
  from: route.query.from ?? '',
  to: route.query.to ?? '',
  category: route.query.category ?? ''
})
const pagination = reactive({
  page: Number(route.query.page) || 1, // giao diện đếm từ 1, API đếm từ 0
  size: Number(route.query.size) || 10,
  total: 0
})
const sortState = reactive({
  sort: ['amount', 'createdAt'].includes(route.query.sort) ? route.query.sort : 'expenseDate',
  direction: route.query.direction === 'asc' ? 'asc' : 'desc'
})

const rows = ref([])
const totals = ref({ totalCount: 0, totalAmount: 0, byCategory: [] })
const loading = ref(false)
const busyId = ref(null)
const formVisible = ref(false)
const editingExpense = ref(null)
let latestRequestId = 0

async function fetchExpenses() {
  const requestId = ++latestRequestId
  loading.value = true
  const params = {
    ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== '' && v !== null)),
    page: pagination.page - 1,
    size: pagination.size,
    sort: sortState.sort,
    direction: sortState.direction
  }
  router.replace({ query: { ...params, page: pagination.page } })
  try {
    const res = await expenseApi.search(params)
    if (requestId !== latestRequestId) return
    const page = res.page
    if (page.content.length === 0 && page.totalElements > 0 && pagination.page > 1) {
      pagination.page = page.totalPages
      return fetchExpenses()
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
  fetchExpenses()
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
  Object.assign(filters, { keyword: '', from: '', to: '', category: '' })
  search()
}

// Bấm thẻ tổng: lọc nhanh theo loại chi (bấm lại để bỏ lọc)
function filterCategory(category) {
  filters.category = filters.category === category ? '' : category
  search()
}

const disableBeforeFrom = (date) => !!filters.from && date < parseDate(filters.from)
const disableAfterTo = (date) => !!filters.to && date > parseDate(filters.to)

function handleSortChange({ prop, order }) {
  sortState.sort = order ? prop : 'expenseDate'
  sortState.direction = order === 'ascending' ? 'asc' : 'desc'
  search()
}
const defaultSort = { prop: sortState.sort, order: sortState.direction === 'asc' ? 'ascending' : 'descending' }

function openCreate() {
  editingExpense.value = null
  formVisible.value = true
}

function openEdit(row) {
  editingExpense.value = row
  formVisible.value = true
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `Xóa khoản chi "${row.description}" (${formatCurrency(row.amount)}, ngày ${row.expenseDate})? Không thể hoàn tác.`,
      'Xóa khoản chi',
      { confirmButtonText: 'Xóa', cancelButtonText: 'Không', type: 'warning', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  busyId.value = row.id
  try {
    await expenseApi.remove(row.id)
    ElMessage.success('Đã xóa khoản chi')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    busyId.value = null
    fetchExpenses()
  }
}

function openSchedule(row) {
  router.push({ name: 'schedule-detail', params: { id: row.scheduleId } })
}

const categoryTotal = (category) => totals.value.byCategory.find((c) => c.category === category) ?? { count: 0, amount: 0 }
const rowIndex = (index) => (pagination.page - 1) * pagination.size + index + 1

onMounted(fetchExpenses)
</script>

<template>
  <div class="expenses">
    <div class="page-actions">
      <el-button type="primary" :icon="Plus" @click="openCreate">Thêm khoản chi</el-button>
    </div>

    <div class="totals">
      <button type="button" class="total-card total-all" :class="{ active: !filters.category }" @click="filterCategory('')">
        <span class="total-label">Tổng chi · {{ totals.totalCount }} khoản</span>
        <span class="total-value">{{ formatCurrency(totals.totalAmount) }}</span>
      </button>
      <button
        v-for="c in categoryOptions"
        :key="c.value"
        type="button"
        class="total-card"
        :class="[`tone-${EXPENSE_CATEGORY_TAG_TYPES[c.value]}`, { active: filters.category === c.value }]"
        @click="filterCategory(c.value)"
      >
        <span class="total-label">{{ c.label }} · {{ categoryTotal(c.value).count }}</span>
        <span class="total-value small">{{ formatCurrency(categoryTotal(c.value).amount) }}</span>
      </button>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="Tìm nội dung, ghi chú, tên buổi"
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
        <el-select v-model="filters.category" placeholder="Loại chi" clearable class="filter" @change="search">
          <el-option v-for="c in categoryOptions" :key="c.value" :value="c.value" :label="c.label" />
        </el-select>
        <el-button @click="resetFilters">Xóa lọc</el-button>
      </div>

      <div v-if="isSmall" v-loading="loading" class="exp-list">
        <el-empty v-if="!loading && !rows.length" :image-size="64" description="Chưa có khoản chi nào" />
        <div v-for="row in rows" :key="row.id" class="exp-item">
          <div class="exp-head">
            <div class="exp-info">
              <div class="name">{{ row.description }}</div>
              <div class="text-secondary">
                {{ row.expenseDate }} ·
                <el-tag :type="EXPENSE_CATEGORY_TAG_TYPES[row.category]" size="small" round effect="plain" disable-transitions>
                  {{ EXPENSE_CATEGORY_LABELS[row.category] }}
                </el-tag>
              </div>
            </div>
            <span class="money strong">{{ formatCurrency(row.amount) }}</span>
          </div>
          <el-link v-if="row.scheduleId" type="primary" underline="never" class="schedule-link" @click="openSchedule(row)">
            {{ row.scheduleTitle }} ({{ row.playDate }})
            <template v-if="row.scheduleStatus === 'CANCELLED'"> · {{ SCHEDULE_STATUS_LABELS.CANCELLED }}</template>
          </el-link>
          <div v-if="row.note" class="text-secondary">{{ row.note }}</div>
          <div class="exp-actions">
            <span class="text-secondary">Ghi bởi {{ row.createdByName }}</span>
            <div class="exp-buttons">
              <el-button :icon="Edit" circle plain type="primary" :aria-label="`Sửa khoản chi ${row.description}`" @click="openEdit(row)" />
              <el-button
                :icon="Delete"
                circle
                plain
                type="danger"
                :aria-label="`Xóa khoản chi ${row.description}`"
                :loading="busyId === row.id"
                @click="remove(row)"
              />
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
          empty-text="Chưa có khoản chi nào"
          @sort-change="handleSortChange"
        >
          <el-table-column label="STT" width="56" align="center">
            <template #default="{ $index }">{{ rowIndex($index) }}</template>
          </el-table-column>
          <el-table-column label="Ngày chi" prop="expenseDate" width="120" sortable="custom" />
          <el-table-column label="Nội dung" min-width="230">
            <template #default="{ row }">
              <div class="name">{{ row.description }}</div>
              <el-link v-if="row.scheduleId" type="primary" underline="never" @click="openSchedule(row)">
                {{ row.scheduleTitle }} ({{ row.playDate }})
                <template v-if="row.scheduleStatus === 'CANCELLED'"> · {{ SCHEDULE_STATUS_LABELS.CANCELLED }}</template>
              </el-link>
              <div v-if="row.note" class="text-secondary">{{ row.note }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Loại" width="136">
            <template #default="{ row }">
              <el-tag :type="EXPENSE_CATEGORY_TAG_TYPES[row.category]" size="small" round effect="plain" disable-transitions>
                {{ EXPENSE_CATEGORY_LABELS[row.category] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Số tiền" prop="amount" width="130" align="right" sortable="custom">
            <template #default="{ row }">
              <span class="money">{{ formatCurrency(row.amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Người ghi" width="150">
            <template #default="{ row }">
              <div>{{ row.createdByName }}</div>
              <div class="text-secondary small">{{ row.createdAt }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Thao tác" width="100" fixed="right" align="center">
            <template #default="{ row }">
              <el-tooltip content="Sửa" :show-after="500">
                <el-button :icon="Edit" link type="primary" aria-label="Sửa khoản chi" @click="openEdit(row)" />
              </el-tooltip>
              <el-tooltip content="Xóa" :show-after="500">
                <el-button
                  :icon="Delete"
                  link
                  type="danger"
                  aria-label="Xóa khoản chi"
                  :loading="busyId === row.id"
                  @click="remove(row)"
                />
              </el-tooltip>
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
          :layout="isSmall ? 'total, prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
          background
          @current-change="fetchExpenses"
          @size-change="search"
        />
      </div>
    </el-card>

    <ExpenseFormDialog v-model="formVisible" :expense="editingExpense" @saved="fetchExpenses" />
  </div>
</template>

<style scoped>
.expenses {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-actions {
  display: flex;
  justify-content: flex-end;
}

.totals {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.total-card {
  --tone: var(--el-color-primary);
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-left: 4px solid var(--tone);
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

.total-all {
  --tone: var(--el-color-danger);
}

.tone-primary {
  --tone: var(--el-color-primary);
}

.tone-success {
  --tone: var(--el-color-success);
}

.tone-warning {
  --tone: var(--el-color-warning);
}

.tone-info {
  --tone: var(--el-color-info);
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

.total-value.small {
  font-size: 18px;
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

.table-wrap {
  overflow-x: auto;
}

.name {
  font-weight: 500;
  /* Nội dung 1 từ rất dài (không có khoảng trắng) không đè lên số tiền ở dạng thẻ */
  overflow-wrap: anywhere;
}

.small {
  font-size: 12px;
}

.money {
  font-variant-numeric: tabular-nums;
}

.money.strong {
  font-weight: 600;
  white-space: nowrap;
}

.exp-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.exp-item:last-child {
  border-bottom: none;
}

.exp-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.exp-info {
  min-width: 0;
}

.schedule-link {
  align-self: flex-start;
}

.exp-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}

.exp-buttons {
  display: flex;
  gap: 8px;
}

.exp-buttons .el-button {
  margin-left: 0;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}

@media (max-width: 768px) {
  .page-actions .el-button {
    flex: 1;
  }

  /* 2 thẻ mỗi hàng, thẻ tổng chiếm cả hàng */
  .totals {
    grid-template-columns: repeat(2, 1fr);
  }

  .total-all {
    grid-column: 1 / -1;
  }

  .keyword {
    width: 100%;
  }

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
