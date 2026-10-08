<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, ArrowRight } from '@element-plus/icons-vue'
import { paymentApi } from '@/api/payments'
import PaymentTotals from './PaymentTotals.vue'
import { PAYMENT_TYPE_LABELS, PAYMENT_TYPE_TAG_TYPES, toOptions } from '@/utils/labels'
import { formatCurrency } from '@/utils/format'

// Tab "Đợt thu": mỗi lần tạo khoản thu là 1 đợt (phí tháng: mỗi tháng 1 đợt). Bấm đợt để xem chi tiết, thu / xóa hàng loạt.
const route = useRoute()
const router = useRouter()
const typeOptions = toOptions(PAYMENT_TYPE_LABELS)

const filters = reactive({
  keyword: route.query.keyword ?? '',
  period: route.query.month && route.query.year ? `${route.query.month}/${route.query.year}` : '',
  paymentType: route.query.paymentType ?? ''
})
const pagination = reactive({
  page: Number(route.query.page) || 1,
  size: Number(route.query.size) || 10,
  total: 0
})

const batches = ref([])
const totals = ref({ paidCount: 0, paidAmount: 0, unpaidCount: 0, unpaidAmount: 0 })
const loading = ref(false)
let latestRequestId = 0

async function fetchBatches() {
  const requestId = ++latestRequestId
  loading.value = true
  const { period, ...rest } = filters
  const params = Object.fromEntries(Object.entries(rest).filter(([, v]) => v !== '' && v !== null))
  if (period) {
    const [month, year] = period.split('/')
    Object.assign(params, { month, year })
  }
  Object.assign(params, { page: pagination.page - 1, size: pagination.size })
  router.replace({ query: { ...params, page: pagination.page } })
  try {
    const res = await paymentApi.searchBatches(params)
    if (requestId !== latestRequestId) return
    const page = res.page
    if (page.content.length === 0 && page.totalElements > 0 && pagination.page > 1) {
      pagination.page = page.totalPages
      return fetchBatches()
    }
    batches.value = page.content
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
  fetchBatches()
}

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
  Object.assign(filters, { keyword: '', period: '', paymentType: '' })
  search()
}

function openBatch(batch) {
  router.push({ name: 'payment-batch', params: { id: batch.id } })
}

const percent = (b) => (b.memberCount ? Math.round((b.paidCount / b.memberCount) * 100) : 0)
const subtitle = (b) =>
  b.paymentType === 'MONTHLY'
    ? `Tháng ${b.month}/${b.year}`
    : `${formatCurrency(b.amount)}/người · Tạo ${b.createdAt.slice(0, 10)}`

defineExpose({ reload: fetchBatches })
onMounted(fetchBatches)
</script>

<template>
  <div class="batch-list">
    <PaymentTotals :totals="totals" />

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="Tìm theo nội dung đợt thu"
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
        <el-button @click="resetFilters">Xóa lọc</el-button>
      </div>

      <div v-loading="loading" class="batches">
        <el-empty
          v-if="!loading && !batches.length"
          :image-size="72"
          description="Chưa có đợt thu nào. Bấm &quot;Tạo phí tháng&quot; hoặc &quot;Thêm khoản thu&quot; để bắt đầu"
        />
        <button v-for="b in batches" :key="b.id" type="button" class="batch-item" @click="openBatch(b)">
          <span class="batch-main">
            <span class="batch-title">
              <el-tag :type="PAYMENT_TYPE_TAG_TYPES[b.paymentType]" size="small" round effect="plain" disable-transitions>
                {{ PAYMENT_TYPE_LABELS[b.paymentType] }}
              </el-tag>
              <span class="title-text">{{ b.description }}</span>
            </span>
            <span class="text-secondary">{{ subtitle(b) }}<template v-if="b.note"> · {{ b.note }}</template></span>
          </span>
          <span class="batch-progress">
            <span class="progress-text">
              <span>Đã thu <strong>{{ b.paidCount }}/{{ b.memberCount }}</strong> người</span>
              <span class="money">{{ formatCurrency(b.paidAmount) }} / {{ formatCurrency(b.totalAmount) }}</span>
            </span>
            <el-progress
              :percentage="percent(b)"
              :status="b.memberCount && b.paidCount === b.memberCount ? 'success' : ''"
              :show-text="false"
              :stroke-width="8"
            />
          </span>
          <el-icon class="batch-arrow"><ArrowRight /></el-icon>
        </button>
      </div>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="fetchBatches"
          @size-change="search"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.batch-list {
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

.toolbar :deep(.el-date-editor.filter) {
  width: 140px;
}

.batches {
  min-height: 80px;
}

.batch-item {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 14px 12px;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s;
}

.batch-item:last-child {
  border-bottom: none;
}

.batch-item:hover {
  background: var(--el-fill-color-light);
}

.batch-item:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}

.batch-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.batch-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.title-text {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 260px;
  flex-shrink: 0;
}

.progress-text {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4px 8px;
  font-size: 13px;
}

.money {
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.batch-arrow {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
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

  .filter,
  .toolbar :deep(.el-date-editor.filter) {
    width: calc(50% - 4px);
  }

  /* Điện thoại: tiến độ xuống dưới tên đợt */
  .batch-item {
    flex-wrap: wrap;
    gap: 10px;
    padding: 12px 4px;
  }

  .batch-main {
    flex-basis: calc(100% - 32px);
  }

  .batch-progress {
    width: 100%;
    order: 3;
  }
}

@media (prefers-reduced-motion: reduce) {
  .batch-item {
    transition: none;
  }
}
</style>
