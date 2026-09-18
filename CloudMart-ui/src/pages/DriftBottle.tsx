import { useCallback, useEffect, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import { App, Button, Checkbox, Empty, Popconfirm, Segmented, Select, Spin, Switch, Tag } from 'antd'
import { MessageOutlined, SendOutlined, UserOutlined } from '@ant-design/icons'
import { history } from 'umi'
import {
  collectDriftBottle,
  fishDriftBottle,
  getDriftBottleQuota,
  interactDriftBottle,
  listDriftBottleCandidateWishes,
  listMyDriftBottles,
  returnDriftBottle,
  throwDriftBottle,
  updateDriftBottlePickerAnonymity,
  type DriftBottleCandidateWish,
  type DriftBottleItem,
  type DriftBottleQuota,
} from '@/api/wish'
import { createConversation } from '@/api/chat'
import { useAuthStore } from '@/stores/auth'
import WishBGM from '@/components/WishBGM'
import TiptapEditor from '@/components/TiptapEditor'
import RichText, { richTextToPlainText } from '@/components/RichText'
import DriftBottleComments from '@/components/DriftBottleComments'
import DecoratedAvatar from '@/components/DecoratedAvatar'
import styles from './DriftBottle.module.css'

/**
 * 漂流瓶（替代相遇信笺用户侧体验）：
 * 匿名随机漂流——投瓶（自由富文本 / 关联最近 30 条心愿二选一）、捞瓶、扔回海里、收藏、
 * 我的漂流瓶、双方匿名开关（均实名可私聊/查看资料）、每日配额（投瓶 10 / 打捞 20）。
 * 投瓶编辑器与发帖一致（Tiptap 富文本）；无附近模式、无轨迹上报。
 */

type ThrowMode = 'TEXT' | 'WISH'
type RoleFilter = 'ALL' | 'THROWN' | 'PICKED'

/** 展示层状态：物理状态 + 收藏/评论数推导（被回复/被收藏不入库） */
function displayStatus(bottle: DriftBottleItem): { label: string; color: string } {
  if (bottle.status === 'FLOATING') return { label: '漂流中', color: 'processing' }
  if (bottle.status === 'RETURNED') return { label: '被扔回海里', color: 'warning' }
  if (bottle.isCollected) return { label: '被收藏', color: 'gold' }
  if (bottle.commentCount > 0) return { label: '被回复', color: 'success' }
  return { label: '被捡到', color: 'cyan' }
}

const formatTime = (iso: string | null) =>
  iso ? new Date(iso).toLocaleString('zh-CN', { hour12: false }) : ''

/** 海面装饰瓶配置：颜色 / 漂流速度 / 出发延迟 / 水深（固定值，避免渲染期随机） */
const SEA_BOTTLES = [
  { key: 'rose', duration: 26, delay: 0, bottom: 34, hue: '#ff8fab' },
  { key: 'amber', duration: 34, delay: -9, bottom: 58, hue: '#ffc46b' },
  { key: 'mint', duration: 30, delay: -17, bottom: 22, hue: '#7adcb4' },
  { key: 'sky', duration: 38, delay: -5, bottom: 70, hue: '#7cc3f5' },
  { key: 'violet', duration: 28, delay: -21, bottom: 46, hue: '#b39df2' },
  { key: 'coral', duration: 33, delay: -13, bottom: 12, hue: '#ff9d8a' },
] as const

interface BottleCardProps {
  bottle: DriftBottleItem
  highlight: boolean
  commentOpen: boolean
  onToggleComments: () => void
  onInteract: (bottle: DriftBottleItem, type: 'BLESS' | 'LIGHT') => void
  onReturn: (bottle: DriftBottleItem) => void
  onCollect: (bottle: DriftBottleItem) => void
  onPickerAnonymity: (bottle: DriftBottleItem, isAnonymous: boolean) => void
}

/** 漂流瓶卡片（捞到的结果卡与「我的漂流瓶」列表卡共用） */
function BottleCard(props: BottleCardProps) {
  const { bottle, highlight } = props
  const status = displayStatus(bottle)
  const isPicker = bottle.role === 'PICKED'
  // 对方身份是否可见：我捞到 → 看投瓶人；我投出 → 看捞瓶人
  const peerUserId = isPicker ? bottle.throwerUserId : bottle.pickerUserId
  const peerNickname = isPicker ? bottle.throwerNickname : bottle.pickerNickname
  const peerAvatar = isPicker ? bottle.throwerAvatar : bottle.pickerAvatar

  const goProfile = (userId: number) => history.push(`/user/${userId}`)
  const goWish = () => bottle.wishId && history.push(`/wish/${bottle.wishId}`)

  const startChat = async () => {
    if (peerUserId === null) return
    try {
      const res = await createConversation(peerUserId)
      if (res.data.success && res.data.data) {
        history.push(`/chat/${res.data.data.id}`)
      }
    } catch {
      // 拦截器已提示
    }
  }

  return (
    <div className={highlight ? `${styles.bottleCard} ${styles.bottleCardHighlight}` : styles.bottleCard}>
      <div className={styles.bottleTop}>
        <div className={styles.bottleTopTags}>
          <Tag color={isPicker ? 'green' : 'blue'}>{isPicker ? '我捞到的' : '我投出的'}</Tag>
          <Tag color={status.color}>{status.label}</Tag>
        </div>
        <span className={styles.bottleTime}>
          {bottle.pickedAt
            ? `投于 ${formatTime(bottle.thrownAt)} · 捞于 ${formatTime(bottle.pickedAt)}`
            : `投于 ${formatTime(bottle.thrownAt)}`}
        </span>
      </div>

      {bottle.wishTitle ? (
        <div className={styles.wishLink}>
          <span className={styles.wishLinkLabel}>心愿</span>
          <Button type="link" className={styles.wishLinkTitle} onClick={goWish}>
            {bottle.wishTitle}
          </Button>
          <div className={styles.bottleTags}>
            {bottle.wishTags.map((tag) => <Tag key={tag} color="gold">{tag}</Tag>)}
          </div>
        </div>
      ) : (
        <RichText content={bottle.content} className={styles.content} />
      )}

      <div className={styles.identityLine}>
        <span className={styles.identityRole}>{isPicker ? '投瓶人' : '捞瓶人'}</span>
        {peerUserId !== null ? (
          <>
            <DecoratedAvatar
              userId={peerUserId}
              size={24}
              src={peerAvatar || undefined}
              fallback={<UserOutlined />}
              style={{ cursor: 'pointer' }}
              onClick={() => goProfile(peerUserId)}
            />
            <span
              className={`${styles.identityName} ${styles.identityNameLink}`}
              onClick={() => goProfile(peerUserId)}
            >
              {peerNickname}
            </span>
            <Button size="small" type="text" icon={<UserOutlined />} onClick={() => goProfile(peerUserId)}>
              查看资料
            </Button>
            <Button size="small" type="text" icon={<MessageOutlined />} onClick={startChat}>
              私聊
            </Button>
          </>
        ) : (
          <span className={styles.identityName}>匿名瓶友</span>
        )}
      </div>

      {/* 捞起人操作区：收藏 / 扔回海里 / 捞瓶匿名开关 */}
      {isPicker && bottle.status === 'PICKED' && (
        <div className={styles.pickerActions}>
          <Popconfirm
            title="把瓶子扔回海里？"
            description="扔回后它将继续漂流，被有缘人捞起"
            onConfirm={() => props.onReturn(bottle)}
          >
            <Button size="small">🌊 扔回海里</Button>
          </Popconfirm>
          {bottle.isCollected ? (
            <Tag color="gold">已收藏 ⭐</Tag>
          ) : (
            <Button size="small" onClick={() => props.onCollect(bottle)}>⭐ 收藏</Button>
          )}
          <span className={styles.anonymitySwitch}>
            <Switch
              size="small"
              checked={bottle.pickerIsAnonymous}
              checkedChildren="匿名"
              unCheckedChildren="实名"
              onChange={(checked) => props.onPickerAnonymity(bottle, checked)}
              aria-label="捞瓶身份匿名开关"
            />
            <span className={styles.anonymityHint}>
              {bottle.pickerIsAnonymous ? '投瓶人看不到你' : '投瓶人可与你私聊'}
            </span>
          </span>
        </div>
      )}

      {/* 关联心愿漂流瓶：捞起人可匿名回应（保留既有祝福/点亮能力） */}
      {isPicker && bottle.wishId !== null && bottle.status === 'PICKED' && (
        <div className={styles.bottleActions}>
          <Button size="small" onClick={() => props.onInteract(bottle, 'BLESS')}>匿名祝福 💛</Button>
          <Button size="small" onClick={() => props.onInteract(bottle, 'LIGHT')}>点亮 TA 的心愿 ⭐（-2 星光）</Button>
        </div>
      )}

      <div className={styles.commentSection}>
        <Button
          size="small"
          type="text"
          className={styles.commentToggle}
          onClick={props.onToggleComments}
          aria-expanded={props.commentOpen}
        >
          💬 与 TA 交流{bottle.commentCount > 0 ? `（${bottle.commentCount}）` : ''}
          {props.commentOpen ? ' ▲' : ' ▼'}
        </Button>
        {props.commentOpen && <DriftBottleComments bottleId={bottle.bottleId} />}
      </div>
    </div>
  )
}

export default function DriftBottlePage() {
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()

  // 每日配额
  const [quota, setQuota] = useState<DriftBottleQuota | null>(null)

  // 投瓶表单
  const [throwMode, setThrowMode] = useState<ThrowMode>('TEXT')
  const [throwText, setThrowText] = useState('')
  const [throwWishId, setThrowWishId] = useState<number | string | null>(null)
  const [throwAnonymous, setThrowAnonymous] = useState(true)
  const [throwing, setThrowing] = useState(false)

  // 捞瓶
  const [fishing, setFishing] = useState(false)
  const [fishedBottle, setFishedBottle] = useState<DriftBottleItem | null>(null)
  const fishSeq = useRef(0)

  // 我的漂流瓶
  const [bottles, setBottles] = useState<DriftBottleItem[]>([])
  const [loadingMine, setLoadingMine] = useState(true)
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL')

  // 展开评论树的瓶子 ID 集合
  const [commentOpen, setCommentOpen] = useState<Set<number>>(new Set())

  // 可关联的「近 30 个自己发布的可关联心愿」
  const [candidateWishes, setCandidateWishes] = useState<DriftBottleCandidateWish[]>([])
  const [loadingWishes, setLoadingWishes] = useState(true)

  const loadMine = useCallback(async () => {
    setLoadingMine(true)
    try {
      const res = await listMyDriftBottles()
      if (res.data.success) setBottles(res.data.data ?? [])
    } catch {
      // 拦截器已提示
    } finally {
      setLoadingMine(false)
    }
  }, [])

  const loadQuota = useCallback(async () => {
    try {
      const res = await getDriftBottleQuota()
      if (res.data.success) setQuota(res.data.data)
    } catch {
      // 拦截器已提示
    }
  }, [])

  const loadCandidateWishes = useCallback(async () => {
    setLoadingWishes(true)
    try {
      const res = await listDriftBottleCandidateWishes()
      if (res.data.success) setCandidateWishes(res.data.data ?? [])
    } catch {
      // 拦截器已提示
    } finally {
      setLoadingWishes(false)
    }
  }, [])

  useEffect(() => {
    if (!user && !userLoading) {
      history.push('/login?redirect=/wish/drift-bottle')
      return
    }
    if (user) {
      loadQuota()
      loadMine()
      loadCandidateWishes()
    }
  }, [user, userLoading, loadQuota, loadMine, loadCandidateWishes])

  const handleThrow = async () => {
    const hasText = throwMode === 'TEXT' && richTextToPlainText(throwText).trim().length > 0
    const hasWish = throwMode === 'WISH' && throwWishId !== null
    if (!hasText && !hasWish) {
      message.warning(throwMode === 'TEXT' ? '写下一句话再投出吧' : '选择一个心愿再投出吧')
      return
    }
    setThrowing(true)
    try {
      await throwDriftBottle(
        throwMode === 'TEXT'
          ? { content: throwText, isAnonymous: throwAnonymous }
          : { wishId: throwWishId!, isAnonymous: throwAnonymous },
      )
      message.success('漂流瓶已投出，愿它漂向有缘人 🌊')
      setThrowText('')
      setThrowWishId(null)
      loadMine()
      loadQuota()
    } catch {
      // 拦截器已提示
    } finally {
      setThrowing(false)
    }
  }

  const handleFish = async () => {
    setFishing(true)
    setFishedBottle(null)
    const seq = ++fishSeq.current
    try {
      const res = await fishDriftBottle()
      if (seq !== fishSeq.current) return
      if (res.data.success) {
        if (res.data.data === null) {
          message.info('海面暂时没有漂流瓶，稍后再来捞一捞')
        } else {
          setFishedBottle(res.data.data)
          loadMine()
          loadQuota()
        }
      }
    } catch {
      // 拦截器已提示
    } finally {
      if (seq === fishSeq.current) setFishing(false)
    }
  }

  const handleInteract = async (bottle: DriftBottleItem, type: 'BLESS' | 'LIGHT') => {
    try {
      await interactDriftBottle(bottle.bottleId, type)
      message.success(type === 'BLESS' ? '祝福已匿名送达 💛' : '已匿名点亮 TA 的心愿 ⭐')
    } catch (err) {
      const code = (err as { code?: string })?.code
      if (code === 'WISH_RATE_LIMITED') message.warning('这个漂流瓶今天已经回应过啦')
      else if (code === 'WISH_STARLIGHT_INSUFFICIENT') message.warning('星光不足，无法点亮')
    }
  }

  const handleReturn = async (bottle: DriftBottleItem) => {
    try {
      await returnDriftBottle(bottle.bottleId)
      message.success('瓶子已回到海里，继续寻找有缘人 🌊')
      if (fishedBottle?.bottleId === bottle.bottleId) setFishedBottle(null)
      loadMine()
      loadQuota()
    } catch {
      // 拦截器已提示
    }
  }

  const handleCollect = async (bottle: DriftBottleItem) => {
    try {
      await collectDriftBottle(bottle.bottleId)
      message.success('已收藏这只漂流瓶 ⭐')
      const apply = (b: DriftBottleItem) =>
        b.bottleId === bottle.bottleId ? { ...b, isCollected: true } : b
      setBottles((prev) => prev.map(apply))
      setFishedBottle((prev) => (prev && prev.bottleId === bottle.bottleId ? { ...prev, isCollected: true } : prev))
    } catch {
      // 拦截器已提示
    }
  }

  const handlePickerAnonymity = async (bottle: DriftBottleItem, isAnonymous: boolean) => {
    try {
      await updateDriftBottlePickerAnonymity(bottle.bottleId, isAnonymous)
      const apply = (b: DriftBottleItem) =>
        b.bottleId === bottle.bottleId ? { ...b, pickerIsAnonymous: isAnonymous } : b
      setBottles((prev) => prev.map(apply))
      setFishedBottle((prev) =>
        prev && prev.bottleId === bottle.bottleId ? { ...prev, pickerIsAnonymous: isAnonymous } : prev)
      message.success(isAnonymous ? '已切换为匿名捞瓶，投瓶人看不到你' : '已切换为实名捞瓶，投瓶人可以与你私聊了')
    } catch {
      // 拦截器已提示
    }
  }

  const toggleComments = (bottleId: number) => {
    setCommentOpen((prev) => {
      const next = new Set(prev)
      if (next.has(bottleId)) next.delete(bottleId)
      else next.add(bottleId)
      return next
    })
  }

  const visibleBottles = bottles.filter((b) => roleFilter === 'ALL' || b.role === roleFilter)

  const renderBottleCard = (bottle: DriftBottleItem, highlight: boolean) => (
    <BottleCard
      key={bottle.bottleId}
      bottle={bottle}
      highlight={highlight}
      commentOpen={commentOpen.has(bottle.bottleId)}
      onToggleComments={() => toggleComments(bottle.bottleId)}
      onInteract={handleInteract}
      onReturn={handleReturn}
      onCollect={handleCollect}
      onPickerAnonymity={handlePickerAnonymity}
    />
  )

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.body}>
        <div className={styles.headerBar}>
          <div>
            <h1 className={styles.pageTitle}>🌊 漂流瓶</h1>
            <p className={styles.pageSubtitle}>
              把一句话装进瓶子，让海把它带给有缘人 · 双方可选匿名 · 未设置默认匿名
            </p>
          </div>
          {quota && (
            <div className={styles.quotaChips} aria-label="今日配额">
              <span className={styles.quotaChip}>🍾 今日投瓶 {quota.throwUsed}/{quota.throwLimit}</span>
              <span className={styles.quotaChip}>🎣 今日打捞 {quota.fishUsed}/{quota.fishLimit}</span>
            </div>
          )}
        </div>

        {/* 动画海面 + 捞瓶 */}
        <section className={styles.seaCard}>
          <div className={styles.seaScene} aria-hidden="true">
            <div className={`${styles.wave} ${styles.waveBack}`} />
            <div className={`${styles.wave} ${styles.waveFront}`} />
            {SEA_BOTTLES.map((bottle) => (
              <div
                key={bottle.key}
                className={styles.seaBottle}
                style={{
                  animationDuration: `${bottle.duration}s`,
                  animationDelay: `${bottle.delay}s`,
                  bottom: bottle.bottom,
                }}
              >
                <div className={styles.bottleShape} style={{ '--hue': bottle.hue } as CSSProperties}>
                  <span className={styles.bottleCork} />
                  <span className={styles.bottleNeck} />
                  <span className={styles.bottleBody} />
                </div>
              </div>
            ))}
            <span className={`${styles.bubble} ${styles.bubble1}`} />
            <span className={`${styles.bubble} ${styles.bubble2}`} />
            <span className={`${styles.bubble} ${styles.bubble3}`} />
          </div>

          <div className={styles.seaIntro}>
            <h2 className={styles.sectionTitle}>打捞漂流瓶</h2>
            <p className={styles.sectionDesc}>随机捞取一个他人投出的漂流瓶，也许正有一段心事在等你</p>
          </div>
          <Button
            type="primary"
            size="large"
            loading={fishing}
            onClick={handleFish}
            className={styles.fishBtn}
          >
            {fishing ? '正在打捞…' : '捞一个漂流瓶'}
          </Button>

          {fishedBottle && (
            <div className={styles.fishedWrap}>
              <div className={styles.fishedTag}>🎣 捞到啦</div>
              {renderBottleCard(fishedBottle, true)}
            </div>
          )}
        </section>

        {/* 投瓶 */}
        <section className={styles.throwCard}>
          <h2 className={styles.sectionTitle}>投出一个漂流瓶</h2>
          <p className={styles.sectionDesc}>写下你的心情，或把它系在一份心愿上，投向大海</p>

          <Segmented
            value={throwMode}
            onChange={(v) => setThrowMode(v as ThrowMode)}
            options={[
              { label: '自由文字', value: 'TEXT' },
              { label: '关联心愿', value: 'WISH' },
            ]}
            className={styles.modeRadio}
          />

          {throwMode === 'TEXT' ? (
            <div className={styles.throwEditor}>
              <TiptapEditor
                value={throwText}
                onChange={setThrowText}
                placeholder="写下你想随海漂流的一句话…（与发帖同款编辑器）"
              />
            </div>
          ) : (
            <Select
              value={throwWishId ?? undefined}
              onChange={(v) => setThrowWishId(v === null || v === undefined ? null : (v as number | string))}
              placeholder={loadingWishes ? '加载心愿中…' : '选择一个自己发布的心愿（最近 30 条）'}
              loading={loadingWishes}
              showSearch
              optionFilterProp="label"
              allowClear
              className={styles.throwSelect}
              options={candidateWishes.map((w) => ({ value: w.wishId, label: w.title }))}
              notFoundContent={loadingWishes ? <Spin size="small" /> : '暂无可关联的心愿（需公开进行中）'}
            />
          )}

          <div className={styles.anonymousRow}>
            <Checkbox
              checked={throwAnonymous}
              onChange={(e) => setThrowAnonymous(e.target.checked)}
            >
              匿名投出（默认）
            </Checkbox>
            <span className={styles.anonymousHint}>
              {throwAnonymous
                ? '捞起者看不到你是谁'
                : '实名投出：捞起者可见你的昵称和头像，你们可以互相交流'}
            </span>
          </div>

          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={throwing}
            onClick={handleThrow}
            className={styles.throwBtn}
          >
            投入大海
          </Button>
        </section>

        {/* 我的漂流瓶 */}
        <section>
          <div className={styles.mineHeader}>
            <h2 className={styles.sectionTitle}>我的漂流瓶</h2>
            <Segmented
              value={roleFilter}
              onChange={(v) => setRoleFilter(v as RoleFilter)}
              options={[
                { label: '全部', value: 'ALL' },
                { label: '我投出的', value: 'THROWN' },
                { label: '我捞到的', value: 'PICKED' },
              ]}
            />
          </div>
          {loadingMine ? (
            <p className={styles.emptyText}>加载中…</p>
          ) : visibleBottles.length === 0 ? (
            <Empty description="还没有漂流瓶，投出或捞起第一个吧" />
          ) : (
            <div className={styles.bottleList}>
              {visibleBottles.map((bottle) => renderBottleCard(bottle, false))}
            </div>
          )}
        </section>
      </div>
      <WishBGM />
    </div>
  )
}
