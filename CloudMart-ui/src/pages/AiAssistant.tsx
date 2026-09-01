import { useCallback, useEffect, useRef, useState } from 'react'
import { App, Button, Checkbox, Input, Modal, Popconfirm, Spin, Switch, Tag } from 'antd'
import {
  ArrowLeftOutlined,
  BarChartOutlined,
  CheckOutlined,
  CloseOutlined,
  ReloadOutlined,
  RobotOutlined,
  RocketOutlined,
} from '@ant-design/icons'
import { history, useSearchParams } from 'umi'
import {
  breakdownGoal,
  createAiGoals,
  getConsentStatus,
  getWishDetail,
  grantConsent,
  listMyAiGoals,
  updateAiGoalStatus,
  updateNotificationPreferences,
  getNotificationPreferences,
  type AiBreakdownGoal,
  type AiBreakdownResult,
  type AiGoal,
  type AiGoalStatus,
  type NotificationChannel,
  type NotificationPreferenceMatrix,
  type NotificationType,
} from '@/api/wish'
import { useAuthStore } from '@/stores/auth'
import WishBGM from '@/components/WishBGM'
import styles from './AiAssistant.module.css'

const { TextArea } = Input

/** AI 数据处理协议版本（协议文本管理模块上线前为静态版本，与树洞页一致） */
const AI_CONSENT_VERSION = 'v1.0'
const MAX_TEXT = 1000

