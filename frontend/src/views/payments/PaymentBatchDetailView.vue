<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Edit, Delete, Plus, Search } from '@element-plus/icons-vue'
import { paymentApi } from '@/api/payments'
import PaymentRows from '@/components/payments/PaymentRows.vue'
import PaymentBulkBar from '@/components/payments/PaymentBulkBar.vue'
import PaymentFormDialog from '@/components/payments/PaymentFormDialog.vue'
import BatchFormDialog from '@/components/payments/BatchFormDialog.vue'
import AddBatchMembersDialog from '@/components/payments/AddBatchMembersDialog.vue'
import { usePaymentActions } from '@/composables/usePaymentActions'
import { PAYMENT_TYPE_LABELS, PAYMENT_TYPE_TAG_TYPES } from '@/utils/labels'
import { formatCurrency } from '@/utils/format'

// Chi tiết 1 đợt thu: thu tiền từng người / nhiều người, thêm người, sửa đợt, xóa đợt
const route = useRoute()
const router = useRouter()

const batch = ref(null)
const payments = ref([])
const loading = ref(false)
const error = ref(null)
const selected = ref([])
const statusFilter = ref('')
const keyword = ref('')
const deleting = ref(false)
// Phân trang ngay trên trình duyệt (đã tải hết người trong đợt)
const pagination = reactive({ page: 1, size: 20 })

const editVisible = ref(false)
const editingPayment = ref(null)
const batchFormVisible = ref(false)
const addVisible = ref(false)

const { busyId, bulkBusy, collect, undo, remove, bulkCollect, bulkRemove } = usePaymentActions(fetchAll)

async function fetchAll() {
  const id = route.params.id
  loading.value = true
  try {
    const [b, list] = await Promise.all([paymentApi.getBatch(id), paymentApi.batchPayments(id)])
    batch.value = b
    payments.value = list
    error.value = null
    const ids = new Set(list.map((p) => p.id))
    selected.value = selected.value.filter((x) => ids.has(x))
  } catch (err) {
    // Đợt vừa bị xóa hết người (xóa từng khoản / xóa nhiều) -> về danh sách
    if (err.status === 404 && batch.value) {
      ElMessage.info('Đợt thu không còn khoản nào nên đã được xóa')
      router.replace({ name: 'payments' })
      return
    }
    error.value = err
  } finally {
    loading.value = false
  }
}

const normalize = (text) =>
  (text ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/gi, 'd').toLowerCase()

const unpaidCount = computed(() => payments.value.filter((p) => p.status === 'UNPAID').length)
const paidCount = computed(() => payments.value.length - unpaidCount.value)
const visibleRows = computed(() => {
  const k = normalize(keyword.value.trim())
  return payments.value.filter((p) =>
    (!statusFilter.value || p.status === statusFilter.value)
    && (!k || normalize(p.userName).includes(k) || normalize(p.userEmail).includes(k)))
})
const pagedRows = computed(() =>
  visibleRows.value.slice((pagination.page - 1) * pagination.size, pagination.page * pagination.size))
const selectedRows = computed(() => payments.value.filter((p) => selected.value.includes(p.id)))
// Đã chọn hết trang này nhưng còn khoản khớp bộ lọc ở trang khác -> gợi ý chọn tất cả
const selectedSet = computed(() => new Set(selected.value))
const pageAllSelected = computed(() => pagedRows.value.length > 0 && pagedRows.value.every((p) => selectedSet.value.has(p.id)))
const allVisibleSelected = computed(() => visibleRows.value.every((p) => selectedSet.value.has(p.id)))
const showSelectAllHint = computed(() => pageAllSelected.value && visibleRows.value.length > pagedRows.value.length)

function selectAllVisible() {
  selected.value = [...new Set([...selected.value, ...visibleRows.value.map((p) => p.id)])]
}

// Đổi bộ lọc: về trang 1; danh sách ngắn lại (vừa xóa) thì lùi về trang cuối còn dữ liệu
watch([statusFilter, keyword], () => {
  pagination.page = 1
})
watch(() => visibleRows.value.length, (length) => {
  const lastPage = Math.max(1, Math.ceil(length / pagination.size))
  if (pagination.page > lastPage) pagination.page = lastPage
})
const percent = computed(() =>
  batch.value?.memberCount ? Math.round((batch.value.paidCount / batch.value.memberCount) * 100) : 0)
