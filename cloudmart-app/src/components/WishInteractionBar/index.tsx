import { useCallback, useEffect, useMemo, useState } from 'react'
import { View, Text, TouchableOpacity, Modal, TextInput, Alert, Vibration, Platform } from 'react-native'
import { wishApi } from '@/api/wish'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type { ApiResponse, MyWishInteraction } from '@/types'

/**
 * 心愿互动按钮组（Sprint 1.2）。
 *
 * 规则：点亮可重复（每次扣 2 星光）；同求每愿望唯一（已同求高亮，可取消）；
 * 祝福每愿望每日 1 次（今日已祝福禁用）；未登录点击引导登录。
 *
 * 触感反馈：React Native 内置 Vibration（expo-haptics 因 registry 不可达暂未引入，
 * 网络恢复后可替换为 Haptics.impactAsync 获得 Taptic Engine 分级反馈）。
 */

const BLESS_CONTENT_MAX = 200
/** 轻触反馈时长（ms），iOS/Android 通用 */
const HAPTIC_DURATION_MS = 15

export interface WishInteractionCounts {
  lightCount: number
  sameWishCount: number
  blessCount: number
}

interface WishInteractionBarProps {
  wishId: number
  counts: WishInteractionCounts
  isLoggedIn: boolean
  onCountsChange: (counts: Partial<WishInteractionCounts>) => void
  onRequireLogin: () => void
}

/** 触感反馈：iOS Vibration 不生效时静默（不阻断主流程） */
function hapticLight() {
  try {
    if (Platform.OS === 'android') {
      Vibration.vibrate(HAPTIC_DURATION_MS)
    } else {
      Vibration.vibrate()
    }
  } catch {
    // 触感失败不影响业务
  }
}

/** 业务错误提示（APP 端 request 不统一弹错，组件自行 Alert） */
function alertBusinessError(body: ApiResponse<unknown>) {
  const code = body.error?.code ?? ''
  const title = code === 'WISH_STARLIGHT_INSUFFICIENT'
    ? '星光不足，可通过每日签到、打卡获取星光'
    : code === 'WISH_ALREADY_INTERACTED'
      ? '已同求过该心愿'
      : body.error?.message ?? '操作失败，请稍后重试'
  Alert.alert('提示', title)
}

