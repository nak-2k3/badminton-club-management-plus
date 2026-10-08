<script setup>
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'

// Chọn nhiều thành viên: tìm nhanh (không phân biệt dấu), "Chọn tất cả" áp dụng cho danh sách đang lọc
const props = defineProps({
  // [{ id, name, description (email) }]
  options: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
const selected = defineModel({ type: Array, default: () => [] })

const keyword = ref('')
const normalize = (text) =>
  (text ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/gi, 'd').toLowerCase()

const filtered = computed(() => {
  const k = normalize(keyword.value.trim())
  if (!k) return props.options
  return props.options.filter((m) => normalize(m.name).includes(k) || normalize(m.description).includes(k))
})

const selectedSet = computed(() => new Set(selected.value))
const filteredSelectedCount = computed(() => filtered.value.filter((m) => selectedSet.value.has(m.id)).length)
const allChecked = computed(() => filtered.value.length > 0 && filteredSelectedCount.value === filtered.value.length)
const indeterminate = computed(() => filteredSelectedCount.value > 0 && !allChecked.value)

function toggleAll(checked) {
  const ids = filtered.value.map((m) => m.id)
  if (checked) {
    selected.value = [...new Set([...selected.value, ...ids])]
  } else {
    const remove = new Set(ids)
    selected.value = selected.value.filter((id) => !remove.has(id))
  }
}

function toggle(id, checked) {
  selected.value = checked ? [...selected.value, id] : selected.value.filter((x) => x !== id)
}
</script>

<template>
  <div class="picker">
    <el-input v-model="keyword" :prefix-icon="Search" placeholder="Tìm tên hoặc email" clearable />
    <div class="picker-head">
      <el-checkbox
        :model-value="allChecked"
        :indeterminate="indeterminate"
        :disabled="!filtered.length"
        @change="toggleAll"
      >
        {{ keyword.trim() ? 'Chọn tất cả kết quả tìm' : 'Chọn tất cả' }}
      </el-checkbox>
      <span class="text-secondary">Đã chọn {{ selected.length }}/{{ options.length }}</span>
    </div>
    <div v-loading="loading" class="picker-list">
      <el-empty
        v-if="!loading && !filtered.length"
        :image-size="48"
        :description="options.length ? 'Không tìm thấy thành viên' : 'Không có thành viên đang hoạt động'"
      />
      <el-checkbox
        v-for="m in filtered"
        :key="m.id"
        :model-value="selectedSet.has(m.id)"
        class="picker-item"
        @change="(checked) => toggle(m.id, checked)"
      >
        <span class="item-name">{{ m.name }}</span>
        <span class="text-secondary item-email">{{ m.description }}</span>
      </el-checkbox>
    </div>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.picker-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 4px;
}

.picker-head .el-checkbox {
  margin-right: 0;
}

.picker-list {
  max-height: 240px;
  min-height: 80px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  padding: 4px 0;
}

.picker-item {
  display: flex;
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 6px 12px;
}

.picker-item:hover {
  background: var(--el-fill-color-light);
}

/* Tên + email xuống dòng gọn trên màn hình hẹp */
.picker-item :deep(.el-checkbox__label) {
  display: flex;
  flex-wrap: wrap;
  column-gap: 8px;
  min-width: 0;
  line-height: 1.4;
  white-space: normal;
}

.item-name {
  color: var(--el-text-color-primary);
}

.item-email {
  font-size: 12px;
  word-break: break-all;
}
</style>
