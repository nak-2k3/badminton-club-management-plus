<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { accountApi } from '@/api/account'
import { useAuthStore } from '@/stores/auth'
import { GENDER_LABELS, LEVEL_LABELS, ROLE_LABELS, toOptions } from '@/utils/labels'
import { startOfToday } from '@/utils/format'

const auth = useAuthStore()
const genderOptions = toOptions(GENDER_LABELS)

const profile = ref(null)
const loading = ref(false)
const loadError = ref(null)

// ----- Thông tin cá nhân -----
const profileFormRef = ref()
const savingProfile = ref(false)
const profileForm = reactive({ fullName: '', phone: '', gender: '', birthDate: '', address: '' })
const profileErrors = reactive({})
const profileRules = {
  fullName: [{ required: true, message: 'Vui lòng nhập họ tên', trigger: 'blur' }],
  phone: [
    { pattern: /^0\d{9,10}$/, message: 'Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số', trigger: 'blur' }
  ],
  gender: [{ required: true, message: 'Vui lòng chọn giới tính', trigger: 'change' }]
}
// Ngày sinh phải trước hôm nay (backend @Past)
const disableTodayAndFuture = (date) => date.getTime() >= startOfToday().getTime()

function fillProfileForm(data) {
  Object.assign(profileForm, {
    fullName: data.fullName,
    phone: data.phone ?? '',
    gender: data.gender,
    birthDate: data.birthDate ?? '',
    address: data.address ?? ''
  })
}

