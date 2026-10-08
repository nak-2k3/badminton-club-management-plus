<script setup>
import { computed } from 'vue'
import { Edit, Delete, ArrowDown } from '@element-plus/icons-vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import {
  PAYMENT_METHOD_LABELS,
  PAYMENT_STATUS_LABELS,
  PAYMENT_STATUS_TAG_TYPES,
  PAYMENT_TYPE_LABELS,
  PAYMENT_TYPE_TAG_TYPES,
  toOptions
} from '@/utils/labels'
import { formatCurrency } from '@/utils/format'

// Danh sách khoản thu có ô chọn nhiều: bảng trên máy tính, dạng thẻ trên điện thoại (thấy tên + số tiền cạnh nút "Thu tiền").
// showBatch: hiện cột "Khoản thu" (tên đợt, bấm để mở đợt) — tắt ở trang chi tiết đợt vì đã biết đợt nào.
const props = defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  showBatch: { type: Boolean, default: true },
  busyId: { type: Number, default: null },
  startIndex: { type: Number, default: 1 },
  emptyText: { type: String, default: 'Chưa có khoản thu nào' },
  sortable: { type: Boolean, default: false },
  defaultSort: { type: Object, default: undefined }
})
const selected = defineModel('selected', { type: Array, default: () => [] })
const emit = defineEmits(['edit', 'collect', 'undo', 'remove', 'open-batch', 'sort-change'])

const methodOptions = toOptions(PAYMENT_METHOD_LABELS)
const { isSmall } = useBreakpoint(768)

const selectedSet = computed(() => new Set(selected.value))
const selectedOnPage = computed(() => props.rows.filter((r) => selectedSet.value.has(r.id)).length)
const allChecked = computed(() => props.rows.length > 0 && selectedOnPage.value === props.rows.length)
const indeterminate = computed(() => selectedOnPage.value > 0 && !allChecked.value)

function toggleAll(checked) {
  const ids = props.rows.map((r) => r.id)
  selected.value = checked
    ? [...new Set([...selected.value, ...ids])]
    : selected.value.filter((id) => !ids.includes(id))
}

function toggle(id, checked) {
  selected.value = checked ? [...selected.value, id] : selected.value.filter((x) => x !== id)
}

// Thu thêm không có kỳ thu: hiện ngày tạo
const createdDate = (row) => row.createdAt?.slice(0, 10) ?? ''
</script>

