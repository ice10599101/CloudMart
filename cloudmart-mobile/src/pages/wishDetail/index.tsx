import { useState, useEffect, useRef } from 'react'
import { Picker, View, Text, ScrollView, Image, Swiper, SwiperItem, Textarea, Input } from '@tarojs/components'
import Taro, { useRouter, useShareAppMessage } from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import WishCheckinCalendar from '@/components/WishCheckinCalendar'
import WishInteractionBar, { type WishInteractionCounts } from '@/components/WishInteractionBar'
import WishCommentSection, { type WishCommentSectionHandle } from '@/components/WishCommentSection'
import WishShareCard from '@/components/WishShareCard'
import type { WishDetail, FruitType, WishFulfillmentDetail } from '@/types'
import styles from './index.module.scss'

const FRUIT_LABELS: Record<FruitType, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

const FRUIT_COLORS: Record<FruitType, string> = {
  GLOW: '#00d4ff',
  RESONANCE: '#9370db',
  BLOOM: '#ff6b6b',
  SPARK: '#ffd700',
}

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审核',
  ACTIVE: '进行中',
  OVERDUE: '已过期',
  FULFILLING: '还愿中',
  FULFILLED: '已还愿',
  ARCHIVED: '已归档',
  REJECTED: '已拒绝',
}

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

