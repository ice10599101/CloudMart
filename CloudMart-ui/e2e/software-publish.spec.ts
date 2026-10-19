import { test, expect } from '@playwright/test'

// 所有 API 都拦截，不创建真实草稿或提交真实审核。
test.use({ channel: process.env.PLAYWRIGHT_CHANNEL })

for (const saveFails of [false, true]) {
  test(`发布页提交使用当前表单，保存${saveFails ? '失败时停止提交' : '成功后再审核'}`, async ({ page }) => {
    const writes: Array<{ method: string; path: string; body: any }> = []
    let failSave = false
    await page.addInitScript(() => {
      localStorage.clear()
      localStorage.setItem('access_token', 'mock-token')
    })
    await page.route('**/api/**', async (route) => {
      const request = route.request()
      const path = new URL(request.url()).pathname
      const method = request.method()
      const ok = (data: unknown) => route.fulfill({ json: { success: true, data } })
      if (method === 'GET') {
        if (path.endsWith('/publisher-status')) return ok({ canPublish: true })
        if (path.includes('/user/profile')) return ok({ id: '42', nickname: '测试用户' })
        return ok([])
      }
      writes.push({ method, path, body: request.postDataJSON() })
      if (method === 'POST' && path === '/api/software') {
        return ok({ softwareId: '100', workingRevisionId: '501' })
      }
      if (method === 'PUT' && path === '/api/software/100/revisions/501') {
        if (failSave) return route.fulfill({ status: 500, json: {
          success: false, error: { code: 'INTERNAL_ERROR', message: '模拟保存失败' },
        } })
        return ok({ softwareId: '100', revisionId: '501' })
      }
      // 明确拒绝审核以保留页面，便于断言请求顺序；不会触达真实后端。
      return route.fulfill({ status: 400, json: {
        success: false, error: { code: 'CONTENT_REJECTED', message: '模拟审核拒绝' },
      } })
    })
    await page.goto('/software/publish')
    await page.getByPlaceholder('软件名称', { exact: true }).fill('初始软件')
    await page.getByPlaceholder('版本号，如 1.0.0').fill('1.0.0')
    await page.getByRole('button', { name: '保存草稿', exact: true }).click()
    await expect(page.getByText('草稿已保存', { exact: true })).toBeVisible()
    writes.length = 0
    failSave = saveFails
    await page.getByPlaceholder('软件名称', { exact: true }).fill('修改后的软件')
    await page.getByRole('button', { name: '提交审核', exact: true }).click()
    await page.getByRole('button', { name: '确认提交', exact: true }).click()
    await expect(page.getByText(saveFails ? '模拟保存失败' : '模拟审核拒绝', { exact: true })).toBeVisible()
    expect(writes.map(({ method, path }) => `${method} ${path}`)).toEqual(saveFails
      ? ['PUT /api/software/100/revisions/501']
      : ['PUT /api/software/100/revisions/501', 'POST /api/software/100/revisions/501/submit'])
    expect(writes[0].body.name).toBe('修改后的软件')
  })
}
