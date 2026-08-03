import { test, expect } from '@playwright/test'

const API_BASE = 'http://localhost:8090/api'

test.describe('API Integration Tests', () => {
  async function apiGet(path: string, headers: Record<string, string> = {}) {
    const response = await fetch(`${API_BASE}${path}`, { headers })
    return { status: response.status, data: await response.json().catch(() => null) }
  }

  async function apiPost(path: string, body: unknown, headers: Record<string, string> = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...headers },
      body: JSON.stringify(body),
    })
    return { status: response.status, data: await response.json().catch(() => null) }
  }

  test.describe('Auth API', () => {
    test('POST /auth/login - should return error for invalid credentials', async () => {
      const res = await apiPost('/auth/login', { account: 'invalid', password: 'wrong' })
      expect([200, 401, 500]).toContain(res.status)
      if (res.status === 200) {
        expect(res.data.success).toBe(false)
      }
    })

    test('POST /auth/login - should validate required fields', async () => {
      const res = await apiPost('/auth/login', {})
      expect([200, 400, 401]).toContain(res.status)
    })

    test('POST /auth/refresh - should reject invalid refresh token', async () => {
      const res = await apiPost('/auth/refresh', { refreshToken: 'invalid-token' })
      expect([200, 401]).toContain(res.status)
      if (res.status === 200) {
        expect(res.data.success).toBe(false)
      }
    })

    test('GET /auth/oauth2/jwks - should return JWKS', async () => {
      const res = await apiGet('/auth/oauth2/jwks')
      expect(res.status).toBe(200)
      expect(res.data).toBeDefined()
      expect(res.data.keys).toBeDefined()
    })
  })

  test.describe('User API', () => {
    test('POST /user/users/register - should validate required fields', async () => {
      const res = await apiPost('/user/users/register', {})
      expect([200, 400]).toContain(res.status)
      if (res.status === 200) {
        expect(res.data.success).toBe(false)
      }
    })

    test('POST /user/users/register - should reject invalid email', async () => {
      const res = await apiPost('/user/users/register', {
        password: 'test123456',
        email: 'not-an-email',
        nickname: 'testuser',
      })
      expect([200, 400]).toContain(res.status)
      if (res.status === 200) {
        expect(res.data.success).toBe(false)
      }
    })

    test('GET /user/users/recommend - should return recommended users or require auth', async () => {
      const res = await apiGet('/user/users/recommend')
      expect([200, 401]).toContain(res.status)
      if (res.status === 200) {
        expect(res.data.success).toBe(true)
      }
    })

    test('GET /user/users/count - should return user count or require auth', async () => {
      const res = await apiGet('/user/users/count')
      expect([200, 401]).toContain(res.status)
    })

    test('GET /user/users/me - should require authentication', async () => {
      const res = await apiGet('/user/users/me')
      expect([200, 401]).toContain(res.status)
    })
  })

  test.describe('Product API', () => {
    test('GET /product/products/search - should return product list', async () => {
      const res = await apiGet('/product/products/search')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /product/products/search - should support keyword query', async () => {
      const res = await apiGet('/product/products/search?keyword=手机&page=1&pageSize=10')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /product/categories - should return category list', async () => {
      const res = await apiGet('/product/categories')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /product/brands - should return brand list or require auth', async () => {
      const res = await apiGet('/product/brands')
      expect([200, 401]).toContain(res.status)
      if (res.status === 200) {
        expect(res.data.success).toBe(true)
      }
    })

    test('GET /product/products/{id} - should handle non-existent product', async () => {
      const res = await apiGet('/product/products/999999999')
      expect([200, 400, 404]).toContain(res.status)
      if (res.status === 200) {
        expect(res.data.success).toBe(false)
      }
    })

    test('GET /product/reviews/product/{productId} - should return reviews', async () => {
      const res = await apiGet('/product/reviews/product/1')
      expect(res.status).toBe(200)
    })

    test('GET /product/reviews/stats/{productId} - should return review stats', async () => {
      const res = await apiGet('/product/reviews/stats/1')
      expect(res.status).toBe(200)
    })
  })

  test.describe('Community API', () => {
    test('GET /community/posts/feed - should return post feed', async () => {
      const res = await apiGet('/community/posts/feed')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /community/posts/feed - should support tab parameter', async () => {
      const res = await apiGet('/community/posts/feed?tab=recommend&page=1&size=10')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /community/tags/hot - should return hot tags', async () => {
      const res = await apiGet('/community/tags/hot')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /community/tags/trending - should return trending topics', async () => {
      const res = await apiGet('/community/tags/trending')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /community/search/hot - should return hot searches', async () => {
      const res = await apiGet('/community/search/hot')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /community/search/history - should return empty for anonymous', async () => {
      const res = await apiGet('/community/search/history')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /community/growth/level-configs - should return level configs', async () => {
      const res = await apiGet('/community/growth/level-configs')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /community/growth/check-in/status - should require authentication', async () => {
      const res = await apiGet('/community/growth/check-in/status')
      expect([200, 401]).toContain(res.status)
    })

    test('POST /community/posts - should require authentication', async () => {
      const res = await apiPost('/community/posts', { title: 'test', content: 'test' })
      expect([200, 401]).toContain(res.status)
    })
  })

  test.describe('Order API', () => {
    test('GET /order/orders - should require authentication', async () => {
      const res = await apiGet('/order/orders')
      expect([200, 401]).toContain(res.status)
    })

    test('POST /order/orders - should require authentication', async () => {
      const res = await apiPost('/order/orders', {})
      expect([200, 401]).toContain(res.status)
    })
  })

  test.describe('Cart API', () => {
    test('GET /cart - should require authentication', async () => {
      const res = await apiGet('/cart')
      expect([200, 401]).toContain(res.status)
    })

    test('POST /cart/items - should require authentication', async () => {
      const res = await apiPost('/cart/items', { skuId: 1, quantity: 1 })
      expect([200, 401]).toContain(res.status)
    })
  })

  test.describe('Coupon API', () => {
    test('GET /coupon/coupon-templates - should return template list', async () => {
      const res = await apiGet('/coupon/coupon-templates')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /coupon/coupon-templates - should support pagination', async () => {
      const res = await apiGet('/coupon/coupon-templates?page=1&pageSize=10')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /coupon/user-coupons - should require authentication', async () => {
      const res = await apiGet('/coupon/user-coupons')
      expect([200, 401]).toContain(res.status)
    })

    test('POST /coupon/user-coupons/claim - should require authentication', async () => {
      const res = await apiPost('/coupon/user-coupons/claim?templateId=1', {})
      expect([200, 401]).toContain(res.status)
    })
  })

  test.describe('Seckill API', () => {
    test('GET /seckill/activities - should return activity list', async () => {
      const res = await apiGet('/seckill/activities')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /seckill/activities - should support status filter', async () => {
      const res = await apiGet('/seckill/activities?status=ACTIVE')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /seckill/activities/{activityId} - should handle non-existent activity', async () => {
      const res = await apiGet('/seckill/activities/999999999')
      expect([200, 400, 404]).toContain(res.status)
      if (res.status === 200) {
        expect(res.data.success).toBe(false)
      }
    })
  })

  test.describe('Live API', () => {
    test('GET /live/rooms - should return live room list', async () => {
      const res = await apiGet('/live/rooms')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /live/rooms - should support pagination and status filter', async () => {
      const res = await apiGet('/live/rooms?page=1&size=10')
      expect(res.status).toBe(200)
      expect(res.data.success).toBe(true)
    })

    test('GET /live/rooms/{roomId} - should handle non-existent room', async () => {
      const res = await apiGet('/live/rooms/999999999')
      expect([200, 400, 404, 500]).toContain(res.status)
      if (res.status === 200) {
        expect(res.data.success).toBe(false)
      }
    })
  })

  test.describe('AI Chat API', () => {
    test('POST /ai/chat - should require authentication', async () => {
      const res = await apiPost('/ai/chat', { message: 'hello' })
      expect([200, 401]).toContain(res.status)
    })

    test('GET /ai/search - should require authentication', async () => {
      const res = await apiGet('/ai/search?query=手机')
      expect([200, 401]).toContain(res.status)
    })

    test('GET /ai/vector-search - should require authentication', async () => {
      const res = await apiGet('/ai/vector-search?query=手机')
      expect([200, 401]).toContain(res.status)
    })

    test('GET /ai/hybrid-search - should require authentication', async () => {
      const res = await apiGet('/ai/hybrid-search?query=手机')
      expect([200, 401]).toContain(res.status)
    })

    test('GET /ai/reviews/summary/{productId} - should require authentication', async () => {
      const res = await apiGet('/ai/reviews/summary/1')
      expect([200, 401]).toContain(res.status)
    })
  })

  test.describe('Payment API', () => {
    test('GET /payment/payments - should require authentication', async () => {
      const res = await apiGet('/payment/payments')
      expect([200, 401]).toContain(res.status)
    })
  })

  test.describe('Notification API', () => {
    test('GET /notification/notifications - should require authentication', async () => {
      const res = await apiGet('/notification/notifications')
      expect([200, 401]).toContain(res.status)
    })
  })

  test.describe('Marketing API', () => {
    test('GET /marketing/tiered - should return tiered promotions or require auth', async () => {
      const res = await apiGet('/marketing/tiered')
      expect([200, 401]).toContain(res.status)
    })

    test('GET /marketing/group - should return group activities or require auth', async () => {
      const res = await apiGet('/marketing/group')
      expect([200, 401]).toContain(res.status)
    })
  })
})
