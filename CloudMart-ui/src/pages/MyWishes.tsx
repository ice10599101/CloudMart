import { useState, useEffect, useCallback, useRef } from 'react'
import { Spin, Empty, Card, Tag, Button, Segmented, Popconfirm, App } from 'antd'
import { PlusOutlined, DeleteOutlined, RightOutlined, TrophyOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { listMyWishes, deleteWish } from '@/api/wish'
import type { MyWishListItem, WishStatus } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import Skeleton from '@/components/Skeleton'
import styles from './MyWishes.module.css'
import WishBGM from '@/components/WishBGM'

const PAGE_SIZE = 20

const STATUS_FILTERS = [
  { label: '全部', value: '' },
  { label: '进行中', value: 'ACTIVE' },
  { label: '还愿中', value: 'FULFILLING' },
  { label: '已还愿', value: 'FULFILLED' },
]

const FRUIT_LABELS: Record<string, string> = {
  GLOW: '微光',
  RESONANCE: '共鸣',
  BLOOM: '绽放',
  SPARK: '星火',
}

const FRUIT_COLORS: Record<string, string> = {
  GLOW: '#00D4FF',
  RESONANCE: '#9370DB',
  BLOOM: '#FF6B6B',
  SPARK: '#FFD700',
}

export default function MyWishes() {
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [wishes, setWishes] = useState<MyWishListItem[]>([])
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const { message } = App.useApp()
  const { user } = useAuthStore()
  const sentinelRef = useRef<HTMLDivElement>(null)

  const fetchWishes = useCallback(async (reset: boolean) => {
    if (!user) {
      setLoading(false)
      return
    }
    if (reset) {
      setLoading(true)
      setCursor(null)
    } else {
      setLoadingMore(true)
    }

    try {
      const res = await listMyWishes({
        status: (statusFilter || undefined) as WishStatus | undefined,
        cursor: reset ? undefined : cursor ?? undefined,
        pageSize: PAGE_SIZE,
      })

      if (res.data.success) {
        const newItems = res.data.data
        setWishes(prev => reset ? newItems : [...prev, ...newItems])
        const meta = res.data.meta
        setCursor(meta?.nextCursor ?? null)
        setHasMore(meta?.hasMore ?? false)
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [user, statusFilter, cursor])

  useEffect(() => {
    if (!user) {
      message.warning('请先登录')
      history.push('/login?redirect=/wish/my')
      return
    }
    fetchWishes(true)
  }, [user, statusFilter])

  // IntersectionObserver 无限滚动
  useEffect(() => {
    if (!hasMore || loading || loadingMore) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          fetchWishes(false)
        }
      },
      { threshold: 0.1 }
    )

    if (sentinelRef.current) {
      observer.observe(sentinelRef.current)
    }

    return () => observer.disconnect()
  }, [hasMore, loading, loadingMore, fetchWishes])

  const handleDelete = async (id: number) => {
    try {
      const res = await deleteWish(id)
      if (res.data.success) {
        message.success('心愿已删除')
        setWishes(prev => prev.filter(w => w.id !== id))
      }
    } catch {
      // 错误已由 request 拦截器处理
    }
  }

  if (!user) return null

  if (loading) {
    return (
      <div className={`${styles.loadingContainer} wish-universe-theme`}>
        <Skeleton variant="wish-list" count={6} />
      </div>
    )
  }

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.header}>
        <h1 className={styles.pageTitle}>我的心愿</h1>
        <div className={styles.toolbar}>
          <Segmented
            options={STATUS_FILTERS}
            value={statusFilter}
            onChange={(value) => setStatusFilter(value as string)}
          />
          <Button
            icon={<TrophyOutlined />}
            onClick={() => history.push('/wish/badges')}
          >
            我的徽章
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => history.push('/wish/create')}
          >
            新建心愿
          </Button>
        </div>
      </div>

      {wishes.length === 0 ? (
        <div className={styles.emptyContainer}>
          <Empty description="还没有心愿，点击下方按钮许下第一个心愿" />
          <Button
            type="primary"
            size="large"
            onClick={() => history.push('/wish/create')}
          >
            发布心愿
          </Button>
        </div>
      ) : (
        <>
          <div className={styles.list}>
            {wishes.map((wish) => (
              <Card
                key={wish.id}
                className={styles.wishCard}
                hoverable
                onClick={() => history.push(`/wish/${wish.id}`)}
              >
                <div className={styles.cardContent}>
                  <div className={styles.cardLeft}>
                    <Tag color={FRUIT_COLORS[wish.fruitType]}>
                      {FRUIT_LABELS[wish.fruitType]}
                    </Tag>
                    <div className={styles.cardInfo}>
                      <h3 className={styles.cardTitle}>{wish.title}</h3>
                      <span className={styles.cardDate}>
                        {new Date(wish.createdAt).toLocaleDateString('zh-CN')}
                      </span>
                    </div>
                  </div>
                  <div className={styles.cardRight}>
                    <div className={styles.progressWrap}>
                      <div className={styles.progressBg}>
                        <div
                          className={styles.progressBar}
                          style={{
                            width: `${wish.progress}%`,
                            background: FRUIT_COLORS[wish.fruitType],
                          }}
                        />
                      </div>
                      <span className={styles.progressText}>{wish.progress}%</span>
                    </div>
                    <div className={styles.cardActions}>
                      <Popconfirm
                        title="确定删除这个心愿吗？"
                        description="删除后不可恢复"
                        onConfirm={(e) => {
                          e?.stopPropagation()
                          handleDelete(wish.id)
                        }}
                        onCancel={(e) => e?.stopPropagation()}
                        okText="确定"
                        cancelText="取消"
                      >
                        <Button
                          danger
                          size="small"
                          icon={<DeleteOutlined />}
                          onClick={(e) => e.stopPropagation()}
                        >
                          删除
                        </Button>
                      </Popconfirm>
                      <RightOutlined className={styles.arrow} />
                    </div>
                  </div>
                </div>
              </Card>
            ))}
          </div>

          {hasMore && (
            <div ref={sentinelRef} className={styles.sentinel}>
              {loadingMore && <Spin />}
            </div>
          )}

          {!hasMore && wishes.length > 0 && (
            <div className={styles.endText}>已经到底啦~</div>
          )}
        </>
      )}
      <WishBGM />
    </div>
  )
}
