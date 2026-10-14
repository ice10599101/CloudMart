#!/usr/bin/env node
/**
 * C36-2 后端可达性门禁（零依赖静态检查，不编译 Java）：
 *
 * A. private 方法零引用：各 mall 模块 src/main/java 内定义但同类无任何调用的 private 方法
 *    （C02 类死代码复发检测）。框架反射入口（@Scheduled/@PostConstruct/@PreDestroy/
 *    @EventListener/@JsonCreator/@JsonAnyGetter/@JsonAnySetter）与 private 构造器豁免。
 *
 * B. Feign → 下游 Controller 契约：每个 @FeignClient(name="mall-xxx") 声明的
 *    METHOD+路径必须能在 mall-xxx 模块的 @RequestMapping 组合中找到；断链（如多一层
 *    /admin 前缀的 R15 事故）在 CI 即红。外部服务（name 非 mall- 前缀，带 url）不核对。
 *
 * 局限（故意保守，只报确定项）：不解析 Spring 元注解组合、不做完整 Java 语法分析；
 * 反射/SpEL/MyBatis/MapStruct/SPI/AutoConfiguration 入口天然不产生 private 方法调用，
 * 若确属反射调用且无法改公开，走 backend-orphan-allowlist.json（必须写明调用机制）。
 *
 * 用法：node scripts/ci/check-backend-orphans.mjs
 * 退出码：0 通过；1 发现死方法或 Feign 断链。
 */
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

const REFLECTION_ANNOTATIONS = [
  'Scheduled', 'PostConstruct', 'PreDestroy', 'EventListener',
  'JsonCreator', 'JsonAnyGetter', 'JsonAnySetter', 'JsonValue',
]
const HTTP_METHODS = ['Get', 'Post', 'Put', 'Delete', 'Patch']
// 兼容全限定写法 @org.springframework.web.bind.annotation.GetMapping（R27 场景）
const FQ = '(?:org\\.springframework\\.web\\.bind\\.annotation\\.)?'
const METHOD_ANNO_SOURCE = `@${FQ}(Get|Post|Put|Delete|Patch)Mapping\\s*(?:\\(([^)]*)\\))?`
const MAPPING_RE = new RegExp(METHOD_ANNO_SOURCE, 'g')

function listJavaFiles(dir) {
  if (!fs.existsSync(dir)) return []
  const out = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) out.push(...listJavaFiles(full))
    else if (entry.name.endsWith('.java')) out.push(full)
  }
  return out
}

/** @returns {string[]} 仓库内全部 mall-* 模块名 */
function listModules() {
  return fs.readdirSync(repoRoot, { withFileTypes: true })
    .filter((e) => e.isDirectory() && /^mall-/.test(e.name))
    .map((e) => e.name)
}

/**
 * 词法掩码：用状态机统一处理 Java 注释/字符串/char/文本块，输出等长文本
 * （注释与字面量内容替换为空格，结构字符保留）。不能用独立正则分别剥离——
 * 字符串内的 "https://" 含 //，先行注释正则会吃掉闭合引号导致跨行误配。
 */
export function stripCommentsAndStrings(src) {
  const out = src.split('')
  let i = 0
  const blank = (from, to) => {
    for (let k = from; k < to; k++) if (out[k] !== '\n' && out[k] !== '\r' && out[k] !== '\t') out[k] = ' '
  }
  while (i < src.length) {
    const c = src[i]
    // 文本块 """..."""
    if (c === '"' && src.startsWith('"""', i)) {
      let j = i + 3
      while (j < src.length && !src.startsWith('"""', j)) j++
      j = Math.min(src.length, j + 3)
      blank(i, j)
      i = j
      continue
    }
    // 行注释 //
    if (c === '/' && src[i + 1] === '/') {
      let j = i + 2
      while (j < src.length && src[j] !== '\n') j++
      blank(i, j)
      i = j
      continue
    }
    // 块注释 /* */
    if (c === '/' && src[i + 1] === '*') {
      let j = i + 2
      while (j < src.length && !src.startsWith('*/', j)) j++
      j = Math.min(src.length, j + 2)
      blank(i, j)
      i = j
      continue
    }
    // 字符串 "..."
    if (c === '"') {
      let j = i + 1
      while (j < src.length && src[j] !== '"' && src[j] !== '\n') {
        if (src[j] === '\\') j++
        j++
      }
      j = Math.min(src.length, j + 1)
      blank(i, j)
      i = j
      continue
    }
    // char 'x' / '\n' / '\''
    if (c === "'") {
      let j = i + 1
      while (j < src.length && src[j] !== "'" && src[j] !== '\n') {
        if (src[j] === '\\') j++
        j++
      }
      j = Math.min(src.length, j + 1)
      blank(i, j)
      i = j
      continue
    }
    i++
  }
  return out.join('')
}

