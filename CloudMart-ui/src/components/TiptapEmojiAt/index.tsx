import { useEffect, useRef, useState } from 'react'
import { SmileOutlined, TeamOutlined } from '@ant-design/icons'
import { searchUsers } from '@/api/community'
import DecoratedAvatar from '@/components/DecoratedAvatar'
import s from './style.module.css'

export const EMOJI_GROUPS: Array<{ label: string; emojis: string[] }> = [
  { label: '常用', emojis: ['😄', '😊', '🥰', '😂', '🤣', '😍', '🤔', '😅', '😭', '🥺', '😘', '😎', '🤩', '😴', '🙄', '😳', '🤗', '🤫', '😤', '🫡'] },
  { label: '手势', emojis: ['👍', '👎', '👌', '✌️', '🤝', '🙏', '👏', '💪', '🤙', '✊', '👊', '🫶', '🤞', '🖐️'] },
  { label: '心情', emojis: ['❤️', '💔', '💖', '✨', '🔥', '🎉', '💯', '⭐', '🌈', '☀️', '🌧️', '⚡', '🌸', '🎂', '🎁', '🎵'] },
  { label: '动物', emojis: ['🐶', '🐱', '🦊', '🐻', '🐼', '🐨', '🐰', '🦁', '🐷', '🐸', '🐵', '🦄', '🐢', '🐧'] },
  { label: '食物', emojis: ['🍎', '🍔', '🍕', '🍜', '🍰', '🍺', '☕', '🍫', '🍓', '🍚', '🧋', '🍡', '🍦', '🍟'] },
]

interface TiptapEmojiAtProps {
  /** TipTap editor 实例 */
  editor: any
  disabled?: boolean
}

/**
 * TipTap 富文本编辑器的 表情选择器 + @用户 工具按钮。
 * 表情在光标处插入；@用户 从搜索结果选择后插入「@昵称 」文本。
 */
export default function TiptapEmojiAt({ editor, disabled = false }: TiptapEmojiAtProps) {
  const [panel, setPanel] = useState<'none' | 'emoji' | 'at'>('none')
  const [emojiTab, setEmojiTab] = useState(0)
  const [atQuery, setAtQuery] = useState('')
  const [atResults, setAtResults] = useState<Array<{ id: number; nickname: string; avatar?: string }>>([])
  const [atLoading, setAtLoading] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const atTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

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

  const insert = (text: string) => {
    editor?.chain().focus().insertContent(text).run()
  }

  const togglePanel = (target: 'emoji' | 'at') => {
    setPanel((prev) => (prev === target ? 'none' : target))
    if (target === 'at') {
      setAtQuery('')
      setAtResults([])
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

  return (
      <div className={s.root} ref={rootRef}>
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
                        onClick={() => insert(emoji)}
                    >
                      {emoji}
                    </button>
                ))}
              </div>
            </div>
        )}

        {panel === 'at' && (
            <div className={`${s.panel} ${s.panelBelow} ${s.atPanelWide}`} role="dialog" aria-label="提及用户">
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
                        onClick={() => {
                          insert(`@${user.nickname} `)
                          setPanel('none')
                        }}
                    >
                      <DecoratedAvatar
                          userId={user.id}
                          src={user.avatar}
                          size={26}
                          fallback={user.nickname?.charAt(0) || '?'}
                      />
                      <span>{user.nickname}</span>
                    </button>
                ))}
              </div>
            </div>
        )}
      </div>
  )
}
