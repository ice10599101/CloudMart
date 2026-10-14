import { test } from 'node:test'
import assert from 'node:assert/strict'
import path from 'node:path'
import { resolveCandidates, parseSpecifiers, computeReachable } from './check-frontend-orphans.mjs'

test('resolveCandidates：同基名平台变体与目录桶文件全部解析（import 基名即整组可达）', () => {
  const files = new Set([
    path.join('/repo', 'src', 'a.ts'),
    path.join('/repo', 'src', 'a.web.tsx'),
    path.join('/repo', 'src', 'Foo', 'index.tsx'),
    path.join('/repo', 'src', 'Foo', 'index.h5.tsx'),
    path.join('/repo', 'src', 'Foo', 'index.weapp.tsx'),
    path.join('/repo', 'src', 'keep.ts'),
  ])
  const exists = (p) => files.has(p)

  assert.deepEqual(resolveCandidates(path.join('/repo', 'src', 'a'), exists).sort(),
    [path.join('/repo', 'src', 'a.ts'), path.join('/repo', 'src', 'a.web.tsx')].sort())
  assert.deepEqual(resolveCandidates(path.join('/repo', 'src', 'Foo'), exists).sort(),
    [
      path.join('/repo', 'src', 'Foo', 'index.h5.tsx'),
      path.join('/repo', 'src', 'Foo', 'index.tsx'),
      path.join('/repo', 'src', 'Foo', 'index.weapp.tsx'),
    ].sort())
  assert.deepEqual(resolveCandidates(path.join('/repo', 'src', 'missing'), exists), [])
})

test('parseSpecifiers：静态 import/export-from/require/动态 import 全部提取，裸包保留', () => {
  const text = `
import x from './a'
import { y } from '@/utils/b'
export { z } from '../dir/c'
const d = require('./d')
const e = await import('@/components/E')
import 'react'
const plain = "no import here"
`
  const specs = parseSpecifiers(text)
  for (const expected of ['./a', '@/utils/b', '../dir/c', './d', '@/components/E', 'react']) {
    assert.ok(specs.includes(expected), `应提取 ${expected}`)
  }
  assert.equal(specs.length, 6)
})

test('computeReachable：从入口沿边传播，孤立节点不可达，环不致死循环', () => {
  const roots = new Set(['entry'])
  const adjacency = new Map([
    ['entry', new Set(['a', 'b'])],
    ['a', new Set(['c'])],
    ['c', new Set(['a'])], // a↔c 环
    ['b', new Set()],
    // orphan 无任何入边
    ['orphan', new Set(['a'])],
  ])
  const reachable = computeReachable(roots, adjacency)
  assert.deepEqual([...reachable].sort(), ['a', 'b', 'c', 'entry'])
  assert.ok(!reachable.has('orphan'))
})

test('computeReachable：空 roots 时不传播任何节点', () => {
  const reachable = computeReachable(new Set(), new Map([['a', new Set(['b'])]]))
  assert.equal(reachable.size, 0)
})
