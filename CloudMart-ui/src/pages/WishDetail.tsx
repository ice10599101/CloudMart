import { useState, useEffect } from 'react'
import { Empty, Card, Tag, Avatar, Button, Carousel, Timeline, Progress, App, Popconfirm } from 'antd'
import {
  StarOutlined,
  HeartOutlined,
  MessageOutlined,
  DeleteOutlined,
  CalendarOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons'
import { history, useParams } from 'umi'
import { getWishDetail, deleteWish } from '@/api/wish'
import type { WishDetail } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import Skeleton from '@/components/Skeleton'
import styles from './WishDetail.module.css'
import WishBGM from '@/components/WishBGM'

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

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '进行中',
  OVERDUE: '已过期',
  FULFILLING: '还愿中',
  FULFILLED: '已还愿',
  ARCHIVED: '已归档',
}

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

export default function WishDetail() {
  const params = useParams<{ id: string }>()
  const wishId = Number(params.id)
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetail | null>(null)
  const { message } = App.useApp()
  const { user } = useAuthStore()

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await getWishDetail(wishId)
        if (res.data.success) {
          setWish(res.data.data)
        }
      } catch {
        // 错误已由 request 拦截器处理
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [wishId])

  const handleDelete = async () => {
    try {
      const res = await deleteWish(wishId)
      if (res.data.success) {
        message.success('心愿已删除')
        history.push('/wish/my')
      }
    } catch {
      // 错误已由 request 拦截器处理
    }
  }

  if (loading) {
    return (
      <div className={`${styles.loadingContainer} wish-universe-theme`}>
        <Skeleton variant="wish-detail" />
      </div>
    )
  }

  if (!wish) {
    return (
      <div className={`${styles.emptyContainer} wish-universe-theme`}>
        <Empty description="心愿不存在或已被删除" />
        <Button onClick={() => history.push('/wish/list')}>返回心愿广场</Button>
      </div>
    )
  }

  const isAuthor = user?.id === wish.authorId

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.backBar}>
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => history.back()}
          className={styles.backBtn}
        >
          返回
        </Button>
        {isAuthor && (
          <div className={styles.actionBtns}>
            <Popconfirm
              title="确定删除这个心愿吗？"
              description="删除后不可恢复"
              onConfirm={handleDelete}
              okText="确定"
              cancelText="取消"
            >
              <Button danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </div>
        )}
      </div>

      <div className={styles.content}>
        {/* 媒体轮播 */}
        {wish.mediaUrls && wish.mediaUrls.length > 0 && (
          <Card className={styles.mediaCard}>
            <Carousel autoplay className={styles.carousel}>
              {wish.mediaUrls.map(url => (
                <div key={url}>
                  <img src={url} alt="media" className={styles.mediaImage} />
                </div>
              ))}
            </Carousel>
          </Card>
        )}

        {/* 心愿信息 */}
        <Card className={styles.infoCard}>
          <div className={styles.header}>
            <div className={styles.tags}>
              <Tag color={FRUIT_COLORS[wish.fruitType]}>
                {FRUIT_LABELS[wish.fruitType]}
              </Tag>
              <Tag>{STATUS_LABELS[wish.status] || wish.status}</Tag>
              {wish.tags?.map(tag => (
                <Tag key={tag} className={styles.tag}>{tag}</Tag>
              ))}
            </div>
            <h1 className={styles.title}>{wish.title}</h1>
            <div className={styles.meta}>
              <div className={styles.author}>
                <Avatar
                  size={32}
                  src={wish.authorAvatar || undefined}
                  icon={<StarOutlined />}
                />
                <span className={styles.authorName}>{wish.authorNickname}</span>
              </div>
              <span className={styles.date}>
                {new Date(wish.createdAt).toLocaleString('zh-CN')}
              </span>
            </div>
          </div>

          <div className={styles.description}>
            {wish.description}
          </div>

          {wish.expectedAt && (
            <div className={styles.expectedAt}>
              <CalendarOutlined /> 预计完成时间：
              {new Date(wish.expectedAt).toLocaleDateString('zh-CN')}
            </div>
          )}

          {/* 互动统计 */}
          <div className={styles.stats}>
            <div className={styles.statItem}>
              <HeartOutlined />
              <span>{formatCount(wish.supportCount)} 互动</span>
            </div>
            <div className={styles.statItem}>
              <MessageOutlined />
              <span>{formatCount(wish.commentCount)} 评论</span>
            </div>
          </div>
        </Card>

        {/* 进度 */}
        {wish.progress && (
          <Card className={styles.progressCard} title="心愿进度">
            <div className={styles.progressContent}>
              <Progress
                percent={wish.progress.percentage}
                strokeColor={FRUIT_COLORS[wish.fruitType]}
                size={['100%', 12]}
              />
              <div className={styles.progressDetail}>
                <span>当前进度：{wish.progress.currentValue} / {wish.progress.targetValue}</span>
                <span>打卡天数：{wish.checkinDays} 天</span>
              </div>
            </div>
          </Card>
        )}

        {/* 成长记录 */}
        {wish.growthRecords && wish.growthRecords.length > 0 && (
          <Card className={styles.growthCard} title="成长记录">
            <Timeline
              items={wish.growthRecords.map(record => ({
                color: FRUIT_COLORS[wish.fruitType],
                children: (
                  <div className={styles.growthItem}>
                    <div className={styles.growthContent}>{record.content}</div>
                    {record.mediaUrls?.length > 0 && (
                      <div className={styles.growthMedia}>
                        {record.mediaUrls.map(url => (
                          <img key={url} src={url} alt="growth" className={styles.growthImage} />
                        ))}
                      </div>
                    )}
                    <div className={styles.growthDate}>
                      {new Date(record.createdAt).toLocaleString('zh-CN')}
                      {record.progressDelta > 0 && (
                        <Tag color="green" className={styles.deltaTag}>
                          +{record.progressDelta}
                        </Tag>
                      )}
                    </div>
                  </div>
                ),
              }))}
            />
          </Card>
        )}
      </div>
      <WishBGM />
    </div>
  )
}
