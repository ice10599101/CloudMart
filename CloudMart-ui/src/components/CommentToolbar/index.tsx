import { useEffect, useRef, useState } from 'react'
import {
  SmileOutlined,
  PictureOutlined,
  TeamOutlined,
  LoadingOutlined,
  CloseOutlined,
} from '@ant-design/icons'
import { uploadFile } from '@/api/file'
import { searchUsers } from '@/api/community'
import s from './style.module.css'

/** 评论中图片片段模式：![alt](https url)。该模式可安全穿过服务端 HTML 转义，渲染层再还原为 <img> */
export const COMMENT_IMAGE_PATTERN = /!\[[^\]]*\]\((https?:\/\/[^\s)]+)\)/g

/** 把评论内容拆分为 文本/图片 段（渲染层据此还原图片，文本部分继续走 @高亮/转义逻辑） */
export function splitCommentImages(
    content: string,
): Array<{ type: 'text' | 'image'; value: string }> {
  const parts: Array<{ type: 'text' | 'image'; value: string }> = []
  let last = 0
  for (const match of content.matchAll(COMMENT_IMAGE_PATTERN)) {
    const idx = match.index ?? 0
    if (idx > last) parts.push({ type: 'text', value: content.slice(last, idx) })
    parts.push({ type: 'image', value: match[1] })
    last = idx + match[0].length
  }
  if (last < content.length) parts.push({ type: 'text', value: content.slice(last) })
  return parts
}

/**
 * 在 textarea/input 光标处插入片段并恢复焦点与光标。
 * 兼容原生元素与 antd TextArea ref（resizableTextArea.textArea）。
 * 返回插入后的新全文。
 */
export function insertAtCursor(
    textareaRef: React.MutableRefObject<any>,
    fragment: string,
    current: string,
    maxLength = Number.POSITIVE_INFINITY,
): string {
  const el: HTMLTextAreaElement | HTMLInputElement | undefined =
      textareaRef?.current?.resizableTextArea?.textArea ?? textareaRef?.current
  const start = el?.selectionStart ?? current.length
  const end = el?.selectionEnd ?? start
  const next = (current.slice(0, start) + fragment + current.slice(end)).slice(0, maxLength)
  requestAnimationFrame(() => {
    el?.focus()
    const pos = Math.min(start + fragment.length, next.length)
    el?.setSelectionRange(pos, pos)
  })
  return next
}

const EMOJI_GROUPS: Array<{ label: string; emojis: string[] }> = [
  { label: '常用', emojis: ['😄', '😊', '🥰', '😂', '🤣', '😍', '🤔', '😅', '😭', '🥺', '😘', '😎', '🤩', '😴', '🙄', '😳', '🤗', '🤫', '😤', '🫡'] },
  { label: '手势', emojis: ['👍', '👎', '👌', '✌️', '🤝', '🙏', '👏', '💪', '🤙', '✊', '👊', '🫶', '🤞', '🖐️'] },
  { label: '心情', emojis: ['❤️', '💔', '💖', '✨', '🔥', '🎉', '💯', '⭐', '🌈', '☀️', '🌧️', '⚡', '🌸', '🎂', '🎁', '🎵'] },
  { label: '动物', emojis: ['🐶', '🐱', '🦊', '🐻', '🐼', '🐨', '🐰', '🦁', '🐷', '🐸', '🐵', '🦄', '🐢', '🐧'] },
  { label: '食物', emojis: ['🍎', '🍔', '🍕', '🍜', '🍰', '🍺', '☕', '🍫', '🍓', '🍚', '🧋', '🍡', '🍦', '🍟'] },
]

const MAX_UPLOAD_BYTES = 5 * 1024 * 1024

interface CommentToolbarProps {
  /** 受控 textarea/input ref（原生元素或 antd ref 均可），用于光标处插入 */
  textareaRef: React.MutableRefObject<any>
  /** 表情/@ 纯文本插入回调：父层用 insertAtCursor 计算新值并 setValue */
  onInsert: (fragment: string) => void
  /** 待发图片（URL 列表）：提供时显示图片按钮与预览条，发表时由父层拼入内容 */
  pendingImages?: string[]
  onImagePicked?: (url: string) => void
  onRemoveImage?: (url: string) => void
  disabled?: boolean
}

/**
 * 评论编辑器工具条：表情选择器 / 图片上传（预览条）/ @用户。
 *
 * <p>弹层向工具条下方展开（不遮挡输入框）；图片走「预览条」模式——
 * 上传后只显示缩略图，发表时由父层把图片 URL 拼入内容。</p>
 */
