import { useState, useEffect, useCallback, useRef } from 'react'
import { Spin, Empty, Card, Tag, Button, Segmented, Popconfirm, App } from 'antd'
import {
  PlusOutlined,
  DeleteOutlined,
  RightOutlined,
  TrophyOutlined,
  BookOutlined,
  GiftOutlined,
  CalendarOutlined,
} from '@ant-design/icons'
import { history } from 'umi'
import { WeakNetworkBanner } from '@/components/StateFeedback'
import { listMyWishes, deleteWish, getMyResources } from '@/api/wish'
import type { MyWishListItem, WishStatus, MyResourcesData } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import Skeleton from '@/components/Skeleton'
import StarCountUp from '@/components/StarCountUp'
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
  const [resources, setResources] = useState<MyResourcesData | null>(null)
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()
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
    if (!user && !userLoading) {
      message.warning('请先登录')
      history.push('/login?redirect=/wish/my')
      return
    }
    fetchWishes(true)
  }, [user, statusFilter])

  // 星光余额概览（文档 L1910：星光余额展示）
  useEffect(() => {
    if (!user) return
    getMyResources()
      .then((res) => {
        if (res.data.success) setResources(res.data.data)
      })
      .catch(() => {
        // 余额卡片加载失败不阻塞心愿列表
      })
  }, [user])

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

  const handleDelete = async (id: number | string) => {
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
      <WeakNetworkBanner />
      <div className={styles.header}>
        <h1 className={styles.pageTitle}>我的心愿</h1>
        {resources && (
          <Card
            size="small"
            style={{
              marginBottom: 16,
              background: 'linear-gradient(135deg, rgba(255, 215, 0, 0.12), rgba(0, 212, 255, 0.08))',
              borderColor: 'rgba(255, 215, 0, 0.35)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                <StarCountUp
                  value={resources.balance}
                  showIcon
                  style={{ fontSize: 26, fontWeight: 700, color: '#FFD700' }}
                />
                <span style={{ color: 'var(--color-text-secondary)' }}>星光余额</span>
              </div>
              <div style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>
                今日 <span style={{ color: '#52c41a' }}>+{resources.todayEarned}</span>
                {' / '}
                <span style={{ color: '#ff7875' }}>-{resources.todaySpent}</span>
              </div>
            </div>
          </Card>
        )}
        <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
          <Button icon={<CalendarOutlined />} onClick={() => history.push('/wish/signin')}>
            每日签到
          </Button>
          <Button icon={<BookOutlined />} onClick={() => history.push('/wish/collections')}>
            我的收藏
          </Button>
          <Button icon={<GiftOutlined />} onClick={() => history.push('/wish/workshop')}>
            虚拟工坊
          </Button>
        </div>
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
          <Empty description="还没有心愿，点击右上角「新建心愿」许下第一个心愿" />
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
