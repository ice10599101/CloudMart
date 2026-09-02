import { useCallback, useEffect, useRef, useState } from 'react'
import { App, Button, Empty, Switch, Tag } from 'antd'
import { history } from 'umi'
import {
  getNearbyMode,
  interactEncounterLetter,
  listEncounterLetters,
  reportTrace,
  readEncounterLetter,
  setNearbyMode as setNearbyModeApi,
  type EncounterLetterItem,
} from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import WishBGM from '@/components/WishBGM'
import { createTraceReporter, getBrowserPosition, type TraceReporter } from '@/utils/traceReporter'
import styles from './EncounterLetters.module.css'

/**
 * 擦肩而过信笺（Sprint 3.3 WEB 端）：
 * 附近模式开关 + 匿名信笺列表（PENDING 未拆封/DELIVERED 拆信/READ 已读）+
 * Web Animations API 拆信动效 + 诗意文案 + 匿名互动（祝福/点亮，不暴露身份）。
 */

const STATUS_LABEL: Record<EncounterLetterItem['status'], string> = {
  PENDING: '未拆封',
  DELIVERED: '待拆信',
  READ: '已读',
}

export default function EncounterLetters() {
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()

  // 开关状态：进入页面时从后端回显（Redis 开关键 24h 有效）
  const [nearbyMode, setNearbyModeState] = useState(false)
  const [letters, setLetters] = useState<EncounterLetterItem[]>([])
  const [loading, setLoading] = useState(true)
  /** 拆信动效中的信笺（信封展开动画 1.2s 后标记 READ） */
  const [opening, setOpening] = useState<number | null>(null)

  const loadLetters = useCallback(async () => {
    if (!user && !userLoading) {
      history.push('/login?redirect=/wish/encounters')
      return
    }
    setLoading(true)
    try {
      const res = await listEncounterLetters()
      if (res.data.success) setLetters(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    loadLetters()
  }, [loadLetters])

  useEffect(() => {
    if (!user) return
    // 回显附近模式开关（查询失败保持关闭态，不阻断页面）
    getNearbyMode()
      .then((res) => {
        if (res.data.success) setNearbyModeState(res.data.data === true)
      })
      .catch(() => undefined)
  }, [user])

  // 轨迹上报器（B8 数据源）：附近模式开启期间每 5 分钟上报一次当前位置
  const reporterRef = useRef<TraceReporter | null>(null)
  useEffect(() => {
    if (!nearbyMode) {
      reporterRef.current?.stop()
      reporterRef.current = null
      return
    }
    const reporter = createTraceReporter({
      report: async (pos) => { await reportTrace(pos.lat, pos.lng) },
      getPosition: getBrowserPosition,
      // 定位被拒等失败仅轻度提示一次，不打扰（下个周期自然重试）
      onError: () => undefined,
    })
    reporterRef.current = reporter
    reporter.start()
    return () => {
      reporter.stop()
      reporterRef.current = null
    }
  }, [nearbyMode])

  const handleModeToggle = async (enabled: boolean) => {
    try {
      // 先上报后端成功再更新本地开关（失败时 Switch 不变，由拦截器提示）
      await setNearbyModeApi(enabled)
      setNearbyModeState(enabled)
      message.success(enabled ? '附近模式已开启，缘分正在靠近' : '附近模式已关闭，轨迹立即停止上报')
    } catch {
      // 拦截器已提示
    }
  }

  /** 拆信：Web Animations API 驱动信封展开 → READ */
  const handleOpen = async (letter: EncounterLetterItem) => {
    setOpening(letter.letterId)
    try {
      const el = document.getElementById(`letter-${letter.letterId}`)
      if (el && typeof el.animate === 'function') {
        el.animate(
          [
            { transform: 'rotateY(0deg) scale(1)' },
            { transform: 'rotateY(180deg) scale(1.08)' },
            { transform: 'rotateY(0deg) scale(1.02)' },
          ],
          { duration: 900, easing: 'ease-in-out' },
        )
      }
      await new Promise((r) => {
        setTimeout(r, 900)
      })
      const res = await readEncounterLetter(letter.letterId)
      if (res.data.success) {
        setLetters((prev) => prev.map((l) => (l.letterId === letter.letterId
          ? { ...l, status: 'READ', content: res.data.data?.content ?? l.content } : l)))
      }
    } catch {
      // 拦截器已提示
    } finally {
      setOpening(null)
    }
  }

  const handleInteract = async (letter: EncounterLetterItem, type: 'BLESS' | 'LIGHT') => {
    try {
      await interactEncounterLetter(letter.letterId, type)
      message.success(type === 'BLESS' ? '祝福已匿名送达 💛' : '已匿名点亮 TA 的心愿 ⭐')
    } catch (err) {
      const code = (err as { code?: string })?.code
      if (code === 'WISH_RATE_LIMITED') message.warning('这封信笺今天已经互动过啦')
      else if (code === 'WISH_STARLIGHT_INSUFFICIENT') message.warning('星光不足，无法点亮')
    }
  }

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.body}>
        <div className={styles.headerBar}>
          <div>
            <h1 className={styles.pageTitle}>✉️ 相遇信笺</h1>
            <p className={styles.pageSubtitle}>和同路人擦肩而过的缘分 · 全程匿名 · 24 小时后轨迹自动消失</p>
          </div>
          <div className={styles.modeSwitch}>
            <span>附近模式</span>
            <Switch checked={nearbyMode} onChange={handleModeToggle} />
          </div>
        </div>

        {loading ? (
          <p className={styles.emptyText}>加载中...</p>
        ) : letters.length === 0 ? (
          <Empty description="还没有相遇信笺。开启附近模式，与同路人不期而遇" />
        ) : (
          <div className={styles.letterGrid}>
            {letters.map((letter) => {
              const isOpening = opening === letter.letterId
              return (
                <div
                  key={letter.letterId}
                  id={`letter-${letter.letterId}`}
                  className={`${styles.letterCard} ${isOpening ? styles.letterOpening : ''}`}
                >
                  <div className={styles.letterTop}>
                    <span className={styles.letterTag}>✉️ {STATUS_LABEL[letter.status]}</span>
                    <Tag>{letter.encounterGeohash6.slice(0, 4)} 片区</Tag>
                  </div>
                  <div className={styles.letterTags}>
                    {letter.wishTags.map((tag) => (
                      <Tag key={tag} color="gold">{tag}</Tag>
                    ))}
                  </div>
                  {letter.content ? (
                    <p className={styles.poetic}>{letter.content}</p>
                  ) : (
                    <p className={styles.sealedText}>🔒 这封信还没有送达，请稍候</p>
                  )}
                  <div className={styles.letterActions}>
                    {letter.status === 'DELIVERED' && (
                      <Button type="primary" size="small" onClick={() => handleOpen(letter)}>
                        拆信
                      </Button>
                    )}
                    {letter.status !== 'PENDING' && (
                      <>
                        <Button size="small" onClick={() => handleInteract(letter, 'BLESS')}>
                          匿名祝福 💛
                        </Button>
                        <Button size="small" onClick={() => handleInteract(letter, 'LIGHT')}>
                          点亮 TA 的心愿 ⭐（-2 星光）
                        </Button>
                      </>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
      <WishBGM />
    </div>
  )
}
