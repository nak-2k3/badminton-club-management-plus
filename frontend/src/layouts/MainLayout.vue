<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { House, ArrowDown, SwitchButton, UserFilled, User, Fold, Expand } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useBreakpoint } from '@/composables/useBreakpoint'
import ThemeToggle from '@/components/common/ThemeToggle.vue'
import { ROLE_LABELS } from '@/utils/labels'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const { isSmall } = useBreakpoint(768)

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

// Thu gọn menu: nhớ lựa chọn trên máy tính, luôn thu gọn trên màn hình nhỏ
const COLLAPSE_KEY = 'sidebarCollapsed'
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')
watch(isSmall, (small) => small && (collapsed.value = true), { immediate: true })

function toggleSidebar() {
  collapsed.value = !collapsed.value
  if (!isSmall.value) localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
}

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
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="brand" :class="{ collapsed }">
        <span class="brand-logo">🏸</span>
        <transition name="fade">
          <span v-if="!collapsed" class="brand-name">CLB Cầu Lông</span>
        </transition>
      </div>
      <el-menu :default-active="activeMenu" :collapse="collapsed" router class="menu">
        <el-menu-item v-for="item in visibleMenu" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="main-area">
      <el-header class="header">
        <div class="header-left">
          <el-button
            text
            class="collapse-btn"
            :aria-label="collapsed ? 'Mở rộng menu' : 'Thu gọn menu'"
            @click="toggleSidebar"
          >
            <el-icon :size="18"><Expand v-if="collapsed" /><Fold v-else /></el-icon>
          </el-button>
          <h1 class="page-title">{{ route.meta.title }}</h1>
        </div>

        <div class="header-right">
          <ThemeToggle />
          <el-dropdown trigger="click">
            <button type="button" class="user-info" :aria-label="`Tài khoản: ${auth.user?.fullName ?? ''}`">
              <!-- Màn hình nhỏ: chỉ hiện biểu tượng, tên và vai trò xem trong menu thả xuống -->
              <el-icon v-if="isSmall" :size="18"><User /></el-icon>
              <template v-else>
                <span class="user-name">{{ auth.user?.fullName }}</span>
                <el-tag size="small" effect="plain" round disable-transitions>
                  {{ ROLE_LABELS[auth.role] ?? auth.role }}
                </el-tag>
              </template>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled class="user-summary">
                  <div>
                    <div class="summary-name">{{ auth.user?.fullName }}</div>
                    <div class="text-secondary">{{ auth.user?.email }}</div>
                    <div class="text-secondary">{{ ROLE_LABELS[auth.role] ?? auth.role }}</div>
                  </div>
                </el-dropdown-item>
                <el-dropdown-item :icon="SwitchButton" divided @click="handleLogout">Đăng xuất</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <!-- key theo tên route: đổi query (lọc, phân trang) không tạo lại trang -->
        <router-view v-slot="{ Component, route: viewRoute }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="viewRoute.name" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-light);
  transition: width 0.25s ease, background-color 0.3s;
  overflow-x: hidden;
}

.brand {
  height: var(--app-header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-weight: 600;
  font-size: 18px;
  color: var(--el-color-primary);
  border-bottom: 1px solid var(--el-border-color-light);
  white-space: nowrap;
}

.brand-logo {
  font-size: 22px;
}

.menu {
  border-right: none;
}

.menu:not(.el-menu--collapse) {
  width: 220px;
}

.main-area {
  min-width: 0;
}

.header {
  height: var(--app-header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
  transition: background-color 0.3s;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-left {
  flex: 1;
  min-width: 0;
}

.header-right {
  flex-shrink: 0;
}

/* Mục thông tin tài khoản trong menu thả xuống: không làm mờ như mục disabled thường */
:deep(.user-summary.is-disabled) {
  cursor: default;
  color: var(--el-text-color-primary);
  line-height: 1.5;
  padding-top: 8px;
  padding-bottom: 8px;
}

.summary-name {
  font-weight: 600;
}

.collapse-btn {
  padding: 8px;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  cursor: pointer;
  transition: background-color 0.2s;
}

.user-info:hover {
  background: var(--el-fill-color-light);
}

.user-name {
  font-weight: 500;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow {
  color: var(--el-text-color-secondary);
}

.main {
  padding: 20px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .header {
    padding: 0 8px;
  }

  .main {
    padding: 12px;
  }

  .page-title {
    font-size: 16px;
  }
}
</style>
