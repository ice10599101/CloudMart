#!/usr/bin/env node
/**
 * C36-4 删除验证门禁——防止删文件/改路径后留下悬空引用：
 *
 * 1. TS/TSX 悬空 import：三端 src 与 pet-game 脚本中的相对路径与 @ 别名 import
 *    （含 index 桶、.web/.h5 等平台后缀）目标必须存在；裸包（react 等）不检查；
 * 2. package.json scripts：`node <相对脚本>` 形态引用的文件必须存在；
 * 3. Markdown 相对链接：docs/ 与根目录 .md 中指向仓库内 .md/.png 等文件的相对链接
 *    必须存在（http(s)/mailto/#锚点 跳过）。
 *
 * 用法：node scripts/ci/check-dangling-refs.mjs
 * 退出码：0 无悬空；1 发现悬空引用。
 */
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseSpecifiers, resolveCandidates } from './check-frontend-orphans.mjs'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

const CODE_EXT = ['.ts', '.tsx', '.js', '.jsx']
const SKIP_RE = /\.d\.ts$|(?:^|[/\\])(?:node_modules|dist|build|\.umi|\.expo|target)(?:[/\\]|$)/

const SCAN_ROOTS = [
  { dir: 'CloudMart-ui/src', project: 'CloudMart-ui', aliases: [{ prefix: '@/', target: 'src/' }] },
  { dir: 'cloudmart-app/src', project: 'cloudmart-app', aliases: [{ prefix: '@/assets/', target: 'assets/' }, { prefix: '@/', target: 'src/' }] },
  { dir: 'cloudmart-mobile/src', project: 'cloudmart-mobile', aliases: [{ prefix: '@/', target: 'src/' }] },
  { dir: 'pet-game/assets/scripts', project: 'pet-game', aliases: [] },
]

const trackedFiles = new Set(
  execSync('git ls-files', { cwd: repoRoot, encoding: 'utf8' })
    .split('\n')
    .filter(Boolean)
    .map((p) => path.join(repoRoot, p)),
)
const isTracked = (f) => trackedFiles.has(f)

function walk(dir) {
  if (!fs.existsSync(dir)) return []
  const out = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) out.push(...walk(full))
    else if (CODE_EXT.includes(path.extname(entry.name))) out.push(full)
  }
  return out
}

function resolveAlias(projectDir, aliases, spec, fromFile) {
  for (const a of aliases) {
    if (spec.startsWith(a.prefix)) {
      return resolveCandidates(path.join(repoRoot, projectDir, a.target, spec.slice(a.prefix.length)))
    }
  }
  return []
}

/** TS 悬空 import 检查 */
function findDanglingTsImports() {
  const broken = []
  for (const root of SCAN_ROOTS) {
    for (const file of walk(path.join(repoRoot, root.dir))) {
      if (SKIP_RE.test(file) || !isTracked(file)) continue
      const text = fs.readFileSync(file, 'utf8')
      for (const spec of parseSpecifiers(text)) {
        let candidates = []
        if (spec.startsWith('.')) {
          candidates = resolveCandidates(path.resolve(path.dirname(file), spec))
        } else if (spec.startsWith('@/')) {
          candidates = resolveAlias(root.project, root.aliases, spec, file)
        } else {
          continue // 裸包由 tsc/build 解析
        }
        // 样式/资源侧车导入（import './x.css'）：resolveCandidates 只覆盖代码扩展，单独验存在
        const existsAsAsset = fs.existsSync(path.resolve(path.dirname(file), spec))
          || (spec.startsWith('@/') && aliasRawExists(root, spec))
        if (candidates.length === 0 && !existsAsAsset) {
          broken.push(`${path.relative(repoRoot, file).replace(/\\/g, '/')} → ${spec}`)
        }
      }
    }
  }
  return broken
}

function aliasRawExists(root, spec) {
  for (const a of root.aliases) {
    if (spec.startsWith(a.prefix) && fs.existsSync(path.join(repoRoot, root.project, a.target, spec.slice(a.prefix.length)))) {
      return true
    }
  }
  return false
}

