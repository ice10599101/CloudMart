import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import { login, refreshTokenApi, logoutApi } from './auth'

describe('auth API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('login() calls POST /auth/login with correct data', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await login({ account: 'user', password: 'pass' })

    expect(request.post).toHaveBeenCalledWith('/auth/login', { account: 'user', password: 'pass' })
  })

  it('refreshTokenApi() calls POST /auth/refresh with token', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await refreshTokenApi('refresh-token-value')

    expect(request.post).toHaveBeenCalledWith('/auth/refresh', { refreshToken: 'refresh-token-value' })
  })

  it('logoutApi() calls POST /auth/logout', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await logoutApi()

    expect(request.post).toHaveBeenCalledWith('/auth/logout')
  })
})