const PRIVATE_METHOD_RE = /^[ \t]*(?:@\w+(?:\([^)]*\))?[ \t]*)*private\s+(?:static\s+|final\s+|synchronized\s+|default\s+)*(?:<[^>]+>\s*)?[\w$][\w$.<>\[\],? ]*?\s+(\w+)\s*\(/gm

/** A：全仓 private 死方法扫描 */
function findDeadPrivateMethods(modules) {
  const dead = []
  for (const mod of modules) {
    const mainRoot = path.join(repoRoot, mod, 'src', 'main', 'java')
    for (const file of listJavaFiles(mainRoot)) {
      const raw = fs.readFileSync(file, 'utf8')
      const className = path.basename(file, '.java')
      const clean = stripCommentsAndStrings(raw)
      const lines = clean.split('\n')
      for (const m of clean.matchAll(PRIVATE_METHOD_RE)) {
        const name = m[1]
        if (name === className) continue // private 构造器（单例/工具类）
        const declLine = clean.slice(0, m.index).split('\n').length - 1
        // 声明位置向上看 5 行：框架反射注解（注解可与签名相隔若干行/其他注解）
        const above = lines.slice(Math.max(0, declLine - 5), declLine + 1).join('\n')
        if (REFLECTION_ANNOTATIONS.some((a) => new RegExp(`@${a}\\b`).test(above))) continue
        // 同类引用次数：直接调用 name( 与方法引用 ::name（stream map(this::x) 常见），
        // 总数减去声明本身 1 次
        const esc = name.replace(/\$/g, '\\$')
        const directCalls = clean.match(new RegExp(`\\b${esc}\\s*\\(`, 'g'))?.length ?? 0
        const methodRefs = clean.match(new RegExp(`::${esc}\\b`, 'g'))?.length ?? 0
        if (directCalls + methodRefs <= 1) {
          dead.push(`${path.relative(repoRoot, file).replace(/\\/g, '/')}#${name}`)
        }
      }
    }
  }
  return dead
}

/** 提取注解括号内第一个字符串（value/path/name 属性或默认值），多值返回数组 */
function annotationStringArgs(annoBody) {
  if (!annoBody) return []
  const args = []
  // name = "x" / value = "x" / path = {"a","b"} / 裸 "x"
  for (const m of annoBody.matchAll(/"((?:[^"\\]|\\.)*)"/g)) args.push(m[1])
  return args
}

/**
 * 解析一个 Java 文件内全部 @RequestMapping 风格端点：返回 [{method, path}]。
 * prependPrefix：@FeignClient(path="...") 前缀——OpenFeign 将它拼在类 @RequestMapping 之前
 * （两者叠加，不是二选一）。
 */
/**
 * 收集文件内全部类声明（含嵌套类，如 BrandController.java 内同文件的
 * AdminBrandController）与其紧邻的 @RequestMapping 前缀；返回按位置排序的边界。
 */
function classScopes(src) {
  const scopes = []
  // 只绑定显式标注 @RequestMapping 的类；无注解的内部 record/enum/DTO 不产生边界，
  // 其中的方法（若有）继承外层 controller 前缀
  const classRe = new RegExp(`@${FQ}RequestMapping\\s*(?:\\(([^)]*)\\))?[\\s\\S]{0,300}?\\b(?:class|interface|record|enum)\\s+(\\w+)`, 'g')
  for (const m of src.matchAll(classRe)) {
    scopes.push({ start: m.index, name: m[2], prefixes: normalizePrefixes(annotationStringArgs(m[1] ?? '')) })
  }
  return scopes.sort((a, b) => a.start - b.start)
}

function prefixAt(scopes, pos) {
  let current = ['']
  for (const s of scopes) {
    if (s.start <= pos) current = s.prefixes
    else break
  }
  return current
}

function parseMappings(src, prependPrefix = null) {
  const out = []
  const scopes = classScopes(src)

  const methodAnnoRe = new RegExp(METHOD_ANNO_SOURCE, 'g')
  for (const m of src.matchAll(methodAnnoRe)) {
    const method = 'HTTP_' + m[1].toUpperCase()
    const args = m[2] ?? ''
    const paths = annotationStringArgs(args).length
      ? annotationStringArgs(args)
      : ['']
    for (const clsPrefix of prefixAt(scopes, m.index)) {
      for (const p of paths) {
        const full = prependPrefix ? joinPath(prependPrefix, joinPath(clsPrefix, p)) : joinPath(clsPrefix, p)
        out.push({ method, path: full })
      }
    }
  }
  return out
}

function normalizePrefixes(prefixes) {
  if (!prefixes.length) return ['']
  return prefixes.map((p) => p.replace(/\/+$/, ''))
}

function joinPath(prefix, p) {
  const full = (prefix + (p.startsWith('/') || !p ? p : '/' + p)).replace(/\/+$/, '')
  return full || '/'
}

