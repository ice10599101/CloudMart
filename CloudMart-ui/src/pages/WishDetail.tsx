import { useRef, useState, useEffect } from 'react'
import { Empty, Input, Card, Tag, Button, Carousel, Timeline, Progress, App, Popconfirm, DatePicker, Modal, Select, Upload, InputNumber } from 'antd'
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
  ShareAltOutlined,
  BookOutlined,
  BookFilled,
  PlusOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { history, useParams, useSearchParams } from 'umi'
import CommentToolbar, { insertAtCursor } from '@/components/CommentToolbar'
import DOMPurify from 'dompurify'
import RichText from '@/components/RichText'
import {
  withdrawFulfillment,
  updateGrowthRecord, deleteGrowthRecord,
  getWishDetail, deleteWish, getFulfillmentDetail, updateWish, inheritFulfillment,
  checkinWish, addGrowthRecord, collectWish, uncollectWish, getWishCollectionStatus, sparkWish,
  archiveWish, unarchiveWish, listWishGoals, createWishGoal, updateWishGoal, deleteWishGoal,
  reorderWishGoals, submitWishReport, listWishGrowthTimeline,
  type WishGoalStep, type WishGrowthTimelineItem,
} from '@/api/wish'
import type { WishDetail as WishDetailData, WishFulfillmentDetail } from '@/api/wish'
import { uploadFile } from '@/api/file'
import { recordBrowseHistory } from '@/api/community'
import { useAuthStore } from '@/stores/auth'
import Skeleton from '@/components/Skeleton'
import WishInteractionBar, { type WishInteractionCounts } from '@/components/WishInteractionBar'
import WishBlessList from '@/components/WishBlessList'
import WishCommentSection from '@/components/WishCommentSection'
import ShareCardModal from '@/components/ShareCardModal'
import CheckinCalendar from '@/components/CheckinCalendar'
import WateringEffect from '@/components/WateringEffect'
import styles from './WishDetail.module.css'
import WishBGM from '@/components/WishBGM'
import GiftSection from '@/components/GiftSection'
import DecoratedAvatar from '@/components/DecoratedAvatar'

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

/** 历史心愿描述为纯文本（含 \n 换行），新版编辑器产出富文本 HTML；按是否含常见 HTML 标签分流渲染 */
const RICH_TEXT_PATTERN = /<\/?(p|div|br|h[1-6]|ul|ol|li|blockquote|pre|img|table|strong|em|u|s|span|a|code)\b/i

