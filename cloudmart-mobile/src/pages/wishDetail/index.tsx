import { useState, useEffect, useRef } from 'react'
import { View, Text, ScrollView, Image, Swiper, SwiperItem } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import WishInteractionBar, { type WishInteractionCounts } from '@/components/WishInteractionBar'
import WishCommentSection, { type WishCommentSectionHandle } from '@/components/WishCommentSection'
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
  const wishId = Number(router.params.id)
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { user, isLoggedIn } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetail | null>(null)
  const [fulfillment, setFulfillment] = useState<WishFulfillmentDetail | null>(null)
  const commentRef = useRef<WishCommentSectionHandle>(null)

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

  if (loading) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
        <CustomNavBar title='心愿详情' back />
        <View className={styles.loading}>
          <View className={styles.spinner} />
        </View>
      </View>
    )
  }

  if (!wish) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
        <CustomNavBar title='心愿详情' back />
        <View className={styles.empty}>
          <Text className={styles.emptyIcon}>🌌</Text>
          <Text className={styles.emptyText}>心愿不存在或已被删除</Text>
        </View>
      </View>
    )
  }

  const isAuthor = user?.id === wish.authorId

  return (
    <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
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
      {isAuthor && (
        <View className={styles.bottomBar}>
          {(wish.status === 'ACTIVE' || wish.status === 'OVERDUE') && (
            <View
              className={styles.fulfillBtn}
              onClick={() => Taro.navigateTo({ url: `/pages/wishFulfillment/index?id=${wishId}` })}
            >
              <Text className={styles.fulfillBtnText}>🌸 我要还愿</Text>
            </View>
          )}
          <View className={styles.deleteBtn} onClick={handleDelete}>
            <Text className={styles.deleteBtnText}>删除心愿</Text>
          </View>
        </View>
      )}
      <WishBGM />
    </View>
  )
}
