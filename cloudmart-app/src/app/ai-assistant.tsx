import { View, Text, FlatList, TouchableOpacity, TextInput, ActivityIndicator, Alert, Switch } from 'react-native'
import { useCallback, useEffect, useRef, useState } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type {
  AiBreakdownResult,
  AiGoal,
  AiGoalStatus,
  NotificationChannel,
  NotificationPreferenceMatrix,
  NotificationType,
} from '@/types'

/** AI 数据处理协议版本（与树洞页一致） */
const AI_CONSENT_VERSION = 'v1.0'
const MAX_TEXT = 1000

const GOAL_STATUS_LABEL: Record<AiGoalStatus, string> = {
  PENDING: '待开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const GOAL_STATUS_COLOR: Record<AiGoalStatus, string> = {
  PENDING: WishColors.textTertiary,
  IN_PROGRESS: WishColors.accentCyan,
  COMPLETED: '#3ddc97',
  CANCELLED: '#ffb347',
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

/** 从 axios 异常体提取业务错误信封（与树洞页同模式） */
function extractBusinessError(error: unknown): { code?: string; message?: string } | undefined {
  return (error as { response?: { data?: { error?: { code?: string; message?: string } } } })
    ?.response?.data?.error
}

const isGoalTerminal = (status: AiGoalStatus) => status === 'COMPLETED' || status === 'CANCELLED'

export default function AiAssistantScreen() {
  const insets = useSafeAreaInsets()
  const params = useLocalSearchParams<{ wishId?: string }>()
  /** 预期管理通知「调整目标」深链携带的心愿 ID */
  const wishId = params.wishId ? Number(params.wishId) : undefined
  const user = useAuthStore((s) => s.user)

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
    if (!user) {
      setGoalsLoading(false)
      return
    }
    loadGoals()
    loadMatrix()
  }, [user, loadGoals, loadMatrix])

  const toastByCode = (code: string, fallbackMessage?: string) => {
    const title =
      code === 'WISH_AI_RATE_LIMITED'
        ? '今天的拆解次数用完了，明天再来吧（每日 10 次）'
        : code === 'WISH_AI_UNAVAILABLE'
          ? '助手暂时走神了，请稍后再试'
          : fallbackMessage || '操作失败，请稍后重试'
    Alert.alert('提示', title)
  }

  const handleBreakdown = useCallback(
    async (rawText?: string) => {
      const value = (rawText ?? text).trim()
      if (!value) {
        Alert.alert('提示', '先描述一下你想实现什么吧')
        return
      }
      if (breaking) return
      if (!user) {
        router.replace('/login')
        return
      }

      // 同意状态前置检查，未同意弹协议；查询失败由后端 403 兜底
      try {
        const statusRes = await wishApi.getConsentStatus()
        if (statusRes.data.success && !statusRes.data.data.granted) {
          pendingTextRef.current = value
          Alert.alert(
            'AI 数据处理协议',
            '在使用 AI 心愿助手前，请了解并同意：\n· 你输入的目标描述将在脱敏处理后发送给 AI 服务（通义千问）生成拆解步骤\n· 系统会自动移除手机号、邮箱、身份证号等个人信息\n· 对话记录仅你自己可见，可随时联系客服删除\n· 你可以随时撤回本同意',
            [
              { text: '暂不使用', style: 'cancel' },
              {
                text: '同意并继续',
                onPress: async () => {
                  try {
                    const grantRes = await wishApi.grantConsent({
                      consentType: 'AI_DATA_PROCESSING',
                      version: AI_CONSENT_VERSION,
                      action: 'GRANT',
                    })
                    if (grantRes.data.success) {
                      const pending = pendingTextRef.current
                      pendingTextRef.current = null
                      if (pending) await handleBreakdown(pending)
                    }
                  } catch {
                    toastByCode('')
                  }
                },
              },
            ],
          )
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
        const business = extractBusinessError(error)
        const code = business?.code ?? ''
        if (code === 'WISH_CONSENT_REQUIRED') {
          pendingTextRef.current = value
          Alert.alert(
            'AI 数据处理协议',
            '需要先同意 AI 数据处理协议才能使用拆解功能',
            [
              { text: '暂不使用', style: 'cancel' },
              {
                text: '同意并继续',
                onPress: () => {
                  // 同意后重试拆解
                  void (async () => {
                    try {
                      await wishApi.grantConsent({
                        consentType: 'AI_DATA_PROCESSING',
                        version: AI_CONSENT_VERSION,
                        action: 'GRANT',
                      })
                      await handleBreakdown(value)
                    } catch {
                      toastByCode('')
                    }
                  })()
                },
              },
            ],
          )
          return
        }
        toastByCode(code, business?.message)
      } finally {
        setBreaking(false)
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [text, breaking, user, wishId],
  )

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
      Alert.alert('提示', '至少勾选一个步骤')
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
        Alert.alert('完成', '步骤计划已保存')
        setBreakdown(null)
        setSelectedGoalTitles(new Set())
        loadGoals()
      }
    } catch {
      Alert.alert('提示', '保存失败，请稍后重试')
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
          Alert.alert('提示', '状态已变化，已为你刷新')
          loadGoals()
        }
      }
    } catch {
      Alert.alert('提示', '操作失败，请稍后重试')
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
  const handleBatchSetAll = (enabled: boolean) => {
    if (enabled) {
      void doBatchSetAll(true)
      return
    }
    Alert.alert('一键关闭所有提醒', '关闭后你将不再收到任何推送，可随时恢复', [
      { text: '取消', style: 'cancel' },
      { text: '确定', style: 'destructive', onPress: () => void doBatchSetAll(false) },
    ])
  }

  const doBatchSetAll = async (enabled: boolean) => {
    setBatchUpdating(true)
    try {
      const updates = NOTIFICATION_TYPES.flatMap((type) =>
        CHANNELS.map(({ key }) => ({ type, channel: key, enabled })),
      )
      const res = await wishApi.updateNotificationPreferences(updates)
      if (res.data.success) {
        setMatrix(res.data.data)
        Alert.alert('完成', enabled ? '已恢复所有提醒' : '已关闭所有提醒，愿你清净片刻')
      }
    } catch {
      Alert.alert('提示', '操作失败，请稍后重试')
    } finally {
      setBatchUpdating(false)
    }
  }

  const selectedCount = breakdown ? breakdown.goals.filter((g) => selectedGoalTitles.has(g.title)).length : 0

  const renderGoalItem = ({ item }: { item: AiGoal }) => (
    <View
      style={{
        borderWidth: 1,
        borderColor: WishColors.border,
        borderRadius: BorderRadius.lg,
        padding: Spacing.md,
        marginBottom: Spacing.sm,
        backgroundColor: 'rgba(255,255,255,0.04)',
        opacity: isGoalTerminal(item.status) ? 0.6 : 1,
      }}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ flex: 1, fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }}>{item.title}</Text>
        <View
          style={{
            marginLeft: Spacing.xs,
            paddingHorizontal: Spacing.sm,
            paddingVertical: 2,
            borderRadius: BorderRadius.sm,
            backgroundColor: 'rgba(255,255,255,0.08)',
          }}
        >
          <Text style={{ fontSize: FontSize.xs, color: GOAL_STATUS_COLOR[item.status] }}>
            {GOAL_STATUS_LABEL[item.status]}
          </Text>
        </View>
      </View>
      <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginTop: 4 }}>{item.description}</Text>
      <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 6 }}>
        预计 {item.estimatedDays} 天 · 创建于 {new Date(item.createdAt).toLocaleDateString('zh-CN')}
      </Text>
      <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.sm }}>
        {item.status === 'PENDING' && (
          <TouchableOpacity
            accessibilityLabel={`开始步骤：${item.title}`}
            onPress={() => handleGoalAction(item, 'IN_PROGRESS')}
            style={{
              paddingHorizontal: Spacing.lg,
              paddingVertical: 6,
              borderRadius: BorderRadius.full,
              borderWidth: 1,
              borderColor: 'rgba(233,69,96,0.5)',
            }}
          >
            <Text style={{ fontSize: FontSize.sm, color: WishColors.primary }}>
              {actingGoalId === item.id ? '处理中…' : '开始'}
            </Text>
          </TouchableOpacity>
        )}
        {item.status === 'IN_PROGRESS' && (
          <TouchableOpacity
            accessibilityLabel={`完成步骤：${item.title}`}
            onPress={() => handleGoalAction(item, 'COMPLETED')}
            style={{
              paddingHorizontal: Spacing.lg,
              paddingVertical: 6,
              borderRadius: BorderRadius.full,
              borderWidth: 1,
              borderColor: 'rgba(233,69,96,0.5)',
            }}
          >
            <Text style={{ fontSize: FontSize.sm, color: WishColors.primary }}>
              {actingGoalId === item.id ? '处理中…' : '完成'}
            </Text>
          </TouchableOpacity>
        )}
        {!isGoalTerminal(item.status) && (
          <TouchableOpacity
            accessibilityLabel={`放弃步骤：${item.title}`}
            onPress={() => handleGoalAction(item, 'CANCELLED')}
            style={{
              paddingHorizontal: Spacing.lg,
              paddingVertical: 6,
              borderRadius: BorderRadius.full,
              borderWidth: 1,
              borderColor: 'rgba(255,255,255,0.25)',
            }}
          >
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary }}>放弃</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  )

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      {/* 顶栏 */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingHorizontal: Spacing.md,
          paddingVertical: Spacing.sm,
          borderBottomWidth: 1,
          borderBottomColor: WishColors.border,
        }}
      >
        <TouchableOpacity onPress={() => router.back()} accessibilityLabel="返回">
          <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text }}>AI 心愿助手</Text>
        <TouchableOpacity onPress={() => router.push('/annual-report')} accessibilityLabel="年度报告">
          <Text style={{ fontSize: FontSize.sm, color: WishColors.accentGold }}>年度报告</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={goals}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderGoalItem}
        ListHeaderComponent={
          <View style={{ padding: Spacing.md, paddingBottom: 0 }}>
            {/* 意图输入 */}
            <View
              style={{
                backgroundColor: WishColors.bgContainer,
                borderWidth: 1,
                borderColor: WishColors.border,
                borderRadius: BorderRadius.xl,
                padding: Spacing.md,
                marginBottom: Spacing.md,
              }}
            >
              <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: 4 }}>
                说出你的心愿或目标
              </Text>
              <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginBottom: Spacing.sm }}>
                例如「我想减肥 10 斤」，助手会帮你拆成可执行的步骤
              </Text>
              <TextInput
                value={text}
                onChangeText={setText}
                maxLength={MAX_TEXT}
                multiline
                editable={!breaking}
                placeholder="把目标写在这里...（可选：关联到期的心愿会自动带入）"
                placeholderTextColor={WishColors.textTertiary}
                style={{
                  minHeight: 88,
                  textAlignVertical: 'top',
                  borderRadius: BorderRadius.md,
                  backgroundColor: 'rgba(255,255,255,0.06)',
                  borderWidth: 1,
                  borderColor: 'rgba(255,255,255,0.12)',
                  color: WishColors.text,
                  padding: Spacing.sm,
                  fontSize: FontSize.sm,
                }}
              />
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: Spacing.sm }}>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>每日 10 次 · 内容脱敏后才会发给 AI</Text>
                <TouchableOpacity
                  accessibilityLabel="帮我拆解"
                  onPress={() => handleBreakdown()}
                  disabled={breaking}
                  style={{
                    backgroundColor: breaking ? 'rgba(233,69,96,0.4)' : WishColors.primary,
                    paddingHorizontal: Spacing.lg,
                    paddingVertical: Spacing.sm,
                    borderRadius: BorderRadius.full,
                  }}
                >
                  <Text style={{ color: '#fff', fontSize: FontSize.sm }}>{breaking ? '拆解中…' : '帮我拆解'}</Text>
                </TouchableOpacity>
              </View>
            </View>

            {/* 拆解结果 */}
            {breakdown && !breaking && (
              <View
                style={{
                  backgroundColor: WishColors.bgContainer,
                  borderWidth: 1,
                  borderColor: 'rgba(233,69,96,0.3)',
                  borderRadius: BorderRadius.xl,
                  padding: Spacing.md,
                  marginBottom: Spacing.md,
                }}
              >
                <View
                  style={{
                    alignSelf: 'flex-start',
                    backgroundColor: 'rgba(255,215,0,0.12)',
                    borderRadius: BorderRadius.full,
                    paddingHorizontal: Spacing.md,
                    paddingVertical: 4,
                    marginBottom: Spacing.sm,
                  }}
                >
                  <Text style={{ fontSize: FontSize.sm, color: WishColors.accentGold, fontWeight: '600' }}>
                    {breakdown.intent}
                  </Text>
                </View>
                {breakdown.goals.map((goal) => {
                  const checked = selectedGoalTitles.has(goal.title)
                  return (
                    <TouchableOpacity
                      key={goal.title}
                      activeOpacity={0.8}
                      onPress={() => toggleGoalSelect(goal.title)}
                      accessibilityRole="checkbox"
                      accessibilityState={{ checked }}
                      style={{
                        flexDirection: 'row',
                        alignItems: 'flex-start',
                        gap: Spacing.sm,
                        backgroundColor: 'rgba(255,255,255,0.05)',
                        borderRadius: BorderRadius.md,
                        padding: Spacing.sm,
                        marginBottom: Spacing.xs,
                      }}
                    >
                      <View
                        style={{
                          width: 22,
                          height: 22,
                          borderRadius: 11,
                          borderWidth: 2,
                          borderColor: checked ? WishColors.primary : 'rgba(255,255,255,0.35)',
                          backgroundColor: checked ? WishColors.primary : 'transparent',
                          alignItems: 'center',
                          justifyContent: 'center',
                          marginTop: 2,
                        }}
                      >
                        {checked && <Text style={{ color: '#fff', fontSize: 12 }}>✓</Text>}
                      </View>
                      <View style={{ flex: 1 }}>
                        <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: WishColors.text }}>{goal.title}</Text>
                        <Text style={{ fontSize: FontSize.xs, color: WishColors.textSecondary, marginTop: 2 }}>
                          {goal.description}
                        </Text>
                        <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 4 }}>
                          预计 {goal.estimatedDays} 天 · 优先级 {goal.priority}
                        </Text>
                      </View>
                    </TouchableOpacity>
                  )
                })}
                {breakdown.suggestion && (
                  <Text
                    style={{
                      fontSize: FontSize.sm,
                      color: WishColors.textSecondary,
                      borderLeftWidth: 3,
                      borderLeftColor: WishColors.primary,
                      paddingLeft: Spacing.sm,
                      marginTop: Spacing.sm,
                    }}
                  >
                    {breakdown.suggestion}
                  </Text>
                )}
                <TouchableOpacity
                  accessibilityLabel="保存勾选步骤"
                  onPress={handleSaveGoals}
                  disabled={selectedCount === 0 || savingGoals}
                  style={{
                    marginTop: Spacing.sm,
                    backgroundColor: selectedCount === 0 ? 'rgba(233,69,96,0.4)' : WishColors.primary,
                    borderRadius: BorderRadius.full,
                    paddingVertical: Spacing.sm,
                    alignItems: 'center',
                  }}
                >
                  <Text style={{ color: '#fff', fontSize: FontSize.sm }}>
                    {savingGoals ? '保存中...' : `保存勾选的 ${selectedCount} 个步骤`}
                  </Text>
                </TouchableOpacity>
              </View>
            )}

            {/* 我的步骤计划标题 */}
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
              <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text }}>我的步骤计划</Text>
              <TouchableOpacity onPress={() => loadGoals()} accessibilityLabel="刷新列表">
                <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>刷新</Text>
              </TouchableOpacity>
            </View>
            {goalsLoading && <ActivityIndicator color={WishColors.primary} style={{ marginVertical: Spacing.lg }} />}
            {!goalsLoading && goals.length === 0 && (
              <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary, textAlign: 'center', paddingVertical: Spacing.lg }}>
                还没有步骤计划，先让助手帮你拆解一个目标吧
              </Text>
            )}

            {/* 提醒设置 */}
            {matrix && (
              <View
                style={{
                  backgroundColor: WishColors.bgContainer,
                  borderWidth: 1,
                  borderColor: WishColors.border,
                  borderRadius: BorderRadius.xl,
                  padding: Spacing.md,
                  marginTop: Spacing.md,
                  marginBottom: Spacing.sm,
                }}
              >
                <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: Spacing.sm }}>
                  提醒设置
                </Text>
                {NOTIFICATION_TYPES.map((type) => {
                  const item = matrix.preferences.find((p) => p.type === type)
                  return (
                    <View
                      key={type}
                      style={{
                        flexDirection: 'row',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        paddingVertical: 6,
                      }}
                    >
                      <Text style={{ fontSize: FontSize.sm, color: WishColors.text }}>{NOTIFICATION_TYPE_LABEL[type]}</Text>
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                        {CHANNELS.map(({ key, label }) => (
                          <View key={key} style={{ alignItems: 'center' }}>
                            <Switch
                              value={item?.channels[key] ?? true}
                              onValueChange={(v) => handleTogglePreference(type, key, v)}
                              trackColor={{ false: 'rgba(255,255,255,0.2)', true: WishColors.primary }}
                              thumbColor="#fff"
                              style={{ transform: [{ scale: 0.7 }] }}
                            />
                            <Text style={{ fontSize: 10, color: WishColors.textTertiary }}>{label}</Text>
                          </View>
                        ))}
                      </View>
                    </View>
                  )
                })}
                <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.sm }}>
                  <TouchableOpacity
                    accessibilityLabel="一键关闭所有提醒"
                    onPress={() => handleBatchSetAll(false)}
                    disabled={batchUpdating}
                    style={{
                      flex: 1,
                      borderWidth: 1,
                      borderColor: 'rgba(233,69,96,0.6)',
                      borderRadius: BorderRadius.full,
                      paddingVertical: Spacing.sm,
                      alignItems: 'center',
                    }}
                  >
                    <Text style={{ color: WishColors.primary, fontSize: FontSize.sm }}>一键关闭所有提醒</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    accessibilityLabel="全部恢复提醒"
                    onPress={() => handleBatchSetAll(true)}
                    disabled={batchUpdating}
                    style={{
                      flex: 1,
                      borderWidth: 1,
                      borderColor: 'rgba(255,255,255,0.25)',
                      borderRadius: BorderRadius.full,
                      paddingVertical: Spacing.sm,
                      alignItems: 'center',
                    }}
                  >
                    <Text style={{ color: WishColors.textSecondary, fontSize: FontSize.sm }}>全部恢复</Text>
                  </TouchableOpacity>
                </View>
              </View>
            )}
          </View>
        }
        ListEmptyComponent={
          goalsLoading ? null : <View />
        }
        ListFooterComponent={
          nextCursor ? (
            <TouchableOpacity
              onPress={() => loadGoals(nextCursor)}
              style={{ alignItems: 'center', paddingVertical: Spacing.md }}
            >
              <Text style={{ color: WishColors.accentCyan, fontSize: FontSize.sm }}>加载更多</Text>
            </TouchableOpacity>
          ) : (
            <View style={{ height: Spacing.xxl }} />
          )
        }
        contentContainerStyle={{ paddingBottom: insets.bottom + Spacing.lg }}
      />
    </View>
  )
}
