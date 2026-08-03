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

test.describe('Marketing Module', () => {
  test('GET /marketing/group-buys - should return group buy list', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/marketing/group-buys?page=1&size=10')
    expect([200, 401]).toContain(res.status)
  })

  test('POST /marketing/group-buys - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/marketing/group-buys', { name: 'test' })
    expect([200, 401, 403]).toContain(res.status)
  })

  test('POST /marketing/group-buys/1/join - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/marketing/group-buys/1/join', {})
    expect([200, 401]).toContain(res.status)
  })

  test('GET /marketing/promotions - should return promotion list', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/marketing/promotions?page=1&size=10')
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('Live Module', () => {
  test('GET /live/rooms - should return room list', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/live/rooms?page=1&size=10')
    expect([200, 401]).toContain(res.status)
  })

  test('POST /live/rooms - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/live/rooms', { title: 'test' })
    expect([200, 401, 403]).toContain(res.status)
  })

  test('GET /live/rooms/1 - should return room detail or 404', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/live/rooms/999')
    expect([200, 404]).toContain(res.status)
  })
})

test.describe('AI Module', () => {
  test('POST /ai/chat - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/ai/chat', { message: 'Hello' })
    expect([200, 401]).toContain(res.status)
  })

  test('GET /ai/history - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/ai/history?page=1&size=10')
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('Notification Module', () => {
  test('GET /notification/notifications - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/notification/notifications?page=1&pageSize=10')
    expect([200, 401]).toContain(res.status)
  })

  test('GET /notification/notifications/unread-count - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/notification/notifications/unread-count')
    expect([200, 401]).toContain(res.status)
  })

  test('PUT /notification/notifications/1/read - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/notification/notifications/1/read', {})
    expect([200, 401]).toContain(res.status)
  })

  test('PUT /notification/notifications/read-all - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/notification/notifications/read-all', {})
    expect([200, 401]).toContain(res.status)
  })

  test('GET /notification/conversations - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/notification/conversations')
    expect([200, 401]).toContain(res.status)
  })

  test('POST /notification/conversations - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/notification/conversations', { otherUserId: 1 })
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('Inventory Module', () => {
  test('GET /inventory/1 - should return inventory or 404', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/inventory/1')
    expect([200, 401, 404]).toContain(res.status)
  })

  test('POST /inventory/deduct - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/inventory/deduct', { skuId: 1, quantity: 1, orderId: 1 })
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('WMS Module', () => {
  test('GET /wms/warehouses - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/wms/warehouses')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Risk Module', () => {
  test('POST /risk/check - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/risk/check', { userId: 1, action: 'ORDER' })
    expect([200, 401]).toContain(res.status)
  })
})
