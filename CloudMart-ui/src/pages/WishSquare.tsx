import { useState, useEffect, useCallback, useRef } from 'react'
import { Spin, Empty, Card, Tag, Select, Input, Button, Avatar } from 'antd'
import { StarOutlined, HeartOutlined, MessageOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { WeakNetworkBanner, pageSizeForNetwork } from '@/components/StateFeedback'
import { listWishes, getCategories } from '@/api/wish'
import type { WishListItem, Category } from '@/api/wish'
import { stripHtml } from '@/utils/format'
import Skeleton from '@/components/Skeleton'
import styles from './WishSquare.module.css'
import WishBGM from '@/components/WishBGM'

const PAGE_SIZE = 20

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: '进行中',
  FULFILLING: '还愿中',
  FULFILLED: '已还愿',
}

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

export default function WishSquare() {
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [wishes, setWishes] = useState<WishListItem[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [categoryId, setCategoryId] = useState<number | undefined>()
  const [keyword, setKeyword] = useState('')
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const sentinelRef = useRef<HTMLDivElement>(null)
  const observerRef = useRef<IntersectionObserver | null>(null)

  const fetchWishes = useCallback(async (reset: boolean) => {
    if (reset) {
      setLoading(true)
      setCursor(null)
    } else {
      setLoadingMore(true)
    }

    try {
      const res = await listWishes({
        categoryId,
        keyword: keyword || undefined,
        cursor: reset ? undefined : cursor ?? undefined,
        pageSize: pageSizeForNetwork(PAGE_SIZE),
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
  }, [categoryId, keyword, cursor])

  useEffect(() => {
    fetchWishes(true)
  }, [categoryId, keyword])

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const res = await getCategories()
        if (res.data.success) {
          setCategories(res.data.data)
        }
      } catch {
        // ignore
      }
    }
    fetchCategories()
  }, [])

  useEffect(() => {
    if (!hasMore || loading || loadingMore) return

    if (observerRef.current) {
      observerRef.current.disconnect()
    }

    observerRef.current = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !loading && !loadingMore) {
          fetchWishes(false)
        }
      },
      { threshold: 0.1 }
    )

    if (sentinelRef.current) {
      observerRef.current.observe(sentinelRef.current)
    }

    return () => {
      observerRef.current?.disconnect()
    }
  }, [hasMore, loading, loadingMore, fetchWishes])

  const handleSearch = (value: string) => {
    setKeyword(value)
  }

  const handleCategoryChange = (value: number | undefined) => {
    setCategoryId(value)
  }

  if (loading) {
    return (
      <div className={`${styles.loadingContainer} wish-universe-theme`}>
        <Skeleton variant="wish-masonry" count={8} />
      </div>
    )
  }

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <WeakNetworkBanner />
      <div className={styles.header}>
        <h1 className={styles.pageTitle}>心愿广场</h1>
        <div className={styles.filters}>
          <Select
            allowClear
            placeholder="全部分类"
            value={categoryId}
            onChange={handleCategoryChange}
            className={styles.categorySelect}
            options={categories.map(c => ({ label: c.name, value: c.id }))}
          />
          <Input.Search
            allowClear
            placeholder="搜索心愿..."
            onSearch={handleSearch}
            className={styles.searchInput}
          />
        </div>
      </div>

      {wishes.length === 0 ? (
        <div className={styles.emptyContainer}>
          <Empty description="暂无心愿，成为第一个许愿的人吧" />
          <Button
            type="primary"
            onClick={() => history.push('/wish/create')}
            className={styles.createBtn}
          >
            发布心愿
          </Button>
        </div>
      ) : (
        <>
          <div className={styles.masonryGrid}>
            {wishes.map((wish) => (
              <Card
                key={wish.id}
                hoverable
                className={styles.wishCard}
                onClick={() => history.push(`/wish/${wish.id}`)}
                cover={
                  wish.mediaUrls && wish.mediaUrls.length > 0 ? (
                    <img
                      loading="lazy"
                      src={wish.mediaUrls[0]}
                      alt={wish.title}
                      className={styles.cardCover}
                    />
                  ) : undefined
                }
              >
                <div className={styles.cardBody}>
                  <h3 className={styles.cardTitle}>{wish.title}</h3>
                  <p className={styles.cardDesc}>{stripHtml(wish.description)}</p>
                  {wish.tags && wish.tags.length > 0 && (
                    <div className={styles.cardTags}>
                      {wish.tags.slice(0, 3).map(tag => (
                        <Tag key={tag} className={styles.tag}>{tag}</Tag>
                      ))}
                    </div>
                  )}
                  <div className={styles.cardFooter}>
                    <div className={styles.author}>
                      <Avatar
                        size={24}
                        src={wish.authorAvatar || undefined}
                        icon={<StarOutlined />}
                      />
                      <span className={styles.authorName}>{wish.authorNickname}</span>
                    </div>
                    <div className={styles.stats}>
                      <span className={styles.statItem}>
                        <HeartOutlined /> {formatCount(wish.supportCount)}
                      </span>
                      <span className={styles.statItem}>
                        <MessageOutlined /> {formatCount(wish.commentCount)}
                      </span>
                    </div>
                  </div>
                  {wish.status !== 'ACTIVE' && (
                    <Tag className={styles.statusTag}>
                      {STATUS_LABELS[wish.status] || wish.status}
                    </Tag>
                  )}
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
