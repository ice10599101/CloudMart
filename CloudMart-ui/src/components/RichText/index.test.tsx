import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import RichText, { isRichText, richTextToPlainText } from '@/components/RichText'

describe('isRichText', () => {
  it('detects editor HTML (p/strong/ul/img)', () => {
    expect(isRichText('<p>hello <strong>world</strong></p>')).toBe(true)
    expect(isRichText('<ul><li>a</li></ul>')).toBe(true)
    expect(isRichText('<img src="https://x/y.png">')).toBe(true)
    expect(isRichText('line1<br/>line2')).toBe(true)
  })

  it('treats plain text and empty values as non-rich', () => {
    expect(isRichText('纯文本内容')).toBe(false)
    expect(isRichText('')).toBe(false)
    expect(isRichText(null)).toBe(false)
    expect(isRichText(undefined)).toBe(false)
  })
})

describe('richTextToPlainText', () => {
  it('strips tags and keeps line breaks from block elements', () => {
    expect(richTextToPlainText('<p>第一段</p><p>第二段</p>')).toBe('第一段\n第二段')
    expect(richTextToPlainText('a<br>b')).toBe('a\nb')
  })

  it('returns empty for empty input', () => {
    expect(richTextToPlainText(null)).toBe('')
    expect(richTextToPlainText('')).toBe('')
  })
})

describe('RichText component', () => {
  it('renders HTML content as sanitized markup (bold preserved)', () => {
    const { container } = render(<RichText content="<p>你好 <strong>世界</strong></p>" />)
    expect(container.querySelector('strong')?.textContent).toBe('世界')
  })

  it('renders plain text with pre-line, no tag injection', () => {
    const { container } = render(<RichText content={'第一行\n第二行'} />)
    expect(container.querySelector('.rich-text-plain')?.textContent).toContain('第一行')
    expect(container.querySelector('.rich-text-plain')?.textContent).toContain('第二行')
  })

  it('returns null for empty content', () => {
    const { container } = render(<RichText content="" />)
    expect(container.firstChild).toBeNull()
  })

  it('applies clamp style when clamp is set', () => {
    const { container } = render(<RichText content="<p>长内容</p>" clamp={3} />)
    const el = container.querySelector('.rich-text-full') as HTMLElement
    expect(el.style.display).toBe('-webkit-box')
    expect(el.style.webkitLineClamp).toBe('3')
    expect(el.style.overflow).toBe('hidden')
  })

  it('sanitizes script tags from editor HTML', () => {
    const malicious = '<p>ok</p>' + '<scr' + 'ipt>alert(1)</scr' + 'ipt>' + '<img src=x onerror="alert(2)">'
    const { container } = render(
      <RichText content={malicious} />,
    )
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('p')?.textContent).toBe('ok')
  })
})
