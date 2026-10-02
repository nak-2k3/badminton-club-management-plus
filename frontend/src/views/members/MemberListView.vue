<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search, View, Edit, MoreFilled, Setting } from '@element-plus/icons-vue'
import { memberApi } from '@/api/members'
import { useCatalogStore } from '@/stores/catalog'
import { useAuthStore } from '@/stores/auth'
import { useMemberActions } from '@/composables/useMemberActions'
import MemberFormDialog from '@/components/members/MemberFormDialog.vue'
import ResetPasswordDialog from '@/components/members/ResetPasswordDialog.vue'
import {
  GENDER_LABELS,
  LEVEL_LABELS,
  ROLE_LABELS,
  USER_STATUS_LABELS,
  USER_STATUS_TAG_TYPES,
  toOptions
} from '@/utils/labels'

const route = useRoute()
const router = useRouter()
const catalog = useCatalogStore()
const auth = useAuthStore()
const { changeStatus } = useMemberActions()

const statusOptions = toOptions(USER_STATUS_LABELS)
const genderOptions = toOptions(GENDER_LABELS)

// Bộ lọc được đồng bộ lên URL để quay lại từ trang chi tiết vẫn giữ nguyên
const filters = reactive({
  keyword: route.query.keyword ?? '',
  status: route.query.status ?? '',
  roleId: route.query.roleId ? Number(route.query.roleId) : '',
  levelId: route.query.levelId ? Number(route.query.levelId) : '',
  gender: route.query.gender ?? ''
})
const pagination = reactive({
  page: Number(route.query.page) || 1, // giao diện đếm từ 1, API đếm từ 0
  size: Number(route.query.size) || 10,
  total: 0
})
const sorting = reactive({
  sort: route.query.sort ?? 'createdAt',
  direction: route.query.direction ?? 'desc'
})

const members = ref([])
const loading = ref(false)
// Mỗi lần gọi API tăng số thứ tự; response về muộn của request cũ sẽ bị bỏ qua
let latestRequestId = 0

const formVisible = ref(false)
const editingMember = ref(null)
const passwordVisible = ref(false)
const passwordMember = ref(null)

