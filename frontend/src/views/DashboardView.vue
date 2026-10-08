<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled, CircleCheck, Lock, CircleClose, ArrowRight } from '@element-plus/icons-vue'
import { memberApi } from '@/api/members'
import { scheduleApi } from '@/api/schedules'
import MyPaymentsCard from '@/components/payments/MyPaymentsCard.vue'
import { useAuthStore } from '@/stores/auth'
import {
  ROLE_LABELS,
  GENDER_LABELS,
  SCHEDULE_STATUS_LABELS,
  SCHEDULE_STATUS_TAG_TYPES
} from '@/utils/labels'
import { todayText, weekdayOf } from '@/utils/format'

const auth = useAuthStore()
const router = useRouter()

const isAdmin = computed(() => auth.hasRole('ADMIN'))
const today = new Date().toLocaleDateString('vi-VN', {
  weekday: 'long',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric'
})

// Thống kê thành viên (chỉ ADMIN): dùng lại API danh sách với size=1, lấy totalElements
const STAT_CARDS = [
  { key: 'all', label: 'Tổng thành viên', icon: UserFilled, tone: 'primary', status: null },
  { key: 'ACTIVE', label: 'Đang hoạt động', icon: CircleCheck, tone: 'success', status: 'ACTIVE' },
  { key: 'LOCKED', label: 'Bị khóa', icon: Lock, tone: 'danger', status: 'LOCKED' },
  { key: 'INACTIVE', label: 'Ngừng hoạt động', icon: CircleClose, tone: 'info', status: 'INACTIVE' }
]
const stats = ref({})
const statsLoading = ref(false)
const statsError = ref(false)

async function loadStats() {
  statsLoading.value = true
  statsError.value = false
  try {
    const results = await Promise.all(
      STAT_CARDS.map((card) => memberApi.search({ size: 1, ...(card.status && { status: card.status }) }))
    )
    stats.value = Object.fromEntries(STAT_CARDS.map((card, i) => [card.key, results[i].totalElements]))
  } catch {
    statsError.value = true
  } finally {
    statsLoading.value = false
  }
}

// Buổi sắp tới tôi đã đăng ký (mọi vai trò); hiện cả buổi bị hủy để thành viên biết
const UPCOMING_SIZE = 5
const upcoming = ref([])
const upcomingTotal = ref(0)
const upcomingLoading = ref(false)
const upcomingError = ref(false)

async function loadUpcoming() {
  upcomingLoading.value = true
  upcomingError.value = false
  try {
    const res = await scheduleApi.search({ mine: true, from: todayText(), size: UPCOMING_SIZE })
    upcoming.value = res.content
    upcomingTotal.value = res.totalElements
  } catch {
    upcomingError.value = true
  } finally {
    upcomingLoading.value = false
  }
}

function openSchedule(schedule) {
  router.push({ name: 'schedule-detail', params: { id: schedule.id } })
}

function openMembers(card) {
  router.push({ name: 'members', query: card.status ? { status: card.status } : {} })
}

onMounted(() => {
  loadUpcoming()
  if (isAdmin.value) loadStats()
})
</script>

