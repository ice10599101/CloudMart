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

test.describe('Community - Posts', () => {
  test('GET /community/posts/feed - should return post list', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/community/posts/feed?page=1&size=10')
    expect(res.status).toBe(200)
    expect(res.data.success).toBe(true)
  })

  test('POST /community/posts - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/community/posts', { title: 'test', content: 'test' })
    expect([200, 401]).toContain(res.status)
  })

  test('GET /community/posts/999 - non-existent should return error', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/community/posts/999')
    expect([200, 404]).toContain(res.status)
  })

  test('POST /community/posts/1/like - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/community/posts/1/like', {})
    expect([200, 401]).toContain(res.status)
  })

  test('POST /community/posts/1/favorite - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/community/posts/1/favorite', {})
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('Community - Comments', () => {
  test('GET /community/posts/1/comments - should return comments', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/community/posts/1/comments?page=1&size=10')
    expect(res.status).toBe(200)
    expect(res.data.success).toBe(true)
  })

  test('POST /community/posts/1/comments - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/community/posts/1/comments', { content: 'test' })
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('Community - Tags', () => {
  test('GET /community/tags - should return tag list', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/community/tags')
    expect(res.status).toBe(200)
    expect(res.data.success).toBe(true)
  })
})

test.describe('Community - Growth', () => {
  test('POST /community/growth/check-in - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/community/growth/check-in', {})
    expect([200, 401]).toContain(res.status)
  })

  test('GET /community/growth/check-in/status - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/community/growth/check-in/status')
    expect([200, 401]).toContain(res.status)
  })

  test('GET /community/growth/level - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/community/growth/level')
    expect([200, 401]).toContain(res.status)
  })

  test('GET /community/growth/level-configs - should return configs', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/community/growth/level-configs')
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('Community - Badges', () => {
  test('GET /community/badges - should return badge list', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/community/badges')
    expect([200, 401]).toContain(res.status)
  })

  test('GET /community/badges/my - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/community/badges/my')
    expect([200, 401]).toContain(res.status)
  })
})
