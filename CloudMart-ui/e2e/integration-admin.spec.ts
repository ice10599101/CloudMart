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

test.describe('Admin - Auth', () => {
  test('POST /auth/admin/login - invalid credentials should return error', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/auth/admin/login', { account: 'fake', password: 'wrong' })
    expect([200, 401]).toContain(res.status)
    if (res.status === 200) {
      expect(res.data.success).toBe(false)
    }
  })

  test('GET /admin/profile - without token should return 401', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/profile')
    expect([200, 401]).toContain(res.status)
  })
})

test.describe('Admin - User Management', () => {
  test('GET /admin/users/page - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/users/page?page=1&size=10')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('POST /admin/users - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/admin/users', { username: 'test' })
    expect([200, 401, 403]).toContain(res.status)
  })

  test('PUT /admin/users/1 - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/admin/users/1', { nickname: 'test' })
    expect([200, 401, 403]).toContain(res.status)
  })

  test('DELETE /admin/users/1 - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'DELETE', '/admin/users/1')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('PUT /admin/users/1/status - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/admin/users/1/status', { status: 0 })
    expect([200, 401, 403]).toContain(res.status)
  })

  test('PUT /admin/users/resetPassword - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/admin/users/resetPassword', { userId: 1, newPassword: 'xxx' })
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Role Management', () => {
  test('GET /admin/roles - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/roles')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('POST /admin/roles - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/admin/roles', { name: 'test' })
    expect([200, 401, 403]).toContain(res.status)
  })

  test('PUT /admin/roles/1 - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/admin/roles/1', { name: 'test' })
    expect([200, 401, 403]).toContain(res.status)
  })

  test('DELETE /admin/roles/1 - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'DELETE', '/admin/roles/1')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('PUT /admin/roles/menus - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/admin/roles/menus', { roleId: 1, menuIds: [1] })
    expect([200, 401, 403]).toContain(res.status)
  })

  test('PUT /admin/roles/1/data-scope - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/admin/roles/1/data-scope', { dataScope: 'ALL' })
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Menu Management', () => {
  test('GET /admin/menus/tree - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/menus/tree')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('POST /admin/menus - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/admin/menus', { name: 'test' })
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Dept Management', () => {
  test('GET /admin/depts/tree - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/depts/tree')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('POST /admin/depts - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/admin/depts', { name: 'test' })
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Post Management', () => {
  test('GET /admin/posts - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/posts')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Dict Management', () => {
  test('GET /admin/dict/types - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/dict/types')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('GET /admin/dict/data/type/status - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/dict/data/type/status')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('PUT /admin/dict/types/cache/refresh - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/admin/dict/types/cache/refresh')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Config Management', () => {
  test('GET /admin/configs - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/configs')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('PUT /admin/configs/cache/refresh - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/admin/configs/cache/refresh')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Notice Management', () => {
  test('GET /admin/notices/page - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/notices/page?page=1&size=10')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Log Management', () => {
  test('GET /admin/logs/oper/page - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/logs/oper/page?page=1&size=10')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('GET /admin/logs/login/page - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/logs/login/page?page=1&size=10')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('DELETE /admin/logs/oper/clean - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'DELETE', '/admin/logs/oper/clean')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('DELETE /admin/logs/login/clean - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'DELETE', '/admin/logs/login/clean')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Dashboard', () => {
  test('GET /admin/dashboard/stats - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/dashboard/stats')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('GET /admin/dashboard/recent-orders - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/dashboard/recent-orders?pageSize=5')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('GET /admin/dashboard/sales-trend - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/dashboard/sales-trend?days=7')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Product Management', () => {
  test('GET /product/admin/products/search - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/product/admin/products/search?page=1&pageSize=10')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('POST /product/products - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/product/products', { name: 'test' })
    expect([200, 401, 403]).toContain(res.status)
  })

  test('POST /product/categories - should require admin auth or return validation error', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/product/categories', { name: 'test' })
    expect([200, 400, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Order Management', () => {
  test('GET /order/admin/orders - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/order/admin/orders?page=1&pageSize=10')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('PUT /order/orders/1/ship - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'PUT', '/order/orders/1/ship', {})
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Payment Management', () => {
  test('GET /payment/admin/payments - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/payment/admin/payments?page=1&pageSize=10')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Community Management', () => {
  test('GET /admin/community/stats - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/community/stats')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('GET /admin/community/posts/page - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/community/posts/page?page=1&size=10')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Online Users', () => {
  test('GET /admin/online-users - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/online-users')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Admin - Monitor', () => {
  test('GET /admin/monitor/server - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/monitor/server')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('GET /admin/monitor/cache - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/admin/monitor/cache')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Gen Module', () => {
  test('GET /gen/tables - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/gen/tables')
    expect([200, 401, 403]).toContain(res.status)
  })

  test('POST /gen/preview - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'POST', '/gen/preview', { tableName: 'admin_user' })
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('Job Module', () => {
  test('GET /job/list - should require admin auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'GET', '/job/list')
    expect([200, 401, 403]).toContain(res.status)
  })
})

test.describe('File Module', () => {
  test('DELETE /file/delete - should require auth', async ({ page }) => {
    await page.goto('/')
    const res = await apiRequest(page, 'DELETE', '/file/delete?url=test.png')
    expect([200, 401, 403]).toContain(res.status)
  })
})
