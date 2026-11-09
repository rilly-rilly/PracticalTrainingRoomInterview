import { reactive } from 'vue'

const KEY = 'trainee-selection-auth-v1'

export const authState = reactive({
  token: '',
  role: '',
  profile: null
})

export function restoreAuth() {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return
    const parsed = JSON.parse(raw)
    if (parsed?.token) {
      authState.token = parsed.token
      authState.role = parsed.role
      authState.profile = parsed.profile
    }
  } catch (e) {
    console.warn('[auth] 恢复登录态失败', e)
  }
}

export function persistAuth(payload) {
  authState.token = payload.token
  authState.role = payload.role
  authState.profile = payload.profile
  localStorage.setItem(KEY, JSON.stringify(payload))
}

export function clearAuth() {
  authState.token = ''
  authState.role = ''
  authState.profile = null
  localStorage.removeItem(KEY)
}

export function getToken() {
  return authState.token
}

export function roleHome(role) {
  if (role === 'student') return '/student/status'
  if (role === 'teacher') return '/teacher/applicants'
  if (role === 'admin') return '/admin/import'
  return '/'
}
