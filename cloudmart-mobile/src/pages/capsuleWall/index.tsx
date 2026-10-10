import { useState, useEffect } from 'react'
import { View, Text, ScrollView } from '@tarojs/components'
import { wishApi, type WallCapsuleItem } from '@/api/wish'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

/**
 * 公共胶囊墙（§6）：匿名精选墙（已审核通过的已开封胶囊，不暴露作者身份）。
 * 分页加载；申请上墙在胶囊详情/我的胶囊完成（作者本人）。
 */
export default function CapsuleWallPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { dataTheme, themeStyle } = useThemeClass()
  const [items, setItems] = useState<WallCapsuleItem[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(true)

  const load = async (pageNum: number, append: boolean) => {
    try {
      const res = await wishApi.getCapsuleWall(pageNum, 10)
      const pageData = res.data?.data
      const list = pageData?.records ?? []
      setItems(prev => (append ? [...prev, ...list] : list))
      setPage(pageNum)
      setHasMore(list.length >= 10)
    } catch {
      setHasMore(false)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load(1, false) }, [])

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <CustomNavBar title='公共胶囊墙' back />
      <ScrollView
        scrollY
        className={styles.scroll}
        style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}
        onScrollToLower={() => hasMore && !loading && load(page + 1, true)}
      >
        <Text className={styles.intro}>
          时间到的那一刻，有些话值得被更多人看见。这里每颗胶囊都是匿名展出的时光信笺。
        </Text>
        {loading && items.length === 0 ? (
          <Text className={styles.empty}>加载中...</Text>
        ) : items.length === 0 ? (
          <Text className={styles.empty}>墙上还没有胶囊，去申请第一颗吧</Text>
        ) : items.map(item => (
          <View key={item.capsuleId} className={styles.card}>
            <Text className={styles.title}>{item.title}</Text>
            <Text className={styles.content}>{item.content}</Text>
            <Text className={styles.meta}>
              🕐 {item.openedAt ? new Date(item.openedAt).toLocaleDateString('zh-CN') : ''} 开启
            </Text>
          </View>
        ))}
        {!hasMore && items.length > 0 && <Text className={styles.empty}>— 墙的尽头 —</Text>}
      </ScrollView>
    </View>
  )
}
