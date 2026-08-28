import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert } from 'react-native'
import { useState, useEffect } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors, FRUIT_LABELS, FRUIT_COLORS, WISH_STATUS_LABELS, formatCount } from '@/constants/wish-theme'
import WishInteractionBar from '@/components/WishInteractionBar'
import WishCommentSection from '@/components/WishCommentSection'
import WishBGM from '@/components/WishBGM'
import type { WishDetail, WishFulfillmentDetail } from '@/types'

export default function WishDetailScreen() {
  const insets = useSafeAreaInsets()
  const params = useLocalSearchParams<{ id?: string; extend?: string }>()
  const wishId = Number(params.id)
  const user = useAuthStore((s) => s.user)
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetail | null>(null)
  const [fulfillment, setFulfillment] = useState<WishFulfillmentDetail | null>(null)
  // 预期管理通知「延长预期」深链：作者本人修改 expected_at（状态保持不变）
  const [extendOpen, setExtendOpen] = useState(false)
  const [extendSaving, setExtendSaving] = useState(false)

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await wishApi.getWishDetail(wishId)
        if (res.data?.success) {
          setWish(res.data.data)
          // 已还愿心愿加载还愿故事（未还愿/无权限静默忽略）
          if (res.data.data.status === 'FULFILLED') {
            try {
              const fulfillmentRes = await wishApi.getFulfillmentDetail(wishId)
              if (fulfillmentRes.data?.success) {
                setFulfillment(fulfillmentRes.data.data)
              }
            } catch {
              // 静默处理
            }
          }
        }
      } catch {
        // 错误已由 request 拦截器处理
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [wishId])

  // 预期管理通知「延长预期」深链：作者本人且心愿未完结时打开延期选择
  useEffect(() => {
    if (params.extend === '1' && wish && user?.id === wish.authorId
        && (wish.status === 'ACTIVE' || wish.status === 'OVERDUE')) {
      setExtendOpen(true)
    }
  }, [params.extend, wish, user])

  /** 延长预期：新 expected_at = max(当前时间, 原预期) + 天数（状态保持不变） */
  const handleExtend = async (days: number) => {
    if (!wish) return
    const baseTime = Math.max(Date.now(), wish.expectedAt ? new Date(wish.expectedAt).getTime() : 0)
    const nextIso = new Date(baseTime + days * 24 * 3600 * 1000).toISOString()
    setExtendSaving(true)
    try {
      const res = await wishApi.updateWish(wishId, { expectedAt: nextIso })
      if (res.data?.success) {
        setWish((prev) => (prev ? { ...prev, expectedAt: nextIso } : prev))
        setExtendOpen(false)
        Alert.alert('完成', '预期已延长，继续加油')
      }
    } catch {
      Alert.alert('提示', '保存失败，请稍后重试')
    } finally {
      setExtendSaving(false)
    }
  }

  const handleCountsChange = (partial: Partial<Pick<WishDetail, 'lightCount' | 'sameWishCount' | 'blessCount' | 'anonStarCount'>>) => {
    setWish((prev) => (prev ? { ...prev, ...partial } : prev))
  }

  const handleCommentCountChange = (delta: number) => {
    setWish((prev) => (prev ? { ...prev, commentCount: Math.max(0, prev.commentCount + delta) } : prev))
  }

  const handleDelete = () => {
    Alert.alert('确认删除', '删除后不可恢复，确定删除吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '删除',
        style: 'destructive',
        onPress: async () => {
          try {
            const res = await wishApi.deleteWish(wishId)
            if (res.data?.success) {
              Alert.alert('提示', '已删除', [{ text: '好的', onPress: () => router.back() }])
            }
          } catch {
            Alert.alert('错误', '删除失败')
          }
        },
      },
    ])
  }

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: WishColors.bgBase, justifyContent: 'center', alignItems: 'center', paddingTop: insets.top }}>
        <ActivityIndicator size="large" color={WishColors.primary} />
      </View>
    )
  }

  if (!wish) {
    return (
      <View style={{ flex: 1, backgroundColor: WishColors.bgBase, justifyContent: 'center', alignItems: 'center', paddingTop: insets.top }}>
        <Text style={{ fontSize: 48, opacity: 0.3 }}>🌌</Text>
        <Text style={{ fontSize: FontSize.md, color: WishColors.textTertiary, marginTop: Spacing.md }}>
          心愿不存在或已被删除
        </Text>
        <TouchableOpacity onPress={() => router.back()} style={{ marginTop: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>返回</Text>
        </TouchableOpacity>
      </View>
    )
  }

  const isAuthor = user?.id === wish.authorId

  //心愿内容 + 互动按钮组，作为评论 FlatList 的头部插槽
  const detailHeader = (
    <View>
      {/* 媒体 */}
      {wish.mediaUrls && wish.mediaUrls.length > 0 && (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: Spacing.md }}>
          {wish.mediaUrls.map((url) => (
            <Image
              key={url}
              source={{ uri: url }}
              style={{ width: 260, height: 180, borderRadius: BorderRadius.lg, marginRight: Spacing.sm }}
              resizeMode="cover"
            />
          ))}
        </ScrollView>
      )}

      {/* 信息卡 */}
      <View
        style={{
          padding: Spacing.lg,
          borderRadius: BorderRadius.lg,
          backgroundColor: WishColors.bgContainer,
          borderWidth: 1,
          borderColor: WishColors.border,
        }}
      >
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
          <View
            style={{
              paddingHorizontal: Spacing.md,
              paddingVertical: 2,
              borderRadius: 14,
              backgroundColor: FRUIT_COLORS[wish.fruitType],
            }}
          >
            <Text style={{ fontSize: FontSize.xs, color: '#fff', fontWeight: '600' }}>
              {FRUIT_LABELS[wish.fruitType]}
            </Text>
          </View>
          <View style={{ paddingHorizontal: Spacing.md, paddingVertical: 2, borderRadius: 14, backgroundColor: 'rgba(0,212,255,0.12)' }}>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan }}>
              {WISH_STATUS_LABELS[wish.status] || wish.status}
            </Text>
          </View>
          {wish.tags?.map((tag) => (
            <View key={tag} style={{ paddingHorizontal: Spacing.md, paddingVertical: 2, borderRadius: 14, backgroundColor: 'rgba(255,255,255,0.08)' }}>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary }}>{tag}</Text>
            </View>
          ))}
        </View>

        <Text style={{ fontSize: 24, fontWeight: '700', color: WishColors.text, marginTop: Spacing.md, lineHeight: 34 }}>
          {wish.title}
        </Text>

        <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: Spacing.md }}>
          {wish.authorAvatar ? (
            <Image source={{ uri: wish.authorAvatar }} style={{ width: 32, height: 32, borderRadius: 16 }} />
          ) : (
            <View
              style={{
                width: 32,
                height: 32,
                borderRadius: 16,
                backgroundColor: 'rgba(255,255,255,0.1)',
                justifyContent: 'center',
                alignItems: 'center',
              }}
            >
              <Text style={{ fontSize: 12, color: FRUIT_COLORS[wish.fruitType] }}>★</Text>
            </View>
          )}
          <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginLeft: Spacing.sm, fontWeight: '600' }}>
            {wish.authorNickname}
          </Text>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginLeft: 'auto' }}>
            {new Date(wish.createdAt).toLocaleDateString('zh-CN')}
          </Text>
        </View>

        <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary, lineHeight: 24, marginTop: Spacing.md }}>
          {wish.description}
        </Text>

        {wish.expectedAt && (
          <Text style={{ fontSize: FontSize.sm, color: WishColors.accentGold, marginTop: Spacing.md }}>
            📅 预计完成：{new Date(wish.expectedAt).toLocaleDateString('zh-CN')}
          </Text>
        )}

        <View
          style={{
            flexDirection: 'row',
            gap: Spacing.lg,
            marginTop: Spacing.md,
            paddingTop: Spacing.md,
            borderTopWidth: 1,
            borderTopColor: WishColors.border,
          }}
        >
          <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>
            ♥ {formatCount(wish.supportCount)} 互动
          </Text>
          <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>
            💬 {formatCount(wish.commentCount)} 评论
          </Text>
        </View>
      </View>

      {/* 进度 */}
      {wish.progress && (
        <View
          style={{
            marginTop: Spacing.md,
            padding: Spacing.lg,
            borderRadius: BorderRadius.lg,
            backgroundColor: WishColors.bgContainer,
            borderWidth: 1,
            borderColor: WishColors.border,
          }}
        >
          <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text, marginBottom: Spacing.sm }}>
            心愿进度
          </Text>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <View style={{ flex: 1, height: 8, borderRadius: 4, backgroundColor: 'rgba(255,255,255,0.08)', overflow: 'hidden' }}>
              <View
                style={{
                  width: `${Math.min(Math.max(wish.progress.percentage, 0), 100)}%`,
                  height: '100%',
                  borderRadius: 4,
                  backgroundColor: FRUIT_COLORS[wish.fruitType],
                }}
              />
            </View>
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginLeft: Spacing.sm, fontWeight: '600' }}>
              {wish.progress.currentValue}/{wish.progress.targetValue}
            </Text>
          </View>
          <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.xs }}>
            打卡 {wish.checkinDays} 天
          </Text>
        </View>
      )}

      {/* 成长记录 */}
      {wish.growthRecords && wish.growthRecords.length > 0 && (
        <View
          style={{
            marginTop: Spacing.md,
            padding: Spacing.lg,
            borderRadius: BorderRadius.lg,
            backgroundColor: WishColors.bgContainer,
            borderWidth: 1,
            borderColor: WishColors.border,
          }}
        >
          <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text, marginBottom: Spacing.sm }}>
            成长记录
          </Text>
          {wish.growthRecords.map((record) => (
            <View key={record.id} style={{ flexDirection: 'row', paddingVertical: Spacing.sm }}>
              <View
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: 4,
                  backgroundColor: FRUIT_COLORS[wish.fruitType],
                  marginTop: 6,
                  marginRight: Spacing.sm,
                }}
              />
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, lineHeight: 20 }}>
                  {record.content}
                </Text>
                {record.mediaUrls && record.mediaUrls.length > 0 && (
                  <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.xs }}>
                    {record.mediaUrls.map((url) => (
                      <Image
                        key={url}
                        source={{ uri: url }}
                        style={{ width: 80, height: 80, borderRadius: BorderRadius.sm }}
                        resizeMode="cover"
                      />
                    ))}
                  </View>
                )}
                <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 4 }}>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>
                    {new Date(record.createdAt).toLocaleString('zh-CN')}
                  </Text>
                  {record.progressDelta > 0 && (
                    <Text style={{ fontSize: FontSize.xs, color: '#4ade80', marginLeft: Spacing.sm }}>
                      +{record.progressDelta}
                    </Text>
                  )}
                </View>
              </View>
            </View>
          ))}
        </View>
      )}

      {/* 树洞入口（Sprint 1.3：作者本人 + 树洞心愿 + 已启用 AI 回复） */}
      {isAuthor && wish.visibility === 'TREE_HOLE' && wish.enableAiReply && (
        <TouchableOpacity
          onPress={() => router.push(`/tree-hole/${wishId}`)}
          style={{
            marginTop: Spacing.md,
            height: 52,
            borderRadius: BorderRadius.lg,
            backgroundColor: WishColors.accentPurple,
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'center',
            gap: Spacing.sm,
          }}
        >
          <Text style={{ fontSize: 20 }}>🌙</Text>
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#fff', letterSpacing: 1 }}>
            进入树洞 · 让守护者陪你聊聊
          </Text>
        </TouchableOpacity>
      )}

      {/* 互动按钮组（点亮/同求/祝福，Sprint 1.2） */}
      <View
        style={{
          marginTop: Spacing.md,
          padding: Spacing.lg,
          borderRadius: BorderRadius.lg,
          backgroundColor: WishColors.bgContainer,
          borderWidth: 1,
          borderColor: WishColors.border,
        }}
      >
        <WishInteractionBar
          wishId={wishId}
          counts={{
            lightCount: wish.lightCount,
            sameWishCount: wish.sameWishCount,
            blessCount: wish.blessCount,
            anonStarCount: wish.anonStarCount,
          }}
          isLoggedIn={Boolean(user)}
          onCountsChange={handleCountsChange}
          onRequireLogin={() => router.push('/login')}
        />
      </View>

      {/* 还愿故事（Sprint 1.10） */}
      {fulfillment && (
        <View
          style={{
            marginTop: Spacing.md,
            padding: Spacing.lg,
            borderRadius: BorderRadius.lg,
            backgroundColor: 'rgba(15,52,96,0.35)',
            borderWidth: 1,
            borderColor: 'rgba(255,107,107,0.35)',
          }}
        >
          <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text }}>🌸 还愿故事</Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: Spacing.md }}>
            {fulfillment.authorAvatar ? (
              <Image source={{ uri: fulfillment.authorAvatar }} style={{ width: 32, height: 32, borderRadius: 16 }} />
            ) : (
              <View
                style={{
                  width: 32,
                  height: 32,
                  borderRadius: 16,
                  backgroundColor: 'rgba(255,255,255,0.1)',
                  justifyContent: 'center',
                  alignItems: 'center',
                }}
              >
                <Text style={{ fontSize: 12, color: '#ff6b6b' }}>★</Text>
              </View>
            )}
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginLeft: Spacing.sm, fontWeight: '600' }}>
              {fulfillment.authorNickname}
            </Text>
            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginLeft: 'auto' }}>
              还愿于 {new Date(fulfillment.createdAt).toLocaleString('zh-CN')}
            </Text>
          </View>
          <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary, lineHeight: 26, marginTop: Spacing.md }}>
            {fulfillment.story}
          </Text>
          {fulfillment.mediaUrls && fulfillment.mediaUrls.length > 0 && (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, marginTop: Spacing.md }}>
              {fulfillment.mediaUrls.map((url) => (
                <Image
                  key={url}
                  source={{ uri: url }}
                  style={{ width: 100, height: 100, borderRadius: BorderRadius.md }}
                  resizeMode="cover"
                />
              ))}
            </View>
          )}
          {fulfillment.feeling && (
            <View
              style={{
                marginTop: Spacing.md,
                padding: Spacing.md,
                borderRadius: BorderRadius.md,
                backgroundColor: 'rgba(255,255,255,0.05)',
              }}
            >
              <Text style={{ fontSize: FontSize.sm, color: '#ff8fa3', fontWeight: '600' }}>💬 感悟</Text>
              <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, lineHeight: 22, marginTop: Spacing.xs }}>
                {fulfillment.feeling}
              </Text>
            </View>
          )}
        </View>
      )}
    </View>
  )

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          padding: Spacing.md,
          borderBottomWidth: 1,
          borderBottomColor: WishColors.border,
        }}
      >
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.lg, color: WishColors.textSecondary }}>‹ 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginLeft: Spacing.md }}>
          心愿详情
        </Text>
      </View>

      {/* 评论模块承载页面滚动（FlatList + headerComponent），评论触底自动分页 */}
      <WishCommentSection
        wishId={wishId}
        commentCount={wish.commentCount}
        isLoggedIn={Boolean(user)}
        currentUserId={user?.id}
        onCountChange={handleCommentCountChange}
        onRequireLogin={() => router.push('/login')}
        headerComponent={detailHeader}
      />

      {isAuthor && (
        <View
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
            padding: Spacing.md,
            paddingBottom: insets.bottom + Spacing.md,
            backgroundColor: 'rgba(26,26,46,0.95)',
            borderTopWidth: 1,
            borderTopColor: WishColors.border,
            flexDirection: 'row',
            gap: Spacing.md,
          }}
        >
          {(wish.status === 'ACTIVE' || wish.status === 'OVERDUE') && (
            <TouchableOpacity
              activeOpacity={0.85}
              onPress={() => router.push(`/wish-fulfillment?id=${wishId}`)}
              style={{
                flex: 2,
                paddingVertical: Spacing.md,
                borderRadius: 28,
                alignItems: 'center',
                backgroundColor: WishColors.primary,
              }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#fff' }}>🌸 我要还愿</Text>
            </TouchableOpacity>
          )}
          {(wish.status === 'ACTIVE' || wish.status === 'OVERDUE') && (
            <TouchableOpacity
              accessibilityLabel="延长预期"
              onPress={() => setExtendOpen(true)}
              style={{
                flex: 1,
                paddingVertical: Spacing.md,
                borderRadius: 28,
                alignItems: 'center',
                backgroundColor: 'rgba(255,255,255,0.08)',
                borderWidth: 1,
                borderColor: 'rgba(255,255,255,0.25)',
              }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.textSecondary }}>延长预期</Text>
            </TouchableOpacity>
          )}
          <TouchableOpacity
            onPress={handleDelete}
            style={{
              flex: 1,
              paddingVertical: Spacing.md,
              borderRadius: 28,
              alignItems: 'center',
              backgroundColor: 'rgba(233,69,96,0.15)',
              borderWidth: 1,
              borderColor: 'rgba(233,69,96,0.4)',
            }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.primary }}>删除心愿</Text>
          </TouchableOpacity>
        </View>
      )}

      {extendOpen && (
        <View
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: 0,
            bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.6)',
            alignItems: 'center',
            justifyContent: 'center',
            padding: Spacing.xl,
          }}
        >
          <View
            style={{
              width: '100%',
              backgroundColor: WishColors.bgContainer,
              borderRadius: BorderRadius.xl,
              padding: Spacing.lg,
            }}
          >
            <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginBottom: 4 }}>
              延长预期
            </Text>
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginBottom: Spacing.md }}>
              为这个心愿设定一个新的预期完成时间（状态保持进行中）
            </Text>
            {[
              { days: 30, label: '延长 30 天' },
              { days: 90, label: '延长 90 天' },
              { days: 180, label: '延长 180 天' },
            ].map(({ days, label }) => (
              <TouchableOpacity
                key={days}
                accessibilityLabel={label}
                disabled={extendSaving}
                onPress={() => handleExtend(days)}
                style={{
                  paddingVertical: Spacing.md,
                  borderRadius: BorderRadius.md,
                  alignItems: 'center',
                  backgroundColor: 'rgba(255,255,255,0.06)',
                  marginBottom: Spacing.sm,
                }}
              >
                <Text style={{ fontSize: FontSize.md, color: WishColors.text }}>
                  {extendSaving ? '保存中…' : label}
                </Text>
              </TouchableOpacity>
            ))}
            <TouchableOpacity
              accessibilityLabel="取消延长预期"
              onPress={() => setExtendOpen(false)}
              style={{ paddingVertical: Spacing.sm, alignItems: 'center' }}
            >
              <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>取消</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}

      <WishBGM />
    </View>
  )
}
