import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type TokenResponse, type UserView } from '@/api/auth'

const ACCESS_KEY = 'offerpilot_access_token'
const REFRESH_KEY = 'offerpilot_refresh_token'
const USER_KEY = 'offerpilot_user'

function readStoredUser(): UserView | null {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null') as UserView | null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem(ACCESS_KEY) || '')
  const refreshToken = ref(localStorage.getItem(REFRESH_KEY) || '')
  const user = ref<UserView | null>(readStoredUser())

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  function persist(tokens: TokenResponse) {
    accessToken.value = tokens.accessToken
    refreshToken.value = tokens.refreshToken
    user.value = tokens.user

    localStorage.setItem(ACCESS_KEY, tokens.accessToken)
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
    localStorage.setItem(USER_KEY, JSON.stringify(tokens.user))
  }

  async function login(account: string, password: string) {
    const tokens = await authApi.login({ account, password })
    persist(tokens)
  }

  async function register(email: string, password: string, nickname: string) {
    const tokens = await authApi.register({ email, password, nickname })
    persist(tokens)
  }

  async function fetchMe() {
    user.value = await authApi.me()
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  async function logout() {
    try {
      if (refreshToken.value) {
        await authApi.logout(refreshToken.value)
      }
    } finally {
      accessToken.value = ''
      refreshToken.value = ''
      user.value = null
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    login,
    register,
    fetchMe,
    logout
  }
})