export default function WishInteractionBar({
  wishId,
  counts,
  isLoggedIn,
  onCountsChange,
  onRequireLogin,
}: WishInteractionBarProps) {
  const [myInteractions, setMyInteractions] = useState<MyWishInteraction[]>([])
  const [blessModalVisible, setBlessModalVisible] = useState(false)
  const [blessContent, setBlessContent] = useState('')
  const [blessing, setBlessing] = useState(false)

  const refreshMyInteractions = useCallback(async () => {
    if (!isLoggedIn) {
      setMyInteractions([])
      return
    }
    try {
      const res = await wishApi.listMyInteractions(wishId)
      if (res.data?.success) setMyInteractions(res.data.data)
    } catch {
      // 状态接口失败不阻断详情展示，按钮按未互动处理
    }
  }, [wishId, isLoggedIn])

  useEffect(() => {
    refreshMyInteractions()
  }, [refreshMyInteractions])

  const mySameWish = useMemo(
    () => myInteractions.find((i) => i.type === 'SAME_WISH') ?? null,
    [myInteractions],
  )
  const blessedToday = useMemo(
    () => myInteractions.some((i) => i.type === 'BLESS' && i.createdToday),
    [myInteractions],
  )
  const myLightCount = useMemo(
    () => myInteractions.filter((i) => i.type === 'LIGHT').length,
    [myInteractions],
  )

  const requireLoginOr = (action: () => void) => {
    if (!isLoggedIn) {
      Alert.alert('提示', '登录后即可互动', [
        { text: '取消', style: 'cancel' },
        { text: '去登录', onPress: onRequireLogin },
      ])
      return
    }
    action()
  }

  const handleLight = async () => {
    hapticLight()
    try {
      const res = await wishApi.createInteraction(wishId, { type: 'LIGHT' })
      if (res.data?.success) {
        onCountsChange({ lightCount: res.data.data.lightCount })
        refreshMyInteractions()
        Alert.alert('已点亮 ✨', '为 TA 加了一束光')
      } else if (res.data) {
        alertBusinessError(res.data)
        if (res.data.error?.code === 'WISH_ALREADY_INTERACTED') refreshMyInteractions()
      }
    } catch {
      Alert.alert('错误', '点亮失败，请稍后重试')
    }
  }

  const handleSameWish = async () => {
    hapticLight()
    try {
      const res = await wishApi.createInteraction(wishId, { type: 'SAME_WISH' })
      if (res.data?.success) {
        onCountsChange({ sameWishCount: res.data.data.sameWishCount })
        refreshMyInteractions()
        Alert.alert('已加入共同愿望 🤝')
      } else if (res.data) {
        alertBusinessError(res.data)
        if (res.data.error?.code === 'WISH_ALREADY_INTERACTED') refreshMyInteractions()
      }
    } catch {
      Alert.alert('错误', '同求失败，请稍后重试')
    }
  }

  const handleRevokeSameWish = () => {
    if (!mySameWish) return
    Alert.alert('取消同求？', '取消后可重新同求，已消耗星光不退还', [
      { text: '保留', style: 'cancel' },
      {
        text: '取消同求',
        style: 'destructive',
        onPress: async () => {
          try {
            const res = await wishApi.revokeInteraction(wishId, mySameWish.id)
            if (res.data?.success) {
              onCountsChange({ sameWishCount: Math.max(0, counts.sameWishCount - 1) })
              refreshMyInteractions()
              Alert.alert('已取消同求')
            } else if (res.data) {
              alertBusinessError(res.data)
            }
          } catch {
            Alert.alert('错误', '操作失败，请稍后重试')
          }
        },
      },
    ])
  }

  const handleBless = async () => {
    const content = blessContent.trim()
    if (!content) {
      Alert.alert('提示', '写一句祝福吧')
      return
    }
    setBlessing(true)
    try {
      const res = await wishApi.createInteraction(wishId, { type: 'BLESS', content })
      if (res.data?.success) {
        hapticLight()
        onCountsChange({ blessCount: res.data.data.blessCount })
        refreshMyInteractions()
        setBlessModalVisible(false)
        Alert.alert('祝福已送达 🌟')
      } else if (res.data) {
        alertBusinessError(res.data)
      }
    } catch {
      Alert.alert('错误', '祝福失败，请稍后重试')
    } finally {
      setBlessing(false)
    }
  }

  const btnBaseStyle = {
    flex: 1,
    alignItems: 'center' as const,
    paddingVertical: Spacing.md,
    borderRadius: BorderRadius.lg,
    borderWidth: 1,
    borderColor: WishColors.border,
    backgroundColor: 'rgba(255,255,255,0.06)',
  }

  const btnIconStyle = {
    fontSize: 22,
    lineHeight: 28,
  }

  const btnTextStyle = {
    fontSize: FontSize.xs,
    color: WishColors.textSecondary,
    marginTop: 4,
  }

  return (
    <View>
      <View style={{ flexDirection: 'row', gap: Spacing.md }}>
        <TouchableOpacity
          style={btnBaseStyle}
          activeOpacity={0.7}
          onPress={() => requireLoginOr(handleLight)}
          accessibilityLabel={`点亮，已点亮 ${counts.lightCount} 次`}
        >
          <Text style={btnIconStyle}>💡</Text>
          <Text style={btnTextStyle}>
            点亮 {counts.lightCount}
            {myLightCount > 0 ? `（我 ${myLightCount}）` : ''}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            btnBaseStyle,
            mySameWish && {
              borderColor: WishColors.primary,
              backgroundColor: 'rgba(233,69,96,0.12)',
            },
          ]}
          activeOpacity={0.7}
          onPress={() => requireLoginOr(mySameWish ? handleRevokeSameWish : handleSameWish)}
          accessibilityLabel={
            mySameWish
              ? `已同求，共同愿望 ${counts.sameWishCount} 人，点击取消同求`
              : `同求，共同愿望 ${counts.sameWishCount} 人`
          }
        >
          <Text style={btnIconStyle}>🤝</Text>
          <Text style={[btnTextStyle, mySameWish && { color: WishColors.primary, fontWeight: '600' }]}>
            {mySameWish ? '已同求' : '同求'} {counts.sameWishCount}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[btnBaseStyle, blessedToday && { opacity: 0.45 }]}
          activeOpacity={0.7}
          disabled={blessedToday}
          onPress={() => requireLoginOr(() => {
            setBlessContent('')
            setBlessModalVisible(true)
          })}
          accessibilityLabel={
            blessedToday
              ? `今日已祝福，累计祝福 ${counts.blessCount} 次`
              : `送出祝福，累计祝福 ${counts.blessCount} 次`
          }
        >
          <Text style={btnIconStyle}>{blessedToday ? '⭐' : '🌟'}</Text>
          <Text style={btnTextStyle}>
            {blessedToday ? '已祝福' : '祝福'} {counts.blessCount}
          </Text>
        </TouchableOpacity>
      </View>

      {/* 祝福输入弹层 */}
      <Modal
        visible={blessModalVisible}
        transparent
        animationType="slide"
        onRequestClose={() => setBlessModalVisible(false)}
      >
        <View
          style={{
            flex: 1,
            justifyContent: 'flex-end',
            backgroundColor: 'rgba(0,0,0,0.6)',
          }}
        >
          <View
            style={{
              backgroundColor: WishColors.bgContainer,
              borderTopLeftRadius: BorderRadius.xl,
              borderTopRightRadius: BorderRadius.xl,
              padding: Spacing.lg,
              paddingBottom: Spacing.xxxl,
              gap: Spacing.md,
            }}
          >
            <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>
              送出祝福 🌟
            </Text>
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary }}>
              愿 TA 梦想成真（{BLESS_CONTENT_MAX} 字以内）
            </Text>
            <TextInput
              value={blessContent}
              onChangeText={(t) => setBlessContent(t.slice(0, BLESS_CONTENT_MAX))}
              placeholder="例如：希望你梦想成真！"
              placeholderTextColor={WishColors.textTertiary}
              multiline
              maxLength={BLESS_CONTENT_MAX}
              style={{
                minHeight: 90,
                padding: Spacing.md,
                borderRadius: BorderRadius.md,
                backgroundColor: 'rgba(255,255,255,0.08)',
                color: WishColors.text,
                fontSize: FontSize.md,
                textAlignVertical: 'top',
              }}
            />
            <View style={{ flexDirection: 'row', gap: Spacing.md }}>
              <TouchableOpacity
                style={{
                  flex: 1,
                  paddingVertical: Spacing.md,
                  borderRadius: BorderRadius.xl,
                  alignItems: 'center',
                  backgroundColor: 'rgba(255,255,255,0.1)',
                }}
                onPress={() => setBlessModalVisible(false)}
              >
                <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={{
                  flex: 2,
                  paddingVertical: Spacing.md,
                  borderRadius: BorderRadius.xl,
                  alignItems: 'center',
                  backgroundColor: WishColors.primary,
                  opacity: blessContent.trim() && !blessing ? 1 : 0.5,
                }}
                disabled={!blessContent.trim() || blessing}
                onPress={handleBless}
              >
                <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#fff' }}>
                  {blessing ? '发送中...' : '送出祝福'}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  )
}
