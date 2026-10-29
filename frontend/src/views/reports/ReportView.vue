<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, ArrowRight } from '@element-plus/icons-vue'
import { reportApi, downloadBlob } from '@/api/reports'
import MonthlyChart from '@/components/reports/MonthlyChart.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { EXPENSE_CATEGORY_LABELS } from '@/utils/labels'
import { formatCurrency } from '@/utils/format'

// Trang "Báo cáo" (ADMIN, TREASURER): thu chi theo kỳ, tồn quỹ, biểu đồ theo tháng, công nợ, xuất Excel.
// Thu tính theo ngày thu tiền, chi theo ngày chi; công nợ là số chưa thu tại thời điểm xem.
const route = useRoute()
const router = useRouter()
const { isSmall } = useBreakpoint(768)

const INCOME_LABELS = { MONTHLY: 'Phí tháng', EXTRA: 'Thu thêm', GUEST: 'Phí khách' }
const PRESETS = [
  { value: 'thisMonth', label: 'Tháng này' },
  { value: 'lastMonth', label: 'Tháng trước' },
  { value: 'thisQuarter', label: 'Quý này' },
  { value: 'thisYear', label: 'Năm nay' }
]

const pad = (n) => String(n).padStart(2, '0')
const fmt = (d) => `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`

function presetRange(preset) {
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth()
  switch (preset) {
    case 'lastMonth':
      return [fmt(new Date(y, m - 1, 1)), fmt(new Date(y, m, 0))]
    case 'thisQuarter': {
      const q = Math.floor(m / 3) * 3
      return [fmt(new Date(y, q, 1)), fmt(new Date(y, q + 3, 0))]
    }
    case 'thisYear':
      return [fmt(new Date(y, 0, 1)), fmt(new Date(y, 11, 31))]
    default:
      return [fmt(new Date(y, m, 1)), fmt(new Date(y, m + 1, 0))]
  }
}

// Kỳ báo cáo đồng bộ lên URL (quay lại từ trang khác vẫn giữ kỳ)
const preset = ref(PRESETS.some((p) => p.value === route.query.preset) ? route.query.preset : route.query.from ? '' : 'thisMonth')
const range = ref(route.query.from && route.query.to ? [route.query.from, route.query.to] : presetRange(preset.value || 'thisMonth'))

const report = ref(null)
const loading = ref(false)
const error = ref(null)
const exporting = ref(false)
const view = ref('chart')
const showAllDebtors = ref(false)
let latestRequestId = 0