async function loadProfile() {
  loading.value = true
  loadError.value = null
  try {
    profile.value = await accountApi.getProfile()
    fillProfileForm(profile.value)
  } catch (err) {
    loadError.value = err
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  Object.keys(profileErrors).forEach((key) => delete profileErrors[key])
  if (!(await profileFormRef.value.validate().catch(() => false))) return
  savingProfile.value = true
  try {
    profile.value = await accountApi.updateProfile({
      ...profileForm,
      phone: profileForm.phone || null,
      birthDate: profileForm.birthDate || null,
      address: profileForm.address || null
    })
    fillProfileForm(profile.value)
    auth.fetchMe().catch(() => {}) // cập nhật tên trên header
    ElMessage.success('Đã lưu thông tin cá nhân')
  } catch (err) {
    Object.assign(profileErrors, err.errors)
    ElMessage.error(err.message)
  } finally {
    savingProfile.value = false
  }
}

// ----- Đổi email đăng nhập (cần mật khẩu hiện tại) -----
const emailFormRef = ref()
const savingEmail = ref(false)
const emailForm = reactive({ newEmail: '', currentPassword: '' })
const emailErrors = reactive({})
const emailRules = {
  newEmail: [
    { required: true, message: 'Vui lòng nhập email mới', trigger: 'blur' },
    { type: 'email', message: 'Email không hợp lệ', trigger: 'blur' }
  ],
  currentPassword: [{ required: true, message: 'Vui lòng nhập mật khẩu hiện tại', trigger: 'blur' }]
}

async function changeEmail() {
  Object.keys(emailErrors).forEach((key) => delete emailErrors[key])
  if (!(await emailFormRef.value.validate().catch(() => false))) return
  savingEmail.value = true
  try {
    profile.value = await accountApi.changeEmail(emailForm.newEmail.trim(), emailForm.currentPassword)
    emailFormRef.value.resetFields()
    auth.fetchMe().catch(() => {}) // cập nhật email trên menu tài khoản
    ElMessage.success(`Đã đổi email đăng nhập thành ${profile.value.email}. Lần sau hãy đăng nhập bằng email mới.`)
  } catch (err) {
    Object.assign(emailErrors, err.errors)
    ElMessage.error(err.message)
  } finally {
    savingEmail.value = false
  }
}

// ----- Đổi mật khẩu -----
const passwordFormRef = ref()
const savingPassword = ref(false)
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const passwordErrors = reactive({})
const passwordRules = {
  currentPassword: [{ required: true, message: 'Vui lòng nhập mật khẩu hiện tại', trigger: 'blur' }],
  newPassword: [
    { required: true, message: 'Vui lòng nhập mật khẩu mới', trigger: 'blur' },
    { min: 6, message: 'Mật khẩu tối thiểu 6 ký tự', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: 'Vui lòng nhập lại mật khẩu mới', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        value === passwordForm.newPassword ? callback() : callback(new Error('Mật khẩu nhập lại không khớp')),
      trigger: 'blur'
    }
  ]
}

async function changePassword() {
  Object.keys(passwordErrors).forEach((key) => delete passwordErrors[key])
  if (!(await passwordFormRef.value.validate().catch(() => false))) return
  savingPassword.value = true
  try {
    await accountApi.changePassword(passwordForm.currentPassword, passwordForm.newPassword)
    passwordFormRef.value.resetFields()
    ElMessage.success('Đã đổi mật khẩu. Lần đăng nhập sau hãy dùng mật khẩu mới.')
  } catch (err) {
    Object.assign(passwordErrors, err.errors)
    ElMessage.error(err.message)
  } finally {
    savingPassword.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <div v-loading="loading" class="account-page">
    <el-result v-if="loadError" icon="error" title="Không tải được thông tin tài khoản" :sub-title="loadError.message">
      <template #extra>
        <el-button type="primary" @click="loadProfile">Thử lại</el-button>
      </template>
    </el-result>

    <template v-else-if="profile">
      <el-card shadow="never" class="summary">
        <div class="summary-main">
          <h2>{{ profile.fullName }}</h2>
          <p class="text-secondary">{{ profile.email }}</p>
        </div>
        <div class="summary-tags">
          <el-tag type="primary" effect="plain" round>{{ ROLE_LABELS[profile.roleName] ?? profile.roleName }}</el-tag>
          <el-tag v-if="profile.levelName" type="warning" effect="plain" round>
            {{ LEVEL_LABELS[profile.levelName] ?? profile.levelName }}
          </el-tag>
          <span class="text-secondary">Tham gia từ {{ profile.joinDate }}</span>
        </div>
      </el-card>

      <el-row :gutter="16">
        <el-col :xs="24" :lg="14">
          <el-card shadow="never" class="section">
            <template #header>Thông tin cá nhân</template>
            <el-form
              ref="profileFormRef"
              :model="profileForm"
              :rules="profileRules"
              label-position="top"
              @submit.prevent="saveProfile"
            >
              <el-row :gutter="16">
                <el-col :xs="24" :sm="12">
                  <el-form-item label="Họ tên" prop="fullName" :error="profileErrors.fullName">
                    <el-input v-model="profileForm.fullName" maxlength="100" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                  <el-form-item label="Email đăng nhập">
                    <el-input :model-value="profile.email" disabled />
                    <div class="text-secondary field-hint">Đổi email ở mục "Đổi email đăng nhập"</div>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                  <el-form-item label="Số điện thoại" prop="phone" :error="profileErrors.phone">
                    <el-input v-model="profileForm.phone" maxlength="11" placeholder="0901234567" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12">
                  <el-form-item label="Ngày sinh" prop="birthDate" :error="profileErrors.birthDate">
                    <el-date-picker
                      v-model="profileForm.birthDate"
                      type="date"
                      format="DD/MM/YYYY"
                      value-format="DD/MM/YYYY"
                      placeholder="dd/mm/yyyy"
                      :disabled-date="disableTodayAndFuture"
                      class="full-width"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="Giới tính" prop="gender" :error="profileErrors.gender">
                    <el-radio-group v-model="profileForm.gender">
                      <el-radio v-for="g in genderOptions" :key="g.value" :value="g.value">{{ g.label }}</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="Địa chỉ" prop="address" :error="profileErrors.address">
                    <el-input v-model="profileForm.address" maxlength="255" />
                  </el-form-item>
                </el-col>
              </el-row>
              <p class="text-secondary hint">Vai trò, trình độ và ngày tham gia do quản trị viên quản lý.</p>
              <el-button type="primary" native-type="submit" :loading="savingProfile">Lưu thông tin</el-button>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="10">
          <el-card shadow="never" class="section">
            <template #header>Đổi email đăng nhập</template>
            <el-form
              ref="emailFormRef"
              :model="emailForm"
              :rules="emailRules"
              label-position="top"
              @submit.prevent="changeEmail"
            >
              <el-form-item label="Email mới" prop="newEmail" :error="emailErrors.newEmail">
                <el-input v-model.trim="emailForm.newEmail" maxlength="100" placeholder="email@example.com" autocomplete="email" />
              </el-form-item>
              <el-form-item label="Mật khẩu hiện tại (để xác nhận)" prop="currentPassword" :error="emailErrors.currentPassword">
                <el-input
                  v-model="emailForm.currentPassword"
                  type="password"
                  show-password
                  autocomplete="current-password"
                />
              </el-form-item>
              <el-button type="primary" native-type="submit" :loading="savingEmail">Đổi email</el-button>
            </el-form>
          </el-card>

          <el-card shadow="never" class="section">
            <template #header>Đổi mật khẩu</template>
            <el-form
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-position="top"
              @submit.prevent="changePassword"
            >
              <el-form-item label="Mật khẩu hiện tại" prop="currentPassword" :error="passwordErrors.currentPassword">
                <el-input
                  v-model="passwordForm.currentPassword"
                  type="password"
                  show-password
                  autocomplete="current-password"
                />
              </el-form-item>
              <el-form-item label="Mật khẩu mới" prop="newPassword" :error="passwordErrors.newPassword">
                <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <el-form-item label="Nhập lại mật khẩu mới" prop="confirmPassword">
                <el-input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  show-password
                  autocomplete="new-password"
                />
              </el-form-item>
              <el-button type="primary" native-type="submit" :loading="savingPassword">Đổi mật khẩu</el-button>
            </el-form>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.account-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}

.summary {
  border-left: 4px solid var(--el-color-primary);
}

.summary :deep(.el-card__body) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.summary h2 {
  margin: 0 0 2px;
  font-size: 22px;
}

.summary p {
  margin: 0;
  font-size: 13px;
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.section {
  margin-bottom: 16px;
}

.full-width {
  width: 100%;
}

.hint {
  margin: 0 0 12px;
}

.field-hint {
  line-height: 1.4;
  margin-top: 4px;
}
</style>
