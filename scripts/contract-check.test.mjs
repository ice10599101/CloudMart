import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DYNAMIC_MARKER,
  evaluateContract,
  extractFrontendCalls,
  matchRoute,
  methodCompatible,
  normalizeExemptions,
  parseJavaEndpoints,
} from './contract-check.mjs'

const dyn = DYNAMIC_MARKER

test('路径匹配：后端 {id} 只占一段，不得吞斜杠', () => {
  assert.equal(matchRoute('/admin/orders/1', '/admin/orders/{id}'), true)
  assert.equal(matchRoute('/admin/orders/1/items', '/admin/orders/{id}'), false)
  assert.equal(matchRoute('/admin/orders/1', '/admin/orders/*'), true)
  assert.equal(matchRoute('/admin/orders/1/items', '/admin/orders/*'), false)
})

test('路径匹配：静态前缀/后缀错误必须不匹配', () => {
  assert.equal(matchRoute('/admin/peta/1', '/admin/pet/{id}'), false)
  assert.equal(matchRoute('/admin/pet/1/extra', '/admin/pet/{id}'), false)
  assert.equal(matchRoute('/admin/pet', '/admin/pet/{id}'), false)
})

test('路径匹配：未解析的前端表达式可跨段（configType=configs/jobs）', () => {
  assert.equal(matchRoute(`/admin/pet/${dyn}`, '/admin/pet/configs/jobs'), true)
  assert.equal(matchRoute(`/admin/pet/${dyn}`, '/admin/pet/configs/studies'), true)
  assert.equal(matchRoute(`/admin/pet/${dyn}`, '/admin/other/configs/jobs'), false)
})

test('方法必须一致；后端 ANY 兼容任意方法', () => {
  assert.equal(methodCompatible('GET', 'GET'), true)
  assert.equal(methodCompatible('GET', 'POST'), false)
  assert.equal(methodCompatible('POST', 'ANY'), true)
  assert.equal(methodCompatible('DELETE', 'ANY'), true)
})

test('后端解析：无参 @GetMapping 不得吞下一个注解的参数', () => {
  const src = `
package demo;
@RestController
@RequestMapping("/profile")
public class AdminProfileController {
    @GetMapping
    @Operation(summary = "获取个人信息")
    public ApiResponse<Object> get() { return null; }

    @PutMapping("/password")
    public ApiResponse<Void> password() { return null; }
}
`
  const endpoints = parseJavaEndpoints('demo/AdminProfileController.java', src)
  assert.deepEqual(
    endpoints.map((e) => `${e.method} ${e.path}`).sort(),
    ['GET /profile', 'PUT /profile/password'],
  )
})

test('后端解析：多路径数组、RequestMapping 显式方法、多个 Controller', () => {
  const src = `
@RestController
@RequestMapping(value = {"/a", "/b"}, produces = "application/json")
class FirstController {
    @GetMapping({"/x", "/y"})
    public void x() {}
    @RequestMapping(value = "/z", method = RequestMethod.POST)
    public void z() {}
}

@RestController
@RequestMapping("/second")
class SecondController {
    @DeleteMapping("/{id}")
    public void del() {}
}
`
  const endpoints = parseJavaEndpoints('demo/Multi.java', src)
  assert.deepEqual(
    endpoints.map((e) => `${e.method} ${e.path}`).sort(),
    ['DELETE /second/{id}', 'GET /a/x', 'GET /a/y', 'GET /b/x', 'GET /b/y', 'POST /a/z', 'POST /b/z'],
  )
})

test('前端解析：常量 BASE、局部 const url 不被同名声明覆盖', () => {
  const src = `
const BASE = '/admin/software'
export function list() {
  return request.get<ApiResponse<PageResult>>(\`\${BASE}/items\`, { params })
}
export function ban(softwareId: string) {
  const url = \`\${BASE}/items/\${softwareId}/ban\`
  return request.post<ApiResponse<boolean>>(url, payload)
}
export function createTopic(name: string) {
  const url = \`\${BASE}/topics/\${name}\`
  return request.post<ApiResponse<string>>(url, { name })
}
`
  const calls = extractFrontendCalls('demo/software.ts', src)
  assert.deepEqual(
    calls.map((c) => `${c.method} ${c.path}`),
    [`GET /admin/software/items`, `POST /admin/software/items/${dyn}/ban`, `POST /admin/software/topics/${dyn}`],
  )
})

