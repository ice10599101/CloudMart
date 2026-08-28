import { useState, useEffect, useCallback } from 'react'
import { Spin, message } from 'antd'
import {
  HeartOutlined,
  MessageOutlined,
  StarOutlined,
  UserOutlined,
  NotificationOutlined,
  TrophyOutlined,
  SafetyCertificateOutlined,
  BellOutlined,
  SendOutlined,
  ShareAltOutlined,
  ClockCircleOutlined,
  RobotOutlined,
} from '@ant-design/icons'
import { history } from 'umi'
import Skeleton from '@/components/Skeleton'
import {
  listNotifications,
  markAsRead,
  markAllAsRead,
  type NotificationItem,
} from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { recordExpectedAction, type ExpectedActionType } from '@/api/wish'
import styles from './Messages.module.css'

type NotificationCategory = 'all' | 'interaction' | 'follow' | 'system'

interface TabConfig {
  key: NotificationCategory
  label: string
}

interface EnrichedNotification extends NotificationItem {
  category: NotificationCategory
  avatarClass: string
  icon: React.ReactNode
}

const TABS: TabConfig[] = [
  { key: 'all', label: '全部' },
  { key: 'interaction', label: '互动' },
  { key: 'follow', label: '关注' },
  { key: 'system', label: '系统' },
]

const NOTIFICATION_TYPE_MAP: Record<string, { category: NotificationCategory; avatarClass: string; icon: React.ReactNode }> = {
  LIKE: { category: 'interaction', avatarClass: styles.avatarLike, icon: <HeartOutlined /> },
  COMMENT: { category: 'interaction', avatarClass: styles.avatarComment, icon: <MessageOutlined /> },
  COLLECT: { category: 'interaction', avatarClass: styles.avatarCollect, icon: <StarOutlined /> },
  SHARE: { category: 'interaction', avatarClass: styles.avatarCollect, icon: <ShareAltOutlined /> },
  MENTION: { category: 'interaction', avatarClass: styles.avatarComment, icon: <MessageOutlined /> },
  TAG_NEW_POST: { category: 'interaction', avatarClass: styles.avatarCollect, icon: <ShareAltOutlined /> },
  FOLLOW: { category: 'follow', avatarClass: styles.avatarFollow, icon: <UserOutlined /> },
  SYSTEM: { category: 'system', avatarClass: styles.avatarSystem, icon: <NotificationOutlined /> },
  BADGE: { category: 'system', avatarClass: styles.avatarBadge, icon: <TrophyOutlined /> },
  ACCOUNT: { category: 'system', avatarClass: styles.avatarAccount, icon: <SafetyCertificateOutlined /> },
  CHAT: { category: 'interaction', avatarClass: styles.avatarComment, icon: <SendOutlined /> },
  LEVEL_UP: { category: 'system', avatarClass: styles.avatarBadge, icon: <TrophyOutlined /> },
  // 心愿宇宙域通知类型（Sprint 1.2/2.4/2.5，默认 SYSTEM 样式兜底）
  WISH_COMMENT: { category: 'interaction', avatarClass: styles.avatarComment, icon: <MessageOutlined /> },
  WISH_LIGHT: { category: 'interaction', avatarClass: styles.avatarLike, icon: <HeartOutlined /> },
  WISH_FULFILL: { category: 'system', avatarClass: styles.avatarBadge, icon: <TrophyOutlined /> },
  CAPSULE_OPEN: { category: 'system', avatarClass: styles.avatarSystem, icon: <ClockCircleOutlined /> },
  CAPSULE_AVAILABLE: { category: 'system', avatarClass: styles.avatarSystem, icon: <ClockCircleOutlined /> },
  BADGE_EARNED: { category: 'system', avatarClass: styles.avatarBadge, icon: <TrophyOutlined /> },
  CHECKIN_REMINDER: { category: 'system', avatarClass: styles.avatarSystem, icon: <ClockCircleOutlined /> },
  AI_REMINDER: { category: 'system', avatarClass: styles.avatarSystem, icon: <RobotOutlined /> },
  CHECK_IN: { category: 'system', avatarClass: styles.avatarSystem, icon: <BellOutlined /> },
}

function formatRelativeTime(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return '刚刚'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days === 1) return '昨天'
  if (days < 30) return `${days}天前`
  const months = Math.floor(days / 30)
  if (months < 12) return `${months}个月前`
  return `${Math.floor(months / 12)}年前`
}

