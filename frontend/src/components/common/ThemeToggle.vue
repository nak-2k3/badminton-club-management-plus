<script setup>
import { Moon, Sunny } from '@element-plus/icons-vue'
import { useThemeStore } from '@/stores/theme'

const theme = useThemeStore()
</script>

<template>
  <el-tooltip :content="theme.dark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'" :show-after="400">
    <el-button
      circle
      class="theme-toggle"
      :aria-label="theme.dark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'"
      @click="theme.toggle()"
    >
      <transition name="spin" mode="out-in">
        <el-icon :key="theme.dark ? 'moon' : 'sun'" :size="16">
          <Moon v-if="theme.dark" />
          <Sunny v-else />
        </el-icon>
      </transition>
    </el-button>
  </el-tooltip>
</template>

<style scoped>
.spin-enter-active,
.spin-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.spin-enter-from {
  transform: rotate(-90deg);
  opacity: 0;
}

.spin-leave-to {
  transform: rotate(90deg);
  opacity: 0;
}
</style>