async function fetchReport() {
  const requestId = ++latestRequestId
  const [from, to] = range.value
  router.replace({ query: { ...(preset.value ? { preset: preset.value } : { from, to }) } })
  loading.value = true
  try {
    const res = await reportApi.summary({ from, to })
    if (requestId !== latestRequestId) return
    report.value = res
    error.value = null
  } catch (err) {
    if (requestId !== latestRequestId) return
    error.value = err
    ElMessage.error(err.message)
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

function choosePreset(value) {
  preset.value = value
  range.value = presetRange(value)
  fetchReport()
}

function chooseRange(value) {
  if (!value) {
    choosePreset('thisMonth')
    return
  }
  preset.value = ''
  range.value = value
  fetchReport()
}

async function exportExcel() {
  const [from, to] = range.value
  exporting.value = true
  try {
    const blob = await reportApi.exportExcel({ from, to })
    downloadBlob(blob, `bao-cao-thu-chi_${from.replaceAll('/', '-')}_${to.replaceAll('/', '-')}.xlsx`)
    ElMessage.success('Đã xuất file Excel báo cáo')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    exporting.value = false
  }
}

const income = computed(() => report.value?.income ?? [])
const expense = computed(() => report.value?.expense ?? [])
const share = (amount, total) => (Number(total) > 0 ? Math.round((Number(amount) / Number(total)) * 100) : 0)
const net = computed(() => (report.value ? Number(report.value.totalIncome) - Number(report.value.totalExpense) : 0))
const debtors = computed(() => report.value?.outstanding.debtors ?? [])
const DEBTOR_PREVIEW = 10
const visibleDebtors = computed(() => (showAllDebtors.value ? debtors.value : debtors.value.slice(0, DEBTOR_PREVIEW)))

// Bấm dòng cơ cấu: mở trang chi tiết đã lọc sẵn đúng kỳ
function openIncome(source) {
  const [from, to] = range.value
  if (source === 'GUEST') router.push({ name: 'guest-fees', query: { from, to, paymentStatus: 'PAID', includeCancelled: 'true' } })
  else router.push({ name: 'payments', query: { tab: 'items', paymentType: source, status: 'PAID' } })
}

function openExpense(category) {
  const [from, to] = range.value
  router.push({ name: 'expenses', query: { from, to, category } })
}

function openDebtor(d) {
  if (d.userId == null) router.push({ name: 'guest-fees', query: { paymentStatus: 'UNPAID' } })
  else if (d.paymentCount > 0) router.push({ name: 'payments', query: { tab: 'items', status: 'UNPAID', keyword: d.email } })
  else router.push({ name: 'guest-fees', query: { paymentStatus: 'UNPAID', keyword: d.name } })
}

onMounted(fetchReport)
</script>

<template>
  <div class="report">
    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group :model-value="preset" class="presets" @change="choosePreset">
          <el-radio-button v-for="p in PRESETS" :key="p.value" :value="p.value">{{ p.label }}</el-radio-button>
        </el-radio-group>
        <el-date-picker
          :model-value="range"
          type="daterange"
          format="DD/MM/YYYY"
          value-format="DD/MM/YYYY"
          range-separator="–"
          start-placeholder="Từ ngày"
          end-placeholder="Đến ngày"
          :editable="false"
          :clearable="false"
          class="range"
          @update:model-value="chooseRange"
        />
        <el-button type="primary" :icon="Download" :loading="exporting" :disabled="!report" class="export" @click="exportExcel">
          Xuất Excel
        </el-button>
      </div>
      <p class="text-secondary period-note">
        Kỳ báo cáo {{ range[0] }} – {{ range[1] }}. Thu tính theo ngày thu tiền, chi tính theo ngày chi.
      </p>
    </el-card>

    <el-result v-if="error && !report" icon="error" title="Không tải được báo cáo" :sub-title="error.message">
      <template #extra><el-button @click="fetchReport">Thử lại</el-button></template>
    </el-result>

    <template v-else>
      <div v-loading="loading && !report" class="stats">
        <div class="stat">
          <span class="stat-label">Tồn quỹ đầu kỳ</span>
          <span class="stat-value">{{ report ? formatCurrency(report.openingBalance) : '—' }}</span>
          <span class="text-secondary stat-sub">Thu − chi trước {{ range[0] }}</span>
        </div>
        <div class="stat">
          <span class="stat-label">Tổng thu</span>
          <span class="stat-value">{{ report ? formatCurrency(report.totalIncome) : '—' }}</span>
          <span class="text-secondary stat-sub">{{ income.reduce((s, l) => s + l.count, 0) }} khoản đã thu</span>
        </div>
        <div class="stat">
          <span class="stat-label">Tổng chi</span>
          <span class="stat-value">{{ report ? formatCurrency(report.totalExpense) : '—' }}</span>
          <span class="text-secondary stat-sub">{{ expense.reduce((s, l) => s + l.count, 0) }} khoản chi</span>
        </div>
        <div class="stat stat-hero">
          <span class="stat-label">Tồn quỹ cuối kỳ</span>
          <span class="stat-value" :class="{ negative: report && Number(report.closingBalance) < 0 }">
            {{ report ? formatCurrency(report.closingBalance) : '—' }}
          </span>
          <span class="text-secondary stat-sub">
            Chênh lệch trong kỳ
            <strong :class="net < 0 ? 'negative' : 'positive'">{{ net > 0 ? '+' : '' }}{{ formatCurrency(net) }}</strong>
          </span>
        </div>
      </div>

      <div v-if="report" class="breakdown">
        <el-card shadow="never">
          <template #header>
            <div class="card-header"><span>Cơ cấu thu</span><strong>{{ formatCurrency(report.totalIncome) }}</strong></div>
          </template>
          <button v-for="l in income" :key="l.source" type="button" class="share-row" @click="openIncome(l.source)">
            <span class="share-head">
              <span>{{ INCOME_LABELS[l.source] }} <span class="text-secondary">· {{ l.count }} khoản</span></span>
              <span class="money">{{ formatCurrency(l.amount) }}</span>
            </span>
            <span class="share-track">
              <span class="share-bar income" :style="{ width: `${share(l.amount, report.totalIncome)}%` }" />
            </span>
            <span class="text-secondary share-pct">{{ share(l.amount, report.totalIncome) }}%
              <el-icon class="share-arrow"><ArrowRight /></el-icon>
            </span>
          </button>
        </el-card>
        <el-card shadow="never">
          <template #header>
            <div class="card-header"><span>Cơ cấu chi</span><strong>{{ formatCurrency(report.totalExpense) }}</strong></div>
          </template>
          <button v-for="l in expense" :key="l.category" type="button" class="share-row" @click="openExpense(l.category)">
            <span class="share-head">
              <span>{{ EXPENSE_CATEGORY_LABELS[l.category] }} <span class="text-secondary">· {{ l.count }} khoản</span></span>
              <span class="money">{{ formatCurrency(l.amount) }}</span>
            </span>
            <span class="share-track">
              <span class="share-bar expense" :style="{ width: `${share(l.amount, report.totalExpense)}%` }" />
            </span>
            <span class="text-secondary share-pct">{{ share(l.amount, report.totalExpense) }}%
              <el-icon class="share-arrow"><ArrowRight /></el-icon>
            </span>
          </button>
        </el-card>
      </div>

      <el-card v-if="report" shadow="never">
        <template #header>
          <div class="card-header">
            <span>Thu chi theo tháng</span>
            <el-radio-group v-model="view" size="small">
              <el-radio-button value="chart">Biểu đồ</el-radio-button>
              <el-radio-button value="table">Bảng</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <MonthlyChart v-if="view === 'chart'" :months="report.months" />
        <div v-else class="table-wrap">
          <el-table :data="report.months" stripe size="small">
            <el-table-column label="Tháng" min-width="90">
              <template #default="{ row }">{{ String(row.month).padStart(2, '0') }}/{{ row.year }}</template>
            </el-table-column>
            <el-table-column label="Thu" min-width="120" align="right">
              <template #default="{ row }"><span class="money">{{ formatCurrency(row.income) }}</span></template>
            </el-table-column>
            <el-table-column label="Chi" min-width="120" align="right">
              <template #default="{ row }"><span class="money">{{ formatCurrency(row.expense) }}</span></template>
            </el-table-column>
            <el-table-column label="Chênh lệch" min-width="120" align="right">
              <template #default="{ row }">
                <span class="money" :class="{ negative: row.income - row.expense < 0 }">
                  {{ formatCurrency(row.income - row.expense) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <el-card v-if="report" shadow="never">
        <template #header>
          <div class="card-header">
            <span>
              Công nợ hiện tại
              <span class="text-secondary">({{ debtors.length }} người · tính đến bây giờ, không theo kỳ)</span>
            </span>
            <strong class="owed">{{ formatCurrency(report.outstanding.total) }}</strong>
          </div>
        </template>
        <p class="text-secondary debt-summary">
          Khoản thu chưa nộp: {{ report.outstanding.paymentCount }} khoản · {{ formatCurrency(report.outstanding.paymentAmount) }}
          — Phí khách chưa thu: {{ report.outstanding.guestCount }} khách · {{ formatCurrency(report.outstanding.guestAmount) }}
          (tính cho người dẫn, bỏ buổi đã hủy)
        </p>
        <el-empty v-if="!debtors.length" :image-size="64" description="Không còn ai nợ. Tuyệt vời!" />
        <template v-else>
          <div v-if="isSmall" class="debt-list">
            <button v-for="d in visibleDebtors" :key="d.userId ?? 'self'" type="button" class="debt-item" @click="openDebtor(d)">
              <span class="debt-main">
                <span class="name">{{ d.name }}</span>
                <span class="text-secondary">
                  <template v-if="d.paymentCount">{{ d.paymentCount }} khoản · {{ formatCurrency(d.paymentAmount) }}</template>
                  <template v-if="d.paymentCount && d.guestCount"> — </template>
                  <template v-if="d.guestCount">{{ d.guestCount }} khách · {{ formatCurrency(d.guestAmount) }}</template>
                </span>
              </span>
              <span class="money strong">{{ formatCurrency(d.total) }}</span>
            </button>
          </div>
          <div v-else class="table-wrap">
            <el-table :data="visibleDebtors" stripe row-key="name" class="debt-table" @row-click="openDebtor">
              <el-table-column label="STT" width="56" align="center">
                <template #default="{ $index }">{{ $index + 1 }}</template>
              </el-table-column>
              <el-table-column label="Thành viên" min-width="200">
                <template #default="{ row }">
                  <div class="name">{{ row.name }}</div>
                  <div v-if="row.email" class="text-secondary">{{ row.email }}</div>
                </template>
              </el-table-column>
              <el-table-column label="Khoản chưa nộp" min-width="150" align="right">
                <template #default="{ row }">
                  <span v-if="row.paymentCount" class="money">{{ formatCurrency(row.paymentAmount) }}</span>
                  <div v-if="row.paymentCount" class="text-secondary">{{ row.paymentCount }} khoản</div>
                  <span v-else class="text-secondary">—</span>
                </template>
              </el-table-column>
              <el-table-column label="Phí khách chưa thu" min-width="150" align="right">
                <template #default="{ row }">
                  <span v-if="row.guestCount" class="money">{{ formatCurrency(row.guestAmount) }}</span>
                  <div v-if="row.guestCount" class="text-secondary">{{ row.guestCount }} khách</div>
                  <span v-else class="text-secondary">—</span>
                </template>
              </el-table-column>
              <el-table-column label="Tổng nợ" min-width="130" align="right">
                <template #default="{ row }"><strong class="money">{{ formatCurrency(row.total) }}</strong></template>
              </el-table-column>
            </el-table>
          </div>
          <el-button
            v-if="debtors.length > DEBTOR_PREVIEW"
            link
            type="primary"
            class="more"
            @click="showAllDebtors = !showAllDebtors"
          >
            {{ showAllDebtors ? 'Thu gọn' : `Xem tất cả ${debtors.length} người` }}
          </el-button>
        </template>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.report {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.toolbar :deep(.el-date-editor.range) {
  width: 260px;
}

.export {
  margin-left: auto;
}

.period-note {
  margin: 10px 0 0;
  font-size: 13px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  min-height: 96px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}

.stat-hero {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  color: var(--el-text-color-primary);
  font-size: 24px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stat-sub {
  font-size: 12px;
}

.negative {
  color: var(--el-color-danger);
}

.positive {
  color: var(--el-color-success);
}

.breakdown {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
}

.card-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.share-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 6px 12px;
  width: 100%;
  padding: 10px 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.share-row:hover {
  background: var(--el-fill-color-light);
}

.share-row:focus-visible {
  outline: 2px solid var(--el-color-primary);
}

.share-head {
  display: flex;
  grid-column: 1 / -1;
  justify-content: space-between;
  gap: 8px;
}

.share-track {
  align-self: center;
  height: 8px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--el-fill-color);
}

.share-bar {
  display: block;
  height: 100%;
  border-radius: 4px;
}

.share-bar.income {
  background: var(--app-chart-income);
}

.share-bar.expense {
  background: var(--app-chart-expense);
}

.share-pct {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 52px;
  justify-content: flex-end;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.money {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.money.strong {
  font-weight: 600;
}

.owed {
  color: var(--el-color-warning);
  font-variant-numeric: tabular-nums;
}

.table-wrap {
  overflow-x: auto;
}

.debt-summary {
  margin: 0 0 12px;
  font-size: 13px;
}

.debt-table :deep(.el-table__row) {
  cursor: pointer;
}

.name {
  font-weight: 500;
}

.debt-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 10px 0;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.debt-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.more {
  margin-top: 8px;
}

@media (max-width: 768px) {
  .presets {
    display: flex;
    width: 100%;
  }

  .presets :deep(.el-radio-button) {
    flex: 1;
  }

  .presets :deep(.el-radio-button__inner) {
    width: 100%;
    padding: 8px 4px;
  }

  .toolbar :deep(.el-date-editor.range) {
    width: 100%;
  }

  .export {
    width: 100%;
    margin-left: 0;
  }

  .stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat {
    padding: 12px 14px;
  }

  .stat-value {
    font-size: 18px;
  }

  .breakdown {
    grid-template-columns: 1fr;
  }
}
</style>
