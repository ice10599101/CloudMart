import { useEffect, useRef, useState } from 'react'
import { Popover, Tabs } from 'antd'
import { SmileOutlined } from '@ant-design/icons'
import { EMOJI_GROUPS } from '@/components/TiptapEmojiAt'
import styles from './index.module.css'

/**
 * 独立表情选择器（纯文本输入框用，如漂流瓶瓶下评论）。
 * 与 TiptapEmojiAt 共用表情分组数据；选中后通过 onPick 回调插入。
 */

interface EmojiPanelProps {
  onPick: (emoji: string) => void
  disabled?: boolean
}

export default function EmojiPanel({ onPick, disabled = false }: EmojiPanelProps) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!open) return
    const onDocClick = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [open])

  return (
    <div ref={rootRef}>
      <Popover
        open={open}
        placement="topLeft"
        trigger={[]}
        content={
          <div className={styles.panel} onClick={(e) => e.stopPropagation()}>
            <Tabs
              size="small"
              items={EMOJI_GROUPS.map((group, index) => ({
                key: String(index),
                label: group.label,
                children: (
                  <div className={styles.grid}>
                    {group.emojis.map((emoji) => (
                      <button
                        key={emoji}
                        type="button"
                        className={styles.cell}
                        onClick={() => onPick(emoji)}
                        aria-label={`插入表情 ${emoji}`}
                      >
                        {emoji}
                      </button>
                    ))}
                  </div>
                ),
              }))}
            />
          </div>
        }
      >
        <button
          type="button"
          className={styles.trigger}
          disabled={disabled}
          onClick={() => setOpen((prev) => !prev)}
          aria-label="选择表情"
          title="表情"
        >
          <SmileOutlined />
        </button>
      </Popover>
    </div>
  )
}
