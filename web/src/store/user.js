import { defineStore } from 'pinia'
import { login as loginApi, register as registerApi } from '@/api/auth'

const TOKEN_KEY = 'kb_token'
const NAME_KEY = 'kb_nickname'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    nickname: localStorage.getItem(NAME_KEY) || ''
  }),
  getters: {
    logged: (state) => !!state.token
  },
  actions: {
    async login(form) {
      const data = await loginApi(form)
      this.apply(data)
      return data
    },
    async register(form) {
      const data = await registerApi(form)
      this.apply(data)
      return data
    },
    apply(data) {
      this.token = data.token
      this.nickname = data.nickname || data.username
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(NAME_KEY, this.nickname)
    },
    logout() {
      this.token = ''
      this.nickname = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(NAME_KEY)
    }
  }
})