function formatCount(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

export default function WishDetail() {
  const params = useParams<{ id: string }>()
  const wishId = params.id ?? ''
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetailData | null>(null)
  const [fulfillment, setFulfillment] = useState<WishFulfillmentDetail | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const [extendOpen, setExtendOpen] = useState(false)
  const [extendDate, setExtendDate] = useState<Dayjs | null>(null)
  const [extendSaving, setExtendSaving] = useState(false)
  // 传承推送（Sprint 2.7：作者对 FULFILLED 心愿定向推送曾同求用户）
  const [inheritOpen, setInheritOpen] = useState(false)
  const [shareOpen, setShareOpen] = useState(false)
  // 打卡成功浇水动效（Sprint 1.3 体验要求）：记录触发时的 fruitType
  const [wateringFruit, setWateringFruit] = useState<'GLOW' | 'RESONANCE' | 'BLOOM' | 'SPARK' | null>(null)
  const [inheritMessage, setInheritMessage] = useState('')
  const [inheriting, setInheriting] = useState(false)
  // 星火永久收藏（文档 2.3，仅作者对 FULFILLED+BLOOM 心愿）
  const [sparkSaving, setSparkSaving] = useState(false)
  // 每日打卡（仅作者 + ACTIVE；成功后本地记录今日已打卡，刷新页面后重打会 409 由后端幂等兜底）
  const [checkinOpen, setCheckinOpen] = useState(false)
  const [checkinContent, setCheckinContent] = useState('')
  const [checkinSaving, setCheckinSaving] = useState(false)
  // 收藏状态（非作者）：null=回显中
  const [collected, setCollected] = useState<boolean | null>(null)
  const [collectSaving, setCollectSaving] = useState(false)
  // 成长记录（仅作者）
  const [growthOpen, setGrowthOpen] = useState(false)
  const [growthType, setGrowthType] = useState('TEXT')
  const [growthContent, setGrowthContent] = useState('')
  const growthInputRef = useRef<any>(null)
  const inheritInputRef = useRef<any>(null)
  const checkinInputRef = useRef<any>(null)
  const [growthDelta, setGrowthDelta] = useState<number | undefined>()
  const [growthMedia, setGrowthMedia] = useState<string[]>([])
  const [growthSaving, setGrowthSaving] = useState(false)
  const [refreshTick, setRefreshTick] = useState(0)
  const [blessTick, setBlessTick] = useState(0)
  const [checkedInToday, setCheckedInToday] = useState(false)
  // ---- 心愿 v2：目标清单 / 归档 / 举报 / 成长完整时间轴 ----
  const [goals, setGoals] = useState<WishGoalStep[]>([])
  const [goalsOpen, setGoalsOpen] = useState(false)
  const [goalsLoading, setGoalsLoading] = useState(false)
  const [goalTitle, setGoalTitle] = useState('')
  const [goalSaving, setGoalSaving] = useState(false)
  const [archiveSaving, setArchiveSaving] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const [reportReason, setReportReason] = useState('')
  const [reportSaving, setReportSaving] = useState(false)
  const [timelineItems, setTimelineItems] = useState<WishGrowthTimelineItem[]>([])
  const [timelineCursor, setTimelineCursor] = useState<string | null>(null)
  const [timelineHasMore, setTimelineHasMore] = useState(false)
  const [timelineLoading, setTimelineLoading] = useState(false)

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
  }, [wishId, refreshTick])

  /** 浏览器足迹上报：本次页面打开仅上报一次（refreshTick 刷新不重复上报） */
  const browseReportedRef = useRef(false)
  useEffect(() => {
    if (browseReportedRef.current || !user?.id || !wish) return
    browseReportedRef.current = true
    void recordBrowseHistory({
      targetType: 'WISH',
      targetId: wish.id,
      title: wish.title,
      cover: wish.mediaUrls?.[0],
    }).catch(() => {})
  }, [user?.id, wish])

  /** 收藏/取消收藏（非作者） */
  const handleCollectToggle = async () => {
    setCollectSaving(true)
    try {
      if (collected) {
        await uncollectWish(wishId)
        setCollected(false)
        message.success('已取消收藏')
      } else {
        await collectWish(wishId)
        setCollected(true)
        message.success('已收藏，可在我的收藏查看')
      }
    } catch {
      // 业务错误已由 request 拦截器提示
    } finally {
      setCollectSaving(false)
    }
  }

  /** 提交成长记录：成功后刷新详情（时间线 + 进度来自服务端） */
  const handleGrowthSubmit = async () => {
    if (!growthContent.trim()) {
      message.warning('请填写成长记录内容')
      return
    }
    setGrowthSaving(true)
    try {
      const res = await addGrowthRecord(wishId, {
        type: growthType,
        content: growthContent.trim(),
        mediaUrls: growthMedia.length > 0 ? growthMedia : undefined,
        progressDelta: growthDelta,
      })
      if (res.data.success) {
        message.success('成长记录已添加')
        setGrowthOpen(false)
        setGrowthContent('')
        setGrowthDelta(undefined)
        setGrowthMedia([])
        setRefreshTick((t) => t + 1)
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setGrowthSaving(false)
    }
  }

  /** 上传成长媒体（图片/视频） */
  const growthCustomRequest = (options: { file: unknown; onSuccess: (body: unknown) => void; onError: () => void }) => {
    const file = options.file as File
    uploadFile(file)
      .then((res) => {
        const uploadedUrl = res.data.data?.url
        if (res.data.success && uploadedUrl) {
          setGrowthMedia((prev) => [...prev, uploadedUrl])
          options.onSuccess(res.data.data)
        } else {
          options.onError()
        }
      })
      .catch(() => options.onError())
  }

  // 收藏状态回显（登录的非作者用户；作者不可收藏自己的心愿）  // 收藏状态回显（登录的非作者用户；作者不可收藏自己的心愿）
  useEffect(() => {
    if (!user || !wish || user.id === wish.authorId) return
    getWishCollectionStatus(wishId)
      .then((res) => setCollected(res.data.data === true))
      .catch(() => setCollected(false))
  }, [user, wish, wishId])

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

  /** 设为星火永久收藏（文档 2.3：仅作者对 FULFILLED+BLOOM 心愿，幂等） */
  const handleSpark = async () => {
    setSparkSaving(true)
    try {
      const res = await sparkWish(wishId)
      if (res.data.success) {
        message.success('已设为星火永久收藏')
        setWish((prev) => (prev ? { ...prev, fruitType: 'SPARK' } : prev))
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setSparkSaving(false)
    }
  }

  /** 互动成功后同步心愿计数（服务端返回的最新值） */
  const handleCountsChange = (partial: Partial<WishInteractionCounts>) => {
    setWish((prev) => (prev ? { ...prev, ...partial } : prev))
  }

  // ---- 心愿 v2 交互 ----
  const loadGoals = async () => {
    setGoalsLoading(true)
    try {
      const res = await listWishGoals(wishId)
      if (res.data.success) setGoals(res.data.data ?? [])
    } finally {
      setGoalsLoading(false)
    }
  }

  // T22：还愿撤回（API 封装此前无页面接线；作者本人入口，后端状态机校验：
  // 已进入社区流转/审核中的撤回会被拒绝并回显原因，撤回成功刷新详情）
  const handleWithdrawFulfillment = async () => {
    try {
      const res = await withdrawFulfillment(wishId)
      if (res.data.success) {
        message.success('还愿已撤回，可重新提交')
        setRefreshTick((tick) => tick + 1)
      }
    } catch (error) {
      const code = (error as { response?: { data?: { error?: { code?: string; message?: string } } } })
        ?.response?.data?.error
      message.warning(code?.message ?? '撤回失败（可能已进入社区流转），已刷新')
      setRefreshTick((tick) => tick + 1)
    }
  }

  // T22：成长记录编辑/删除（API 封装此前无页面接线；仅本人可见入口，
  // 后端归属校验兜底）。编辑用 prompt 轻量交互——富文本编辑器接入随 T22 后续批次
  const handleEditGrowth = async (record: { id: number | string; content: string }) => {
    const content = window.prompt('编辑成长记录内容：', record.content)
    if (content === null || !content.trim() || content === record.content) return
    try {
      const res = await updateGrowthRecord(wishId, record.id, { content: content.trim() })
      if (res.data.success) {
        message.success('成长记录已更新')
        setRefreshTick((tick) => tick + 1)
      }
    } catch {
      message.warning('更新失败（记录可能已被修改），已刷新')
      setRefreshTick((tick) => tick + 1)
    }
  }

  const handleDeleteGrowth = async (record: { id: number | string }) => {
    try {
      const res = await deleteGrowthRecord(wishId, record.id)
      if (res.data.success) {
        message.success('成长记录已删除')
        setRefreshTick((tick) => tick + 1)
      }
    } catch {
      message.warning('删除失败，已刷新')
      setRefreshTick((tick) => tick + 1)
    }
  }

  const handleAddGoal = async () => {
    if (!goalTitle.trim()) {
      message.warning('请填写步骤标题')
      return
    }
    setGoalSaving(true)
    try {
      const res = await createWishGoal(wishId, { title: goalTitle.trim(), sortOrder: goals.length })
      if (res.data.success) {
        setGoalTitle('')
        loadGoals()
      }
    } finally {
      setGoalSaving(false)
    }
  }

  const toggleGoalDone = async (goal: WishGoalStep) => {
    // T12：完成/恢复（恢复清理完成时间，后端首次完成事实不被重写）
    const nextStatus = goal.status === 'COMPLETED' ? 'IN_PROGRESS' : 'COMPLETED'
    const res = await updateWishGoal(goal.id, { status: nextStatus, version: goal.version })
    if (res.data.success) loadGoals()
  }

  const removeGoal = async (goal: WishGoalStep) => {
    try {
      const res = await deleteWishGoal(goal.id, goal.version)
      if (res.data.success) loadGoals()
    } catch {
      message.warning('步骤已被其他设备修改，已刷新最新状态')
      loadGoals()
    }
  }

  const moveGoal = async (index: number, direction: -1 | 1) => {
    const target = index + direction
    if (target < 0 || target >= goals.length) return
    const next = [...goals]
    ;[next[index], next[target]] = [next[target], next[index]]
    // T12：全集显式版本项（version=0 新建目标也参与 CAS），冲突整批刷新
    const items = next.map((goal, order) => ({
      goalId: goal.id,
      version: goal.version,
      sortOrder: order,
    }))
    try {
      const res = await reorderWishGoals(wishId, items)
      if (res.data.success) loadGoals()
    } catch {
      message.warning('排序冲突：步骤已被其他设备修改，已刷新最新状态')
      loadGoals()
    }
  }

  const handleArchive = async () => {
    setArchiveSaving(true)
    try {
      const res = await archiveWish(wishId, {})
      if (res.data.success) {
        message.success('心愿已归档（停止提醒与增长写入，可随时取消归档）')
        setRefreshTick((tick) => tick + 1)
      }
    } finally {
      setArchiveSaving(false)
    }
  }

  const handleUnarchive = async () => {
    setArchiveSaving(true)
    try {
      const res = await unarchiveWish(wishId)
      if (res.data.success) {
        message.success('已取消归档，心愿恢复到归档前状态')
        setRefreshTick((tick) => tick + 1)
      }
    } finally {
      setArchiveSaving(false)
    }
  }

  const handleReportWish = async () => {
    if (!reportReason.trim()) {
      message.warning('请填写举报说明（必填）')
      return
    }
    setReportSaving(true)
    try {
      const res = await submitWishReport({
        targetType: 'WISH',
        targetId: wishId,
        reasonCode: 'OTHER',
        description: reportReason.trim(),
      })
      if (res.data.success) {
        message.success('举报已提交，处理进度可在设置页「我的举报与申诉」查看')
        setReportOpen(false)
        setReportReason('')
      }
    } finally {
      setReportSaving(false)
    }
  }

  const loadTimeline = async (reset: boolean) => {
    setTimelineLoading(true)
    try {
      const res = await listWishGrowthTimeline(wishId, {
        cursor: reset ? undefined : (timelineCursor ?? undefined),
        pageSize: 20,
      })
      if (res.data.success) {
        const items = res.data.data ?? []
        setTimelineItems((prev) => (reset ? items : [...prev, ...items]))
        setTimelineCursor(items.length > 0 ? String(items[items.length - 1].id) : null)
        setTimelineHasMore(items.length >= 20)
      }
    } finally {
      setTimelineLoading(false)
    }
  }

  /** 评论数变化（发表 +1 / 删除 -1） */
  const handleCommentCountChange = (delta: number) => {
    setWish((prev) =>
      prev ? { ...prev, commentCount: Math.max(0, prev.commentCount + delta) } : prev,
    )
  }

  const gotoLogin = () => history.push('/login')

  /** 提交每日打卡：成功后刷新详情（打卡天数/连续打卡来自服务端聚合） */
  const handleCheckinSubmit = async () => {
    setCheckinSaving(true)
    try {
      const res = await checkinWish(wishId, checkinContent.trim() || undefined)
      if (res.data.success) {
        const { currentStreak, starlightCredited } = res.data.data
        setWateringFruit(wish?.fruitType ?? null)
        window.setTimeout(() => setWateringFruit(null), 1800)
        message.success(`打卡成功！已连续 ${currentStreak} 天，星光 +${starlightCredited} ✨`)
        setCheckinOpen(false)
        setCheckinContent('')
        setCheckedInToday(true)
        const detailRes = await getWishDetail(wishId)
        if (detailRes.data.success) {
          setWish(detailRes.data.data)
        }
      }
    } catch {
      // 409（今日已打卡/状态冲突）等业务错误已由 request 拦截器统一提示
    } finally {
      setCheckinSaving(false)
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
        {!isAuthor && collected !== null && (
          <Button
            type="text"
            icon={collected ? <BookFilled style={{ color: FRUIT_COLORS[wish.fruitType] }} /> : <BookOutlined />}
            loading={collectSaving}
            onClick={handleCollectToggle}
          >
            {collected ? '已收藏' : '收藏'}
          </Button>
        )}
        {!isAuthor && (
          <Button type="text" icon={<WarningOutlined />} onClick={() => setReportOpen(true)}>
            举报
          </Button>
        )}
        {isAuthor && (
          <div className={styles.actionBtns}>
            {(wish.status === 'ACTIVE' || wish.status === 'OVERDUE') && (
              <Button
                icon={<PlusOutlined />}
                onClick={() => setGrowthOpen(true)}
              >
                记录成长
              </Button>
            )}
            {wish.status === 'ACTIVE' && (
              <Button
                icon={<CalendarOutlined />}
                disabled={checkedInToday}
                onClick={() => setCheckinOpen(true)}
              >
                {checkedInToday ? '今日已打卡' : '每日打卡'}
              </Button>
            )}
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
            {/* N03 生命周期：归档（停止提醒与增长写入）/ 取消归档（按归档前状态恢复） */}
            {(wish.status === 'ACTIVE' || wish.status === 'OVERDUE') && (
              <Popconfirm
                title="归档这条心愿？"
                description="归档后停止提醒与增长写入，可随时取消归档恢复"
                onConfirm={handleArchive}
                okText="归档"
                cancelText="取消"
              >
                <Button loading={archiveSaving}>
                  归档
                </Button>
              </Popconfirm>
            )}
            {wish.status === 'ARCHIVED' && (
              <Button type="primary" ghost loading={archiveSaving} onClick={handleUnarchive}>
                取消归档
              </Button>
            )}
            {/* 星火永久收藏（文档 2.3：FULFILLED+BLOOM 可设置；SPARK 展示已收藏态） */}
            {wish.status === 'FULFILLED' && wish.fruitType === 'BLOOM' && (
              <Popconfirm
                title="设为星火永久收藏？"
                description="心愿将以星火形态在世界生命树永久展示，可被他人收藏到收藏馆"
                onConfirm={handleSpark}
                okText="确定"
                cancelText="取消"
              >
                <Button loading={sparkSaving} style={{ borderColor: '#FFB727', color: '#B87A00' }}>
                  ⭐ 设为星火
                </Button>
              </Popconfirm>
            )}
            {wish.fruitType === 'SPARK' && (
              <Button disabled style={{ borderColor: 'rgba(255,215,0,0.45)', color: '#B89400', background: 'rgba(255,215,0,0.08)' }}>
                ⭐ 星火永久
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
                  <img loading="lazy" src={url} alt="media" className={styles.mediaImage} />
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
              {wish.auditStatus === 'PENDING' && (
                <Tag color="orange">审核中，审核通过后将展示在心愿广场</Tag>
              )}
              {wish.tags?.map(tag => (
                <Tag key={tag} className={styles.tag}>{tag}</Tag>
              ))}
            </div>
            <h1 className={styles.title}>{wish.title}</h1>
            <div className={styles.meta}>
              <div
                className={styles.author}
                style={{ cursor: 'pointer' }}
                onClick={() => history.push(`/user/${wish.authorId}`)}
              >
                <DecoratedAvatar
                  userId={wish.authorId}
                  size={32}
                  src={wish.authorAvatar || undefined}
                  fallback={<StarOutlined />}
                />
                <span className={styles.authorName}>{wish.authorNickname}</span>
              </div>
              <span className={styles.date}>
                {new Date(wish.createdAt).toLocaleString('zh-CN')}
              </span>
              <Button
                size="small"
                icon={<ShareAltOutlined />}
                onClick={() => setShareOpen(true)}
              >
                分享
              </Button>
            </div>
          </div>

          {RICH_TEXT_PATTERN.test(wish.description) ? (
            <div
              className={`${styles.description} ${styles.descriptionRich}`}
              dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(wish.description) }}
            />
          ) : (
            <div className={styles.description}>
              {wish.description}
            </div>
          )}

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
              <div
                className={styles.author}
                style={{ cursor: 'pointer' }}
                onClick={() => history.push(`/user/${fulfillment.authorId}`)}
              >
                <DecoratedAvatar
                  userId={fulfillment.authorId}
                  size={32}
                  src={fulfillment.authorAvatar || undefined}
                  fallback={<StarOutlined />}
                />
                <span className={styles.authorName}>{fulfillment.authorNickname}</span>
              </div>
              <span className={styles.date}>
                还愿于 {new Date(fulfillment.createdAt).toLocaleString('zh-CN')}
              </span>
            </div>
            <RichText content={fulfillment.story} className={styles.fulfillmentStory} variant="full" />
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
            {/* T22：作者可撤回还愿故事（允许状态下重新提交；后端状态机校验） */}
            {isAuthor && (
              <div style={{ marginTop: 12, textAlign: 'right' }}>
                <Popconfirm
                  title="确认撤回还愿故事？撤回后心愿回到可还愿状态，可重新提交"
                  onConfirm={() => handleWithdrawFulfillment()}
                >
                  <Button type="link" size="small" danger>撤回还愿</Button>
                </Popconfirm>
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
            onBlessed={() => setBlessTick((t) => t + 1)}
          />
        </Card>

        {/* 全站虚拟礼物：送礼按钮 + 礼物墙 */}
        <Card className={styles.interactionCard}>
          <GiftSection targetType="WISH" targetId={wishId} />
        </Card>

        {/* 祝福墙（祝福者与被祝福者均可见，Sprint 1.2 补充） */}
        <Card className={styles.interactionCard}>
          <WishBlessList
            wishId={wishId}
            blessCount={wish.blessCount}
            refreshTick={blessTick}
          />
        </Card>

        {/* 进度 + 打卡日历（作者；浇水动效覆盖层） */}
        {wish.progress && (
          <Card className={styles.progressCard} title="心愿进度">
            <div className={styles.progressContent} style={{ position: 'relative' }}>
              {wateringFruit && <WateringEffect fruitType={wateringFruit} />}
              <Progress
                percent={wish.progress.percentage}
                strokeColor={FRUIT_COLORS[wish.fruitType]}
                size={['100%', 12]}
              />
              <div className={styles.progressDetail}>
                <span>当前进度：{wish.progress.currentValue} / {wish.progress.targetValue}</span>
                <span>打卡天数：{wish.checkinDays} 天</span>
              </div>
              {isAuthor && (
                <div style={{ marginTop: 16 }}>
                  <CheckinCalendar
                    wishId={wishId}
                    accentColor={FRUIT_COLORS[wish.fruitType] ?? '#00D4FF'}
                  />
                </div>
              )}
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
                      {/* T22：本人可编辑/删除自己的成长记录（统计与奖励冲正由后端规则控制） */}
                      {isAuthor && (
                        <span style={{ marginLeft: 8 }}>
                          <Button
                            type="link"
                            size="small"
                            onClick={() => handleEditGrowth(record)}
                          >
                            编辑
                          </Button>
                          <Popconfirm
                            title="确认删除该成长记录？统计将按后端规则处理"
                            onConfirm={() => handleDeleteGrowth(record)}
                          >
                            <Button type="link" size="small" danger>删除</Button>
                          </Popconfirm>
                        </span>
                      )}
                    </div>
                  </div>
                ),
              }))}
            />
            <div style={{ marginTop: 12, textAlign: 'center' }}>
              {timelineItems.length > 0 ? (
                <Button size="small" loading={timelineLoading} disabled={!timelineHasMore} onClick={() => loadTimeline(false)}>
                  {timelineHasMore ? '加载更早记录' : '已加载全部'}
                </Button>
              ) : (
                <Button size="small" loading={timelineLoading} onClick={() => loadTimeline(true)}>
                  查看完整时间轴
                </Button>
              )}
            </div>
          </Card>
        )}

        {/* 作者：目标步骤清单（v2 GoalPlan，最多 20 步） */}
        {isAuthor && goalsOpen && (
          <Card
            className={styles.growthCard}
            title="目标步骤"
            extra={
              <Button size="small" onClick={() => setGoalsOpen(false)}>
                收起
              </Button>
            }
          >
            <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
              <Input
                value={goalTitle}
                onChange={(e) => setGoalTitle(e.target.value)}
                placeholder="下一步做什么？（最多 20 步）"
                onPressEnter={handleAddGoal}
              />
              <Button type="primary" loading={goalSaving} onClick={handleAddGoal}>
                添加
              </Button>
            </div>
            {goalsLoading ? (
              <div style={{ textAlign: 'center', padding: 16 }}>加载中...</div>
            ) : goals.length === 0 ? (
              <Empty description="还没有步骤；从一个小目标开始" />
            ) : (
              <Timeline
                items={goals.map((goal, index) => ({
                  color: goal.status === 'COMPLETED' ? 'green' : 'blue',
                  children: (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                      <Input
                        size="small"
                        style={{ width: 220, textDecoration: goal.status === 'COMPLETED' ? 'line-through' : 'none', opacity: goal.status === 'COMPLETED' ? 0.6 : 1 }}
                        defaultValue={goal.title}
                        onPressEnter={(e) => {
                          const title = (e.target as HTMLInputElement).value.trim()
                          if (title && title !== goal.title) {
                            updateWishGoal(goal.id, { title, version: goal.version }).then(loadGoals)
                          }
                        }}
                      />
                      <Button size="small" onClick={() => toggleGoalDone(goal)}>
                        {goal.status === 'COMPLETED' ? '取消完成' : '完成'}
                      </Button>
                      <Button size="small" disabled={index === 0} onClick={() => moveGoal(index, -1)}>
                        ↑
                      </Button>
                      <Button size="small" disabled={index === goals.length - 1} onClick={() => moveGoal(index, 1)}>
                        ↓
                      </Button>
                      <Popconfirm title="删除这个步骤？" onConfirm={() => removeGoal(goal)}>
                        <Button size="small" danger>
                          删除
                        </Button>
                      </Popconfirm>
                    </div>
                  ),
                }))}
              />
            )}
          </Card>
        )}
        {isAuthor && !goalsOpen && (
          <div style={{ marginBottom: 16, textAlign: 'right' }}>
            <Button
              size="small"
              onClick={() => {
                setGoalsOpen(true)
                loadGoals()
              }}
            >
              🎯 目标步骤清单
            </Button>
          </div>
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
        open={growthOpen}
        title="记录成长"
        okText="保存记录"
        cancelText="取消"
        onOk={handleGrowthSubmit}
        confirmLoading={growthSaving}
        onCancel={() => setGrowthOpen(false)}
        destroyOnHidden
      >
        <Select
          value={growthType}
          onChange={(v) => setGrowthType(v)}
          style={{ width: '100%', marginBottom: 12 }}
          options={[
            { value: 'TEXT', label: '文字记录' },
            { value: 'IMAGE', label: '图片' },
            { value: 'VIDEO', label: '视频' },
            { value: 'DIARY', label: '心情日记' },
          ]}
        />
        <CommentToolbar
          textareaRef={growthInputRef}
          onInsert={(fragment) =>
            setGrowthContent((prev) => insertAtCursor(growthInputRef, fragment, prev, 500))
          }
        />
        <Input.TextArea
          ref={growthInputRef}
          rows={4}
          maxLength={500}
          value={growthContent}
          onChange={(e) => setGrowthContent(e.target.value)}
          placeholder="记录这一步的成长与心得…"
          style={{ marginBottom: 12 }}
        />
        {(growthType === 'IMAGE' || growthType === 'VIDEO') && (
          <Upload
            listType="picture-card"
            maxCount={4}
            accept={growthType === 'IMAGE' ? 'image/*' : 'video/*'}
            customRequest={growthCustomRequest as never}
            onRemove={(file) => {
              setGrowthMedia((prev) => prev.filter((u) => u !== (file.response as { url?: string })?.url && u !== file.uid))
              return true
            }}
          >
            {growthMedia.length < 4 && <PlusOutlined />}
          </Upload>
        )}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12 }}>
          <span style={{ color: 'var(--color-text-secondary)' }}>进度推进：</span>
          <InputNumber
            min={0}
            max={100}
            precision={0}
            value={growthDelta}
            onChange={(v) => setGrowthDelta(v ?? undefined)}
            placeholder="0"
            style={{ width: 120 }}
          />
          <span style={{ color: 'var(--color-text-secondary)' }}>%（可选）</span>
        </div>
      </Modal>

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
        <CommentToolbar
          textareaRef={inheritInputRef}
          onInsert={(fragment) =>
            setInheritMessage((prev) => insertAtCursor(inheritInputRef, fragment, prev, 500))
          }
        />
        <Input.TextArea
          ref={inheritInputRef}
          value={inheritMessage}
          onChange={(e) => setInheritMessage(e.target.value)}
          maxLength={500}
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="如：谢谢你们陪我一起许下这个愿望，希望你也可以"
        />
      </Modal>
      <Modal
        open={checkinOpen}
        title="每日打卡"
        okText="打卡（星光 +2）"
        cancelText="取消"
        onOk={handleCheckinSubmit}
        onCancel={() => setCheckinOpen(false)}
        confirmLoading={checkinSaving}
      >
        <p style={{ marginBottom: 12 }}>
          为今天的心愿之旅留下一点痕迹吧（心得可留空，直接打卡）：
        </p>
        <CommentToolbar
          textareaRef={checkinInputRef}
          onInsert={(fragment) =>
            setCheckinContent((prev) => insertAtCursor(checkinInputRef, fragment, prev, 200))
          }
        />
        <Input.TextArea
          ref={checkinInputRef}
          value={checkinContent}
          onChange={(e) => setCheckinContent(e.target.value)}
          maxLength={200}
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="如：今天离目标又近了一步"
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
      {wish && (
        <ShareCardModal
          open={shareOpen}
          onClose={() => setShareOpen(false)}
          title={wish.title}
          author={wish.authorNickname}
          dateText={new Date(wish.createdAt).toLocaleDateString('zh-CN')}
          fruitLabel={FRUIT_LABELS[wish.fruitType] ?? wish.fruitType}
          fruitColor={FRUIT_COLORS[wish.fruitType] ?? '#9370DB'}
        />
      )}
      <WishBGM />
    </div>
  )
}