async function fetchMembers() {
  const requestId = ++latestRequestId
  loading.value = true
  const params = {
    ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v !== '' && v !== null)),
    page: pagination.page - 1,
    size: pagination.size,
    sort: sorting.sort,
    direction: sorting.direction
  }
  router.replace({ query: { ...params, page: pagination.page } })
  try {
    const res = await memberApi.search(params)
    if (requestId !== latestRequestId) return
    // Trang hiện tại vượt quá số trang (vd sau khi lọc) -> về trang cuối
    if (res.content.length === 0 && res.totalElements > 0 && pagination.page > 1) {
      pagination.page = res.totalPages
      return fetchMembers()
    }
    members.value = res.content
    pagination.total = res.totalElements
  } catch (err) {
    if (requestId === latestRequestId) ElMessage.error(err.message)
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

let keywordTimer
let skipNextKeywordWatch = false

function search() {
  // Hủy lượt tìm đang chờ của ô từ khóa, tránh gọi API 2 lần (vd gõ xong bấm Enter ngay)
  clearTimeout(keywordTimer)
  pagination.page = 1
  fetchMembers()
}

// Gõ từ khóa: chờ 400ms sau lần gõ cuối mới tìm
watch(
  () => filters.keyword,
  () => {
    if (skipNextKeywordWatch) {
      skipNextKeywordWatch = false
      return
    }
    clearTimeout(keywordTimer)
    keywordTimer = setTimeout(search, 400)
  }
)

function resetFilters() {
  // Watcher của keyword chạy sau hàm này; bỏ qua lượt đó vì search() bên dưới đã tìm rồi
  if (filters.keyword !== '') skipNextKeywordWatch = true
  Object.assign(filters, { keyword: '', status: '', roleId: '', levelId: '', gender: '' })
  search()
}

const SORT_PROPS = { fullName: 'fullName', birthDate: 'birthDate', joinDate: 'joinDate' }
function handleSortChange({ prop, order }) {
  if (!order || !SORT_PROPS[prop]) {
    sorting.sort = 'createdAt'
    sorting.direction = 'desc'
  } else {
    sorting.sort = SORT_PROPS[prop]
    sorting.direction = order === 'ascending' ? 'asc' : 'desc'
  }
  search()
}
const defaultSort = SORT_PROPS[sorting.sort]
  ? { prop: sorting.sort, order: sorting.direction === 'asc' ? 'ascending' : 'descending' }
  : undefined

function openCreate() {
  editingMember.value = null
  formVisible.value = true
}

function openEdit(member) {
  editingMember.value = member
  formVisible.value = true
}

function openDetail(member) {
  router.push({ name: 'member-detail', params: { id: member.id } })
}

async function handleCommand(command, member) {
  if (command === 'password') {
    passwordMember.value = member
    passwordVisible.value = true
    return
  }
  const updated = await changeStatus(member, command)
  if (updated) fetchMembers()
}

const isSelf = (member) => member.id === auth.user?.id

function handleSaved(saved) {
  fetchMembers()
  // Admin tự sửa thông tin của mình -> cập nhật tên trên header
  if (isSelf(saved)) auth.fetchMe().catch(() => {})
}
const rowIndex = (index) => (pagination.page - 1) * pagination.size + index + 1

onMounted(async () => {
  catalog.load().catch((err) => ElMessage.error(err.message))
  fetchMembers()
})
</script>

<template>
  <div class="member-list">
    <!--
      Trang nằm trong <transition mode="out-in"> của MainLayout nên chỉ được có ĐÚNG 1 nút gốc.
      Không đặt chú thích HTML ngang hàng với div này: ở chế độ dev Vue giữ chú thích,
      component thành 2 nút gốc và trang kế tiếp không hiện (phải F5).
    -->
    <el-card shadow="never">
      <!-- Thanh tìm kiếm & lọc -->
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="Tìm theo tên, email, số điện thoại"
          clearable
          class="keyword"
          @keyup.enter="search"
        />
        <el-select v-model="filters.status" placeholder="Trạng thái" clearable class="filter" @change="search">
          <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-select v-model="filters.roleId" placeholder="Vai trò" clearable class="filter" @change="search">
          <el-option v-for="r in catalog.roles" :key="r.id" :value="r.id" :label="ROLE_LABELS[r.name] ?? r.name" />
        </el-select>
        <el-select v-model="filters.levelId" placeholder="Trình độ" clearable class="filter" @change="search">
          <el-option v-for="l in catalog.levels" :key="l.id" :value="l.id" :label="LEVEL_LABELS[l.name] ?? l.name" />
        </el-select>
        <el-select v-model="filters.gender" placeholder="Giới tính" clearable class="filter" @change="search">
          <el-option v-for="g in genderOptions" :key="g.value" :value="g.value" :label="g.label" />
        </el-select>
        <el-button @click="resetFilters">Xóa lọc</el-button>
        <div class="spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">Thêm thành viên</el-button>
      </div>

      <!-- Bảng danh sách -->
      <div class="table-wrap">
        <el-table
          v-loading="loading"
          :data="members"
          :default-sort="defaultSort"
          row-key="id"
          stripe
          empty-text="Không có thành viên nào"
          @sort-change="handleSortChange"
          @row-dblclick="openDetail"
        >
          <!-- Tổng độ rộng ~920px: vừa khung ở màn 1280px kể cả khi menu trái mở rộng -->
          <el-table-column label="STT" width="56" align="center">
            <template #default="{ $index }">{{ rowIndex($index) }}</template>
          </el-table-column>
          <el-table-column label="Họ tên" prop="fullName" min-width="200" sortable="custom">
            <template #default="{ row }">
              <el-link type="primary" underline="never" class="name-link" @click="openDetail(row)">
                {{ row.fullName }}
              </el-link>
              <div class="text-secondary ellipsis">{{ row.email }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Số điện thoại" prop="phone" width="118">
            <template #default="{ row }">{{ row.phone || '—' }}</template>
          </el-table-column>
          <el-table-column label="Ngày sinh" prop="birthDate" width="112" sortable="custom">
            <template #default="{ row }">{{ row.birthDate || '—' }}</template>
          </el-table-column>
          <el-table-column label="Vai trò / Trình độ" width="140">
            <template #default="{ row }">
              <div>{{ ROLE_LABELS[row.roleName] ?? row.roleName }}</div>
              <div class="text-secondary">{{ LEVEL_LABELS[row.levelName] ?? row.levelName ?? 'Chưa xếp trình độ' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Tham gia" prop="joinDate" width="108" sortable="custom" />
          <el-table-column label="Trạng thái" width="128">
            <template #default="{ row }">
              <el-tag :type="USER_STATUS_TAG_TYPES[row.status]" size="small" round disable-transitions>
                {{ USER_STATUS_LABELS[row.status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Thao tác" width="112" fixed="right" align="center">
            <template #default="{ row }">
              <el-tooltip content="Xem chi tiết" :show-after="500">
                <el-button :icon="View" link @click="openDetail(row)" />
              </el-tooltip>
              <el-tooltip content="Sửa" :show-after="500">
                <el-button :icon="Edit" link type="primary" @click="openEdit(row)" />
              </el-tooltip>
              <!-- Dòng của chính mình: không có thao tác trạng thái / đặt lại mật khẩu (làm ở Tài khoản của tôi) -->
              <el-tooltip v-if="isSelf(row)" content="Đổi email, mật khẩu tại Tài khoản của tôi" :show-after="300">
                <el-button :icon="Setting" link aria-label="Tài khoản của tôi" @click="router.push({ name: 'account' })" />
              </el-tooltip>
              <el-dropdown v-else trigger="click" @command="(cmd) => handleCommand(cmd, row)">
                <el-button :icon="MoreFilled" link aria-label="Thao tác khác" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="row.status !== 'ACTIVE'" command="ACTIVE">Kích hoạt</el-dropdown-item>
                    <el-dropdown-item v-if="row.status !== 'LOCKED'" command="LOCKED">Khóa tài khoản</el-dropdown-item>
                    <el-dropdown-item v-if="row.status !== 'INACTIVE'" command="INACTIVE">Ngừng hoạt động</el-dropdown-item>
                    <el-dropdown-item command="password" divided>Đặt lại mật khẩu</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Phân trang -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="fetchMembers"
          @size-change="search"
        />
      </div>
    </el-card>

    <MemberFormDialog v-model="formVisible" :member="editingMember" @saved="handleSaved" />
    <ResetPasswordDialog v-model="passwordVisible" :member="passwordMember" />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.keyword {
  width: 260px;
  max-width: 100%;
}

.filter {
  width: 140px;
}

.spacer {
  flex: 1;
}

.table-wrap {
  overflow-x: auto;
}

.name-link {
  font-weight: 500;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}

@media (max-width: 768px) {
  .keyword {
    width: 100%;
  }

  /* 2 ô lọc mỗi hàng */
  .filter {
    width: calc(50% - 4px);
  }

  .spacer {
    display: none;
  }
}
</style>
