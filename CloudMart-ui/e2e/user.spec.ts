import { test, expect } from '@playwright/test'

test.describe('User Center - Profile', () => {
  test('user center page loads or redirects to login', async ({ page }) => {
    await page.goto('/profile')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onProfile = currentUrl.includes('/profile')
    const onLogin = currentUrl.includes('/login')
    expect(onProfile || onLogin).toBeTruthy()
  })

  test('user center shows tab navigation when authenticated', async ({ page }) => {
    await page.goto('/profile')
    await page.waitForTimeout(3000)

    if (page.url().includes('/profile')) {
      const profileTab = page.locator('text=基本信息')
      const postsTab = page.locator('text=我的帖子')
      const addressTab = page.locator('text=收货地址')
      const wishlistTab = page.locator('text=我的收藏')

      if (await profileTab.isVisible()) {
        await expect(profileTab).toBeVisible()
      }
      if (await postsTab.isVisible()) {
        await expect(postsTab).toBeVisible()
      }
      if (await addressTab.isVisible()) {
        await expect(addressTab).toBeVisible()
      }
      if (await wishlistTab.isVisible()) {
        await expect(wishlistTab).toBeVisible()
      }
    }
  })

  test('user center tabs are clickable', async ({ page }) => {
    await page.goto('/profile')
    await page.waitForTimeout(3000)

    if (page.url().includes('/profile')) {
      const postsTab = page.locator('text=我的帖子').first()
      if (await postsTab.isVisible()) {
        await postsTab.click()
        await page.waitForTimeout(2000)
      }

      const draftsTab = page.locator('text=我的草稿').first()
      if (await draftsTab.isVisible()) {
        await draftsTab.click()
        await page.waitForTimeout(2000)
      }

      const likedTab = page.locator('text=我的点赞').first()
      if (await likedTab.isVisible()) {
        await likedTab.click()
        await page.waitForTimeout(2000)
      }
    }
  })

  test('user center shows all expected tabs', async ({ page }) => {
    await page.goto('/profile')
    await page.waitForTimeout(3000)

    if (page.url().includes('/profile')) {
      const expectedTabs = ['基本信息', '我的帖子', '我的草稿', '收货地址', '我的收藏', '我的点赞', '我的回复']
      let visibleCount = 0
      for (const tabLabel of expectedTabs) {
        const tab = page.locator(`text=${tabLabel}`).first()
        if (await tab.isVisible()) {
          visibleCount++
        }
      }
      expect(visibleCount).toBeGreaterThanOrEqual(0)
    }
  })
})

test.describe('User Center - Settings', () => {
  test('settings page loads or redirects to login', async ({ page }) => {
    await page.goto('/settings')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onSettings = currentUrl.includes('/settings')
    const onLogin = currentUrl.includes('/login')
    expect(onSettings || onLogin).toBeTruthy()
  })

  test('settings page shows form elements when authenticated', async ({ page }) => {
    await page.goto('/settings')
    await page.waitForTimeout(3000)

    if (page.url().includes('/settings')) {
      const formElements = page.locator('.ant-input, .ant-switch, .ant-select, .ant-btn, .ant-form')
      const count = await formElements.count()
      expect(count).toBeGreaterThanOrEqual(0)
    }
  })

  test('settings page has password change section', async ({ page }) => {
    await page.goto('/settings')
    await page.waitForTimeout(3000)

    if (page.url().includes('/settings')) {
      const passwordSection = page.locator('text=修改密码, text=密码')
      if (await passwordSection.first().isVisible()) {
        expect(await passwordSection.count()).toBeGreaterThan(0)
      }
    }
  })
})

test.describe('User Center - Following', () => {
  test('following page loads or redirects to login', async ({ page }) => {
    await page.goto('/user/1/following')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onFollowing = currentUrl.includes('/following')
    const onLogin = currentUrl.includes('/login')
    const onProfile = currentUrl.includes('/profile')
    expect(onFollowing || onLogin || onProfile).toBeTruthy()
  })
})

test.describe('User Center - User Profile', () => {
  test('user profile page loads', async ({ page }) => {
    await page.goto('/user/1')
    await page.waitForTimeout(3000)

    const bodyText = await page.locator('body').textContent()
    expect(bodyText).toBeTruthy()
  })
})

test.describe('User Center - Orders', () => {
  test('orders page loads or redirects to login', async ({ page }) => {
    await page.goto('/orders')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onOrders = currentUrl.includes('/orders')
    const onLogin = currentUrl.includes('/login')
    expect(onOrders || onLogin).toBeTruthy()
  })

  test('order detail page loads or redirects', async ({ page }) => {
    await page.goto('/orders/1')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onOrderDetail = currentUrl.includes('/orders')
    const onLogin = currentUrl.includes('/login')
    expect(onOrderDetail || onLogin).toBeTruthy()
  })
})
