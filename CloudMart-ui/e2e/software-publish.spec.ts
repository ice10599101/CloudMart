import { test, expect } from '@playwright/test'

// 所有 API 都拦截，不创建真实草稿或提交真实审核。
test.use({ channel: process.env.PLAYWRIGHT_CHANNEL })

for (const mode of ['DIRECT', 'REVIEW'] as const) {
for (const saveFails of [false, true]) {
  test(`${mode} 发布页提交使用当前表单，保存${saveFails ? '失败时停止提交' : '成功后按权限发布'}`, async ({ page }) => {
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
        if (path.endsWith('/publisher-status')) return ok({ canPublish: true, publishMode: mode })
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
      return ok({ softwareId: '100', revisionId: '501', reviewStatus: mode === 'DIRECT' ? 'APPROVED' : 'PENDING' })
    })
    await page.goto('/software/publish')
    await page.getByPlaceholder('软件名称', { exact: true }).fill('初始软件')
    await page.getByPlaceholder('版本号，如 1.0.0').fill('1.0.0')
    await page.getByRole('button', { name: '保存草稿', exact: true }).click()
    await expect(page.getByText('草稿已保存', { exact: true })).toBeVisible()
    writes.length = 0
    failSave = saveFails
    await page.getByPlaceholder('软件名称', { exact: true }).fill('修改后的软件')
    await page.getByRole('button', { name: mode === 'DIRECT' ? '直接发布' : '提交审核', exact: true }).click()
    await page.getByRole('button', { name: mode === 'DIRECT' ? '确认发布' : '确认提交', exact: true }).click()
    await expect(page.getByText(saveFails ? '模拟保存失败' : mode === 'DIRECT' ? '软件已发布' : '已提交审核，审核通过后公开可见', { exact: true })).toBeVisible()
    expect(writes.map(({ method, path }) => `${method} ${path}`)).toEqual(saveFails
      ? ['PUT /api/software/100/revisions/501']
      : ['PUT /api/software/100/revisions/501', 'POST /api/software/100/revisions/501/submit'])
    expect(writes[0].body.name).toBe('修改后的软件')
  })
}

}

test('迁移 SW03 前本地草稿中的平台/架构多选，保存时展开为字符串配对', async ({ page }) => {
  const writes: Array<{ method: string; path: string; body: any }> = []
  await page.addInitScript(() => {
    localStorage.clear()
    localStorage.setItem('access_token', 'mock-token')
    localStorage.setItem('software-publish-draft', JSON.stringify({
      name: '旧版草稿',
      summary: '',
      descriptionHtml: '',
      licenseType: 'FREE',
      accessMode: 'REGISTERED',
      tagIds: [],
      releases: [{
        uid: 'release-1',
        versionName: '3.8.2',
        changelog: '',
        recommended: true,
        sources: [{
          uid: 'source-1',
          label: '网盘',
          platform: ['WINDOWS', 'LINUX'],
          arch: ['X64', 'ARM64'],
          sourceType: 'NETDISK',
          link: 'https://example.com/download',
          extractionCode: '',
        }],
      }],
      softwareId: '5',
      revisionId: '9',
    }))
  })
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    const ok = (data: unknown) => route.fulfill({ json: { success: true, data } })
    if (method === 'GET') {
      if (path.endsWith('/publisher-status')) return ok({ canPublish: true, publishMode: 'DIRECT' })
      if (path.includes('/user/profile')) return ok({ id: '42', nickname: '测试用户' })
      return ok([])
    }
    writes.push({ method, path, body: request.postDataJSON() })
    return ok({ softwareId: '5', revisionId: '9' })
  })

  await page.goto('/software/publish')
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page.getByText('草稿已保存', { exact: true })).toBeVisible()

  expect(writes.map(({ method, path }) => `${method} ${path}`)).toEqual([
    'PUT /api/software/5/revisions/9',
  ])
  const sources = writes[0].body.releases[0].sources
  expect(sources.map((source: any) => [source.platform, source.arch])).toEqual([
    ['WINDOWS', 'X64'],
    ['WINDOWS', 'ARM64'],
    ['LINUX', 'X64'],
    ['LINUX', 'ARM64'],
  ])
  expect(sources.every((source: any) => typeof source.platform === 'string' && typeof source.arch === 'string')).toBe(true)
})
