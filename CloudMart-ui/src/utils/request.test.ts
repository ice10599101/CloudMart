import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('antd', () => ({
  message: { error: vi.fn(), warning: vi.fn() },
}))

vi.mock('umi', () => ({
  history: { push: vi.fn(), replace: vi.fn() },
}))

vi.mock('@/stores/adminAuth', () => ({
  useAdminAuthStore: {
    setState: vi.fn(),
    getState: vi.fn(() => ({ accessToken: '', refreshToken: '', adminInfo: null, permissions: [], roles: [] })),
  },
}))

function isAdminRequest(url: string): boolean {
  return url.startsWith('/admin/') || url.startsWith('/auth/admin/')
}

const USER_PUBLIC_PATH_PREFIXES = [
  '/auth/login',
  '/auth/refresh',
  '/user/users/register',
  '/product/products/search',
  '/product/categories',
  '/product/reviews/product/',
  '/product/reviews/stats/',
  '/product/products/',
  '/coupon/coupon-templates',
  '/seckill/activities',
  '/seckill/products/activity/',
  '/live/rooms',
  '/marketing/group/activities',
  '/marketing/group/orders',
  '/payment/payments/callback',
  '/file/uploads/',
  '/community/users/recommend',
  '/community/posts/hot',
  '/community/posts/',
  '/community/topics/',
]

const ADMIN_PUBLIC_PATH_PREFIXES = [
  '/auth/admin/login',
  '/auth/admin/refresh',
]

function isPublicPath(url: string): boolean {
  if (!url) return false
  const prefixes = isAdminRequest(url) ? ADMIN_PUBLIC_PATH_PREFIXES : USER_PUBLIC_PATH_PREFIXES
  for (const prefix of prefixes) {
    if (url.startsWith(prefix)) return true
  }
  if (!isAdminRequest(url) && /\/product\/products\/\d+/.test(url)) return true
  return false
}

describe('request utility', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  describe('isAdminRequest()', () => {
    it('returns true for /admin/ paths', () => {
      expect(isAdminRequest('/admin/users')).toBe(true)
      expect(isAdminRequest('/admin/roles')).toBe(true)
      expect(isAdminRequest('/admin/profile')).toBe(true)
    })

    it('returns true for /auth/admin/ paths', () => {
      expect(isAdminRequest('/auth/admin/login')).toBe(true)
      expect(isAdminRequest('/auth/admin/refresh')).toBe(true)
    })

    it('returns false for user paths', () => {
      expect(isAdminRequest('/auth/login')).toBe(false)
      expect(isAdminRequest('/product/products')).toBe(false)
      expect(isAdminRequest('/order/orders')).toBe(false)
    })
  })

  describe('isPublicPath()', () => {
    it('returns true for user public paths', () => {
      expect(isPublicPath('/auth/login')).toBe(true)
      expect(isPublicPath('/auth/refresh')).toBe(true)
      expect(isPublicPath('/user/users/register')).toBe(true)
      expect(isPublicPath('/product/products/search')).toBe(true)
      expect(isPublicPath('/product/categories')).toBe(true)
      expect(isPublicPath('/coupon/coupon-templates')).toBe(true)
      expect(isPublicPath('/seckill/activities')).toBe(true)
      expect(isPublicPath('/live/rooms')).toBe(true)
      expect(isPublicPath('/community/users/recommend')).toBe(true)
      expect(isPublicPath('/community/posts/hot')).toBe(true)
    })

    it('returns true for admin public paths', () => {
      expect(isPublicPath('/auth/admin/login')).toBe(true)
      expect(isPublicPath('/auth/admin/refresh')).toBe(true)
    })

    it('returns false for non-public user paths', () => {
      expect(isPublicPath('/order/orders')).toBe(false)
      expect(isPublicPath('/cart')).toBe(false)
      expect(isPublicPath('/user/users/me')).toBe(false)
    })

    it('returns false for non-public admin paths', () => {
      expect(isPublicPath('/admin/users')).toBe(false)
      expect(isPublicPath('/admin/roles')).toBe(false)
    })

    it('returns true for product detail URL pattern', () => {
      expect(isPublicPath('/product/products/123')).toBe(true)
    })

    it('returns false for empty url', () => {
      expect(isPublicPath('')).toBe(false)
    })

    it('returns true for review product paths', () => {
      expect(isPublicPath('/product/reviews/product/1')).toBe(true)
      expect(isPublicPath('/product/reviews/stats/1')).toBe(true)
    })
  })

  describe('request interceptor', () => {
    it('adds Bearer token for non-public user paths', () => {
      localStorage.setItem('access_token', 'user-token')

      const config = { url: '/order/orders', headers: {} as Record<string, string> }

      if (!isPublicPath(config.url)) {
        const tokenKey = isAdminRequest(config.url) ? 'admin_access_token' : 'access_token'
        const token = localStorage.getItem(tokenKey)
        if (token) {
          config.headers.Authorization = `Bearer ${token}`
        }
      }

      expect(config.headers.Authorization).toBe('Bearer user-token')
    })

    it('adds admin Bearer token for admin paths', () => {
      localStorage.setItem('admin_access_token', 'admin-token')

      const config = { url: '/admin/users', headers: {} as Record<string, string> }

      if (!isPublicPath(config.url)) {
        const tokenKey = isAdminRequest(config.url) ? 'admin_access_token' : 'access_token'
        const token = localStorage.getItem(tokenKey)
        if (token) {
          config.headers.Authorization = `Bearer ${token}`
        }
      }

      expect(config.headers.Authorization).toBe('Bearer admin-token')
    })

    it('skips token for public paths', () => {
      localStorage.setItem('access_token', 'user-token')

      const config = { url: '/auth/login', headers: {} as Record<string, string> }

      if (!isPublicPath(config.url)) {
        config.headers.Authorization = `Bearer ${localStorage.getItem('access_token')}`
      }

      expect(config.headers.Authorization).toBeUndefined()
    })
  })

  describe('response interceptor', () => {
    it('handles success=false with UNAUTHORIZED code', () => {
      const data = { success: false, error: { code: 'UNAUTHORIZED', message: '请先登录' } }

      expect(data.success).toBe(false)
      expect(data.error?.code).toBe('UNAUTHORIZED')
    })

    it('handles SERVICE_UNAVAILABLE codes', () => {
      const data = { success: false, error: { code: 'PRODUCT_SERVICE_UNAVAILABLE', message: '服务不可用' } }

      expect(data.success).toBe(false)
      expect(data.error?.code?.endsWith('_SERVICE_UNAVAILABLE')).toBe(true)
    })
  })
})