function enrichNotification(item: NotificationItem): EnrichedNotification {
  const mapping = NOTIFICATION_TYPE_MAP[item.type] ?? NOTIFICATION_TYPE_MAP.SYSTEM
  return {
    ...item,
    category: mapping.category,
    avatarClass: mapping.avatarClass,
    icon: mapping.icon,
  }
}

function extractUsername(content: string): string {
  const match = content.match(/^(\S+)\s/)
  return match ? match[1] : ''
}

function extractPostTitle(content: string): string | null {
  const match = content.match(/《(.+?)》/)
  return match ? match[1] : null
}

function extractCommentPreview(content: string): string | null {
  const match = content.match(/：(.+)$/)
  return match ? match[1] : null
}

function extractFollowNickname(content: string): string {
  const match = content.match(/^(\S+)\s+关注了/)
  return match ? match[1] : ''
}

function renderNotificationText(item: EnrichedNotification): React.ReactNode {
  const username = extractUsername(item.content)
  const postTitle = extractPostTitle(item.content)
  const commentPreview = item.type === 'COMMENT' ? extractCommentPreview(item.content) : null

  if (item.type === 'LIKE') {
    return (
      <p className={styles.notificationText}>
        <strong>{username}</strong> 赞了你的帖子
        {postTitle && (
          <span className={styles.postLink} onClick={(e) => { e.stopPropagation(); navigateToBiz(item) }}>《{postTitle}》</span>
        )}
      </p>
    )
  }

  if (item.type === 'COMMENT') {
    return (
      <>
        <p className={styles.notificationText}>
          <strong>{username}</strong> 评论了你的帖子
          {postTitle && (
            <span className={styles.postLink} onClick={(e) => { e.stopPropagation(); navigateToBiz(item) }}>《{postTitle}》</span>
          )}
        </p>
        {commentPreview && (
          <span className={styles.notificationCommentPreview}>{commentPreview}</span>
        )}
      </>
    )
  }

  if (item.type === 'COLLECT') {
    return (
      <p className={styles.notificationText}>
        <strong>{username}</strong> 收藏了你的帖子
        {postTitle && (
          <span className={styles.postLink} onClick={(e) => { e.stopPropagation(); navigateToBiz(item) }}>《{postTitle}》</span>
        )}
      </p>
    )
  }

  if (item.type === 'SHARE') {
    return (
      <p className={styles.notificationText}>
        <strong>{username}</strong> 分享了你的帖子
        {postTitle && (
          <span className={styles.postLink} onClick={(e) => { e.stopPropagation(); navigateToBiz(item) }}>《{postTitle}》</span>
        )}
      </p>
    )
  }

  if (item.type === 'MENTION') {
    return (
      <p className={styles.notificationText}>
        <strong>{username}</strong> 在帖子中@了你
        {postTitle && (
          <span className={styles.postLink} onClick={(e) => { e.stopPropagation(); navigateToBiz(item) }}>《{postTitle}》</span>
        )}
      </p>
    )
  }

  if (item.type === 'TAG_NEW_POST') {
    return (
      <p className={styles.notificationText}>
        {item.content}
      </p>
    )
  }

  if (item.type === 'FOLLOW') {
    return (
      <p className={styles.notificationText}>
        <strong>{extractFollowNickname(item.content)}</strong> 关注了你
      </p>
    )
  }

  return <p className={styles.notificationText}>{item.content}</p>
}

function navigateToBiz(item: EnrichedNotification) {
  if (item.bizType === 'POST' && item.bizId) {
    history.push(`/post/${item.bizId}`)
  } else if (item.bizType === 'TAG' && item.bizId) {
    history.push(`/topic/${item.bizId}`)
  } else if (item.bizType === 'USER' && item.bizId) {
    history.push(`/user/${item.bizId}`)
  } else if (item.bizType === 'CONVERSATION' && item.bizId) {
    history.push(`/chat/${item.bizId}`)
  } else if (item.type === 'CHAT' && item.bizId) {
    history.push(`/chat/${item.bizId}`)
  } else if (item.bizType === 'WISH' && item.bizId) {
    history.push(`/wish/${item.bizId}`)
  } else if (item.bizType === 'CAPSULE' && item.bizId) {
    history.push(`/wish/capsules/${item.bizId}`)
  }
}

