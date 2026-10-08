<script setup>
import { Wallet, CircleCheck } from '@element-plus/icons-vue'
import { formatCurrency } from '@/utils/format'

// 2 thẻ tổng Chưa thu / Đã thu. clickable: bấm thẻ để lọc theo trạng thái (emit 'filter')
defineProps({
  totals: { type: Object, required: true },
  active: { type: String, default: '' },
  clickable: { type: Boolean, default: false }
})
const emit = defineEmits(['filter'])
</script>

<template>
  <div class="totals">
    <component
      :is="clickable ? 'button' : 'div'"
      :type="clickable ? 'button' : undefined"
      class="total-card tone-warning"
      :class="{ active: active === 'UNPAID', clickable }"
      @click="clickable && emit('filter', 'UNPAID')"
    >
      <span class="total-icon"><el-icon :size="22"><Wallet /></el-icon></span>
      <span class="total-body">
        <span class="total-label">Chưa thu · {{ totals.unpaidCount }} khoản</span>
        <span class="total-value">{{ formatCurrency(totals.unpaidAmount) }}</span>
      </span>
    </component>
    <component
      :is="clickable ? 'button' : 'div'"
      :type="clickable ? 'button' : undefined"
      class="total-card tone-success"
      :class="{ active: active === 'PAID', clickable }"
      @click="clickable && emit('filter', 'PAID')"
    >
      <span class="total-icon"><el-icon :size="22"><CircleCheck /></el-icon></span>
      <span class="total-body">
        <span class="total-label">Đã thu · {{ totals.paidCount }} khoản</span>
        <span class="total-value">{{ formatCurrency(totals.paidAmount) }}</span>
      </span>
    </component>
  </div>
</template>

<style scoped>
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
}

.total-card.clickable {
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.total-card.clickable:hover,
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

@media (prefers-reduced-motion: reduce) {
  .total-card.clickable {
    transition: none;
  }
}
</style>
