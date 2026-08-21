import axios from 'axios'
import Constants from 'expo-constants'
import { Platform } from 'react-native'
import { storage } from '@/utils/storage'
import type { ApiResponse } from '@/types'

/**
 * API 基址按运行环境解析：
 * - Web（metro dev server / expo web）：相对路径 /api，由 metro.config.js 代理转发到 Gateway
 * - Native + Expo Go dev：走 metro 代理（manifest2.extra.expoGoHostUri 为 Expo 连接的
 *   dev server 地址，手机只需可达电脑 8081——Expo Go 本身就依赖它；避免真机直连
 *   公网 8090 被网络拦截）
 * - Native 生产构建或显式配置：EXPO_PUBLIC_API_HOST 直连 Gateway
 *
 * 注意：RN 运行时全局存在 window（window === global），typeof window 判定 web
 * 在原生端恒为 true，必须用 Platform.OS 判定（曾因此导致真机全部请求走相对路径失败）
 */
function resolveApiBase(): string {
  if (Platform.OS === 'web') return '/api'
  const isDev = process.env.NODE_ENV !== 'production'
  const metroHost =
    Constants.expoConfig?.hostUri ??
    (Constants.manifest2 as { extra?: { expoGoHostUri?: string } } | undefined)?.extra?.expoGoHostUri
  if (isDev && metroHost) {
    return `http://${metroHost}/api`
  }
  return `${process.env.EXPO_PUBLIC_API_HOST || 'http://127.0.0.1'}:8090/api`
}

const API_BASE = resolveApiBase()
// 诊断日志：native 端输出到 metro 终端，用于确认真机实际请求基址
if (Platform.OS !== 'web') {
  console.log('[request] API_BASE =', API_BASE)
}

const client = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

client.interceptors.request.use(async (config) => {
  const token = await storage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  const method = (config.method ?? 'GET').toUpperCase()
  if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
    config.headers['X-Idempotency-Key'] = `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
  }

  return config
})

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = await storage.getItem('refresh_token')
      if (!refreshToken) {
        await storage.multiRemove(['access_token', 'refresh_token'])
        return Promise.reject(error)
      }

      if (isRefreshing) {
        return new Promise((resolve) => {
          pendingRequests.push((token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(client(originalRequest))
          })
        })
      }

      isRefreshing = true
      originalRequest._retry = true

      try {
        const res = await client.post('/auth/refresh', { refreshToken })
        const { accessToken: newAccessToken, refreshToken: newRefreshToken } = res.data.data
        await storage.multiSet([
          ['access_token', newAccessToken],
          ['refresh_token', newRefreshToken],
        ])
        pendingRequests.forEach((cb) => cb(newAccessToken))
        pendingRequests = []
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        return client(originalRequest)
      } catch {
        await storage.multiRemove(['access_token', 'refresh_token'])
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }
    return Promise.reject(error)
  }
)

interface RequestConfig {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: Record<string, unknown> | FormData
  header?: Record<string, string>
}

async function request<T = unknown>(config: RequestConfig): Promise<{ data: ApiResponse<T> }> {
  const res = await client.request<ApiResponse<T>>({
    url: config.url,
    method: config.method || 'GET',
    data: config.data,
    headers: config.header,
  })
  return { data: res.data }
}

export default request
