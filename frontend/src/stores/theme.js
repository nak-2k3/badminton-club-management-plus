import { defineStore } from 'pinia'

const STORAGE_KEY = 'theme'

// Chế độ sáng/tối: class "dark" trên <html> (đã được đặt sẵn trong index.html trước khi Vue chạy)
export const useThemeStore = defineStore('theme', {
  state: () => ({
    dark: document.documentElement.classList.contains('dark')
  }),

  actions: {
    toggle() {
      this.setDark(!this.dark)
    },

    setDark(value) {
      this.dark = value
      document.documentElement.classList.toggle('dark', value)
      try {
        localStorage.setItem(STORAGE_KEY, value ? 'dark' : 'light')
      } catch {
        // Trình duyệt chặn localStorage: vẫn đổi theme trong phiên hiện tại
      }
    }
  }
})
