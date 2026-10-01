<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Edit, Key, Lock, Unlock, CircleClose } from '@element-plus/icons-vue'
import { memberApi } from '@/api/members'
import { useAuthStore } from '@/stores/auth'
import { useMemberActions } from '@/composables/useMemberActions'
import { useBreakpoint } from '@/composables/useBreakpoint'
import MemberFormDialog from '@/components/members/MemberFormDialog.vue'
import ResetPasswordDialog from '@/components/members/ResetPasswordDialog.vue'
import {
  GENDER_LABELS,
  LEVEL_LABELS,
  ROLE_LABELS,
  USER_STATUS_LABELS,
  USER_STATUS_TAG_TYPES
} from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { changeStatus } = useMemberActions()
const { isSmall } = useBreakpoint(768)

const member = ref(null)
const loading = ref(false)
const error = ref(null)
const formVisible = ref(false)
const passwordVisible = ref(false)

const isSelf = computed(() => member.value?.id === auth.user?.id)

async function fetchMember() {
  loading.value = true
  error.value = null
  try {
    member.value = await memberApi.getById(route.params.id)
  } catch (err) {
    member.value = null
    error.value = err
  } finally {
    loading.value = false
  }
}

async function handleStatus(status) {
  const updated = await changeStatus(member.value, status)
  if (updated) member.value = updated
}

function handleSaved(saved) {
  member.value = saved
  // Admin tự sửa thông tin của mình -> cập nhật tên trên header (lỗi thì giữ tên cũ, không ảnh hưởng trang)
  if (isSelf.value) auth.fetchMe().catch(() => {})
}

function goBack() {
  // Quay lại danh sách (giữ bộ lọc) nếu đi từ danh sách; mở link trực tiếp thì về danh sách mặc định
  if (window.history.state?.back) router.back()
  else router.push({ name: 'members' })
}

watch(() => route.params.id, (id) => id && fetchMember())
onMounted(fetchMember)
</script>

<template>
  <div v-loading="loading" class="detail-page">
    <el-button :icon="ArrowLeft" text class="back" @click="goBack">Danh sách thành viên</el-button>

    <el-result
      v-if="error"
      :icon="error.status === 404 ? 'warning' : 'error'"
      :title="error.status === 404 ? 'Không tìm thấy thành viên' : 'Không tải được dữ liệu'"
      :sub-title="error.message"
    >
      <template #extra>
        <el-button type="primary" @click="router.push({ name: 'members' })">Về danh sách</el-button>
      </template>
    </el-result>

    <template v-else-if="member">
      <el-card shadow="never" class="profile">
        <div class="profile-main">
          <div class="profile-info">
            <h2>{{ member.fullName }}</h2>
            <p class="text-secondary">{{ member.email }}</p>
            <div class="tags">
              <el-tag :type="USER_STATUS_TAG_TYPES[member.status]">{{ USER_STATUS_LABELS[member.status] }}</el-tag>
              <el-tag type="primary" effect="plain">{{ ROLE_LABELS[member.roleName] ?? member.roleName }}</el-tag>
              <el-tag v-if="member.levelName" type="warning" effect="plain">
                {{ LEVEL_LABELS[member.levelName] ?? member.levelName }}
              </el-tag>
            </div>
          </div>
        </div>
        <div class="actions">
          <el-button type="primary" :icon="Edit" @click="formVisible = true">Sửa thông tin</el-button>
          <el-button :icon="Key" @click="passwordVisible = true">Đặt lại mật khẩu</el-button>
          <template v-if="!isSelf">
            <el-button v-if="member.status !== 'ACTIVE'" type="success" plain :icon="Unlock" @click="handleStatus('ACTIVE')">
              Kích hoạt
            </el-button>
            <el-button v-if="member.status !== 'LOCKED'" type="danger" plain :icon="Lock" @click="handleStatus('LOCKED')">
              Khóa tài khoản
            </el-button>
            <el-button v-if="member.status !== 'INACTIVE'" plain :icon="CircleClose" @click="handleStatus('INACTIVE')">
              Ngừng hoạt động
            </el-button>
          </template>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>Thông tin chi tiết</template>
        <el-descriptions :column="isSmall ? 1 : 2" border class="info">
          <el-descriptions-item label="Họ tên">{{ member.fullName }}</el-descriptions-item>
          <el-descriptions-item label="Email">{{ member.email }}</el-descriptions-item>
          <el-descriptions-item label="Số điện thoại">{{ member.phone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Giới tính">{{ GENDER_LABELS[member.gender] }}</el-descriptions-item>
          <el-descriptions-item label="Ngày sinh">{{ member.birthDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Trình độ">
            {{ LEVEL_LABELS[member.levelName] ?? member.levelName ?? 'Chưa xếp' }}
          </el-descriptions-item>
          <el-descriptions-item label="Vai trò">{{ ROLE_LABELS[member.roleName] ?? member.roleName }}</el-descriptions-item>
          <el-descriptions-item label="Trạng thái">{{ USER_STATUS_LABELS[member.status] }}</el-descriptions-item>
          <el-descriptions-item label="Ngày tham gia">{{ member.joinDate }}</el-descriptions-item>
          <el-descriptions-item label="Ngày tạo tài khoản">{{ member.createdAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Địa chỉ" :span="isSmall ? 1 : 2">{{ member.address || '—' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </template>

    <MemberFormDialog v-model="formVisible" :member="member" @saved="handleSaved" />
    <ResetPasswordDialog v-model="passwordVisible" :member="member" />
  </div>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}

.back {
  align-self: flex-start;
  padding-left: 0;
}

.profile :deep(.el-card__body) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.profile {
  border-left: 4px solid var(--el-color-primary);
}

.profile-main {
  min-width: 0;
}

.profile-info h2 {
  margin: 0 0 2px;
  font-size: 22px;
}

.profile-info p {
  margin: 0 0 10px;
  font-size: 13px;
}

.tags,
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.actions .el-button {
  margin-left: 0;
}

@media (max-width: 768px) {
  .actions {
    width: 100%;
  }
}
</style>
