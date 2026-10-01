<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { House, ArrowDown, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ROLE_LABELS } from '@/utils/labels'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

// Menu bên trái; mục có roles chỉ hiện với các vai trò đó
const menuItems = [
  { path: '/', title: 'Trang chủ', icon: House },
  { path: '/members', title: 'Thành viên', icon: UserFilled, roles: ['ADMIN'] }
]

// Trang con (vd chi tiết thành viên) khai báo meta.menu để menu cha vẫn được tô sáng
const activeMenu = computed(() => route.meta.menu ?? route.path)

const visibleMenu = computed(() =>
  menuItems.filter((item) => !item.roles || auth.hasRole(...item.roles))
)

async function handleLogout() {
  try {
    await ElMessageBox.confirm('Bạn có chắc muốn đăng xuất?', 'Đăng xuất', {
      confirmButtonText: 'Đăng xuất',
      cancelButtonText: 'Hủy',
      type: 'warning'
    })
  } catch {
    return
  }
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">🏸 CLB Cầu Lông</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="item in visibleMenu" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="page-title">{{ route.meta.title }}</span>
        <el-dropdown trigger="click">
          <span class="user-info">
            <el-avatar :size="32" :src="auth.user?.avatar || undefined">
              {{ auth.user?.fullName?.charAt(0) }}
            </el-avatar>
            <span class="user-name">{{ auth.user?.fullName }}</span>
            <el-tag size="small" effect="plain">{{ ROLE_LABELS[auth.role] ?? auth.role }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :icon="SwitchButton" @click="handleLogout">Đăng xuất</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #fff;
  border-right: 1px solid #e4e7ed;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 18px;
  color: #1d6f42;
  border-bottom: 1px solid #e4e7ed;
}

.menu {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

@media (max-width: 600px) {
  .user-name {
    display: none;
  }
}
</style>