function FollowDetail({ item, followedMap, onToggleFollow }: { item: EnrichedNotification; followedMap: Record<number, boolean>; onToggleFollow: (userId: number) => void }) {
  const nickname = extractFollowNickname(item.content)
  const userId = item.bizId ?? item.userId
  const isFollowed = followedMap[userId] ?? false

  return (
    <div className={styles.followRow}>
      <div className={styles.followAvatar}>
        {nickname ? nickname[0] : <UserOutlined />}
      </div>
      <span className={styles.followNickname}>{nickname}</span>
      <button
        type="button"
        className={`${styles.followBackBtn} ${isFollowed ? styles.followBackBtnFollowed : ''}`}
        onClick={(e) => {
          e.stopPropagation()
          onToggleFollow(userId)
        }}
      >
        {isFollowed ? '已关注' : '回关'}
      </button>
    </div>
  )
}

/** 预期管理通知 3 选项（Sprint 2.5：延长预期/调整目标/转入时间胶囊） */
function ExpectedActions({
  item,
  onAction,
}: {
  item: EnrichedNotification
  onAction: (item: EnrichedNotification, action: ExpectedActionType) => void
}) {
  return (
    <div className={styles.expectedActions}>
      <button
        type="button"
        className={styles.expectedBtn}
        onClick={(e) => { e.stopPropagation(); onAction(item, 'EXTEND') }}
      >
        延长预期
      </button>
      <button
        type="button"
        className={styles.expectedBtn}
        onClick={(e) => { e.stopPropagation(); onAction(item, 'ADJUST') }}
      >
        调整目标
      </button>
      <button
        type="button"
        className={styles.expectedBtn}
        onClick={(e) => { e.stopPropagation(); onAction(item, 'TO_CAPSULE') }}
      >
        转入胶囊
      </button>
    </div>
  )
}

