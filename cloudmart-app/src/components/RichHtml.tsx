import React, { useMemo } from 'react'
import { Text, StyleSheet, TextProps } from 'react-native'
import RenderHTML from 'react-native-render-html'

/**
 * 判断内容是否为富文本（编辑器 HTML）。历史纯文本数据按换行渲染不丢格式。
 */
export function isRichText(content: string | null | undefined): boolean {
  if (!content) return false
  return /<\/?(p|div|br|h[1-6]|ul|ol|li|blockquote|pre|img|table|strong|em|b|i|u|s|span|a|code)\b/i.test(content)
}

export interface RichHtmlProps {
  /** 编辑器产出的 HTML 或历史纯文本 */
  content: string | null | undefined
  /** 内容宽度（RenderHTML 必需，取内容容器宽度） */
  width: number
  /** 主题色与文字色（跟随当前主题） */
  color: string
  linkColor?: string
  fontSize?: number
  /** 附加 Text 样式（纯文本分支） */
  textStyle?: TextProps['style']
}

/**
 * 移动端统一富文本渲染组件。
 * 所有编辑器发布的内容（帖子/心愿描述/公告/系统通知）必须经此组件渲染，
 * 禁止以 <Text>{content}</Text> 直接插值（HTML 会被原样显示为标签文本）。
 * HTML 经 react-native-render-html 渲染（RN 无 DOM，脚本不可执行，事件属性被忽略）；
 * 纯文本（历史数据）按换行渲染。
 */
export default function RichHtml({
  content,
  width,
  color,
  linkColor = '#00D4FF',
  fontSize = 15,
  textStyle,
}: RichHtmlProps) {
  const isHtml = isRichText(content)

  const source = useMemo(() => ({ html: content ?? '' }), [content])

  if (!content || !content.trim()) return null

  if (!isHtml) {
    return <Text style={[{ fontSize, color, lineHeight: 24 }, styles.plain, textStyle]}>{content}</Text>
  }

  return (
    <RenderHTML
      contentWidth={width}
      source={source}
      defaultTextProps={{ allowFontScaling: false }}
      tagsStyles={{
        p: { color, fontSize, marginTop: 0, marginBottom: 8 },
        h1: { color, fontSize: fontSize + 5, marginBottom: 6 },
        h2: { color, fontSize: fontSize + 3, marginBottom: 6 },
        h3: { color, fontSize: fontSize + 1, marginBottom: 4 },
        a: { color: linkColor, textDecorationLine: 'underline' },
        img: { borderRadius: 8 },
        blockquote: { borderLeftWidth: 3, borderLeftColor: linkColor, paddingLeft: 10, backgroundColor: 'rgba(127,127,127,0.08)' },
        li: { color, fontSize },
      }}
      baseStyle={{ color, fontSize, lineHeight: 24 }}
    />
  )
}

const styles = StyleSheet.create({
  plain: {
    // 历史纯文本保留换行
  },
})
