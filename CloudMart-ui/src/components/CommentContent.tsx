import { history } from 'umi'
import { splitCommentImages } from '@/components/CommentToolbar'

/**
 * 评论内容统一渲染（社区/软件共用）：markdown 图片段 + @昵称高亮跳搜索。
 * 与 PostDetail 的 renderCommentContent 同源提取，避免两处维护同一套规则。
 */
export default function CommentContent({ content }: { content: string }) {
  return (
    <>
      {splitCommentImages(content).map((part, partIndex) => {
        if (part.type === 'image') {
          return (
            <img
              key={`img-${partIndex}`}
              src={part.value}
              alt="图片"
              style={{
                maxWidth: 'min(100%, 280px)',
                maxHeight: 220,
                borderRadius: 10,
                display: 'block',
                margin: '6px 0',
              }}
            />
          )
        }
        const parts = part.value.split(/(@[\w\u4e00-\u9fa5]+)/g)
        return parts.map((text, i) => {
          if (text.startsWith('@') && text.length > 1) {
            const nickname = text.slice(1)
            return (
              <span
                key={`${partIndex}-${i}`}
                style={{ color: 'var(--color-primary)', cursor: 'pointer' }}
                onClick={() => history.push(`/search?q=${encodeURIComponent(nickname)}`)}
              >
                {text}
              </span>
            )
          }
          return text
        })
      })}
    </>
  )
}
