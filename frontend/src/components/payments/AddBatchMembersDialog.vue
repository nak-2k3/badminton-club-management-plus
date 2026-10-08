<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { paymentApi } from '@/api/payments'
import { formatCurrency } from '@/utils/format'
import MemberPicker from './MemberPicker.vue'

// Thêm người vào đợt thu (quên ai đó lúc tạo): cùng nội dung, số tiền với đợt; người đã có trong đợt không hiện
const props = defineProps({
  batch: { type: Object, default: null },
  // userId đã có trong đợt
  existingIds: { type: Array, default: () => [] }
})
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const allMembers = ref([])
const loading = ref(false)
const userIds = ref([])
const error = ref('')
const saving = ref(false)

const existing = computed(() => new Set(props.existingIds))
const options = computed(() => allMembers.value.filter((m) => !existing.value.has(m.id)))
const isMonthly = computed(() => props.batch?.paymentType === 'MONTHLY')

watch(visible, async (open) => {
  if (!open) return
  userIds.value = []
  error.value = ''
  loading.value = true
  try {
    allMembers.value = await paymentApi.memberOptions()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
})

async function handleSubmit() {
  error.value = ''
  if (!userIds.value.length) {
    error.value = 'Vui lòng chọn ít nhất 1 thành viên'
    return
  }
  saving.value = true
  try {
    const count = userIds.value.length
    const saved = await paymentApi.addBatchMembers(props.batch.id, userIds.value)
    ElMessage.success(`Đã thêm ${count} người vào đợt "${saved.description}"`)
    visible.value = false
    emit('saved', saved)
  } catch (err) {
    error.value = err.errors?.userIds ?? ''
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="Thêm người vào đợt"
    width="500px"
    :close-on-click-modal="false"
    destroy-on-close
    class="add-members-dialog"
  >
    <p v-if="batch" class="text-secondary intro">
      <strong>{{ batch.description }}</strong> ·
      <template v-if="isMonthly">số tiền theo Mức phí tháng của từng giới tính</template>
      <template v-else>{{ formatCurrency(batch.amount) }} mỗi người</template>
    </p>
    <el-form label-position="top" @submit.prevent="handleSubmit">
      <el-form-item label="Thành viên" required :error="error">
        <MemberPicker v-model="userIds" :options="options" :loading="loading" />
      </el-form-item>
      <p v-if="existingIds.length" class="text-secondary hint">{{ existingIds.length }} người đã có trong đợt nên không hiện trong danh sách.</p>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        Thêm {{ userIds.length || '' }} người
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.intro {
  margin: 0 0 12px;
}

.hint {
  margin: -8px 0 0;
  font-size: 13px;
}

:global(.add-members-dialog) {
  max-width: calc(100vw - 32px);
}
</style>
