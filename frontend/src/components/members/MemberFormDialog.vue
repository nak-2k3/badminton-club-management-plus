<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { memberApi } from '@/api/members'
import { useCatalogStore } from '@/stores/catalog'
import { useAuthStore } from '@/stores/auth'
import { GENDER_LABELS, LEVEL_LABELS, ROLE_LABELS, toOptions } from '@/utils/labels'

// member = null: thêm mới; member = object: sửa
const props = defineProps({
  member: { type: Object, default: null }
})
const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['saved'])

const catalog = useCatalogStore()
const auth = useAuthStore()
const isEdit = computed(() => !!props.member)
const isSelf = computed(() => isEdit.value && props.member.id === auth.user?.id)

const formRef = ref()
const saving = ref(false)
const genderOptions = toOptions(GENDER_LABELS)

const emptyForm = () => ({
  fullName: '',
  email: '',
  password: '',
  phone: '',
  gender: '',
  birthDate: '',
  address: '',
  roleId: null,
  levelId: null,
  joinDate: ''
})
const form = reactive(emptyForm())
const serverErrors = reactive({})

const rules = computed(() => ({
  fullName: [{ required: true, message: 'Vui lòng nhập họ tên', trigger: 'blur' }],
  email: [
    { required: true, message: 'Vui lòng nhập email', trigger: 'blur' },
    { type: 'email', message: 'Email không hợp lệ', trigger: 'blur' }
  ],
  password: isEdit.value
    ? []
    : [
        { required: true, message: 'Vui lòng nhập mật khẩu', trigger: 'blur' },
        { min: 6, message: 'Mật khẩu tối thiểu 6 ký tự', trigger: 'blur' }
      ],
  phone: [
    {
      pattern: /^0\d{9,10}$/,
      message: 'Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số',
      trigger: 'blur'
    }
  ],
  gender: [{ required: true, message: 'Vui lòng chọn giới tính', trigger: 'change' }],
  roleId: [{ required: true, message: 'Vui lòng chọn vai trò', trigger: 'change' }],
  joinDate: isEdit.value ? [{ required: true, message: 'Vui lòng chọn ngày tham gia', trigger: 'change' }] : []
}))

// Ngày tham gia: không cho chọn ngày trong tương lai (backend @PastOrPresent)
const disableFuture = (date) => date.getTime() > Date.now()

// Ngày sinh: phải trước hôm nay (backend @Past), nên chặn cả hôm nay
const disableTodayAndFuture = (date) => {
  const startOfToday = new Date()
  startOfToday.setHours(0, 0, 0, 0)
  return date.getTime() >= startOfToday.getTime()
}

watch(visible, async (open) => {
  if (!open) return
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  Object.assign(form, emptyForm())
  if (props.member) {
    const m = props.member
    Object.assign(form, {
      fullName: m.fullName,
      email: m.email,
      phone: m.phone ?? '',
      gender: m.gender,
      birthDate: m.birthDate ?? '',
      address: m.address ?? '',
      roleId: m.roleId,
      levelId: m.levelId,
      joinDate: m.joinDate
    })
  }
  // Không cần clearValidate: dialog dùng destroy-on-close nên mỗi lần mở là một form mới, chưa có lỗi cũ
  try {
    await catalog.load()
    if (!isEdit.value && !form.roleId) {
      form.roleId = catalog.roles.find((r) => r.name === 'MEMBER')?.id ?? null
    }
  } catch (err) {
    ElMessage.error(err.message)
  }
})

async function handleSubmit() {
  Object.keys(serverErrors).forEach((key) => delete serverErrors[key])
  if (!(await formRef.value.validate().catch(() => false))) return

  const payload = {
    ...form,
    phone: form.phone || null,
    birthDate: form.birthDate || null,
    address: form.address || null,
    joinDate: form.joinDate || null
  }
  saving.value = true
  try {
    let saved
    if (isEdit.value) {
      delete payload.password
      saved = await memberApi.update(props.member.id, payload)
      ElMessage.success('Đã cập nhật thông tin thành viên')
    } else {
      saved = await memberApi.create(payload)
      ElMessage.success(`Đã thêm thành viên "${saved.fullName}"`)
    }
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
    :title="isEdit ? 'Sửa thông tin thành viên' : 'Thêm thành viên'"
    width="640px"
    :close-on-click-modal="false"
    destroy-on-close
    class="member-dialog"
  >
    <!-- rules đổi theo chế độ thêm/sửa: tắt validate-on-rule-change để form không tự báo lỗi khi vừa mở -->
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="Họ tên" prop="fullName" :error="serverErrors.fullName">
            <el-input v-model="form.fullName" maxlength="100" placeholder="Nguyễn Văn A" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="Email" prop="email" :error="serverErrors.email">
            <el-input v-model="form.email" maxlength="100" placeholder="email@example.com" />
          </el-form-item>
        </el-col>

        <el-col v-if="!isEdit" :xs="24" :sm="12">
          <el-form-item label="Mật khẩu" prop="password" :error="serverErrors.password">
            <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="Số điện thoại" prop="phone" :error="serverErrors.phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="0901234567" />
          </el-form-item>
        </el-col>

        <el-col :xs="24" :sm="12">
          <el-form-item label="Giới tính" prop="gender" :error="serverErrors.gender">
            <el-radio-group v-model="form.gender">
              <el-radio v-for="g in genderOptions" :key="g.value" :value="g.value">{{ g.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="Ngày sinh" prop="birthDate" :error="serverErrors.birthDate">
            <el-date-picker
              v-model="form.birthDate"
              type="date"
              format="DD/MM/YYYY"
              value-format="DD/MM/YYYY"
              placeholder="dd/mm/yyyy"
              :disabled-date="disableTodayAndFuture"
              class="full-width"
            />
          </el-form-item>
        </el-col>

        <el-col :xs="24" :sm="12">
          <el-form-item label="Vai trò" prop="roleId" :error="serverErrors.roleId">
            <el-select v-model="form.roleId" :disabled="isSelf" placeholder="Chọn vai trò" class="full-width">
              <el-option v-for="r in catalog.roles" :key="r.id" :value="r.id" :label="ROLE_LABELS[r.name] ?? r.name" />
            </el-select>
            <div v-if="isSelf" class="hint">Bạn không thể tự đổi vai trò của chính mình</div>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="Trình độ" prop="levelId" :error="serverErrors.levelId">
            <el-select v-model="form.levelId" clearable placeholder="Chưa xếp trình độ" class="full-width">
              <el-option v-for="l in catalog.levels" :key="l.id" :value="l.id" :label="LEVEL_LABELS[l.name] ?? l.name" />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :xs="24" :sm="12">
          <el-form-item label="Ngày tham gia" prop="joinDate" :error="serverErrors.joinDate">
            <el-date-picker
              v-model="form.joinDate"
              type="date"
              format="DD/MM/YYYY"
              value-format="DD/MM/YYYY"
              :placeholder="isEdit ? 'dd/mm/yyyy' : 'Mặc định: hôm nay'"
              :disabled-date="disableFuture"
              class="full-width"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Địa chỉ" prop="address" :error="serverErrors.address">
            <el-input v-model="form.address" maxlength="255" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ isEdit ? 'Lưu thay đổi' : 'Thêm thành viên' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}

:global(.member-dialog) {
  max-width: calc(100vw - 32px);
}
</style>
