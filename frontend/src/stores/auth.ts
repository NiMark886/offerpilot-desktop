import { ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type UserView } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserView | null>(null)

  async function fetchMe() {
    user.value = await authApi.me()
  }

  return {
    user,
    fetchMe
  }
})
