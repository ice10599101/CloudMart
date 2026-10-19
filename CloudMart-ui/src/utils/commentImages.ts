/** 图片标记支持标准 ![alt](url) 及历史遗留的 [图片](url) 两种格式。 */
const COMMENT_IMAGE_PATTERN = /(?:!\[[^\]]*\]|\[图片\])\((https?:\/\/[^\s)]+|\/files\/[^\s)]+)\)/g

/** 把评论内容拆分为文本/图片段；文本继续走 React 转义逻辑。 */
export function splitCommentImages(
  content: string,
): Array<{ type: 'text' | 'image'; value: string }> {
  const parts: Array<{ type: 'text' | 'image'; value: string }> = []
  let last = 0
  for (const match of content.matchAll(COMMENT_IMAGE_PATTERN)) {
    const index = match.index ?? 0
    if (index > last) parts.push({ type: 'text', value: content.slice(last, index) })
    parts.push({ type: 'image', value: match[1] })
    last = index + match[0].length
  }
  if (last < content.length) parts.push({ type: 'text', value: content.slice(last) })
  return parts
}
