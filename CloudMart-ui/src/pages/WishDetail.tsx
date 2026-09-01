import { useState, useEffect } from 'react'
import { Empty, Input, Card, Tag, Avatar, Button, Carousel, Timeline, Progress, App, Popconfirm, DatePicker, Modal } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import {
  StarOutlined,
  HeartOutlined,
  MessageOutlined,
  DeleteOutlined,
  CalendarOutlined,
  ArrowLeftOutlined,
  MoonOutlined,
  GiftOutlined,
  TrophyOutlined,
} from '@ant-design/icons'
import { history, useParams, useSearchParams } from 'umi'
import { getWishDetail, deleteWish, getFulfillmentDetail, updateWish, inheritFulfillment } from '@/api/wish'
import type { WishDetail, WishFulfillmentDetail } from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import Skeleton from '@/components/Skeleton'
import WishInteractionBar, { type WishInteractionCounts } from '@/components/WishInteractionBar'
import WishCommentSection from '@/components/WishCommentSection'
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
  const wishId = params.id ?? ''
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetail | null>(null)
  const [fulfillment, setFulfillment] = useState<WishFulfillmentDetail | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const [extendOpen, setExtendOpen] = useState(false)
  const [extendDate, setExtendDate] = useState<Dayjs | null>(null)
  const [extendSaving, setExtendSaving] = useState(false)
  // 传承推送（Sprint 2.7：作者对 FULFILLED 心愿定向推送曾同求用户）
  const [inheritOpen, setInheritOpen] = useState(false)
  const [inheritMessage, setInheritMessage] = useState('')
  const [inheriting, setInheriting] = useState(false)
  const { message } = App.useApp()
  const { user } = useAuthStore()

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await getWishDetail(wishId)
        if (res.data.success) {
          setWish(res.data.data)
          // 已还愿心愿加载还愿故事（公开匿名可见；PRIVATE/TREE_HOLE 仅作者）
          if (res.data.data.status === 'FULFILLED') {
            try {
              const fulfillmentRes = await getFulfillmentDetail(wishId)
              if (fulfillmentRes.data.success) {
                setFulfillment(fulfillmentRes.data.data)
              }
            } catch {
              // 未还愿/已撤回/无权限时静默不展示
            }
          }
        }
      } catch {
        // 错误已由 request 拦截器处理
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [wishId])

  // 预期管理通知「延长预期」深链：作者本人且心愿未完结时打开延期弹窗
  useEffect(() => {
    if (searchParams.get('extend') === '1' && wish && user?.id === wish.authorId
        && (wish.status === 'ACTIVE' || wish.status === 'OVERDUE')) {
      setExtendDate(wish.expectedAt ? dayjs(wish.expectedAt).add(30, 'day') : dayjs().add(30, 'day'))
      setExtendOpen(true)
      setSearchParams(new URLSearchParams())
    }
  }, [searchParams, wish, user, setSearchParams])

  const handleExtendSave = async () => {
    if (!extendDate || !extendDate.isAfter(dayjs())) {
      message.warning('新的预期时间需要晚于现在')
      return
    }
    setExtendSaving(true)
    try {
      const res = await updateWish(wishId, { expectedAt: extendDate.toDate().toISOString() })
      if (res.data.success) {
        message.success('预期已延长，继续加油')
        setExtendOpen(false)
        setWish((prev) => (prev ? { ...prev, expectedAt: extendDate.toDate().toISOString() } : prev))
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setExtendSaving(false)
    }
  }

  const handleInherit = async () => {
    setInheriting(true)
    try {
      const res = await inheritFulfillment(wishId, inheritMessage.trim() || undefined)
      if (res.data.success) {
        message.success(`传承已送达 ${res.data.data?.pushedCount ?? 0} 位同路人`)
        setInheritOpen(false)
        setInheritMessage('')
      }
    } catch (err) {
      const code = (err as { code?: string })?.code
      if (code === 'WISH_ALREADY_INHERITED') message.info('这条心愿已经传承过了')
      else if (code === 'WISH_NOT_FULFILLED') message.warning('心愿还未实现，无法发起传承')
    } finally {
      setInheriting(false)
    }
  }

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

  /** 互动成功后同步心愿计数（服务端返回的最新值） */
  const handleCountsChange = (partial: Partial<WishInteractionCounts>) => {
    setWish((prev) => (prev ? { ...prev, ...partial } : prev))
  }

  /** 评论数变化（发表 +1 / 删除 -1） */
  const handleCommentCountChange = (delta: number) => {
    setWish((prev) =>
      prev ? { ...prev, commentCount: Math.max(0, prev.commentCount + delta) } : prev,
    )
  }

  const gotoLogin = () => history.push('/login')

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
            {(wish.status === 'ACTIVE' || wish.status === 'OVERDUE') && (
              <Button
                type="primary"
                icon={<GiftOutlined />}
                onClick={() => history.push(`/wish/${wishId}/fulfillment`)}
                className={styles.fulfillBtn}
              >
                我要还愿
              </Button>
            )}
            {wish.status === 'FULFILLED' && (
              <Button
                icon={<TrophyOutlined />}
                onClick={() => setInheritOpen(true)}
              >
                传承给同路人
              </Button>
            )}
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

        {/* 还愿故事（Sprint 1.10：已还愿心愿展示，公开心愿匿名可见） */}
        {fulfillment && (
          <Card className={styles.fulfillmentCard} title="🌸 还愿故事">
            <div className={styles.fulfillmentHeader}>
              <div className={styles.author}>
                <Avatar
                  size={32}
                  src={fulfillment.authorAvatar || undefined}
                  icon={<StarOutlined />}
                />
                <span className={styles.authorName}>{fulfillment.authorNickname}</span>
              </div>
              <span className={styles.date}>
                还愿于 {new Date(fulfillment.createdAt).toLocaleString('zh-CN')}
              </span>
            </div>
            <div className={styles.fulfillmentStory}>{fulfillment.story}</div>
            {fulfillment.mediaUrls && fulfillment.mediaUrls.length > 0 && (
              <div className={styles.fulfillmentMedia}>
                {fulfillment.mediaUrls.map((url) => (
                  <img key={url} src={url} alt="fulfillment" className={styles.fulfillmentImage} />
                ))}
              </div>
            )}
            {fulfillment.feeling && (
              <div className={styles.fulfillmentFeeling}>
                <span className={styles.feelingLabel}>💬 感悟</span>
                <span className={styles.feelingText}>{fulfillment.feeling}</span>
              </div>
            )}
          </Card>
        )}

        {/* 树洞入口（Sprint 1.3：作者本人 + 树洞心愿 + 已启用 AI 回复） */}
        {isAuthor && wish.visibility === 'TREE_HOLE' && wish.enableAiReply && (
          <Card className={styles.interactionCard}>
            <Button
              type="primary"
              block
              icon={<MoonOutlined />}
              onClick={() => history.push(`/wish/${wishId}/tree-hole`)}
              className={styles.treeHoleBtn}
            >
              进入树洞 · 让守护者陪你聊聊
            </Button>
          </Card>
        )}

        {/* 互动按钮组（点亮/同求/祝福，Sprint 1.2） */}
        <Card className={styles.interactionCard}>
          <WishInteractionBar
            wishId={wishId}
            counts={{
              lightCount: wish.lightCount,
              sameWishCount: wish.sameWishCount,
              blessCount: wish.blessCount,
              anonStarCount: wish.anonStarCount,
            }}
            isLoggedIn={Boolean(user)}
            onCountsChange={handleCountsChange}
            onRequireLogin={gotoLogin}
          />
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

        {/* 评论模块（Sprint 1.2） */}
        <Card className={styles.commentCard}>
          <WishCommentSection
            wishId={wishId}
            commentCount={wish.commentCount}
            isLoggedIn={Boolean(user)}
            currentUserId={user?.id}
            onCountChange={handleCommentCountChange}
            onRequireLogin={gotoLogin}
          />
        </Card>
      </div>
      <Modal
        open={inheritOpen}
        title="传承给同路人"
        okText="发送传承"
        cancelText="取消"
        onOk={handleInherit}
        onCancel={() => setInheritOpen(false)}
        confirmLoading={inheriting}
      >
        <p style={{ marginBottom: 12 }}>
          你的心愿已实现 ✨ 把这份力量传给曾与你同求的人（附言可留空）：
        </p>
        <Input.TextArea
          value={inheritMessage}
          onChange={(e) => setInheritMessage(e.target.value)}
          maxLength={500}
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="如：谢谢你们陪我一起许下这个愿望，希望你也可以"
        />
      </Modal>
      <Modal
        open={extendOpen}
        title="延长预期"
        okText="保存新预期"
        cancelText="取消"
        onOk={handleExtendSave}
        onCancel={() => setExtendOpen(false)}
        confirmLoading={extendSaving}
      >
        <p style={{ marginBottom: 12 }}>为这个心愿设定一个新的预期完成时间（状态保持进行中）：</p>
        <DatePicker
          showTime
          value={extendDate}
          onChange={setExtendDate}
          style={{ width: '100%' }}
          disabledDate={(d) => d.isBefore(dayjs(), 'day')}
        />
      </Modal>
      <WishBGM />
    </div>
  )
}
