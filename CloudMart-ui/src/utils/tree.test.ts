import { describe, it, expect } from 'vitest'
import { convertToTreeSelect } from '@/utils/tree'

interface TestNode {
  id: number
  name: string
  children?: TestNode[]
}

describe('convertToTreeSelect', () => {
  it('converts flat nodes to tree select nodes', () => {
    const nodes: TestNode[] = [
      { id: 1, name: 'Node 1' },
      { id: 2, name: 'Node 2' },
    ]
    const result = convertToTreeSelect(nodes, 'name')
    expect(result).toEqual([
      { title: 'Node 1', value: 1, key: 1, children: undefined },
      { title: 'Node 2', value: 2, key: 2, children: undefined },
    ])
  })

  it('converts nested nodes recursively', () => {
    const nodes: TestNode[] = [
      {
        id: 1,
        name: 'Parent',
        children: [
          { id: 11, name: 'Child 1' },
          { id: 12, name: 'Child 2' },
        ],
      },
    ]
    const result = convertToTreeSelect(nodes, 'name')
    expect(result).toEqual([
      {
        title: 'Parent',
        value: 1,
        key: 1,
        children: [
          { title: 'Child 1', value: 11, key: 11, children: undefined },
          { title: 'Child 2', value: 12, key: 12, children: undefined },
        ],
      },
    ])
  })

  it('returns empty array for empty input', () => {
    expect(convertToTreeSelect([], 'name')).toEqual([])
  })
})
