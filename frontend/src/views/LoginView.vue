<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import ThemeToggle from '@/components/common/ThemeToggle.vue'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref()
const loading = ref(false)
const form = reactive({ email: '', password: '' })
// Lỗi theo từng trường do backend trả về (ErrorResponse.errors)
const serverErrors = reactive({ email: '', password: '' })

const rules = {
  email: [
    { required: true, message: 'Vui lòng nhập email', trigger: 'blur' },
    { type: 'email', message: 'Email không hợp lệ', trigger: 'blur' }
  ],
  password: [{ required: true, message: 'Vui lòng nhập mật khẩu', trigger: 'blur' }]
}

async function handleLogin() {
  serverErrors.email = ''
  serverErrors.password = ''
  if (!(await formRef.value.validate().catch(() => false))) return

  loading.value = true
  try {
    await auth.login({ email: form.email.trim(), password: form.password })
    ElMessage.success(`Xin chào, ${auth.user.fullName}`)
    router.replace(route.query.redirect || { name: 'dashboard' })
  } catch (err) {
    Object.assign(serverErrors, err.errors)
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="theme-corner"><ThemeToggle /></div>
    <el-card class="login-card" shadow="always">
      <div class="login-header">
        <div class="logo">🏸</div>
        <h1>CLB Cầu Lông</h1>
        <p>Đăng nhập hệ thống quản lý</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="Email" prop="email" :error="serverErrors.email">
          <el-input
            v-model="form.email"
            :prefix-icon="User"
            placeholder="email@example.com"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="Mật khẩu" prop="password" :error="serverErrors.password">
          <el-input
            v-model="form.password"
            type="password"
            :prefix-icon="Lock"
            placeholder="Nhập mật khẩu"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>

        <el-button type="primary" native-type="submit" :loading="loading" class="login-button">
          Đăng nhập
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  box-sizing: border-box;
  background: var(--app-login-gradient);
  position: relative;
}

.theme-corner {
  position: absolute;
  top: 16px;
  right: 16px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  border-radius: 12px;
  animation: card-in 0.4s ease;
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
}

.login-header {
  text-align: center;
  margin-bottom: 8px;
}

.logo {
  font-size: 44px;
}

.login-header h1 {
  margin: 8px 0 4px;
  font-size: 24px;
}

.login-header p {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
}

@media (prefers-reduced-motion: reduce) {
  .login-card {
    animation: none;
  }
}

.login-button {
  width: 100%;
  margin-top: 8px;
}
</style>
