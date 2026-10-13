import { test, expect, type APIRequestContext, type Page } from '@playwright/test'

/**
 * R28-5 后台 RBAC：角色-菜单-权限全链路 E2E
 *
 * 覆盖：
 * 1. 超管/无权限/只读客服的侧边栏菜单可见性（前端 toLayoutMenu 过滤规则）；
 * 2. 纵向越权：低权 token 直打管理写/读接口 → 403；
 * 3. 横向边界：C 端用户 token 打 /api/admin/** → 网关 403；匿名 → 401；
 * 4. 角色菜单变更后子账号重新登录，侧边栏实时增减（重新签发权限生效链路）。
 *
 * 前置：远程网关可达（本地经 npm run dev 的 /api 代理），测试账号已由超管创建。
 * 测试自身负责：角色菜单基线复位（beforeAll/afterAll）与临时公告清理。
 */

const ADMIN = { account: 'admin', password: 'admin123' }
const NONE_ACCOUNT = { account: 'r28e2e_none', password: 'Test@1234' }
const CS_ACCOUNT = { account: 'r28e2e_cs', password: 'Test@1234' }
const SWITCH_ACCOUNT = { account: 'r28e2e_switch', password: 'Test@1234' }
const OPS_ACCOUNT = { account: 'r28e2e_ops', password: 'Test@1234' }
const CUSTOMER_ACCOUNT = { account: 'e2e-user-a', password: '123456' }

const CS_ROLE_ID = '2109988358850662401'
const CS_BASE_MENU_IDS = [100, 1045, 1046]
const MENU_MANAGE_ID = 1020

interface LoginResult {
  status: number
  token: string
}

async function login(
  request: APIRequestContext,
  path: string,
  account: string,
  password: string,
): Promise<LoginResult> {
  // 经本地 dev 代理访问公网网关，偶发连接重置/5xx（非账号锁定）；
  // 401/403 为确定结果立即返回，网络错误与 5xx 有限次退避重试
  let lastStatus = 0
  for (let attempt = 1; attempt <= 6; attempt += 1) {
    try {
      const response = await request.post(path, {
        data: { account, password },
        timeout: 15000,
      })
      lastStatus = response.status()
      if (lastStatus === 401 || lastStatus === 403 || lastStatus === 200) {
        if (lastStatus !== 200) return { status: lastStatus, token: '' }
        const body = await response.json().catch(() => null)
        return { status: lastStatus, token: body?.data?.accessToken ?? '' }
      }
    } catch (error) {
      lastStatus = 0
      if (attempt === 6) throw error
    }
    await new Promise((resolve) => {
      setTimeout(resolve, 2000 * attempt)
    })
  }
  throw new Error(`login failed after retries: ${account} @ ${path}, lastStatus=${lastStatus}`)
}

async function adminLogin(request: APIRequestContext, account: string, password: string): Promise<string> {
  const result = await login(request, '/api/auth/admin/login', account, password)
  expect(result.status, `管理员登录应成功: ${account}`).toBe(200)
  expect(result.token).toBeTruthy()
  return result.token
}

async function setRoleMenus(request: APIRequestContext, roleId: string, menuIds: number[]): Promise<void> {
  const token = await adminLogin(request, ADMIN.account, ADMIN.password)
  const response = await request.put('/api/admin/roles/menus', {
    headers: { Authorization: `Bearer ${token}` },
    data: { roleId, menuIds },
    timeout: 15000,
  })
  expect(response.status(), '超管分配角色菜单应成功').toBe(200)
}

async function uiAdminLogin(page: Page, account: string, password: string): Promise<void> {
  // 实测当前构建按 browser history 生效（pathname 路由），以运行时事实为准
  await page.goto('/admin/login')
  await expect(page.locator('.ant-pro-form-login-title')).toBeVisible({ timeout: 20000 })

  // UI 登录请求经 dev 代理走公网，偶发瞬抖会停留在登录页；提交最多重试 3 次
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const inputs = page.locator('.ant-input')
    await inputs.nth(0).fill(account)
    await inputs.nth(1).fill(password)
    await page.locator('button[type="submit"], .ant-btn-primary').first().click()
    try {
      await page.waitForURL('**/admin/dashboard**', { timeout: 20000 })
      return
    } catch {
      if (attempt === 3) throw new Error(`UI 登录多次未跳转: ${account}`)
      await page.waitForTimeout(2000)
    }
  }
}

/** 顶级菜单渲染在侧边栏（menuitem 可访问名带图标前缀，如 "setting 系统管理"），点击目录展开二级 */
async function openSystemMenu(page: Page): Promise<void> {
  await page.getByRole('menuitem', { name: '系统管理' }).first().click()
  await expect(page.locator('aside.ant-layout-sider')).toBeVisible({ timeout: 10000 })
}

