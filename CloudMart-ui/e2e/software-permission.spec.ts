import { test, expect } from '@playwright/test'

test.use({ channel: process.env.PLAYWRIGHT_CHANNEL })

for (const memberPage of [false, true]) {
test(`管理员在${memberPage ? '会员管理' : '发布权限'}页可设置三种模式`, async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('admin_access_token', 'mock-admin-token'))
  const writes: unknown[] = []
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const ok = (data: unknown) => route.fulfill({ json: { success: true, data } })
    if (path === '/api/admin/profile') return ok({ id: '1', username: 'admin', permissions: ['*:*:*'], roles: [] })
    if (path.endsWith('/publisher-permissions/42/toggle')) {
      writes.push(route.request().postDataJSON())
      return ok(true)
    }
    if (path === '/api/admin/business/members') return ok({ records: [{ id: '42', username: 'tester', nickname: '测试会员', status: 1 }], total: 1 })
    if (path.endsWith('/publisher-permissions/batch')) return ok([])
    if (path.endsWith('/publisher-permissions')) return ok({
      records: [{ userId: '42', userNickname: '测试会员', canPublish: true, publishMode: 'REVIEW' }], total: 1,
    })
    return ok([])
  })
  await page.goto(memberPage ? '/admin/system/users' : '/admin/software/publishers')
  for (const [mode, label] of [['DIRECT', '可直接发布'], ['REVIEW', '发布后需审核'], ['DENIED', '禁止发布']]) {
    if (memberPage) await page.getByRole('button', { name: '发布资格', exact: true }).click()
    else await page.getByText('更改发布权限', { exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '设置发布权限' })
    await dialog.getByText(label, { exact: true }).click()
    await expect(dialog.getByRole('radio', { name: label, exact: true })).toBeChecked()
    await dialog.getByPlaceholder('原因（1~500 字，必填，写入权限审计）').fill('调整发布方式')
    await dialog.getByRole('button', { name: /^确\s*认$/ }).click()
    if (!memberPage) await page.getByRole('button', { name: '确认变更', exact: true }).click()
    await expect(dialog).not.toBeVisible()
    expect(writes.at(-1)).toEqual({ publishMode: mode, reason: '调整发布方式' })
  }
})
}

test('默认禁止的用户无法打开发布表单', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('access_token', 'mock-user-token'))
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const data = path.endsWith('/publisher-status') ? { canPublish: false, publishMode: 'DENIED' } : []
    await route.fulfill({ json: { success: true, data } })
  })
  await page.goto('/software/publish')
  await expect(page.getByText('尚未开通发布资格', { exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('软件名称', { exact: true })).toHaveCount(0)
})
