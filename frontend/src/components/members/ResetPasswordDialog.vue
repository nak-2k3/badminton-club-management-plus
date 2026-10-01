<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { memberApi } from '@/api/members'

const props = defineProps({
  member: { type: Object, default: null }
})
const visible = defineModel({ type: Boolean, default: false })

const formRef = ref()
const saving = ref(false)
const form = reactive({ newPassword: '', confirmPassword: '' })
const serverErrors = reactive({ newPassword: '' })

const rules = {
  newPassword: [
    { required: true, message: 'Vui lòng nhập mật khẩu mới', trigger: 'blur' },
    { min: 6, message: 'Mật khẩu tối thiểu 6 ký tự', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: 'Vui lòng nhập lại mật khẩu', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        value === form.newPassword ? callback() : callback(new Error('Mật khẩu nhập lại không khớp')),
      trigger: 'blur'
    }
  ]
}

watch(visible, (open) => {
  if (!open) return
  form.newPassword = ''
  form.confirmPassword = ''
  serverErrors.newPassword = ''
  formRef.value?.clearValidate()
})

async function handleSubmit() {
  serverErrors.newPassword = ''
  if (!(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    await memberApi.resetPassword(props.member.id, form.newPassword)
    ElMessage.success(`Đã đặt lại mật khẩu cho "${props.member.fullName}"`)
    visible.value = false
  } catch (err) {
    serverErrors.newPassword = err.errors?.newPassword ?? ''
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="Đặt lại mật khẩu" width="420px" :close-on-click-modal="false">
    <p class="desc">
      Đặt mật khẩu mới cho <strong>{{ member?.fullName }}</strong> ({{ member?.email }}).
    </p>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSubmit">
      <el-form-item label="Mật khẩu mới" prop="newPassword" :error="serverErrors.newPassword">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="Nhập lại mật khẩu" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">Đặt lại</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.desc {
  margin-top: 0;
  color: #606266;
}
</style>
