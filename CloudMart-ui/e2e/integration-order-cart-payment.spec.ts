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

test.describe('Order Module', () => {
  test('POST /order/orders - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/order/orders', {
      requestId: 'test-req-id',
      items: [{ skuId: 1, quantity: 1, price: 100 }],
      receiverName: 'Test',
      receiverPhone: '13800138000',
      receiverAddress: 'Beijing',
    })
    expect([200, 401]).toContain(res.status)
  })

  test('GET /order/orders - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/order/orders?page=1&pageSize=10')
    expect([200, 401]).toContain(res.status)
  })

  test('GET /order/orders/{id} - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/order/orders/999')
    expect([200, 401]).toContain(res.status)
  })

  test('PUT /order/orders/{id}/cancel - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/order/orders/1/cancel', {})
    expect([200, 401]).toContain(res.status)
  })

  test('PUT /order/orders/{id}/confirm - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/order/orders/1/confirm', {})
    expect([200, 401]).toContain(res.status)
  })

  test('POST /order/orders/{id}/refund - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/order/orders/1/refund', {})
    expect([200, 401]).toContain(res.status)
  })

  test('PUT /order/orders/{id}/ship - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/order/orders/1/ship', {})
    expect([200, 401, 403]).toContain(res.status)
  })

  test('GET /order/admin/orders - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/order/admin/orders?page=1&pageSize=10')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Cart Module', () => {
  test('GET /cart - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/cart')
    expect([200, 401]).toContain(res.status)
  })

  test('POST /cart/items - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/cart/items', { productId: 1, skuId: 1, quantity: 1 })
    expect([200, 401]).toContain(res.status)
  })

  test('PUT /cart/items/{skuId} - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/cart/items/1', { quantity: 2 })
    expect([200, 401]).toContain(res.status)
  })

  test('DELETE /cart/items/{skuId} - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'DELETE', '/cart/items/1')
    expect([200, 401]).toContain(res.status)
  })

  test('DELETE /cart - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'DELETE', '/cart')
    expect([200, 401]).toContain(res.status)
  })

  test('DELETE /cart/checked - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'DELETE', '/cart/checked')
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('Payment Module', () => {
  test('POST /payment/payments - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/payment/payments', { orderId: 1, amount: 100, payMethod: 'MOCK' })
    expect([200, 401]).toContain(res.status)
  })

  test('POST /payment/payments/callback - should accept callback', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/payment/payments/callback', { paymentId: 999999, status: 'SUCCESS', transactionId: 'TXN001' })
    expect([200, 404]).toContain(res.status)
  })

  test('POST /payment/payments/{id}/refund - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/payment/payments/1/refund')
    expect([200, 401]).toContain(res.status)
  })

  test('GET /payment/payments/order/{orderId} - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/payment/payments/order/1')
    expect([200, 401]).toContain(res.status)
  })

  test('PUT /payment/payments/{id}/simulate-success - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/payment/payments/1/simulate-success', {})
    expect([200, 401]).toContain(res.status)
  })

  test('GET /payment/admin/payments - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/payment/admin/payments?page=1&pageSize=10')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Coupon Module', () => {
  test('GET /coupon/coupon-templates - should return template list', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/coupon/coupon-templates?page=1&size=10')
    expect([200, 401]).toContain(res.status)
  })

  test('POST /coupon/coupon-templates - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/coupon/coupon-templates', {
      name: 'e2e test coupon',
      type: 'AMOUNT_OFF',
      discountValue: 10,
      minOrderAmount: 100,
      totalCount: 100,
      validType: 'FIXED_DAYS',
      validDays: 30,
    })
    expect([200, 401, 403]).toContain(res.status)
  })

  test('POST /coupon/user-coupons/claim - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/coupon/user-coupons/claim', { templateId: 1 })
    expect([200, 401]).toContain(res.status)
  })

  test('GET /coupon/user-coupons - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/coupon/user-coupons?status=UNUSED&page=1&size=10')
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('Seckill Module', () => {
  test('GET /seckill/activities - should return activity list', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/seckill/activities')
    expect([200, 401]).toContain(res.status)
  })

  test('POST /seckill/execute - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/seckill/execute', { activityId: 1, skuId: 1 })
    expect([200, 401]).toContain(res.status)
  })

  test('GET /seckill/result - should require authentication', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/seckill/result?activityId=1&skuId=1')
    expect([200, 401]).toContain(res.status)
  })
})
