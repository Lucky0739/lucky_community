import { defineStore } from 'pinia'
import { request } from '@/api/http'
import { idStr, parseJsonPreservingIds } from '@/utils/id'

function userIdFromToken(token) {
  try {
    const part = String(token || '').split('.')[1]
    if (!part) return ''
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0))
    const data = parseJsonPreservingIds(new TextDecoder().decode(bytes))
    return data?.userId != null && data.userId !== '' ? idStr(data.userId) : ''
  } catch {
    return ''
  }
}

const ROLE_STORE = 'lucky-auth-roles'

function saveRoles(userId, roles) {
  sessionStorage.setItem(ROLE_STORE, JSON.stringify({ userId, roles: roles || [] }))
}

function readRoles(userId) {
  try {
    const saved = JSON.parse(sessionStorage.getItem(ROLE_STORE) || 'null')
    if (saved && idStr(saved.userId) === idStr(userId)) {
      return saved.roles || []
    }
  } catch {
    // 本地角色缓存坏了就当没有
  }
  return []
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: '',
    /** 雪花 ID，string */
    userId: '',
    phone: '',
    nickName: '',
    icon: '',
    roles: [],
    ready: false,
  }),
  getters: {
    loggedIn: (state) => Boolean(state.accessToken),
    isAdmin: (state) => (state.roles || []).some((role) => role?.roleCode === 'PLATFORM_ADMIN'),
  },
  actions: {
    applyLogin(data) {
      this.accessToken = data?.accessToken || ''
      this.userId = data?.userId != null && data?.userId !== '' ? idStr(data.userId) : ''
      this.phone = data?.phone || ''
      this.nickName = data?.nickName || data?.phone || ''
      this.icon = data?.icon || ''
      this.roles = data?.roles || []
      saveRoles(this.userId, this.roles)
    },
    setProfile(profile) {
      if (profile?.userId != null && profile.userId !== '') {
        this.userId = idStr(profile.userId)
      }
      if (profile?.nickName) {
        this.nickName = profile.nickName
      }
      this.icon = profile?.icon || ''
    },
    async login(phone, password) {
      const data = await request('/user/login', {
        method: 'POST',
        body: { phone, password },
      })
      this.applyLogin(data)
      return data
    },
    async register(phone, password) {
      await request('/user/register', {
        method: 'POST',
        body: { phone, password },
      })
    },
    async logout() {
      try {
        await request('/user/logout', {
          method: 'POST',
          token: this.accessToken,
        })
      } finally {
        this.$reset()
        sessionStorage.removeItem(ROLE_STORE)
        this.ready = true
      }
    },
    async restore() {
      try {
        const accessToken = await request('/user/refresh', { method: 'POST' })
        if (typeof accessToken === 'string' && accessToken) {
          this.accessToken = accessToken
          const userId = userIdFromToken(accessToken)
          if (userId) {
            this.userId = userId
            this.roles = readRoles(userId)
            try {
              this.setProfile(await request(`/user/profile/${userId}`, { token: accessToken }))
            } catch {
              // 资料接口未就绪时仍保留登录态
            }
          }
        }
      } catch {
        this.accessToken = ''
      } finally {
        this.ready = true
      }
    },
  },
})
