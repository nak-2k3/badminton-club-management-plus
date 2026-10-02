<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Edit, Delete, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { courtApi } from '@/api/courts'
import CourtFormDialog from '@/components/courts/CourtFormDialog.vue'
import { formatCurrency } from '@/utils/format'

const filters = reactive({ keyword: '', active: '' })
const courts = ref([])
const loading = ref(false)
let latestRequestId = 0

const formVisible = ref(false)
const editingCourt = ref(null)

async function fetchCourts() {
  const requestId = ++latestRequestId
  loading.value = true
  try {
    const params = {}
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
    if (filters.active !== '') params.active = filters.active
    const res = await courtApi.list(params)
    if (requestId === latestRequestId) courts.value = res
  } catch (err) {
    if (requestId === latestRequestId) ElMessage.error(err.message)
  } finally {
    if (requestId === latestRequestId) loading.value = false
  }
}

// Gõ từ khóa: chờ 400ms sau lần gõ cuối mới tìm
let keywordTimer
watch(
  () => filters.keyword,
  () => {
    clearTimeout(keywordTimer)
    keywordTimer = setTimeout(fetchCourts, 400)
  }
)

function search() {
  clearTimeout(keywordTimer)
  fetchCourts()
}

function openCreate() {
  editingCourt.value = null
  formVisible.value = true
}

function openEdit(court) {
  editingCourt.value = court
  formVisible.value = true
}

async function toggleActive(court) {
  const activate = !court.active
  try {
    await ElMessageBox.confirm(
      activate
        ? `Hoạt động lại sân "${court.courtName}"? Sân sẽ được chọn khi tạo lịch chơi.`
        : `Tạm ngưng sân "${court.courtName}"? Sân sẽ không được chọn khi tạo lịch chơi mới; lịch đã có vẫn giữ nguyên.`,
      activate ? 'Hoạt động lại' : 'Tạm ngưng sân',
      { confirmButtonText: 'Xác nhận', cancelButtonText: 'Hủy', type: activate ? 'info' : 'warning' }
    )
  } catch {
    return
  }
  try {
    await courtApi.setActive(court.id, activate)
    ElMessage.success(activate ? 'Sân đã hoạt động lại' : 'Đã tạm ngưng sân')
    fetchCourts()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function removeCourt(court) {
  try {
    await ElMessageBox.confirm(
      `Xóa sân "${court.courtName}"? Chỉ xóa được sân chưa có lịch chơi nào.`,
      'Xóa sân',
      { confirmButtonText: 'Xóa', cancelButtonText: 'Hủy', type: 'error', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  try {
    await courtApi.remove(court.id)
    ElMessage.success(`Đã xóa sân "${court.courtName}"`)
    fetchCourts()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

onMounted(fetchCourts)
</script>

<template>
  <div class="court-list">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="Tìm theo tên, địa chỉ, SĐT"
          clearable
          class="keyword"
          @keyup.enter="search"
        />
        <el-radio-group v-model="filters.active" @change="search">
          <el-radio-button value="">Tất cả</el-radio-button>
          <el-radio-button :value="true">Hoạt động</el-radio-button>
          <el-radio-button :value="false">Tạm ngưng</el-radio-button>
        </el-radio-group>
        <div class="spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreate">Thêm sân</el-button>
      </div>

      <div class="table-wrap">
        <el-table v-loading="loading" :data="courts" row-key="id" stripe empty-text="Chưa có sân nào">
          <el-table-column label="STT" width="56" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="Tên sân" min-width="220">
            <template #default="{ row }">
              <div class="court-name">{{ row.courtName }}</div>
              <div class="text-secondary">{{ row.address }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Giá / sân / giờ" width="140" align="right">
            <template #default="{ row }">
              <span class="money">{{ formatCurrency(row.hourlyRate) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Điện thoại" width="125">
            <template #default="{ row }">{{ row.phone || '—' }}</template>
          </el-table-column>
          <el-table-column label="Ghi chú" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.note || '—' }}</template>
          </el-table-column>
          <el-table-column label="Trạng thái" width="115">
            <template #default="{ row }">
              <el-tag :type="row.active ? 'success' : 'info'" size="small" round disable-transitions>
                {{ row.active ? 'Hoạt động' : 'Tạm ngưng' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Thao tác" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <el-tooltip content="Sửa" :show-after="500">
                <el-button :icon="Edit" link type="primary" aria-label="Sửa" @click="openEdit(row)" />
              </el-tooltip>
              <el-tooltip :content="row.active ? 'Tạm ngưng' : 'Hoạt động lại'" :show-after="500">
                <el-button
                  :icon="row.active ? VideoPause : VideoPlay"
                  link
                  :aria-label="row.active ? 'Tạm ngưng' : 'Hoạt động lại'"
                  @click="toggleActive(row)"
                />
              </el-tooltip>
              <el-tooltip content="Xóa" :show-after="500">
                <el-button :icon="Delete" link type="danger" aria-label="Xóa" @click="removeCourt(row)" />
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <p class="text-secondary footnote">
        Giá thuê tính cho 1 sân trong 1 giờ. Tiền sân một buổi = giá × số giờ × số sân thuê.
      </p>
    </el-card>

    <CourtFormDialog v-model="formVisible" :court="editingCourt" @saved="fetchCourts" />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.keyword {
  width: 280px;
  max-width: 100%;
}

.spacer {
  flex: 1;
}

.table-wrap {
  overflow-x: auto;
}

.court-name {
  font-weight: 500;
}

.money {
  font-variant-numeric: tabular-nums;
}

.footnote {
  margin: 12px 0 0;
}

@media (max-width: 768px) {
  .keyword {
    width: 100%;
  }

  .spacer {
    display: none;
  }
}
</style>
