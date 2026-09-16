import { useCallback, useEffect, useState } from 'react'
import { App, Button, Checkbox, Empty, Radio, Select, Spin, Tag } from 'antd'
import { history } from 'umi'
import {
  fishDriftBottle,
  interactDriftBottle,
  listDriftBottleCandidateWishes,
  listMyDriftBottles,
  throwDriftBottle,
  type DriftBottleCandidateWish,
  type DriftBottleItem,
} from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import WishBGM from '@/components/WishBGM'
import TiptapEditor from '@/components/TiptapEditor'
import RichText, { richTextToPlainText } from '@/components/RichText'
import DriftBottleComments from '@/components/DriftBottleComments'
import styles from './EncounterLetters.module.css'

/**
 * 漂流瓶（替代相遇信笺用户侧体验）：
 * 匿名随机漂流——投瓶（自由富文本 / 关联心愿二选一）、捞瓶、我的漂流瓶、匿名回应。
 * 投瓶编辑器与发帖一致（Tiptap 富文本）；无附近模式、无轨迹上报，全程不暴露投瓶人身份。
 */

type ThrowMode = 'TEXT' | 'WISH'

export default function EncounterLetters() {
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()

  // 投瓶表单
  const [throwMode, setThrowMode] = useState<ThrowMode>('TEXT')
  const [throwText, setThrowText] = useState('')
  const [throwWishId, setThrowWishId] = useState<number | string | null>(null)
  const [throwAnonymous, setThrowAnonymous] = useState(true)
  const [throwing, setThrowing] = useState(false)

  // 捞瓶
  const [fishing, setFishing] = useState(false)
  const [fishedBottle, setFishedBottle] = useState<DriftBottleItem | null>(null)

  // 我的漂流瓶
  const [bottles, setBottles] = useState<DriftBottleItem[]>([])
  const [loadingMine, setLoadingMine] = useState(true)

  // 展开评论树的瓶子 ID 集合
  const [commentOpen, setCommentOpen] = useState<Set<number>>(new Set())

  // 可关联的「近 20 个自己发布的可关联心愿」
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
      history.push('/login?redirect=/wish/encounters')
      return
    }
    if (user) {
      loadMine()
      loadCandidateWishes()
    }
  }, [user, userLoading, loadMine, loadCandidateWishes])

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
    } catch {
      // 拦截器已提示
    } finally {
      setThrowing(false)
    }
  }

  const handleFish = async () => {
    setFishing(true)
    setFishedBottle(null)
    try {
      const res = await fishDriftBottle()
      if (res.data.success) {
        if (res.data.data === null) {
          message.info('海面暂时没有漂流瓶，稍后再来捞一捞')
        } else {
          setFishedBottle(res.data.data)
          loadMine()
        }
      }
    } catch {
      // 拦截器已提示
    } finally {
      setFishing(false)
    }
  }

  const handleInteract = async (bottle: DriftBottleItem, type: 'BLESS' | 'LIGHT') => {
    try {
      await interactDriftBottle(bottle.bottleId, type)
      message.success(type === 'BLESS' ? '祝福已匿名送达 💛' : '已匿名点亮 TA 的心愿 ⭐')
      if (fishedBottle?.bottleId === bottle.bottleId) setFishedBottle(null)
      loadMine()
    } catch (err) {
      const code = (err as { code?: string })?.code
      if (code === 'WISH_RATE_LIMITED') message.warning('这个漂流瓶今天已经回应过啦')
      else if (code === 'WISH_STARLIGHT_INSUFFICIENT') message.warning('星光不足，无法点亮')
    }
  }

  const renderBottleActions = (bottle: DriftBottleItem) => {
    // 仅捞起人可回应关联心愿的漂流瓶（文字漂流瓶不提供回应）
    if (bottle.wishId === null) return null
    return (
      <div className={styles.bottleActions}>
        <Button size="small" onClick={() => handleInteract(bottle, 'BLESS')}>匿名祝福 💛</Button>
        <Button size="small" onClick={() => handleInteract(bottle, 'LIGHT')}>点亮 TA 的心愿 ⭐（-2 星光）</Button>
      </div>
    )
  }

  const toggleComments = (bottleId: number) => {
    setCommentOpen((prev) => {
      const next = new Set(prev)
      if (next.has(bottleId)) next.delete(bottleId)
      else next.add(bottleId)
      return next
    })
  }

  const renderBottleContent = (bottle: DriftBottleItem) => (
    <>
      {bottle.wishTitle ? (
        <div className={styles.wishLink}>
          <span className={styles.wishLinkLabel}>心愿</span>
          <span className={styles.wishLinkTitle}>{bottle.wishTitle}</span>
          <div className={styles.bottleTags}>
            {bottle.wishTags.map((tag) => <Tag key={tag} color="gold">{tag}</Tag>)}
          </div>
        </div>
      ) : (
        <RichText content={bottle.content} className={styles.content} />
      )}
      {!bottle.isAnonymous && bottle.throwerNickname && (
        <p className={styles.throwerLine}>投瓶人：{bottle.throwerNickname}</p>
      )}
      {renderBottleActions(bottle)}
      <div className={styles.commentSection}>
        <Button
          size="small"
          type="text"
          className={styles.commentToggle}
          onClick={() => toggleComments(bottle.bottleId)}
          aria-expanded={commentOpen.has(bottle.bottleId)}
        >
          💬 与 TA 交流{bottle.commentCount > 0 ? `（${bottle.commentCount}）` : ''}
          {commentOpen.has(bottle.bottleId) ? ' ▲' : ' ▼'}
        </Button>
        {commentOpen.has(bottle.bottleId) && <DriftBottleComments bottleId={bottle.bottleId} />}
      </div>
    </>
  )

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.body}>
        <div className={styles.headerBar}>
          <div>
            <h1 className={styles.pageTitle}>🍾 漂流瓶</h1>
            <p className={styles.pageSubtitle}>把一句话装进瓶子，让海把它带给有缘人 · 全程匿名 · 不暴露身份</p>
          </div>
        </div>

        {/* 捞瓶海面 */}
        <section className={styles.seaCard}>
          <div className={styles.seaVisual} aria-hidden="true">🍾</div>
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
            <div className={styles.fishedCard}>
              <div className={styles.fishedTag}>🎣 捞到啦</div>
              {renderBottleContent(fishedBottle)}
            </div>
          )}
        </section>

        {/* 投瓶 */}
        <section className={styles.throwCard}>
          <h2 className={styles.sectionTitle}>投出一个漂流瓶</h2>
          <p className={styles.sectionDesc}>写下你的心情，或把它系在一份心愿上，匿名投向大海</p>

          <Radio.Group
            value={throwMode}
            onChange={(e) => setThrowMode(e.target.value as ThrowMode)}
            className={styles.modeRadio}
          >
            <Radio.Button value="TEXT">自由文字</Radio.Button>
            <Radio.Button value="WISH">关联心愿</Radio.Button>
          </Radio.Group>

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
              placeholder={loadingWishes ? '加载心愿中…' : '选择一个自己发布的心愿'}
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
              {throwAnonymous ? '捞起者看不到你是谁' : '实名投出：捞起者可见你的昵称和头像'}
            </span>
          </div>

          <Button
            type="primary"
            loading={throwing}
            onClick={handleThrow}
            className={styles.throwBtn}
          >
            投入大海
          </Button>
        </section>

        {/* 我的漂流瓶 */}
        <section>
          <h2 className={styles.sectionTitle}>我的漂流瓶</h2>
          {loadingMine ? (
            <p className={styles.emptyText}>加载中…</p>
          ) : bottles.length === 0 ? (
            <Empty description="还没有漂流瓶，投出或捞起第一个吧" />
          ) : (
            <div className={styles.bottleList}>
              {bottles.map((bottle) => (
                <div key={bottle.bottleId} className={styles.bottleCard}>
                  <div className={styles.bottleTop}>
                    <Tag color={bottle.role === 'THROWN' ? 'blue' : 'green'}>
                      {bottle.role === 'THROWN' ? '我投出的' : '我捞到的'}
                    </Tag>
                    <span className={styles.bottleStatus}>
                      {bottle.status === 'FLOATING' ? '漂流中' : '已被捞起'}
                    </span>
                  </div>
                  {renderBottleContent(bottle)}
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
      <WishBGM />
    </div>
  )
}