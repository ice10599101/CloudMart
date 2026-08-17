import { useState, useEffect } from 'react'
import { View, Text, ScrollView, Image, Swiper, SwiperItem } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import type { WishDetail, FruitType } from '@/types'
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
  const { user } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetail | null>(null)

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await wishApi.getWishDetail(wishId)
        if (res.data.success) {
          setWish(res.data.data)
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
        <CustomNavBar title='心愿详情' showBack />
        <View className={styles.loading}>
          <View className={styles.spinner} />
        </View>
      </View>
    )
  }

  if (!wish) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
        <CustomNavBar title='心愿详情' showBack />
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
      <CustomNavBar title='心愿详情' showBack />
      <ScrollView scrollY className={styles.scroll}>
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
            {wish.growthRecords.map((record, index) => (
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

        <View style={{ height: '160rpx' }} />
      </ScrollView>

      {/* 底部操作栏 */}
      {isAuthor && (
        <View className={styles.bottomBar}>
          <View className={styles.deleteBtn} onClick={handleDelete}>
            <Text className={styles.deleteBtnText}>删除心愿</Text>
          </View>
        </View>
      )}
      <WishBGM />
    </View>
  )
}
