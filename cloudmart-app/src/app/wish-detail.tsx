import { View, Text, ScrollView, TouchableOpacity, Image, ActivityIndicator, Alert, TextInput, Share } from 'react-native'
import { useState, useEffect } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { fileApi } from '@/api/file'
import * as ImagePicker from 'expo-image-picker'
import WishCheckinCalendar from '@/components/WishCheckinCalendar'
import { isOnline, enqueueCheckin, flushQueue } from '@/utils/offlineCheckin'
import NetInfo from '@react-native-community/netinfo'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors, FRUIT_LABELS, FRUIT_COLORS, WISH_STATUS_LABELS, formatCount } from '@/constants/wish-theme'
import WishInteractionBar from '@/components/WishInteractionBar'
import WishCommentSection from '@/components/WishCommentSection'
import WishBGM from '@/components/WishBGM'
import WishShareCard from '@/components/WishShareCard'
import type { WishDetail, WishFulfillmentDetail } from '@/types'

export default function WishDetailScreen() {
  const insets = useSafeAreaInsets()
  const params = useLocalSearchParams<{ id?: string; extend?: string }>()
  const wishId = String(params.id ?? '')
  const user = useAuthStore((s) => s.user)
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetail | null>(null)
  const [fulfillment, setFulfillment] = useState<WishFulfillmentDetail | null>(null)
  // 预期管理通知「延长预期」深链：作者本人修改 expected_at（状态保持不变）
  const [extendOpen, setExtendOpen] = useState(false)
  const [extendSaving, setExtendSaving] = useState(false)
  // 每日打卡（仅作者 + ACTIVE；成功后本地记录今日已打卡，重复提交 409 由后端幂等兜底）
  const [checkinOpen, setCheckinOpen] = useState(false)
  const [checkinContent, setCheckinContent] = useState('')
  const [checkinSaving, setCheckinSaving] = useState(false)
  const [checkedInToday, setCheckedInToday] = useState(false)
  // 收藏（B2，非作者）+ 成长记录（B1，仅作者）
  const [collected, setCollected] = useState(false)
  const [collectSaving, setCollectSaving] = useState(false)
  const [growthOpen, setGrowthOpen] = useState(false)
  const [growthType, setGrowthType] = useState<'TEXT' | 'DIARY' | 'IMAGE'>('TEXT')
  // IMAGE 类型：本地上传列表（key/base64/url/status），复用发布页上传链路
  const [growthUploads, setGrowthUploads] = useState<Array<{ key: string; base64: string; url?: string; status: 'uploading' | 'success' | 'error' }>>([])
  const [growthPicking, setGrowthPicking] = useState(false)
  const [growthContent, setGrowthContent] = useState('')
  const [growthDelta, setGrowthDelta] = useState('')
  const [growthSaving, setGrowthSaving] = useState(false)
  // 分享卡片（作者/非作者均可生成星空卡片）
  const [shareOpen, setShareOpen] = useState(false)
  // 星火永久收藏（文档 2.3，仅作者对 FULFILLED+BLOOM 心愿）
  const [sparkSaving, setSparkSaving] = useState(false)

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

  // 离线打卡队列：网络恢复时静默补传（Sprint 1.3 APP 验收）
  useEffect(() => {
    const unsub = NetInfo.addEventListener((state) => {
      if (state.isConnected && state.isInternetReachable) {
        void flushQueue((wishId, ok) => {
          if (ok && wishId === wishId) {
            // 打卡补传成功后刷新对应详情
          }
        })
      }
    })
    return () => unsub()
  }, [])

  void isOnline()

  /** 提交每日打卡：断网入队（恢复后静默补传）；成功后刷新详情 */
  const handleCheckinSubmit = async () => {
    setCheckinSaving(true)
    try {
      if (!(await isOnline())) {
        await enqueueCheckin(wishId, checkinContent.trim() || null)
        Alert.alert('已离线保存 📴', '当前无网络，打卡已存入离线队列，联网后自动补传')
        setCheckinOpen(false)
        setCheckinContent('')
        setCheckedInToday(true)
        return
      }
      const res = await wishApi.checkinWish(wishId, checkinContent.trim() || undefined)
      if (res.data?.success) {
        const { currentStreak, starlightCredited } = res.data.data
        Alert.alert('打卡成功 🌟', `已连续 ${currentStreak} 天，星光 +${starlightCredited} ✨`)
        setCheckinOpen(false)
        setCheckinContent('')
        setCheckedInToday(true)
        const detailRes = await wishApi.getWishDetail(wishId)
        if (detailRes.data?.success) {
          setWish(detailRes.data.data)
        }
      }
    } catch (error) {
      // axios 异常体：response.data.error.code
      const code = (error as { response?: { data?: { error?: { code?: string } } } })
        ?.response?.data?.error?.code
      if (code === 'WISH_ALREADY_CHECKIN_TODAY') {
        Alert.alert('提示', '今天已经打过卡啦，明天再来')
        setCheckedInToday(true)
        setCheckinOpen(false)
      } else if (code === 'WISH_STATUS_CONFLICT') {
        Alert.alert('提示', '仅进行中的心愿可打卡')
      } else {
        Alert.alert('提示', '打卡失败，请稍后重试')
      }
    } finally {
      setCheckinSaving(false)
    }
  }

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

  // 收藏状态回显（非作者；登录态）
  useEffect(() => {
    if (!user || !wish || user.id === wish.authorId) return
    wishApi.getWishCollectionStatus(wishId)
      .then((res) => { if (res.data.success) setCollected(res.data.data === true) })
      .catch(() => setCollected(false))
  }, [user, wish, wishId])

  /** 收藏/取消收藏 */
  const handleCollectToggle = async () => {
    setCollectSaving(true)
    try {
      if (collected) {
        const res = await wishApi.uncollectWish(wishId)
        if (res.data.success) { setCollected(false); Alert.alert('提示', '已取消收藏') }
      } else {
        const res = await wishApi.collectWish(wishId)
        if (res.data.success) { setCollected(true); Alert.alert('提示', '已收藏') }
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      Alert.alert('提示', errNode?.response?.data?.error?.message || '操作失败，请稍后重试')
    } finally {
      setCollectSaving(false)
    }
  }

  /** 选图（最多 9 张）并逐张上传：IMAGE 成长记录媒体 */
  const pickGrowthImages = async () => {
    if (growthPicking) return
    setGrowthPicking(true)
    try {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync()
      if (!permission.granted) {
        Alert.alert('提示', '需要相册权限才能上传图片')
        return
      }
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        quality: 0.8,
        base64: true,
        allowsMultipleSelection: true,
        selectionLimit: 9 - growthUploads.length,
      })
      if (result.canceled) return
      const remaining = 9 - growthUploads.length
      const items = result.assets.slice(0, remaining).map((a, i) => ({
        key: `g-${Date.now()}-${i}`,
        base64: a.base64 ?? '',
        status: 'uploading' as const,
      }))
      setGrowthUploads((prev) => [...prev, ...items])
      items.forEach((item) => uploadGrowthImage(item.key, item.base64))
    } finally {
      setGrowthPicking(false)
    }
  }

  const uploadGrowthImage = async (key: string, base64: string) => {
    try {
      const res = await fileApi.upload({ file: base64, type: 'image/jpeg' })
      const url = res.data?.data?.url
      if (!url) throw new Error('upload failed')
      setGrowthUploads((prev) => prev.map((u) => (u.key === key ? { ...u, url, status: 'success' } : u)))
    } catch {
      setGrowthUploads((prev) => prev.map((u) => (u.key === key ? { ...u, status: 'error' } : u)))
    }
  }

  /** 传承给同路人（Sprint 2.7）：还愿后定向推曾同求用户 */
  const [legacySaving, setLegacySaving] = useState(false)
  const handleLegacy = () => {
    Alert.alert('传承给同路人', '将你的故事推送给曾与你同求这个心愿的人，鼓励他们继续前行。确定传承吗？', [
      { text: '再想想', style: 'cancel' },
      {
        text: '确定传承',
        onPress: async () => {
          setLegacySaving(true)
          try {
            const res = await wishApi.inheritFulfillment(wishId)
            if (res.data.success) Alert.alert('完成', '传承成功，故事正在照亮同路人 ✨')
          } catch (err) {
            const e = err as { data?: { error?: { message?: string } } }
            Alert.alert('失败', e?.data?.error?.message || '传承失败，请稍后重试')
          } finally {
            setLegacySaving(false)
          }
        },
      },
    ])
  }

  /** 提交成长记录：成功后刷新详情（时间线 + 进度） */
  const handleGrowthSubmit = async () => {
    if (!growthContent.trim()) {
      Alert.alert('提示', '请填写成长记录内容')
      return
    }
    setGrowthSaving(true)
    try {
      const mediaUrls = growthUploads
        .filter((u) => u.status === 'success' && u.url)
        .map((u) => u.url as string)
      if (growthType === 'IMAGE' && mediaUrls.length === 0) {
        Alert.alert('提示', '图片记录需至少上传 1 张图片')
        return
      }
      const res = await wishApi.addGrowthRecord(wishId, {
        type: growthType,
        content: growthContent.trim() || (growthType === 'IMAGE' ? '📷 图片记录' : ''),
        mediaUrls: mediaUrls.length > 0 ? mediaUrls : undefined,
        progressDelta: growthDelta ? Number(growthDelta) : undefined,
      })
      if (res.data.success) {
        setGrowthOpen(false)
        setGrowthContent('')
        setGrowthDelta('')
        setGrowthUploads([])
        const detailRes = await wishApi.getWishDetail(wishId)
        if (detailRes.data.success) setWish(detailRes.data.data)
        Alert.alert('完成', '成长记录已添加')
      }
    } catch (err) {
      const errNode = err as { response?: { data?: { error?: { message?: string } } } }
      Alert.alert('提示', errNode?.response?.data?.error?.message || '保存失败，请稍后重试')
    } finally {
      setGrowthSaving(false)
    }
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

  /** 设为星火永久收藏（文档 2.3：仅作者对 FULFILLED+BLOOM 心愿，幂等，二次确认） */
  const handleSpark = () => {
    Alert.alert('设为星火永久收藏', '心愿将以星火形态在世界生命树永久展示，可被他人收藏到收藏馆。确定吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '确定',
        onPress: async () => {
          setSparkSaving(true)
          try {
            const res = await wishApi.sparkWish(wishId)
            if (res.data?.success) {
              Alert.alert('提示', '已设为星火永久收藏')
              setWish((prev) => (prev ? { ...prev, fruitType: 'SPARK' } : prev))
            }
          } catch (err) {
            const errNode = err as { response?: { data?: { error?: { message?: string } } } }
            Alert.alert('错误', errNode?.response?.data?.error?.message || '设置失败，请稍后重试')
          } finally {
            setSparkSaving(false)
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

  /** 系统分享面板（Sprint 1.5 体验要求：APP 走系统分享） */
  const handleWishShare = async () => {
    if (!wish) return
    try {
      await Share.share({ message: `✦ 心愿宇宙 ✦
「${wish.title}」
许愿人：${wish.authorNickname}` })
    } catch {
      // 用户取消
    }
  }

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
          {isAuthor && (
            <WishCheckinCalendar wishId={wishId} accentColor={FRUIT_COLORS[wish.fruitType]} />
          )}
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

      {!isAuthor && (
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
          <TouchableOpacity
            activeOpacity={0.85}
            accessibilityLabel={collected ? '已收藏' : '收藏心愿'}
            disabled={collectSaving}
            onPress={handleCollectToggle}
            style={{
              flex: 1,
              paddingVertical: Spacing.md,
              borderRadius: 28,
              alignItems: 'center',
              backgroundColor: collected ? 'rgba(255, 215, 0, 0.15)' : 'rgba(255,255,255,0.08)',
              borderWidth: 1,
              borderColor: collected ? 'rgba(255, 215, 0, 0.5)' : 'rgba(255,255,255,0.25)',
            }}
          >
            <Text style={{ fontSize: FontSize.md, color: collected ? '#FFD700' : WishColors.text }}>
              {collected ? '⭐ 已收藏' : '☆ 收藏心愿'}
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            activeOpacity={0.85}
            accessibilityLabel="分享心愿"
            onPress={() => setShareOpen(true)}
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
            <Text style={{ fontSize: FontSize.md, color: WishColors.text }}>✨ 分享</Text>
          </TouchableOpacity>
        </View>
      )}
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
              accessibilityLabel="记录成长"
              onPress={() => setGrowthOpen(true)}
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
              <Text style={{ fontSize: FontSize.md, color: WishColors.text }}>📝 记录成长</Text>
            </TouchableOpacity>
          )}
          {wish.status === 'ACTIVE' && (
            <TouchableOpacity
              activeOpacity={0.85}
              accessibilityLabel={checkedInToday ? '今日已打卡' : '每日打卡'}
              onPress={() => {
                if (checkedInToday) {
                  Alert.alert('提示', '今天已经打过卡啦，明天再来')
                  return
                }
                setCheckinOpen(true)
              }}
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
              <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.textSecondary }}>
                {checkedInToday ? '✅ 已打卡' : '📅 打卡'}
              </Text>
            </TouchableOpacity>
          )}
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
          {/* 星火永久收藏（文档 2.3：FULFILLED+BLOOM 可设置；SPARK 展示已收藏态） */}
          {wish.status === 'FULFILLED' && wish.fruitType === 'BLOOM' && (
            <TouchableOpacity
              activeOpacity={0.85}
              accessibilityLabel="设为星火永久收藏"
              disabled={sparkSaving}
              onPress={handleSpark}
              style={{
                flex: 2,
                paddingVertical: Spacing.md,
                borderRadius: 28,
                alignItems: 'center',
                backgroundColor: '#ffb727',
              }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#4a3200' }}>
                {sparkSaving ? '设置中...' : '⭐ 设为星火'}
              </Text>
            </TouchableOpacity>
          )}
          {wish.status === 'FULFILLED' && (
            <TouchableOpacity
              activeOpacity={0.85}
              disabled={legacySaving}
              onPress={handleLegacy}
              style={{
                flex: 2,
                paddingVertical: Spacing.md,
                borderRadius: 28,
                alignItems: 'center',
                backgroundColor: 'rgba(80, 200, 120, 0.12)',
                borderWidth: 1,
                borderColor: 'rgba(80, 200, 120, 0.45)',
              }}
            >
              <Text style={{ fontSize: FontSize.sm, color: '#50c878' }}>
                {legacySaving ? '传承中...' : '🌱 传承给同路人'}
              </Text>
            </TouchableOpacity>
          )}
          {wish.fruitType === 'SPARK' && (
            <View
              style={{
                flex: 2,
                paddingVertical: Spacing.md,
                borderRadius: 28,
                alignItems: 'center',
                backgroundColor: 'rgba(255,215,0,0.12)',
                borderWidth: 1,
                borderColor: 'rgba(255,215,0,0.45)',
              }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#ffd700' }}>⭐ 星火永久</Text>
            </View>
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
          <TouchableOpacity
            activeOpacity={0.85}
            accessibilityLabel="分享心愿"
            onPress={() => setShareOpen(true)}
            style={{
              paddingVertical: Spacing.md,
              paddingHorizontal: Spacing.lg,
              borderRadius: 28,
              alignItems: 'center',
              backgroundColor: 'rgba(255,255,255,0.08)',
              borderWidth: 1,
              borderColor: 'rgba(255,255,255,0.25)',
            }}
          >
            <Text style={{ fontSize: FontSize.md, color: WishColors.text }}>✨</Text>
          </TouchableOpacity>
        </View>
      )}

      {growthOpen && (
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
              记录成长
            </Text>
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginBottom: Spacing.md }}>
              记录这一步的成长与心得，可同时推进心愿进度
            </Text>
            <View style={{ flexDirection: 'row', gap: Spacing.sm, marginBottom: Spacing.md }}>
              {(['TEXT', 'DIARY', 'IMAGE'] as const).map((t) => (
                <TouchableOpacity
                  key={t}
                  activeOpacity={0.85}
                  onPress={() => setGrowthType(t)}
                  style={{
                    flex: 1,
                    paddingVertical: Spacing.sm,
                    borderRadius: BorderRadius.md,
                    alignItems: 'center',
                    borderWidth: 1,
                    borderColor: growthType === t ? WishColors.accentCyan : WishColors.border,
                    backgroundColor: growthType === t ? 'rgba(0, 212, 255, 0.12)' : 'transparent',
                  }}
                >
                  <Text style={{ fontSize: FontSize.sm, color: growthType === t ? WishColors.accentCyan : WishColors.textSecondary }}>
                    {t === 'TEXT' ? '文字记录' : t === 'DIARY' ? '心情日记' : '图片记录'}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
            <TextInput
              value={growthContent}
              onChangeText={setGrowthContent}
              maxLength={500}
              placeholder={growthType === 'IMAGE' ? '给这组图片写点文字（可留空）' : '如：今天完成了第一阶段的目标'}
              placeholderTextColor={WishColors.textSecondary}
              multiline
              style={{
                minHeight: 80,
                borderWidth: 1,
                borderColor: WishColors.border,
                borderRadius: BorderRadius.md,
                padding: Spacing.md,
                marginBottom: Spacing.sm,
                fontSize: FontSize.sm,
                color: WishColors.text,
                textAlignVertical: 'top',
              }}
            />
            {growthType === 'IMAGE' && (
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, marginBottom: Spacing.sm }}>
                {growthUploads.map((u) => (
                  <View key={u.key} style={{ width: 64, height: 64, borderRadius: 8, overflow: 'hidden', backgroundColor: 'rgba(255,255,255,0.06)', alignItems: 'center', justifyContent: 'center' }}>
                    {u.status === 'success' && u.url ? (
                      <Image source={{ uri: u.url }} style={{ width: 64, height: 64 }} />
                    ) : (
                      <Text style={{ fontSize: FontSize.xs, color: u.status === 'error' ? '#ff6b6b' : WishColors.textSecondary }}>
                        {u.status === 'error' ? '失败' : '上传中'}
                      </Text>
                    )}
                  </View>
                ))}
                {growthUploads.length < 9 && (
                  <TouchableOpacity
                    activeOpacity={0.85}
                    disabled={growthPicking}
                    onPress={pickGrowthImages}
                    style={{ width: 64, height: 64, borderRadius: 8, borderWidth: 1, borderColor: WishColors.border, alignItems: 'center', justifyContent: 'center' }}
                  >
                    <Text style={{ fontSize: FontSize.xl, color: WishColors.textSecondary }}>＋</Text>
                  </TouchableOpacity>
                )}
              </View>
            )}
            <TextInput
              value={growthDelta}
              onChangeText={(v) => setGrowthDelta(v.replace(/[^0-9]/g, ''))}
              maxLength={3}
              keyboardType="number-pad"
              placeholder="进度推进百分比（可选，0-100）"
              placeholderTextColor={WishColors.textSecondary}
              style={{
                borderWidth: 1,
                borderColor: WishColors.border,
                borderRadius: BorderRadius.md,
                padding: Spacing.md,
                marginBottom: Spacing.md,
                fontSize: FontSize.sm,
                color: WishColors.text,
              }}
            />
            <View style={{ flexDirection: 'row', gap: Spacing.md }}>
              <TouchableOpacity
                activeOpacity={0.85}
                onPress={() => setGrowthOpen(false)}
                style={{
                  flex: 1,
                  paddingVertical: Spacing.sm + 2,
                  borderRadius: BorderRadius.lg,
                  alignItems: 'center',
                  backgroundColor: 'rgba(255,255,255,0.08)',
                }}
              >
                <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity
                activeOpacity={0.85}
                disabled={growthSaving}
                onPress={handleGrowthSubmit}
                style={{
                  flex: 1,
                  paddingVertical: Spacing.sm + 2,
                  borderRadius: BorderRadius.lg,
                  alignItems: 'center',
                  backgroundColor: WishColors.accentCyan,
                  opacity: growthSaving ? 0.6 : 1,
                }}
              >
                <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: '#0b1026' }}>
                  {growthSaving ? '保存中...' : '保存'}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      )}

      {checkinOpen && (
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
              每日打卡
            </Text>
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginBottom: Spacing.md }}>
              为今天的心愿之旅留点痕迹吧（心得可留空），打卡可获得星光 +2 ✨
            </Text>
            <TextInput
              value={checkinContent}
              onChangeText={setCheckinContent}
              maxLength={200}
              placeholder="如：今天离目标又近了一步"
              placeholderTextColor={WishColors.textSecondary}
              multiline
              style={{
                minHeight: 80,
                borderWidth: 1,
                borderColor: WishColors.border,
                borderRadius: BorderRadius.md,
                padding: Spacing.md,
                marginBottom: Spacing.md,
                fontSize: FontSize.sm,
                color: WishColors.text,
                textAlignVertical: 'top',
              }}
            />
            <View style={{ flexDirection: 'row', gap: Spacing.md }}>
              <TouchableOpacity
                activeOpacity={0.85}
                onPress={() => setCheckinOpen(false)}
                style={{
                  flex: 1,
                  paddingVertical: Spacing.sm + 2,
                  borderRadius: BorderRadius.lg,
                  alignItems: 'center',
                  backgroundColor: 'rgba(255,255,255,0.08)',
                }}
              >
                <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity
                activeOpacity={0.85}
                disabled={checkinSaving}
                onPress={handleCheckinSubmit}
                style={{
                  flex: 1,
                  paddingVertical: Spacing.sm + 2,
                  borderRadius: BorderRadius.lg,
                  alignItems: 'center',
                  backgroundColor: WishColors.primary,
                  opacity: checkinSaving ? 0.6 : 1,
                }}
              >
                <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#fff' }}>
                  {checkinSaving ? '打卡中...' : '打卡'}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
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

      {wish && (
        <WishShareCard
          visible={shareOpen}
          onClose={() => setShareOpen(false)}
          title={wish.title}
          author={wish.authorNickname}
          dateText={new Date(wish.createdAt).toLocaleDateString('zh-CN')}
          fruitLabel={FRUIT_LABELS[wish.fruitType]}
          fruitColor={FRUIT_COLORS[wish.fruitType]}
        />
      )}
      <WishBGM />
    </View>
  )
}