const GOAL_STATUS_LABEL: Record<AiGoalStatus, string> = {
  PENDING: '待开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const GOAL_STATUS_COLOR: Record<AiGoalStatus, string> = {
  PENDING: 'default',
  IN_PROGRESS: 'processing',
  COMPLETED: 'success',
  CANCELLED: 'warning',
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

export default function AiAssistant() {
  const [searchParams] = useSearchParams()
  const { message } = App.useApp()
  const { user } = useAuthStore()

  /** 预期管理通知「调整目标」深链携带的心愿 ID */
  const wishIdParam = searchParams.get('wishId')
  const wishId = wishIdParam ? Number(wishIdParam) : undefined

  const [text, setText] = useState('')
  const [breaking, setBreaking] = useState(false)
  const [breakdown, setBreakdown] = useState<AiBreakdownResult | null>(null)
  const [selectedGoalTitles, setSelectedGoalTitles] = useState<Set<string>>(new Set())
  const [savingGoals, setSavingGoals] = useState(false)

  const [goals, setGoals] = useState<AiGoal[]>([])
  const [goalsLoading, setGoalsLoading] = useState(true)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [actingGoalId, setActingGoalId] = useState<number | string | null>(null)

  const [matrix, setMatrix] = useState<NotificationPreferenceMatrix | null>(null)
  const [matrixLoading, setMatrixLoading] = useState(true)
  const [batchUpdating, setBatchUpdating] = useState(false)

  const [consentOpen, setConsentOpen] = useState(false)
  const [consentAgreeing, setConsentAgreeing] = useState(false)
  const pendingTextRef = useRef<string | null>(null)

  /** 预期管理深链：预填到期心愿内容，便于 AI 重新拆解 */
  useEffect(() => {
    if (!wishId) return
    let cancelled = false
    getWishDetail(wishId)
      .then((res) => {
        if (cancelled || !res.data.success) return
        const detail = res.data.data
        if (detail) {
          setText(`${detail.title}${detail.description ? `\n${detail.description}` : ''}`)
        }
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
      const res = await listMyAiGoals({ cursor, pageSize: 20 })
      const items = res.data.data ?? []
      setGoals((prev) => (cursor ? [...prev, ...items] : items))
      setNextCursor(res.data.meta?.nextCursor ?? null)
    } catch {
      // 列表加载失败不阻断拆解主流程
    } finally {
      if (cursor) setLoadingMore(false)
      else setGoalsLoading(false)
    }
  }, [])

  const loadMatrix = useCallback(async () => {
    try {
      const res = await getNotificationPreferences()
      if (res.data.success) setMatrix(res.data.data)
    } catch {
      // 偏好加载失败仅隐藏开关，不提示
    } finally {
      setMatrixLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!user) return
    loadGoals()
    loadMatrix()
  }, [user, loadGoals, loadMatrix])

  const handleBreakdown = useCallback(
    async (rawText?: string) => {
      const value = (rawText ?? text).trim()
      if (!value) {
        message.warning('先描述一下你想实现什么吧')
        return
      }
      if (breaking) return
      if (!user) {
        history.push('/login?redirect=/wish/assistant')
        return
      }

      // 同意状态前置检查，未同意弹协议；接口查询失败由后端 403 兜底
      try {
        const statusRes = await getConsentStatus()
        if (statusRes.data.success && !statusRes.data.data.granted) {
          pendingTextRef.current = value
          setConsentOpen(true)
          return
        }
      } catch {
        // 继续发送
      }

      setBreaking(true)
      try {
        const res = await breakdownGoal({ text: value, wishId })
        if (res.data.success) {
          setBreakdown(res.data.data)
          setSelectedGoalTitles(new Set(res.data.data.goals.map((g) => g.title)))
        }
      } catch (err) {
        const code = (err as { code?: string })?.code
        if (code === 'WISH_CONSENT_REQUIRED') {
          pendingTextRef.current = value
          setConsentOpen(true)
        } else if (code === 'WISH_AI_RATE_LIMITED') {
          message.warning('今天的拆解次数用完了，明天再来吧（每日 10 次）')
        } else if (code === 'WISH_AI_UNAVAILABLE') {
          message.warning('助手暂时走神了，请稍后再试')
        }
      } finally {
        setBreaking(false)
      }
    },
    [text, breaking, user, wishId, message],
  )

  /** 同意 AI 数据处理协议后自动拆解待发文本 */
  const handleConsentAgree = async () => {
    setConsentAgreeing(true)
    try {
      const res = await grantConsent({
        consentType: 'AI_DATA_PROCESSING',
        version: AI_CONSENT_VERSION,
        action: 'GRANT',
      })
      if (res.data.success) {
        setConsentOpen(false)
        message.success('感谢信任，助手会认真帮你规划')
        const pending = pendingTextRef.current
        pendingTextRef.current = null
        if (pending) {
          await handleBreakdown(pending)
        }
      }
    } catch {
      // 拦截器已提示
    } finally {
      setConsentAgreeing(false)
    }
  }

  const toggleGoalSelect = (title: string, checked: boolean) => {
    setSelectedGoalTitles((prev) => {
      const next = new Set(prev)
      if (checked) next.add(title)
      else next.delete(title)
      return next
    })
  }

  const handleSaveGoals = async () => {
    if (!breakdown) return
    const selected = breakdown.goals.filter((g) => selectedGoalTitles.has(g.title))
    if (selected.length === 0) {
      message.warning('至少勾选一个步骤')
      return
    }
    setSavingGoals(true)
    try {
      const res = await createAiGoals({
        sessionId: breakdown.sessionId,
        wishId,
        goals: selected,
      })
      if (res.data.success) {
        message.success(`已保存 ${res.data.data?.length ?? selected.length} 个步骤`)
        setBreakdown(null)
        setSelectedGoalTitles(new Set())
        loadGoals()
      }
    } catch (err) {
      const code = (err as { code?: string })?.code
      if (code === 'WISH_VALIDATION_ERROR') {
        message.warning('步骤内容超出限制，请调整后重试')
      }
    } finally {
      setSavingGoals(false)
    }
  }

  const handleGoalAction = async (goal: AiGoal, status: AiGoalStatus) => {
    setActingGoalId(goal.id)
    try {
      const res = await updateAiGoalStatus(goal.id, status)
      if (res.data.success) {
        setGoals((prev) => prev.map((g) => (g.id === goal.id ? { ...g, status } : g)))
      }
    } catch (err) {
      const code = (err as { code?: string })?.code
      if (code === 'WISH_AI_GOAL_STATUS_INVALID' || code === 'WISH_CONCURRENT_CONFLICT') {
        message.warning('状态已变化，已为你刷新')
        loadGoals()
      }
    } finally {
      setActingGoalId(null)
    }
  }

  const handleTogglePreference = async (type: NotificationType, channel: NotificationChannel, enabled: boolean) => {
    try {
      const res = await updateNotificationPreferences([{ type, channel, enabled }])
      if (res.data.success) setMatrix(res.data.data)
    } catch {
      // 拦截器已提示
    }
  }

  /** 一键批量设置全部类型 × 全渠道开关 */
  const handleBatchSetAll = async (enabled: boolean) => {
    setBatchUpdating(true)
    try {
      const updates = NOTIFICATION_TYPES.flatMap((type) =>
        CHANNELS.map(({ key }) => ({ type, channel: key, enabled })),
      )
      const res = await updateNotificationPreferences(updates)
      if (res.data.success) {
        setMatrix(res.data.data)
        message.success(enabled ? '已恢复所有提醒' : '已关闭所有提醒，愿你清净片刻')
      }
    } catch {
      // 拦截器已提示
    } finally {
      setBatchUpdating(false)
    }
  }

  const isGoalTerminal = (status: AiGoalStatus) => status === 'COMPLETED' || status === 'CANCELLED'
  const selectedCount = breakdown ? breakdown.goals.filter((g) => selectedGoalTitles.has(g.title)).length : 0

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.header}>
        <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => history.back()} className={styles.backBtn}>
          返回
        </Button>
        <div className={styles.headerTitle}>
          <RobotOutlined />
          <span>AI 心愿助手</span>
        </div>
        <Button type="text" icon={<BarChartOutlined />} onClick={() => history.push('/wish/annual-report')} className={styles.reportLink}>
          年度报告
        </Button>
      </div>

      <div className={styles.body}>
        <div className={styles.inputCard}>
          <p className={styles.intentLabel}>说出你的心愿或目标</p>
          <p className={styles.intentHint}>例如「我想减肥 10 斤」「想考研究生」，助手会帮你拆成可执行的步骤</p>
          <TextArea
            className={styles.intentInput}
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={MAX_TEXT}
            autoSize={{ minRows: 3, maxRows: 8 }}
            placeholder="把目标写在这里...（可选：关联到期的心愿会自动带入）"
            disabled={breaking}
          />
          <div className={styles.inputFooter}>
            <span className={styles.dailyLimit}>每日 10 次 · 内容脱敏后才会发给 AI</span>
            <Button
              type="primary"
              icon={<RocketOutlined />}
              loading={breaking}
              onClick={() => handleBreakdown()}
            >
              {breaking ? '拆解中…' : '帮我拆解'}
            </Button>
          </div>
        </div>

        {breaking && (
          <div className={styles.sectionCard} style={{ textAlign: 'center', padding: 32 }}>
            <Spin />
            <p className={styles.emptyGoals}>助手正在思考，请稍候…</p>
          </div>
        )}

        {breakdown && !breaking && (
          <div className={styles.breakdownCard}>
            <span className={styles.breakdownIntent}>{breakdown.intent}</span>
            {breakdown.goals.map((goal: AiBreakdownGoal) => (
              <div key={goal.title} className={styles.goalItem}>
                <Checkbox
                  className={styles.goalCheck}
                  checked={selectedGoalTitles.has(goal.title)}
                  onChange={(e) => toggleGoalSelect(goal.title, e.target.checked)}
                />
                <div className={styles.goalText}>
                  <p className={styles.goalTitle}>{goal.title}</p>
                  <p className={styles.goalDesc}>{goal.description}</p>
                  <p className={styles.goalMeta}>
                    预计 {goal.estimatedDays} 天 · 优先级 {goal.priority}
                  </p>
                </div>
              </div>
            ))}
            {breakdown.suggestion && <p className={styles.breakdownSuggestion}>{breakdown.suggestion}</p>}
            <Button
              type="primary"
              className={styles.saveBtn}
              loading={savingGoals}
              disabled={selectedCount === 0}
              onClick={handleSaveGoals}
            >
              保存勾选的 {selectedCount} 个步骤
            </Button>
          </div>
        )}

        <div className={styles.sectionCard}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>我的步骤计划</h3>
            <Button type="text" size="small" icon={<ReloadOutlined />} onClick={() => loadGoals()} />
          </div>
          {goalsLoading ? (
            <div style={{ textAlign: 'center', padding: 24 }}>
              <Spin />
            </div>
          ) : goals.length === 0 ? (
            <p className={styles.emptyGoals}>还没有步骤计划，先让助手帮你拆解一个目标吧</p>
          ) : (
            goals.map((goal) => (
              <div key={goal.id} className={`${styles.myGoalItem} ${isGoalTerminal(goal.status) ? styles.myGoalItemDone : ''}`}>
                <div className={styles.myGoalTop}>
                  <p className={styles.myGoalTitle}>{goal.title}</p>
                  <Tag color={GOAL_STATUS_COLOR[goal.status]}>{GOAL_STATUS_LABEL[goal.status]}</Tag>
                </div>
                <p className={styles.myGoalDesc}>{goal.description}</p>
                <p className={styles.goalMeta}>
                  预计 {goal.estimatedDays} 天 · 优先级 {goal.priority} · 创建于 {new Date(goal.createdAt).toLocaleDateString('zh-CN')}
                </p>
                <div className={styles.myGoalActions}>
                  {goal.status === 'PENDING' && (
                    <Button
                      size="small"
                      type="primary"
                      ghost
                      loading={actingGoalId === goal.id}
                      onClick={() => handleGoalAction(goal, 'IN_PROGRESS')}
                    >
                      开始
                    </Button>
                  )}
                  {goal.status === 'IN_PROGRESS' && (
                    <Button
                      size="small"
                      type="primary"
                      icon={<CheckOutlined />}
                      loading={actingGoalId === goal.id}
                      onClick={() => handleGoalAction(goal, 'COMPLETED')}
                    >
                      完成
                    </Button>
                  )}
                  {!isGoalTerminal(goal.status) && (
                    <Button
                      size="small"
                      icon={<CloseOutlined />}
                      loading={actingGoalId === goal.id}
                      onClick={() => handleGoalAction(goal, 'CANCELLED')}
                    >
                      放弃
                    </Button>
                  )}
                </div>
              </div>
            ))
          )}
          {nextCursor && (
            <Button className={styles.loadMoreBtn} loading={loadingMore} onClick={() => { setLoadingMore(true); loadGoals(nextCursor) }}>
              加载更多
            </Button>
          )}
        </div>

        <div className={styles.sectionCard}>
          <div className={styles.sectionHeader}>
            <h3 className={styles.sectionTitle}>提醒设置</h3>
          </div>
          {matrixLoading ? (
            <div style={{ textAlign: 'center', padding: 24 }}>
              <Spin />
            </div>
          ) : matrix ? (
            <>
              {NOTIFICATION_TYPES.map((type) => {
                const item = matrix.preferences.find((p) => p.type === type)
                return (
                  <div key={type} className={styles.prefRow}>
                    <span className={styles.prefType}>{NOTIFICATION_TYPE_LABEL[type]}</span>
                    <div className={styles.prefChannels}>
                      {CHANNELS.map(({ key, label }) => (
                        <label key={key} className={styles.prefChannelLabel}>
                          {label}
                          <Switch
                            size="small"
                            style={{ marginLeft: 4 }}
                            checked={item?.channels[key] ?? true}
                            onChange={(v) => handleTogglePreference(type, key, v)}
                          />
                        </label>
                      ))}
                    </div>
                  </div>
                )
              })}
              <div className={styles.prefBatchBtns}>
                <Popconfirm title="确定关闭所有提醒吗？" description="关闭后你将不再收到任何推送，可随时恢复" onConfirm={() => handleBatchSetAll(false)}>
                  <Button danger loading={batchUpdating}>
                    一键关闭所有提醒
                  </Button>
                </Popconfirm>
                <Button loading={batchUpdating} onClick={() => handleBatchSetAll(true)}>
                  全部恢复
                </Button>
              </div>
            </>
          ) : (
            <p className={styles.emptyGoals}>提醒设置加载失败，请稍后重试</p>
          )}
        </div>
      </div>

      <Modal
        open={consentOpen}
        title="AI 数据处理协议"
        onOk={handleConsentAgree}
        onCancel={() => {
          setConsentOpen(false)
          pendingTextRef.current = null
        }}
        confirmLoading={consentAgreeing}
        okText="同意并继续"
        cancelText="暂不使用"
      >
        <div>
          <p>在使用 AI 心愿助手前，请了解并同意：</p>
          <ul>
            <li>你输入的目标描述将在<b>脱敏处理后</b>发送给 AI 服务（通义千问）生成拆解步骤</li>
            <li>系统会自动移除手机号、邮箱、身份证号等个人信息</li>
            <li>对话记录仅你自己可见，可随时联系客服删除</li>
            <li>你可以随时在设置中撤回本同意</li>
          </ul>
        </div>
      </Modal>
      <WishBGM />
    </div>
  )
}
