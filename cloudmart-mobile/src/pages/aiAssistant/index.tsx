import { useCallback, useEffect, useRef, useState } from 'react'
import { Switch, Text, Textarea, View } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import type { AiBreakdownResult, AiGoal, AiGoalStatus, NotificationChannel, NotificationPreferenceMatrix, NotificationType } from '@/types'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import { useAuthStore } from '@/store/auth'
import styles from './index.module.scss'

/** AI 数据处理协议版本（与树洞页一致） */
const AI_CONSENT_VERSION = 'v1.0'
const MAX_TEXT = 1000

const GOAL_STATUS_LABEL: Record<AiGoalStatus, string> = {
  PENDING: '待开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const GOAL_STATUS_CLASS: Record<AiGoalStatus, string> = {
  PENDING: styles.statusTag,
  IN_PROGRESS: styles.statusTagProgress,
  COMPLETED: styles.statusTagDone,
  CANCELLED: styles.statusTagCancel,
}

const NOTIFICATION_TYPE_LABEL: Record<NotificationType, string> = {
  WISH_COMMENT: '心愿评论',
  WISH_LIGHT: '心愿点亮',
  WISH_FULFILL: '心愿还愿',
  CAPSULE_OPEN: '胶囊开启',
  AI_REMINDER: '陪伴提醒',
  CHECKIN_REMINDER: '到期/打卡提醒',
  MATCH_RECOMMEND: '同愿推荐',
  BRAND_REWARD: '品牌奖励',
  ENCOUNTER_LETTER: '擦肩信笺',
  DEVICE_OFFLINE: '设备离线',
  LEVEL_UP: '等级提升',
  BADGE_EARNED: '徽章获得',
  SYSTEM: '系统通知',
}

const NOTIFICATION_TYPES = Object.keys(NOTIFICATION_TYPE_LABEL) as NotificationType[]

const CHANNELS: Array<{ key: NotificationChannel; label: string }> = [
  { key: 'IN_APP', label: '站内' },
  { key: 'PUSH', label: '推送' },
  { key: 'SMS', label: '短信' },
  { key: 'EMAIL', label: '邮件' },
]

const isGoalTerminal = (status: AiGoalStatus) => status === 'COMPLETED' || status === 'CANCELLED'

