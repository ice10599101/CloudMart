import { useCallback, useEffect, useRef, useState } from 'react'
import { View, Text, Button, ScrollView } from '@tarojs/components'
import Taro, { useRouter, useDidShow, useDidHide } from '@tarojs/taro'
import {
  petApi, petCompanionApi,
  type PetBottleResult,
  type PetInfo,
  type PetIntimacyInfo,
  type PetOnboardingProgress,
  type PetActionItem,
  type PetBootstrap,
} from '@/api/pet'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { useThemeClass } from '@/composables/useThemeClass'
import { PET_CREAM_STYLE, PET_CREAM_STAT_TONE, PET_CREAM_EXT_STYLE } from '@/styles/petCream'
import { PET_STAGE_URL } from '@/api/pet'
import { CreamButton, CreamChip, CreamStatBar, CreamMasthead, CreamSheet, CreamCard } from '@/components/pet-cream'
import cream from '@/components/pet-cream/pet-cream.module.scss'
import styles from './index.module.scss'
// R30：共享常量 / 纯函数 / hook
import {
  ACTION_LABEL,
  ACTIVITY_LABEL,
  GROWTH_STAGE_LABEL,
  MENU_GROUPS,
  PANELS,
  PANEL_TITLE,
  SPECIES_EMOJI,
  STATUS_LABEL,
  formatClock,
  friendlyError,
  parseResult,
  parseUtc,
  useCountdown,
  type PanelKey,
} from './constants'
// 面板组件（各自管理数据与动作，行为不变，见各 panels 文件）
import { DailyQuestPanel } from './panels/DailyQuestPanel'
import { ActivityCenterPanel } from './panels/ActivityCenterPanel'
import { SocialPanel } from './panels/SocialPanel'
import { MemoryPanel } from './panels/MemoryPanel'
import { HomeSheetPanel } from './panels/HomeSheetPanel'
import { WorkPanel } from './panels/WorkPanel'
import { StudyPanel } from './panels/StudyPanel'
import { BottlePanel, BottleResultModal } from './panels/BottlePanel'
import { BattlePanel } from './panels/BattlePanel'
import { ChatPanel } from './panels/ChatPanel'
import { RankingsPanel } from './panels/RankingsPanel'
import { RemindersPanel } from './panels/RemindersPanel'
import { AchievementsPanel } from './panels/AchievementsPanel'
import { CarePanel } from './panels/CarePanel'
import { AdoptView } from './AdoptView'
import { ProfileModal } from './ProfileModal'

/**
 * 社区宠物原生管理页（小程序/H5，实施文档 §5/§34）。
 *
 * 全平台可用的完整管理入口：领养/互动/打工/读书/捞瓶/对战/聊天/成就/提醒/档案/分享。
 * Cocos 舞台在独立页 petStage（H5 为完整双向桥；微信小程序 web-view 受平台限制只做
 * 观赏，游戏操作经 navigateTo 携 intent 回落本页执行，平台约束见实施文档 §2.4）。
 *
 * R30：各功能面板拆至 ./panels/，领养向导在 ./AdoptView，档案弹层在 ./ProfileModal，
 * 共享常量/纯函数在 ./constants.ts；本文件只保留外壳、主宠状态、心跳、照料动作与路由意图。
 */
