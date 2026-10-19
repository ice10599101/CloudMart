import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Empty, App, Typography } from 'antd'
import { ReloadOutlined, StarFilled } from '@ant-design/icons'
import { history } from 'umi'
import { getCapsuleWall, type CapsuleWallItem } from '@/api/wish'
import listStyles from './CapsuleList.module.css'
import styles from './CapsuleWall.module.css'

const PAGE_SIZE = 10

/**
 * R17 公共胶囊墙（用户站 Web 入口）。
 * 匿名公共墙：只展示作者主动发布且当前仍在墙的胶囊，不暴露作者身份与媒体附件。
 */
export default function CapsuleWall() {
    const { message } = App.useApp()
    const [items, setItems] = useState<CapsuleWallItem[]>([])
    const [loading, setLoading] = useState(true)
    const [loadingMore, setLoadingMore] = useState(false)
    const [page, setPage] = useState(1)
    const [total, setTotal] = useState(0)
    const [hasMore, setHasMore] = useState(false)

    const fetchPage = useCallback(async (nextPage: number, append: boolean) => {
        if (append) setLoadingMore(true)
        try {
            const res = await getCapsuleWall(nextPage, PAGE_SIZE)
            if (!res.data.success) {
                message.error(res.data.error?.message ?? '加载失败')
                return
            }
            const data = res.data.data
            setItems((prev) => (append ? [...prev, ...data.records] : data.records))
            setTotal(data.total ?? 0)
            setHasMore(nextPage * PAGE_SIZE < (data.total ?? 0))
            setPage(nextPage)
        } catch {
            // 错误已由 request 拦截器处理
        } finally {
            setLoading(false)
            setLoadingMore(false)
        }
    }, [message])

    useEffect(() => {
        setLoading(true)
        fetchPage(1, false)
    }, [fetchPage])

    return (
        <div className={`${listStyles.container} wish-universe-theme`}>
            <div className={listStyles.pageWrap}>
                <div className={styles.hero}>
                    <h1 className={styles.title}>
                        <StarFilled /> 公共胶囊墙
                    </h1>
                    <Typography.Paragraph type="secondary" className={styles.subtitle}>
                        时间到的那一刻，有些话值得被更多人看见。这里每颗胶囊都是匿名展出的时光信笺。
                    </Typography.Paragraph>
                    <Button onClick={() => history.push('/wish/capsules')}>返回我的胶囊</Button>
                </div>

                {loading ? (
                    <Card loading className={styles.wallCard} />
                ) : items.length === 0 ? (
                    <Card className={styles.emptyCard}>
                        <Empty description="墙上还没有胶囊，去申请第一颗吧" image={Empty.PRESENTED_IMAGE_SIMPLE}>
                            <Button type="primary" ghost onClick={() => history.push('/wish/capsules')}>
                                去看我的胶囊
                            </Button>
                        </Empty>
                    </Card>
                ) : (
                    <div className={styles.wallList}>
                        {items.map((item) => (
                            <Card key={item.capsuleId} className={styles.wallCard}>
                                <Typography.Text strong className={styles.cardTitle}>
                                    {item.title}
                                </Typography.Text>
                                <Typography.Paragraph className={styles.cardContent}>{item.content}</Typography.Paragraph>
                                <Typography.Text type="secondary" className={styles.cardMeta}>
                                    🕐 {item.openedAt ? new Date(item.openedAt).toLocaleDateString('zh-CN') : ''} 开启
                                </Typography.Text>
                            </Card>
                        ))}
                        <Typography.Text type="secondary" className={styles.totalHint}>
                            共 {total} 颗时光信笺
                        </Typography.Text>
                        {hasMore && (
                            <Button
                                block
                                icon={<ReloadOutlined />}
                                loading={loadingMore}
                                onClick={() => fetchPage(page + 1, true)}
                            >
                                加载更多
                            </Button>
                        )}
                    </div>
                )}
            </div>
        </div>
    )
}
