#!/usr/bin/env node
/**
 * 契约门禁（T23 收严版）
 *
 * 目标：让"前端调了但后台没有"的断链在 CI 里必须变红，而不是以退出 0 + warning 掩盖。
 *
 * 检查项：
 *  1. 菜单无路由：admin_menu（C 类）的 path 必须在 CloudMart-ui/.umirc.ts 有对应路由。
 *  2. 漏代理（HTTP method + path）：前端 request.get/post/put/delete/patch 的后台路径
 *     必须被 mall-admin Controller 的真实映射覆盖。方法不一致即失败（旧版只比路径）。
 *  3. 未使用公共能力（软）：mall-admin 端点在三端/后台源码中零引用，列出供收敛；
 *     服务间专用前缀与运维探针不计。
 *  4. 分页形状：旧 Map.of("records", ...) 在允许清单外即失败。
 *
 * 路径语义：
 *  - 后端 `{id}` / `{id:.+}` / `*` 只匹配**单段**，`**` 匹配 0..n 段；不再用 `*` 吞斜杠。
 *  - 前端模板中的 `${expr}` 能解析为常量则按字面值；无法解析的表达式记为
 *    "动态段"（至少 1 段），因为业务值（如 configType）可能含多段。
 *  - 方法必须一致；后端 @RequestMapping 未限定方法时按 ANY 处理。
 *
 * 豁免：scripts/contract-exemptions.json，每条必须有 owner/reason/expires；
 * 到期即失败；问题已消失的豁免条目也算失败（防止僵尸豁免）。默认模式下
 * 有效豁免仍打印 WARN；--strict 下只要还存在"被豁免的断链"即失败——stric
 * 是收敛棘轮，不允许把当前缺陷永久锁进基线。未使用公共能力始终是软信号
 * （服务间专用前缀与运维探针已排除），供 T22/SW11 补入口时收敛。
 *
 * 用法：
 *   node scripts/contract-check.mjs            # 门禁：断链/过期豁免/分页形状失败
 *   node scripts/contract-check.mjs --strict   # 更严：未使用端点、任何断链豁免均失败
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(HERE, '..')

/** 网关 StripPrefix=2 后 mall-admin 收到的是 `/{controller}`；对外统一加 /admin 前缀。 */
const ADMIN_EXTERNAL_PREFIX = '/admin'
/** 运维探针：无用户菜单属预期，不计入"未使用公共能力"。 */
const OPS_PREFIXES = ['/diagnostic']
/** 前端动态表达式占位符：不能出现在真实 URL 中。导出供自测构造夹具。 */
export const DYNAMIC_MARKER = '\u0000'

// ==================== 通用 ====================

function read(rel) {
  return fs.readFileSync(path.join(ROOT, rel), 'utf-8')
}

function exists(rel) {
  return fs.existsSync(path.join(ROOT, rel))
}

function walk(dirRel, ext, out = []) {
  const dir = path.join(ROOT, dirRel)
  if (!fs.existsSync(dir)) return out
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name)
    const st = fs.statSync(full)
    if (st.isDirectory()) walk(path.relative(ROOT, full).replace(/\\/g, '/'), ext, out)
    else if (name.endsWith(ext)) out.push(path.relative(ROOT, full).replace(/\\/g, '/'))
  }
  return out
}

export function splitPath(p) {
  return p.split('?')[0].split('#')[0].split('/').filter(Boolean)
}

/** 后端路径模式：literal / single（{var} 或 *）/ multi（**）。 */
export function parseBackendPattern(p) {
  return splitPath(p).map((seg) => {
    if (seg === '**') return { type: 'multi' }
    if (seg === '*') return { type: 'single' }
    if (/^\{[^}]+\}$/.test(seg)) return { type: 'single' }
    return { type: 'literal', value: seg }
  })
}

/** 前端路径：literal / dynamic（未解析的 ${...}，可占 1..n 段）。 */
export function parseFrontendPattern(p) {
  return splitPath(p).map((seg) => (seg.includes(DYNAMIC_MARKER) ? { type: 'dynamic' } : { type: 'literal', value: seg }))
}