test('前端解析：忽略非 /admin 路径与无法解析的实参', () => {
  const src = `
request.get('/software/list')
request.post(someConfig)
request.put('/admin/pet/' + configType + '/' + id + '/enabled')
`
  const calls = extractFrontendCalls('demo/mixed.ts', src)
  assert.deepEqual(calls.map((c) => `${c.method} ${c.path}`), [`PUT /admin/pet/${dyn}/${dyn}/enabled`])
})

test('契约评估：方法错、路径错、前缀错都会红', () => {
  const endpoints = [{ method: 'POST', path: '/admin/software/items', handler: 'x' }]
  const result = evaluateContract({
    endpoints,
    calls: [
      { method: 'GET', path: '/admin/software/items', at: 'a.ts:1' },
      { method: 'POST', path: '/admin/software/item', at: 'a.ts:2' },
      { method: 'POST', path: '/admin/other/items', at: 'a.ts:3' },
    ],
  })
  assert.equal(result.errors.filter((e) => e.includes('[漏代理]')).length, 3)
})

test('契约评估：豁免默认降级为 WARN，strict 下仍失败', () => {
  const endpoints = [{ method: 'POST', path: '/admin/x', handler: 'x' }]
  const calls = [{ method: 'GET', path: '/admin/x', at: 'a.ts:1' }]
  const exemptions = {
    broken: [{ method: 'GET', path: '/admin/x', owner: 'team-a', reason: '待补后端', expires: '2030-01-01', key: 'GET /admin/x' }],
    unused: [],
  }
  const relaxed = evaluateContract({ endpoints, calls, exemptions })
  assert.equal(relaxed.errors.length, 0)
  assert.equal(relaxed.warns.filter((w) => w.includes('漏代理·豁免')).length, 1)

  const strict = evaluateContract({ endpoints, calls, exemptions, strict: true })
  assert.equal(strict.errors.length, 1)
  assert.match(strict.errors[0], /strict/)
})

test('契约评估：问题消失后的豁免条目必须删除', () => {
  const result = evaluateContract({
    endpoints: [{ method: 'GET', path: '/admin/x', handler: 'x' }],
    calls: [{ method: 'GET', path: '/admin/x', at: 'a.ts:1' }],
    exemptions: {
      broken: [{ method: 'GET', path: '/admin/x', owner: 'team-a', reason: '已修复', expires: '2030-01-01', key: 'GET /admin/x' }],
      unused: [],
    },
  })
  assert.equal(result.errors.length, 1)
  assert.match(result.errors[0], /已无对应断链/)
})

test('契约评估：服务间专用前缀与运维探针不计未使用', () => {
  const endpoints = [
    { method: 'POST', path: '/admin/auth/validate', handler: 'auth' },
    { method: 'GET', path: '/admin/diagnostic/feign-test', handler: 'diag' },
  ]
  const result = evaluateContract({
    endpoints,
    calls: [{ method: 'GET', path: '/admin/anything', at: 'a.ts:1' }],
    serviceOnly: ['/auth'],
  })
  assert.equal(result.warns.length, 0)
})

test('豁免台账：缺 owner/reason、非法日期、过期均失败', () => {
  const { errors, broken } = normalizeExemptions(
    {
      broken: [
        { method: 'GET', path: '/a' },
        { method: 'GET', path: '/b', owner: 'x', reason: 'y', expires: '2030/01/01' },
        { method: 'GET', path: '/c', owner: 'x', reason: 'y', expires: '2020-01-01' },
      ],
    },
    '2026-10-19',
  )
  assert.equal(broken.length, 0)
  assert.equal(errors.length, 3)
  assert.match(errors[0], /缺 method\/path|缺 owner\/reason/)
  assert.match(errors[1], /expires 必须是/)
  assert.match(errors[2], /已.*到期/)
})