export default function AiAssistantPage() {
  const router = useRouter()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()

  /** 预期管理通知「调整目标」深链携带的心愿 ID */
  const wishIdParam = router.params.wishId
  const wishId = wishIdParam ? Number(wishIdParam) : undefined

  const [text, setText] = useState('')
  const [breaking, setBreaking] = useState(false)
  const [breakdown, setBreakdown] = useState<AiBreakdownResult | null>(null)
  const [selectedGoalTitles, setSelectedGoalTitles] = useState<Set<string>>(new Set())
  const [savingGoals, setSavingGoals] = useState(false)

  const [goals, setGoals] = useState<AiGoal[]>([])
  const [goalsLoading, setGoalsLoading] = useState(true)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [actingGoalId, setActingGoalId] = useState<number | null>(null)

  const [matrix, setMatrix] = useState<NotificationPreferenceMatrix | null>(null)
  const [batchUpdating, setBatchUpdating] = useState(false)

  const [consentVisible, setConsentVisible] = useState(false)
  const [consentAgreeing, setConsentAgreeing] = useState(false)
  const pendingTextRef = useRef<string | null>(null)

  /** 预期管理深链：预填到期心愿内容，便于 AI 重新拆解 */
  useEffect(() => {
    if (!wishId) return
    let cancelled = false
    wishApi
      .getWishDetail(wishId)
      .then((res) => {
        if (cancelled || !res.data.success || !res.data.data) return
        const detail = res.data.data
        setText(detail.description ? `${detail.title}\n${detail.description}` : detail.title)
      })
      .catch(() => {
        // 心愿不可见/已删除时静默，不影响自由输入
      })
    return () => {
      cancelled = true
    }
  }, [wishId])

  const loadGoals = useCallback(async (cursor?: string) => {
    try {
      const res = await wishApi.listMyAiGoals({ cursor, pageSize: 20 })
      const items = res.data.data ?? []
      setGoals((prev) => (cursor ? [...prev, ...items] : items))
      setNextCursor(res.data.meta?.nextCursor ?? null)
    } catch {
      // 列表加载失败不阻断拆解主流程
    } finally {
      setGoalsLoading(false)
    }
  }, [])

  const loadMatrix = useCallback(async () => {
    try {
      const res = await wishApi.getNotificationPreferences()
      if (res.data.success) setMatrix(res.data.data)
    } catch {
      // 偏好加载失败仅隐藏开关，不提示
    }
  }, [])

  useEffect(() => {
    if (!isLoggedIn) {
      setGoalsLoading(false)
      return
    }
    loadGoals()
    loadMatrix()
  }, [isLoggedIn, loadGoals, loadMatrix])

  /** 业务错误按 code 分发（信封 success=false 与 HTTP 非 2xx 两路） */
  const toastByCode = (code: string, fallbackMessage?: string) => {
    const title =
      code === 'WISH_AI_RATE_LIMITED'
        ? '今天的拆解次数用完了，明天再来吧（每日 10 次）'
        : code === 'WISH_AI_UNAVAILABLE'
          ? '助手暂时走神了，请稍后再试'
          : fallbackMessage || '操作失败，请稍后重试'
    Taro.showToast({ title, icon: 'none' })
  }

  const handleBreakdown = useCallback(
    async (rawText?: string) => {
      const value = (rawText ?? text).trim()
      if (!value) {
        Taro.showToast({ title: '先描述一下你想实现什么吧', icon: 'none' })
        return
      }
      if (breaking) return
      if (!isLoggedIn) {
        Taro.redirectTo({ url: '/pages/login/index' })
        return
      }

      // 同意状态前置检查，未同意弹协议；查询失败由后端 403 兜底
      try {
        const statusRes = await wishApi.getConsentStatus()
        if (statusRes.data.success && !statusRes.data.data.granted) {
          pendingTextRef.current = value
          setConsentVisible(true)
          return
        }
      } catch {
        // 继续发送
      }

      setBreaking(true)
      try {
        const res = await wishApi.breakdownGoal({ text: value, wishId })
        if (res.data.success && res.data.data) {
          setBreakdown(res.data.data)
          setSelectedGoalTitles(new Set(res.data.data.goals.map((g) => g.title)))
        } else {
          toastByCode(res.data.error?.code ?? '', res.data.error?.message)
        }
      } catch (error) {
        const body = (error as { data?: { error?: { code?: string; message?: string } } })?.data
        const code = body?.error?.code ?? ''
        if (code === 'WISH_CONSENT_REQUIRED') {
          pendingTextRef.current = value
          setConsentVisible(true)
          return
        }
        toastByCode(code, body?.error?.message)
      } finally {
        setBreaking(false)
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [text, breaking, isLoggedIn, wishId],
  )

  /** 同意 AI 数据处理协议后自动拆解待发文本 */
  const handleConsentAgree = async () => {
    setConsentAgreeing(true)
    try {
      const res = await wishApi.grantConsent({
        consentType: 'AI_DATA_PROCESSING',
        version: AI_CONSENT_VERSION,
        action: 'GRANT',
      })
      if (res.data.success) {
        setConsentVisible(false)
        Taro.showToast({ title: '助手会认真帮你规划', icon: 'none' })
        const pending = pendingTextRef.current
        pendingTextRef.current = null
        if (pending) {
          await handleBreakdown(pending)
        }
      }
    } catch {
      toastByCode('')
    } finally {
      setConsentAgreeing(false)
    }
  }

  const toggleGoalSelect = (title: string) => {
    setSelectedGoalTitles((prev) => {
      const next = new Set(prev)
      if (next.has(title)) next.delete(title)
      else next.add(title)
      return next
    })
  }

  const handleSaveGoals = async () => {
    if (!breakdown) return
    const selected = breakdown.goals.filter((g) => selectedGoalTitles.has(g.title))
    if (selected.length === 0) {
      Taro.showToast({ title: '至少勾选一个步骤', icon: 'none' })
      return
    }
    setSavingGoals(true)
    try {
      const res = await wishApi.createAiGoals({
        sessionId: breakdown.sessionId,
        wishId,
        goals: selected,
      })
      if (res.data.success) {
        Taro.showToast({ title: '步骤计划已保存', icon: 'success' })
        setBreakdown(null)
        setSelectedGoalTitles(new Set())
        loadGoals()
      }
    } catch {
      Taro.showToast({ title: '保存失败，请稍后重试', icon: 'none' })
    } finally {
      setSavingGoals(false)
    }
  }

  const handleGoalAction = async (goal: AiGoal, status: AiGoalStatus) => {
    setActingGoalId(goal.id)
    try {
      const res = await wishApi.updateAiGoalStatus(goal.id, status)
      if (res.data.success) {
        setGoals((prev) => prev.map((g) => (g.id === goal.id ? { ...g, status } : g)))
      } else {
        const code = res.data.error?.code ?? ''
        if (code === 'WISH_AI_GOAL_STATUS_INVALID' || code === 'WISH_CONCURRENT_CONFLICT') {
          Taro.showToast({ title: '状态已变化，已为你刷新', icon: 'none' })
          loadGoals()
        }
      }
    } catch {
      Taro.showToast({ title: '操作失败，请稍后重试', icon: 'none' })
    } finally {
      setActingGoalId(null)
    }
  }

  const handleTogglePreference = async (type: NotificationType, channel: NotificationChannel, enabled: boolean) => {
    try {
      const res = await wishApi.updateNotificationPreferences([{ type, channel, enabled }])
      if (res.data.success) setMatrix(res.data.data)
    } catch {
      // 静默
    }
  }

  /** 一键批量设置全部类型 × 全渠道开关 */
  const handleBatchSetAll = async (enabled: boolean) => {
    setBatchUpdating(true)
    try {
      const updates = NOTIFICATION_TYPES.flatMap((type) =>
        CHANNELS.map(({ key }) => ({ type, channel: key, enabled })),
      )
      const res = await wishApi.updateNotificationPreferences(updates)
      if (res.data.success) {
        setMatrix(res.data.data)
        Taro.showToast({ title: enabled ? '已恢复所有提醒' : '已关闭所有提醒', icon: 'none' })
      }
    } catch {
      Taro.showToast({ title: '操作失败，请稍后重试', icon: 'none' })
    } finally {
      setBatchUpdating(false)
    }
  }

  const selectedCount = breakdown ? breakdown.goals.filter((g) => selectedGoalTitles.has(g.title)).length : 0

  return (
    <View className={styles.container}>
      <CustomNavBar title="AI 心愿助手" back />
      <View style={{ paddingTop: `${statusBarHeight + navBarHeight}px` }}>
        <View className={styles.reportLink} onClick={() => Taro.navigateTo({ url: '/pages/annualReport/index' })}>
          <Text className={styles.reportLinkText}>查看年度报告 ›</Text>
        </View>
        <View className={styles.body}>
          <View className={styles.card}>
            <Text className={styles.cardTitle}>说出你的心愿或目标</Text>
            <Text className={styles.hint}>例如「我想减肥 10 斤」，助手会帮你拆成可执行的步骤</Text>
            <Textarea
              className={styles.input}
              value={text}
              onInput={(e) => setText(e.detail.value)}
              maxlength={MAX_TEXT}
              placeholder="把目标写在这里...（可选：关联到期的心愿会自动带入）"
              disabled={breaking}
              autoHeight
            />
            <View className={styles.inputFooter}>
              <Text className={styles.dailyLimit}>每日 10 次 · 内容脱敏后才会发给 AI</Text>
              <View
                className={`${styles.primaryBtn} ${breaking || !text.trim() ? styles.primaryBtnDisabled : ''}`}
                onClick={() => handleBreakdown()}
              >
                <Text className={styles.primaryBtnText}>{breaking ? '拆解中…' : '帮我拆解'}</Text>
              </View>
            </View>
          </View>

          {breakdown && !breaking && (
            <View className={styles.card}>
              <View className={styles.intentTag}>
                <Text>{breakdown.intent}</Text>
              </View>
              {breakdown.goals.map((goal) => {
                const checked = selectedGoalTitles.has(goal.title)
                return (
                  <View key={goal.title} className={styles.goalItem} onClick={() => toggleGoalSelect(goal.title)}>
                    <View className={`${styles.checkCircle} ${checked ? styles.checkCircleChecked : ''}`}>
                      <Text>✓</Text>
                    </View>
                    <View className={styles.goalText}>
                      <Text className={styles.goalTitle}>{goal.title}</Text>
                      <Text className={styles.goalDesc}>{goal.description}</Text>
                      <Text className={styles.goalMeta}>
                        预计 {goal.estimatedDays} 天 · 优先级 {goal.priority}
                      </Text>
                    </View>
                  </View>
                )
              })}
              {breakdown.suggestion && (
                <View className={styles.suggestion}>
                  <Text>{breakdown.suggestion}</Text>
                </View>
              )}
              <View
                className={`${styles.saveBtn} ${selectedCount === 0 || savingGoals ? styles.primaryBtnDisabled : ''}`}
                onClick={handleSaveGoals}
              >
                <Text className={styles.primaryBtnText}>
                  {savingGoals ? '保存中...' : `保存勾选的 ${selectedCount} 个步骤`}
                </Text>
              </View>
            </View>
          )}

          <View className={styles.card}>
            <Text className={styles.cardTitle}>我的步骤计划</Text>
            {goalsLoading ? (
              <Text className={styles.emptyText}>加载中...</Text>
            ) : goals.length === 0 ? (
              <Text className={styles.emptyText}>还没有步骤计划，先让助手帮你拆解一个目标吧</Text>
            ) : (
              goals.map((goal) => (
                <View key={goal.id} className={styles.goalItem}>
                  <View className={styles.goalText}>
                    <View style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <Text className={styles.goalTitle}>{goal.title}</Text>
                      <Text className={GOAL_STATUS_CLASS[goal.status]}>{GOAL_STATUS_LABEL[goal.status]}</Text>
                    </View>
                    <Text className={styles.goalDesc}>{goal.description}</Text>
                    <Text className={styles.goalMeta}>
                      预计 {goal.estimatedDays} 天 · 创建于 {new Date(goal.createdAt).toLocaleDateString('zh-CN')}
                    </Text>
                    <View className={styles.myGoalActions}>
                      {goal.status === 'PENDING' && (
                        <View
                          className={`${styles.actionBtn} ${actingGoalId === goal.id ? styles.actionBtnDisabled : ''}`}
                          onClick={() => handleGoalAction(goal, 'IN_PROGRESS')}
                        >
                          <Text className={styles.actionBtnText}>开始</Text>
                        </View>
                      )}
                      {goal.status === 'IN_PROGRESS' && (
                        <View
                          className={`${styles.actionBtn} ${actingGoalId === goal.id ? styles.actionBtnDisabled : ''}`}
                          onClick={() => handleGoalAction(goal, 'COMPLETED')}
                        >
                          <Text className={styles.actionBtnText}>完成</Text>
                        </View>
                      )}
                      {!isGoalTerminal(goal.status) && (
                        <View
                          className={`${styles.actionBtn} ${actingGoalId === goal.id ? styles.actionBtnDisabled : ''}`}
                          onClick={() => handleGoalAction(goal, 'CANCELLED')}
                        >
                          <Text className={styles.actionBtnText}>放弃</Text>
                        </View>
                      )}
                    </View>
                  </View>
                </View>
              ))
            )}
            {nextCursor && (
              <View className={styles.actionBtn} onClick={() => loadGoals(nextCursor)}>
                <Text className={styles.actionBtnText}>加载更多</Text>
              </View>
            )}
          </View>

          {matrix && (
            <View className={styles.card}>
              <Text className={styles.cardTitle}>提醒设置</Text>
              {NOTIFICATION_TYPES.map((type, idx) => {
                const item = matrix.preferences.find((p) => p.type === type)
                return (
                  <View key={type} className={`${styles.prefRow} ${idx === NOTIFICATION_TYPES.length - 1 ? styles.prefRowLast : ''}`}>
                    <Text className={styles.prefType}>{NOTIFICATION_TYPE_LABEL[type]}</Text>
                    <View className={styles.prefChannels}>
                      {CHANNELS.map(({ key, label }) => (
                        <View key={key} className={styles.prefChannelLabel}>
                          <Text>{label}</Text>
                          <Switch
                            checked={item?.channels[key] ?? true}
                            onChange={(e) => handleTogglePreference(type, key, e.detail.value)}
                          />
                        </View>
                      ))}
                    </View>
                  </View>
                )
              })}
              <View className={styles.batchBtns}>
                <View
                  className={`${styles.dangerBtn} ${batchUpdating ? styles.actionBtnDisabled : ''}`}
                  onClick={() => handleBatchSetAll(false)}
                >
                  <Text className={styles.btnTextDanger}>一键关闭所有提醒</Text>
                </View>
                <View
                  className={`${styles.plainBtn} ${batchUpdating ? styles.actionBtnDisabled : ''}`}
                  onClick={() => handleBatchSetAll(true)}
                >
                  <Text className={styles.btnText}>全部恢复</Text>
                </View>
              </View>
            </View>
          )}
        </View>
      </View>

      {consentVisible && (
        <View className={styles.modalMask} onClick={() => setConsentVisible(false)}>
          <View className={styles.modalBody} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>AI 数据处理协议</Text>
            <View className={styles.modalContent}>
              <Text className={styles.modalText}>
                在使用 AI 心愿助手前，请了解并同意：{'\n'}
                · 你输入的目标描述将在脱敏处理后发送给 AI 服务（通义千问）生成拆解步骤{'\n'}
                · 系统会自动移除手机号、邮箱、身份证号等个人信息{'\n'}
                · 对话记录仅你自己可见，可随时联系客服删除{'\n'}
                · 你可以随时撤回本同意
              </Text>
            </View>
            <View className={styles.modalBtns}>
              <View className={styles.modalCancel} onClick={() => setConsentVisible(false)}>
                <Text>暂不使用</Text>
              </View>
              <View className={styles.modalOk} onClick={handleConsentAgree}>
                <Text>{consentAgreeing ? '提交中...' : '同意并继续'}</Text>
              </View>
            </View>
          </View>
        </View>
      )}

      <WishBGM />
    </View>
  )
}
