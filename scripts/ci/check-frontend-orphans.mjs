#!/usr/bin/env node
/**
 * C36-1 前端模块可达性门禁：识别三端 src 内「确定无入口可达」的孤立 TS/TSX 模块。
 *
 * 入口（roots）按各端框架约定识别，不靠人工维护模块清单：
 *  - CloudMart-ui（Umi 配置式路由）：.umirc.ts 的 component、src/app.ts、src/global.ts、
 *    src/layouts/**、全局 d.ts；
 *  - cloudmart-app（Expo Router 文件路由）：src/app/** 全部文件（含 _layout 等约定文件）；
 *  - cloudmart-mobile（Taro）：src/app.tsx、app.config.ts 的 pages、src/custom-tab-bar/**。
 *
 * 引用边识别：import/re-export/export-from/require()/动态 import() 的说明符；
 * 支持相对路径、@ 别名（W/A/M 均指向 src；A 端 @/assets 指向 assets）、
 * index 桶文件与平台后缀变体（import './x' 同时让 x.web/x.native/x.h5/x.ios/x.android 可达）。
 *
 * 裸包导入（react 等）与白名单（scripts/ci/frontend-orphan-allowlist.json，
 * 每条必须写明实际入口/保留原因）不计。测试/e2e/声明文件本身不作为被检模块。
 *
 * 用法：node scripts/ci/check-frontend-orphans.mjs
 * 退出码：0 无确定孤立；1 发现孤立模块。
 */
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

const CODE_EXT = ['.ts', '.tsx', '.js', '.jsx']
const PLATFORM_SUFFIXES = ['.web', '.native', '.h5', '.weapp', '.ios', '.android']
const SKIP_FILE = /\.d\.ts$|(?:^|[/\\])(?:__tests__|e2e|nodeRegistry)(?:[/\\]|$)|\.(?:test|spec)\.[jt]sx?$/
const SKIP_DIRS = /(?:^|[/\\])(?:nodeRegistry)(?:[/\\]|$)/

