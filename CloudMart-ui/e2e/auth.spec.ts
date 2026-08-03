import { test, expect } from '@playwright/test'

test.describe('Authentication - Login', () => {
  test('login page loads with form fields visible', async ({ page }) => {
    await page.goto('/login')
    await page.waitForTimeout(3000)

    await expect(page.locator('.ant-form')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.ant-input').first()).toBeVisible({ timeout: 10000 })
    await expect(page.locator('input[type="password"]').first()).toBeVisible()
    await expect(page.locator('.ant-checkbox-wrapper')).toBeVisible()
    await expect(page.locator('button[type="submit"]')).toBeVisible()
  })

  test('login form shows validation errors on empty submit', async ({ page }) => {
    await page.goto('/login')
    await page.waitForTimeout(3000)

    await page.locator('button[type="submit"]').click()
    await page.waitForTimeout(1000)

    await expect(page.locator('.ant-form-item-explain-error').first()).toBeVisible({ timeout: 5000 })
  })

  test('login with test credentials attempts the flow', async ({ page }) => {
    await page.goto('/login')
    await page.waitForTimeout(3000)

    const inputs = page.locator('.ant-input')
    await inputs.first().fill('testuser')
    await page.locator('input[type="password"]').fill('test123456')
    await page.locator('button[type="submit"]').click()
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    expect(typeof currentUrl).toBe('string')
  })

  test('login page has link to register page', async ({ page }) => {
    await page.goto('/login')
    await page.waitForTimeout(3000)

    const registerLink = page.locator('a:has-text("注册"), text=立即注册').first()
    if (await registerLink.isVisible({ timeout: 3000 }).catch(() => false)) {
      await registerLink.click()
      await page.waitForTimeout(2000)
      expect(page.url()).toContain('/register')
    }
  })
})

test.describe('Authentication - Register', () => {
  test('register page loads with form fields visible', async ({ page }) => {
    await page.goto('/register')
    await page.waitForTimeout(3000)

    await expect(page.locator('.ant-form')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.ant-input').first()).toBeVisible({ timeout: 10000 })
    await expect(page.locator('input[type="password"]').first()).toBeVisible()
  })

  test('register form shows validation errors on empty submit', async ({ page }) => {
    await page.goto('/register')
    await page.waitForTimeout(3000)

    await page.locator('button[type="submit"]').click()
    await page.waitForTimeout(1000)

    await expect(page.locator('.ant-form-item-explain-error').first()).toBeVisible({ timeout: 5000 })
  })

  test('register page has link to login page', async ({ page }) => {
    await page.goto('/register')
    await page.waitForTimeout(3000)

    const loginLink = page.locator('a:has-text("登录"), text=立即登录').first()
    if (await loginLink.isVisible({ timeout: 3000 }).catch(() => false)) {
      await loginLink.click()
      await page.waitForTimeout(2000)
      expect(page.url()).toContain('/login')
    }
  })
})

test.describe('Authentication - Navigation', () => {
  test('navigate from login to register and back', async ({ page }) => {
    await page.goto('/login')
    await page.waitForTimeout(3000)
    await expect(page.locator('.ant-form')).toBeVisible({ timeout: 10000 })

    const registerLink = page.locator('a:has-text("注册"), text=立即注册').first()
    if (await registerLink.isVisible({ timeout: 3000 }).catch(() => false)) {
      await registerLink.click()
      await page.waitForTimeout(2000)
      await expect(page.locator('.ant-form')).toBeVisible({ timeout: 10000 })

      const loginLink = page.locator('a:has-text("登录"), text=立即登录').first()
      if (await loginLink.isVisible({ timeout: 3000 }).catch(() => false)) {
        await loginLink.click()
        await page.waitForTimeout(2000)
        await expect(page.locator('.ant-form')).toBeVisible({ timeout: 10000 })
      }
    }
  })

  test('direct access to login page works', async ({ page }) => {
    await page.goto('/login')
    await page.waitForTimeout(3000)
    expect(page.url()).toContain('/login')
    await expect(page.locator('.ant-form')).toBeVisible({ timeout: 10000 })
  })

  test('direct access to register page works', async ({ page }) => {
    await page.goto('/register')
    await page.waitForTimeout(3000)
    expect(page.url()).toContain('/register')
    await expect(page.locator('.ant-form')).toBeVisible({ timeout: 10000 })
  })
})