export default function CommentToolbar({
  textareaRef,
  onInsert,
  pendingImages,
  onImagePicked,
  onRemoveImage,
  disabled = false,
}: CommentToolbarProps) {
  const [panel, setPanel] = useState<'none' | 'emoji' | 'at'>('none')
  const [emojiTab, setEmojiTab] = useState(0)
  const [atQuery, setAtQuery] = useState('')
  const [atResults, setAtResults] = useState<AtUser[]>([])
  const [atLoading, setAtLoading] = useState(false)
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const atTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const imageEnabled = typeof onImagePicked === 'function'

  useEffect(() => {
    if (panel === 'none') return
    const onDocClick = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setPanel('none')
      }
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [panel])

  const togglePanel = (target: 'emoji' | 'at') => {
    setPanel((prev) => (prev === target ? 'none' : target))
    if (target === 'at') {
      setAtQuery('')
      setAtResults([])
    }
    requestAnimationFrame(() => textareaRef.current?.focus())
  }

  const handleImagePick = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file || disabled || !onImagePicked) return
    if (!file.type.startsWith('image/')) return
    if (file.size > MAX_UPLOAD_BYTES) {
      window.alert('图片不能超过 5MB')
      return
    }
    setUploading(true)
    try {
      const res = await uploadFile(file)
      if (res.data.success && res.data.data?.url) {
        onImagePicked(res.data.data.url)
        setPanel('none')
      }
    } catch {
      // 上传失败由拦截器提示
    } finally {
      setUploading(false)
    }
  }

  const handleAtQueryChange = (query: string) => {
    setAtQuery(query)
    if (atTimerRef.current) clearTimeout(atTimerRef.current)
    const keyword = query.trim()
    if (!keyword) {
      setAtResults([])
      return
    }
    atTimerRef.current = setTimeout(async () => {
      setAtLoading(true)
      try {
        const res = await searchUsers(keyword)
        setAtResults((res.data.data ?? []).slice(0, 8))
      } catch {
        setAtResults([])
      } finally {
        setAtLoading(false)
      }
    }, 300)
  }

  const selectUser = (user: AtUser) => {
    onInsert(`@${user.nickname} `)
    setPanel('none')
  }

  return (
      <div className={s.root} ref={rootRef}>
        {pendingImages && pendingImages.length > 0 && (
            <div className={s.previewStrip}>
              {pendingImages.map((url) => (
                  <div key={url} className={s.previewItem}>
                    <img src={url} alt="待发图片" className={s.previewThumb} />
                    <button
                        type="button"
                        className={s.previewRemove}
                        aria-label="移除图片"
                        onClick={() => onRemoveImage?.(url)}
                    >
                      <CloseOutlined />
                    </button>
                  </div>
              ))}
            </div>
        )}
        <div className={s.btnRow}>
          <button
              type="button"
              className={s.toolBtn}
              title="表情"
              disabled={disabled}
              aria-label="插入表情"
              onClick={() => togglePanel('emoji')}
          >
            <SmileOutlined />
          </button>
          {imageEnabled && (
              <button
                  type="button"
                  className={s.toolBtn}
                  title="上传图片"
                  disabled={disabled || uploading}
                  aria-label="上传图片"
                  onClick={() => fileInputRef.current?.click()}
              >
                {uploading ? <LoadingOutlined /> : <PictureOutlined />}
              </button>
          )}
          <button
              type="button"
              className={s.toolBtn}
              title="@用户"
              disabled={disabled}
              aria-label="提及用户"
              onClick={() => togglePanel('at')}
          >
            <TeamOutlined />
          </button>
        </div>
        <input
            ref={fileInputRef}
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            style={{ display: 'none' }}
            onChange={handleImagePick}
        />

        {panel === 'emoji' && (
            <div className={`${s.panel} ${s.panelBelow}`} role="dialog" aria-label="选择表情">
              <div className={s.emojiTabs}>
                {EMOJI_GROUPS.map((group, i) => (
                    <button
                        key={group.label}
                        type="button"
                        className={`${s.emojiTab} ${i === emojiTab ? s.emojiTabActive : ''}`}
                        onClick={() => setEmojiTab(i)}
                    >
                      {group.label}
                    </button>
                ))}
              </div>
              <div className={s.emojiGrid}>
                {EMOJI_GROUPS[emojiTab]?.emojis.map((emoji) => (
                    <button
                        key={emoji}
                        type="button"
                        className={s.emojiCell}
                        onClick={() => {
                          onInsert(emoji)
                        }}
                    >
                      {emoji}
                    </button>
                ))}
              </div>
            </div>
        )}

        {panel === 'at' && (
            <div className={`${s.panel} ${s.panelBelow}`} role="dialog" aria-label="提及用户">
              <input
                  className={s.atSearch}
                  value={atQuery}
                  placeholder="输入昵称搜索用户..."
                  autoFocus
                  onChange={(e) => handleAtQueryChange(e.target.value)}
              />
              <div className={s.atResults}>
                {atLoading && <div className={s.atHint}>搜索中...</div>}
                {!atLoading && atResults.length === 0 && (
                    <div className={s.atHint}>输入昵称搜索要 @ 的用户</div>
                )}
                {atResults.map((user) => (
                    <button
                        key={user.id}
                        type="button"
                        className={s.atItem}
                        onClick={() => selectUser(user)}
                    >
                      {user.avatar ? (
                          <img src={user.avatar} alt="" className={s.atAvatar} />
                      ) : (
                          <span className={s.atAvatarPlaceholder}>{user.nickname?.charAt(0) || '?'}</span>
                      )}
                      <span>{user.nickname}</span>
                    </button>
                ))}
              </div>
            </div>
        )}
      </div>
  )
}

interface AtUser {
  id: number
  nickname: string
  avatar?: string
}
