<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Tickets } from '@element-plus/icons-vue'
import { expenseApi } from '@/api/expenses'
import ExpenseFormDialog from '@/components/expenses/ExpenseFormDialog.vue'
import { EXPENSE_CATEGORY_LABELS, EXPENSE_CATEGORY_TAG_TYPES } from '@/utils/labels'
import { formatCurrency } from '@/utils/format'

// Thẻ "Khoản chi" trong trang chi tiết buổi chơi (ADMIN, TREASURER): ghi nhanh tiền sân + cầu (điền sẵn tiền sân
// dự kiến), thêm khoản chi khác gắn với buổi, sửa / xóa
const props = defineProps({
  schedule: { type: Object, required: true }
})

const expenses = ref([])
const totalAmount = ref(0)
const loading = ref(false)
const loadError = ref(false)
const busyId = ref(null)
const formVisible = ref(false)
const editingExpense = ref(null)
const presetCategory = ref(null)

const sessionExpense = computed(() => expenses.value.find((e) => e.category === 'SESSION') ?? null)
// Ghi tiền sân + cầu: mỗi buổi 1 khoản; buổi đã hủy không gợi ý (vẫn thêm được qua "Thêm khoản chi")
const canQuickSession = computed(() => !sessionExpense.value && props.schedule.status !== 'CANCELLED')

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const res = await expenseApi.search({ scheduleId: props.schedule.id, size: 100, sort: 'expenseDate', direction: 'asc' })
    expenses.value = res.page.content
    totalAmount.value = res.totalAmount
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function openAdd(category = null) {
  editingExpense.value = null
  presetCategory.value = category
  formVisible.value = true
}

function openEdit(expense) {
  editingExpense.value = expense
  presetCategory.value = null
  formVisible.value = true
}

async function remove(expense) {
  try {
    await ElMessageBox.confirm(
      `Xóa khoản chi "${expense.description}" (${formatCurrency(expense.amount)})? Không thể hoàn tác.`,
      'Xóa khoản chi',
      { confirmButtonText: 'Xóa', cancelButtonText: 'Không', type: 'warning', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  busyId.value = expense.id
  try {
    await expenseApi.remove(expense.id)
    ElMessage.success('Đã xóa khoản chi')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    busyId.value = null
    load()
  }
}

watch(() => props.schedule.id, load, { immediate: true })
</script>

<template>
  <el-card shadow="never" class="expenses">
    <template #header>
      <div class="header">
        <span>
          Khoản chi
          <span class="text-secondary">({{ expenses.length }} khoản · {{ formatCurrency(totalAmount) }})</span>
        </span>
        <div class="header-actions">
          <el-button v-if="canQuickSession" type="primary" :icon="Tickets" size="small" @click="openAdd('SESSION')">
            Ghi tiền sân + cầu
          </el-button>
          <el-button :icon="Plus" size="small" @click="openAdd()">Thêm khoản chi</el-button>
        </div>
      </div>
    </template>

    <el-skeleton v-if="loading && !expenses.length" :rows="2" animated />
    <el-result v-else-if="loadError" icon="error" title="Không tải được khoản chi">
      <template #extra><el-button @click="load">Thử lại</el-button></template>
    </el-result>
    <template v-else>
      <p v-if="schedule.status !== 'CANCELLED'" class="text-secondary rent-compare">
        Tiền sân dự kiến {{ formatCurrency(schedule.estimatedCourtCost) }}
        <template v-if="sessionExpense"> · đã ghi tiền sân + cầu {{ formatCurrency(sessionExpense.amount) }}</template>
        <template v-else> · <span class="diff">chưa ghi tiền sân + cầu</span></template>
      </p>
      <el-empty v-if="!expenses.length" :image-size="56" description="Buổi này chưa có khoản chi nào" />
      <div v-for="e in expenses" :key="e.id" class="exp-row">
        <div class="exp-main">
          <div class="exp-title">
            <el-tag :type="EXPENSE_CATEGORY_TAG_TYPES[e.category]" size="small" round effect="plain" disable-transitions>
              {{ EXPENSE_CATEGORY_LABELS[e.category] }}
            </el-tag>
            <span class="name">{{ e.description }}</span>
          </div>
          <span class="text-secondary">
            {{ e.expenseDate }} · ghi bởi {{ e.createdByName }}<template v-if="e.note"> · {{ e.note }}</template>
          </span>
        </div>
        <span class="money">{{ formatCurrency(e.amount) }}</span>
        <div class="exp-buttons">
          <el-button :icon="Edit" link type="primary" :aria-label="`Sửa khoản chi ${e.description}`" @click="openEdit(e)" />
          <el-button
            :icon="Delete"
            link
            type="danger"
            :aria-label="`Xóa khoản chi ${e.description}`"
            :loading="busyId === e.id"
            @click="remove(e)"
          />
        </div>
      </div>
    </template>

    <ExpenseFormDialog
      v-model="formVisible"
      :expense="editingExpense"
      :schedule="editingExpense ? null : schedule"
      :category="presetCategory"
      @saved="load"
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

.rent-compare {
  margin: 0 0 8px;
  font-size: 13px;
}

.diff {
  color: var(--el-color-warning);
}

.exp-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.exp-row:last-child {
  border-bottom: none;
}

.exp-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.exp-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.name {
  font-weight: 500;
  word-break: break-word;
}

.money {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.exp-buttons {
  display: flex;
  flex-shrink: 0;
}

@media (max-width: 480px) {
  .exp-row {
    flex-wrap: wrap;
  }

  .exp-main {
    flex-basis: 100%;
  }

  .money {
    flex: 1;
  }
}
</style>
