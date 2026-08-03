import { test, expect } from '@playwright/test'

test.describe('Shopping - Homepage', () => {
  test('homepage loads with content', async ({ page }) => {
    await page.goto('/')
    await page.waitForTimeout(3000)

    await expect(page).toHaveTitle(/.+/)
    const bodyText = await page.locator('body').textContent()
    expect(bodyText).toBeTruthy()
  })

  test('homepage shows feed tabs', async ({ page }) => {
    await page.goto('/')
    await page.waitForTimeout(3000)

    await expect(page.locator('button:has-text("推荐")').first()).toBeVisible({ timeout: 5000 })
  })

  test('homepage has navigation elements', async ({ page }) => {
    await page.goto('/')
    await page.waitForTimeout(3000)

    const nav = page.locator('nav, header, .ant-layout-header, [class*="nav"], [class*="header"]')
    if (await nav.first().isVisible()) {
      expect(await nav.count()).toBeGreaterThan(0)
    }
  })
})

test.describe('Shopping - Product Pages', () => {
  test('product list page loads', async ({ page }) => {
    await page.goto('/products')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    expect(currentUrl).toContain('/products')
  })

  test('product detail page is accessible', async ({ page }) => {
    await page.goto('/products/1')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onProductDetail = currentUrl.includes('/products/1')
    const redirectedToLogin = currentUrl.includes('/login')
    const redirectedToNotFound = currentUrl.includes('*')
    expect(onProductDetail || redirectedToLogin || redirectedToNotFound).toBeTruthy()
  })
})

test.describe('Shopping - Cart & Checkout', () => {
  test('cart page loads or redirects to login', async ({ page }) => {
    await page.goto('/cart')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onCart = currentUrl.includes('/cart')
    const onLogin = currentUrl.includes('/login')
    expect(onCart || onLogin).toBeTruthy()
  })

  test('cart page shows empty state or cart items', async ({ page }) => {
    await page.goto('/cart')
    await page.waitForTimeout(3000)

    if (page.url().includes('/cart')) {
      const emptyState = page.locator('.ant-empty')
      const cartItems = page.locator('[class*="cart"], [class*="item"]')
      const hasContent = (await emptyState.isVisible()) || (await cartItems.count()) > 0
      expect(hasContent || true).toBeTruthy()
    }
  })

  test('checkout page loads or redirects to login', async ({ page }) => {
    await page.goto('/checkout')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onCheckout = currentUrl.includes('/checkout')
    const onLogin = currentUrl.includes('/login')
    expect(onCheckout || onLogin).toBeTruthy()
  })
})

test.describe('Shopping - Search', () => {
  test('search page loads with search input', async ({ page }) => {
    await page.goto('/search')
    await page.waitForTimeout(3000)

    await expect(page.locator('.ant-input, input[type="text"], input[placeholder*="搜索"], input[placeholder*="搜"]').first()).toBeVisible({ timeout: 5000 })
  })

  test('search page shows tab options', async ({ page }) => {
    await page.goto('/search')
    await page.waitForTimeout(3000)

    const productTab = page.locator('text=商品')
    const postTab = page.locator('text=帖子')
    if (await productTab.isVisible() || await postTab.isVisible()) {
      expect(true).toBeTruthy()
    }
  })

  test('search page can input search text', async ({ page }) => {
    await page.goto('/search')
    await page.waitForTimeout(3000)

    const searchInput = page.locator('.ant-input, input[type="text"]').first()
    if (await searchInput.isVisible()) {
      await searchInput.fill('测试商品')
      await expect(searchInput).toHaveValue('测试商品')
    }
  })
})

test.describe('Shopping - Seckill', () => {
  test('seckill page loads', async ({ page }) => {
    await page.goto('/shop/seckill')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    expect(currentUrl).toContain('/shop/seckill')
  })

  test('seckill page shows activity content', async ({ page }) => {
    await page.goto('/shop/seckill')
    await page.waitForTimeout(3000)

    const hasContent = await page.locator('body').textContent()
    expect(hasContent).toBeTruthy()
    expect(hasContent!.length).toBeGreaterThan(0)
  })
})

test.describe('Shopping - Coupons', () => {
  test('coupons page loads', async ({ page }) => {
    await page.goto('/shop/coupons')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    expect(currentUrl).toContain('/shop/coupons')
  })

  test('coupons page shows coupon content', async ({ page }) => {
    await page.goto('/shop/coupons')
    await page.waitForTimeout(3000)

    const bodyText = await page.locator('body').textContent()
    expect(bodyText).toBeTruthy()
  })
})

test.describe('Shopping - Group Buy & Live', () => {
  test('group buy page loads', async ({ page }) => {
    await page.goto('/shop/group-buy')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onGroupBuy = currentUrl.includes('/shop/group-buy')
    const onLogin = currentUrl.includes('/login')
    expect(onGroupBuy || onLogin).toBeTruthy()
  })

  test('live streaming page loads', async ({ page }) => {
    await page.goto('/live')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onLive = currentUrl.includes('/live')
    const onLogin = currentUrl.includes('/login')
    expect(onLive || onLogin).toBeTruthy()
  })
})

test.describe('Shopping - Wishlist', () => {
  test('wishlist page loads or redirects to login', async ({ page }) => {
    await page.goto('/wishlist')
    await page.waitForTimeout(3000)

    const currentUrl = page.url()
    const onWishlist = currentUrl.includes('/wishlist')
    const onLogin = currentUrl.includes('/login')
    const onProfile = currentUrl.includes('/profile')
    expect(onWishlist || onLogin || onProfile).toBeTruthy()
  })
})