const PROJECTS = [
  {
    dir: 'CloudMart-ui',
    aliases: [{ prefix: '@/', target: 'src/' }],
    roots: (files) => {
      const r = new Set()
      const umirc = path.join(repoRoot, 'CloudMart-ui', '.umirc.ts')
      if (fs.existsSync(umirc)) {
        for (const m of fs.readFileSync(umirc, 'utf8').matchAll(/['"](@\/[^'"]+)['"]/g)) {
          for (const hit of aliasCandidates('CloudMart-ui', [{ prefix: '@/', target: 'src/' }], m[1])) r.add(hit)
        }
      }
      for (const f of files) {
        const rel = path.relative(path.join(repoRoot, 'CloudMart-ui'), f).replace(/\\/g, '/')
        if (rel === 'src/app.ts' || rel === 'src/global.ts' || rel.startsWith('src/layouts/')) r.add(f)
      }
      // vitest/jest 配置中的 setupFiles（测试运行时入口，不经 import 图）
      for (const cfgName of ['vitest.config.ts', 'vitest.config.mts', 'jest.config.js']) {
        const cfg = path.join(repoRoot, 'CloudMart-ui', cfgName)
        if (!fs.existsSync(cfg)) continue
        const text = fs.readFileSync(cfg, 'utf8')
        for (const m of text.matchAll(/['"](\.\/)?(src\/[^'"]+\.(?:ts|tsx))['"]/g)) {
          r.add(path.join(repoRoot, 'CloudMart-ui', m[2]))
        }
      }
      return r
    },
  },
  {
    dir: 'cloudmart-app',
    aliases: [{ prefix: '@/assets/', target: 'assets/' }, { prefix: '@/', target: 'src/' }],
    roots: (files) => new Set(files.filter((f) => f.replace(/\\/g, '/').includes('/src/app/'))),
  },
  {
    dir: 'cloudmart-mobile',
    aliases: [{ prefix: '@/', target: 'src/' }],
    roots: (files) => {
      const r = new Set(files.filter((f) => /(?:^|[/\\])app\.tsx$|(?:^|[/\\])app\.config\.ts$|(?:^|[/\\])custom-tab-bar[/\\]/.test(f)))
      const cfg = path.join(repoRoot, 'cloudmart-mobile', 'src', 'app.config.ts')
      if (fs.existsSync(cfg)) {
        const text = fs.readFileSync(cfg, 'utf8')
        for (const m of text.matchAll(/['"](?:\.\/)?(pages\/[^'"]+|src\/pages\/[^'"]+)['"]/g)) {
          const p = m[1].replace(/^src\//, '')
          const base = path.join(repoRoot, 'cloudmart-mobile', 'src', p)
          resolveCandidates(base).forEach((c) => r.add(c))
        }
      }
      return r
    },
  },
]

function listTracked(projectDir) {
  const out = execSync('git ls-files', { cwd: path.join(repoRoot, projectDir), encoding: 'utf8' })
  // 已 git rm/工作区删除但尚未提交的文件不再参与可达性分析
  return out
    .split('\n')
    .filter(Boolean)
    .map((p) => path.join(repoRoot, projectDir, p))
    .filter((f) => fs.existsSync(f))
}

export function resolveCandidates(baseNoExt, existsFn = fs.existsSync) {
  const out = new Set()
  for (const ext of CODE_EXT) {
    if (existsFn(baseNoExt + ext)) out.add(baseNoExt + ext)
    for (const suffix of PLATFORM_SUFFIXES) {
      const platform = baseNoExt + suffix + ext
      if (existsFn(platform)) out.add(platform)
    }
    // 目录桶文件的平台变体：components/Foo/index.h5.tsx 由 import './Foo' 经平台解析命中
    for (const suffix of ['', ...PLATFORM_SUFFIXES]) {
      const idx = path.join(baseNoExt, 'index' + suffix + ext)
      if (existsFn(idx)) out.add(idx)
    }
  }
  return [...out]
}

function aliasCandidates(projectDir, aliases, spec, existsFn = fs.existsSync) {
  for (const a of aliases) {
    if (spec.startsWith(a.prefix)) {
      const rest = spec.slice(a.prefix.length)
      const base = path.join(repoRoot, projectDir, a.target, rest)
      // 目录导入（如 @/components/RichTextEditor）的全部平台桶文件都算可达：
      // Expo/Metro 与 Taro 按平台在 index.tsx / index.web.tsx / index.h5.tsx 间选择
      return resolveCandidates(base, existsFn)
    }
  }
  return []
}

const IMPORT_RE = /(?:import\s+[^'"]*?from\s*|export\s+(?:\*|\{[^}]*\})\s+from\s*|import\s*|require\s*\(\s*|import\s*\(\s*)['"]([^'"]+)['"]/gs

/** 从源码文本提取全部模块说明符（静态 import/export、require、动态 import()） */
export function parseSpecifiers(text) {
  const specs = new Set()
  for (const m of text.matchAll(IMPORT_RE)) {
    if (m[1]) specs.add(m[1])
  }
  return [...specs]
}

function outgoingEdges(file, project) {
  const text = fs.readFileSync(file, 'utf8')
  const edges = new Set()
  for (const spec of parseSpecifiers(text)) {
    if (spec.startsWith('.')) {
      const base = path.resolve(path.dirname(file), spec)
      resolveCandidates(base).forEach((c) => edges.add(c))
    } else if (spec.startsWith('@/')) {
      for (const hit of aliasCandidates(project.dir, project.aliases, spec)) edges.add(hit)
    }
  }
  return edges
}

/**
 * 纯图可达性：从 roots 沿 adjacency（文件→其引用的文件集合）正向传播，
 * 返回可达集合。供 CLI 与单测共用。
 */
export function computeReachable(roots, adjacency) {
  const reachable = new Set()
  const stack = [...roots]
  while (stack.length) {
    const cur = stack.pop()
    if (reachable.has(cur)) continue
    reachable.add(cur)
    for (const nxt of adjacency.get(cur) ?? []) {
      if (!reachable.has(nxt)) stack.push(nxt)
    }
  }
  return reachable
}

/**
 * 单项目可达性分析（纯文件系统输入，便于单测以虚拟文件集合驱动）。
 * @returns {{modules: string[], roots: Set, reachable: Set, orphans: string[]}}
 */
export function analyzeProject(project, tracked) {
  const modules = tracked.filter(
    (f) => CODE_EXT.includes(path.extname(f)) && !SKIP_FILE.test(f) && !SKIP_DIRS.test(f) && f.includes(`${path.sep}src${path.sep}`),
  )
  const moduleSet = new Set(modules)
  const roots = new Set([...project.roots(tracked)].filter((f) => moduleSet.has(f)))

  const adjacency = new Map()
  for (const f of modules) {
    adjacency.set(f, new Set([...outgoingEdges(f, project)].filter((t) => moduleSet.has(t))))
  }

  const reachable = computeReachable(roots, adjacency)

  // Taro 约定：页面/组件同目录同名 *.config.ts 由编译器随模块自动加载（不经 import 图）
  for (const f of [...reachable]) {
    const companion = f.slice(0, -path.extname(f).length) + '.config.ts'
    if (moduleSet.has(companion)) reachable.add(companion)
  }

  return { modules, roots, reachable, orphans: modules.filter((f) => !reachable.has(f)).sort() }
}

function main() {
  const allowlistPath = path.join(repoRoot, 'scripts', 'ci', 'frontend-orphan-allowlist.json')
  const allowlist = JSON.parse(fs.readFileSync(allowlistPath, 'utf8'))
  const allowed = new Map(allowlist.entries.map((e) => [`${e.project}/${e.path.replace(/\\/g, '/')}`, e]))

  let orphanCount = 0
  for (const project of PROJECTS) {
    const tracked = listTracked(project.dir)
    const { modules, roots, orphans } = analyzeProject(project, tracked)
    const allowCount = [...allowed.keys()].filter((k) => k.startsWith(project.dir + '/')).length
    for (const f of orphans) {
      const rel = path.relative(path.join(repoRoot, project.dir), f).replace(/\\/g, '/')
      const key = `${project.dir}/${rel}`
      const entry = allowed.get(key)
      if (entry) {
        if (!entry.reason || !entry.reason.trim()) {
          console.error(`::error::白名单条目缺原因：${key}`)
          orphanCount++
        }
        continue
      }
      orphanCount++
      console.error(`::error::[${project.dir}] 确定孤立模块（无任何框架入口/import 可达）：${rel}`)
    }
    console.log(`${project.dir}: ${modules.length} 个模块，${roots.size} 个框架入口，${orphans.length} 个待判定（白名单 ${allowCount} 条）`)
  }

  if (orphanCount > 0) {
    console.error(`\n前端可达性门禁失败：${orphanCount} 个确定孤立模块。`)
    console.error('若为框架隐式入口（字符串动态加载/平台约定），请补进脚本入口识别或在 frontend-orphan-allowlist.json 写明实际入口，禁止以“暂时保留”豁免。')
    process.exit(1)
  }
  console.log('前端模块可达性检查通过：无确定孤立模块。')
}

// 仅作为脚本直接执行时扫描全仓；被 node:test import 时只加载函数
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
