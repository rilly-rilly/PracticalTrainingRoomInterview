import axios from 'axios'
import { getToken, clearAuth } from '../auth'
import { pushToast } from '../utils/toast'

// 切换开关：true=mock 模式（不走真实后端），false=真实 Spring Boot 后端
export const USE_MOCK = false

const instance = axios.create({
  baseURL: '',
  timeout: 10000
})

// ===== Mock adapter（演示阶段用，不走真实 HTTP） =====
import { handleRequest } from '../mock/server'
if (USE_MOCK) {
  instance.defaults.adapter = async (config) => {
    await new Promise((resolve) => setTimeout(resolve, 220 + Math.random() * 180))
    const { status, body } = await handleRequest(config)
    const response = {
      data: body,
      status,
      statusText: String(status),
      headers: {},
      config,
      request: {}
    }
    if (status >= 200 && status < 300) return response
    const error = new axios.AxiosError(body.message, 'ERR_BAD_REQUEST', config, {}, response)
    error.isApiError = true
    throw error
  }
}

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (res) => res.data,
  (error) => {
    const body = error?.response?.data
    const code = body?.code || 0
    const message = body?.message || '网络异常，请稍后重试'
    if (code === 401) {
      clearAuth()
    }
    const payload = { code, message, data: body?.data ?? null }
    pushToast(`[${code}] ${message}`, code === 401 || code === 403 ? 'error' : 'warn', code)
    return Promise.reject(payload)
  }
)

export default instance
