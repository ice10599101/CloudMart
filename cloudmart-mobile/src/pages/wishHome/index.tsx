import { useState, useEffect } from 'react'
import { View, Text, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import type { HomeAggregation, FruitType } from '@/types'
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

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

export default function WishHomePage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [data, setData] = useState<HomeAggregation | null>(null)

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await wishApi.getHome()
        if (res.data.success) {
          setData(res.data.data)
        }
      } catch {
        // 错误已由 request 处理
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [])

  const navigateTo = (url: string) => Taro.navigateTo({ url })

  const goCreate = () => {
    if (!isLoggedIn) {
      Taro.navigateTo({ url: '/pages/login/index' })
      return
    }
    navigateTo('/pages/wishCreate/index')
  }

  if (loading) {
    return (
        <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
          <CustomNavBar title='心愿宇宙' />
          <View className={styles.loading}>
            <View className={styles.spinner} />
          </View>
        </View>
    )
  }

  return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
        <CustomNavBar title='心愿宇宙' />
        <ScrollView scrollY className={styles.scroll}>
          {/* Banner */}
          <View className={styles.banner}>
            <View className={styles.bannerContent}>
              <Text className={styles.bannerTitle}>心愿宇宙</Text>
              <Text className={styles.bannerSubtitle}>每一个心愿，都是一颗种子</Text>
              <View className={styles.createBtn} onClick={goCreate}>
                <Text className={styles.createBtnText}>许下心愿</Text>
              </View>
            </View>
            <View className={styles.starField}>
              {Array.from({ length: 20 }).map((_, i) => (
                  <View
                      key={i}
                      className={styles.star}
                      style={{
                        left: `${Math.random() * 100}%`,
                        top: `${Math.random() * 100}%`,
                        animationDelay: `${Math.random() * 3}s`,
                        opacity: Math.random() * 0.8 + 0.2,
                      }}
                  />
              ))}
            </View>
          </View>

          {/* 入口导航 */}
          <View className={styles.entryNav}>
            <View className={styles.entryCard} onClick={() => navigateTo('/pages/wishSquare/index')}>
              <Text className={styles.entryIcon} style={{ color: '#00d4ff' }}>★</Text>
              <Text className={styles.entryText}>心愿广场</Text>
            </View>
            <View className={styles.entryCard} onClick={() => navigateTo('/pages/worldTree/index')}>
              <Text className={styles.entryIcon} style={{ color: '#3ddc97' }}>🌳</Text>
              <Text className={styles.entryText}>世界树</Text>
            </View>
            <View className={styles.entryCard} onClick={() => navigateTo('/pages/capsuleList/index')}>
              <Text className={styles.entryIcon} style={{ color: '#4ecdc4' }}>🔒</Text>
              <Text className={styles.entryText}>时间胶囊</Text>
            </View>
            <View className={styles.entryCard} onClick={() => navigateTo('/pages/myWishes/index')}>
              <Text className={styles.entryIcon} style={{ color: '#ff6b6b' }}>♥</Text>
              <Text className={styles.entryText}>我的心愿</Text>
            </View>
            <View className={styles.entryCard} onClick={goCreate}>
              <Text className={styles.entryIcon} style={{ color: '#ffd700' }}>+</Text>
              <Text className={styles.entryText}>发布心愿</Text>
            </View>
          </View>

          {/* 今日推荐 */}
          {data && data.todayRecommend.length > 0 && (
              <View className={styles.section}>
                <Text className={styles.sectionTitle}>🔥 今日推荐</Text>
                <ScrollView scrollX className={styles.horizontalScroll}>
                  {data.todayRecommend.map(item => (
                      <View
                          key={item.wishId}
                          className={styles.recommendCard}
                          onClick={() => navigateTo(`/pages/wishDetail/index?id=${item.wishId}`)}
                      >
                        {item.coverUrl ? (
                            <Image className={styles.recommendCover} src={item.coverUrl} mode='aspectFill' />
                        ) : (
                            <View className={styles.recommendCoverPlaceholder}>
                              <Text style={{ color: FRUIT_COLORS[item.fruitType], fontSize: '40rpx' }}>★</Text>
                            </View>
                        )}
                        <Text className={styles.recommendTitle}>{item.title}</Text>
                        <View className={styles.recommendMeta}>
                          <Text style={{ color: FRUIT_COLORS[item.fruitType] }}>
                            {FRUIT_LABELS[item.fruitType]}
                          </Text>
                          <Text className={styles.recommendCount}>{formatCount(item.supportCount)} 互动</Text>
                        </View>
                      </View>
                  ))}
                </ScrollView>
              </View>
          )}

          {/* 我的心愿 */}
          {data && data.myWishes.length > 0 && (
              <View className={styles.section}>
                <View className={styles.sectionHeader}>
                  <Text className={styles.sectionTitle}>♥ 我的心愿</Text>
                  <Text className={styles.moreLink} onClick={() => navigateTo('/pages/myWishes/index')}>查看全部</Text>
                </View>
                {data.myWishes.map(item => (
                    <View
                        key={item.wishId}
                        className={styles.myWishCard}
                        onClick={() => navigateTo(`/pages/wishDetail/index?id=${item.wishId}`)}
                    >
                      <View className={styles.myWishHeader}>
                        <Text className={styles.fruitTag} style={{ background: FRUIT_COLORS[item.fruitType] }}>
                          {FRUIT_LABELS[item.fruitType]}
                        </Text>
                        <Text className={styles.myWishTitle}>{item.title}</Text>
                      </View>
                      <View className={styles.progressWrap}>
                        <View className={styles.progressBg}>
                          <View
                              className={styles.progressBar}
                              style={{ width: `${item.progress}%`, background: FRUIT_COLORS[item.fruitType] }}
                          />
                        </View>
                        <Text className={styles.progressText}>{item.progress}%</Text>
                      </View>
                    </View>
                ))}
              </View>
          )}

          {/* 热门共鸣 */}
          {data && data.hotResonance.length > 0 && (
              <View className={styles.section}>
                <Text className={styles.sectionTitle}>✨ 热门共鸣</Text>
                <View className={styles.hotList}>
                  {data.hotResonance.map((item, index) => (
                      <View
                          key={item.wishId}
                          className={styles.hotItem}
                          onClick={() => navigateTo(`/pages/wishDetail/index?id=${item.wishId}`)}
                      >
                        <Text
                            className={styles.hotRank}
                            style={{ color: index < 3 ? ['#ff6b35', '#ffa500', '#ffd700'][index] : 'rgba(255,255,255,0.4)' }}
                        >
                          {index + 1}
                        </Text>
                        <Text className={styles.hotTitle}>{item.title}</Text>
                        <Text className={styles.hotCount}>{formatCount(item.supportCount)} 互动</Text>
                      </View>
                  ))}
                </View>
              </View>
          )}

          <View style={{ height: '120rpx' }} />
        </ScrollView>
        <WishBGM />
      </View>
  )
}
