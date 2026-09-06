import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Button, Input, Modal, Popconfirm, Tooltip } from 'antd'
import {
  BulbOutlined,
  EyeInvisibleOutlined,
  GiftOutlined,
  StarOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import {
  createInteraction,
  listMyInteractions,
  revokeInteraction,
  type MyInteractionItem,
} from '@/api/wish'
import styles from './style.module.css'

/**
 * 心愿互动按钮组（文档 2.2/五 节，Sprint 1.2；匿名星光 Sprint 2.6）。
 *
 * 规则：
 * - 点亮：可重复，每次扣 2 星光（402 星光不足提示）；成功触发微光粒子特效
 * - 同求：每愿望唯一；已同求高亮（呼吸灯动效），可取消后重新同求
 * - 祝福：每愿望每日 1 次；今日已祝福则禁用（tooltip 说明）
 * - 匿名星光：每愿望 1 次、每日 3 次，扣 5 星光；身份对作者保密（神秘星人）
 * - 未登录点击引导登录
 */
export interface WishInteractionCounts {
  lightCount: number
  sameWishCount: number
  blessCount: number
  anonStarCount: number
}

interface WishInteractionBarProps {
  wishId: number | string
  counts: WishInteractionCounts
  isLoggedIn: boolean
  onCountsChange: (counts: Partial<WishInteractionCounts>) => void
  onRequireLogin: () => void
}

const BLESS_CONTENT_MAX = 200
const ANON_STAR_COST = 5

export default function WishInteractionBar({
  wishId,
  counts,
  isLoggedIn,
  onCountsChange,
  onRequireLogin,
}: WishInteractionBarProps) {
  const { message } = App.useApp()
  const [myInteractions, setMyInteractions] = useState<MyInteractionItem[]>([])
  const [lighting, setLighting] = useState(false)
  const [sameWishing, setSameWishing] = useState(false)
  const [revoking, setRevoking] = useState(false)
  const [anonStarring, setAnonStarring] = useState(false)
  const [blessModalOpen, setBlessModalOpen] = useState(false)
  const [blessContent, setBlessContent] = useState('')
  const [blessing, setBlessing] = useState(false)
  /** 点亮微光特效（key 变更触发 CSS 动画重放） */
  const [burstKey, setBurstKey] = useState(0)

  const refreshMyInteractions = useCallback(async () => {
    if (!isLoggedIn) {
      setMyInteractions([])
      return
    }
    try {
      const res = await listMyInteractions(wishId)
      if (res.data.success) {
        setMyInteractions(res.data.data)
      }
    } catch {
      // 状态接口失败不阻断详情展示，按钮按未互动处理
    }
  }, [wishId, isLoggedIn])

  useEffect(() => {
    refreshMyInteractions()
  }, [refreshMyInteractions])

  const mySameWish = useMemo(
    () => myInteractions.find((i) => i.type === 'SAME_WISH') ?? null,
    [myInteractions],
  )
  const myAnonStar = useMemo(
    () => myInteractions.find((i) => i.type === 'ANON_STAR') ?? null,
    [myInteractions],
  )
  const blessedToday = useMemo(
    () => myInteractions.some((i) => i.type === 'BLESS' && i.createdToday),
    [myInteractions],
  )
  const myLightCount = useMemo(
    () => myInteractions.filter((i) => i.type === 'LIGHT').length,
    [myInteractions],
  )

  /** 按 error.code 提示业务错误（402/409/429），未携带 code 时静默（拦截器已提示） */
  const handleBusinessError = (err: unknown, interactionLabel: string) => {
    const code = (err as { code?: string })?.code
    if (code === 'WISH_STARLIGHT_INSUFFICIENT') {
      message.warning('星光不足，可通过每日签到、打卡获取星光')
    } else if (code === 'WISH_RATE_LIMITED') {
      message.warning((err as Error)?.message || '今日次数已达上限')
    } else if (code === 'WISH_ALREADY_INTERACTED') {
      message.info(`已${interactionLabel}过该心愿`)
      refreshMyInteractions()
    }
  }

  const requireLoginOr = (action: () => void) => {
    if (!isLoggedIn) {
      message.info('登录后即可互动')
      onRequireLogin()
      return
    }
    action()
  }

  const handleLight = async () => {
    setLighting(true)
    try {
      const res = await createInteraction(wishId, { type: 'LIGHT' })
      if (res.data.success) {
        onCountsChange({ lightCount: res.data.data.lightCount })
        refreshMyInteractions()
        setBurstKey((k) => k + 1)
        message.success('已点亮，为 TA 加了一束光 ✨（-2 星光）')
      }
    } catch (err) {
      handleBusinessError(err, '点亮')
    } finally {
      setLighting(false)
    }
  }

  const handleSameWish = async () => {
    setSameWishing(true)
    try {
      const res = await createInteraction(wishId, { type: 'SAME_WISH' })
      if (res.data.success) {
        onCountsChange({ sameWishCount: res.data.data.sameWishCount })
        refreshMyInteractions()
        message.success('已加入共同愿望 🤝')
      }
    } catch (err) {
      handleBusinessError(err, '同求')
    } finally {
      setSameWishing(false)
    }
  }

  const handleAnonStar = async () => {
    setAnonStarring(true)
    try {
      const res = await createInteraction(wishId, { type: 'ANON_STAR' })
      if (res.data.success) {
        onCountsChange({ anonStarCount: res.data.data.anonStarCount })
        refreshMyInteractions()
        message.success('已匿名送出星光，TA 眼中你是一颗神秘星辰 💫')
      }
    } catch (err) {
      handleBusinessError(err, '匿名星光')
    } finally {
      setAnonStarring(false)
    }
  }

  const handleRevokeSameWish = async () => {
    if (!mySameWish) return
    setRevoking(true)
    try {
      const res = await revokeInteraction(wishId, mySameWish.id)
      if (res.data.success) {
        onCountsChange({ sameWishCount: Math.max(0, counts.sameWishCount - 1) })
        refreshMyInteractions()
        message.success('已取消同求')
      }
    } catch (err) {
      handleBusinessError(err, '同求')
    } finally {
      setRevoking(false)
    }
  }

  const openBlessModal = () => {
    setBlessContent('')
    setBlessModalOpen(true)
  }

  const handleBless = async () => {
    const content = blessContent.trim()
    if (!content) {
      message.warning('写一句祝福吧')
      return
    }
    if (blessing) return
    setBlessing(true)
    try {
      const res = await createInteraction(wishId, { type: 'BLESS', content })
      if (res.data.success) {
        // 先关闭弹窗再做后续刷新，确保成功后弹窗立即关闭，避免重复提交
        setBlessModalOpen(false)
        onCountsChange({ blessCount: res.data.data.blessCount })
        refreshMyInteractions()
        message.success('祝福已送达 🌟')
      }
    } catch (err) {
      handleBusinessError(err, '祝福')
    } finally {
      setBlessing(false)
    }
  }

  const sameWishButton = mySameWish ? (
    <Popconfirm
      title="取消同求？"
      description="取消后可重新同求，已消耗星光不退还"
      okText="取消同求"
      cancelText="保留"
      onConfirm={handleRevokeSameWish}
    >
      <Button
        className={`${styles.interactBtn} ${styles.interactActive}`}
        icon={<TeamOutlined />}
        loading={revoking}
        aria-label={`已同求，共同愿望 ${counts.sameWishCount} 人，点击取消同求`}
      >
        已同求 {counts.sameWishCount}
      </Button>
    </Popconfirm>
  ) : (
    <Button
      className={styles.interactBtn}
      icon={<TeamOutlined />}
      loading={sameWishing}
      onClick={() => requireLoginOr(handleSameWish)}
      aria-label={`同求，共同愿望 ${counts.sameWishCount} 人`}
    >
      同求 {counts.sameWishCount}
    </Button>
  )

  return (
    <div className={styles.interactionBar}>
      <div className={styles.interactionButtons}>
        <Button
          className={styles.interactBtn}
          icon={<BulbOutlined />}
          loading={lighting}
          onClick={() => requireLoginOr(handleLight)}
          aria-label={`点亮，已点亮 ${counts.lightCount} 次`}
        >
          点亮 {counts.lightCount}
          {myLightCount > 0 && <span className={styles.mineCount}>（我 {myLightCount}）</span>}
        </Button>

        {sameWishButton}

        <Tooltip title={blessedToday ? '今日已祝福过，明天再来吧' : ''}>
          <Button
            className={styles.interactBtn}
            icon={blessedToday ? <StarOutlined /> : <GiftOutlined />}
            disabled={blessedToday}
            onClick={() => requireLoginOr(openBlessModal)}
            aria-label={
              blessedToday
                ? `今日已祝福，累计祝福 ${counts.blessCount} 次`
                : `送出祝福，累计祝福 ${counts.blessCount} 次`
            }
          >
            {blessedToday ? '已祝福' : '祝福'} {counts.blessCount}
          </Button>
        </Tooltip>

        <Tooltip
          title={
            myAnonStar
              ? '已为该心愿送出匿名星光'
              : `匿名支持 TA：消耗 ${ANON_STAR_COST} 星光，TA 不会知道你是谁（每个心愿 1 次，每日 3 次）`
          }
        >
          {myAnonStar ? (
            <Button
              className={styles.interactBtn}
              icon={<EyeInvisibleOutlined />}
              disabled
              aria-label={`已送匿名星光，累计匿名星光 ${counts.anonStarCount} 次`}
            >
              已送星光 {counts.anonStarCount}
            </Button>
          ) : (
            <Popconfirm
              title={`匿名送出星光？`}
              description={`将消耗 ${ANON_STAR_COST} 星光，TA 只会看到"神秘星人"送来的光`}
              okText={`送出 ${ANON_STAR_COST} 星光`}
              cancelText="再想想"
              onConfirm={() => requireLoginOr(handleAnonStar)}
            >
              <Button
                className={styles.interactBtn}
                icon={<EyeInvisibleOutlined />}
                loading={anonStarring}
                aria-label={`匿名送出星光，累计匿名星光 ${counts.anonStarCount} 次`}
              >
                匿名星光 {counts.anonStarCount}
              </Button>
            </Popconfirm>
          )}
        </Tooltip>
      </div>

      {/* 点亮微光粒子特效（呼吸灯动效，key 重放动画） */}
      <span key={burstKey} className={styles.burst} aria-hidden="true">
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <span key={i} className={styles.spark} data-index={i} />
        ))}
      </span>

      <Modal
        title="送出祝福 🌟"
        open={blessModalOpen}
        onCancel={() => setBlessModalOpen(false)}
        onOk={handleBless}
        okText="送出祝福"
        cancelText="取消"
        confirmLoading={blessing}
        okButtonProps={{ disabled: !blessContent.trim() }}
      >
        <p className={styles.blessHint}>
          写下你的祝福，愿 TA 梦想成真（{BLESS_CONTENT_MAX} 字以内）
        </p>
        <Input.TextArea
          value={blessContent}
          onChange={(e) => setBlessContent(e.target.value.slice(0, BLESS_CONTENT_MAX))}
          placeholder="例如：希望你梦想成真！"
          rows={3}
          maxLength={BLESS_CONTENT_MAX}
          showCount
          autoFocus
        />
      </Modal>
    </div>
  )
}
