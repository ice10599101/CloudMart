import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Empty, Skeleton, Statistic, Tabs, Tag } from 'antd'
import { ArrowLeftOutlined, GiftOutlined, StarFilled } from '@ant-design/icons'
import { history } from 'umi'
import {
  getMyGiftSummary,
  listReceivedGiftRecords,
  listSentGiftRecords,
  type GiftRecordItem,
  type MyGiftSummary,
} from '@/api/gift'
import { getMyResources } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import styles from './MyGifts.module.css'

/**
 * 我的礼物（全站虚拟礼物个人中心）。
 *
 * 资产总览：送/收两方向累计件数与星光（礼物为即时消费，无库存语义）
 * + 当前星光余额（与心愿宇宙星光体系同源）；「我送出的 / 我收到的」
 * 游标分页列表，记录可跳回送礼场景（心愿/帖子/直播间）。
 */

type RecordTab = 'sent' | 'received'

const TARGET_META: Record<GiftRecordItem['targetType'], { label: string; path: (id: number) => string }> = {
  WISH: { label: '心愿', path: (id) => `/wish/${id}` },
  POST: { label: '帖子', path: (id) => `/post/${id}` },
  LIVE_ROOM: { label: '直播间', path: (id) => `/live/${id}` },
}

const PAGE_SIZE = 20

export default function MyGifts() {
  const { user, userLoading } = useAuthStore()
  const [summary, setSummary] = useState<MyGiftSummary | null>(null)
  const [balance, setBalance] = useState<number | null>(null)
  const [tab, setTab] = useState<RecordTab>('sent')
  const [records, setRecords] = useState<GiftRecordItem[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    if (!user && !userLoading) {
      history.push('/login?redirect=/gift/my')
      return
    }
    if (!user) return
    getMyGiftSummary()
      .then(({ data: res }) => {
        if (res.success && res.data) setSummary(res.data)
      })
      .catch(() => setSummary(null))
    getMyResources()
      .then(({ data: res }) => {
        if (res.success && res.data) setBalance(res.data.balance ?? null)
      })
      .catch(() => setBalance(null))
  }, [user, userLoading])

  const load = useCallback(async (tabKey: RecordTab, nextCursor?: string | null, reset = true) => {
    if (reset) setLoading(true)
    else setLoadingMore(true)
    try {
      const fetcher = tabKey === 'sent' ? listSentGiftRecords : listReceivedGiftRecords
      const { data: res } = await fetcher(
        nextCursor ? Number(nextCursor) : undefined,
        PAGE_SIZE,
      )
      if (res.success) {
        const list = Array.isArray(res.data) ? res.data : []
        setRecords((prev) => (reset ? list : [...prev, ...list]))
        setCursor(res.meta?.nextCursor ?? null)
        setHasMore(res.meta?.hasMore ?? false)
      }
    } catch {
      if (reset) setRecords([])
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [])

  useEffect(() => {
    if (user) load(tab, null, true)
  }, [tab, user, load])

  return (
    <div style={{ maxWidth: 860, margin: '24px auto', padding: '0 16px' }}>
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => history.push('/profile')} style={{ marginBottom: 12 }}>
        个人中心
      </Button>

      <Card title="礼物资产" className={styles.summaryCard} loading={!summary && !userLoading}>
        <div className={styles.statGrid}>
          <Statistic title="累计送出" value={summary?.sentCount ?? 0} suffix="件" />
          <Statistic
            title="送出消耗星光"
            value={summary?.sentStarlight ?? 0}
            prefix={<StarFilled style={{ color: '#faad14', fontSize: 14 }} />}
          />
          <Statistic title="累计收到" value={summary?.receivedCount ?? 0} suffix="件" />
          <Statistic
            title="收到星光价值"
            value={summary?.receivedStarlight ?? 0}
            prefix={<StarFilled style={{ color: '#faad14', fontSize: 14 }} />}
          />
          <div>
            <Statistic
              title="当前星光余额"
              value={balance ?? 0}
              prefix={<StarFilled style={{ color: '#00d4ff', fontSize: 14 }} />}
            />
            <a className={styles.balanceLink} onClick={() => history.push('/wish/starlight-log')}>
              查看星光流水
            </a>
          </div>
        </div>
      </Card>

      <Card styles={{ body: { paddingTop: 0 } }}>
        <Tabs
          activeKey={tab}
          onChange={(key) => setTab(key as RecordTab)}
          items={[
            { key: 'sent', label: '我送出的' },
            { key: 'received', label: '我收到的' },
          ]}
        />

        {loading ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : records.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={tab === 'sent' ? '还没有送出礼物，去心愿广场逛逛吧' : '还没有收到礼物'}
          />
        ) : (
          <div className={styles.recordList}>
            {records.map((record) => {
              const meta = TARGET_META[record.targetType]
              return (
                <div key={record.id} className={styles.recordRow}>
                  {record.giftIconUrl ? (
                    <img className={styles.recordIcon} src={record.giftIconUrl} alt={record.giftName} />
                  ) : (
                    <GiftOutlined className={styles.recordIconFallback} />
                  )}
                  <div className={styles.recordMain}>
                    <div className={styles.recordTitle}>
                      <span className={styles.recordName}>
                        {record.giftName} ×{record.count}
                      </span>
                      <Tag color="gold" className={styles.recordPrice}>
                        <StarFilled /> {record.totalPrice}
                      </Tag>
                    </div>
                    <div className={styles.recordMeta}>
                      {tab === 'sent' ? (
                        <span>
                          送给我 · {meta.label}
                          <a className={styles.recordLink} onClick={() => history.push(meta.path(record.targetId))}>
                            查看
                          </a>
                        </span>
                      ) : (
                        <span>来自 {record.senderNickname ?? `用户#${record.senderId}`} · {meta.label}</span>
                      )}
                      <span>{formatDateTime(record.createdAt)}</span>
                    </div>
                    {record.message && <div className={styles.recordMessage}>“{record.message}”</div>}
                  </div>
                </div>
              )
            })}
            {hasMore && (
              <Button block loading={loadingMore} onClick={() => load(tab, cursor, false)} className={styles.loadMore}>
                加载更多
              </Button>
            )}
          </div>
        )}
      </Card>
    </div>
  )
}
