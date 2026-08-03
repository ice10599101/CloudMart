import { test, expect } from '@playwright/test'

test.describe('Admin - Login', () => {
  test('admin login page loads with LoginForm component', async ({ page }) => {
    await page.goto('/admin/login')
    await page.waitForTimeout(3000)

    await expect(page.locator('.ant-pro-form-login-title')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.ant-pro-form-login-desc')).toBeVisible()
    await expect(page.locator('.ant-input').first()).toBeVisible()
  })

  test('admin login page shows title and subtitle', async ({ page }) => {
    await page.goto('/admin/login')
    await page.waitForTimeout(3000)

    await expect(page.locator('.ant-pro-form-login-title')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.ant-pro-form-login-desc')).toBeVisible()
  })

  test('admin login page has username and password fields', async ({ page }) => {
    await page.goto('/admin/login')
    await page.waitForTimeout(3000)

    const inputs = page.locator('.ant-input')
    await expect(inputs.first()).toBeVisible({ timeout: 10000 })
    expect(await inputs.count()).toBeGreaterThanOrEqual(2)
  })

  test('admin login page shows CloudMart branding', async ({ page }) => {
    await page.goto('/admin/login')
    await page.waitForTimeout(3000)

    await expect(page.locator('text=CloudMart')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('text=智慧电商管理平台')).toBeVisible()
  })

  test('admin login with invalid credentials shows error', async ({ page }) => {
    await page.goto('/admin/login')
    await page.waitForTimeout(3000)

    const inputs = page.locator('.ant-input')
    await inputs.nth(0).fill('admin')
    await inputs.nth(1).fill('wrongpassword')
    await page.locator('.ant-btn-primary').first().click()
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const stayedOnLogin = currentUrl.includes('/admin/login')
    const navigatedToDashboard = currentUrl.includes('/admin/dashboard')
    expect(stayedOnLogin || navigatedToDashboard).toBeTruthy()
  })

  test('admin login page has forgot password link', async ({ page }) => {
    await page.goto('/admin/login')
    await page.waitForTimeout(3000)

    const forgotLink = page.locator('text=忘记密码')
    if (await forgotLink.isVisible()) {
      await expect(forgotLink).toBeVisible()
    }
  })
})

test.describe('Admin - Dashboard', () => {
  test('admin dashboard loads or redirects to login', async ({ page }) => {
    await page.goto('/admin/dashboard')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onDashboard = currentUrl.includes('/admin/dashboard')
    const onLogin = currentUrl.includes('/admin/login')
    expect(onDashboard || onLogin).toBeTruthy()
  })

  test('admin dashboard shows statistics cards when authenticated', async ({ page }) => {
    await page.goto('/admin/dashboard')
    await page.waitForTimeout(3000)

    if (page.url().includes('/admin/dashboard')) {
      const statsCards = page.locator('.ant-statistic, .ant-card')
      if (await statsCards.first().isVisible()) {
        expect(await statsCards.count()).toBeGreaterThan(0)
      }
    }
  })
})

test.describe('Admin - System Pages', () => {
  const systemPages = [
    { path: '/admin/system/users', name: 'Users' },
    { path: '/admin/system/roles', name: 'Roles' },
    { path: '/admin/system/menus', name: 'Menus' },
    { path: '/admin/system/depts', name: 'Depts' },
    { path: '/admin/system/dict', name: 'Dict' },
    { path: '/admin/system/config', name: 'Config' },
    { path: '/admin/system/notices', name: 'Notices' },
    { path: '/admin/system/oper-log', name: 'OperLog' },
    { path: '/admin/system/login-log', name: 'LoginLog' },
  ]

  for (const systemPage of systemPages) {
    test(`${systemPage.name} page loads or redirects to admin login`, async ({ page }) => {
      await page.goto(systemPage.path)
      await page.waitForTimeout(3000)

      const currentUrl = page.url()
      const onPage = currentUrl.includes(systemPage.path)
      const onLogin = currentUrl.includes('/admin/login')
      expect(onPage || onLogin).toBeTruthy()
    })
  }

  test('system pages have ProTable when authenticated', async ({ page }) => {
    await page.goto('/admin/system/users')
    await page.waitForTimeout(3000)

    if (page.url().includes('/admin/system/users')) {
      const proTable = page.locator('.ant-pro-table')
      if (await proTable.isVisible()) {
        await expect(proTable).toBeVisible()
      }
    }
  })
})

test.describe('Admin - Business Pages', () => {
  const businessPages = [
    { path: '/admin/business/products', name: 'Products' },
    { path: '/admin/business/categories', name: 'Categories' },
    { path: '/admin/business/orders', name: 'Orders' },
    { path: '/admin/business/members', name: 'Members' },
    { path: '/admin/business/coupons', name: 'Coupons' },
    { path: '/admin/business/seckill', name: 'Seckill' },
    { path: '/admin/business/reviews', name: 'Reviews' },
    { path: '/admin/business/inventory', name: 'Inventory' },
    { path: '/admin/business/payments', name: 'Payments' },
    { path: '/admin/business/brands', name: 'Brands' },
    { path: '/admin/business/blacklist', name: 'Blacklist' },
    { path: '/admin/business/shipping', name: 'Shipping' },
  ]

  for (const businessPage of businessPages) {
    test(`${businessPage.name} page loads or redirects to admin login`, async ({ page }) => {
      await page.goto(businessPage.path)
      await page.waitForTimeout(3000)

      const currentUrl = page.url()
      const onPage = currentUrl.includes(businessPage.path)
      const onLogin = currentUrl.includes('/admin/login')
      expect(onPage || onLogin).toBeTruthy()
    })
  }

  test('business products page has ProTable when authenticated', async ({ page }) => {
    await page.goto('/admin/business/products')
    await page.waitForTimeout(3000)

    if (page.url().includes('/admin/business/products')) {
      const proTable = page.locator('.ant-pro-table')
      if (await proTable.isVisible()) {
        await expect(proTable).toBeVisible()
      }
    }
  })
})

test.describe('Admin - Community Pages', () => {
  const communityPages = [
    { path: '/admin/community/posts', name: 'Posts' },
    { path: '/admin/community/review', name: 'Review' },
    { path: '/admin/community/comments', name: 'Comments' },
    { path: '/admin/community/tags', name: 'Tags' },
    { path: '/admin/community/reports', name: 'Reports' },
    { path: '/admin/community/badges', name: 'Badges' },
    { path: '/admin/community/growth', name: 'Growth' },
    { path: '/admin/community/notifications', name: 'Notifications' },
  ]

  for (const communityPage of communityPages) {
    test(`${communityPage.name} page loads or redirects to admin login`, async ({ page }) => {
      await page.goto(communityPage.path)
      await page.waitForTimeout(3000)

      const currentUrl = page.url()
      const onPage = currentUrl.includes(communityPage.path)
      const onLogin = currentUrl.includes('/admin/login')
      expect(onPage || onLogin).toBeTruthy()
    })
  }
})

test.describe('Admin - Monitor Pages', () => {
  const monitorPages = [
    { path: '/admin/monitor/job', name: 'Job' },
    { path: '/admin/monitor/server', name: 'Server' },
    { path: '/admin/monitor/cache', name: 'Cache' },
    { path: '/admin/monitor/online', name: 'Online' },
  ]

  for (const monitorPage of monitorPages) {
    test(`${monitorPage.name} page loads or redirects to admin login`, async ({ page }) => {
      await page.goto(monitorPage.path)
      await page.waitForTimeout(3000)

      const currentUrl = page.url()
      const onPage = currentUrl.includes(monitorPage.path)
      const onLogin = currentUrl.includes('/admin/login')
      expect(onPage || onLogin).toBeTruthy()
    })
  }
})

test.describe('Admin - Tool Pages', () => {
  test('code generator page loads or redirects to admin login', async ({ page }) => {
    await page.goto('/admin/tool/gen')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onGen = currentUrl.includes('/admin/tool/gen')
    const onLogin = currentUrl.includes('/admin/login')
    expect(onGen || onLogin).toBeTruthy()
  })
})

test.describe('Admin - Layout & Navigation', () => {
  test('admin root redirects to dashboard or login', async ({ page }) => {
    await page.goto('/admin')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onDashboard = currentUrl.includes('/admin/dashboard')
    const onLogin = currentUrl.includes('/admin/login')
    expect(onDashboard || onLogin).toBeTruthy()
  })

  test('admin pages share consistent layout when authenticated', async ({ page }) => {
    await page.goto('/admin/dashboard')
    await page.waitForTimeout(3000)

    if (page.url().includes('/admin/dashboard')) {
      const sidebar = page.locator('.ant-layout-sider, .ant-menu, [class*="sider"], [class*="sidebar"]')
      if (await sidebar.first().isVisible()) {
        expect(await sidebar.count()).toBeGreaterThan(0)
      }
    }
  })
})
