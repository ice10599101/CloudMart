import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Empty, App, Popconfirm, Tabs, Tag, Typography } from 'antd'
import { PlusOutlined, ReloadOutlined, MailOutlined } from '@ant-design/icons'
import { history } from 'umi'
import { cancelCapsule, listMyCapsules, type CapsuleItem, type CapsuleStatus } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import { reportTimezoneIfNeeded } from '@/utils/wish-timezone'
import styles from './CapsuleList.module.css'
import WishBGM from '@/components/WishBGM'

const PAGE_SIZE = 20

const STATUS_META: Record<CapsuleStatus, { label: string; color: string; emoji: string }> = {
    SEALED: { label: '封印中', color: 'blue', emoji: '🔒' },
    AVAILABLE: { label: '待开启', color: 'gold', emoji: '🎁' },
    OPENED: { label: '已开启', color: 'green', emoji: '💌' },
    CANCELLED: { label: '已取消', color: 'default', emoji: '🌑' },
}

/** 距开启倒计时文案（SEALED）/ 已到期时长（OPENED） */
function formatRemaining(openAt: string): string {
    const diffMs = new Date(openAt).getTime() - Date.now()
    if (diffMs <= 0) return '已到期'
    const days = Math.floor(diffMs / 86400000)
    const hours = Math.floor((diffMs % 86400000) / 3600000)
    const minutes = Math.floor((diffMs % 3600000) / 60000)
    if (days > 0) return `${days} 天 ${hours} 小时后开启`
    if (hours > 0) return `${hours} 小时 ${minutes} 分后开启`
    return `${minutes} 分后开启`
}

