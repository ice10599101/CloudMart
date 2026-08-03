import { test as base, expect } from '@playwright/test'

const API_BASE = 'http://127.0.0.1:8090/api'

type ApiMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

async function apiRequest(
  method: ApiMethod,
  path: string,
  options?: { body?: unknown; token?: string; params?: Record<string, string> },
) {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (options?.token) headers.Authorization = `Bearer ${options.token}`

  const url = new URL(`${API_BASE}${path}`)
  if (options?.params) {
    Object.entries(options.params).forEach(([k, v]) => url.searchParams.set(k, v))
  }

  const response = await fetch(url.toString(), {
    method,
    headers,
    body: options?.body ? JSON.stringify(options.body) : undefined,
  })

  const data = await response.json().catch(() => null)
  return { status: response.status, data }
}

export function apiGet(path: string, token?: string, params?: Record<string, string>) {
  return apiRequest('GET', path, { token, params })
}

export function apiPost(path: string, body: unknown, token?: string) {
  return apiRequest('POST', path, { body, token })
}

export function apiPut(path: string, body: unknown, token?: string) {
  return apiRequest('PUT', path, { body, token })
}

export function apiDelete(path: string, token?: string) {
  return apiRequest('DELETE', path, { token })
}

export { expect }
export const integrationTest = base