/** {id} 等路径变量归一为匹配单段的正则；对字面点/斜杠转义 */
function pathToRegExp(p) {
  const escaped = p.replace(/[.*+?^$()|[\]\\]/g, '\\$&').replace(/\{[^/}]+\}/g, '[^/]+')
  return new RegExp(`^${escodedOrRoot(escaped)}$`)
}
function escodedOrRoot(escaped) {
  return escaped === '' ? '/' : escaped
}

/** 解析 @FeignClient 文件：返回 [{service, method, path, from}] */
function parseFeignEdges(file) {
  const src = fs.readFileSync(file, 'utf8')
  const feignMatch = src.match(/@FeignClient\s*\(([^)]*)\)/s)
  if (!feignMatch) return []
  const args = feignMatch[1]
  if (/\burl\s*=/.test(args) && !/\b(name|value)\s*=/.test(args)) return [] // 纯外部 URL 客户端
  const svcMatch = args.match(/\b(?:name|value)\s*=\s*"([^"]+)"/)
  if (!svcMatch) return []
  const service = svcMatch[1]
  if (!/^mall-/.test(service)) return []
  const feignPath = args.match(/\bpath\s*=\s*"([^"]+)"/)
  return parseMappings(src, feignPath ? feignPath[1].replace(/\/+$/, '') : null)
    .map((e) => ({ ...e, service, from: path.relative(repoRoot, file).replace(/\\/g, '/') }))
}

/** B：Feign 边与下游真实 Controller 映射对照 */
function findBrokenFeignEdges(modules) {
  // 目标服务 → 端点正则表（路径变量归一）
  const serviceEndpoints = new Map()
  for (const mod of modules) {
    const set = []
    for (const f of listJavaFiles(path.join(repoRoot, mod, 'src', 'main', 'java'))) {
      for (const ep of parseMappings(fs.readFileSync(f, 'utf8'))) {
        set.push({ ...ep, re: pathToRegExp(ep.path) })
      }
    }
    serviceEndpoints.set(mod, set)
  }

  const broken = []
  for (const mod of modules) {
    const feignFiles = listJavaFiles(path.join(repoRoot, mod, 'src', 'main', 'java')).filter((f) =>
      /FeignClient\.java$|FeignClients?\.java$/.test(path.basename(f)))
    for (const f of feignFiles) {
      for (const edge of parseFeignEdges(f)) {
        const endpoints = serviceEndpoints.get(edge.service)
        if (!endpoints) {
          broken.push(`${edge.from} → ${edge.service}（目标模块不存在） ${edge.method.replace('HTTP_', '')} ${edge.path}`)
          continue
        }
        const ok = endpoints.some((ep) => ep.method === edge.method && ep.re.test(edge.path))
        if (!ok) {
          broken.push(`${edge.from} → ${edge.service} ${edge.method.replace('HTTP_', '')} ${edge.path}`)
        }
      }
    }
  }
  return broken
}

function main() {
  const modules = listModules()
  const allowlistPath = path.join(repoRoot, 'scripts', 'ci', 'backend-orphan-allowlist.json')
  const allowlist = JSON.parse(fs.readFileSync(allowlistPath, 'utf8'))
  const allowed = new Map(allowlist.entries.map((e) => [e.id, e]))

  let failures = 0
  const checkAllowed = (id, msg) => {
    const entry = allowed.get(id)
    if (entry) {
      if (!entry.reason || !entry.reason.trim()) {
        console.error(`::error::白名单条目缺原因：${id}`)
        failures++
      }
      return
    }
    console.error(`::error::${msg}`)
    failures++
  }

  const dead = findDeadPrivateMethods(modules)
  for (const id of dead) checkAllowed(id, `private 方法零引用（死代码，删除或补真实调用）：${id}`)

  const brokenFeign = findBrokenFeignEdges(modules)
  for (const edge of brokenFeign) {
    const id = 'feign:' + edge
    checkAllowed(id, `Feign 边在下游无对应 Controller 映射（契约断链）：${edge}`)
  }

  // 白名单条目若已不再被报告（死代码已删/链路已修），CI 变红，防止僵尸豁免
  const reported = new Set([...dead, ...brokenFeign.map((b) => 'feign:' + b)])
  for (const id of allowed.keys()) {
    if (!reported.has(id)) {
      console.error(`::error::白名单条目已无对应问题，请立即删除：${id}`)
      failures++
    }
  }

  if (failures > 0) {
    console.error(`\n后端可达性门禁失败：${failures} 项。`)
    console.error('反射/框架隐式调用须在 backend-orphan-allowlist.json 写明机制与入口；僵尸白名单条目必须删除。')
    process.exit(1)
  }
  console.log(`后端可达性检查通过：扫描 ${modules.length} 个模块，无零引用 private 方法，Feign 契约无断链。`)
}

export { findDeadPrivateMethods, parseFeignEdges, parseMappings, pathToRegExp, listJavaFiles, listModules }

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
