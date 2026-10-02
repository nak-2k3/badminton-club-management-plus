<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { feeApi } from '@/api/fees'
import { FEE_TYPE_LABELS, GENDER_LABELS, toOptions } from '@/utils/labels'

// fee = null: thêm mới (preset: gợi ý loại phí/giới tính); fee = object: sửa
const props = defineProps({
  fee: { type: Object, default: null },
  preset: { type: Object, default: null }
})
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const isEdit = computed(() => !!props.fee)
const feeTypeOptions = toOptions(FEE_TYPE_LABELS)
const genderOptions = toOptions(GENDER_LABELS)

const formRef = ref()
const saving = ref(false)
const emptyForm = () => ({ feeType: 'MONTHLY', gender: '', amount: null, effectiveFrom: '' })
const form = reactive(emptyForm())
const serverErrors = reactive({})

const isMonthly = computed(() => form.feeType === 'MONTHLY')
const rules = computed(() => ({
  feeType: [{ required: true, message: 'Vui lòng chọn loại phí', trigger: 'change' }],
  gender: isMonthly.value ? [{ required: true, message: 'Phí tháng phải chọn giới tính', trigger: 'change' }] : [],
  amount: [{ required: true, message: 'Vui lòng nhập số tiền', trigger: 'blur' }],
  effectiveFrom: [{ required: true, message: 'Vui lòng chọn ngày áp dụng', trigger: 'change' }]
}))

watch(visible, (open) => {
  if (!open) return
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  Object.assign(form, emptyForm(), props.preset ?? {})
  if (props.fee) {
    const f = props.fee
    Object.assign(form, {
      feeType: f.feeType,
      gender: f.gender ?? '',
      amount: Number(f.amount),
      effectiveFrom: f.effectiveFrom
    })
  }
})

async function handleSubmit() {
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  if (!(await formRef.value.validate().catch(() => false))) return
  const payload = { ...form, gender: isMonthly.value ? form.gender : null }
  saving.value = true
  try {
    const saved = isEdit.value ? await feeApi.update(props.fee.id, payload) : await feeApi.create(payload)
    ElMessage.success(isEdit.value ? 'Đã cập nhật mức phí' : 'Đã thêm mức phí')
    visible.value = false
    emit('saved', saved)
  } catch (err) {
    Object.assign(serverErrors, err.errors)
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? 'Sửa mức phí' : 'Thêm mức phí'"
    width="480px"
    :close-on-click-modal="false"
    destroy-on-close
    class="fee-dialog"
  >
    <el-alert
      v-if="!isEdit"
      type="info"
      :closable="false"
      show-icon
      class="tip"
      title="Đổi giá bằng cách thêm mức mới với ngày áp dụng mới. Mức cũ được giữ lại làm lịch sử."
    />
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="Loại phí" prop="feeType" :error="serverErrors.feeType">
        <el-radio-group v-model="form.feeType">
          <el-radio-button v-for="t in feeTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="isMonthly" label="Giới tính" prop="gender" :error="serverErrors.gender">
        <el-radio-group v-model="form.gender">
          <el-radio v-for="g in genderOptions" :key="g.value" :value="g.value">{{ g.label }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <p v-else class="text-secondary guest-note">Phí khách áp dụng chung cho mọi khách, mỗi buổi chơi.</p>
      <el-form-item
        :label="isMonthly ? 'Số tiền mỗi tháng' : 'Số tiền mỗi buổi'"
        prop="amount"
        :error="serverErrors.amount"
      >
        <el-input-number
          v-model="form.amount"
          :min="0"
          :step="10000"
          :precision="0"
          controls-position="right"
          placeholder="VD: 300000"
          class="full-width"
        />
      </el-form-item>
      <el-form-item label="Áp dụng từ ngày" prop="effectiveFrom" :error="serverErrors.effectiveFrom">
        <el-date-picker
          v-model="form.effectiveFrom"
          type="date"
          format="DD/MM/YYYY"
          value-format="DD/MM/YYYY"
          placeholder="dd/mm/yyyy"
          class="full-width"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ isEdit ? 'Lưu thay đổi' : 'Thêm mức phí' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.tip {
  margin-bottom: 16px;
}

.guest-note {
  margin: -4px 0 16px;
}

:global(.fee-dialog) {
  max-width: calc(100vw - 32px);
}
</style>
