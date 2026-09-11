import { RichText as TaroRichText } from '@tarojs/components'
import { Text } from '@tarojs/components'

/**
 * 判断内容是否为富文本（编辑器 HTML）。历史纯文本数据按换行渲染不丢格式。
 */
export function isRichText(content: string | null | undefined): boolean {
  if (!content) return false
  return /<\/?(p|div|br|h[1-6]|ul|ol|li|blockquote|pre|img|table|strong|em|b|i|u|s|span|a|code)\b/i.test(content)
}

export interface RichTextProps {
  /** 编辑器产出的 HTML 或历史纯文本 */
  content: string | null | undefined
  className?: string
}

/**
 * 小程序/H5 端统一富文本渲染组件。
 * 所有编辑器发布的内容（帖子/心愿描述/公告/系统通知）必须经此组件渲染，
 * 禁止以 <Text>{content}</Text> 直接插值（HTML 会被原样显示为标签文本）。
 * 小程序端 TaroRichText 仅渲染标签白名单节点、不执行脚本；纯文本按换行渲染。
 */
export default function RichText({ content, className }: RichTextProps) {
  if (!content || !content.trim()) return null

  if (isRichText(content)) {
    return <TaroRichText nodes={content} className={className} />
  }

  return <Text className={className} userSelect style={{ whiteSpace: 'pre-line', wordBreak: 'break-word' }}>{content}</Text>
}
