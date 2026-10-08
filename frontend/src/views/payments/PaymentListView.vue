<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Calendar } from '@element-plus/icons-vue'
import PaymentBatchList from '@/components/payments/PaymentBatchList.vue'
import PaymentItemList from '@/components/payments/PaymentItemList.vue'
import PaymentFormDialog from '@/components/payments/PaymentFormDialog.vue'
import MonthlyGenerateDialog from '@/components/payments/MonthlyGenerateDialog.vue'

// Trang "Khoản thu" (ADMIN, TREASURER): tab "Đợt thu" (mặc định) và tab "Tất cả khoản".
// Tạo khoản thu / phí tháng xong thì mở luôn trang chi tiết đợt vừa tạo.
const route = useRoute()
const router = useRouter()

const tab = ref(route.query.tab === 'items' ? 'items' : 'batches')
const formVisible = ref(false)
const monthlyVisible = ref(false)

// Đổi tab: bỏ bộ lọc của tab cũ
function changeTab(name) {
  router.replace({ query: name === 'items' ? { tab: 'items' } : {} })
}

function openBatch(batchId) {
  if (batchId) router.push({ name: 'payment-batch', params: { id: batchId } })
}
</script>

<template>
  <div class="payments">
    <div class="page-actions">
      <el-button :icon="Calendar" @click="monthlyVisible = true">Tạo phí tháng</el-button>
      <el-button type="primary" :icon="Plus" @click="formVisible = true">Thêm khoản thu</el-button>
    </div>

    <el-tabs v-model="tab" class="tabs" @tab-change="changeTab">
      <el-tab-pane label="Đợt thu" name="batches" />
      <el-tab-pane label="Tất cả khoản" name="items" />
    </el-tabs>

    <PaymentBatchList v-if="tab === 'batches'" key="batches" />
    <PaymentItemList v-else key="items" />

    <PaymentFormDialog v-model="formVisible" @saved="(batch) => openBatch(batch.id)" />
    <MonthlyGenerateDialog v-model="monthlyVisible" @saved="(res) => openBatch(res.batchId)" />
  </div>
</template>

<style scoped>
.payments {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.page-actions .el-button {
  margin-left: 0;
}

.tabs :deep(.el-tabs__header) {
  margin: 0;
}

@media (max-width: 768px) {
  .page-actions .el-button {
    flex: 1;
  }
}
</style>