<template>
  <div>
    <div v-if="isSmall" v-loading="loading" class="pay-list">
      <div v-if="rows.length" class="pay-select-all">
        <el-checkbox :model-value="allChecked" :indeterminate="indeterminate" @change="toggleAll">
          Chọn tất cả {{ rows.length }} khoản đang hiện
        </el-checkbox>
      </div>
      <el-empty v-if="!loading && !rows.length" :image-size="64" :description="emptyText" />
      <div v-for="row in rows" :key="row.id" class="pay-item" :class="{ checked: selectedSet.has(row.id) }">
        <div class="pay-head">
          <el-checkbox
            :model-value="selectedSet.has(row.id)"
            :aria-label="`Chọn khoản của ${row.userName}`"
            class="pay-check"
            @change="(v) => toggle(row.id, v)"
          />
          <div class="pay-info">
            <div class="name">{{ row.userName }}</div>
            <el-link v-if="showBatch" type="primary" underline="never" @click="emit('open-batch', row)">
              {{ row.description }}
            </el-link>
            <div v-else class="text-secondary">{{ row.userEmail }}</div>
          </div>
          <span class="money strong">{{ formatCurrency(row.amount) }}</span>
        </div>
        <div v-if="showBatch || row.note" class="text-secondary">
          <template v-if="showBatch">
            <el-tag :type="PAYMENT_TYPE_TAG_TYPES[row.paymentType]" size="small" round effect="plain" disable-transitions>
              {{ PAYMENT_TYPE_LABELS[row.paymentType] }}
            </el-tag>
            <template v-if="row.paymentType === 'EXTRA'"> · Tạo {{ createdDate(row) }}</template>
          </template>
          <template v-if="row.note">{{ showBatch ? ' · ' : '' }}{{ row.note }}</template>
        </div>
        <div class="pay-actions">
          <div>
            <el-tag :type="PAYMENT_STATUS_TAG_TYPES[row.status]" size="small" round disable-transitions>
              {{ PAYMENT_STATUS_LABELS[row.status] }}
            </el-tag>
            <span v-if="row.paidAt" class="text-secondary"> {{ PAYMENT_METHOD_LABELS[row.paymentMethod] }} · {{ row.paidAt }}</span>
          </div>
          <div class="pay-buttons">
            <template v-if="row.status === 'UNPAID'">
              <el-button :icon="Edit" circle plain type="primary" :aria-label="`Sửa khoản của ${row.userName}`" @click="emit('edit', row)" />
              <el-button
                :icon="Delete"
                circle
                plain
                type="danger"
                :aria-label="`Xóa khoản của ${row.userName}`"
                :disabled="busyId === row.id"
                @click="emit('remove', row)"
              />
              <el-dropdown trigger="click" @command="(m) => emit('collect', row, m)">
                <el-button type="success" :loading="busyId === row.id">
                  Thu tiền <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="m in methodOptions" :key="m.value" :command="m.value">{{ m.label }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <el-button v-else link type="info" :disabled="busyId === row.id" @click="emit('undo', row)">Hoàn tác</el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="table-wrap">
      <el-table
        v-loading="loading"
        :data="rows"
        :default-sort="defaultSort"
        row-key="id"
        stripe
        :empty-text="emptyText"
        :row-class-name="({ row }) => (selectedSet.has(row.id) ? 'row-checked' : '')"
        @sort-change="(e) => emit('sort-change', e)"
      >
        <el-table-column width="48" align="center">
          <template #header>
            <el-checkbox
              :model-value="allChecked"
              :indeterminate="indeterminate"
              :disabled="!rows.length"
              aria-label="Chọn tất cả khoản đang hiện"
              @change="toggleAll"
            />
          </template>
          <template #default="{ row }">
            <el-checkbox
              :model-value="selectedSet.has(row.id)"
              :aria-label="`Chọn khoản của ${row.userName}`"
              @change="(v) => toggle(row.id, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="STT" width="56" align="center">
          <template #default="{ $index }">{{ startIndex + $index }}</template>
        </el-table-column>
        <el-table-column label="Thành viên" min-width="170">
          <template #default="{ row }">
            <div class="name">{{ row.userName }}</div>
            <div class="text-secondary">{{ row.userEmail }}</div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="showBatch"
          label="Khoản thu"
          prop="period"
          min-width="190"
          :sortable="sortable ? 'custom' : false"
        >
          <template #default="{ row }">
            <el-link type="primary" underline="never" @click="emit('open-batch', row)">{{ row.description }}</el-link>
            <div class="text-secondary">
              <el-tag :type="PAYMENT_TYPE_TAG_TYPES[row.paymentType]" size="small" round effect="plain" disable-transitions>
                {{ PAYMENT_TYPE_LABELS[row.paymentType] }}
              </el-tag>
              <template v-if="row.paymentType === 'EXTRA'"> · Tạo {{ createdDate(row) }}</template>
              <template v-if="row.note"> · {{ row.note }}</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-else label="Ghi chú" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'text-secondary': !row.note }">{{ row.note || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Số tiền" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ formatCurrency(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Trạng thái" width="160">
          <template #default="{ row }">
            <el-tag :type="PAYMENT_STATUS_TAG_TYPES[row.status]" size="small" round disable-transitions>
              {{ PAYMENT_STATUS_LABELS[row.status] }}
            </el-tag>
            <div v-if="row.paidAt" class="text-secondary">{{ PAYMENT_METHOD_LABELS[row.paymentMethod] }} · {{ row.paidAt }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Thao tác" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 'UNPAID'">
              <el-tooltip content="Sửa" :show-after="500">
                <el-button :icon="Edit" link type="primary" aria-label="Sửa khoản thu" @click="emit('edit', row)" />
              </el-tooltip>
              <el-tooltip content="Xóa" :show-after="500">
                <el-button
                  :icon="Delete"
                  link
                  type="danger"
                  aria-label="Xóa khoản thu"
                  :disabled="busyId === row.id"
                  @click="emit('remove', row)"
                />
              </el-tooltip>
              <el-dropdown trigger="click" class="collect" @command="(m) => emit('collect', row, m)">
                <el-button type="success" size="small" :loading="busyId === row.id">
                  Thu tiền <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="m in methodOptions" :key="m.value" :command="m.value">{{ m.label }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <el-button v-else link type="info" size="small" :disabled="busyId === row.id" @click="emit('undo', row)">
              Hoàn tác
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.table-wrap {
  overflow-x: auto;
}

.table-wrap :deep(.row-checked > td.el-table__cell) {
  background-color: var(--el-color-primary-light-9) !important;
}

.name {
  font-weight: 500;
}

.collect {
  margin-left: 8px;
  vertical-align: middle;
}

.money {
  font-variant-numeric: tabular-nums;
}

.money.strong {
  font-weight: 600;
  white-space: nowrap;
}

.pay-select-all {
  padding: 4px 0 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.pay-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 8px;
  margin: 0 -8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.pay-item.checked {
  background: var(--el-color-primary-light-9);
}

.pay-item:last-child {
  border-bottom: none;
}

.pay-head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.pay-check {
  height: 22px;
}

.pay-info {
  flex: 1;
  min-width: 0;
}

.pay-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pay-buttons .el-button {
  margin-left: 0;
}

.pay-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}
</style>
