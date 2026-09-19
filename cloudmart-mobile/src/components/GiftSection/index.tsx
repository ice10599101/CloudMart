import { useEffect, useState } from 'react'
import { View, Text, Input, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { giftApi } from '@/api/gift'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import AssetIcon from '@/components/AssetIcon'
import type { GiftItem, GiftRecordItem, GiftTargetType } from '@/types'
import styles from './index.module.scss'

/**
 * 全站虚拟礼物区块（对齐 Web 端 GiftSection + GiftPickerModal）。
 * 送礼按钮 + 场景礼物墙（心愿/帖子/直播间复用）。
 * 余额展示来自 /wish/my/resources；余额不足禁用送礼。
 */

const COUNT_PRESETS = [1, 10, 52, 99]
const MAX_COUNT = 99

const TARGET_LABEL: Record<GiftTargetType, string> = {
  WISH: '心愿',
  POST: '帖子',
  LIVE_ROOM: '主播',
}

interface GiftSectionProps {
  targetType: GiftTargetType
  targetId: number | string
  /** 外部刷新信号（送礼后自动刷新） */
  refreshTick?: number
}

export default function GiftSection({ targetType, targetId, refreshTick = 0 }: GiftSectionProps) {
  const { isLoggedIn } = useAuthStore()
  const [pickerOpen, setPickerOpen] = useState(false)
  const [records, setRecords] = useState<GiftRecordItem[]>([])
  const [loading, setLoading] = useState(false)

  // 弹窗内状态
  const [gifts, setGifts] = useState<GiftItem[]>([])
  const [balance, setBalance] = useState<number | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [count, setCount] = useState(1)
  const [messageText, setMessageText] = useState('')
  const [sending, setSending] = useState(false)

  const loadRecords = async () => {
    setLoading(true)
    try {
      const res = await giftApi.listTargetGiftRecords(targetType, targetId, { pageSize: 10 })
      if (res.data.success) setRecords(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (targetId) void loadRecords()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [targetType, targetId, refreshTick])

  const openPicker = async () => {
    if (!isLoggedIn) {
      Taro.showToast({ title: '请先登录', icon: 'none' })
      return
    }
    setPickerOpen(true)
    try {
      const [giftsRes, resourcesRes] = await Promise.all([
        giftApi.listGifts(),
        wishApi.getMyResources().catch(() => null),
      ])
      if (giftsRes.data.success) setGifts(giftsRes.data.data ?? [])
      if (resourcesRes?.data.success) setBalance(resourcesRes.data.data?.balance ?? null)
    } catch {
      // 静默
    }
  }

  const selectedGift = gifts.find((g) => g.id === selectedId) ?? null
  const totalPrice = selectedGift ? selectedGift.priceStarlight * count : 0
  const insufficient = balance != null && totalPrice > balance

  const handleSend = async () => {
    if (!selectedGift || sending) return
    if (insufficient) {
      Taro.showToast({ title: '星光余额不足', icon: 'none' })
      return
    }
    setSending(true)
    try {
      const res = await giftApi.sendGift({
        giftId: selectedGift.id,
        count,
        targetType,
        targetId,
        message: messageText.trim() || undefined,
      })
      if (res.data.success) {
        Taro.showToast({ title: `送出 ${selectedGift.name} ×${count} 🎁`, icon: 'none' })
        setPickerOpen(false)
        setMessageText('')
        setCount(1)
        void loadRecords()
      } else {
        Taro.showToast({ title: res.data.error?.message ?? '送礼失败', icon: 'none' })
      }
    } catch (err) {
      const message =
        (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message || '送礼失败'
      Taro.showToast({ title: message, icon: 'none' })
    } finally {
      setSending(false)
    }
  }

  return (
    <View className={styles.section}>
      <View className={styles.header}>
        <Text className={styles.title}>🎁 礼物墙</Text>
        <View className={styles.sendBtn} onClick={openPicker}>
          <Text className={styles.sendBtnText}>送礼物</Text>
        </View>
      </View>

      {records.length > 0 ? (
        <ScrollView scrollX enhanced showScrollbar={false} className={styles.recordsScroll}>
          <View className={styles.recordsRow}>
            {records.map((record) => (
              <View key={record.id} className={styles.recordChip}>
                <AssetIcon icon={record.giftIconUrl ?? '🎁'} size={28} />
                <Text className={styles.recordText}>
                  {record.senderNickname ?? '神秘人'} 送出 {record.giftName} ×{record.count}
                </Text>
              </View>
            ))}
          </View>
        </ScrollView>
      ) : (
        !loading && <Text className={styles.emptyText}>还没有人送出礼物，来做第一个吧</Text>
      )}

      {/* 送礼弹窗 */}
      {pickerOpen && (
        <View className={styles.mask} onClick={() => !sending && setPickerOpen(false)}>
          <View className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <View className={styles.modalHeader}>
              <Text className={styles.modalTitle}>送礼物给{TARGET_LABEL[targetType]}</Text>
              <Text className={styles.balance}>星光 {balance ?? '--'}</Text>
            </View>

            <ScrollView scrollY className={styles.giftGrid}>
              <View className={styles.gridRow}>
                {gifts.map((gift) => (
                  <View
                    key={gift.id}
                    className={`${styles.giftCard} ${selectedId === gift.id ? styles.giftCardActive : ''}`}
                    onClick={() => setSelectedId(gift.id)}
                  >
                    <AssetIcon icon={gift.iconUrl ?? '🎁'} size={44} />
                    <Text className={styles.giftName}>{gift.name}</Text>
                    <Text className={styles.giftPrice}>⭐ {gift.priceStarlight}</Text>
                  </View>
                ))}
              </View>
              {gifts.length === 0 && <Text className={styles.emptyText}>礼物架暂未上架</Text>}
            </ScrollView>

            <View className={styles.countRow}>
              {COUNT_PRESETS.map((preset) => (
                <View
                  key={preset}
                  className={`${styles.countChip} ${count === preset ? styles.countChipActive : ''}`}
                  onClick={() => setCount(preset)}
                >
                  <Text className={styles.countChipText}>×{preset}</Text>
                </View>
              ))}
              <Input
                className={styles.countInput}
                type='number'
                value={String(count)}
                onInput={(e) => {
                  const n = Number(e.detail.value)
                  if (Number.isFinite(n) && n > 0) setCount(Math.min(n, MAX_COUNT))
                }}
              />
            </View>

            <Input
              className={styles.messageInput}
              placeholder='附言（可选）'
              value={messageText}
              onInput={(e) => setMessageText(e.detail.value)}
              maxlength={50}
            />

            <View className={styles.footer}>
              <Text className={styles.totalText}>
                {selectedGift ? `合计 ⭐${totalPrice}` : '选择一份礼物'}
                {insufficient ? '（余额不足）' : ''}
              </Text>
              <View
                className={`${styles.confirmBtn} ${!selectedGift || insufficient ? styles.confirmBtnDisabled : ''}`}
                onClick={() => selectedGift && !insufficient && handleSend()}
              >
                <Text className={styles.confirmBtnText}>{sending ? '送礼中...' : '送出'}</Text>
              </View>
            </View>
          </View>
        </View>
      )}
    </View>
  )
}
