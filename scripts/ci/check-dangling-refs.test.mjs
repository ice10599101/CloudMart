import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { mdBrokenLinks, findDanglingTsImports, findDanglingScriptRefs, findDanglingMdLinks } from './check-dangling-refs.mjs'

test('mdBrokenLinks：缺失相对文件报悬空，存在/外链/锚点均放行', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'dangling-'))
  fs.writeFileSync(path.join(dir, 'a.md'), '# x')
  const md = path.join(dir, 'index.md')
  fs.writeFileSync(md, [
    '[ok](a.md)',
    '[bad](missing.md)',
    '[web](https://example.com/x.md)',
    '[anchor](#section)',
    '[img](a.png)',
  ].join('\n'))
  const broken = mdBrokenLinks(fs.readFileSync(md, 'utf8'), md)
  assert.deepEqual(broken, ['missing.md', 'a.png'])
  fs.rmSync(dir, { recursive: true, force: true })
})

test('mdBrokenLinks：#锚点与 query 剥离后仍按目标文件判定', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'dangling-'))
  fs.writeFileSync(path.join(dir, 'doc.md'), '# x')
  const md = path.join(dir, 'index.md')
  fs.writeFileSync(md, '[x](doc.md#section?y=1) [y](gone.md#x)')
  assert.deepEqual(mdBrokenLinks(fs.readFileSync(md, 'utf8'), md), ['gone.md#x'])
  fs.rmSync(dir, { recursive: true, force: true })
})

test('仓库基线：TS import、package scripts、Markdown 链接当前零悬空', () => {
  // 锁当前全仓绿基线；未来删除文件若漏改引用方，本用例立即变红
  assert.deepEqual(findDanglingTsImports(), [])
  assert.deepEqual(findDanglingScriptRefs(), [])
  assert.deepEqual(findDanglingMdLinks(), [])
})
