#!/usr/bin/env node
/**
 * C36-3 Cocos 资源完整性门禁（pet-game）：
 *
 * 1. meta 齐全：assets 下每个源文件与每个目录都必须有同名 .meta（Cocos 靠 .meta 的
 *    uuid 建立引用，缺 meta 的资源进编辑器会重新生成 uuid，场景引用即刻断链）；
 * 2. uuid 唯一：所有 .meta 的标准 uuid（36 字符）不得重复；
 * 3. 引用可解析：场景/预制/材质等 JSON 中出现的资源引用——
 *    "__uuid__": "<标准或压缩 uuid>"、"__type__": "<23 字符压缩 uuid>"——必须能在
 *    meta uuid 集合中反查；cc.XXX 内置类型（以 cc. 开头）跳过。
 *
 * Cocos 压缩 uuid：标准 32 位 hex 前 5 字符保留，其余每 3 hex（12bit）编码为 2 个
 * Base64 字符，共 5+18=23 字符。
 *
 * 用法：node scripts/ci/check-cocos-assets.mjs [pet-game 目录（默认仓库内 pet-game）]
 * 退出码：0 通过；1 缺 meta/uuid 重复/引用断链。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const gameRoot = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.join(repoRoot, 'pet-game')
const assetsRoot = path.join(gameRoot, 'assets')

const BASE64_KEYS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
const STD_UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const COMPRESSED_RE = /^[0-9a-f]{5}[A-Za-z0-9+/]{18}$/
// 作为引用容器出现、需要解析 uuid 的 Cocos 序列化文件
const SERIALIZED_EXT = new Set(['.scene', '.prefab', '.anim', '.material', '.mesh', '.skeleton', '.json'])

export function compressUuid(stdUuid) {
  const hex = stdUuid.replace(/-/g, '')
  let out = hex.slice(0, 5)
  for (let i = 5; i < 32; i += 3) {
    const n = parseInt(hex.slice(i, i + 3), 16)
    out += BASE64_KEYS[n >> 6] + BASE64_KEYS[n & 0x3f]
  }
  return out
}

function walk(dir) {
  const dirs = []
  const files = []
  const stack = [dir]
  while (stack.length) {
    const cur = stack.pop()
    for (const entry of fs.readdirSync(cur, { withFileTypes: true })) {
      const full = path.join(cur, entry.name)
      if (entry.isDirectory()) {
        dirs.push(full)
        stack.push(full)
      } else files.push(full)
    }
  }
  return { dirs, files }
}

const ZERO_UUID = '00000000-0000-0000-0000-000000000000'

function analyze(root) {
  const errors = []
  const warnings = []
  if (!fs.existsSync(root)) return { errors: ['assets 目录不存在：' + root], warnings: [], uuids: new Map() }

  const { dirs, files } = walk(root)

  // 1. meta 齐全：源文件 meta 是硬指标（uuid 断链根源）；目录 meta 缺失仅警告
  //    （Cocos 编辑器/构建导入时自动生成目录占位 meta，不参与资源引用）
  for (const f of files) {
    if (f.endsWith('.meta')) continue
    if (!fs.existsSync(f + '.meta')) errors.push(`源文件缺 .meta：${path.relative(root, f)}`)
  }
  for (const d of dirs) {
    if (!fs.existsSync(path.join(d, '.meta'))) {
      warnings.push(`目录缺 .meta（编辑器导入时自动生成，不阻断）：${path.relative(root, d)}`)
    }
  }

  // 2. uuid 收集与唯一性（目录 meta 为 importer=directory + 全零占位，排除）
  const stdUuids = new Map() // uuid → meta 相对路径
  for (const meta of files.filter((f) => f.endsWith('.meta'))) {
    let json
    try {
      json = JSON.parse(fs.readFileSync(meta, 'utf8'))
    } catch (e) {
      errors.push(`.meta 不是合法 JSON：${path.relative(root, meta)}（${e.message}）`)
      continue
    }
    if (json.importer === 'directory') continue
    const uuid = json.uuid
    if (!uuid || !STD_UUID_RE.test(uuid) || uuid === ZERO_UUID) {
      errors.push(`源文件 .meta uuid 缺失/非标准/全零占位：${path.relative(root, meta)}`)
      continue
    }
    if (stdUuids.has(uuid)) {
      errors.push(`uuid 重复：${uuid} 同时出现在 ${stdUuids.get(uuid)} 与 ${path.relative(root, meta)}`)
    }
    stdUuids.set(uuid, path.relative(root, meta))
  }

  // 标准 + 压缩两套可解析集合
  const resolvable = new Set()
  for (const u of stdUuids.keys()) {
    resolvable.add(u)
    resolvable.add(compressUuid(u))
  }

  // 3. 序列化文件内的 uuid 引用反查
  for (const f of files) {
    const ext = path.extname(f)
    if (!SERIALIZED_EXT.has(ext) || f.endsWith('.meta')) continue
    const text = fs.readFileSync(f, 'utf8')
    // "__uuid__": "..."（资源引用）
    for (const m of text.matchAll(/"__uuid__"\s*:\s*"([^"]+)"/g)) {
      const ref = m[1]
      if (!resolvable.has(ref)) {
        errors.push(`uuid 引用断链：${path.relative(root, f)} 引用 ${ref}，无对应 .meta`)
      }
    }
    // "__type__": "..."：cc. 开头是内置类型；23 字符压缩串是用户脚本组件
    for (const m of text.matchAll(/"__type__"\s*:\s*"([^"]+)"/g)) {
      const type = m[1]
      if (type.startsWith('cc.') || type.startsWith('cc:')) continue
      if (COMPRESSED_RE.test(type) && !resolvable.has(type)) {
        errors.push(`脚本组件类型断链：${path.relative(root, f)} 的 __type__=${type} 无对应脚本 .meta`)
      }
    }
  }

  return { errors, warnings, uuids: stdUuids }
}

function main() {
  const { errors, warnings, uuids } = analyze(assetsRoot)
  for (const w of warnings) console.log(`[warn] ${w}`)
  if (errors.length) {
    for (const e of errors) console.error(`::error::${e}`)
    console.error(`\nCocos 资源完整性检查失败：${errors.length} 项（源文件缺 meta/uuid 重复/引用断链）。`)
    process.exit(1)
  }
  console.log(`Cocos 资源完整性检查通过：${uuids.size} 个资源 uuid，源文件 meta 齐全且无引用断链。`)
}

export { analyze }

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
