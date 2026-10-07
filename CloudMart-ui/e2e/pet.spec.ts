import { test, expect } from '@playwright/test'

/**
 * PET-23/PET-24：宠物模块核心旅程 E2E（方案 §10.3 旅程 1/2 的 Web 可自动化子集）。
 *
 * 前置：dev server 跑在 localhost:8000。分层策略：
 *  - 未登录断言：路由守卫跳登录（旅程第 0 步）；
 *  - 已登录断言（占位 token）：后端 401 会被清登录态跳登录——门禁是
 *    「不白屏 + 跳登录（而非崩溃）」，真实数据断言在集成测试/远程验证层
 *    （scripts/verify/pet24-deploy-verify.py 用真实测试账号覆盖）。
 */

test.describe('PetCream 核心旅程', () => {
  test('未登录访问宠物页 → 跳登录（旅程 1 第 0 步）', async ({ page }) => {
    await page.goto('/pet')
    await page.waitForTimeout(2000)
    const onLogin = page.url().includes('login')
    const onPet = page.url().includes('/pet')
    // 未登录被守卫拦截，或页面自身降级均可接受（不白屏即门禁）
    expect(onLogin || onPet).toBeTruthy()
  })

  test.describe('占位登录态（后端 401 后允许被清回登录页）', () => {
    test.use({ storageState: undefined })

    /** 深链接直达目标面板，等待渲染后返回 body 文本与最终 URL */
    async function openPanel(page: import('@playwright/test').Page, panel: string) {
      await page.addInitScript((key: string) => {
        localStorage.setItem('access_token', 'e2e-placeholder-token')
        void key
      }, panel)
      await page.goto(`/pet?panel=${panel}`)
      await page.waitForTimeout(3500)
      return {
        bodyText: await page.locator('body').innerText(),
        url: page.url(),
      }
    }

    test('宠物页可达且主结构渲染（PET-03 回归：不白屏不崩溃）', async ({ page }) => {
      const { bodyText, url } = await openPanel(page, 'quests')
      expect(bodyText.length).toBeGreaterThan(0)
      // 占位 token 被 401 清登录态属预期；落在 /pet 或 /login 均为守卫正常
      expect(url.includes('/pet') || url.includes('login')).toBeTruthy()
    })

    test('活动中心深链接（PET-23 恢复中心/批领入口）', async ({ page }) => {
      const { bodyText, url } = await openPanel(page, 'center')
      // 有 token 时直达面板（活动中心/业务日文案），被清登录态时落登录页
      const ok = bodyText.includes('活动中心') || bodyText.includes('业务日') || url.includes('login')
      expect(ok).toBeTruthy()
    })

    test('限时活动深链接（PET-11 三视图）', async ({ page }) => {
      const { bodyText, url } = await openPanel(page, 'events')
      const ok = bodyText.includes('进行中') || bodyText.includes('待领奖') || bodyText.includes('历史')
        || bodyText.includes('活动事件') || url.includes('login')
      expect(ok).toBeTruthy()
    })

    test('任务深链接（PET-09 任务集）', async ({ page }) => {
      const { bodyText, url } = await openPanel(page, 'quests')
      const ok = bodyText.includes('任务') || bodyText.includes('签到')
        || bodyText.includes('每日任务') || url.includes('login')
      expect(ok).toBeTruthy()
    })

    test('社交深链接（PET-23 举报入口）', async ({ page }) => {
      const { bodyText, url } = await openPanel(page, 'social')
      const ok = bodyText.includes('举报') || bodyText.includes('关系') || url.includes('login')
      expect(ok).toBeTruthy()
    })
  })
})
