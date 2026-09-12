import { useMemo } from 'react'
import DOMPurify from 'dompurify'

/**
 * 判断内容是否为富文本（HTML）。编辑器（TipTap）产出的内容都带块级标签；
 * 兼容历史纯文本数据：不含标签的内容按纯文本渲染并保留换行。
 */
export function isRichText(content: string | null | undefined): boolean {
  if (!content) return false
  return /<\/?(p|div|br|h[1-6]|ul|ol|li|blockquote|pre|img|table|thead|tbody|tr|strong|em|b|i|u|s|strike|span|a|code|hr)\b/i.test(content)
}

/**
 * 提取纯文本（用于表格列摘要、搜索快照等摘要场景）。
 * 与 utils/format.stripHtml 语义一致，这里提供组件域内统一实现。
 */
export function richTextToPlainText(content: string | null | undefined): string {
  if (!content) return ''
  return content
    .replace(/<\/(p|div|h[1-6]|li|tr|blockquote)>/gi, '\n')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

// 模块级注册一次：外链统一新窗口打开并隔离 opener，防钓鱼防 reverse tabnabbing
let hookRegistered = false
function ensureLinkSafetyHook() {
  if (hookRegistered) return
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A' && node.getAttribute('href')) {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer nofollow')
    }
  })
  hookRegistered = true
}

const clampStyle = (lines: number): React.CSSProperties => ({
  display: '-webkit-box',
  WebkitBoxOrient: 'vertical',
  WebkitLineClamp: lines,
  overflow: 'hidden',
})

export interface RichTextProps {
  /** 编辑器产出的 HTML 或历史纯文本 */
  content: string | null | undefined
  /** 可选行数截断（列表卡片预览场景），完整渲染时不传 */
  clamp?: number
  /** 附加类名（调用方用自己的 CSS Module 类控制外层布局） */
  className?: string
  /** 内容排版样式类：clamp 预览用紧凑排版，详情用完整排版 */
  variant?: 'preview' | 'full'
  style?: React.CSSProperties
  onClick?: (e: React.MouseEvent<HTMLDivElement>) => void
}

/**
 * 统一富文本渲染组件。
 *
 * 所有由 Tiptap 编辑器发布的内容（帖子/心愿描述/公告/系统通知/商品详情）必须经此组件渲染，
 * 禁止以 {content} 文本插值（会原样显示 HTML 标签）或 stripHtml 纯文本降级展示。
 * HTML 内容经 DOMPurify 消毒；纯文本（历史数据）按换行分段渲染，不丢格式。
 */
export default function RichText({ content, clamp, className, variant = 'full', style, onClick }: RichTextProps) {
  ensureLinkSafetyHook()

  const isHtml = isRichText(content)

  const html = useMemo(() => {
    if (!content) return ''
    return isHtml ? DOMPurify.sanitize(content) : ''
  }, [content, isHtml])

  if (!content || !content.trim()) return null

  if (isHtml) {
    return (
      <div
        className={`rich-text rich-text-${variant} ${className ?? ''}`}
        style={{ ...(clamp ? clampStyle(clamp) : undefined), ...style }}
        onClick={onClick}
        dangerouslySetInnerHTML={{ __html: html }}
      />
    )
  }

  // 历史纯文本：保留换行语义，同样支持 clamp
  return (
    <div
      className={`rich-text rich-text-plain rich-text-${variant} ${className ?? ''}`}
      style={{
        whiteSpace: 'pre-line',
        wordBreak: 'break-word',
        ...(clamp ? clampStyle(clamp) : undefined),
        ...style,
      }}
      onClick={onClick}
    >
      {content}
    </div>
  )
}
