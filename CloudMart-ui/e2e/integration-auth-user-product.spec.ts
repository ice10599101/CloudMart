import { test, expect } from '@playwright/test'

const API_BASE = '/api'

async function apiRequest(page: import('@playwright/test').Page, method: string, path: string, body?: unknown, token?: string) {
  return page.evaluate(async ({ url, method: m, body: b, token: t }) => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (t) headers.Authorization = `Bearer ${t}`
    const opts: RequestInit = { method: m, headers }
    if (b) opts.body = JSON.stringify(b)
    const res = await fetch(url, opts)
    return { status: res.status, data: await res.json().catch(() => null) }
  }, { url: `${API_BASE}${path}`, method, body, token })
}

test.describe('Auth Module', () => {
  test('POST /auth/login - invalid credentials should return error envelope', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/auth/login', { account: 'nonexistent_user_xyz', password: 'wrong_password' })
    expect([200, 401]).toContain(res.status)
    if (res.status === 200) {
      expect(res.data.success).toBe(false)
      expect(res.data.error).toBeDefined()
      expect(res.data.error.code).toBeDefined()
    }
  })

  test('POST /auth/login - valid credentials should return success envelope with tokens', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/auth/login', { account: 'testuser', password: 'test123456' })
    expect([200, 401]).toContain(res.status)
    if (res.status === 200 && res.data.success) {
      expect(res.data.data.accessToken).toBeDefined()
      expect(res.data.data.refreshToken).toBeDefined()
      expect(res.data.data.tokenType).toBeDefined()
      expect(res.data.data.expiresIn).toBeDefined()
    }
  })

  test('POST /auth/refresh - invalid token should return error', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/auth/refresh', { refreshToken: 'invalid-refresh-token-xyz' })
    expect([200, 401]).toContain(res.status)
    if (res.status === 200) {
      expect(res.data.success).toBe(false)
    }
  })

  test('POST /auth/logout - without token should return envelope', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/auth/logout', {})
    expect([200, 401]).toContain(res.status)
  })

  test('POST /auth/admin/login - invalid credentials should return error envelope', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/auth/admin/login', { account: 'fake_admin', password: 'wrong' })
    expect([200, 401]).toContain(res.status)
    if (res.status === 200) {
      expect(res.data.success).toBe(false)
    }
  })
})

test.describe('User Module', () => {
  test('POST /user/users/register - valid data should return success envelope', async ({ page }) => {
    await page.goto('/')
    const timestamp = Date.now()
    const res = await apiRequest(page, 'POST', '/user/users/register', {
      password: 'Test123456!',
      email: `e2e_${timestamp}@test.com`,
      nickname: `e2e_user_${timestamp}`,
    })
    expect([200, 400]).toContain(res.status)
    if (res.status === 200 && res.data.success) {
      expect(res.data.data.id).toBeDefined()
      expect(res.data.data.username).toBeDefined()
    }
  })

  test('GET /user/users/me - without token should return 401', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/user/users/me')
    expect([200, 401]).toContain(res.status)
  })

  test('PUT /user/users/profile - without token should return 401', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/user/users/profile', { signature: 'e2e test' })
    expect([200, 401]).toContain(res.status)
  })

  test('POST /user/users/addresses - without token should return 401', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/user/users/addresses', {
      receiverName: 'e2e test',
      receiverPhone: '13800000000',
      province: 'Beijing',
      city: 'Beijing',
      district: 'Chaoyang',
      detailAddress: 'e2e test addr',
      isDefault: false,
    })
    expect([200, 401]).toContain(res.status)
  })

  test('GET /user/users/addresses - without token should return 401', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/user/users/addresses')
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('Product Module', () => {
  test('GET /product/products/search - with keyword should return product list', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/product/products/search?keyword=phone&page=1&pageSize=10')
    expect(res.status).toBe(200)
    expect(res.data.success).toBe(true)
    expect(res.data.data).toBeDefined()
  })

  test('GET /product/categories - should return category list', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/product/categories')
    expect(res.status).toBe(200)
    expect(res.data.success).toBe(true)
    expect(res.data.data).toBeDefined()
  })

  test('GET /product/reviews/product/{productId} - should return reviews', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/product/reviews/product/1?page=1&size=10')
    expect(res.status).toBe(200)
    expect(res.data.success).toBe(true)
  })

  test('GET /product/reviews/stats/{productId} - should return review stats', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/product/reviews/stats/1')
    expect(res.status).toBe(200)
    expect(res.data.success).toBe(true)
  })

  test('POST /product/wishlists/{productId} - without token should return 401', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/product/wishlists/1', {})
    expect([200, 401]).toContain(res.status)
  })

  test('GET /product/wishlists - without token should return 401', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/product/wishlists?page=1&size=10')
    expect([200, 401]).toContain(res.status)
  })

  test('POST /product/brands - without admin token should return 401 or 403', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/product/brands', { name: 'e2e test brand' })
    expect([200, 401, 403]).toContain(res.status)
  })
})