/**
 * 两条路径模式是否可能指向同一实际路径（语言交集判定）。
 *
 * 用于"前端调用是否被后端覆盖"：前端未解析的 `${expr}` 可能是多段（如 configType=
 * 'configs/jobs'），因此动态段可吸收 1..n 段；后端 `{id}` / `*` 仍是严格单段。
 * 代价：动态段内部无法静态验证，只保证静态前缀/后缀与 HTTP 方法一致。
 */
export function patternsIntersect(a, b, i = 0, j = 0) {
  if (i === a.length && j === b.length) return true
  if (i === a.length || j === b.length) return false
  const x = a[i]
  const y = b[j]
  if (x.type === 'literal' && y.type === 'literal') {
    return x.value === y.value && patternsIntersect(a, b, i + 1, j + 1)
  }
  if (x.type === 'dynamic' || x.type === 'multi') {
    const min = x.type === 'multi' ? 0 : 1
    for (let k = j + min; k <= b.length; k++) {
      if (patternsIntersect(a, b, i + 1, k)) return true
    }
    return false
  }
  if (y.type === 'dynamic' || y.type === 'multi') {
    const min = y.type === 'multi' ? 0 : 1
    for (let k = i + min; k <= a.length; k++) {
      if (patternsIntersect(a, b, k, j + 1)) return true
    }
    return false
  }
  // single / param 组合：各占一段
  return patternsIntersect(a, b, i + 1, j + 1)
}

/** 严格匹配：用于判定"前端调用是否被后端覆盖"。 */
export function matchRoute(frontendPath, backendPath) {
  return patternsIntersect(parseFrontendPattern(frontendPath), parseBackendPattern(backendPath))
}

/** 宽松匹配：语义同上，保留为"后端端点是否可能被前端调用"的命名。 */
export function mayReach(frontendPath, backendPath) {
  return matchRoute(frontendPath, backendPath)
}

export function methodCompatible(feMethod, beMethod) {
  return beMethod === 'ANY' || feMethod === beMethod
}

// ==================== Java 源码掩码 ====================

/**
 * 生成两份等长文本：
 * - clean：注释被空白替换，字符串保留（用于读取注解参数）
 * - masked：注释与字符串内容都被空白替换（用于结构识别，避免注释里的注解/花括号干扰）
 */
export function maskJava(src) {
  const clean = src.split('')
  const masked = src.split('')
  const n = src.length
  let i = 0
  const blank = (from, to) => {
    for (let k = from; k < to; k++) {
      if (src[k] !== '\n' && src[k] !== '\r') {
        clean[k] = ' '
        masked[k] = ' '
      }
    }
  }
  while (i < n) {
    const c = src[i]
    const d = src[i + 1]
    if (c === '/' && d === '/') {
      const start = i
      while (i < n && src[i] !== '\n') i++
      blank(start, i)
      continue
    }
    if (c === '/' && d === '*') {
      const start = i
      i += 2
      while (i < n && !(src[i] === '*' && src[i + 1] === '/')) i++
      i = Math.min(n, i + 2)
      blank(start, i)
      continue
    }
    // 文本块 """..."""
    if (c === '"' && d === '"' && src[i + 2] === '"') {
      const start = i
      i += 3
      while (i < n && !(src[i] === '"' && src[i + 1] === '"' && src[i + 2] === '"')) i++
      i = Math.min(n, i + 3)
      for (let k = start + 3; k < i - 3; k++) {
        if (src[k] !== '\n' && src[k] !== '\r') masked[k] = ' '
      }
      continue
    }
    if (c === '"' || c === "'") {
      const quote = c
      i++
      while (i < n) {
        if (src[i] === '\\') {
          masked[i] = ' '
          if (i + 1 < n) masked[i + 1] = ' '
          i += 2
          continue
        }
        if (src[i] === quote) break
        if (src[i] !== '\n' && src[i] !== '\r') masked[i] = ' '
        i++
      }
      i++
      continue
    }
    i++
  }
  return { clean: clean.join(''), masked: masked.join('') }
}

