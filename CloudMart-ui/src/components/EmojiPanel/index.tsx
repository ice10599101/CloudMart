import { useState } from 'react'
import { Popover, Tabs } from 'antd'
import { SmileOutlined } from '@ant-design/icons'
import { EMOJI_GROUPS } from '@/components/TiptapEmojiAt'
import styles from './index.module.css'

/**
 * 独立表情选择器（纯文本输入框用，如漂流瓶瓶下评论）。
 * 与 TiptapEmojiAt 共用表情分组数据；选中后通过 onPick 回调插入。
 * 开关交给 antd Popover 托管（trigger=click），点选表情时不会误关闭；
 * 向下弹出（bottomLeft），不遮挡上方的回复输入框。
 */

interface EmojiPanelProps {
  onPick: (emoji: string) => void
  disabled?: boolean
}

export default function EmojiPanel({ onPick, disabled = false }: EmojiPanelProps) {
  const [open, setOpen] = useState(false)

  const pick = (emoji: string) => {
    onPick(emoji)
    setOpen(false)
  }

  return (
    <Popover
      open={open}
      onOpenChange={setOpen}
      trigger="click"
      placement="bottomLeft"
      content={
        <div className={styles.panel}>
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
                      onClick={() => pick(emoji)}
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
        aria-label="选择表情"
        title="表情"
      >
        <SmileOutlined />
      </button>
    </Popover>
  )
}