export default function Messages() {
  const [activeTab, setActiveTab] = useState<NotificationCategory>('all')
  const [notifications, setNotifications] = useState<EnrichedNotification[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [followedMap, setFollowedMap] = useState<Record<number, boolean>>({})
  const { unreadCount, fetchUnreadCount, resetUnread } = useNotificationStore()

  const enrichAndSet = useCallback((items: NotificationItem[]) => {
    setNotifications(items.map(enrichNotification))
  }, [])

  const fetchNotifications = useCallback(async (pageNum: number, append = false, type?: string) => {
    try {
      const apiType = type && type !== 'all' ? type : undefined
      const res = await listNotifications(pageNum, 20, apiType)
      const items = res.data.data ?? []
      const meta = res.data.meta
      if (append) {
        setNotifications((prev) => [...prev, ...items.map(enrichNotification)])
      } else {
        enrichAndSet(items)
      }
      if (meta) {
        setHasMore((meta.page ?? 1) * (meta.pageSize ?? 20) < (meta.total ?? 0))
      } else {
        setHasMore(items.length >= 20)
      }
      return
    } catch {
      // API failed, keep empty state
    }

    if (!append) {
      setNotifications([])
      setHasMore(false)
    }
  }, [enrichAndSet])

  useEffect(() => {
    let cancelled = false
    async function init() {
      setLoading(true)
      await Promise.allSettled([fetchNotifications(1), fetchUnreadCount()])
      if (!cancelled) setLoading(false)
    }
    init()
    return () => { cancelled = true }
  }, [fetchNotifications, fetchUnreadCount])

  useEffect(() => {
    fetchNotifications(1, false, activeTab)
  }, [activeTab, fetchNotifications])

  const handleMarkAllRead = useCallback(async () => {
    try {
      await markAllAsRead()
    } catch {
      // optimistic
    }
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })))
    resetUnread()
    message.success('已全部标为已读')
  }, [resetUnread])

  const handleNotificationClick = useCallback(async (item: EnrichedNotification) => {
    if (!item.isRead) {
      setNotifications((prev) =>
        prev.map((n) => (n.id === item.id ? { ...n, isRead: true } : n)),
      )
      fetchUnreadCount()
      try {
        await markAsRead(item.id)
      } catch {
        // optimistic
      }
    }
    navigateToBiz(item)
  }, [fetchUnreadCount])

  const handleToggleFollow = useCallback((userId: number) => {
    setFollowedMap((prev) => {
      const next = !prev[userId]
      return { ...prev, [userId]: next }
    })
  }, [])

  const handleExpectedAction = useCallback(async (item: EnrichedNotification, action: ExpectedActionType) => {
    const wishId = item.bizId
    if (!wishId) return
    if (!item.isRead) {
      setNotifications((prev) => prev.map((n) => (n.id === item.id ? { ...n, isRead: true } : n)))
      fetchUnreadCount()
      try {
        await markAsRead(item.id)
      } catch {
        // optimistic
      }
    }
    // 埋点失败不阻断跳转（转化率数据允许少量丢失）
    try {
      await recordExpectedAction(wishId, action)
    } catch {
      // ignore
    }
    if (action === 'EXTEND') {
      history.push(`/wish/${wishId}?extend=1`)
    } else if (action === 'ADJUST') {
      history.push(`/wish/assistant?wishId=${wishId}`)
    } else {
      history.push(`/wish/capsules/create?wishId=${wishId}`)
    }
  }, [fetchUnreadCount])

  const handleLoadMore = useCallback(async () => {
    const nextPage = page + 1
    setLoadingMore(true)
    await fetchNotifications(nextPage, true, activeTab)
    setPage(nextPage)
    setLoadingMore(false)
  }, [page, fetchNotifications, activeTab])

  const filteredNotifications = notifications

  const tabUnreadCounts = useCallback((): Record<NotificationCategory, number> => {
    const counts: Record<NotificationCategory, number> = { all: 0, interaction: 0, follow: 0, system: 0 }
    for (const n of notifications) {
      if (!n.isRead) {
        counts.all++
        counts[n.category]++
      }
    }
    return counts
  }, [notifications])()

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <h1 className={styles.headerTitle}>
            <BellOutlined style={{ marginRight: 8 }} />
            消息中心
          </h1>
          {unreadCount > 0 && (
            <span className={styles.unreadBadge}>{unreadCount}</span>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button
            type="button"
            className={styles.markAllBtn}
            onClick={() => history.push('/chat')}
          >
            <SendOutlined style={{ marginRight: 4 }} />
            私信
          </button>
          <button
            type="button"
            className={styles.markAllBtn}
            onClick={handleMarkAllRead}
            disabled={unreadCount === 0}
          >
            全部已读
          </button>
        </div>
      </div>

      <div className={styles.tabBar}>
        {TABS.map((tab) => {
          const count = tabUnreadCounts[tab.key]
          return (
            <button
              key={tab.key}
              type="button"
              className={`${styles.tab} ${activeTab === tab.key ? styles.tabActive : ''}`}
              onClick={() => setActiveTab(tab.key)}
            >
              {tab.label}
              {count > 0 && <span className={styles.tabBadge}>{count}</span>}
            </button>
          )
        })}
      </div>

      {loading ? (
        <div className={styles.loadingWrap}>
          <Skeleton variant="list" count={6} />
        </div>
      ) : filteredNotifications.length === 0 ? (
        <div className={styles.emptyWrap}>
          <BellOutlined className={styles.emptyIcon} />
          <span className={styles.emptyText}>
            {activeTab === 'all' && '暂无消息'}
            {activeTab === 'interaction' && '暂无互动消息'}
            {activeTab === 'follow' && '暂无关注消息'}
            {activeTab === 'system' && '暂无系统消息'}
          </span>
        </div>
      ) : (
        <div className={styles.notificationList}>
          {filteredNotifications.map((item) => (
            <div
              key={item.id}
              className={`${styles.notificationItem} ${!item.isRead ? styles.notificationItemUnread : ''}`}
              onClick={() => handleNotificationClick(item)}
            >
              <div className={`${styles.notificationAvatar} ${item.avatarClass}`}>
                {item.icon}
              </div>

              <div className={styles.notificationContent}>
                {renderNotificationText(item)}
                {item.type === 'FOLLOW' && (
                  <FollowDetail
                    item={item}
                    followedMap={followedMap}
                    onToggleFollow={handleToggleFollow}
                  />
                )}
                {item.type === 'CHECKIN_REMINDER' && item.bizType === 'EXPECTED_MANAGEMENT' && item.bizId && (
                  <ExpectedActions item={item} onAction={handleExpectedAction} />
                )}
                {item.type === 'WISH_FULFILL' && item.bizType === 'FULFILLMENT_LEGACY' && item.bizId && (
                  <div className={styles.legacyNotice}>
                    🎉 你的同愿实现了 —— 点击查看 TA 的还愿故事
                  </div>
                )}
              </div>

              <span className={styles.notificationTime}>
                {formatRelativeTime(item.createdAt)}
              </span>
            </div>
          ))}

          {hasMore && (
            <div className={styles.loadMoreWrap}>
              <button
                type="button"
                className={styles.loadMoreBtn}
                onClick={handleLoadMore}
                disabled={loadingMore}
              >
                {loadingMore ? '加载中...' : '加载更多'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
