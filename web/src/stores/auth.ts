import { defineStore } from 'pinia'
import { platformApi } from '../api/platform'
import type { CurrentUser } from '../types/api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    currentUser: null as CurrentUser | null,
    initialized: false,
  }),
  getters: {
    isAuthenticated: state => state.currentUser !== null,
  },
  actions: {
    async bootstrap() {
      if (this.initialized) {
        return
      }
      try {
        this.currentUser = await platformApi.getCurrentUser()
        this.initialized = true
      }
      catch (error) {
        const status = (error as Error & { status?: number }).status
        if (status === 401) {
          this.currentUser = null
          this.initialized = true
          return
        }

        // During local startup the frontend can render a moment before the backend is ready.
        // Treat that first probe as a soft miss so router bootstrap does not emit noisy warnings.
        if (error instanceof TypeError || status == null) {
          this.currentUser = null
          return
        }

        if (status !== 401) {
          throw error
        }
      }
    },
    async login(username: string, password: string) {
      this.currentUser = await platformApi.login(username, password)
      this.initialized = true
    },
    async logout() {
      try {
        await platformApi.logout()
      }
      finally {
        this.currentUser = null
        this.initialized = true
      }
    },
  },
})