test.describe.serial('R28-5 后台 RBAC 角色菜单与越权', () => {
  test.beforeEach(() => {
    // 公网链路重试退避需要更宽超时
    test.setTimeout(120000)
  })

  test.beforeAll(async ({ request }) => {
    // 复位 cs 角色菜单基线，保证 r28e2e_cs / r28e2e_switch 的菜单断言确定性
    await setRoleMenus(request, CS_ROLE_ID, CS_BASE_MENU_IDS)
  })

  test.afterAll(async ({ request }) => {
    await setRoleMenus(request, CS_ROLE_ID, CS_BASE_MENU_IDS)
  })

  test.describe('UI：侧边栏菜单按角色过滤', () => {
    test('超管可见顶级目录与系统管理全部子菜单', async ({ page }) => {
      await uiAdminLogin(page, ADMIN.account, ADMIN.password)

      await expect(page.getByRole('menuitem', { name: '工作台' }).first()).toBeVisible()
      await expect(page.getByRole('menuitem', { name: '系统管理' }).first()).toBeVisible()
      await expect(page.getByRole('menuitem', { name: '业务管理' }).first()).toBeVisible()

      await openSystemMenu(page)
      const sider = page.locator('aside.ant-layout-sider')
      await expect(sider.getByText('用户管理', { exact: true })).toBeVisible()
      await expect(sider.getByText('菜单管理', { exact: true })).toBeVisible()
      await expect(sider.getByText('通知公告', { exact: true })).toBeVisible()
    })

    test('无权限管理员可见无 perms 兜底项，但不见任何授权子菜单', async ({ page }) => {
      await uiAdminLogin(page, NONE_ACCOUNT.account, NONE_ACCOUNT.password)

      // 工作台 perms=null 对所有登录管理员可见；业务/社区目录因子项含 perms 为空的菜单（文件管理/社区全量）而可见
      await expect(page.getByRole('menuitem', { name: '工作台' }).first()).toBeVisible()
      await expect(page.getByRole('menuitem', { name: '系统管理' }).first()).toBeVisible()

      await openSystemMenu(page)
      const sider = page.locator('aside.ant-layout-sider')
      await expect(sider.getByText('账号设置', { exact: true })).toBeVisible()
      expect(await sider.getByText('通知公告', { exact: true }).count()).toBe(0)
      expect(await sider.getByText('菜单管理', { exact: true }).count()).toBe(0)
      expect(await sider.getByText('用户管理', { exact: true }).count()).toBe(0)
    })

    test('只读客服可见通知公告、不可见菜单管理与用户管理', async ({ page }) => {
      await uiAdminLogin(page, CS_ACCOUNT.account, CS_ACCOUNT.password)

      await expect(page.getByRole('menuitem', { name: '工作台' }).first()).toBeVisible()

      await openSystemMenu(page)
      const sider = page.locator('aside.ant-layout-sider')
      await expect(sider.getByText('通知公告', { exact: true })).toBeVisible()
      expect(await sider.getByText('菜单管理', { exact: true }).count()).toBe(0)
      expect(await sider.getByText('用户管理', { exact: true }).count()).toBe(0)
    })
  })

  test.describe('API：纵向越权（低权 token 直打管理接口）', () => {
    test('无权限账号读写用户管理均 403', async ({ request }) => {
      const token = await adminLogin(request, NONE_ACCOUNT.account, NONE_ACCOUNT.password)

      const getResponse = await request.get('/api/admin/users/page?page=1&pageSize=1', {
        headers: { Authorization: `Bearer ${token}` },
        timeout: 15000,
      })
      expect(getResponse.status()).toBe(403)
      const getBody = await getResponse.json()
      expect(getBody.error.code).toBe('FORBIDDEN')

      const postResponse = await request.post('/api/admin/users', {
        headers: { Authorization: `Bearer ${token}` },
        data: { username: 'r28e2e_should_not_exist', password: 'Test@1234' },
        timeout: 15000,
      })
      expect(postResponse.status()).toBe(403)
      const postBody = await postResponse.json()
      expect(postBody.error.code).toBe('FORBIDDEN')
    })

    test('只读客服可查公告但不可新增（403）', async ({ request }) => {
      const token = await adminLogin(request, CS_ACCOUNT.account, CS_ACCOUNT.password)

      const getResponse = await request.get('/api/admin/notices/page?page=1&pageSize=1', {
        headers: { Authorization: `Bearer ${token}` },
        timeout: 15000,
      })
      expect(getResponse.status()).toBe(200)

      const postResponse = await request.post('/api/admin/notices', {
        headers: { Authorization: `Bearer ${token}` },
        data: { noticeTitle: 'r28e2e_cs_should_be_denied' },
        timeout: 15000,
      })
      expect(postResponse.status()).toBe(403)
      const postBody = await postResponse.json()
      expect(postBody.error.code).toBe('FORBIDDEN')
    })

    test('运营编辑可新增公告但不可删除，超管完成清理', async ({ request }) => {
      const opsToken = await adminLogin(request, OPS_ACCOUNT.account, OPS_ACCOUNT.password)
      const noticeTitle = `R28E2E自动化公告-${Date.now()}`

      const createResponse = await request.post('/api/admin/notices', {
        headers: { Authorization: `Bearer ${opsToken}` },
        data: { noticeTitle, noticeType: 1, noticeContent: 'R28 E2E 临时公告', status: 0 },
        timeout: 15000,
      })
      // 新增接口契约为 ApiResponse<Void>，不回传 id，需按标题查回
      expect(createResponse.status()).toBe(200)

      const listResponse = await request.get(
        `/api/admin/notices/page?page=1&pageSize=10&noticeTitle=${encodeURIComponent(noticeTitle)}`,
        { headers: { Authorization: `Bearer ${opsToken}` }, timeout: 15000 },
      )
      expect(listResponse.status()).toBe(200)
      const listBody = await listResponse.json()
      const noticeId = listBody.data?.records?.[0]?.id
      expect(noticeId, '新增公告应能按标题查回').toBeTruthy()

      // 运营编辑只有 add/edit，删除必须 403；公告仍存在，随后由超管清理
      const deniedDelete = await request.delete(`/api/admin/notices/${noticeId}`, {
        headers: { Authorization: `Bearer ${opsToken}` },
        timeout: 15000,
      })
      expect(deniedDelete.status()).toBe(403)

      const adminToken = await adminLogin(request, ADMIN.account, ADMIN.password)
      const cleanupDelete = await request.delete(`/api/admin/notices/${noticeId}`, {
        headers: { Authorization: `Bearer ${adminToken}` },
        timeout: 15000,
      })
      expect(cleanupDelete.status()).toBe(200)
    })
  })

  test.describe('网关边界：后台路径身份隔离', () => {
    test('C 端用户 token 打 /api/admin/** → 403，且其 C 端接口可用（token 有效锚点）', async ({ request }) => {
      const result = await login(request, '/api/auth/login', CUSTOMER_ACCOUNT.account, CUSTOMER_ACCOUNT.password)
      expect(result.status).toBe(200)
      const customerToken = result.token

      const meResponse = await request.get('/api/user/users/me', {
        headers: { Authorization: `Bearer ${customerToken}` },
        timeout: 15000,
      })
      expect(meResponse.status()).toBe(200)

      const adminResponse = await request.get('/api/admin/users/page?page=1&pageSize=1', {
        headers: { Authorization: `Bearer ${customerToken}` },
        timeout: 15000,
      })
      expect(adminResponse.status()).toBe(403)
      const adminBody = await adminResponse.json()
      expect(adminBody.error.code).toBe('FORBIDDEN')
      expect(adminBody.error.message).toContain('管理员权限')
    })

    test('匿名访问 /api/admin/** → 401', async ({ request }) => {
      const response = await request.get('/api/admin/users/page?page=1&pageSize=1', { timeout: 15000 })
      expect(response.status()).toBe(401)
      const body = await response.json()
      expect(body.error.code).toBe('UNAUTHORIZED')
    })
  })

  test.describe('角色菜单变更：重新登录后侧边栏实时刷新', () => {
    test('超管给角色追加菜单管理 → 子账号可见；撤销后重新登录消失', async ({ page, request }) => {
      try {
        await setRoleMenus(request, CS_ROLE_ID, [...CS_BASE_MENU_IDS, MENU_MANAGE_ID])

        await uiAdminLogin(page, SWITCH_ACCOUNT.account, SWITCH_ACCOUNT.password)
        await openSystemMenu(page)
        await expect(page.locator('aside.ant-layout-sider').getByText('菜单管理', { exact: true })).toBeVisible()

        // 清除会话，模拟用户重新登录拿到新权限
        await page.evaluate(() => {
          localStorage.clear()
        })
      } finally {
        await setRoleMenus(request, CS_ROLE_ID, CS_BASE_MENU_IDS)
      }

      await uiAdminLogin(page, SWITCH_ACCOUNT.account, SWITCH_ACCOUNT.password)
      await openSystemMenu(page)
      const sider = page.locator('aside.ant-layout-sider')
      await expect(sider.getByText('通知公告', { exact: true })).toBeVisible()
      expect(await sider.getByText('菜单管理', { exact: true }).count()).toBe(0)
    })
  })
})
