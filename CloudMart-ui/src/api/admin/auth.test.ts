import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  adminLogin,
  adminRefreshToken,
  adminLogout,
  getAdminProfile,
  updateAdminProfile,
  updateAdminPassword,
} from './auth'

describe('admin auth API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('adminLogin() calls POST /auth/admin/login', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await adminLogin({ account: 'admin', password: 'admin123' })

    expect(request.post).toHaveBeenCalledWith('/auth/admin/login', {
      account: 'admin',
      password: 'admin123',
    })
  })

  it('adminRefreshToken() calls POST /auth/admin/refresh', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await adminRefreshToken({ refreshToken: 'rt-value' })

    expect(request.post).toHaveBeenCalledWith('/auth/admin/refresh', { refreshToken: 'rt-value' })
  })

  it('adminLogout() calls POST /auth/admin/logout', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await adminLogout()

    expect(request.post).toHaveBeenCalledWith('/auth/admin/logout')
  })

  it('getAdminProfile() calls GET /admin/profile', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getAdminProfile()

    expect(request.get).toHaveBeenCalledWith('/admin/profile')
  })

  it('updateAdminProfile() calls PUT /admin/profile', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await updateAdminProfile({ nickname: 'Admin2' })

    expect(request.put).toHaveBeenCalledWith('/admin/profile', { nickname: 'Admin2' })
  })

  it('updateAdminPassword() calls PUT /admin/profile/password', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await updateAdminPassword({ oldPassword: 'old', newPassword: 'new' })

    expect(request.put).toHaveBeenCalledWith('/admin/profile/password', {
      oldPassword: 'old',
      newPassword: 'new',
    })
  })
})
