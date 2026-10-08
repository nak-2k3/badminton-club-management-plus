import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true, title: 'Đăng nhập' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: 'Trang chủ' }
      },
      {
        path: 'schedules',
        name: 'schedules',
        component: () => import('@/views/schedules/ScheduleListView.vue'),
        meta: { title: 'Lịch chơi' }
      },
      {
        path: 'schedules/:id(\\d+)',
        name: 'schedule-detail',
        component: () => import('@/views/schedules/ScheduleDetailView.vue'),
        meta: { title: 'Chi tiết buổi chơi', menu: '/schedules' }
      },
      {
        path: 'members',
        name: 'members',
        component: () => import('@/views/members/MemberListView.vue'),
        meta: { title: 'Quản lý thành viên', roles: ['ADMIN'] }
      },
      {
        path: 'members/:id(\\d+)',
        name: 'member-detail',
        component: () => import('@/views/members/MemberDetailView.vue'),
        meta: { title: 'Chi tiết thành viên', roles: ['ADMIN'], menu: '/members' }
      },
      {
        path: 'courts',
        name: 'courts',
        component: () => import('@/views/courts/CourtListView.vue'),
        meta: { title: 'Quản lý sân', roles: ['ADMIN'] }
      },
      {
        path: 'fees',
        name: 'fees',
        component: () => import('@/views/fees/FeeSettingView.vue'),
        meta: { title: 'Mức phí', roles: ['ADMIN', 'TREASURER'] }
      },
      {
        path: 'guest-fees',
        name: 'guest-fees',
        component: () => import('@/views/guests/GuestFeeListView.vue'),
        meta: { title: 'Phí khách', roles: ['ADMIN', 'TREASURER'] }
      },
      {
        path: 'account',
        name: 'account',
        component: () => import('@/views/account/AccountView.vue'),
        meta: { title: 'Tài khoản của tôi' }
      }
      // Thêm trang mới ở đây; giới hạn quyền bằng meta: { roles: ['ADMIN', 'TREASURER'] }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: 'Không tìm thấy trang' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // Tải lại trang: có token trong localStorage nhưng chưa có user
  if (auth.isAuthenticated && !auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      auth.logout()
      return to.meta.requiresAuth ? { name: 'login', query: { redirect: to.fullPath } } : true
    }
  }

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return { name: 'dashboard' }
  }

  const roles = to.matched.flatMap((record) => record.meta.roles ?? [])
  if (roles.length && !auth.hasRole(...roles)) {
    return { name: 'dashboard' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} | CLB Cầu Lông` : 'CLB Cầu Lông'
})

export default router
