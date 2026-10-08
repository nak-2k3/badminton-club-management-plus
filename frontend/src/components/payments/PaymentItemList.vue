<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { paymentApi } from '@/api/payments'
import PaymentRows from './PaymentRows.vue'
import PaymentBulkBar from './PaymentBulkBar.vue'
import PaymentTotals from './PaymentTotals.vue'
import PaymentFormDialog from './PaymentFormDialog.vue'
import { usePaymentActions } from '@/composables/usePaymentActions'
import { PAYMENT_STATUS_LABELS, PAYMENT_TYPE_LABELS, toOptions } from '@/utils/labels'

// Tab "Tất cả khoản": mọi khoản thu của mọi đợt — tra 1 người còn nợ gì, chọn nhiều để thu / xóa
const route = useRoute()
const router = useRouter()
const statusOptions = toOptions(PAYMENT_STATUS_LABELS)
const typeOptions = toOptions(PAYMENT_TYPE_LABELS)

// Bộ lọc đồng bộ lên URL; tháng dạng "M/YYYY" (gửi API thành month + year)
const filters = reactive({
  keyword: route.query.keyword ?? '',
  period: route.query.month && route.query.year ? `${route.query.month}/${route.query.year}` : '',
  paymentType: route.query.paymentType ?? '',
  status: route.query.status ?? ''
})
const pagination = reactive({
  page: Number(route.query.page) || 1, // giao diện đếm từ 1, API đếm từ 0
  size: Number(route.query.size) || 10,
  total: 0
})
const direction = ref(route.query.direction === 'asc' ? 'asc' : 'desc')

const rows = ref([])
const selected = ref([])
const totals = ref({ paidCount: 0, paidAmount: 0, unpaidCount: 0, unpaidAmount: 0 })
const loading = ref(false)
const editVisible = ref(false)
const editingPayment = ref(null)
let latestRequestId = 0

const selectedRows = computed(() => rows.value.filter((r) => selected.value.includes(r.id)))
const { busyId, bulkBusy, collect, undo, remove, bulkCollect, bulkRemove } = usePaymentActions(fetchPayments)

function buildParams() {
  const { period, ...rest } = filters
  const params = Object.fromEntries(Object.entries(rest).filter(([, v]) => v !== '' && v !== null))
  if (period) {
    const [month, year] = period.split('/')
    Object.assign(params, { month, year })
  }
  return { ...params, page: pagination.page - 1, size: pagination.size, sort: 'period', direction: direction.value }
}

async function fetchPayments() {
  const requestId = ++latestRequestId
  loading.value = true
  const params = buildParams()
  router.replace({ query: { tab: 'items', ...params, page: pagination.page } })
  try {
    const res = await paymentApi.search(params)
    if (requestId !== latestRequestId) return
    const page = res.page
    if (page.content.length === 0 && page.totalElements > 0 && pagination.page > 1) {
      pagination.page = page.totalPages
      return fetchPayments()
    }
    rows.value = page.content
    pagination.total = page.totalElements
    totals.value = res
    // Bỏ chọn các khoản không còn trên trang (vừa xóa / sang trang khác)
    const ids = new Set(page.content.map((r) => r.id))
    selected.value = selected.value.filter((id) => ids.has(id))
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
  fetchPayments()
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
  Object.assign(filters, { keyword: '', period: '', paymentType: '', status: '' })
  search()
}

// Bấm thẻ tổng: lọc nhanh theo trạng thái thu
function filterStatus(status) {
  filters.status = filters.status === status ? '' : status
  search()
}

function handleSortChange({ order }) {
  direction.value = order === 'ascending' ? 'asc' : 'desc'
  search()
}
const defaultSort = { prop: 'period', order: direction.value === 'asc' ? 'ascending' : 'descending' }

function openEdit(row) {
  editingPayment.value = row
  editVisible.value = true
}

function openBatch(row) {
  router.push({ name: 'payment-batch', params: { id: row.batchId } })
}

defineExpose({ reload: fetchPayments })
onMounted(fetchPayments)
</script>

<template>
  <div class="item-list">
    <PaymentTotals :totals="totals" :active="filters.status" clickable @filter="filterStatus" />

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="Tìm thành viên, email, SĐT, nội dung"
          clearable
          class="keyword"
          @keyup.enter="search"
        />
        <el-date-picker
          v-model="filters.period"
          type="month"
          format="MM/YYYY"
          value-format="M/YYYY"
          placeholder="Tháng"
          title="Phí tháng: theo kỳ thu. Thu thêm: theo tháng tạo"
          :editable="false"
          class="filter"
          @change="search"
        />
        <el-select v-model="filters.paymentType" placeholder="Loại" clearable class="filter" @change="search">
          <el-option v-for="t in typeOptions" :key="t.value" :value="t.value" :label="t.label" />
        </el-select>
        <el-select v-model="filters.status" placeholder="Trạng thái thu" clearable class="filter" @change="search">
          <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-button @click="resetFilters">Xóa lọc</el-button>
      </div>

      <PaymentRows
        v-model:selected="selected"
        :rows="rows"
        :loading="loading"
        :busy-id="busyId"
        :start-index="(pagination.page - 1) * pagination.size + 1"
        sortable
        :default-sort="defaultSort"
        @sort-change="handleSortChange"
        @edit="openEdit"
        @collect="collect"
        @undo="undo"
        @remove="remove"
        @open-batch="openBatch"
      />

      <PaymentBulkBar
        :rows="selectedRows"
        :busy="bulkBusy"
        @collect="(m) => bulkCollect(selectedRows, m)"
        @remove="bulkRemove(selectedRows)"
        @clear="selected = []"
      />

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="fetchPayments"
          @size-change="search"
        />
      </div>
    </el-card>

    <PaymentFormDialog v-model="editVisible" :payment="editingPayment" @saved="fetchPayments" />
  </div>
</template>

<style scoped>
.item-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
</style>