/** package.json scripts 中 `node <脚本>` 引用 */
function findDanglingScriptRefs() {
  const broken = []
  const pkgFiles = ['CloudMart-ui/package.json', 'cloudmart-app/package.json', 'cloudmart-mobile/package.json', 'package.json']
  for (const rel of pkgFiles) {
    const full = path.join(repoRoot, rel)
    if (!fs.existsSync(full)) continue
    let pkg
    try {
      pkg = JSON.parse(fs.readFileSync(full, 'utf8'))
    } catch {
      continue
    }
    for (const [name, cmd] of Object.entries(pkg.scripts ?? {})) {
      for (const m of String(cmd).matchAll(/(?:^|&&|\|\||;)\s*node\s+([^\s;|&]+)|^node\s+([^\s;|&]+)/g)) {
        const ref = (m[1] ?? m[2]).replace(/^["']|["']$/g, '')
        if (!ref || ref.startsWith('-') || !ref.includes('/')) continue
        const target = path.resolve(path.dirname(full), ref)
        if (!fs.existsSync(target)) broken.push(`${rel}#scripts.${name} → ${ref}`)
      }
    }
  }
  return broken
}

const MD_LINK_RE = /\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g
const MD_TARGET_RE = /\.(md|png|jpg|jpeg|gif|svg|webp|puml|json|py|sh|mjs|ts|tsx|yml|yaml|sql)$/i

/** 判定单个 md 文件文本中的悬空相对链接（纯函数，fromFile 为该 md 的绝对路径） */
export function mdBrokenLinks(text, fromFile) {
  const broken = []
  for (const m of text.matchAll(MD_LINK_RE)) {
    let link = m[1].split('#')[0].split('?')[0]
    if (!link || /^https?:|^mailto:|^#|^[a-z]+:/i.test(link)) continue
    link = decodeURIComponent(link)
    if (!MD_TARGET_RE.test(link)) continue
    if (!fs.existsSync(path.resolve(path.dirname(fromFile), link))) broken.push(m[1])
  }
  return broken
}

/** Markdown 相对链接（仅仓库内文件目标） */
function findDanglingMdLinks() {
  const broken = []
  const mdFiles = []
  for (const top of ['docs', '.']) {
    const base = path.join(repoRoot, top)
    if (!fs.existsSync(base)) continue
    mdFiles.push(...walkMd(base, top === '.'))
  }
  for (const f of mdFiles) {
    for (const bad of mdBrokenLinks(fs.readFileSync(f, 'utf8'), f)) {
      broken.push(`${path.relative(repoRoot, f).replace(/\\/g, '/')} → ${bad}`)
    }
  }
  return broken
}

function walkMd(dir, rootOnly) {
  const out = []
  const stack = [dir]
  while (stack.length) {
    const cur = stack.pop()
    for (const entry of fs.readdirSync(cur, { withFileTypes: true })) {
      if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'target') continue
      const full = path.join(cur, entry.name)
      if (entry.isDirectory()) {
        if (rootOnly && entry.name.startsWith('.')) continue
        stack.push(full)
      } else if (entry.name.endsWith('.md') && isTracked(full)) out.push(full)
    }
  }
  return out
}

function main() {
  const broken = [
    ...findDanglingTsImports().map((x) => `TS 悬空 import：${x}`),
    ...findDanglingScriptRefs().map((x) => `package script 悬空：${x}`),
    ...findDanglingMdLinks().map((x) => `Markdown 悬空链接：${x}`),
  ]
  if (broken.length) {
    for (const b of broken) console.error(`::error::${b}`)
    console.error(`\n悬空引用门禁失败：${broken.length} 处。删除/移动文件后必须同步更新引用方。`)
    process.exit(1)
  }
  console.log('悬空引用检查通过：TS import、package scripts、Markdown 链接均无悬空。')
}

export { findDanglingTsImports, findDanglingScriptRefs, findDanglingMdLinks }

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
