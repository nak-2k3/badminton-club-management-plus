<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { courtApi } from '@/api/courts'

// court = null: thêm mới; court = object: sửa
const props = defineProps({
  court: { type: Object, default: null }
})
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const isEdit = computed(() => !!props.court)
const formRef = ref()
const saving = ref(false)
const emptyForm = () => ({ courtName: '', address: '', hourlyRate: null, phone: '', note: '' })
const form = reactive(emptyForm())
const serverErrors = reactive({})

const rules = {
  courtName: [{ required: true, message: 'Vui lòng nhập tên sân', trigger: 'blur' }],
  address: [{ required: true, message: 'Vui lòng nhập địa chỉ', trigger: 'blur' }],
  hourlyRate: [{ required: true, message: 'Vui lòng nhập giá thuê', trigger: 'blur' }],
  phone: [
    { pattern: /^0\d{9,10}$/, message: 'Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số', trigger: 'blur' }
  ]
}

watch(visible, (open) => {
  if (!open) return
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  Object.assign(form, emptyForm())
  if (props.court) {
    const c = props.court
    Object.assign(form, {
      courtName: c.courtName,
      address: c.address,
      hourlyRate: Number(c.hourlyRate),
      phone: c.phone ?? '',
      note: c.note ?? ''
    })
  }
})

async function handleSubmit() {
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  if (!(await formRef.value.validate().catch(() => false))) return
  const payload = { ...form, phone: form.phone || null, note: form.note || null }
  saving.value = true
  try {
    const saved = isEdit.value
      ? await courtApi.update(props.court.id, payload)
      : await courtApi.create(payload)
    ElMessage.success(isEdit.value ? 'Đã cập nhật thông tin sân' : `Đã thêm sân "${saved.courtName}"`)
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
    :title="isEdit ? 'Sửa thông tin sân' : 'Thêm sân'"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
    class="court-dialog"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="Tên sân" prop="courtName" :error="serverErrors.courtName">
        <el-input v-model="form.courtName" maxlength="100" placeholder="VD: Nhà thi đấu Ninh Kiều" />
      </el-form-item>
      <el-form-item label="Địa chỉ" prop="address" :error="serverErrors.address">
        <el-input v-model="form.address" maxlength="255" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="Giá thuê (1 sân / 1 giờ)" prop="hourlyRate" :error="serverErrors.hourlyRate">
            <el-input-number
              v-model="form.hourlyRate"
              :min="0"
              :step="5000"
              :precision="0"
              controls-position="right"
              placeholder="VD: 60000"
              class="full-width"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="Số điện thoại liên hệ" prop="phone" :error="serverErrors.phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="0901234567" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Ghi chú" prop="note" :error="serverErrors.note">
        <el-input v-model="form.note" type="textarea" :rows="2" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ isEdit ? 'Lưu thay đổi' : 'Thêm sân' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

:global(.court-dialog) {
  max-width: calc(100vw - 32px);
}
</style>