const unpaidAmount = computed(() => (batch.value ? Number(batch.value.totalAmount) - Number(batch.value.paidAmount) : 0))
const subtitle = computed(() => {
  const b = batch.value
  if (!b) return ''
  return b.paymentType === 'MONTHLY'
    ? `Tháng ${b.month}/${b.year} · số tiền theo Mức phí của từng giới tính`
    : `${formatCurrency(b.amount)} mỗi người · tạo ${b.createdAt.slice(0, 10)} bởi ${b.createdByName}`
})

// Chưa ai nộp: xóa cả đợt. Đã có người nộp: chỉ xóa người chưa nộp, giữ người đã nộp.
async function deleteBatch() {
  const b = batch.value
  const message = paidCount.value === 0
    ? `Xóa đợt "${b.description}" cùng ${unpaidCount.value} khoản chưa thu? Không thể hoàn tác.`
    : `Xóa ${unpaidCount.value} người chưa nộp khỏi đợt "${b.description}"? ${paidCount.value} người đã nộp được giữ lại `
      + '(muốn xóa họ thì hoàn tác thu tiền trước).'
  try {
    await ElMessageBox.confirm(message, paidCount.value === 0 ? 'Xóa đợt thu' : 'Xóa người chưa nộp', {
      confirmButtonText: 'Xóa',
      cancelButtonText: 'Không',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return
  }
  deleting.value = true
  try {
    const res = await paymentApi.removeBatch(b.id)
    if (res.batchDeleted) {
      ElMessage.success(`Đã xóa đợt "${b.description}"`)
      router.replace({ name: 'payments' })
      return
    }
    ElMessage.success(`Đã xóa ${res.deleted} người chưa nộp, giữ lại ${res.keptPaid} người đã nộp`)
    fetchAll()
  } catch (err) {
    ElMessage.error(err.message)
    fetchAll()
  } finally {
    deleting.value = false
  }
}

function openEdit(row) {
  editingPayment.value = row
  editVisible.value = true
}

function goBack() {
  // Quay lại danh sách (giữ bộ lọc) nếu đi từ danh sách; mở link trực tiếp thì về danh sách mặc định
  if (window.history.state?.back) router.back()
  else router.push({ name: 'payments' })
}

watch(() => route.params.id, (id) => {
  if (!id) return
  batch.value = null
  selected.value = []
  fetchAll()
})
onMounted(fetchAll)
</script>

<template>
  <div v-loading="loading && !batch" class="detail-page">
    <el-button :icon="ArrowLeft" text class="back" @click="goBack">Khoản thu</el-button>

    <el-result
      v-if="error"
      :icon="error.status === 404 ? 'warning' : 'error'"
      :title="error.status === 404 ? 'Không tìm thấy đợt thu' : 'Không tải được dữ liệu'"
      :sub-title="error.message"
    >
      <template #extra>
        <el-button type="primary" @click="router.push({ name: 'payments' })">Về danh sách</el-button>
      </template>
    </el-result>

    <template v-else-if="batch">
      <el-card shadow="never" class="summary">
        <div class="summary-info">
          <div class="title-row">
            <el-tag :type="PAYMENT_TYPE_TAG_TYPES[batch.paymentType]" round effect="plain" disable-transitions>
              {{ PAYMENT_TYPE_LABELS[batch.paymentType] }}
            </el-tag>
            <h2>{{ batch.description }}</h2>
          </div>
          <p class="text-secondary">{{ subtitle }}</p>
          <p v-if="batch.note" class="note">{{ batch.note }}</p>
          <div class="progress">
            <div class="progress-text">
              <span>Đã thu <strong>{{ batch.paidCount }}/{{ batch.memberCount }}</strong> người</span>
              <span class="money">
                {{ formatCurrency(batch.paidAmount) }} / {{ formatCurrency(batch.totalAmount) }}
                <span v-if="unpaidAmount > 0" class="owed">· còn {{ formatCurrency(unpaidAmount) }}</span>
              </span>
            </div>
            <el-progress
              :percentage="percent"
              :status="batch.memberCount && batch.paidCount === batch.memberCount ? 'success' : ''"
              :stroke-width="10"
              :show-text="false"
            />
          </div>
        </div>
        <div class="summary-actions">
          <el-button :icon="Plus" @click="addVisible = true">Thêm người</el-button>
          <el-button :icon="Edit" @click="batchFormVisible = true">Sửa đợt</el-button>
          <el-tooltip :disabled="unpaidCount > 0" content="Mọi người đã nộp — hoàn tác thu tiền trước nếu muốn xóa">
            <span>
              <el-button
                type="danger"
                plain
                :icon="Delete"
                :loading="deleting"
                :disabled="unpaidCount === 0"
                @click="deleteBatch"
              >
                {{ paidCount === 0 ? 'Xóa đợt' : `Xóa ${unpaidCount} người chưa nộp` }}
              </el-button>
            </span>
          </el-tooltip>
        </div>
      </el-card>

      <el-card shadow="never">
        <div class="toolbar">
          <el-radio-group v-model="statusFilter">
            <el-radio-button value="">Tất cả ({{ payments.length }})</el-radio-button>
            <el-radio-button value="UNPAID">Chưa thu ({{ unpaidCount }})</el-radio-button>
            <el-radio-button value="PAID">Đã thu ({{ paidCount }})</el-radio-button>
          </el-radio-group>
          <el-input v-model="keyword" :prefix-icon="Search" placeholder="Tìm tên hoặc email" clearable class="keyword" />
        </div>

        <el-alert v-if="showSelectAllHint" type="info" :closable="false" class="select-hint">
          <template #title>
            <template v-if="!allVisibleSelected">
              Đã chọn {{ pagedRows.length }} khoản trên trang này.
              <el-button link type="primary" @click="selectAllVisible">Chọn tất cả {{ visibleRows.length }} khoản</el-button>
            </template>
            <template v-else>
              Đã chọn cả {{ visibleRows.length }} khoản.
              <el-button link type="primary" @click="selected = []">Bỏ chọn</el-button>
            </template>
          </template>
        </el-alert>

        <PaymentRows
          v-model:selected="selected"
          :rows="pagedRows"
          :start-index="(pagination.page - 1) * pagination.size + 1"
          :loading="loading"
          :show-batch="false"
          :busy-id="busyId"
          :empty-text="payments.length ? 'Không có ai khớp bộ lọc' : 'Đợt này chưa có ai'"
          @edit="openEdit"
          @collect="collect"
          @undo="undo"
          @remove="remove"
        />

        <PaymentBulkBar
          :rows="selectedRows"
          :busy="bulkBusy"
          @collect="(m) => bulkCollect(selectedRows, m)"
          @remove="bulkRemove(selectedRows)"
          @clear="selected = []"
        />

        <div v-if="visibleRows.length > 10" class="pagination">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="visibleRows.length"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            background
            @size-change="pagination.page = 1"
          />
        </div>
      </el-card>

      <PaymentFormDialog v-model="editVisible" :payment="editingPayment" @saved="fetchAll" />
      <BatchFormDialog v-model="batchFormVisible" :batch="batch" @saved="fetchAll" />
      <AddBatchMembersDialog
        v-model="addVisible"
        :batch="batch"
        :existing-ids="payments.map((p) => p.userId)"
        @saved="fetchAll"
      />
    </template>
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.summary-info {
  flex: 1;
  min-width: 260px;
}

.title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.title-row h2 {
  margin: 0;
  font-size: 22px;
  word-break: break-word;
}

.summary-info p {
  margin: 4px 0 0;
  font-size: 13px;
}

.note {
  color: var(--el-text-color-regular);
}

.progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 480px;
  margin-top: 12px;
}

.progress-text {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4px 12px;
  font-size: 14px;
}

.money {
  font-variant-numeric: tabular-nums;
}

.owed {
  color: var(--el-color-warning);
}

.summary-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.summary-actions .el-button {
  margin-left: 0;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 16px;
}

.keyword {
  width: 240px;
  max-width: 100%;
}

.select-hint {
  margin-bottom: 12px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}

@media (max-width: 768px) {
  .summary-info {
    min-width: 0;
    flex-basis: 100%;
  }

  .summary-actions {
    width: 100%;
  }

  .summary-actions > * {
    flex: 1;
  }

  .summary-actions :deep(.el-button) {
    width: 100%;
  }

  .keyword {
    width: 100%;
  }

  .toolbar :deep(.el-radio-group) {
    display: flex;
    width: 100%;
  }

  .toolbar :deep(.el-radio-button) {
    flex: 1;
  }

  .toolbar :deep(.el-radio-button__inner) {
    width: 100%;
    padding: 8px 4px;
  }
}
</style>
