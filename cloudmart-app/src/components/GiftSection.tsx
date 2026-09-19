import { View, Text, ScrollView, TouchableOpacity, Image, TextInput, Modal, ActivityIndicator } from 'react-native'
import { useEffect, useState } from 'react'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { giftApi } from '@/api/gift'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import AssetIcon from '@/components/AssetIcon'
import type { GiftItem, GiftRecordItem, GiftTargetType } from '@/types'

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
  /** 外部刷新信号（送礼后自动刷新礼物墙） */
  refreshTick?: number
}

export default function GiftSection({ targetType, targetId, refreshTick = 0 }: GiftSectionProps) {
  const theme = useTheme()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
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
      setRecords(res.data?.data ?? [])
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
    if (!isLoggedIn) return
    setPickerOpen(true)
    try {
      const [giftsRes, resourcesRes] = await Promise.all([
        giftApi.listGifts(),
        wishApi.getMyResources().catch(() => null),
      ])
      setGifts(giftsRes.data?.data ?? [])
      if (resourcesRes) setBalance(resourcesRes.data?.data?.balance ?? null)
    } catch {
      // 静默
    }
  }

  const selectedGift = gifts.find((g) => g.id === selectedId) ?? null
  const totalPrice = selectedGift ? selectedGift.priceStarlight * count : 0
  const insufficient = balance != null && totalPrice > balance

  const handleSend = async () => {
    if (!selectedGift || sending) return
    if (insufficient) return
    setSending(true)
    try {
      const res = await giftApi.sendGift({
        giftId: selectedGift.id,
        count,
        targetType,
        targetId,
        message: messageText.trim() || undefined,
      })
      if (res.data?.success) {
        setPickerOpen(false)
        setMessageText('')
        setCount(1)
        void loadRecords()
      }
    } catch (err) {
      const message =
        (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
      // 失败信息由 request 拦截器或此处置顶提示
      if (message) console.warn('[gift] send failed:', message)
    } finally {
      setSending(false)
    }
  }

  return (
    <View
      style={{
        marginHorizontal: Spacing.lg,
        marginTop: Spacing.md,
        padding: Spacing.lg,
        borderRadius: BorderRadius.lg,
        backgroundColor: 'rgba(255,255,255,0.03)',
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.08)',
      }}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishText }}>🎁 礼物墙</Text>
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={openPicker}
          style={{
            paddingHorizontal: Spacing.lg,
            paddingVertical: Spacing.xs,
            borderRadius: BorderRadius.xl,
            backgroundColor: '#e94560',
          }}
        >
          <Text style={{ fontSize: FontSize.xs, color: '#FFFFFF', fontWeight: '600' }}>送礼物</Text>
        </TouchableOpacity>
      </View>

      {records.length > 0 ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginTop: Spacing.sm }}>
          {records.map((record) => (
            <View
              key={record.id}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                gap: 6,
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.xs,
                borderRadius: BorderRadius.xl,
                backgroundColor: 'rgba(233,69,96,0.12)',
                marginRight: Spacing.sm,
              }}
            >
              <AssetIcon icon={record.giftIconUrl ?? '🎁'} size={22} />
              <Text style={{ fontSize: FontSize.xs, color: 'rgba(255,255,255,0.75)' }}>
                {record.senderNickname ?? '神秘人'} 送出 {record.giftName} ×{record.count}
              </Text>
            </View>
          ))}
        </ScrollView>
      ) : (
        !loading && (
          <Text style={{ textAlign: 'center', paddingVertical: Spacing.md, fontSize: FontSize.xs, color: 'rgba(255,255,255,0.4)' }}>
            还没有人送出礼物，来做第一个吧
          </Text>
        )
      )}

      {/* 送礼弹窗 */}
      <Modal visible={pickerOpen} transparent animationType="slide" onRequestClose={() => !sending && setPickerOpen(false)}>
        <TouchableOpacity activeOpacity={1} onPress={() => !sending && setPickerOpen(false)} style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' }}>
          <TouchableOpacity activeOpacity={1} style={{ backgroundColor: '#16213e', borderTopLeftRadius: BorderRadius.xl, borderTopRightRadius: BorderRadius.xl, padding: Spacing.xl, maxHeight: '80%' }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: Spacing.md }}>
              <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: '#FFFFFF' }}>送礼物给{TARGET_LABEL[targetType]}</Text>
              <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: '#ffd700' }}>星光 {balance ?? '--'}</Text>
            </View>

            <ScrollView style={{ maxHeight: 320 }} showsVerticalScrollIndicator={false}>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
                {gifts.map((gift) => {
                  const isActive = selectedId === gift.id
                  return (
                    <TouchableOpacity
                      key={gift.id}
                      activeOpacity={0.7}
                      onPress={() => setSelectedId(gift.id)}
                      style={{
                        width: '30%',
                        alignItems: 'center',
                        padding: Spacing.sm,
                        borderWidth: 1,
                        borderColor: isActive ? '#e94560' : 'rgba(255,255,255,0.1)',
                        borderRadius: BorderRadius.md,
                        backgroundColor: isActive ? 'rgba(233,69,96,0.12)' : 'transparent',
                      }}
                    >
                      <AssetIcon icon={gift.iconUrl ?? '🎁'} size={36} />
                      <Text numberOfLines={1} style={{ marginTop: 6, fontSize: FontSize.xs, color: '#FFFFFF' }}>{gift.name}</Text>
                      <Text style={{ fontSize: FontSize.xs, color: '#ffd700' }}>⭐ {gift.priceStarlight}</Text>
                    </TouchableOpacity>
                  )
                })}
              </View>
              {gifts.length === 0 && (
                <Text style={{ textAlign: 'center', paddingVertical: Spacing.xl, fontSize: FontSize.sm, color: 'rgba(255,255,255,0.4)' }}>
                  礼物架暂未上架
                </Text>
              )}
            </ScrollView>

            <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, marginTop: Spacing.md }}>
              {COUNT_PRESETS.map((preset) => (
                <TouchableOpacity
                  key={preset}
                  activeOpacity={0.7}
                  onPress={() => setCount(preset)}
                  style={{
                    paddingHorizontal: Spacing.md,
                    paddingVertical: Spacing.xs,
                    borderRadius: BorderRadius.xl,
                    borderWidth: 1,
                    borderColor: count === preset ? '#e94560' : 'rgba(255,255,255,0.1)',
                  }}
                >
                  <Text style={{ fontSize: FontSize.xs, color: count === preset ? '#e94560' : 'rgba(255,255,255,0.6)' }}>×{preset}</Text>
                </TouchableOpacity>
              ))}
              <TextInput
                value={String(count)}
                onChangeText={(text) => {
                  const n = Number(text.replace(/[^0-9]/g, ''))
                  if (Number.isFinite(n) && n > 0) setCount(Math.min(n, MAX_COUNT))
                }}
                keyboardType="number-pad"
                style={{
                  width: 56,
                  paddingVertical: Spacing.xs,
                  borderWidth: 1,
                  borderColor: 'rgba(255,255,255,0.1)',
                  borderRadius: BorderRadius.md,
                  textAlign: 'center',
                  color: '#FFFFFF',
                  fontSize: FontSize.sm,
                }}
              />
            </View>

            <TextInput
              placeholder="附言（可选）"
              placeholderTextColor="rgba(255,255,255,0.3)"
              value={messageText}
              onChangeText={setMessageText}
              maxLength={50}
              style={{
                marginTop: Spacing.sm,
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.sm,
                borderWidth: 1,
                borderColor: 'rgba(255,255,255,0.1)',
                borderRadius: BorderRadius.md,
                color: '#FFFFFF',
                fontSize: FontSize.sm,
              }}
            />

            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: Spacing.lg }}>
              <Text style={{ fontSize: FontSize.sm, color: 'rgba(255,255,255,0.65)' }}>
                {selectedGift ? `合计 ⭐${totalPrice}${insufficient ? '（余额不足）' : ''}` : '选择一份礼物'}
              </Text>
              <TouchableOpacity
                activeOpacity={0.8}
                onPress={handleSend}
                disabled={!selectedGift || insufficient || sending}
                style={{
                  paddingHorizontal: Spacing.xxl,
                  paddingVertical: Spacing.sm,
                  borderRadius: BorderRadius.xl,
                  backgroundColor: !selectedGift || insufficient ? 'rgba(255,255,255,0.1)' : '#e94560',
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: Spacing.xs,
                }}
              >
                {sending && <ActivityIndicator size="small" color="#FFFFFF" />}
                <Text style={{ fontSize: FontSize.sm, color: !selectedGift || insufficient ? 'rgba(255,255,255,0.4)' : '#FFFFFF', fontWeight: '600' }}>
                  送出
                </Text>
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </View>
  )
}

const WishText = '#FFFFFF'
