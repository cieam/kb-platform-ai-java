import { defineStore } from 'pinia'
import { pageKb } from '@/api/kb'

const CURRENT_KEY = 'kb_current_id'

export const useKbStore = defineStore('kb', {
  state: () => ({
    list: [],
    currentId: Number(localStorage.getItem(CURRENT_KEY) || 0),
    loaded: false
  }),
  getters: {
    current: (state) => state.list.find((item) => item.id === state.currentId) || null
  },
  actions: {
    async load() {
      const page = await pageKb({ page: 1, size: 100 })
      this.list = page.list || []
      this.loaded = true
      // 当前知识库被删掉或还没选时，自动落到第一个
      if (!this.list.some((item) => item.id === this.currentId)) {
        this.select(this.list.length ? this.list[0].id : 0)
      }
      return this.list
    },
    select(id) {
      this.currentId = id || 0
      if (id) {
        localStorage.setItem(CURRENT_KEY, String(id))
      } else {
        localStorage.removeItem(CURRENT_KEY)
      }
    }
  }
})
