import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { compressUuid, analyze } from './check-cocos-assets.mjs'

// PetGameTheme.ts.meta 的真实 uuid；场景 PetHome.scene 的 __type__ 压缩串 de28bIpZkFFg6SBhvUEChP3
// 即由该算法生成（全量门禁的反查通过即端到端证据，此处锁算法本身）
test('compressUuid：标准 uuid 压缩为 23 字符 Cocos 形态', () => {
  const std = 'f65e0420-f76a-45dc-9604-ee04374aab89'
  const c = compressUuid(std)
  assert.equal(c.length, 23)
  assert.equal(c.slice(0, 5), 'f65e0')
})

test('compressUuid：与场景内真实压缩组件 uuid 一致（PetGameRoot）', () => {
  // 从 pet-game 各脚本 meta 中找到压缩后等于场景 __type__ 的那个
  const root = path.resolve('pet-game', 'assets')
  const scene = fs.readFileSync(path.join(root, 'scenes', 'PetHome.scene'), 'utf8')
  const compressed = scene.match(/"__type__":\s*"([0-9a-f]{5}[A-Za-z0-9+/]{18})"/)?.[1]
  assert.ok(compressed, '场景中应至少有一个压缩 uuid 组件引用')
  const metas = fs.readdirSync(path.join(root, 'scripts')).filter((f) => f.endsWith('.ts.meta'))
  const matched = metas.some((m) => {
    const uuid = JSON.parse(fs.readFileSync(path.join(root, 'scripts', m), 'utf8')).uuid
    return compressUuid(uuid) === compressed
  })
  assert.ok(matched, `压缩串 ${compressed} 应能反查到某个脚本 meta`)
})

function makeTempProject() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'cocos-gate-'))
  const assets = path.join(dir, 'assets')
  fs.mkdirSync(path.join(assets, 'scripts'), { recursive: true })
  const write = (f, c) => fs.writeFileSync(path.join(assets, f), c)
  const meta = (uuid, importer = 'typescript') =>
    JSON.stringify({ ver: '1.2.0', importer, imported: true, uuid, files: [], subMetas: {}, userData: {} })
  return { dir, assets, write, meta }
}

test('analyze：健康工程零错误（文件 meta 齐全、场景引用可解析）', () => {
  const { dir, assets, write, meta } = makeTempProject()
  const uuid = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
  write('scripts/A.ts', 'export const x = 1')
  write('scripts/A.ts.meta', meta(uuid))
  write('scenes.scene', JSON.stringify([{ __type__: compressUuid(uuid) }]))
  write('scenes.scene.meta', meta('11111111-2222-3333-4444-555555555555', 'scene'))
  const r = analyze(assets)
  assert.deepEqual(r.errors, [])
  fs.rmSync(dir, { recursive: true, force: true })
})

test('analyze：源文件缺 meta 报错误；__uuid__ 断链报错误', () => {
  const { dir, assets, write, meta } = makeTempProject()
  write('scripts/Orphan.ts', 'export {}') // 无 meta
  write('scripts/B.ts', 'export {}')
  write('scripts/B.ts.meta', meta('bbbbbbbb-1111-2222-3333-444444444444'))
  write('p.prefab', JSON.stringify([{ __uuid__: 'deadbeef-0000-0000-0000-000000000000' }]))
  write('p.prefab.meta', meta('cccccccc-1111-2222-3333-444444444444', 'prefab'))
  const r = analyze(assets)
  assert.ok(r.errors.some((e) => e.includes('Orphan.ts') && e.includes('缺 .meta')))
  assert.ok(r.errors.some((e) => e.includes('uuid 引用断链') && e.includes('deadbeef')))
  fs.rmSync(dir, { recursive: true, force: true })
})

test('analyze：重复 uuid 与全零文件 uuid 均报错', () => {
  const { dir, assets, write, meta } = makeTempProject()
  const dup = meta('dddddddd-0000-0000-0000-000000000001')
  write('scripts/A.ts', 'export {}'); write('scripts/A.ts.meta', dup)
  write('scripts/B.ts', 'export {}'); write('scripts/B.ts.meta', dup)
  write('scripts/C.ts', 'export {}'); write('scripts/C.ts.meta', meta('00000000-0000-0000-0000-000000000000'))
  const r = analyze(assets)
  assert.ok(r.errors.some((e) => e.includes('uuid 重复')))
  assert.ok(r.errors.some((e) => e.includes('C.ts.meta') && e.includes('全零')))
  fs.rmSync(dir, { recursive: true, force: true })
})
