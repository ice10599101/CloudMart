import { useEffect, useRef, useState } from 'react'
import { View, Text, Image } from '@tarojs/components'
import { wishApi } from '@/api/wish'
import type { WishInteractionItem } from '@/types'
import styles from './index.module.scss'

/**
 * 心愿祝福墙（Sprint 1.2 补充）：展示该心愿收到的全部 BLESS 互动。
 *
 * 祝福者与被祝福者都能看到；cursor 分页 + 上拉/点击加载更多；
 * 祝福发送成功后由父组件递增 refreshTick 触发重载首屏。
 * 图片模式移动端以纯文本呈现（移动端祝福输入不支持图片）。
 */

const BLESS_PAGE_SIZE = 10

interface WishBlessListProps {
  wishId: number | string
  blessCount: number
  /** 祝福发送成功后的刷新信号（父组件递增） */
  refreshTick?: number
}

/** 相对友好的时间展示（与评论组件一致口径） */
function formatTime(iso: string): string {
  const date = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60 / 1000)} 分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 60 / 60 / 1000)} 小时前`
  return date.toLocaleDateString('zh-CN')
}

export default function WishBlessList({ wishId, blessCount, refreshTick = 0 }: WishBlessListProps) {
  const [items, setItems] = useState<WishInteractionItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  /** 防重复加载（refreshTick 重载/触底竞态） */
  const requestSeq = useRef(0)

  const loadFirstPage = async () => {
    const seq = ++requestSeq.current
    setLoading(true)
    try {
      const res = await wishApi.listInteractions(wishId, {
        type: 'BLESS',
        pageSize: BLESS_PAGE_SIZE,
      })
      if (seq !== requestSeq.current) return
      if (res.data.success) {
        setItems(res.data.data)
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 未登录/无权限时列表留空不打扰
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }

  useEffect(() => {
    loadFirstPage()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wishId, refreshTick])

  const handleLoadMore = async () => {
    if (!cursor || loadingMore) return
    const seq = ++requestSeq.current
    setLoadingMore(true)
    try {
      const res = await wishApi.listInteractions(wishId, {
        type: 'BLESS',
        cursor,
        pageSize: BLESS_PAGE_SIZE,
      })
      if (seq !== requestSeq.current) return
      if (res.data.success) {
        setItems((prev) => {
          const seen = new Set(prev.map((i) => i.id))
          return [...prev, ...res.data.data.filter((i) => !seen.has(i.id))]
        })
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 错误静默，保持现状
    } finally {
      if (seq === requestSeq.current) setLoadingMore(false)
    }
  }

  return (
    <View className={styles.section}>
      <View className={styles.header}>
        <Text className={styles.headerIcon}>🌟</Text>
        <Text className={styles.headerTitle}>祝福</Text>
        <Text className={styles.headerCount}>{blessCount}</Text>
      </View>

      {loading ? (
        <View className={styles.loadingWrap}>
          <Text className={styles.loadingText}>加载中...</Text>
        </View>
      ) : items.length === 0 ? (
        <View className={styles.emptyWrap}>
          <Text className={styles.emptyIcon}>🎁</Text>
          <Text className={styles.emptyText}>还没有祝福，来送出第一份祝福吧</Text>
        </View>
      ) : (
        <View className={styles.list}>
          {items.map((blessing) => (
            <View key={blessing.id} className={styles.item}>
              {blessing.avatar ? (
                <Image className={styles.avatar} src={blessing.avatar} mode='aspectFill' />
              ) : (
                <View className={styles.avatarPlaceholder}>
                  <Text className={styles.avatarStar}>★</Text>
                </View>
              )}
              <View className={styles.itemBody}>
                <View className={styles.itemMeta}>
                  <Text className={styles.itemNickname}>{blessing.nickname}</Text>
                  <Text className={styles.itemTime}>{formatTime(blessing.createdAt)}</Text>
                </View>
                {/* content 后端已 XSS 转义，可直接渲染 */}
                <Text className={styles.itemContent}>{blessing.content ?? ''}</Text>
              </View>
            </View>
          ))}
          {hasMore && (
            <View className={styles.loadMoreWrap} onClick={() => !loadingMore && handleLoadMore()}>
              <Text className={styles.loadMoreText}>{loadingMore ? '加载中...' : '加载更多祝福'}</Text>
            </View>
          )}
          {!hasMore && items.length > 0 && (
            <View className={styles.loadMoreWrap}>
              <Text className={styles.loadMoreText}>已经到底啦~</Text>
            </View>
          )}
        </View>
      )}
    </View>
  )
}