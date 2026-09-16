import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Empty, Spin } from 'antd'
import { GiftOutlined, StarOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { listInteractions, type InteractionItem } from '@/api/wish'
import { splitCommentImages } from '@/components/CommentToolbar'
import DecoratedAvatar from '@/components/DecoratedAvatar'
import styles from './style.module.css'

/**
 * 心愿祝福列表（文档 2.2 节补充）：展示该心愿收到的全部 BLESS 互动。
 *
 * 祝福者与被祝福者都能看到：公开心愿任何登录用户可见互动列表（后端
 * PRIVATE/TREE_HOLE 仅作者，语义与评论一致）。cursor 分页 + 加载更多；
 * 祝福发送成功后由父组件递增 refreshTick 触发重载首屏。
 */

const BLESS_PAGE_SIZE = 10

interface WishBlessListProps {
  wishId: number | string
  blessCount: number
  /** 祝福发送成功后的刷新信号（父组件递增） */
  refreshTick?: number
}

export default function WishBlessList({ wishId, blessCount, refreshTick = 0 }: WishBlessListProps) {
  const [blessings, setBlessings] = useState<InteractionItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  /** 防重复加载（refreshTick 重载/触底竞态） */
  const requestSeq = useRef(0)

  const loadFirstPage = useCallback(async () => {
    const seq = ++requestSeq.current
    setLoading(true)
    try {
      const res = await listInteractions(wishId, { type: 'BLESS', pageSize: BLESS_PAGE_SIZE })
      if (seq !== requestSeq.current) return
      if (res.data.success) {
        setBlessings(res.data.data)
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 错误已由 request 拦截器处理；未登录/无权限时列表留空不打扰
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }, [wishId])

  useEffect(() => {
    loadFirstPage()
  }, [loadFirstPage, refreshTick])

  const handleLoadMore = async () => {
    if (!cursor || loadingMore) return
    const seq = ++requestSeq.current
    setLoadingMore(true)
    try {
      const res = await listInteractions(wishId, {
        type: 'BLESS',
        cursor,
        pageSize: BLESS_PAGE_SIZE,
      })
      if (seq !== requestSeq.current) return
      if (res.data.success) {
        // cursor 分页按 id 去重合并，防御服务端数据变动导致的重复
        setBlessings((prev) => {
          const seen = new Set(prev.map((b) => b.id))
          return [...prev, ...res.data.data.filter((b) => !seen.has(b.id))]
        })
        setCursor(res.data.meta?.nextCursor ?? null)
        setHasMore(Boolean(res.data.meta?.hasMore))
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      if (seq === requestSeq.current) setLoadingMore(false)
    }
  }

  /** 祝福内容渲染：![图片](url) 模式还原为图片，其余文本后端已转义原样渲染 */
  const renderContent = (text: string) =>
      splitCommentImages(text).map((part, i) =>
          part.type === 'image' ? (
              <img key={i} src={part.value} alt="祝福图片" className={styles.blessImage} />
          ) : (
              <span key={i}>{part.value}</span>
          ),
      )

  return (
    <div className={styles.section}>
      <div className={styles.header}>
        <GiftOutlined className={styles.headerIcon} aria-hidden="true" />
        <span className={styles.headerTitle}>祝福</span>
        <span className={styles.headerCount}>{blessCount}</span>
      </div>

      {loading ? (
        <div className={styles.loadingWrap}>
          <Spin />
        </div>
      ) : blessings.length === 0 ? (
        <Empty description="还没有祝福，来送出第一份祝福吧" className={styles.empty} />
      ) : (
        <ul className={styles.list}>
          {blessings.map((blessing) => (
            <li key={blessing.id} className={styles.item}>
              <DecoratedAvatar
                userId={blessing.userId ?? undefined}
                size={32}
                src={blessing.avatar || undefined}
                fallback={<StarOutlined />}
                style={{ cursor: 'pointer' }}
                onClick={() => history.push(`/user/${blessing.userId}`)}
              />
              <div className={styles.itemBody}>
                <div className={styles.itemMeta}>
                  <span
                    className={styles.itemNickname}
                    onClick={() => history.push(`/user/${blessing.userId}`)}
                  >
                    {blessing.nickname}
                  </span>
                  <span className={styles.itemTime}>
                    {new Date(blessing.createdAt).toLocaleString('zh-CN')}
                  </span>
                </div>
                <p className={styles.itemContent}>{renderContent(blessing.content ?? '')}</p>
              </div>
            </li>
          ))}
        </ul>
      )}

      {hasMore && !loading && (
        <div className={styles.loadMoreWrap}>
          <Button loading={loadingMore} onClick={handleLoadMore}>
            加载更多祝福
          </Button>
        </div>
      )}
    </div>
  )
}