<template>
  <div class="dashboard">
    <el-card shadow="never" class="welcome">
      <div class="welcome-text">
        <h2>Xin chào, {{ auth.user?.fullName }} 👋</h2>
        <p class="text-secondary">{{ today }}</p>
      </div>
      <el-tag effect="plain" round size="large">{{ ROLE_LABELS[auth.role] ?? auth.role }}</el-tag>
    </el-card>

    <el-card shadow="never" class="upcoming">
      <template #header>
        <div class="card-header">
          <span>Buổi sắp tới của tôi</span>
          <el-button link type="primary" @click="router.push({ name: 'schedules' })">
            Xem lịch chơi <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </template>
      <el-skeleton v-if="upcomingLoading" :rows="2" animated />
      <el-result v-else-if="upcomingError" icon="error" title="Không tải được lịch của bạn">
        <template #extra><el-button @click="loadUpcoming">Thử lại</el-button></template>
      </el-result>
      <el-empty v-else-if="!upcoming.length" :image-size="72" description="Bạn chưa đăng ký buổi chơi nào sắp tới">
        <el-button type="primary" @click="router.push({ name: 'schedules' })">Tìm buổi để đăng ký</el-button>
      </el-empty>
      <template v-else>
        <button
          v-for="item in upcoming"
          :key="item.id"
          type="button"
          class="upcoming-item"
          :class="{ cancelled: item.status === 'CANCELLED' }"
          @click="openSchedule(item)"
        >
          <span class="upcoming-date">
            <span class="weekday">{{ weekdayOf(item.playDate) }}</span>
            <span class="day">{{ item.playDate.slice(0, 5) }}</span>
          </span>
          <span class="upcoming-body">
            <span class="upcoming-title">{{ item.title }}</span>
            <span class="text-secondary">{{ item.startTime }} – {{ item.endTime }} · {{ item.courtName }}</span>
          </span>
          <el-tag :type="SCHEDULE_STATUS_TAG_TYPES[item.status]" size="small" round disable-transitions>
            {{ SCHEDULE_STATUS_LABELS[item.status] }}
          </el-tag>
        </button>
        <el-button
          v-if="upcomingTotal > UPCOMING_SIZE"
          link
          type="primary"
          class="more"
          @click="router.push({ name: 'schedules', query: { mine: 'true', from: todayText() } })"
        >
          Xem tất cả {{ upcomingTotal }} buổi
        </el-button>
      </template>
    </el-card>

    <MyPaymentsCard />

    <template v-if="isAdmin">
      <div class="section-title">
        <span>Thống kê thành viên</span>
        <el-button v-if="statsError" link type="primary" @click="loadStats">Tải lại</el-button>
      </div>
      <div class="stats">
        <button
          v-for="card in STAT_CARDS"
          :key="card.key"
          type="button"
          class="stat-card"
          :class="`tone-${card.tone}`"
          @click="openMembers(card)"
        >
          <span class="stat-icon"><el-icon :size="22"><component :is="card.icon" /></el-icon></span>
          <span class="stat-body">
            <span class="stat-label">{{ card.label }}</span>
            <el-skeleton v-if="statsLoading" :rows="0" animated class="stat-skeleton">
              <template #template><el-skeleton-item variant="h3" style="width: 48px" /></template>
            </el-skeleton>
            <span v-else class="stat-value">{{ statsError ? '—' : stats[card.key] ?? 0 }}</span>
          </span>
          <el-icon class="stat-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </template>

    <el-card shadow="never">
      <template #header>Thông tin tài khoản</template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="Họ tên">{{ auth.user?.fullName }}</el-descriptions-item>
        <el-descriptions-item label="Email">{{ auth.user?.email }}</el-descriptions-item>
        <el-descriptions-item label="Giới tính">{{ GENDER_LABELS[auth.user?.gender] }}</el-descriptions-item>
        <el-descriptions-item label="Vai trò">{{ ROLE_LABELS[auth.role] ?? auth.role }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.welcome h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.welcome p {
  margin: 0;
  font-size: 13px;
  text-transform: capitalize;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: -4px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.stat-card {
  --tone: var(--el-color-primary);
  --tone-bg: var(--el-color-primary-light-9);
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  border-color: var(--tone);
  box-shadow: var(--el-box-shadow-light);
}

.stat-card:focus-visible {
  outline: 2px solid var(--tone);
  outline-offset: 2px;
}

.tone-success {
  --tone: var(--el-color-success);
  --tone-bg: var(--el-color-success-light-9);
}

.tone-danger {
  --tone: var(--el-color-danger);
  --tone-bg: var(--el-color-danger-light-9);
}

.tone-info {
  --tone: var(--el-color-info);
  --tone-bg: var(--el-color-info-light-9);
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--tone-bg);
  color: var(--tone);
}

.stat-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  font-size: 26px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

.stat-skeleton {
  height: 28px;
}

.stat-arrow {
  color: var(--el-text-color-placeholder);
  transition: transform 0.2s, color 0.2s;
}

.stat-card:hover .stat-arrow {
  color: var(--tone);
  transform: translateX(3px);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.upcoming-item {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 10px 8px;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: none;
  color: var(--el-text-color-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  border-radius: 6px;
  transition: background-color 0.2s;
}

.upcoming-item:last-of-type {
  border-bottom: none;
}

.upcoming-item:hover {
  background: var(--el-fill-color-light);
}

.upcoming-item:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}

.upcoming-item.cancelled .upcoming-title {
  text-decoration: line-through;
  color: var(--el-text-color-secondary);
}

.upcoming-date {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 56px;
  flex-shrink: 0;
  padding: 4px 0;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.upcoming-date .weekday {
  font-size: 11px;
}

.upcoming-date .day {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.upcoming-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.upcoming-title {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.more {
  margin-top: 8px;
}

@media (prefers-reduced-motion: reduce) {
  .upcoming-item,
  .stat-card,
  .stat-arrow {
    transition: none;
  }
}
</style>
