import { useCallback, useEffect, useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import { View, Text, Textarea } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { MyWishInteraction } from '@/types'
import styles from './index.module.scss'

/**
 * 心愿互动按钮组（Sprint 1.2；匿名星光 Sprint 2.6）。
 *
 * 规则：点亮可重复（每次扣 2 星光）；同求每愿望唯一（已同求呼吸灯高亮，可取消）；
 * 祝福每愿望每日 1 次（今日已祝福禁用）；匿名星光每愿望 1 次、每日 3 次（扣 5 星光，
 * 身份对作者保密）；未登录点击引导登录。
 */

const BLESS_CONTENT_MAX = 200
const ANON_STAR_COST = 5

export interface WishInteractionCounts {
  lightCount: number
  sameWishCount: number
  blessCount: number
  anonStarCount: number
}

interface WishInteractionBarProps {
  wishId: number
  counts: WishInteractionCounts
  isLoggedIn: boolean
  onCountsChange: (counts: Partial<WishInteractionCounts>) => void
  onRequireLogin: () => void
}

/** 业务错误载体（移动端 request 不统一弹错，组件自行 toast） */
interface ApiErrorLike {
  success: boolean
  error?: { code: string; message: string }
}

/** CSS 变量驱动粒子角度（避免 4 个粒子各写一套 keyframes） */
function sparkStyle(index: number): CSSProperties {
  const angles = [-50, -20, 20, 50]
  return { '--spark-angle': `${angles[index] ?? 0}deg` } as CSSProperties
}

export default function WishInteractionBar({
  wishId,
  counts,
  isLoggedIn,
  onCountsChange,
  onRequireLogin,
}: WishInteractionBarProps) {
  const [myInteractions, setMyInteractions] = useState<MyWishInteraction[]>([])
  const [blessPanelVisible, setBlessPanelVisible] = useState(false)
  const [blessContent, setBlessContent] = useState('')
  const [burstVisible, setBurstVisible] = useState(false)

  const refreshMyInteractions = useCallback(async () => {
    if (!isLoggedIn) {
      setMyInteractions([])
      return
    }
    try {
      const res = await wishApi.listMyInteractions(wishId)
      if (res.data.success) setMyInteractions(res.data.data)
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

  const requireLoginOr = (action: () => void) => {
    if (!isLoggedIn) {
      Taro.showToast({ title: '登录后即可互动', icon: 'none' })
      onRequireLogin()
      return
    }
    action()
  }

  /** 业务错误 toast（星光不足/限频/已互动），其余 code 统一提示 message */
  const toastBusinessError = (body: ApiErrorLike) => {
    const err = body.error ?? { code: '', message: '操作失败，请稍后重试' }
    const title = err.code === 'WISH_STARLIGHT_INSUFFICIENT'
      ? '星光不足，可通过签到/打卡获取'
      : err.code === 'WISH_ALREADY_INTERACTED'
        ? '已同求过该心愿'
        : err.message
    Taro.showToast({ title, icon: 'none' })
    if (err.code === 'WISH_ALREADY_INTERACTED') refreshMyInteractions()
  }

  const handleLight = async () => {
    const res = await wishApi.createInteraction(wishId, { type: 'LIGHT' })
    if (res.data.success) {
      onCountsChange({ lightCount: res.data.data.lightCount })
      refreshMyInteractions()
      setBurstVisible(true)
      Taro.showToast({ title: '已点亮 ✨', icon: 'none' })
    } else {
      toastBusinessError(res.data)
    }
  }

  const handleSameWish = async () => {
    const res = await wishApi.createInteraction(wishId, { type: 'SAME_WISH' })
    if (res.data.success) {
      onCountsChange({ sameWishCount: res.data.data.sameWishCount })
      refreshMyInteractions()
      Taro.showToast({ title: '已加入共同愿望 🤝', icon: 'none' })
    } else {
      toastBusinessError(res.data)
    }
  }

  /** 匿名星光：二次确认后送出（扣 5 星光，身份对作者保密） */
  const handleAnonStar = async () => {
    const confirmed = await Taro.showModal({
      title: '匿名送出星光？',
      content: `将消耗 ${ANON_STAR_COST} 星光，TA 只会看到「神秘星人」送来的光`,
      confirmText: `送出 ${ANON_STAR_COST} 星光`,
      cancelText: '再想想',
    })
    if (!confirmed.confirm) return

    const res = await wishApi.createInteraction(wishId, { type: 'ANON_STAR' })
    if (res.data.success) {
      onCountsChange({ anonStarCount: res.data.data.anonStarCount })
      refreshMyInteractions()
      Taro.showToast({ title: 'TA 眼中你是一颗神秘星辰 💫', icon: 'none' })
    } else {
      toastBusinessError(res.data)
    }
  }

  const handleRevokeSameWish = async () => {
    if (!mySameWish) return
    const confirmed = await Taro.showModal({
      title: '取消同求？',
      content: '取消后可重新同求，已消耗星光不退还',
    })
    if (!confirmed.confirm) return

    const res = await wishApi.revokeInteraction(wishId, mySameWish.id)
    if (res.data.success) {
      onCountsChange({ sameWishCount: Math.max(0, counts.sameWishCount - 1) })
      refreshMyInteractions()
      Taro.showToast({ title: '已取消同求', icon: 'none' })
    } else {
      toastBusinessError(res.data)
    }
  }

  const openBlessPanel = () => {
    setBlessContent('')
    setBlessPanelVisible(true)
  }

  const handleBless = async () => {
    const content = blessContent.trim()
    if (!content) {
      Taro.showToast({ title: '写一句祝福吧', icon: 'none' })
      return
    }
    const res = await wishApi.createInteraction(wishId, { type: 'BLESS', content })
    if (res.data.success) {
      onCountsChange({ blessCount: res.data.data.blessCount })
      refreshMyInteractions()
      setBlessPanelVisible(false)
      Taro.showToast({ title: '祝福已送达 🌟', icon: 'none' })
    } else {
      toastBusinessError(res.data)
    }
  }

  return (
    <View className={styles.interactionBar}>
      <View className={styles.buttonsRow}>
        <View className={styles.interactBtn} onClick={() => requireLoginOr(handleLight)}>
          <Text className={styles.btnIcon}>💡</Text>
          <Text className={styles.btnText}>
            点亮 {counts.lightCount}
            {myLightCount > 0 ? `（我 ${myLightCount}）` : ''}
          </Text>
          {burstVisible && (
            <View className={styles.burst} onAnimationEnd={() => setBurstVisible(false)}>
              {[0, 1, 2, 3].map((i) => (
                <View key={i} className={styles.spark} style={sparkStyle(i)} />
              ))}
            </View>
          )}
        </View>

        {mySameWish ? (
          <View
            className={`${styles.interactBtn} ${styles.interactActive}`}
            onClick={handleRevokeSameWish}
          >
            <Text className={styles.btnIcon}>🤝</Text>
            <Text className={styles.btnTextActive}>已同求 {counts.sameWishCount}</Text>
          </View>
        ) : (
          <View className={styles.interactBtn} onClick={() => requireLoginOr(handleSameWish)}>
            <Text className={styles.btnIcon}>🤝</Text>
            <Text className={styles.btnText}>同求 {counts.sameWishCount}</Text>
          </View>
        )}

        <View
          className={`${styles.interactBtn} ${blessedToday ? styles.interactDisabled : ''}`}
          onClick={() => !blessedToday && requireLoginOr(openBlessPanel)}
        >
          <Text className={styles.btnIcon}>{blessedToday ? '⭐' : '🌟'}</Text>
          <Text className={blessedToday ? styles.btnTextDisabled : styles.btnText}>
            {blessedToday ? '已祝福' : '祝福'} {counts.blessCount}
          </Text>
        </View>

        <View
          className={`${styles.interactBtn} ${myAnonStar ? styles.interactDisabled : ''}`}
          onClick={() => !myAnonStar && requireLoginOr(handleAnonStar)}
        >
          <Text className={styles.btnIcon}>{myAnonStar ? '💫' : '🌠'}</Text>
          <Text className={myAnonStar ? styles.btnTextDisabled : styles.btnText}>
            {myAnonStar ? '已送星光' : '匿名星光'} {counts.anonStarCount}
          </Text>
        </View>
      </View>

      {/* 祝福输入弹层（自绘底部面板） */}
      {blessPanelVisible && (
        <View className={styles.blessMask} onClick={() => setBlessPanelVisible(false)}>
          <View className={styles.blessPanel} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.blessTitle}>送出祝福 🌟</Text>
            <Text className={styles.blessHint}>
              愿 TA 梦想成真（{BLESS_CONTENT_MAX} 字以内）
            </Text>
            <Textarea
              className={styles.blessTextarea}
              value={blessContent}
              onInput={(e) => setBlessContent(e.detail.value.slice(0, BLESS_CONTENT_MAX))}
              maxlength={BLESS_CONTENT_MAX}
              placeholder='例如：希望你梦想成真！'
              autoFocus
              showConfirmBar={false}
            />
            <View className={styles.blessActions}>
              <View className={styles.blessCancel} onClick={() => setBlessPanelVisible(false)}>
                <Text className={styles.blessCancelText}>取消</Text>
              </View>
              <View
                className={`${styles.blessSubmit} ${!blessContent.trim() ? styles.blessSubmitDisabled : ''}`}
                onClick={() => blessContent.trim() && handleBless()}
              >
                <Text className={styles.blessSubmitText}>送出祝福</Text>
              </View>
            </View>
          </View>
        </View>
      )}
    </View>
  )
}
