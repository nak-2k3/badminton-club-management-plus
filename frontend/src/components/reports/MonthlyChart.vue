<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { formatCurrency } from '@/utils/format'

// Biểu đồ cột nhóm Thu / Chi theo tháng (SVG, không dùng thư viện).
// Một trục y (tiền), cột mảnh bo 4px ở đầu, khe 2px giữa 2 cột, lưới mờ; rê chuột / chạm vào tháng để xem số.
const props = defineProps({
  // [{ year, month, income, expense }]
  months: { type: Array, default: () => [] }
})

const SERIES = [
  { key: 'income', label: 'Thu', color: 'var(--app-chart-income)' },
  { key: 'expense', label: 'Chi', color: 'var(--app-chart-expense)' }
]
const HEIGHT = 260
const MARGIN = { top: 16, right: 12, bottom: 30, left: 56 }

const wrapRef = ref()
const width = ref(640)
const hover = ref(null)
let observer

onMounted(() => {
  observer = new ResizeObserver(([entry]) => {
    width.value = Math.max(280, Math.floor(entry.contentRect.width))
  })
  observer.observe(wrapRef.value)
})
onBeforeUnmount(() => observer?.disconnect())

const plotW = computed(() => width.value - MARGIN.left - MARGIN.right)
const plotH = HEIGHT - MARGIN.top - MARGIN.bottom

// Trục y: làm tròn mức cao nhất thành số "đẹp" (1, 2, 2.5, 5 × 10^n), 4 khoảng
const ticks = computed(() => {
  const max = Math.max(0, ...props.months.flatMap((m) => [Number(m.income), Number(m.expense)]))
  if (max <= 0) return [0, 250000, 500000, 750000, 1000000]
  const raw = max / 4
  const pow = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((f) => f * pow).find((s) => s >= raw)
  return [0, 1, 2, 3, 4].map((i) => i * step)
})
const yMax = computed(() => ticks.value[ticks.value.length - 1])
const y = (v) => MARGIN.top + plotH - (Number(v) / yMax.value) * plotH

const groupW = computed(() => plotW.value / Math.max(1, props.months.length))
const barW = computed(() => Math.max(4, Math.min(28, (groupW.value * 0.64 - 2) / 2)))

// Cột bo 4px ở đầu (phía trên), đáy vuông nằm trên đường gốc
function barPath(x, value) {
  const top = y(value)
  const h = MARGIN.top + plotH - top
  if (h <= 0) return ''
  const r = Math.min(4, barW.value / 2, h)
  const w = barW.value
  const base = MARGIN.top + plotH
  return `M${x},${base} V${top + r} Q${x},${top} ${x + r},${top} H${x + w - r} Q${x + w},${top} ${x + w},${top + r} V${base} Z`
}

const bars = computed(() =>
  props.months.map((m, i) => {
    const center = MARGIN.left + groupW.value * i + groupW.value / 2
    const left = center - barW.value - 1 // khe 2px giữa 2 cột
    return {
      ...m,
      label: `${String(m.month).padStart(2, '0')}/${m.year}`,
      center,
      x0: MARGIN.left + groupW.value * i,
      paths: SERIES.map((s, si) => ({ ...s, d: barPath(left + si * (barW.value + 2), m[s.key]) }))
    }
  }))

// Nhãn trục x: thưa bớt khi nhiều tháng để không chồng chữ (mỗi nhãn ~56px)
const labelEvery = computed(() => Math.max(1, Math.ceil(56 / groupW.value)))

// 1.250.000 -> "1,25 tr"; 500.000 -> "500 k"
function compact(v) {
  if (v === 0) return '0'
  if (v >= 1e9) return `${+(v / 1e9).toFixed(2)} tỷ`
  if (v >= 1e6) return `${+(v / 1e6).toFixed(2)} tr`.replace('.', ',')
  return `${+(v / 1e3).toFixed(0)} k`
}

const tooltipStyle = computed(() => {
  if (!hover.value) return {}
  const left = Math.min(Math.max(hover.value.center, 90), width.value - 90)
  return { left: `${left}px` }
})
</script>

<template>
  <div class="chart">
    <div class="legend" aria-hidden="true">
      <span v-for="s in SERIES" :key="s.key" class="legend-item">
        <span class="swatch" :style="{ background: s.color }" />{{ s.label }}
      </span>
    </div>
    <div ref="wrapRef" class="plot" @mouseleave="hover = null">
      <svg
        :width="width"
        :height="HEIGHT"
        :viewBox="`0 0 ${width} ${HEIGHT}`"
        role="img"
        :aria-label="`Biểu đồ thu chi theo tháng, ${months.length} tháng`"
      >
        <g class="grid">
          <g v-for="t in ticks" :key="t">
            <line :x1="MARGIN.left" :x2="width - MARGIN.right" :y1="y(t)" :y2="y(t)" :class="{ base: t === 0 }" />
            <text :x="MARGIN.left - 8" :y="y(t)" text-anchor="end" dominant-baseline="middle">{{ compact(t) }}</text>
          </g>
        </g>
        <g v-for="(b, i) in bars" :key="b.label">
          <rect
            v-if="hover?.label === b.label"
            class="hover-band"
            :x="b.x0"
            :y="MARGIN.top"
            :width="groupW"
            :height="plotH"
          />
          <path v-for="p in b.paths" :key="p.key" :d="p.d" :fill="p.color" />
          <text
            v-if="i % labelEvery === 0"
            class="x-label"
            :x="b.center"
            :y="HEIGHT - 10"
            text-anchor="middle"
          >{{ b.label }}</text>
          <!-- Vùng bắt chuột rộng hơn cột để dễ rê / chạm -->
          <rect
            class="hit"
            :x="b.x0"
            :y="MARGIN.top"
            :width="groupW"
            :height="plotH"
            @mouseenter="hover = b"
            @click="hover = b"
          />
        </g>
      </svg>
      <div v-if="hover" class="tooltip" :style="tooltipStyle" role="status">
        <div class="tt-title">Tháng {{ hover.label }}</div>
        <div v-for="s in SERIES" :key="s.key" class="tt-row">
          <span><span class="swatch" :style="{ background: s.color }" />{{ s.label }}</span>
          <strong>{{ formatCurrency(hover[s.key]) }}</strong>
        </div>
        <div class="tt-row tt-diff">
          <span>Chênh lệch</span>
          <strong>{{ formatCurrency(Number(hover.income) - Number(hover.expense)) }}</strong>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chart {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.legend {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.legend-item {
  display: inline-flex;
  align-items: center;
}

.swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 6px;
  border-radius: 3px;
  vertical-align: -1px;
}

.plot {
  position: relative;
  width: 100%;
}

svg {
  display: block;
  overflow: visible;
}

.grid line {
  stroke: var(--el-border-color-lighter);
  stroke-width: 1;
}

.grid line.base {
  stroke: var(--el-border-color);
}

.grid text,
.x-label {
  fill: var(--el-text-color-secondary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.hover-band {
  fill: var(--el-fill-color-light);
}

.hit {
  fill: transparent;
  cursor: pointer;
}

.tooltip {
  position: absolute;
  top: 0;
  min-width: 170px;
  padding: 8px 10px;
  transform: translateX(-50%);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color-overlay);
  box-shadow: var(--el-box-shadow-light);
  color: var(--el-text-color-primary);
  font-size: 13px;
  pointer-events: none;
}

.tt-title {
  margin-bottom: 4px;
  font-weight: 600;
}

.tt-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-variant-numeric: tabular-nums;
}

.tt-diff {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-regular);
}
</style>
