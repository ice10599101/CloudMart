import { useEffect, useMemo, useState } from 'react'
import { Input, Modal, Segmented, Space, Spin, Tag, Tooltip } from 'antd'
import { SendOutlined, StarFilled, GiftOutlined } from '@ant-design/icons'
import { listGifts, sendGift } from '@/api/gift'
import type { GiftItem, GiftTargetType, SendGiftResult } from '@/api/gift'
import { getMyResources } from '@/api/wish'
import { useMessage } from '@/utils/useMessage'
import styles from './index.module.css'

/**
 * 礼物选择器弹窗（全站虚拟礼物）。
 *
 * 展示上架礼物目录（管理后台维护），按单价 × 数量计算星光总消耗，
 * 送礼成功回调 onSent（直播间场景用于播放特效/刷新记录）。
 * 余额展示来自 mall-wish /my/resources；余额不足禁用送礼按钮。
 */

interface GiftPickerModalProps {
  open: boolean
  targetType: GiftTargetType
  targetId: number | string
  /** 弹窗标题（默认按场景生成） */
  title?: string
  onClose: () => void
  onSent?: (result: SendGiftResult) => void
}

const COUNT_PRESETS = [1, 10, 52, 99]
const MAX_COUNT = 99

const TARGET_LABEL: Record<GiftTargetType, string> = {
  WISH: '心愿',
  POST: '帖子',
  LIVE_ROOM: '主播',
}

export default function GiftPickerModal({
  open,
  targetType,
  targetId,
  title,
  onClose,
  onSent,
}: GiftPickerModalProps) {
  const message = useMessage()
  const [gifts, setGifts] = useState<GiftItem[]>([])
  const [loading, setLoading] = useState(false)
  const [balance, setBalance] = useState<number | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [count, setCount] = useState(1)
  const [messageText, setMessageText] = useState('')
  const [sending, setSending] = useState(false)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    Promise.all([
      listGifts().then(({ data: res }) => {
        if (res.success) setGifts(res.data ?? [])
      }),
      getMyResources().then(({ data: res }) => {
        if (res.success && res.data) setBalance(res.data.balance ?? null)
      }).catch(() => setBalance(null)),
    ]).finally(() => setLoading(false))
  }, [open])

  const selected = useMemo(
    () => gifts.find((gift) => gift.id === selectedId) ?? null,
    [gifts, selectedId],
  )
  const totalPrice = (selected?.priceStarlight ?? 0) * count
  const isInsufficient = balance !== null && totalPrice > balance

  const reset = () => {
    setSelectedId(null)
    setCount(1)
    setMessageText('')
  }

  const handleSend = async () => {
    if (!selected || sending) return
    setSending(true)
    try {
      const { data: res } = await sendGift({
        giftId: selected.id,
        count,
        targetType,
        targetId,
        message: messageText.trim() || undefined,
      })
      if (res.success && res.data) {
        message.success(`送出 ${selected.name} ×${count}，消耗 ${res.data.totalPrice} 星光`)
        setBalance(res.data.balanceAfter)
        onSent?.(res.data)
        reset()
        onClose()
      }
    } finally {
      setSending(false)
    }
  }

  return (
    <Modal
      title={title ?? `送礼物给${TARGET_LABEL[targetType]}`}
      open={open}
      onCancel={() => {
        reset()
        onClose()
      }}
      width={460}
      footer={null}
      destroyOnHidden
    >
      <Spin spinning={loading}>
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 13 }}>
              {balance !== null ? (
                <>
                  星光余额 <Tag icon={<StarFilled />} color="gold">{balance}</Tag>
                </>
              ) : (
                '加载余额中…'
              )}
            </span>
            {selected && (
              <span style={{ fontSize: 13 }}>
                合计 <Tag icon={<StarFilled />} color={isInsufficient ? 'red' : 'gold'}>{totalPrice}</Tag>
              </span>
            )}
          </div>

          {gifts.length === 0 && !loading ? (
            <div className={styles.giftEmpty}>礼物货架空空如也，快去看看别的心意吧</div>
          ) : (
            <div className={styles.giftGrid}>
              {gifts.map((gift) => (
                <Tooltip key={gift.id} title={gift.description ?? gift.name} mouseEnterDelay={0.4}>
                  <div
                    role="button"
                    tabIndex={0}
                    aria-label={`送出礼物${gift.name}，单价${gift.priceStarlight}星光`}
                    className={gift.id === selectedId ? styles.giftItemSelected : styles.giftItem}
                    onClick={() => setSelectedId(gift.id)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') setSelectedId(gift.id)
                    }}
                  >
                    {gift.iconUrl ? (
                      <img className={styles.giftIconImg} src={gift.iconUrl} alt={gift.name} />
                    ) : (
                      <GiftOutlined className={styles.giftIcon} style={{ color: '#faad14' }} />
                    )}
                    <span className={styles.giftName}>{gift.name}</span>
                    <span className={styles.giftPrice}>✦ {gift.priceStarlight}</span>
                  </div>
                </Tooltip>
              ))}
            </div>
          )}

          <Space size={8} wrap>
            <Segmented
              value={count}
              onChange={(value) => setCount(value as number)}
              options={COUNT_PRESETS.map((preset) => ({ label: `×${preset}`, value: preset }))}
            />
            <Input
              type="number"
              min={1}
              max={MAX_COUNT}
              value={count}
              onChange={(event) => {
                const parsed = Number.parseInt(event.target.value, 10)
                setCount(Number.isNaN(parsed) ? 1 : Math.min(Math.max(parsed, 1), MAX_COUNT))
              }}
              style={{ width: 72 }}
              aria-label="礼物数量"
            />
          </Space>

          <Input
            placeholder="写下你的心意（可选，100 字以内）"
            maxLength={100}
            value={messageText}
            onChange={(event) => setMessageText(event.target.value)}
            showCount
          />

          <button
            type="button"
            className={styles.sendButton}
            disabled={!selected || sending || isInsufficient}
            onClick={handleSend}
          >
            {isInsufficient
              ? '星光余额不足（去签到/点亮心愿可获取）'
              : sending
                ? '送礼中…'
                : selected
                  ? `送出 ${selected.name} ×${count}`
                  : '选择一个礼物'}
          </button>
        </Space>
      </Spin>
    </Modal>
  )
}
