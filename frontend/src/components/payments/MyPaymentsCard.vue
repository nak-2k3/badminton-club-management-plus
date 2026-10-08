<script setup>
import { computed, onMounted, ref } from 'vue'
import { paymentApi } from '@/api/payments'
import { PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS, PAYMENT_STATUS_TAG_TYPES } from '@/utils/labels'
import { formatCurrency } from '@/utils/format'

// "Khoản phí của tôi" (mọi vai trò, chỉ xem): khoản chưa nộp + vài khoản đã nộp gần đây
const PAID_PREVIEW = 3
const payments = ref([])
const loading = ref(false)
const error = ref(false)
const showAllPaid = ref(false)

const unpaid = computed(() => payments.value.filter((p) => p.status === 'UNPAID'))
const paid = computed(() => payments.value.filter((p) => p.status === 'PAID'))
const visiblePaid = computed(() => (showAllPaid.value ? paid.value : paid.value.slice(0, PAID_PREVIEW)))
const unpaidTotal = computed(() => unpaid.value.reduce((sum, p) => sum + Number(p.amount), 0))

async function load() {
  loading.value = true
  error.value = false
  try {
    payments.value = await paymentApi.mine()
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>Khoản phí của tôi</span>
        <span v-if="unpaid.length" class="owed">Cần nộp {{ formatCurrency(unpaidTotal) }}</span>
      </div>
    </template>
    <el-skeleton v-if="loading" :rows="2" animated />
    <el-result v-else-if="error" icon="error" title="Không tải được khoản phí của bạn">
      <template #extra><el-button @click="load">Thử lại</el-button></template>
    </el-result>
    <el-empty v-else-if="!payments.length" :image-size="72" description="Bạn chưa có khoản phí nào" />
    <template v-else>
      <p v-if="!unpaid.length" class="text-secondary all-paid">Bạn đã nộp đủ các khoản phí. Cảm ơn bạn!</p>
      <div v-for="item in [...unpaid, ...visiblePaid]" :key="item.id" class="pay-row">
        <span class="pay-body">
          <span class="pay-title">{{ item.description }}</span>
          <span class="text-secondary">
            <template v-if="item.paidAt">{{ PAYMENT_METHOD_LABELS[item.paymentMethod] }} · {{ item.paidAt }}</template>
            <template v-else>{{ item.note || 'Vui lòng nộp cho thủ quỹ' }}</template>
          </span>
        </span>
        <span class="pay-side">
          <span class="money">{{ formatCurrency(item.amount) }}</span>
          <el-tag :type="PAYMENT_STATUS_TAG_TYPES[item.status]" size="small" round disable-transitions>
            {{ item.status === 'PAID' ? 'Đã nộp' : 'Chưa nộp' }}
          </el-tag>
        </span>
      </div>
      <el-button v-if="paid.length > PAID_PREVIEW" link type="primary" class="more" @click="showAllPaid = !showAllPaid">
        {{ showAllPaid ? 'Thu gọn' : `Xem tất cả ${paid.length} khoản đã nộp` }}
      </el-button>
    </template>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.owed {
  color: var(--el-color-warning);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.all-paid {
  margin: 0 0 8px;
}

.pay-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.pay-row:last-of-type {
  border-bottom: none;
}

.pay-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.pay-title {
  font-weight: 500;
}

.pay-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.money {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.more {
  margin-top: 8px;
}
</style>
