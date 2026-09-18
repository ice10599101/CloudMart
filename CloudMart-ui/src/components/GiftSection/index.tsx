import { useCallback, useEffect, useState } from 'react'
import { Button, Space, Tag } from 'antd'
import { GiftOutlined, StarFilled } from '@ant-design/icons'
import GiftPickerModal from '@/components/GiftPickerModal'
import { listTargetGiftRecords } from '@/api/gift'
import type { GiftRecordItem, GiftTargetType, SendGiftResult } from '@/api/gift'
import { useAuthStore } from '@/stores/auth'
import { history } from 'umi'
import styles from './index.module.css'

/**
 * 礼物区块（送礼按钮 + 场景礼物墙）。
 *
 * 心愿详情/帖子详情复用：按钮打开礼物选择器，送礼成功后刷新礼物墙。
 * 未登录点击 → 跳转登录页（与全站未登录交互一致）。
 */

interface GiftSectionProps {
  targetType: Extract<GiftTargetType, 'WISH' | 'POST'>
  targetId: number | string
}

export default function GiftSection({ targetType, targetId }: GiftSectionProps) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [records, setRecords] = useState<GiftRecordItem[]>([])
  const [loading, setLoading] = useState(false)

  const loadRecords = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listTargetGiftRecords(targetType, targetId, undefined, 10)
      if (res.success) setRecords(res.data ?? [])
    } catch {
      // 礼物墙加载失败不阻塞主内容展示
    } finally {
      setLoading(false)
    }
  }, [targetType, targetId])

  useEffect(() => {
    if (accessToken) loadRecords()
  }, [accessToken, loadRecords])

  const handleSent = (result: SendGiftResult) => {
    setRecords((prev) => [
      {
        id: result.recordId,
        giftId: result.giftId,
        giftName: result.giftName,
        giftIconUrl: result.giftIconUrl,
        count: result.count,
        totalPrice: result.totalPrice,
        senderId: 0,
        senderNickname: '我',
        receiverId: result.receiverId,
        receiverNickname: null,
        targetType: result.targetType,
        targetId: result.targetId,
        message: null,
        createdAt: new Date().toISOString(),
      },
      ...prev,
    ])
  }

  return (
    <div className={styles.giftSection}>
      <Button
        type="primary"
        ghost
        icon={<GiftOutlined />}
        onClick={() => {
          if (!accessToken) {
            history.push('/login')
            return
          }
          setPickerOpen(true)
        }}
      >
        送礼物
      </Button>

      {records.length > 0 && (
        <div className={styles.giftWall}>
          <div className={styles.giftWallTitle}>收到的礼物</div>
          <Space size={[6, 6]} wrap>
            {records.map((record) => (
              <Tag
                key={record.id}
                icon={record.giftIconUrl ? undefined : <GiftOutlined />}
                color="gold"
                className={styles.giftTag}
              >
                {record.giftIconUrl && (
                  <img className={styles.giftTagIcon} src={record.giftIconUrl} alt={record.giftName} />
                )}
                {record.giftName} ×{record.count}
                {record.senderNickname && <span className={styles.giftSender}> · {record.senderNickname}</span>}
                <span className={styles.giftPrice}> ✦{record.totalPrice}</span>
                <StarFilled className={styles.giftStar} />
              </Tag>
            ))}
          </Space>
        </div>
      )}

      {accessToken && !loading && records.length === 0 && (
        <div className={styles.giftWallHint}>还没有人送出礼物，做第一个吧</div>
      )}

      <GiftPickerModal
        open={pickerOpen}
        targetType={targetType}
        targetId={targetId}
        onClose={() => setPickerOpen(false)}
        onSent={handleSent}
      />
    </div>
  )
}
