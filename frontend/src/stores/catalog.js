import { defineStore } from 'pinia'
import { catalogApi } from '@/api/catalog'

// Danh mục ít thay đổi (vai trò, trình độ): tải 1 lần rồi dùng lại
export const useCatalogStore = defineStore('catalog', {
  state: () => ({
    roles: [],
    levels: [],
    loaded: false
  }),

  actions: {
    async load() {
      if (this.loaded) return
      const [roles, levels] = await Promise.all([catalogApi.roles(), catalogApi.levels()])
      this.roles = roles
      this.levels = levels
      this.loaded = true
    }
  }
})
