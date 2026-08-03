import { describe, it, expect } from 'vitest'
import { formatCount, timeAgo } from '@/utils/format'

describe('formatCount', () => {
  it('returns number as string for values < 1000', () => {
    expect(formatCount(0)).toBe('0')
    expect(formatCount(42)).toBe('42')
    expect(formatCount(999)).toBe('999')
  })

  it('formats values >= 1000 as x.xk', () => {
    expect(formatCount(1000)).toBe('1.0k')
    expect(formatCount(1500)).toBe('1.5k')
    expect(formatCount(9999)).toBe('10.0k')
  })

  it('formats values >= 10000 as x.xw', () => {
    expect(formatCount(10000)).toBe('1.0w')
    expect(formatCount(25000)).toBe('2.5w')
    expect(formatCount(100000)).toBe('10.0w')
  })
})

describe('timeAgo', () => {
  it('returns "刚刚" for less than 1 minute ago', () => {
    const now = new Date().toISOString()
    expect(timeAgo(now)).toBe('刚刚')
  })

  it('returns "x分钟前" for minutes ago', () => {
    const fiveMinAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString()
    expect(timeAgo(fiveMinAgo)).toBe('5分钟前')
  })

  it('returns "x小时前" for hours ago', () => {
    const threeHoursAgo = new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString()
    expect(timeAgo(threeHoursAgo)).toBe('3小时前')
  })

  it('returns "x天前" for days ago', () => {
    const twoDaysAgo = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString()
    expect(timeAgo(twoDaysAgo)).toBe('2天前')
  })
})
