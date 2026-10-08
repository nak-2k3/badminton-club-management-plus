<script setup>
import { computed } from 'vue'
import { ArrowDown, Delete, Close } from '@element-plus/icons-vue'
import { PAYMENT_METHOD_LABELS, toOptions } from '@/utils/labels'
import { formatCurrency } from '@/utils/format'

// Thanh thao tác hàng loạt — chỉ hiện khi đã chọn ít nhất 1 khoản; dính ở cuối màn hình để luôn bấm được
const props = defineProps({
  // Các khoản đang chọn (object đầy đủ)
  rows: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false }
})
const emit = defineEmits(['collect', 'remove', 'clear'])

const methodOptions = toOptions(PAYMENT_METHOD_LABELS)
const unpaid = computed(() => props.rows.filter((r) => r.status === 'UNPAID'))
const unpaidTotal = computed(() => unpaid.value.reduce((sum, r) => sum + Number(r.amount), 0))
</script>

<template>
  <transition name="bulk">
    <div v-if="rows.length" class="bulk-bar" role="region" aria-label="Thao tác với các khoản đã chọn">
      <div class="bulk-info">
        <strong>Đã chọn {{ rows.length }} khoản</strong>
        <span class="text-secondary">
          {{ unpaid.length ? `${unpaid.length} chưa thu · ${formatCurrency(unpaidTotal)}` : 'Tất cả đã thu' }}
        </span>
      </div>
      <div class="bulk-actions">
        <el-dropdown trigger="click" :disabled="busy || !unpaid.length" @command="(m) => emit('collect', m)">
          <el-button type="success" :loading="busy" :disabled="!unpaid.length">
            Thu tiền <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="m in methodOptions" :key="m.value" :command="m.value">{{ m.label }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button type="danger" plain :icon="Delete" :disabled="busy || !unpaid.length" @click="emit('remove')">
          Xóa
        </el-button>
        <el-button :icon="Close" :disabled="busy" @click="emit('clear')">Bỏ chọn</el-button>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.bulk-bar {
  position: sticky;
  bottom: 12px;
  z-index: 5;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  margin-top: 12px;
  padding: 10px 14px;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 10px;
  background: var(--el-bg-color-overlay);
  box-shadow: var(--el-box-shadow);
}

.bulk-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-variant-numeric: tabular-nums;
}

.bulk-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.bulk-actions .el-button {
  margin-left: 0;
}

.bulk-enter-active,
.bulk-leave-active {
  transition: opacity 0.15s, transform 0.15s;
}

.bulk-enter-from,
.bulk-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 768px) {
  .bulk-actions {
    width: 100%;
  }

  .bulk-actions > * {
    flex: 1;
  }

  .bulk-actions :deep(.el-dropdown .el-button) {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bulk-enter-active,
  .bulk-leave-active {
    transition: none;
  }
}
</style>