function matchBrace(masked, openIndex) {
  let depth = 0
  for (let i = openIndex; i < masked.length; i++) {
    if (masked[i] === '{') depth++
    else if (masked[i] === '}') {
      depth--
      if (depth === 0) return i
    }
  }
  return -1
}

/** 读取注解括号内的完整参数（处理嵌套括号/数组/字符串）。 */
function annotationArgs(text, openParenIndex) {
  let depth = 0
  let inString = null
  for (let i = openParenIndex; i < text.length; i++) {
    const c = text[i]
    if (inString) {
      if (c === '\\') i++
      else if (c === inString) inString = null
      continue
    }
    if (c === '"' || c === "'") {
      inString = c
      continue
    }
    if (c === '(') depth++
    else if (c === ')') {
      depth--
      if (depth === 0) return text.slice(openParenIndex + 1, i)
    }
  }
  return ''
}

/** 读取注解自身的参数；注解后紧跟的不是 `(` 时返回空串（否则会误吞下一个注解的参数）。 */
function argsAfterAnnotation(text, from) {
  let i = from
  while (i < text.length && /\s/.test(text[i])) i++
  if (text[i] !== '(') return ''
  return annotationArgs(text, i)
}

/** 从 @XxxMapping(...) 参数中取路径字面量；无路径返回 ['']。 */
export function annotationPaths(args) {
  if (!args || !args.trim()) return ['']
  const argText = args.trim()
  const keyMatch = argText.match(/(^|[,(\s])(?:value|path)\s*=/)
  let target = argText
  if (keyMatch) {
    target = argText.slice(keyMatch.index + keyMatch[0].length).trim()
  } else if (/(^|[,(\s])(?:method|produces|consumes|headers|params|name)\s*=/.test(argText)) {
    return ['']
  }
  if (target.startsWith('{')) {
    const end = target.indexOf('}')
    if (end >= 0) target = target.slice(0, end + 1)
  } else if (target.startsWith('"')) {
    let i = 1
    while (i < target.length && target[i] !== '"') {
      if (target[i] === '\\') i++
      i++
    }
    target = target.slice(0, i + 1)
  }
  const paths = [...target.matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((m) => m[1])
  return paths.length ? paths : ['']
}

function methodsOf(args, defaultMethod) {
  if (defaultMethod !== 'ANY') return [defaultMethod]
  if (!args) return ['ANY']
  const list = [...args.matchAll(/RequestMethod\.(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)/g)].map((m) => m[1])
  return list.length ? [...new Set(list)] : ['ANY']
}

/**
 * 解析一个 Java 文件里的全部 Controller 映射。
 * @returns {{method:string,path:string,handler:string}[]}
 */
export function parseJavaEndpoints(relFile, src) {
  const { clean, masked } = maskJava(src)
  const classes = []
  const classRe = /(^|[^\w$])class\s+([A-Za-z_$][\w$]*)/g
  let m
  while ((m = classRe.exec(masked))) {
    const keywordIndex = m.index + m[1].length
    const open = masked.indexOf('{', keywordIndex)
    if (open < 0) continue
    const close = matchBrace(masked, open)
    if (close < 0) continue
    // 只处理顶层类：类声明若落在已收集类的 body 内则跳过
    if (classes.some((c) => keywordIndex > c.bodyStart && keywordIndex < c.bodyEnd)) continue
    classes.push({ name: m[2], keywordIndex, bodyStart: open, bodyEnd: close })
  }

  const endpoints = []
  for (const cls of classes) {
    const regionStart = classes
      .filter((c) => c.bodyEnd < cls.keywordIndex)
      .reduce((acc, c) => Math.max(acc, c.bodyEnd), 0)
    const header = clean.slice(regionStart, cls.keywordIndex)
    if (!/@(?:[\w.]+\.)?(?:Rest)?Controller\b/.test(header)) continue

    let classPaths = ['']
    const reqMatches = [...header.matchAll(/@(?:[\w.]+\.)?RequestMapping\b/g)]
    if (reqMatches.length) {
      const last = reqMatches[reqMatches.length - 1]
      classPaths = annotationPaths(argsAfterAnnotation(header, last.index + last[0].length))
    }

    const body = clean.slice(cls.bodyStart + 1, cls.bodyEnd)
    const methodRe = /@(?:[\w.]+\.)?(Get|Post|Put|Delete|Patch)Mapping\b/g
    for (const mm of body.matchAll(methodRe)) {
      const args = argsAfterAnnotation(body, mm.index + mm[0].length)
      for (const p of annotationPaths(args)) {
        for (const cp of classPaths) {
          endpoints.push({ method: mm[1].toUpperCase(), path: joinPath(cp, p), handler: `${relFile}#${cls.name}` })
        }
      }
    }
    const anyRe = /@(?:[\w.]+\.)?RequestMapping\b/g
    for (const mm of body.matchAll(anyRe)) {
      const args = argsAfterAnnotation(body, mm.index + mm[0].length)
      for (const method of methodsOf(args, 'ANY')) {
        for (const p of annotationPaths(args)) {
          for (const cp of classPaths) {
            endpoints.push({ method, path: joinPath(cp, p), handler: `${relFile}#${cls.name}` })
          }
        }
      }
    }
  }
  return endpoints
}

function joinPath(prefix, sub) {
  const full = `${prefix || ''}/${sub || ''}`.replace(/\/+/g, '/')
  return full.length > 1 ? full.replace(/\/$/, '') : full
}

// ==================== 前端调用提取（零依赖语法扫描） ====================

function skipAngles(src, start) {
  let depth = 0
  for (let i = start; i < src.length; i++) {
    if (src[i] === '<') depth++
    else if (src[i] === '>') {
      depth--
      if (depth === 0) return i + 1
    } else if (src[i] === '\n' && i - start > 400) return -1
  }
  return -1
}

/** 从 openParen 之后取第一个实参表达式（顶层逗号/右括号截止）。 */
function firstArgument(src, openParen) {
  let i = openParen + 1
  let depth = 0
  let quote = null
  for (; i < src.length; i++) {
    const c = src[i]
    if (quote) {
      if (c === '\\') i++
      else if (c === quote) quote = null
      continue
    }
    if (c === '"' || c === "'" || c === '`') {
      quote = c
      continue
    }
    if (c === '(' || c === '[' || c === '{') depth++
    else if (c === ')' || c === ']' || c === '}') {
      if (depth === 0) break
      depth--
    } else if (c === ',' && depth === 0) break
  }
  return src.slice(openParen + 1, i).trim()
}

function unquote(text) {
  const q = text[0]
  let out = ''
  for (let i = 1; i < text.length; i++) {
    if (text[i] === '\\') {
      out += text[i + 1] ?? ''
      i++
      continue
    }
    if (text[i] === q) break
    out += text[i]
  }
  return out
}

function evalTemplate(text, decls, atIndex) {
  let out = ''
  let i = 1
  while (i < text.length) {
    const c = text[i]
    if (c === '`') break
    if (c === '\\') {
      out += text[i + 1] ?? ''
      i += 2
      continue
    }
    if (c === '$' && text[i + 1] === '{') {
      let depth = 1
      let j = i + 2
      let quote = null
      for (; j < text.length; j++) {
        const ch = text[j]
        if (quote) {
          if (ch === '\\') j++
          else if (ch === quote) quote = null
          continue
        }
        if (ch === '"' || ch === "'" || ch === '`') quote = ch
        else if (ch === '{') depth++
        else if (ch === '}') {
          depth--
          if (depth === 0) break
        }
      }
      const expr = text.slice(i + 2, j).trim()
      const ident = expr.match(/^([A-Za-z_$][\w$]*)$/)?.[1]
      if (ident) {
        const v = lookupConst(decls, ident, atIndex)
        out += v === null ? DYNAMIC_MARKER : v
      } else if (/^['"]/.test(expr)) out += unquote(expr)
      else out += DYNAMIC_MARKER
      i = j + 1
      continue
    }
    out += c
    i++
  }
  return out
}

/** 整个表达式是否就是一个字符串/模板字面量（避免把 `'/a' + id` 当作 '/a'）。 */
function isWholeQuotedLiteral(t) {
  const q = t[0]
  if (q !== '"' && q !== "'" && q !== '`') return false
  for (let i = 1; i < t.length; i++) {
    if (t[i] === '\\') {
      i++
      continue
    }
    if (t[i] === q) return i === t.length - 1
  }
  return false
}

/** 解析单个实参表达式为路径；无法解析返回 null。 */
export function evalPathExpression(expr, decls, atIndex) {
  const t = expr.trim()
  if (!t) return null
  if (isWholeQuotedLiteral(t) && t.startsWith('`')) return evalTemplate(t, decls, atIndex)
  if (isWholeQuotedLiteral(t)) return unquote(t)
  const direct = lookupConst(decls, t, atIndex)
  if (direct !== null) return direct
  // 顶层字符串拼接：'/a/' + id
  const parts = []
  let depth = 0
  let quote = null
  let last = 0
  for (let i = 0; i < t.length; i++) {
    const c = t[i]
    if (quote) {
      if (c === '\\') i++
      else if (c === quote) quote = null
      continue
    }
    if (c === '"' || c === "'" || c === '`') quote = c
    else if (c === '(' || c === '[' || c === '{') depth++
    else if (c === ')' || c === ']' || c === '}') depth--
    else if (c === '+' && depth === 0) {
      parts.push(t.slice(last, i))
      last = i + 1
    }
  }
  if (parts.length) {
    parts.push(t.slice(last))
    let out = ''
    for (const p of parts) {
      const v = evalPathExpression(p, decls, atIndex)
      out += v === null ? DYNAMIC_MARKER : v
    }
    return out
  }
  return null
}

/** 收集同一文件内的字符串常量声明（保留声明位置，避免同名局部变量互相覆盖）。 */
export function collectStringConstants(src) {
  const decls = []
  const re = /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*(?::\s*[^=]+)?=\s*(`(?:[^`\\]|\\.)*`|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')/g
  for (const m of src.matchAll(re)) {
    decls.push({ name: m[1], raw: m[2], index: m.index })
  }
  return decls
}

function lookupConst(decls, name, atIndex) {
  let found = null
  for (const d of decls) {
    if (d.name === name && d.index < atIndex) found = d
  }
  if (!found) return null
  return found.raw.startsWith('`') ? evalTemplate(found.raw, decls, found.index) : unquote(found.raw)
}

/**
 * 提取源码中的后台请求调用。
 * @returns {{method:string,path:string,at:string}[]}
 */
export function extractFrontendCalls(relFile, src) {
  const decls = collectStringConstants(src)
  const calls = []
  const re = /\brequest\s*\.\s*(get|post|put|delete|patch)\b/g
  let m
  while ((m = re.exec(src))) {
    let i = re.lastIndex
    while (i < src.length && /\s/.test(src[i])) i++
    if (src[i] === '<') {
      const next = skipAngles(src, i)
      if (next < 0) continue
      i = next
      while (i < src.length && /\s/.test(src[i])) i++
    }
    if (src[i] !== '(') continue
    const arg = firstArgument(src, i)
    const resolved = evalPathExpression(arg, decls, m.index)
    if (resolved === null) continue
    if (!resolved.startsWith('/admin')) continue
    const line = src.slice(0, m.index).split('\n').length
    calls.push({ method: m[1].toUpperCase(), path: resolved, at: `${relFile}:${line}` })
  }
  return calls
}

// ==================== 各项检查 ====================

/** 解析 SQL 的 VALUES 元组（支持多行、多组、字符串内逗号）。 */
function parseSqlTuples(text) {
  const tuples = []
  let i = 0
  while (i < text.length) {
    while (i < text.length && /\s|,/.test(text[i])) i++
    if (text[i] !== '(') break
    i++
    const values = []
    let current = ''
    let quote = null
    for (; i < text.length; i++) {
      const c = text[i]
      if (quote) {
        if (c === '\\') {
          current += text[i + 1] ?? ''
          i++
          continue
        }
        if (c === quote) {
          quote = null
          continue
        }
        current += c
        continue
      }
      if (c === "'" || c === '"') {
        quote = c
        continue
      }
      if (c === ',') {
        values.push(current.trim())
        current = ''
        continue
      }
      if (c === ')') {
        values.push(current.trim())
        tuples.push(values)
        i++
        break
      }
      current += c
    }
  }
  return tuples
}

function checkMenus(errors) {
  const migDir = 'mall-admin/src/main/resources/db/migration'
  const menus = new Map()
  const deleted = new Set()
  for (const f of walk(migDir, '.sql')) {
    const sql = read(f)
    for (const m of sql.matchAll(/INSERT\s+INTO\s+`admin_menu`\s*\(([^)]*)\)\s*VALUES\s*([\s\S]*?);/gi)) {
      const columns = m[1].split(',').map((c) => c.trim().replace(/`/g, '').toLowerCase())
      const idIdx = columns.indexOf('id')
      const pathIdx = columns.indexOf('path')
      const typeIdx = columns.indexOf('menu_type')
      if (idIdx < 0 || pathIdx < 0 || typeIdx < 0) continue
      for (const values of parseSqlTuples(m[2])) {
        const id = values[idIdx]
        if (!id || id.toUpperCase() === 'NULL') continue
        menus.set(id, { type: values[typeIdx], path: values[pathIdx] })
      }
    }
    for (const m of sql.matchAll(/DELETE\s+FROM\s+`admin_menu`[^;]*?IN\s*\(([\d,\s]+)\)/gi)) {
      for (const id of m[1].split(',')) deleted.add(id.trim())
    }
  }
  const umirc = read('CloudMart-ui/.umirc.ts')
  for (const [id, menu] of menus) {
    if (deleted.has(id) || menu.type !== 'C' || !menu.path?.startsWith('/admin/')) continue
    if (!umirc.includes(`'${menu.path}'`)) {
      errors.push(`[菜单无路由] admin_menu id=${id} path=${menu.path} 在 .umirc.ts 无对应路由`)
    }
  }
  return menus.size
}

/**
 * 校验豁免台账结构：owner/reason/expires 缺一不可，过期条目直接失败。
 * 导出以便自测（不依赖仓库文件）。
 */
export function normalizeExemptions(data, today, rel = 'scripts/contract-exemptions.json') {
  const errors = []
  const normalize = (list, kind) => {
    const out = []
    for (const [idx, entry] of (list ?? []).entries()) {
      const where = `${rel}#${kind}[${idx}]`
      if (!entry.method || !entry.path) {
        errors.push(`[豁免台账] ${where} 缺 method/path`)
        continue
      }
      if (!entry.owner || !entry.reason) {
        errors.push(`[豁免台账] ${where} 缺 owner/reason（豁免必须有责任人与机制说明）`)
        continue
      }
      if (!/^\d{4}-\d{2}-\d{2}$/.test(entry.expires ?? '')) {
        errors.push(`[豁免台账] ${where} expires 必须是 YYYY-MM-DD`)
        continue
      }
      if (entry.expires < today) {
        errors.push(`[豁免台账] ${where} 已于 ${entry.expires} 到期，必须修复后删除该条`)
        continue
      }
      out.push({ ...entry, method: entry.method.toUpperCase(), key: `${entry.method.toUpperCase()} ${entry.path}` })
    }
    return out
  }
  return {
    errors,
    broken: normalize(data.broken, 'broken'),
    unused: normalize(data.unused, 'unused'),
  }
}

function loadExemptions(errors) {
  const rel = 'scripts/contract-exemptions.json'
  if (!exists(rel)) return { broken: [], unused: [] }
  let data
  try {
    data = JSON.parse(read(rel))
  } catch (e) {
    errors.push(`[豁免台账] ${rel} 不是合法 JSON：${e.message}`)
    return { broken: [], unused: [] }
  }
  const normalized = normalizeExemptions(data, new Date().toISOString().slice(0, 10), rel)
  errors.push(...normalized.errors)
  return { broken: normalized.broken, unused: normalized.unused }
}

function checkPaging(errors) {
  const whitelist = new Set(readLines('scripts/contract-whitelist-paging.txt'))
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
    const src = read(f)
    if (!/Map\s*\.\s*of\(\s*"records"/.test(src)) continue
    if (!whitelist.has(f)) {
      errors.push(`[分页形状] ${f} 使用旧 Map.of("records",...)——列表契约应为 data 数组 + meta{page,pageSize,total}`)
    }
  }
}

function readLines(rel) {
  if (!exists(rel)) return []
  return read(rel)
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'))
}

/** mall-admin 仅接受服务令牌的入站前缀（/auth、/logs 等）：不算"前端应使用"的端点。 */
function serviceTokenPrefixes() {
  const yml = read('mall-admin/src/main/resources/application.yml')
  const prefixes = []
  let inSection = false
  let baseIndent = 0
  for (const line of yml.split('\n')) {
    if (/^\s*service-token-paths:\s*$/.test(line)) {
      inSection = true
      baseIndent = line.search(/\S/)
      continue
    }
    if (!inSection) continue
    const trimmed = line.trim()
    const indent = line.search(/\S/)
    if (trimmed && indent <= baseIndent && !trimmed.startsWith('-')) {
      inSection = false
      continue
    }
    const m = line.match(/^\s*-\s*prefix:\s*(\S+)/)
    if (m) prefixes.push(m[1].replace(/^['"]|['"]$/g, ''))
  }
  return prefixes
}

// ==================== 主流程 ====================

/**
 * 纯函数评估：给定端点、调用与豁免，返回错误/告警。
 * 便于用固定夹具验证"方法错/路径错/前缀错一定会红"。
 */
export function evaluateContract({
  endpoints,
  calls,
  exemptions = { broken: [], unused: [] },
  serviceOnly = [],
  opsPrefixes = OPS_PREFIXES,
  strict = false,
}) {
  const errors = []
  const warns = []
  const keyOf = (e) => e.key ?? `${(e.method ?? '').toUpperCase()} ${e.path}`
  const brokenExemptions = new Map((exemptions.broken ?? []).map((e) => [keyOf(e), e]))
  const unusedExemptions = new Map((exemptions.unused ?? []).map((e) => [keyOf(e), e]))
  const usedBrokenExemptions = new Set()
  const usedUnusedExemptions = new Set()

  // 漏代理：方法 + 路径
  for (const call of calls) {
    const covered = endpoints.some(
      (ep) => methodCompatible(call.method, ep.method) && matchRoute(call.path, ep.path),
    )
    if (covered) continue
    const key = `${call.method} ${call.path}`
    const exemption = brokenExemptions.get(key)
    if (exemption) {
      usedBrokenExemptions.add(key)
      if (strict) {
        errors.push(
          `[漏代理·strict] ${call.method} ${call.path}（${call.at}）仍在豁免台账（owner=${exemption.owner}，到期 ${exemption.expires}）`,
        )
      } else {
        warns.push(
          `[漏代理·豁免] ${call.method} ${call.path}（${call.at}）→ 后端无端点，豁免至 ${exemption.expires}（${exemption.owner}）`,
        )
      }
      continue
    }
    errors.push(`[漏代理] ${call.method} ${call.path}（${call.at}）在 mall-admin 无「方法+路径」匹配端点`)
  }

  // 未使用公共能力（软，供 T22/SW11 补入口；服务间专用与运维探针不计）
  const innerOf = (p) => (p.startsWith(`${ADMIN_EXTERNAL_PREFIX}/`) ? p.slice(ADMIN_EXTERNAL_PREFIX.length) : p)
  const matchesPrefix = (list, p) => {
    const inner = innerOf(p)
    return list.some((x) => inner === x || inner.startsWith(`${x}/`))
  }
  for (const ep of endpoints) {
    if (matchesPrefix(serviceOnly, ep.path) || matchesPrefix(opsPrefixes, ep.path)) continue
    const used = calls.some((call) => methodCompatible(call.method, ep.method) && mayReach(call.path, ep.path))
    if (used) continue
    const key = `${ep.method} ${ep.path}`
    if (unusedExemptions.has(key)) {
      usedUnusedExemptions.add(key)
      continue
    }
    warns.push(`[未使用公共能力] ${ep.method} ${ep.path} 前端零引用（补入口或登记 scripts/contract-exemptions.json）`)
  }

  // 僵尸豁免：问题已消失或键写错
  for (const [key, e] of brokenExemptions) {
    if (!usedBrokenExemptions.has(key)) {
      errors.push(`[豁免台账] broken 条目已无对应断链，请删除：${key}（${e.owner}）`)
    }
  }
  for (const [key, e] of unusedExemptions) {
    if (!usedUnusedExemptions.has(key)) {
      errors.push(`[豁免台账] unused 条目已无对应端点，请删除：${key}（${e.owner}）`)
    }
  }

  return { errors, warns }
}

export function run({ strict = false } = {}) {
  const errors = []
  const info = []

  const menuCount = checkMenus(errors)

  // 后端：mall-admin 全部 Controller（对外路径 = /admin + 控制器映射）
  const endpoints = []
  for (const f of walk('mall-admin/src/main/java/com/cloudmart/admin/controller', '.java')) {
    for (const ep of parseJavaEndpoints(f, read(f))) {
      endpoints.push({ ...ep, path: joinPath(ADMIN_EXTERNAL_PREFIX, ep.path) })
    }
  }
  if (!endpoints.length) errors.push('[漏代理] 未解析到任何 mall-admin 端点，扫描器疑似失效')

  // 前端：api 层 + 页面/组件中直接发起的请求
  const calls = []
  const seen = new Set()
  const scanRoots = ['CloudMart-ui/src/api', 'CloudMart-ui/src/pages', 'CloudMart-ui/src/components', 'CloudMart-ui/src/features']
  for (const dir of scanRoots) {
    for (const ext of ['.ts', '.tsx']) {
      for (const f of walk(dir, ext)) {
        if (f.endsWith(`.test${ext}`) || f.includes('/.umi/')) continue
        for (const call of extractFrontendCalls(f, read(f))) {
          const key = `${call.method} ${call.path}`
          if (seen.has(key)) continue
          seen.add(key)
          calls.push(call)
        }
      }
    }
  }
  if (!calls.length) errors.push('[漏代理] 未解析到任何 /admin 前端调用，扫描器疑似失效')

  const exemptions = loadExemptions(errors)
  const evaluated = evaluateContract({
    endpoints,
    calls,
    exemptions,
    serviceOnly: serviceTokenPrefixes(),
    strict,
  })
  errors.push(...evaluated.errors)

  checkPaging(errors)

  info.push(`菜单 ${menuCount} 条；mall-admin 端点 ${endpoints.length} 个；前台后台调用 ${calls.length} 条`)
  return { errors, warns: evaluated.warns, info }
}

function main() {
  const strict = process.argv.includes('--strict')
  const { errors, warns, info } = run({ strict })
  for (const w of warns) console.log(`WARN: ${w}`)
  for (const i of info) console.log(`INFO: ${i}`)
  if (errors.length) {
    console.error(`\n契约门禁失败（${errors.length} 项${strict ? '，strict' : ''}）：`)
    for (const e of errors) console.error(`  ${e}`)
    process.exit(1)
  }
  console.log(`\n契约门禁通过（warn ${warns.length} 项${strict ? '，strict' : ''}）。`)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