export default function CapsuleList() {
    const [loading, setLoading] = useState(true)
    const [loadingMore, setLoadingMore] = useState(false)
    const [cancellingId, setCancellingId] = useState<number | string | null>(null)
    const [items, setItems] = useState<CapsuleItem[]>([])
    const [statusFilter, setStatusFilter] = useState<CapsuleStatus>('SEALED')
    const [cursor, setCursor] = useState<string | null>(null)
    const [hasMore, setHasMore] = useState(false)
    const { message } = App.useApp()
    const { user } = useAuthStore()

    const fetchPage = useCallback(
        async (status: CapsuleStatus, nextCursor: string | null) => {
            const res = await listMyCapsules({ status, cursor: nextCursor ?? undefined, pageSize: PAGE_SIZE })
            if (!res.data.success) return
            const page = res.data.data
            const meta = res.data.meta
            setItems((prev) => (nextCursor ? [...prev, ...page] : page))
            setCursor(meta?.nextCursor ?? null)
            setHasMore(meta?.hasMore ?? false)
        },
        []
    )

    useEffect(() => {
        reportTimezoneIfNeeded()
        if (!user) {
            message.warning('请先登录后查看你的胶囊')
            history.push('/login?redirect=/wish/capsules')
            return
        }
        let cancelled = false
        setLoading(true)
        setItems([])
        setCursor(null)
        fetchPage(statusFilter, null).finally(() => {
            if (!cancelled) setLoading(false)
        })
        return () => {
            cancelled = true
        }
    }, [user, statusFilter])

    const handleLoadMore = async () => {
        if (!hasMore || loadingMore) return
        setLoadingMore(true)
        try {
            await fetchPage(statusFilter, cursor)
        } catch {
            // 错误已由 request 拦截器处理
        } finally {
            setLoadingMore(false)
        }
    }

    const handleCancel = async (id: number | string) => {
        setCancellingId(id)
        try {
            const res = await cancelCapsule(id)
            if (res.data.success) {
                message.success('胶囊已取消')
                setItems((prev) => prev.filter((c) => c.id !== id))
            }
        } catch {
            // 错误已由 request 拦截器处理
        } finally {
            setCancellingId(null)
        }
    }

    const renderCard = (capsule: CapsuleItem) => {
        const meta = STATUS_META[capsule.status]
        const cancellable = capsule.status === 'SEALED' || capsule.status === 'AVAILABLE'
        return (
            <Card
                key={capsule.id}
                className={styles.capsuleCard}
                hoverable
                onClick={() => history.push(`/wish/capsules/${capsule.id}`)}
                aria-label={`胶囊：${capsule.title}`}
            >
                <div className={styles.cardHeader}>
                    <span className={styles.cardEmoji}>{meta.emoji}</span>
                    <div className={styles.cardTitleWrap}>
                        <Typography.Text strong ellipsis className={styles.cardTitle}>
                            {capsule.title}
                        </Typography.Text>
                        <span className={styles.cardTime}>封存于 {new Date(capsule.createdAt).toLocaleString()}</span>
                    </div>
                    <Tag color={meta.color} className={styles.statusTag}>
                        {meta.label}
                    </Tag>
                </div>
                <div className={styles.cardFooter}>
                    {capsule.status === 'SEALED' && <span className={styles.countdown}>⏳ {formatRemaining(capsule.openAt)}</span>}
                    {capsule.status === 'AVAILABLE' && <span className={styles.readyHint}>🎁 已到期，可以拆开了</span>}
                    {capsule.status === 'OPENED' && (
                        <span className={styles.openedHint}>💌 开启于 {capsule.openedAt ? new Date(capsule.openedAt).toLocaleString() : '-'}</span>
                    )}
                    {capsule.status === 'CANCELLED' && <span className={styles.cancelledHint}>内容已永久封存，无法开启</span>}
                    {cancellable && (
                        <Popconfirm
                            title="确定取消这个胶囊吗？"
                            description="取消后封存内容将永久不可开启"
                            okText="取消胶囊"
                            cancelText="保留"
                            onConfirm={(e) => {
                                e?.stopPropagation()
                                handleCancel(capsule.id)
                            }}
                            onCancel={(e) => e?.stopPropagation()}
                        >
                            <Button
                                size="small"
                                type="text"
                                danger
                                loading={cancellingId === capsule.id}
                                onClick={(e) => e.stopPropagation()}
                                className={styles.cancelBtn}
                            >
                                取消
                            </Button>
                        </Popconfirm>
                    )}
                </div>
            </Card>
        )
    }

    return (
        <div className={`${styles.container} wish-universe-theme`}>
            <div className={styles.pageWrap}>
                <div className={styles.headerRow}>
                    <h1 className={styles.pageTitle}>
                        <MailOutlined /> 我的时间胶囊
                    </h1>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => history.push('/wish/capsules/create')}>
                        封存新胶囊
                    </Button>
                </div>

                <Tabs
                    activeKey={statusFilter}
                    onChange={(key) => setStatusFilter(key as CapsuleStatus)}
                    items={[
                        { key: 'SEALED', label: `🔒 封印中` },
                        { key: 'AVAILABLE', label: '🎁 待开启' },
                        { key: 'OPENED', label: '💌 已开启' },
                        { key: 'CANCELLED', label: '🌑 已取消' },
                    ]}
                    className={styles.tabs}
                />

                {loading ? (
                    <Card loading className={styles.capsuleCard} />
                ) : items.length === 0 ? (
                    <Card className={styles.emptyCard}>
                        <Empty
                            description={statusFilter === 'SEALED' ? '还没有封印中的胶囊，写一封给未来的自己吧' : '这里还没有胶囊'}
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                        >
                            {statusFilter === 'SEALED' && (
                                <Button type="primary" ghost icon={<PlusOutlined />} onClick={() => history.push('/wish/capsules/create')}>
                                    封存第一个胶囊
                                </Button>
                            )}
                        </Empty>
                    </Card>
                ) : (
                    <div className={styles.cardList}>
                        {items.map(renderCard)}
                        {hasMore && (
                            <Button block icon={<ReloadOutlined />} loading={loadingMore} onClick={handleLoadMore} className={styles.loadMore}>
                                加载更多
                            </Button>
                        )}
                    </div>
                )}
            </div>
            <WishBGM />
        </div>
    )
}