export default function PetPage() {
  const { params } = useRouter()
  // PET-03：本实例内已消费意图（防 pet 状态刷新触发 effect 重跑时重复互动）
  const consumedIntentRef = useRef<Set<string>>(new Set())
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { user: currentUser } = useAuthStore()

  const [loading, setLoading] = useState(true)
  const [noPet, setNoPet] = useState(false)
  const [pet, setPet] = useState<PetInfo | null>(null)
  // P1-6：互动请求进行中锁——快速连点只发 1 个请求（服务端配额原子扣减，但每次点击都会真实消耗）
  const [acting, setActing] = useState(false)
  const [panel, setPanel] = useState<PanelKey | null>('home')
  const [intimacy, setIntimacy] = useState<PetIntimacyInfo | null>(null)
  // R18：bootstrap 启动快照——serverNow/businessDate/capabilities/quotas/
  // actionAvailability/pendingOperations 全部以后端为权威，失败置 null（不阻断主流程）
  const [bootstrap, setBootstrap] = useState<PetBootstrap | null>(null)
  const [onboarding, setOnboarding] = useState<PetOnboardingProgress | null>(null)
  // B06 动作可执行性（随主宠刷新；拉取失败按"不可知"隐藏，不打断主流程）
  const [actions, setActions] = useState<PetActionItem[]>([])
  const [reminderUnread, setReminderUnread] = useState(0)
  // 稀有/宠物/彩蛋瓶结果（主页倒计时领奖条与捞瓶面板共用领取链路）
  const [bottleResult, setBottleResult] = useState<PetBottleResult | null>(null)
  const [profileOpen, setProfileOpen] = useState(false)
  // 活动到期/主页领奖后递增，驱动打工/读书/捞瓶面板重新拉取
  const [reloadSignal, setReloadSignal] = useState(0)

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  // 三期：亲密度概览（展示型数据，失败保持原值）
  const loadIntimacy = useCallback(async () => {
    try {
      const { data: res } = await petApi.getIntimacy()
      setIntimacy(res.data)
    } catch {
      // 展示型数据：忽略
    }
  }, [])

  // P2-4：心跳连续失败提示（不再完全静默）——连续 3 次失败展示"陪伴计时可能不准确"，
  // 成功一次自动消失
  const [companionWarn, setCompanionWarn] = useState(false)
  const heartbeatFailuresRef = useRef(0)

  /**
   * 陪伴心跳：小程序在前台时每 60 秒上报一次（服务端按日封顶折算亲密度）。
   * 页面隐藏时不上报——"陪伴"必须是真实停留在宠物页。
   */
  // R23/T37：仅前台发心跳——useDidShow/useDidHide 维护可见性（注释声称的语义此前未实现）
  const pageVisibleRef = useRef(true)
  useDidShow(() => {
    pageVisibleRef.current = true
  })
  useDidHide(() => {
    pageVisibleRef.current = false
  })

  useEffect(() => {
    const timer = setInterval(() => {
      if (!pageVisibleRef.current) {
        return
      }
      void (async () => {
        try {
          await petApi.companionHeartbeat(60)
          // FE-02：心跳返回会话视图，亲密度面板另查 overview，禁止互相覆盖
          heartbeatFailuresRef.current = 0
          setCompanionWarn(false)
          await loadIntimacy()
        } catch {
          heartbeatFailuresRef.current += 1
          if (heartbeatFailuresRef.current >= 3) {
            setCompanionWarn(true)
          }
          // 下一轮心跳自动重试
        }
      })()
    }, 60_000)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    petCompanionApi
      .getOnboarding()
      .then(({ data: res }) => {
        setOnboarding(res.data)
      })
      .catch(() => undefined)
  }, [])

  // R23/T36：加载代际——迟到的旧响应不覆盖新状态
  const refreshGenerationRef = useRef(0)
  const refresh = useCallback(async () => {
    const generation = ++refreshGenerationRef.current
    // R18：bootstrap 与主宠并行加载；快照失败不阻断页面（门控按"未知不隐藏"降级，写操作仍由服务端裁决）
    void petApi.getBootstrap()
      .then(({ data: bootRes }) => {
        if (generation === refreshGenerationRef.current && bootRes.success && bootRes.data) {
          setBootstrap(bootRes.data)
        }
      })
      .catch(() => {
        if (generation === refreshGenerationRef.current) setBootstrap(null)
      })
    try {
      const { data: res } = await petApi.getMyPet()
      if (generation !== refreshGenerationRef.current) {
        return
      }
      setPet(res.data)
      setNoPet(false)
    } catch (error) {
      if (generation !== refreshGenerationRef.current) {
        return
      }
      // R23/T36：仅 PET_NOT_FOUND 才显示领养；其他错误保留旧数据（仅提示）
      if ((error as { code?: string }).code === 'PET_NOT_FOUND') {
        setNoPet(true)
      } else {
        toast(friendlyError(error))
      }
    } finally {
      if (generation === refreshGenerationRef.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    if (currentUser) {
      void refresh()
    }
  }, [currentUser, refresh])

  useEffect(() => {
    if (pet) {
      void loadIntimacy()
    }
  }, [pet?.petId, loadIntimacy])

  /** B06 动作可执行性：喂食/玩耍等当前能否执行与原因，随主宠刷新 */
  const loadActions = useCallback(async () => {
    if (!pet) return
    try {
      const { data: res } = await petCompanionApi.getActions(pet.petId)
      setActions(res.data)
    } catch {
      // 辅助信息：失败时清空（不显示 chips），由下一次 refresh 重试
      setActions([])
    }
  }, [pet?.petId])

  useEffect(() => {
    if (pet) {
      void loadActions()
    }
  }, [pet?.petId, loadActions])

  // 提醒未读角标（面板内已读会通过 onUnreadChanged 同步；面板列表数据由 RemindersPanel 自管）
  useEffect(() => {
    if (!pet) return
    petApi.getReminderUnreadCount()
      .then(({ data: res }) => { setReminderUnread(res.data ?? 0) })
      .catch(() => setReminderUnread(0))
  }, [pet])

  const activeRemaining = useCountdown(pet?.activityType ? pet.activityFinishedAt : null)

  // 任务到期后自动拉一次，让"可领取"自然出现（用户不必手动刷新）
  const activityKey = pet?.activityFinishedAt ?? null
  useEffect(() => {
    if (!activityKey || activeRemaining > 0) return
    const target = parseUtc(activityKey)
    if (Number.isNaN(target) || target > Date.now()) return
    const timer = setTimeout(() => {
      void refresh()
      setReloadSignal((n) => n + 1)
    }, 1500)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activityKey, activeRemaining])

  // F1：长按喂食打开背包食物选择（ActionSheet 展示 FOOD 数量，选中即消耗并恢复状态）
  const openFoodPicker = async () => {
    try {
      const { data: res } = await petApi.listInventory()
      const foods = (res.data || []).filter(
        (it) => it.itemType === 'FOOD' && (it.quantity ?? 0) > 0,
      )
      if (foods.length === 0) {
        toast('背包里没有食物，去商城买一点吧')
        return
      }
      const labels = foods.map((it) => `${it.icon} ${it.name} x${it.quantity}`)
      const picked = await Taro.showActionSheet({ itemList: labels.slice(0, 6) })
      const index = picked.tapIndex ?? -1
      if (index < 0 || acting) {
        return
      }
      setActing(true)
      try {
        const { data: feedRes } = await petApi.feedItem(foods[index].code)
        setPet(feedRes.data)
        toast('好嘞！')
      } catch (error) {
        toast(friendlyError(error))
      } finally {
        setTimeout(() => setActing(false), 300)
      }
    } catch {
      toast('背包暂时打不开，稍后再试')
    }
  }

  const runInteraction = async (action: 'feed' | 'play' | 'clean' | 'rest') => {
    if (acting) return
    // R18：照料动作受 bootstrap actionAvailability/quotas 门控（忙碌/额度用完前端即拦截，
    //  bootstrap 未加载时不拦截，最终仍由服务端权威裁决）
    const availability = bootstrap?.actionAvailability
    if (availability?.busy) {
      toast('它正忙着呢，等活动结束再照顾它吧')
      return
    }
    if (action === 'feed' && availability && !availability.canFeed) {
      toast('今日喂食奖励次数已用完')
      return
    }
    if (action === 'play' && availability && !availability.canPlay) {
      toast('今日玩耍奖励次数已用完')
      return
    }
    setActing(true)
    try {
      const { data: res } =
        action === 'feed' ? await petApi.feed()
          : action === 'play' ? await petApi.play()
            : action === 'clean' ? await petApi.clean()
              : await petApi.rest()
      setPet(res.data)
      toast('好嘞！')
    } catch (error) {
      toast(friendlyError(error))
    } finally {
      // P1-6：300ms 节流窗口，防手速极限连点（请求已返回后仍短暂锁定）
      setTimeout(() => setActing(false), 300)
    }
  }

  const openStage = () => {
    // H5 为 iframe 完整桥；微信小程序 web-view 只做观赏+状态展示（操作回本页）
    Taro.navigateTo({ url: '/pages/petStage/index' })
  }

  /** 领取漂流瓶结果：普通瓶跳信件页 / 稀有弹层 / 空手提示（主页领奖条与捞瓶面板共用） */
  const claimBottleResult = useCallback(async () => {
    try {
      const { data: res } = await petApi.claimBottle()
      const result = parseResult<PetBottleResult>(res.data.result)
      setReloadSignal((n) => n + 1)
      void refresh()
      if (!result) return
      if (result.outcome === 'CAUGHT') {
        if (result.rarity !== 'NORMAL') {
          setBottleResult(result)
          return
        }
        Taro.showToast({ title: '捞到漂流瓶啦！', icon: 'success' })
        Taro.navigateTo({ url: '/pages/encounterLetters/index' })
        return
      }
      if (result.outcome === 'EMPTY') {
        toast('空手而归…宠物获得了经验')
        return
      }
      toast('服务波动，稍后可重试领取')
    } catch (error) {
      toast(friendlyError(error))
    }
  }, [refresh])

  // 微信 web-view 舞台页的实时意图回落：navigateTo('/pages/pet/index?intent=feed&intentId=...')
  // PET-03：同一导航意图只消费一次——intentId 记入本地消费台账（实例 ref 只挡本挂载内的
  // pet 数据刷新重跑；storage 台账跨实例/跨返回导航生效），切宠后原意图不自动作用于新宠
  useEffect(() => {
    const intent = params.intent
    const intentId = params.intentId ?? params.intent
    if (!intent || !intentId || !pet) return
    if (consumedIntentRef.current.has(intentId)) return
    consumedIntentRef.current.add(intentId)
    try {
      const ledgerKey = 'pet_consumed_intent_ids'
      const consumed: string[] = Taro.getStorageSync(ledgerKey) || []
      if (consumed.includes(intentId)) return
      consumed.push(intentId)
      // 台账限长 20，防无限增长
      Taro.setStorageSync(ledgerKey, consumed.slice(-20))
    } catch {
      // storage 异常不阻断意图执行（仍有实例内 ref 保护）
    }
    setPanel('home')
    if (intent === 'feed' || intent === 'play' || intent === 'clean' || intent === 'rest') {
      void runInteraction(intent)
    } else if (intent === 'openProfile') {
      setProfileOpen(true)
    } else {
      const mapping: Record<string, PanelKey> = {
        openWork: 'work', openStudy: 'study', openBottle: 'bottle',
        openBattle: 'battle', openChat: 'chat', openAchievements: 'achievements',
        openRankings: 'rankings',
        // 二期：Cocos 养成按钮 → 养成面板（原文档 §89）
        openCare: 'care',
        // 三期：Cocos 场景按钮 → 家园/任务/社交/职业
        openRoom: 'home', openDaily: 'daily', openSocial: 'social', openCareer: 'care',
      }
      if (mapping[intent]) setPanel(mapping[intent])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.intent, pet])

  if (!currentUser && !loading) {
    return (
      <View className={`${styles.page} ${dataTheme}`} style={{ ...themeStyle, ...PET_CREAM_STYLE, ...PET_CREAM_EXT_STYLE }}>
        <CustomNavBar title="我的宠物" />
        <View className={styles.empty} style={{ paddingTop: statusBarHeight + navBarHeight }}>
          <Text className={styles.emptyEmoji}>🐾</Text>
          <Text className={styles.emptyText}>登录后领养你的宠物吧</Text>
          <Button className={styles.primaryBtn} onClick={() => Taro.navigateTo({ url: '/pages/login/index' })}>去登录</Button>
        </View>
      </View>
    )
  }

  if (loading) {
    return (
      <View className={`${styles.page} ${dataTheme}`} style={{ ...themeStyle, ...PET_CREAM_STYLE, ...PET_CREAM_EXT_STYLE }}>
        <CustomNavBar title="我的宠物" />
        <View className={styles.empty}><Text>加载中…</Text></View>
      </View>
    )
  }

  // 领养向导
  if (noPet || !pet) {
    return <AdoptView onAdopted={() => void refresh()} />
  }

  return (
    <View className={`${styles.page} ${dataTheme}`} style={{ ...themeStyle, ...PET_CREAM_STYLE, ...PET_CREAM_EXT_STYLE }}>
      <CustomNavBar title="我的宠物" />
      <ScrollView scrollY className={styles.body} style={{ paddingTop: statusBarHeight + navBarHeight }}>
        {/* 招牌页头 */}
        <CreamMasthead title="Le Petit Jardin" subtitle="宠物小花园 · 五果相伴" />

        {/* Ma Maison：身份卡 + 舞台入口（拱形主视觉） */}
        <CreamCard variant="arch" label="Ma Maison">
          <View className={styles.heroRow}>
            <View className={cream.avatar}>
              <Text className={styles.heroEmoji}>{SPECIES_EMOJI[pet.species] || '🐾'}</Text>
            </View>
            <View className={styles.heroInfo}>
              <Text className={styles.heroName}>
                {pet.name}
                <Text className={pet.gender === 'FEMALE' ? styles.genderFemale : styles.genderMale}>
                  {pet.gender === 'FEMALE' ? ' ♀' : ' ♂'}
                </Text>
                {' · Lv.'}{pet.level}
              </Text>
              <View className={cream.metaRow}>
                <CreamChip color="#C89B5A">{STATUS_LABEL[pet.status]}</CreamChip>
                <CreamChip color="#E0A45C">{GROWTH_STAGE_LABEL[pet.growthStage] ?? pet.growthStage}</CreamChip>
              </View>
              <Text className={styles.heroStatus}>
                💪{pet.strength} 🧠{pet.intelligence} 🌀{pet.agility} 💖{pet.charm}
                {pet.feedRemainingToday != null ? ` · 今日可喂食 ${pet.feedRemainingToday} 次` : ''}
              </Text>
            </View>
          </View>
          <View className={cream.stageEntry} onClick={openStage}>
            <Text>进入 3D 舞台（完整互动）</Text>
          </View>
          {process.env.TARO_ENV === 'h5' && (
            <View className={styles.stageEmbed}>
              <iframe src={PET_STAGE_URL} title="3D 舞台" className={styles.stageIframe} />
            </View>
          )}
        </CreamCard>

        {/* État：状态仪表 + 动作可用性 + 亲密度 */}
        <CreamCard variant="menu" label="État" title="状态">
          <View className={cream.statsGrid}>
            <CreamStatBar variant="cell" name="生命" value={pet.hp} max={pet.maxHp} color={PET_CREAM_STAT_TONE.hp} />
            <CreamStatBar variant="cell" name="饱食" value={pet.hunger} max={100} color={PET_CREAM_STAT_TONE.hunger} />
            <CreamStatBar variant="cell" name="心情" value={pet.happiness} max={100} color={PET_CREAM_STAT_TONE.happiness} />
            <CreamStatBar variant="cell" name="精力" value={pet.energy} max={100} color={PET_CREAM_STAT_TONE.energy} />
            <CreamStatBar variant="cell" name="清洁" value={pet.cleanliness} max={100} color={PET_CREAM_STAT_TONE.cleanliness} />
          </View>
          {actions.length > 0 && (
            <View className={styles.actionChips}>
              {actions.map((item) => (
                <Text key={item.action} className={item.allowed ? styles.actionChipOk : styles.actionChipNo}>
                  {ACTION_LABEL[item.action] ?? item.action}
                  {item.allowed
                    ? (item.rewardRemainingToday != null ? ` · 余${item.rewardRemainingToday}` : '')
                    : ` · ${item.reasonText ?? '暂不可'}`}
                </Text>
              ))}
            </View>
          )}
          <Text className={styles.jobMeta}>
            💞 亲密度 {intimacy ? `${intimacy.intimacy}（${intimacy.levelName}）` : `${pet.intimacy}（${pet.intimacyLevelName}）`}
            {intimacy && intimacy.nextLevelAt !== null ? ` · 还差 ${intimacy.toNext}` : ''}
            {` · 经验加成 +${intimacy?.expBonusPercent ?? pet.intimacyExpBonusPercent}%`}
            {` · 已陪伴 ${Math.floor((intimacy?.companionSeconds ?? pet.companionSeconds) / 3600)} 小时`}
            {intimacy ? `（今日 ${Math.round(intimacy.todayCompanionSeconds / 60)} 分钟，连续 ${intimacy.companionStreak} 天）` : ''}
          </Text>
        </CreamCard>

        {/* 养成动作（喂食长按可选背包食物） */}
        <View className={cream.actionsRow}>
          <CreamButton
            disabled={acting || (bootstrap?.actionAvailability.busy ?? false) || (bootstrap ? !bootstrap.actionAvailability.canFeed : false)}
            onClick={() => void runInteraction('feed')}
            onLongPress={() => void openFoodPicker()}
          >
            🍖 喂食{typeof bootstrap?.quotas?.feed === 'number' ? `(${bootstrap.quotas.feed})` : ''}
          </CreamButton>
          <CreamButton
            disabled={acting || (bootstrap?.actionAvailability.busy ?? false) || (bootstrap ? !bootstrap.actionAvailability.canPlay : false)}
            onClick={() => void runInteraction('play')}
          >
            🎾 玩耍{typeof bootstrap?.quotas?.playReward === 'number' ? `(${bootstrap.quotas.playReward})` : ''}
          </CreamButton>
          <CreamButton
            disabled={acting || (bootstrap?.actionAvailability.busy ?? false)}
            onClick={() => void runInteraction('clean')}
          >
            🫧 清洁
          </CreamButton>
          <CreamButton
            disabled={acting || (bootstrap?.actionAvailability.busy ?? false)}
            onClick={() => void runInteraction('rest')}
          >
            💤 休息
          </CreamButton>
        </View>
        <View className={cream.actionsRow}>
          <CreamButton variant="ghost" onClick={() => setProfileOpen(true)}>🎀 档案</CreamButton>
          <CreamButton variant="ghost" onClick={() => Taro.navigateTo({ url: '/pages/petWallet/index' })}>🪙 宠物币</CreamButton>
          <CreamButton variant="ghost" onClick={() => Taro.navigateTo({ url: '/pages/petPlay/index' })}>🎮 玩法</CreamButton>
        </View>

        {/* P2-4：陪伴心跳连续失败提示条（成功一次自动消失） */}
        {companionWarn && (
          <View className={styles.countdownCard}>
            <Text className={styles.countdownLabel}>⚠️ 陪伴计时可能不准确</Text>
            <Text className={styles.countdownValue}>网络波动，正在自动重试</Text>
          </View>
        )}

        {/* 进行中任务倒计时（严格按服务端 finishedAt） */}
        {pet.activityType && activeRemaining > 0 && (
          <View className={styles.countdownCard}>
            <Text className={styles.countdownLabel}>⏳ 正在{ACTIVITY_LABEL[pet.activityType] ?? '忙碌'}…</Text>
            <Text className={styles.countdownValue}>{formatClock(activeRemaining)}</Text>
          </View>
        )}
        {pet.claimableActivityType && (
          <View className={styles.claimRow}>
            <Button
              className={styles.primaryBtn}
              onClick={async () => {
                try {
                  if (pet.claimableActivityType === 'WORK') {
                    await petApi.claimWork()
                    toast('打工奖励已领取！')
                  } else if (pet.claimableActivityType === 'STUDY') {
                    await petApi.claimStudy()
                    toast('学习奖励已领取！')
                  } else {
                    await claimBottleResult()
                    return
                  }
                  setReloadSignal((n) => n + 1)
                  void refresh()
                } catch (error) {
                  toast(friendlyError(error))
                }
              }}
            >
              {pet.claimableActivityType === 'WORK'
                ? '领取打工奖励'
                : pet.claimableActivityType === 'STUDY' ? '领取学习奖励' : '查看捞瓶结果'}
            </Button>
          </View>
        )}

        {/* 新手引导（未完成时横幅） */}
        {onboarding && !onboarding.completed && (
          <View className={styles.onboardingBanner}>
            <Text className={styles.onboardingText}>
              🧭 新手引导 {onboarding.currentStep}/{onboarding.totalSteps} 步
            </Text>
            {onboarding.skippable && (
              <Button
                size='mini'
                className={styles.miniBtnGhost}
                onClick={() =>
                  petCompanionApi.skipOnboarding().then(() => {
                    setOnboarding({ ...onboarding, completed: true })
                    Taro.showToast({ title: '已跳过引导', icon: 'none' })
                  }).catch((err) => {
                    Taro.showToast({ title: friendlyError(err), icon: 'none' })
                  })
                }
              >
                跳过
              </Button>
            )}
          </View>
        )}

        {/* 功能宫格（分组，对齐 Web 端） */}
        {MENU_GROUPS.map((group) => (
          <View key={group.label} className={cream.menuGroup}>
            <View className={cream.menuGroupLabel}>
              <Text className={cream.menuGroupTitle}>{group.label}</Text>
              <Text className={cream.menuGroupHint}>{group.hint}</Text>
            </View>
            <View className={cream.menuGrid}>
              {group.keys.map((key) => {
                const meta = PANELS.find((item) => item.key === key)
                if (!meta) return null
                // R19：协作副本为独立整页（非弹层），且受 bootstrap 能力开关门控（未知不隐藏）
                if (key === 'pve' && bootstrap?.capabilities?.cooperativePve === false) return null
                return (
                  <View
                    key={key}
                    className={cream.menuItem}
                    onClick={() => {
                      if (key === 'pve') {
                        Taro.navigateTo({ url: '/pages/pve/index' })
                        return
                      }
                      setPanel(key)
                    }}
                  >
                    <Text className={cream.menuItemEmoji}>{meta.emoji}</Text>
                    <Text className={cream.menuItemLabel}>
                      {meta.label}
                      {key === 'reminders' && reminderUnread > 0 ? `(${reminderUnread})` : ''}
                      {key === 'center' && (bootstrap?.pendingOperations?.pendingActivityClaims ?? 0) > 0
                        ? `(${bootstrap?.pendingOperations?.pendingActivityClaims})`
                        : ''}
                    </Text>
                  </View>
                )
              })}
            </View>
          </View>
        ))}

        {/* 面板弹层：面板内容装进奶油视口层 */}
        {panel && (
        <CreamSheet title={PANEL_TITLE[panel]} onClose={() => setPanel(null)}>
        <View className={styles.panelBody}>
          {panel === 'daily' && <DailyQuestPanel onRefresh={() => void refresh()} />}
          {panel === 'center' && <ActivityCenterPanel onRefresh={() => void refresh()} />}
          {panel === 'social' && <SocialPanel pet={pet} onRefresh={() => void refresh()} />}
          {panel === 'memory' && (
            <MemoryPanel
              petId={pet?.petId ?? null}
              onRefresh={() => void refresh()}
              diaryEnabled={bootstrap?.capabilities?.diary ?? true}
              albumEnabled={bootstrap?.capabilities?.albumBinding ?? true}
            />
          )}
          {panel === 'home' && <HomeSheetPanel pet={pet} onRefresh={() => void refresh()} />}
          {panel === 'work' && (
            <WorkPanel pet={pet} onRefresh={() => void refresh()} reloadSignal={reloadSignal} />
          )}
          {panel === 'study' && (
            <StudyPanel pet={pet} onRefresh={() => void refresh()} reloadSignal={reloadSignal} />
          )}
          {panel === 'bottle' && (
            <BottlePanel
              onRefresh={() => void refresh()}
              onClaim={claimBottleResult}
              reloadSignal={reloadSignal}
            />
          )}
          {panel === 'battle' && <BattlePanel onRefresh={() => void refresh()} />}
          {panel === 'chat' && <ChatPanel />}
          {panel === 'rankings' && (
            <RankingsPanel seasonPassEnabled={bootstrap?.capabilities?.seasonPass !== false} />
          )}
          {panel === 'reminders' && (
            <RemindersPanel onUnreadChanged={setReminderUnread} />
          )}
          {panel === 'care' && (
            <CarePanel
              pet={pet}
              linkedProductEnabled={bootstrap?.capabilities?.linkedProduct !== false}
              onRefresh={() => void refresh()}
              onNavigate={setPanel}
            />
          )}
          {panel === 'achievements' && <AchievementsPanel />}
        </View>
        </CreamSheet>
        )}
      </ScrollView>

      {/* 档案弹窗：改名 30 天冷却 + 外观 + 主页公开 */}
      <ProfileModal
        pet={pet}
        open={profileOpen}
        onClose={() => setProfileOpen(false)}
        onChanged={() => void refresh()}
      />

      {/* 稀有/宠物/彩蛋瓶结果 */}
      {bottleResult && (
        <BottleResultModal result={bottleResult} onClose={() => setBottleResult(null)} />
      )}
    </View>
  )
}
