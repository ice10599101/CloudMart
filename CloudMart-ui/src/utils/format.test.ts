import { describe, it, expect } from 'vitest'
import { formatCount, timeAgo, stripHtml } from '@/utils/format'

describe('stripHtml', () => {
  it('returns empty string for null/undefined/empty input', () => {
    expect(stripHtml(null)).toBe('')
    expect(stripHtml(undefined)).toBe('')
    expect(stripHtml('')).toBe('')
  })

  it('returns plain text unchanged', () => {
    expect(stripHtml('普通文本')).toBe('普通文本')
  })

  it('strips HTML tags from rich text', () => {
    expect(stripHtml('<p>你好<strong>世界</strong></p>')).toBe('你好世界')
    expect(stripHtml('<h1>标题</h1><p>段落</p>')).toBe('标题 段落')
  })

  it('treats Tiptap empty document as empty', () => {
    expect(stripHtml('<p></p>')).toBe('')
    expect(stripHtml('<p><br></p>')).toBe('')
    expect(stripHtml('<p>&nbsp;</p>')).toBe('')
  })

  it('decodes common HTML entities', () => {
    expect(stripHtml('a &amp; b')).toBe('a & b')
    expect(stripHtml('&lt;tag&gt;')).toBe('<tag>')
    expect(stripHtml('&quot;q&quot;')).toBe('"q"')
  })

  it('collapses whitespace between block tags', () => {
    expect(stripHtml('<p>a</p>\n<p>b</p>')).toBe('a b')
  })
})

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
