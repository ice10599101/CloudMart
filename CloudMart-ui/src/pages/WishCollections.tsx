import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Empty, List, Tag, App } from 'antd'
import { BookOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { WeakNetworkBanner } from '@/components/StateFeedback'
import { listWishCollections, uncollectWish, type WishCollectionItem } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import Skeleton from '@/components/Skeleton'
import styles from './MyWishes.module.css'

/**
 * 我的收藏（Sprint 1.5 验收：收藏列表分页 + 取消收藏）。
 * 游标分页（按收藏时间倒序），加载更多追加。
 */

const FRUIT_LABELS: Record<string, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

const PAGE_SIZE = 20

export default function WishCollections() {
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [items, setItems] = useState<WishCollectionItem[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [removingId, setRemovingId] = useState<string | number | null>(null)

  useEffect(() => {
    if (!user && !userLoading) {
      history.push('/login?redirect=/wish/collections')
    }
  }, [user, userLoading])

  const load = useCallback(async (cursor?: string) => {
    const res = await listWishCollections(cursor, PAGE_SIZE)
    const page = res.data.data ?? []
    setItems((prev) => (cursor ? [...prev, ...page] : page))
    setNextCursor(res.data.meta?.nextCursor ?? null)
  }, [])

  useEffect(() => {
    if (!user) return
    load()
      .catch(() => message.error('收藏列表加载失败'))
      .finally(() => setLoading(false))
  }, [user, load, message])

  const handleUncollect = async (item: WishCollectionItem) => {
    setRemovingId(item.wishId)
    try {
      const res = await uncollectWish(item.wishId)
      if (res.data.success) {
        setItems((prev) => prev.filter((it) => it.wishId !== item.wishId))
        message.success('已取消收藏')
      }
    } catch {
      // 业务错误已由 request 拦截器提示
    } finally {
      setRemovingId(null)
    }
  }

  if (userLoading || (!user && loading)) {
    return <Skeleton />
  }

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <WeakNetworkBanner />
      <div className={styles.backBar}>
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => history.push('/wish/my')}
          className={styles.backBtn}
        >
          我的心愿
        </Button>
      </div>

      <Card title={<span><BookOutlined /> 我的收藏</span>} className={styles.listCard}>
        {loading ? (
          <List
            dataSource={[1, 2, 3]}
            renderItem={() => <List.Item><Skeleton /></List.Item>}
          />
        ) : items.length === 0 ? (
          <Empty description="还没有收藏的心愿，去心愿广场逛逛吧" />
        ) : (
          <List
            dataSource={items}
            renderItem={(item) => (
              <List.Item
                className={styles.wishItem}
                actions={[
                  <Button key="open" type="link" size="small" onClick={() => history.push(`/wish/${item.wishId}`)}>
                    查看详情
                  </Button>,
                  <Button
                    key="remove"
                    type="link"
                    size="small"
                    danger
                    loading={removingId === item.wishId}
                    onClick={() => handleUncollect(item)}
                  >
                    取消收藏
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <span onClick={() => history.push(`/wish/${item.wishId}`)} style={{ cursor: 'pointer' }}>
                      {item.title}
                    </span>
                  }
                  description={`作者：${item.authorNickname || '匿名'} · 收藏于 ${new Date(item.collectedAt).toLocaleDateString('zh-CN')}`}
                />
                <Tag>{FRUIT_LABELS[item.fruitType] ?? item.fruitType}</Tag>
              </List.Item>
            )}
          />
        )}
        {nextCursor && items.length > 0 && (
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Button
              loading={loadingMore}
              onClick={async () => {
                setLoadingMore(true)
                try {
                  await load(nextCursor)
                } finally {
                  setLoadingMore(false)
                }
              }}
            >
              加载更多
            </Button>
          </div>
        )}
      </Card>
    </div>
  )
}