export default function WishDetailPage() {
  const router = useRouter()
  const wishId = String(router.params.id ?? '')
  // 预期管理通知「延长预期」深链：作者本人修改 expected_at（状态保持 ACTIVE）
  const [extendOpen, setExtendOpen] = useState(false)
  const [extendDate, setExtendDate] = useState('')
  const [extendSaving, setExtendSaving] = useState(false)
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { user, isLoggedIn } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetail | null>(null)
  const [fulfillment, setFulfillment] = useState<WishFulfillmentDetail | null>(null)
  const commentRef = useRef<WishCommentSectionHandle>(null)
  // 每日打卡（仅作者 + ACTIVE；成功后本地记录今日已打卡，409 由后端幂等兜底）
  const [checkinOpen, setCheckinOpen] = useState(false)
  const [checkinContent, setCheckinContent] = useState('')
  // 分享卡片（作者/非作者均可生成星空卡片）
  const [shareOpen, setShareOpen] = useState(false)
  const [checkinSaving, setCheckinSaving] = useState(false)
  const [checkedInToday, setCheckedInToday] = useState(false)
  // 收藏（B2，非作者）+ 成长记录（B1，仅作者）
  const [collected, setCollected] = useState(false)
  const [collectSaving, setCollectSaving] = useState(false)
  const [growthOpen, setGrowthOpen] = useState(false)
  const [growthType, setGrowthType] = useState<'TEXT' | 'IMAGE' | 'VIDEO' | 'DIARY'>('TEXT')
  const [growthContent, setGrowthContent] = useState('')
  const [growthDelta, setGrowthDelta] = useState('')  // Taro Input 值为字符串
  const [growthSaving, setGrowthSaving] = useState(false)

  /** 收藏状态回显（非作者；登录态） */
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
        if (res.data.success) { setCollected(false); Taro.showToast({ title: '已取消收藏', icon: 'none' }) }
      } else {
        const res = await wishApi.collectWish(wishId)
        if (res.data.success) { setCollected(true); Taro.showToast({ title: '已收藏', icon: 'none' }) }
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      Taro.showToast({ title: errNode?.data?.error?.message || '操作失败，请稍后重试', icon: 'none' })
    } finally {
      setCollectSaving(false)
    }
  }

  /** 提交成长记录：成功后刷新详情（时间线 + 进度） */
  const handleGrowthSubmit = async () => {
    if (!growthContent.trim()) {
      Taro.showToast({ title: '请填写成长记录内容', icon: 'none' })
      return
    }
    setGrowthSaving(true)
    try {
      const res = await wishApi.addGrowthRecord(wishId, {
        type: growthType,
        content: growthContent.trim(),
        progressDelta: growthDelta ? Number(growthDelta) : undefined,
      })
      if (res.data.success) {
        Taro.showToast({ title: '成长记录已添加', icon: 'none' })
        setGrowthOpen(false)
        setGrowthContent('')
        setGrowthDelta('')
        const detailRes = await wishApi.getWishDetail(wishId)
        if (detailRes.data.success) setWish(detailRes.data.data)
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      Taro.showToast({ title: errNode?.data?.error?.message || '保存失败，请稍后重试', icon: 'none' })
    } finally {
      setGrowthSaving(false)
    }
  }

  /** 页面 ScrollView 触底 → 加载更多评论（组件内含 hasMore/loadingMore 防抖） */
  const onScrollToLower = () => {
    commentRef.current?.loadMore()
  }

  /** 互动成功后同步心愿计数（服务端返回的最新值） */
  const handleCountsChange = (partial: Partial<WishInteractionCounts>) => {
    setWish((prev) => (prev ? { ...prev, ...partial } : prev))
  }

  /** 评论数变化（发表 +1 / 删除 -1） */
  const handleCommentCountChange = (delta: number) => {
    setWish((prev) =>
      prev ? { ...prev, commentCount: Math.max(0, prev.commentCount + delta) } : prev,
    )
  }

  /** 未登录引导（组件在调用前已确认未登录态） */
  const gotoLogin = () => {
    Taro.navigateTo({ url: '/pages/login/index' })
  }

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await wishApi.getWishDetail(wishId)
        if (res.data.success) {
          setWish(res.data.data)
          // 已还愿心愿加载还愿故事（公开匿名可见；PRIVATE/TREE_HOLE 仅作者）
          if (res.data.data.status === 'FULFILLED') {
            try {
              const fulfillmentRes = await wishApi.getFulfillmentDetail(wishId)
              if (fulfillmentRes.data.success) {
                setFulfillment(fulfillmentRes.data.data)
              }
            } catch {
              // 未还愿/已撤回/无权限时静默不展示
            }
          }
        }
      } catch {
        // 错误已由 request 处理
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [wishId])

  // 预期管理通知「延长预期」深链：作者本人且心愿未完结时打开延期选择
  useEffect(() => {
    if (router.params.extend === '1' && wish && user?.id === wish.authorId
        && (wish.status === 'ACTIVE' || wish.status === 'OVERDUE')) {
      const target = new Date(Date.now() + 30 * 24 * 3600 * 1000)
      const m = String(target.getMonth() + 1).padStart(2, '0')
      const d = String(target.getDate()).padStart(2, '0')
      setExtendDate(`${target.getFullYear()}-${m}-${d}`)
      setExtendOpen(true)
    }
  }, [router.params.extend, wish, user])

  /** 打开延长预期（底部按钮入口） */
  const openExtend = () => {
    const target = new Date(Date.now() + 30 * 24 * 3600 * 1000)
    const m = String(target.getMonth() + 1).padStart(2, '0')
    const d = String(target.getDate()).padStart(2, '0')
    setExtendDate(`${target.getFullYear()}-${m}-${d}`)
    setExtendOpen(true)
  }

  /** 保存新预期时间（updateWish 仅改 expected_at，状态保持不变） */
  const handleExtendSave = async () => {
    if (!extendDate) return
    if (new Date(`${extendDate}T23:59:59`).getTime() <= Date.now()) {
      Taro.showToast({ title: '新的预期时间需要晚于现在', icon: 'none' })
      return
    }
    setExtendSaving(true)
    try {
      const res = await wishApi.updateWish(wishId, {
        expectedAt: new Date(`${extendDate}T12:00:00`).toISOString(),
      })
      if (res.data.success) {
        Taro.showToast({ title: '预期已延长，继续加油', icon: 'none' })
        setExtendOpen(false)
        setWish((prev) => (prev ? { ...prev, expectedAt: new Date(`${extendDate}T12:00:00`).toISOString() } : prev))
      }
    } catch {
      Taro.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
    } finally {
      setExtendSaving(false)
    }
  }

  /** 提交每日打卡：成功后刷新详情（打卡天数/连续打卡来自服务端聚合） */
  const handleCheckinSubmit = async () => {
    setCheckinSaving(true)
    try {
      const res = await wishApi.checkinWish(wishId, checkinContent.trim() || undefined)
      if (res.data.success) {
        const { currentStreak, starlightCredited } = res.data.data
        Taro.showToast({ title: `连续 ${currentStreak} 天，星光 +${starlightCredited}`, icon: 'none' })
        setCheckinOpen(false)
        setCheckinContent('')
        setCheckedInToday(true)
        const detailRes = await wishApi.getWishDetail(wishId)
        if (detailRes.data.success) {
          setWish(detailRes.data.data)
        }
      }
    } catch (err) {
      // Taro 非 2xx 异常体：{ data: { error: { code } } }
      const errNode = err as { data?: { error?: { code?: string } } }
      const code = errNode?.data?.error?.code
      if (code === 'WISH_ALREADY_CHECKIN_TODAY') {
        Taro.showToast({ title: '今天已经打过卡啦', icon: 'none' })
        setCheckedInToday(true)
        setCheckinOpen(false)
      } else if (code === 'WISH_STATUS_CONFLICT') {
        Taro.showToast({ title: '仅进行中的心愿可打卡', icon: 'none' })
      } else {
        Taro.showToast({ title: '打卡失败，请稍后重试', icon: 'none' })
      }
    } finally {
      setCheckinSaving(false)
    }
  }

  const handleDelete = async () => {
    const res = await Taro.showModal({
      title: '确认删除',
      content: '删除后不可恢复，确定删除吗？',
    })
    if (!res.confirm) return

    try {
      const result = await wishApi.deleteWish(wishId)
      if (result.data.success) {
        Taro.showToast({ title: '已删除', icon: 'success' })
        setTimeout(() => Taro.navigateBack(), 1500)
      }
    } catch {
      // 错误已由 request 处理
    }
  }

  /** 设为星火永久收藏（文档 2.3：仅作者对 FULFILLED+BLOOM 心愿，幂等，二次确认） */
  const [sparkSaving, setSparkSaving] = useState(false)
  const handleSpark = async () => {
    const res = await Taro.showModal({
      title: '设为星火永久收藏',
      content: '心愿将以星火形态在世界生命树永久展示，可被他人收藏到收藏馆。确定吗？',
    })
    if (!res.confirm) return
    setSparkSaving(true)
    try {
      const result = await wishApi.sparkWish(wishId)
      if (result.data.success) {
        Taro.showToast({ title: '已设为星火永久收藏', icon: 'none' })
        setWish((prev) => (prev ? { ...prev, fruitType: 'SPARK' } : prev))
      }
    } catch (err) {
      const errNode = err as { data?: { error?: { message?: string } } }
      Taro.showToast({ title: errNode?.data?.error?.message || '设置失败，请稍后重试', icon: 'none' })
    } finally {
      setSparkSaving(false)
    }
  }

  if (loading) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
        <CustomNavBar title='心愿详情' back />
        <View className={styles.loading}>
          <View className={styles.spinner} />
        </View>
      </View>
    )
  }

  if (!wish) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
        <CustomNavBar title='心愿详情' back />
        <View className={styles.empty}>
          <Text className={styles.emptyIcon}>🌌</Text>
          <Text className={styles.emptyText}>心愿不存在或已被删除</Text>
        </View>
      </View>
    )
  }

  const isAuthor = user?.id === wish.authorId

  // 小程序原生分享（Sprint 1.5 体验要求：wx.share AppMessage）
  useShareAppMessage(() => ({
    title: wish ? `✨ 「${wish.title}」` : '✨ 心愿宇宙',
    path: `/pages/wishDetail/index?id=${wishId}`,
  }))

  return (
    <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
      <CustomNavBar title='心愿详情' back />
      <ScrollView
        scrollY
        className={styles.scroll}
        onScrollToLower={onScrollToLower}
        lowerThreshold={100}
      >
        {/* 媒体轮播 */}
        {wish.mediaUrls && wish.mediaUrls.length > 0 && (
          <Swiper
            className={styles.mediaSwiper}
            indicatorDots
            indicatorColor='rgba(255,255,255,0.3)'
            indicatorActiveColor='#e94560'
            autoplay
            circular
          >
            {wish.mediaUrls.map(url => (
              <SwiperItem key={url}>
                <Image className={styles.mediaImage} src={url} mode='aspectFill' />
              </SwiperItem>
            ))}
          </Swiper>
        )}

        {/* 心愿信息 */}
        <View className={styles.infoCard}>
          <View className={styles.tagsRow}>
            <Text className={styles.fruitTag} style={{ background: FRUIT_COLORS[wish.fruitType] }}>
              {FRUIT_LABELS[wish.fruitType]}
            </Text>
            <Text className={styles.statusTag}>{STATUS_LABELS[wish.status] || wish.status}</Text>
            {wish.tags?.map(tag => (
              <Text key={tag} className={styles.tag}>{tag}</Text>
            ))}
          </View>

          <Text className={styles.title}>{wish.title}</Text>

          <View className={styles.authorRow}>
            {wish.authorAvatar ? (
              <Image className={styles.avatar} src={wish.authorAvatar} mode='aspectFill' />
            ) : (
              <View className={styles.avatarPlaceholder}>
                <Text style={{ fontSize: '24rpx', color: FRUIT_COLORS[wish.fruitType] }}>★</Text>
              </View>
            )}
            <Text className={styles.authorName}>{wish.authorNickname}</Text>
            <Text className={styles.date}>{new Date(wish.createdAt).toLocaleString('zh-CN')}</Text>
          </View>

          <Text className={styles.description}>{wish.description}</Text>

          {wish.expectedAt && (
            <View className={styles.expectedRow}>
              <Text className={styles.expectedLabel}>📅 预计完成：</Text>
              <Text className={styles.expectedValue}>
                {new Date(wish.expectedAt).toLocaleDateString('zh-CN')}
              </Text>
            </View>
          )}

          {/* 互动统计 */}
          <View className={styles.statsRow}>
            <View className={styles.statItem}>
              <Text className={styles.statIcon}>♥</Text>
              <Text className={styles.statText}>{formatCount(wish.supportCount)} 互动</Text>
            </View>
            <View className={styles.statItem}>
              <Text className={styles.statIcon}>💬</Text>
              <Text className={styles.statText}>{formatCount(wish.commentCount)} 评论</Text>
            </View>
          </View>
        </View>

        {/* 还愿故事（Sprint 1.10：已还愿心愿展示，公开心愿匿名可见） */}
        {fulfillment && (
          <View className={styles.fulfillmentCard}>
            <Text className={styles.cardTitle}>🌸 还愿故事</Text>
            <View className={styles.fulfillmentAuthorRow}>
              {fulfillment.authorAvatar ? (
                <Image className={styles.avatar} src={fulfillment.authorAvatar} mode='aspectFill' />
              ) : (
                <View className={styles.avatarPlaceholder}>
                  <Text style={{ fontSize: '24rpx', color: '#ff6b6b' }}>★</Text>
                </View>
              )}
              <Text className={styles.authorName}>{fulfillment.authorNickname}</Text>
              <Text className={styles.fulfillmentDate}>
                {new Date(fulfillment.createdAt).toLocaleString('zh-CN')}
              </Text>
            </View>
            <Text className={styles.fulfillmentStory}>{fulfillment.story}</Text>
            {fulfillment.mediaUrls && fulfillment.mediaUrls.length > 0 && (
              <View className={styles.fulfillmentMedia}>
                {fulfillment.mediaUrls.map(url => (
                  <Image key={url} className={styles.fulfillmentImage} src={url} mode='aspectFill' />
                ))}
              </View>
            )}
            {fulfillment.feeling && (
              <View className={styles.fulfillmentFeeling}>
                <Text className={styles.fulfillmentFeelingLabel}>💬 感悟</Text>
                <Text className={styles.fulfillmentFeelingText}>{fulfillment.feeling}</Text>
              </View>
            )}
          </View>
        )}

        {/* 树洞入口（Sprint 1.3：作者本人 + 树洞心愿 + 已启用 AI 回复） */}
        {isAuthor && wish.visibility === 'TREE_HOLE' && wish.enableAiReply && (
          <View
            className={styles.treeHoleEntry}
            onClick={() => Taro.navigateTo({ url: `/pages/treeHole/index?id=${wishId}` })}
          >
            <Text className={styles.treeHoleEntryIcon}>🌙</Text>
            <Text className={styles.treeHoleEntryText}>进入树洞 · 让守护者陪你聊聊</Text>
            <Text className={styles.treeHoleEntryArrow}>›</Text>
          </View>
        )}

        {/* 互动按钮组（点亮/同求/祝福/匿名星光，Sprint 1.2 + 2.6） */}
        <View className={styles.interactionCard}>
          <WishInteractionBar
            wishId={wishId}
            counts={{
              lightCount: wish.lightCount,
              sameWishCount: wish.sameWishCount,
              blessCount: wish.blessCount,
              anonStarCount: wish.anonStarCount,
            }}
            isLoggedIn={isLoggedIn}
            onCountsChange={handleCountsChange}
            onRequireLogin={gotoLogin}
          />
        </View>

        {/* 进度 */}
        {wish.progress && (
          <View className={styles.progressCard}>
            <Text className={styles.cardTitle}>心愿进度</Text>
            <View className={styles.progressContent}>
              <View className={styles.progressBg}>
                <View
                  className={styles.progressBar}
                  style={{
                    width: `${wish.progress.percentage}%`,
                    background: FRUIT_COLORS[wish.fruitType],
                  }}
                />
              </View>
              <View className={styles.progressDetail}>
                <Text className={styles.progressInfo}>
                  {wish.progress.currentValue} / {wish.progress.targetValue}
                </Text>
                <Text className={styles.progressDays}>打卡 {wish.checkinDays} 天</Text>
              </View>
            </View>
            {isAuthor && (
              <WishCheckinCalendar wishId={wishId} accentColor={FRUIT_COLORS[wish.fruitType]} />
            )}
          </View>
        )}

        {/* 成长记录 */}
        {wish.growthRecords && wish.growthRecords.length > 0 && (
          <View className={styles.growthCard}>
            <Text className={styles.cardTitle}>成长记录</Text>
            {wish.growthRecords.map(record => (
              <View key={record.id} className={styles.growthItem}>
                <View className={styles.growthDot} style={{ background: FRUIT_COLORS[wish.fruitType] }} />
                <View className={styles.growthContent}>
                  <Text className={styles.growthText}>{record.content}</Text>
                  {record.mediaUrls && record.mediaUrls.length > 0 && (
                    <View className={styles.growthMedia}>
                      {record.mediaUrls.map(url => (
                        <Image key={url} className={styles.growthImage} src={url} mode='aspectFill' />
                      ))}
                    </View>
                  )}
                  <View className={styles.growthMeta}>
                    <Text className={styles.growthDate}>
                      {new Date(record.createdAt).toLocaleString('zh-CN')}
                    </Text>
                    {record.progressDelta > 0 && (
                      <Text className={styles.deltaTag}>+{record.progressDelta}</Text>
                    )}
                  </View>
                </View>
              </View>
            ))}
          </View>
        )}

        {/* 评论模块（Sprint 1.2） */}
        <View className={styles.commentCard}>
          <WishCommentSection
            ref={commentRef}
            wishId={wishId}
            commentCount={wish.commentCount}
            isLoggedIn={isLoggedIn}
            currentUserId={user?.id}
            onCountChange={handleCommentCountChange}
            onRequireLogin={gotoLogin}
          />
        </View>

        <View style={{ height: '160rpx' }} />
      </ScrollView>

      {/* 底部操作栏 */}
      {!isAuthor && (
        <View className={styles.bottomBar}>
          <View className={styles.deleteBtn} onClick={collectSaving ? undefined : handleCollectToggle}>
            <Text className={styles.deleteBtnText}>{collected ? '⭐ 已收藏' : '☆ 收藏心愿'}</Text>
          </View>
          <View className={styles.deleteBtn} onClick={() => setShareOpen(true)}>
            <Text className={styles.deleteBtnText}>✨ 分享</Text>
          </View>
        </View>
      )}
      {isAuthor && (
        <View className={styles.bottomBar}>
          {(wish.status === 'ACTIVE' || wish.status === 'OVERDUE') && (
            <View className={styles.deleteBtn} onClick={() => setGrowthOpen(true)}>
              <Text className={styles.deleteBtnText}>📝 记录成长</Text>
            </View>
          )}
          {wish.status === 'ACTIVE' && (
            <View
              className={styles.deleteBtn}
              onClick={() => {
                if (checkedInToday) {
                  Taro.showToast({ title: '今天已经打过卡啦', icon: 'none' })
                  return
                }
                setCheckinOpen(true)
              }}
            >
              <Text className={styles.deleteBtnText}>{checkedInToday ? '✅ 今日已打卡' : '📅 每日打卡'}</Text>
            </View>
          )}
          {(wish.status === 'ACTIVE' || wish.status === 'OVERDUE') && (
            <View className={styles.deleteBtn} onClick={openExtend}>
              <Text className={styles.deleteBtnText}>延长预期</Text>
            </View>
          )}
          {(wish.status === 'ACTIVE' || wish.status === 'OVERDUE') && (
            <View
              className={styles.fulfillBtn}
              onClick={() => Taro.navigateTo({ url: `/pages/wishFulfillment/index?id=${wishId}` })}
            >
              <Text className={styles.fulfillBtnText}>🌸 我要还愿</Text>
            </View>
          )}
          {/* 星火永久收藏（文档 2.3：FULFILLED+BLOOM 可设置；SPARK 展示已收藏态） */}
          {wish.status === 'FULFILLED' && wish.fruitType === 'BLOOM' && (
            <View className={styles.sparkBtn} onClick={sparkSaving ? undefined : handleSpark}>
              <Text className={styles.sparkBtnText}>{sparkSaving ? '设置中...' : '⭐ 设为星火'}</Text>
            </View>
          )}
          {wish.fruitType === 'SPARK' && (
            <View className={styles.sparkDoneBtn}>
              <Text className={styles.sparkDoneBtnText}>⭐ 星火永久</Text>
            </View>
          )}
          <View className={styles.deleteBtn} onClick={handleDelete}>
            <Text className={styles.deleteBtnText}>删除心愿</Text>
          </View>
          <View className={styles.deleteBtn} onClick={() => setShareOpen(true)}>
            <Text className={styles.deleteBtnText}>✨ 分享</Text>
          </View>
        </View>
      )}
      {checkinOpen && (
        <View className={styles.modalMask} onClick={() => setCheckinOpen(false)}>
          <View className={styles.modalBody} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>每日打卡</Text>
            <Text className={styles.modalText}>为今天的心愿之旅留点痕迹吧（心得可留空），打卡可获得星光 +2 ✨</Text>
            <Textarea
              className={styles.checkinTextarea}
              value={checkinContent}
              maxlength={200}
              placeholder='如：今天离目标又近了一步'
              placeholderClass='checkinPlaceholder'
              onInput={(e) => setCheckinContent(e.detail.value)}
            />
            <View className={styles.modalBtns}>
              <View className={styles.modalCancel} onClick={() => setCheckinOpen(false)}>
                <Text>取消</Text>
              </View>
              <View className={styles.modalOk} onClick={checkinSaving ? undefined : handleCheckinSubmit}>
                <Text>{checkinSaving ? '打卡中...' : '打卡'}</Text>
              </View>
            </View>
          </View>
        </View>
      )}
      {growthOpen && (
        <View className={styles.modalMask} onClick={() => setGrowthOpen(false)}>
          <View className={styles.modalBody} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>记录成长</Text>
            <Text className={styles.modalText}>记录这一步的成长与心得，可同时推进心愿进度</Text>
            <View className={styles.growthTypeRow}>
              {(['TEXT', 'DIARY'] as const).map((t) => (
                <View
                  key={t}
                  className={`${styles.growthTypeBtn} ${growthType === t ? styles.growthTypeBtnActive : ''}`}
                  onClick={() => setGrowthType(t)}
                >
                  <Text>{t === 'TEXT' ? '文字记录' : '心情日记'}</Text>
                </View>
              ))}
            </View>
            <Textarea
              className={styles.checkinTextarea}
              value={growthContent}
              maxlength={500}
              placeholder='如：今天完成了第一阶段的目标'
              placeholderClass='checkinPlaceholder'
              onInput={(e) => setGrowthContent(e.detail.value)}
            />
            <Input
              className={styles.checkinTextarea}
              type='number'
              value={growthDelta}
              maxlength={3}
              placeholder='进度推进百分比（可选，0-100）'
              onInput={(e) => setGrowthDelta(e.detail.value)}
            />
            <View className={styles.modalBtns}>
              <View className={styles.modalCancel} onClick={() => setGrowthOpen(false)}>
                <Text>取消</Text>
              </View>
              <View className={styles.modalOk} onClick={growthSaving ? undefined : handleGrowthSubmit}>
                <Text>{growthSaving ? '保存中...' : '保存'}</Text>
              </View>
            </View>
          </View>
        </View>
      )}
      {extendOpen && (
        <View className={styles.modalMask} onClick={() => setExtendOpen(false)}>
          <View className={styles.modalBody} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>延长预期</Text>
            <Text className={styles.modalText}>为这个心愿设定一个新的预期完成时间（状态保持进行中）</Text>
            <Picker mode='date' value={extendDate} onChange={(e) => setExtendDate(e.detail.value)}>
              <View className={styles.datePick}>
                <Text className={styles.datePickText}>{extendDate || '选择日期'}</Text>
              </View>
            </Picker>
            <View className={styles.modalBtns}>
              <View className={styles.modalCancel} onClick={() => setExtendOpen(false)}>
                <Text>取消</Text>
              </View>
              <View className={styles.modalOk} onClick={handleExtendSave}>
                <Text>{extendSaving ? '保存中...' : '保存'}</Text>
              </View>
            </View>
          </View>
        </View>
      )}
      <WishShareCard
        visible={shareOpen}
        onClose={() => setShareOpen(false)}
        title={wish.title}
        author={wish.authorNickname}
        dateText={new Date(wish.createdAt).toLocaleDateString('zh-CN')}
        fruitLabel={FRUIT_LABELS[wish.fruitType]}
        fruitColor={FRUIT_COLORS[wish.fruitType]}
      />
      <WishBGM />
    </View>
  )
}
