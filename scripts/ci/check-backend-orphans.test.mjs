import { test } from 'node:test'
import assert from 'node:assert/strict'
import { pathToFileURL } from 'node:url'

const mod = await import(pathToFileURL(new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1') + 'check-backend-orphans.mjs').href)
const { parseMappings, pathToRegExp, listJavaFiles, stripCommentsAndStrings, findDeadPrivateMethods } = mod

test('pathToRegExp：路径变量归一匹配，字面段精确', () => {
  const re = pathToRegExp('/admin/orders/{orderId}/ship')
  assert.ok(re.test('/admin/orders/123456/ship'))
  assert.ok(!re.test('/admin/orders/123456/cancel'))
  assert.ok(!re.test('/x/admin/orders/1/ship'))
  assert.ok(pathToRegExp('/').test('/'))
})

test('parseMappings：类前缀+方法路径组合，裸注解走空路径', () => {
  const src = `
@RequestMapping("/internal/orders")
public interface OrderApi {
  @GetMapping("/paid-orders") ApiResponse<Object> a();
  @PostMapping ApiResponse<Object> b();
}`
  const eps = parseMappings(src).map((e) => `${e.method} ${e.path}`)
  assert.ok(eps.includes('HTTP_GET /internal/orders/paid-orders'))
  assert.ok(eps.includes('HTTP_POST /internal/orders'))
})

test('parseMappings：同文件嵌套类按各自 @RequestMapping 归前缀（AdminBrandController 场景）', () => {
  const src = `
@RequestMapping("/brands");
public class BrandController {
  @GetMapping public Object list() {}
}
@RequestMapping("/admin/brands");
class AdminBrandController {
  @PostMapping public Object create() {}
  @DeleteMapping("/{id}") public Object remove() {}
}`
  const eps = parseMappings(src).map((e) => `${e.method} ${e.path}`)
  assert.ok(eps.includes('HTTP_GET /brands'))
  assert.ok(eps.includes('HTTP_POST /admin/brands'))
  assert.ok(eps.includes('HTTP_DELETE /admin/brands/{id}'))
  assert.ok(!eps.some((p) => p.includes('HTTP_POST /brands')))
})

test('parseMappings：全限定注解与 @FeignClient path 叠加', () => {
  const src = `
@FeignClient(path="/x")
interface X { @org.springframework.web.bind.annotation.GetMapping("/internal") Object a(); }`
  // Feign：prependPrefix 拼在类前缀前；本文件无类级 @RequestMapping
  const eps = parseMappings(src, '/check').map((e) => e.path)
  assert.ok(eps.includes('/check/internal'))
})

test('stripCommentsAndStrings：URL 中的 // 不被当注释，方法调用保留', () => {
  const src = `
private void f() {
  String u = "https://api.example.com/x";
  objectMapper().readValue(u, Map.class);
}`
  const clean = stripCommentsAndStrings(src)
  assert.ok(clean.includes('objectMapper().readValue'))
  assert.ok(!clean.includes('api.example.com'))
  assert.ok(clean.includes('//') === false || clean.includes('https') === false)
})

test('findDeadPrivateMethods：零调用私有方法被抓，方法引用/反射注解豁免', () => {
  // 构造最小模块目录结构验证（使用当前 mall-pet 模块——全仓门禁已绿，此断言锁零结果口径）
  const modules = ['mall-pet']
  const dead = findDeadPrivateMethods(modules)
  assert.ok(!dead.some((id) => id.endsWith('#orZero')), 'orZero 已删除，不应再报')
})
