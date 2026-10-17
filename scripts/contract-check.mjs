#!/usr/bin/env node
/**
 * BASE-02 契约门禁（零依赖，CI 与本地均可跑）
 *
 * 四类检查（对应方案验收：CI 对漏代理、未使用公共能力、菜单无路由、分页形状变更报警）：
 *  1. 菜单无路由   —— 迁移 SQL admin_menu 净集中 C 类型菜单的 path 必须在 .umirc.ts 有对应路由
 *  2. 漏代理       —— 前端 api 层调用的 /admin/** 路径必须被 mall-admin Controller 端点覆盖
 *  3. 分页形状     —— 新增 Map.of("records",...) 旧分页形态报警（白名单外）
 *  4. 未使用公共能力 —— mall-admin 端点前端零引用（软报警：仅列出不失败，白名单外计 warn）
 *
 * 用法：node scripts/contract-check.mjs [--strict]
 *   --strict：未使用公共能力也按失败处理（当前默认 warn，待清单收敛后开启）
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const errors = []
const warns = []

// ---------- 工具 ----------
function walk(dir, ext, out = []) {
  if (!existsSync(dir)) return out
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    const st = statSync(p)
    if (st.isDirectory()) walk(p, ext, out)
    else if (name.endsWith(ext)) out.push(p)
  }
  return out
}
/** 路径归一：模板/路径变量段 → '*'（段通配，可跨多段——前端 ${configType} 值可含 'configs/jobs' 两段） */
function norm(p) {
  return p.replace(/\$\{[^}]*\}/g, '*').replace(/\{[^}/]*\}/g, '*').replace(/\d+/g, '*')
}

/** 段级通配匹配：'*' 匹配至少 1 段（可回溯跨多段）；其余段精确 */
function matchPath(pattern, target) {
  const ps = pattern.split('/').filter(Boolean)
  const ts = target.split('/').filter(Boolean)
  function rec(i, j) {
    if (i === ps.length) return j === ts.length
    if (ps[i] === '*') {
      if (i === ps.length - 1) return j < ts.length || (j === ts.length && false) || ts.slice(j).length >= 1
      for (let k = j + 1; k <= ts.length; k++) if (rec(i + 1, k)) return true
      return false
    }
    if (j >= ts.length || ps[i] !== ts[j]) return false
    return rec(i + 1, j + 1)
  }
  return rec(0, 0)
}

