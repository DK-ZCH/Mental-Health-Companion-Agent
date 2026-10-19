import { defineStore } from 'pinia'
import { loginStudent, fetchStudentSession } from '@/api/auth'
import { TOKEN_KEY } from '@/api/http'

interface AuthState {
  token: string
  userId: number | null
  username: string
  role: string
}

const LS_USER = 'mh_user_id'
const LS_NAME = 'mh_username'
const LS_ROLE = 'mh_role'

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userId: Number(localStorage.getItem(LS_USER)) || null,
    username: localStorage.getItem(LS_NAME) || '',
    role: localStorage.getItem(LS_ROLE) || ''
  }),
  getters: {
    isLoggedIn: (s) => !!s.token
  },
  actions: {
    async login(username: string, password: string) {
      const r = await loginStudent(username, password)
      if (!r.token) throw new Error('登录响应缺少 token')

      this.token = r.token
      this.userId = r.userId ?? null
      this.role = r.role || ''
      this.username = r.username || username

      localStorage.setItem(TOKEN_KEY, this.token)
      if (this.userId !== null) localStorage.setItem(LS_USER, String(this.userId))
      localStorage.setItem(LS_NAME, this.username)
      localStorage.setItem(LS_ROLE, this.role)

      // 立即校验会话，确保 token 真正可用（避免"登录成功但会话失效"）
      await fetchStudentSession()
      return r
    },
    logout() {
      this.token = ''
      this.userId = null
      this.username = ''
      this.role = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(LS_USER)
      localStorage.removeItem(LS_NAME)
      localStorage.removeItem(LS_ROLE)
    }
  }
})
