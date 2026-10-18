import { describe, expect, it } from 'vitest'
import { requireList, toPageResult } from './paging'

describe('T06 分页契约适配', () => {
  it('数组 + meta 映射为 PageResult', () => {
    const result = toPageResult<number>({
      data: [1, 2, 3],
      meta: { page: 2, pageSize: 3, total: 7 },
    })
    expect(result).toEqual({ items: [1, 2, 3], total: 7, page: 2, pageSize: 3 })
  })

  it('IPage（records/current/size/total）映射为 PageResult', () => {
    const result = toPageResult<string>({
      data: { records: ['a', 'b'], current: 1, size: 2, total: 5 },
    })
    expect(result).toEqual({ items: ['a', 'b'], total: 5, page: 1, pageSize: 2 })
  })

  it('data 为数组但无 meta 时用回退分页信息', () => {
    const result = toPageResult<number>({ data: [1] }, { page: 3, pageSize: 20 })
    expect(result).toEqual({ items: [1], total: 1, page: 3, pageSize: 20 })
  })

  it('契约不匹配必须抛错，不能静默变空数组', () => {
    expect(() => toPageResult({ data: { list: [1] } })).toThrow('分页契约不匹配')
  })

  it('非分页列表必须是数组', () => {
    expect(requireList<number>({ data: [1, 2] })).toEqual([1, 2])
    expect(() => requireList({ data: { list: [] } })).toThrow('列表契约不匹配')
  })
})