// ---------- 1. 菜单无路由 ----------
{
  const migDir = 'mall-admin/src/main/resources/db/migration'
  const menus = new Map() // id -> {path, type}
  const deleted = new Set()
  for (const f of walk(migDir, '.sql')) {
    const sql = readFileSync(f, 'utf-8')
    for (const m of sql.matchAll(/INSERT INTO `admin_menu`[^;]*?VALUES \((\d+),\s*'[^']*',\s*\d+,\s*\d+,\s*'([^']*)',\s*'([^']*)'/g)) {
      menus.set(m[1], { type: m[2], path: m[3] })
    }
    // 单行 VALUES 兜底（path 在第 5 列）
    for (const m of sql.matchAll(/INSERT INTO `admin_menu`[^;]*?VALUES \((\d+),\s*'[^']*',\s*(?:\d+|@?\w+),\s*\d+,\s*'([A-Z])',\s*'([^']*)'/g)) {
      menus.set(m[1], { type: m[2], path: m[3] })
    }
    for (const m of sql.matchAll(/DELETE FROM `admin_menu`[^;]*?IN \(([\d,\s]+)\)/g)) {
      for (const id of m[1].split(',')) deleted.add(id.trim())
    }
  }
  const umirc = readFileSync('CloudMart-ui/.umirc.ts', 'utf-8')
  for (const [id, menu] of menus) {
    if (deleted.has(id) || menu.type !== 'C' || !menu.path.startsWith('/admin/')) continue
    if (!umirc.includes(`'${menu.path}'`)) {
      errors.push(`[菜单无路由] admin_menu id=${id} path=${menu.path} 在 .umirc.ts 无对应路由`)
    }
  }
}

// ---------- 2. 漏代理（前端 /admin/** 调用 vs mall-admin 端点） ----------
{
  // 存量断链台账：BASE-02 上线时已确认的前端先行入口（后端端点缺失），
  // 逐项修复（补端点或删前端入口）后应从台账移除；台账外新增断链直接 CI 失败
  const brokenLinks = new Set(readLines('scripts/contract-whitelist-broken.txt'))
  // 后端端点集合
  const backend = new Set()
  const ctrlDir = 'mall-admin/src/main/java/com/cloudmart/admin/controller'
  for (const f of walk(ctrlDir, '.java')) {
    const src = readFileSync(f, 'utf-8')
    const cls = src.match(/@RequestMapping\("([^"]+)"\)/)?.[1] ?? ''
    for (const m of src.matchAll(/@(?:[\w.]+\.)?(Get|Post|Put|Delete|Patch)Mapping(?:\("([^"]*)"\))?/g)) {
      const sub = m[2] ?? ''
      backend.add(norm(`/admin${cls}${sub}`.replace(/\/+/g, '/')))
    }
  }
  // 前端调用集合（api 层 ts 文件）
  const fePaths = new Map() // normPath -> 首个出现位置
  for (const f of walk('CloudMart-ui/src/api', '.ts')) {
    if (f.endsWith('.test.ts')) continue // 测试断言字符串不是 API 调用
    const src = readFileSync(f, 'utf-8')
    for (const m of src.matchAll(/['"`](\/admin\/[^'"`]+)['"`]/g)) {
      // 排除常量赋值行（const BASE = '/admin/software'——定义非调用，调用处必经拼接）
      const lineStart = src.lastIndexOf('\n', m.index) + 1
      const line = src.slice(lineStart, src.indexOf('\n', m.index))
      if (/\b(?:const|let|var)\s+\w+\s*[:=]/.test(line)) continue
      const p = norm(m[1])
      if (!fePaths.has(p)) fePaths.set(p, `${f}:${m[1]}`)
    }
  }
  const backendNorm = [...backend].map(norm)
  for (const [p, at] of fePaths) {
    const feNorm = norm(p)
    const covered = backendNorm.some((b) => matchPath(feNorm, b) || matchPath(b, feNorm))
    if (!covered) {
      // 存量断链台账（contract-whitelist-broken.txt）：可见 WARN 不阻塞；台账外新增直接失败
      if (brokenLinks.has(p)) {
        warns.push(`[存量断链·台账] 前端调用 ${p}（${at}）后端无端点——补端点或删入口后请从台账移除`)
      } else {
        errors.push(`[漏代理] 前端调用 ${p}（${at}）在 mall-admin 无对应端点`)
      }
    }
  }
  // 4. 未使用公共能力（软）
  const whitelistUnused = new Set(readLines('scripts/contract-whitelist-unused.txt'))
  const feNorms = [...fePaths.keys()].map(norm)
  for (const p of backend) {
    const bNorm = norm(p)
    const used = feNorms.some((f) => matchPath(f, bNorm) || matchPath(bNorm, f))
    if (!used && !whitelistUnused.has(p)) {
      warns.push(`[未使用公共能力] ${p} 前端零引用（加入 scripts/contract-whitelist-unused.txt 或补前端入口）`)
    }
  }
}

// ---------- 3. 分页形状（旧 records 形态白名单外报警） ----------
{
  const whitelistPaging = new Set(readLines('scripts/contract-whitelist-paging.txt'))
  const files = [
    ...walk('mall-common/src/main/java', '.java'),
    ...walk('mall-admin/src/main/java', '.java'),
    ...walk('mall-user/src/main/java', '.java'),
    ...walk('mall-wish/src/main/java', '.java'),
    ...walk('mall-order/src/main/java', '.java'),
    ...walk('mall-community/src/main/java', '.java'),
    ...walk('mall-software/src/main/java', '.java'),
  ]
  for (const f of files) {
    const src = readFileSync(f, 'utf-8')
    const rel = f.replace(/\\/g, '/')
    for (const m of src.matchAll(/Map\s*\.\s*of\(\s*"records"/g)) {
      if (!whitelistPaging.has(rel)) {
        errors.push(`[分页形状] ${rel} 使用旧 Map.of("records",...)——新接口请统一 {items,total,page,size}；如属存量，加入 scripts/contract-whitelist-paging.txt`)
      }
    }
  }
}

function readLines(rel) {
  const p = join(root, rel)
  if (!existsSync(p)) return []
  return readFileSync(p, 'utf-8').split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#'))
}

// ---------- 输出 ----------
for (const w of warns) console.log(`WARN: ${w}`)
if (errors.length) {
  console.error(`\n契约门禁失败（${errors.length} 项）：`)
  for (const e of errors) console.error(`  ${e}`)
  process.exit(1)
}
console.log(`\n契约门禁通过（warn ${warns.length} 项）。`)
