import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'

const TOKEN_KEY = 'accessToken'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY),
    user: null
  }),

  getters: {
    isAuthenticated: (state) => !!state.token,
    role: (state) => state.user?.role ?? null,
    hasRole: (state) => (...roles) => roles.includes(state.user?.role)
  },

  actions: {
    async login(credentials) {
      const res = await authApi.login(credentials)
      this.token = res.accessToken
      this.user = res.user
      localStorage.setItem(TOKEN_KEY, res.accessToken)
    },

    // Gọi khi tải lại trang: có token nhưng chưa có thông tin người dùng
    async fetchMe() {
      this.user = await authApi.me()
    },

    logout() {
      this.token = null
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
    }
  }
})
