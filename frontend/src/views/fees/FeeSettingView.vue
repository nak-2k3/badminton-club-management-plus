<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, VideoPause, VideoPlay, Male, Female, User } from '@element-plus/icons-vue'
import { feeApi } from '@/api/fees'
import { useAuthStore } from '@/stores/auth'
import FeeFormDialog from '@/components/fees/FeeFormDialog.vue'
import { FEE_TYPE_LABELS, GENDER_LABELS } from '@/utils/labels'
import { formatCurrency, parseDate, startOfToday } from '@/utils/format'

const auth = useAuthStore()
const canEdit = computed(() => auth.hasRole('ADMIN'))

const currentFees = ref([])
const fees = ref([])
const feeTypeFilter = ref('')
const loading = ref(false)

const formVisible = ref(false)
const editingFee = ref(null)
const preset = ref(null)

// Thẻ mức đang áp dụng: phí tháng nam, phí tháng nữ, phí khách
const CARD_META = {
  'MONTHLY-MALE': { title: 'Phí tháng – Nam', unit: '/ tháng', icon: Male },
  'MONTHLY-FEMALE': { title: 'Phí tháng – Nữ', unit: '/ tháng', icon: Female },
  'GUEST-': { title: 'Phí khách', unit: '/ buổi', icon: User }
}
const cardKey = (fee) => `${fee.feeType}-${fee.gender ?? ''}`

async function loadAll() {
  loading.value = true
  try {
    const [current, list] = await Promise.all([
      feeApi.current(),
      feeApi.list(feeTypeFilter.value ? { feeType: feeTypeFilter.value } : {})
    ])
    currentFees.value = current
    fees.value = list
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

// Trạng thái một dòng trong lịch sử
function rowStatus(fee) {
  if (!fee.active) return { label: 'Đã ngưng', type: 'info' }
  if (fee.current) return { label: 'Đang áp dụng', type: 'success' }
  const from = parseDate(fee.effectiveFrom)
  if (from && from > startOfToday()) return { label: 'Sắp áp dụng', type: 'warning' }
  return { label: 'Mức cũ', type: 'info' }
}

const typeLabel = (fee) =>
  fee.feeType === 'MONTHLY' ? `${FEE_TYPE_LABELS.MONTHLY} – ${GENDER_LABELS[fee.gender]}` : FEE_TYPE_LABELS.GUEST

function openCreate(card) {
  editingFee.value = null
  preset.value = card ? { feeType: card.feeType, gender: card.gender ?? '' } : null
  formVisible.value = true
}

function openEdit(fee) {
  editingFee.value = fee
  preset.value = null
  formVisible.value = true
}

async function toggleActive(fee) {
  const activate = !fee.active
  try {
    await ElMessageBox.confirm(
      activate
        ? `Áp dụng lại mức ${formatCurrency(fee.amount)} (${typeLabel(fee)}) từ ngày ${fee.effectiveFrom}?`
        : `Ngưng mức ${formatCurrency(fee.amount)} (${typeLabel(fee)})? Mức phí trước đó (nếu có) sẽ được áp dụng lại.`,
      activate ? 'Áp dụng lại mức phí' : 'Ngưng mức phí',
      { confirmButtonText: 'Xác nhận', cancelButtonText: 'Hủy', type: activate ? 'info' : 'warning' }
    )
  } catch {
    return
  }
  try {
    await feeApi.setActive(fee.id, activate)
    ElMessage.success(activate ? 'Đã áp dụng lại mức phí' : 'Đã ngưng mức phí')
    loadAll()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

onMounted(loadAll)
</script>

<template>
  <div v-loading="loading" class="fee-page">
    <div class="cards">
      <el-card v-for="fee in currentFees" :key="cardKey(fee)" shadow="never" class="fee-card">
        <div class="fee-card-head">
          <span class="fee-icon"><el-icon :size="20"><component :is="CARD_META[cardKey(fee)]?.icon" /></el-icon></span>
          <span class="text-secondary">{{ CARD_META[cardKey(fee)]?.title }}</span>
        </div>
        <template v-if="fee.amount !== null">
          <div class="fee-amount">
            {{ formatCurrency(fee.amount) }}
            <span class="text-secondary unit">{{ CARD_META[cardKey(fee)]?.unit }}</span>
          </div>
          <div class="text-secondary">Áp dụng từ {{ fee.effectiveFrom }}</div>
        </template>
        <template v-else>
          <div class="fee-amount empty">Chưa thiết lập</div>
          <el-button v-if="canEdit" link type="primary" @click="openCreate(fee)">Thiết lập ngay</el-button>
        </template>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="history-head">
          <span>Lịch sử mức phí</span>
          <div class="history-actions">
            <el-radio-group v-model="feeTypeFilter" size="small" @change="loadAll">
              <el-radio-button value="">Tất cả</el-radio-button>
              <el-radio-button value="MONTHLY">Phí tháng</el-radio-button>
              <el-radio-button value="GUEST">Phí khách</el-radio-button>
            </el-radio-group>
            <el-button v-if="canEdit" type="primary" :icon="Plus" @click="openCreate()">Thêm mức phí</el-button>
          </div>
        </div>
      </template>

      <div class="table-wrap">
        <el-table :data="fees" row-key="id" stripe empty-text="Chưa có mức phí nào">
          <el-table-column label="Loại phí" min-width="170">
            <template #default="{ row }">{{ typeLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="Số tiền" width="140" align="right">
            <template #default="{ row }"><span class="money">{{ formatCurrency(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column label="Áp dụng từ" prop="effectiveFrom" width="120" />
          <el-table-column label="Trạng thái" width="130">
            <template #default="{ row }">
              <el-tag :type="rowStatus(row).type" size="small" round disable-transitions>
                {{ rowStatus(row).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="canEdit" label="Thao tác" width="100" fixed="right" align="center">
            <template #default="{ row }">
              <el-tooltip content="Sửa" :show-after="500">
                <el-button :icon="Edit" link type="primary" aria-label="Sửa" @click="openEdit(row)" />
              </el-tooltip>
              <el-tooltip :content="row.active ? 'Ngưng mức phí' : 'Áp dụng lại'" :show-after="500">
                <el-button
                  :icon="row.active ? VideoPause : VideoPlay"
                  link
                  :aria-label="row.active ? 'Ngưng mức phí' : 'Áp dụng lại'"
                  @click="toggleActive(row)"
                />
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <p class="text-secondary footnote">
        Mức đang áp dụng là mức còn hiệu lực có ngày áp dụng gần nhất (không sau hôm nay). Học phí tháng và phí khách
        được tính theo mức áp dụng tại thời điểm tạo khoản thu.
      </p>
    </el-card>

    <FeeFormDialog v-model="formVisible" :fee="editingFee" :preset="preset" @saved="loadAll" />
  </div>
</template>

<style scoped>
.fee-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.fee-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fee-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.fee-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.fee-amount {
  font-size: 24px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.fee-amount.empty {
  font-size: 18px;
  color: var(--el-text-color-secondary);
}

.unit {
  font-size: 13px;
  font-weight: 400;
}

.history-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.history-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.table-wrap {
  overflow-x: auto;
}

.money {
  font-variant-numeric: tabular-nums;
}

.footnote {
  margin: 12px 0 0;
}
</style>
