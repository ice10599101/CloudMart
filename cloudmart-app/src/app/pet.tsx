import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  AppState,
  ActivityIndicator,
  Platform,
  ScrollView,
  Text,
  View,
} from 'react-native'
import { WebView } from 'react-native-webview'
import { router, useLocalSearchParams, useFocusEffect } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme, PetCreamSemantic } from '@/constants/pet-cream'
import { CreamCard, CreamButton, CreamStatBar, CreamMasthead, CreamMenuGrid, CreamPanel } from '@/components/pet-cream'
import {
  petApi,
  type PetActionItem,
  type PetBattleItem,
  type PetBootstrap,
  type PetBottleResult,
  type PetInfo,
  type PetIntimacyInfo,
} from '@/api/pet'
import { useAuthStore } from '@/store/auth'
import {
  ACTION_LABEL,
  type BattleRound,
  ChipButton,
  GROWTH_STAGE_LABEL,
  type HostToGame,
  MENU_GROUPS,
  MENU_HIDDEN_BY_CAPABILITY,
  PANEL_ITEM,
  PANEL_TITLE,
  type PanelKey,
  PERSONALITY_LABEL,
  SPECIES_EMOJI,
  STATUS_LABEL,
  formatClock,
  parseResult,
  toDisplayState,
  useCountdown,
} from '@/components/pet/shared'
import { AdoptView } from '@/components/pet/AdoptView'
import {
  AchievementsPanel,
  ActivityCenterPanel,
  BattlePanel,
  BottlePanel,
  CarePanel,
  ChatPanel,
  CompanionPanel,
  DailyQuestPanel,
  HomeSheetPanel,
  LinkedProductPanel,
  PvePanel,
  RankingsPanel,
  RemindersPanel,
  SeasonPassPanel,
  SocialPanel,
  StudyPanel,
  WorkPanel,
} from '@/components/pet/panels'
import { BattleOverlayModal } from '@/components/pet/modals/BattleOverlayModal'
import { BottleResultModal } from '@/components/pet/modals/BottleResultModal'
import { FoodPickerModal } from '@/components/pet/modals/FoodPickerModal'
import { ProfileModal } from '@/components/pet/modals/ProfileModal'

/**
 * 社区宠物主页（App 端，实施文档 §4/§34）。
 *
 * Cocos 宠物舞台通过 react-native-webview 嵌入（产物 URL 取 EXPO_PUBLIC_PET_GAME_URL，
 * 未配置/加载超时自动 Fail-Open 原生降级舞台）；宿主负责页面、导航与 mall-pet API，
 * 数值全部服务端结算，游戏只发意图、不做任何业务计算。
 *
 * R30：各业务面板/弹层/领养向导已按域拆到 @/components/pet（行为不变），
 * 本文件只保留舞台桥接、bootstrap/refresh、心跳、宫格与面板路由。
 */
export default function PetScreen() {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const params = useLocalSearchParams<{ intent?: string }>()
  const gameUrl = process.env.EXPO_PUBLIC_PET_GAME_URL
  const webRef = useRef<WebView>(null)
  const isLoggedIn = useAuthStore((state) => state.isLoggedIn)
  // R19：协作副本取消按钮需判定发起人身份
  const currentUserId = useAuthStore((state) => state.user?.id ?? null)

  const [loading, setLoading] = useState(true)
  const [pet, setPet] = useState<PetInfo | null>(null)
  const [noPet, setNoPet] = useState(false)
  // R18：bootstrap 启动快照——serverNow/businessDate/capabilities/quotas/
  // actionAvailability/pendingOperations 以后端为权威，失败置 null（不阻断主流程）
  const [bootstrap, setBootstrap] = useState<PetBootstrap | null>(null)
  const [gameReady, setGameReady] = useState(false)
  const [gameFailed, setGameFailed] = useState(!gameUrl)
  const stageFrameRef = useRef<any>(null)
  const [panel, setPanel] = useState<PanelKey | null>(null)
  // B06 动作可执行性（辅助展示：失败清空，不打断主流程）
  const [actions, setActions] = useState<PetActionItem[]>([])
  const [intimacy, setIntimacy] = useState<PetIntimacyInfo | null>(null)
  // 提醒未读角标（列表在 RemindersPanel 内自管，已读变化经回调更新角标）
  const [reminderUnread, setReminderUnread] = useState(0)

  // 档案弹窗 / 喂食选择器开关（草稿与数据在各自弹层组件内）
  const [profileOpen, setProfileOpen] = useState(false)
  const [foodPickerOpen, setFoodPickerOpen] = useState(false)

  // 稀有/宠物/彩蛋瓶结果（领取逻辑在宿主：普通瓶跳转邂逅瓶列表，稀有瓶弹层）
  const [bottleResult, setBottleResult] = useState<PetBottleResult | null>(null)

  // 对战回合兜底：未部署 Cocos 时宿主自行逐回合播放（服务端回合流水）
  const [battleOverlay, setBattleOverlay] = useState<{ rounds: BattleRound[]; won: boolean } | null>(null)
  const [roundIndex, setRoundIndex] = useState(0)

  // 主页领奖/活动到期后递增，驱动打工/读书/捞瓶面板重新拉取（面板打开期间挂载，打开即加载）
  const [reloadSignal, setReloadSignal] = useState(0)
  const [stageBubble, setStageBubble] = useState<string | null>(null)

  const stageOrigin = gameUrl ? gameUrl.split('/').slice(0, 3).join('/') : ''
  /** 气泡 3.2s 自动消失（互动反馈可见化；Expo Web 上 Alert 是空操作，必须页面内反馈） */
  const showBubbleTimed = useCallback((content: string) => {
    setStageBubble(content)
    setTimeout(() => setStageBubble((current) => (current === content ? null : current)), 3200)
  }, [])

  const postToGame = useCallback((message: HostToGame) => {
    if (Platform.OS === 'web') {
      // Web：向舞台 iframe 的 contentWindow 发消息（targetOrigin 收紧为游戏源）
      const frame = stageFrameRef.current?.contentWindow
      if (frame && stageOrigin) {
        frame.postMessage(JSON.stringify(message), stageOrigin)
      }
      return
    }
    webRef.current?.injectJavaScript(
      `window.__petHostMessage && window.__petHostMessage(${JSON.stringify(JSON.stringify(message))}); true;`,
    )
  }, [stageOrigin])

  // Web：init/petState 重试器——游戏加载后 __petHostMessage 才挂载，且旧产物 ready 回不来，
  // 按次数重试确保游戏拿到当前宠物数据（收到游戏消息即停）
  const webSyncStage = useCallback((next: PetInfo) => {
    let tries = 0
    const timer = setInterval(() => {
      tries += 1
      if (tries > 8) { clearInterval(timer); return }
      postToGame({ source: 'pet-host', type: 'init', pet: toDisplayState(next) })
    }, 1200)
  }, [postToGame])

  const syncStage = useCallback((next: PetInfo) => {
    if (Platform.OS === 'web') {
      webSyncStage(next)
      return
    }
    postToGame({ source: 'pet-host', type: 'petState', pet: toDisplayState(next) })
  }, [postToGame, webSyncStage])

  const showBubble = useCallback((content: string) => {
    showBubbleTimed(content)
    postToGame({ source: 'pet-host', type: 'chatBubble', content })
  }, [postToGame, showBubbleTimed])

  // R23/T36：加载代际——迟到的旧响应不覆盖新状态
  const refreshGenerationRef = useRef(0)
  // PET-03/T38：陪伴会话镜像——AppState 与页面聚焦状态放组件顶层（原实现在 useEffect 内
  // 调 useRef 违反 Hook 规则，登录后触发 invalid hook call），值变更不触发渲染，仅心跳与清理回调读取
  const appStateRef = useRef(AppState.currentState)
  const petScreenFocusedRef = useRef(false)
  const companionActiveRef = useRef(false)
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
      if (gameReady) syncStage(res.data)
    } catch (error) {
      if (generation !== refreshGenerationRef.current) {
        return
      }
      // R23/T36：仅 PET_NOT_FOUND（确实无宠物）才显示领养；500/网络错误保留旧数据，
      // 不再伪装成"未领养"（拦截器已对非 404 错误给出提示）
      const code = (error as { code?: string })?.code
      if (code === 'PET_NOT_FOUND') {
        setNoPet(true)
      }
    } finally {
      if (generation === refreshGenerationRef.current) {
        setLoading(false)
      }
    }
  }, [gameReady, syncStage])

  // 登录守卫：未登录直接跳登录页（与 Web/小程序端一致，避免 401 后页面空白）
  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
      return
    }
    void refresh()
  }, [isLoggedIn, refresh])

  // B06 动作可执行性：随主宠刷新（喂食/玩耍等当前能否执行与原因）
  useEffect(() => {
    if (!pet) return
    void (async () => {
      try {
        const { data: res } = await petApi.getActions(pet.petId)
        setActions(res.data)
      } catch {
        setActions([])
      }
    })()
  }, [pet?.petId])

  /** PET-03：尽力上报陪伴停止——失败静默（服务端会话超时兜底），不阻塞清理流程 */
  const stopCompanionSession = useCallback(() => {
    if (!companionActiveRef.current) return
    companionActiveRef.current = false
    if (!useAuthStore.getState().isLoggedIn) return
    petApi.stopCompanion().catch(() => undefined)
  }, [])

  // PET-03/T38：切后台/锁屏立即停本地计时并尽力停止服务端会话，返回前台由定时心跳恢复新会话
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      appStateRef.current = state
      if (state !== 'active') {
        stopCompanionSession()
      }
    })
    return () => subscription.remove()
  }, [stopCompanionSession])

  // PET-03/T38：失焦（切页签/离开宠物页）停止有效计时；重新聚焦后开启新会话
  useFocusEffect(
    useCallback(() => {
      petScreenFocusedRef.current = true
      return () => {
        petScreenFocusedRef.current = false
        stopCompanionSession()
      }
    }, [stopCompanionSession]),
  )

  // 三期：亲密度概览（展示型数据，失败保持原值）+ 陪伴心跳（页面聚焦且 App 在前台时每 60 秒上报一次）
  useEffect(() => {
    if (!isLoggedIn) {
      return
    }
    void (async () => {
      try {
        const { data: res } = await petApi.getIntimacy()
        setIntimacy(res.data)
      } catch {
        // 展示型数据：忽略
      }
    })()
    // R23/T37：仅页面聚焦且前台（AppState=active）发心跳——失焦/后台暂停，恢复后为新会话；
    // 服务端以会话超时兜底，客户端报时不作为有效时长依据
    const timer = setInterval(() => {
      if (appStateRef.current !== 'active' || !petScreenFocusedRef.current) {
        return
      }
      void (async () => {
        try {
          await petApi.companionHeartbeat(60)
          companionActiveRef.current = true
          // FE-02：心跳返回会话视图，亲密度面板另查 overview，禁止互相覆盖
          const { data: overview } = await petApi.getIntimacy()
          setIntimacy(overview.data)
        } catch {
          // 心跳失败静默（下一轮重试）
        }
      })()
    }, 60_000)
    return () => {
      clearInterval(timer)
      // 切宠/退出登录/离开页面挂载：结束当前会话本地计时（尽力通知服务端）
      stopCompanionSession()
    }
  }, [isLoggedIn, pet?.petId, stopCompanionSession])

  useEffect(() => {
    if (!gameUrl) return
    // Web：iframe 是否可见由自身决定，不自动降级（桥接失败只影响数据同步，不影响画面）
    if (Platform.OS === 'web') return
    const timer = setTimeout(() => setGameFailed((f) => !gameReady), 12000)
    return () => clearTimeout(timer)
  }, [gameUrl, gameReady])

  // 提醒未读角标：随主宠刷新；面板内已读操作经 onUnreadChange 即时更新
  useEffect(() => {
    if (!pet) return
    petApi.getReminderUnreadCount()
      .then(({ data }) => setReminderUnread(data.data ?? 0))
      .catch(() => setReminderUnread(0))
  }, [pet])

  const runInteraction = async (action: 'feed' | 'play' | 'clean' | 'rest') => {
    if (!pet) return
    // R18：照料动作受 bootstrap actionAvailability/quotas 门控（忙碌/额度用完前端即拦截，
    //  bootstrap 未加载时不拦截，最终仍由服务端权威裁决）
    const availability = bootstrap?.actionAvailability
    if (availability?.busy) {
      showBubbleTimed('它正忙着呢，等活动结束再照顾它吧')
      if (gameReady) postToGame({ source: 'pet-host', type: 'actionResult', action, ok: false, message: '忙碌中' })
      return
    }
    if (action === 'feed' && availability && !availability.canFeed) {
      showBubbleTimed('今日喂食奖励次数已用完')
      return
    }
    if (action === 'play' && availability && !availability.canPlay) {
      showBubbleTimed('今日玩耍奖励次数已用完')
      return
    }
    try {
      const { data: res } =
        action === 'feed' ? await petApi.feed()
          : action === 'play' ? await petApi.play()
            : action === 'clean' ? await petApi.clean()
              : await petApi.rest()
      setPet(res.data)
      if (gameReady) {
        syncStage(res.data)
        postToGame({ source: 'pet-host', type: 'actionResult', action, ok: true })
      }
    } catch (error) {
      const err = error as { code?: string; message?: string }
      if (err.code === 'UNAUTHORIZED') {
        showBubbleTimed('请先登录再和它互动哦')
        router.push('/login')
        return
      }
      showBubbleTimed(err.message ?? '现在不行哦，稍后再试')
      if (gameReady) {
        postToGame({ source: 'pet-host', type: 'actionResult', action, ok: false, message: '现在不行哦' })
      }
    }
  }

  /** 长按喂食：打开食物选择器（列表与喂食逻辑在 FoodPickerModal 内） */
  const openFoodPicker = useCallback(() => {
    if (!pet) return
    setFoodPickerOpen(true)
  }, [pet])

  const closeFoodPicker = useCallback(() => setFoodPickerOpen(false), [])

  /** 食物喂食成功：更新主宠并同步 Cocos 舞台（对齐原 feedFood 的桥接顺序） */
  const handleFoodFed = useCallback((next: PetInfo) => {
    setPet(next)
    if (gameReady) {
      syncStage(next)
      postToGame({ source: 'pet-host', type: 'actionResult', action: 'feed', ok: true })
    }
  }, [gameReady, postToGame, syncStage])

  const notifyFoodEmpty = useCallback(() => {
    setStageBubble('背包里没有食物，去商城买一点吧')
  }, [])

  const openProfile = useCallback(() => setProfileOpen(true), [])

  // 微信 web-view 宿主页 navigateTo 落地：intent 参数触发对应动作
  useEffect(() => {
    const intent = params.intent
    if (!intent) return
    setPanel('home')
    if (['feed', 'play', 'clean', 'rest'].includes(intent)) {
      void runInteraction(intent as 'feed' | 'play' | 'clean' | 'rest')
    } else if (intent === 'openProfile') {
      openProfile()
    } else {
      const mapping: Record<string, PanelKey> = {
        openWork: 'work', openStudy: 'study', openBottle: 'bottle',
        openBattle: 'battle', openChat: 'chat', openAchievements: 'achievements',
        openRankings: 'rankings',
        // 二期：Cocos 养成按钮 → 养成面板（原文档 §89）
        openCare: 'care',
      }
      if (mapping[intent]) setPanel(mapping[intent])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.intent])

  const activeRemaining = useCountdown(pet?.activityType ? pet.activityFinishedAt : null)

  // R19：宫格入口按 capability 显式 false 过滤；bootstrap 未加载/加载失败时全量展示
  const visibleMenuGroups = useMemo(() => {
    const caps = bootstrap?.capabilities
    if (!caps) return MENU_GROUPS
    return MENU_GROUPS
      .map((group) => ({
        ...group,
        keys: group.keys.filter((key) => {
          const capKey = MENU_HIDDEN_BY_CAPABILITY[key]
          return !capKey || caps[capKey] !== false
        }),
      }))
      .filter((group) => group.keys.length > 0)
  }, [bootstrap])

  // 任务到期后自动刷新一次，让"可领取"自然出现；同时驱动已打开的打工/读书/捞瓶面板重载
  const activityKey = pet?.activityFinishedAt ?? null
  useEffect(() => {
    if (!activityKey || activeRemaining > 0) return
    const target = Date.parse(activityKey.length === 19 ? `${activityKey}Z` : activityKey)
    if (Number.isNaN(target) || target > Date.now()) return
    const timer = setTimeout(() => {
      void refresh()
      setReloadSignal((signal) => signal + 1)
    }, 1500)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activityKey, activeRemaining])

  // 对战回合逐条推进（0.9s/回合，点击浮层跳过）
  useEffect(() => {
    if (!battleOverlay) return
    if (roundIndex >= battleOverlay.rounds.length) {
      const timer = setTimeout(() => setBattleOverlay(null), 1600)
      return () => clearTimeout(timer)
    }
    const timer = setTimeout(() => setRoundIndex((i) => i + 1), 900)
    return () => clearTimeout(timer)
  }, [battleOverlay, roundIndex])

  const onWebViewMessage = useCallback((event: { nativeEvent: { data: string } }) => {
    try {
      const message = JSON.parse(event.nativeEvent.data)
      if (message?.source !== 'pet-game') return
      setGameFailed(false)
      if (message.type === 'ready') {
        setGameReady(true)
        if (pet) postToGame({ source: 'pet-host', type: 'init', pet: toDisplayState(pet) })
      } else if (message.type === 'intent') {
        const action = message.action as string
        if (['feed', 'play', 'clean', 'rest'].includes(action)) {
          void runInteraction(action as 'feed' | 'play' | 'clean' | 'rest')
        } else if (action === 'openProfile') {
          openProfile()
        } else {
          const mapping: Record<string, PanelKey> = {
            openWork: 'work', openStudy: 'study', openBottle: 'bottle',
            openBattle: 'battle', openChat: 'chat', openAchievements: 'achievements',
            openRankings: 'rankings',
          }
          if (mapping[action]) setPanel(mapping[action])
        }
      } else if (message.type === 'petTapped' && pet) {
        showBubble(`${pet.name}：主人，点点我干嘛呀～`)
      }
    } catch {
      // 非 JSON 消息忽略
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pet, gameReady, openProfile, showBubble])

  /** 对战结果展示：优先 Cocos 演出，未就绪则宿主兜底逐回合播放 */
  const presentBattle = useCallback((battle: PetBattleItem) => {
    if (battle.status !== 'FINISHED' || !battle.rounds) return
    const rounds = parseResult<BattleRound[]>(battle.rounds)
    if (!rounds || rounds.length === 0) return
    const myPetId = battle.role === 'ATTACKER' ? battle.attackerPetId : battle.defenderPetId
    const won = String(battle.winnerPetId) === String(myPetId)
    if (gameReady) {
      postToGame({ source: 'pet-host', type: 'battleRounds', rounds: rounds as unknown as Record<string, unknown>[], won })
      return
    }
    setRoundIndex(0)
    setBattleOverlay({ rounds, won })
  }, [gameReady, postToGame])

  // Web：舞台 iframe → 宿主消息桥（游戏 postMessage 到 parent，这里统一转发给解析器）
  useEffect(() => {
    if (Platform.OS !== 'web' || !gameUrl) return
    const handler = (event: MessageEvent) => {
      if (event.origin !== stageOrigin) return
      try {
        const raw = typeof event.data === 'string' ? event.data : JSON.stringify(event.data)
        setGameFailed(false)
        onWebViewMessage({ nativeEvent: { data: raw } } as any)
      } catch { /* 非本游戏消息 */ }
    }
    window.addEventListener('message', handler)
    return () => window.removeEventListener('message', handler)
  }, [gameUrl, stageOrigin, onWebViewMessage])

  /** 捞瓶领取：稀有结果弹层、普通瓶跳邂逅瓶列表；刷新主宠并驱动捞瓶面板重载 */
  const openBottleResult = async () => {
    try {
      const { data: res } = await petApi.claimBottle()
      const result = parseResult<PetBottleResult>(res.data.result)
      void refresh()
      setReloadSignal((signal) => signal + 1)
      if (result?.outcome === 'CAUGHT') {
        if (result.rarity !== 'NORMAL') {
          setBottleResult(result)
          return
        }
        router.push('/encounter-letters')
      }
    } catch {
      // 拦截器已提示
    }
  }

  /** 主页倒计时条领奖：打工/学习直接领，捞瓶走领取结果逻辑 */
  const claimCurrentActivity = async () => {
    if (!pet?.claimableActivityType) return
    try {
      if (pet.claimableActivityType === 'WORK') {
        await petApi.claimWork()
        void refresh()
        setReloadSignal((signal) => signal + 1)
      } else if (pet.claimableActivityType === 'STUDY') {
        await petApi.claimStudy()
        void refresh()
        setReloadSignal((signal) => signal + 1)
      } else {
        await openBottleResult()
      }
    } catch { /* 已领取 409 */ }
  }

  const barRows = useMemo(() => {
    if (!pet) return []
    return [
      { label: '❤️ 生命', value: pet.hp, max: pet.maxHp, color: PetCreamSemantic.statHp },
      { label: '🍖 饱食', value: pet.hunger, max: 100, color: PetCreamSemantic.statHunger },
      { label: '💗 心情', value: pet.happiness, max: 100, color: PetCreamSemantic.statHappiness },
      { label: '⚡ 精力', value: pet.energy, max: 100, color: PetCreamSemantic.statEnergy },
      { label: '🧼 清洁', value: pet.cleanliness, max: 100, color: PetCreamSemantic.statClean },
    ]
  }, [pet])

  if (loading) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bgBase }}>
        <ActivityIndicator color={colors.primary} />
      </View>
    )
  }

  if (noPet || !pet) {
    return <AdoptView onAdopted={refresh} />
  }

  const activeRound = battleOverlay && roundIndex < battleOverlay.rounds.length
    ? battleOverlay.rounds[roundIndex]
    : null

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bgBase }} contentContainerStyle={{ padding: Spacing.md, paddingBottom: 48 }}>
      {/* 招牌页头 */}
      <CreamMasthead title="Le Petit Jardin" subtitle="宠物小花园 · 五果相伴" />
      {/* Cocos 舞台（未部署 Fail-Open 原生降级） */}
      <View style={{ height: 320, borderRadius: BorderRadius.lg, overflow: 'hidden', marginBottom: Spacing.md, backgroundColor: PetCreamTheme.bgPage }}>
        {gameUrl && !gameFailed
          ? (
            Platform.OS === 'web'
              ? (
                // @ts-ignore —— react-native 的 JSX 命名空间不含 DOM 元素
                <iframe
                  ref={stageFrameRef}
                  name="pet-stage-iframe"
                  src={gameUrl}
                  title="3D 舞台"
                  style={{ flex: 1, width: '100%', border: 'none', backgroundColor: 'transparent' }}
                />
              )
              : (
                <WebView
                  ref={webRef}
                  source={{ uri: gameUrl }}
                  onMessage={onWebViewMessage}
                  scrollEnabled={false}
                  style={{ flex: 1, backgroundColor: 'transparent' }}
                />
              )
          )
          : (
            <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
              <Text style={{ fontSize: 96 }}>{SPECIES_EMOJI[pet.species] || '🐾'}</Text>
              <Text style={{ color: 'rgba(255,255,255,0.6)', fontSize: FontSize.xs, marginTop: Spacing.xs }}>
                原生模式 · 配置 EXPO_PUBLIC_PET_GAME_URL 启用 Cocos 舞台
              </Text>
            </View>
          )}
        {stageBubble && (
          <View style={{
            position: 'absolute', left: 16, right: 16, bottom: 16,
            padding: Spacing.sm, borderRadius: BorderRadius.md, backgroundColor: 'rgba(0,0,0,0.55)',
          }}>
            <Text style={{ color: '#fff', fontSize: FontSize.sm }}>{stageBubble}</Text>
          </View>
        )}
      </View>

      {/* Ma Maison：身份卡（拱形主视觉） */}
      <CreamCard variant="arch" label="Ma Maison" style={{ marginBottom: Spacing.sm }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: colors.text }}>
          {SPECIES_EMOJI[pet.species]} {pet.name}
          <Text style={{ color: pet.gender === 'FEMALE' ? PetCreamSemantic.genderFemale : PetCreamSemantic.genderMale, fontSize: FontSize.sm }}>
            {pet.gender === 'FEMALE' ? ' ♀' : ' ♂'}
          </Text>
          {' '}· Lv.{pet.level}
        </Text>
        <Text style={{ fontSize: FontSize.xs, color: colors.textSecondary }}>{STATUS_LABEL[pet.status]}</Text>
      </View>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, marginTop: 4 }}>
        <Text style={{ fontSize: FontSize.xs, color: colors.textTertiary }}>
          {GROWTH_STAGE_LABEL[pet.growthStage] ?? pet.growthStage} · {PERSONALITY_LABEL[pet.personality] ?? pet.personality}
        </Text>
        <Text style={{ fontSize: FontSize.xs, color: colors.textTertiary }}>
          ✨ {pet.exp}/{pet.expToNext} · 💪{pet.strength} 🧠{pet.intelligence} 🌀{pet.agility} 💖{pet.charm}
        </Text>
        {pet.feedRemainingToday != null && (
          <Text style={{ fontSize: FontSize.xs, color: colors.textTertiary }}>今日可喂食 {pet.feedRemainingToday} 次</Text>
        )}
      </View>
      </CreamCard>

      {/* État：状态仪表 */}
      <CreamCard label="État" title="状态">
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {barRows.map((row) => <CreamStatBar key={row.label} name={row.label} value={row.value} max={row.max} color={row.color} />)}
        {actions.length > 0 && (
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.xs }}>
            {actions.map((item) => (
              <View
                key={item.action}
                style={{
                  paddingVertical: 3, paddingHorizontal: 10, borderRadius: 999, borderWidth: 1,
                  borderColor: item.allowed ? colors.border : colors.border,
                  backgroundColor: item.allowed ? 'rgba(98, 216, 138, 0.12)' : colors.bgBase,
                }}
              >
                <Text style={{ fontSize: 10, color: item.allowed ? PetCreamSemantic.success : colors.textTertiary }}>
                  {ACTION_LABEL[item.action] ?? item.action}
                  {item.allowed
                    ? (item.rewardRemainingToday != null ? ` · 余${item.rewardRemainingToday}` : '')
                    : ` · ${item.reasonText ?? '暂不可'}`}
                </Text>
              </View>
            ))}
          </View>
        )}
        {/* 三期：亲密度与陪伴（服务端权威，前端只展示） */}
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, marginTop: 4 }}>
          💞 亲密度 {intimacy ? `${intimacy.intimacy}（${intimacy.levelName}）` : `${pet.intimacy}（${pet.intimacyLevelName}）`}
          {intimacy && intimacy.nextLevelAt !== null ? ` · 还差 ${intimacy.toNext}` : ''}
          {` · 经验加成 +${intimacy?.expBonusPercent ?? pet.intimacyExpBonusPercent}%`}
          {` · 已陪伴 ${Math.floor((intimacy?.companionSeconds ?? pet.companionSeconds) / 3600)} 小时`}
          {intimacy ? `（今日 ${Math.round(intimacy.todayCompanionSeconds / 60)} 分钟，连续 ${intimacy.companionStreak} 天）` : ''}
        </Text>
      </View>
      </CreamCard>

      {/* 进行中任务倒计时（按服务端 finishedAt） */}
      {pet.activityType && activeRemaining > 0 && (
        <View style={{
          marginTop: Spacing.md, padding: Spacing.sm, borderRadius: BorderRadius.md,
          backgroundColor: colors.bgContainer, borderWidth: 1, borderColor: colors.border,
        }}>
          <Text style={{ color: colors.text, fontSize: FontSize.sm }}>
            ⏳ 正在进行：{pet.activityType === 'WORK' ? '打工' : pet.activityType === 'STUDY' ? '读书' : '捞漂流瓶'}
          </Text>
          <Text style={{ color: colors.primary, fontSize: FontSize.md, fontWeight: '600' }}>
            剩余 {formatClock(activeRemaining)}
          </Text>
        </View>
      )}
      {pet.claimableActivityType && (
        <View style={{ marginTop: Spacing.sm }}>
          <ChipButton
            label={pet.claimableActivityType === 'WORK' ? '领取打工奖励' : pet.claimableActivityType === 'STUDY' ? '领取学习奖励' : '查看捞瓶结果'}
            primary
            onPress={() => void claimCurrentActivity()}
          />
        </View>
      )}

      {/* 互动按钮（Cocos 意图与原生按钮共用同一 API） */}
      <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.md, flexWrap: 'wrap' }}>
        <CreamButton
          disabled={(bootstrap?.actionAvailability.busy ?? false) || (bootstrap ? !bootstrap.actionAvailability.canFeed : false)}
          onPress={() => void runInteraction('feed')}
          onLongPress={openFoodPicker}
        >
          {`🍖 喂食${typeof bootstrap?.quotas.feed === 'number' ? `(${bootstrap.quotas.feed})` : ''}`}
        </CreamButton>
        <ChipButton
          label={`🎾 玩耍${typeof bootstrap?.quotas.playReward === 'number' ? `(${bootstrap.quotas.playReward})` : ''}`}
          primary
          disabled={(bootstrap?.actionAvailability.busy ?? false) || (bootstrap ? !bootstrap.actionAvailability.canPlay : false)}
          onPress={() => void runInteraction('play')}
        />
        <ChipButton label="🫧 清洁" primary onPress={() => void runInteraction('clean')} />
        <ChipButton label="💤 休息" primary onPress={() => void runInteraction('rest')} />
        <ChipButton label="🎀 档案" onPress={openProfile} />
      </View>

      {/* 面板切换 */}
      {/* 分组功能宫格 */}
      <CreamMenuGrid
        groups={visibleMenuGroups}
        items={PANEL_ITEM}
        active={panel}
        badgeOf={(key) => {
          // R18：活动中心角标取 bootstrap 待领数（权威快照），提醒角标保持原口径
          if (key === 'reminders') return reminderUnread
          if (key === 'center') return bootstrap?.pendingOperations?.pendingActivityClaims ?? 0
          return 0
        }}
        onSelect={(key) => setPanel(key as PanelKey)}
      />

      {/* 面板层（全屏奶油） */}
      <CreamPanel title={panel ? PANEL_TITLE[panel] : ''} visible={panel != null} onClose={() => setPanel(null)}>
      <View style={{ gap: Spacing.sm }}>
        {panel === 'daily' && <DailyQuestPanel onRefresh={refresh} />}
        {panel === 'social' && <SocialPanel pet={pet} onRefresh={refresh} />}
        {panel === 'home' && <HomeSheetPanel pet={pet} onRefresh={refresh} />}

        {panel === 'work' && (
          <WorkPanel
            claimable={pet.claimableActivityType === 'WORK'}
            reloadSignal={reloadSignal}
            onRefresh={refresh}
          />
        )}
        {panel === 'study' && (
          <StudyPanel
            claimable={pet.claimableActivityType === 'STUDY'}
            reloadSignal={reloadSignal}
            onRefresh={refresh}
          />
        )}
        {panel === 'bottle' && (
          <BottlePanel reloadSignal={reloadSignal} onRefresh={refresh} onClaim={openBottleResult} />
        )}

        {panel === 'battle' && <BattlePanel onRefresh={refresh} onPresent={presentBattle} />}

        {panel === 'chat' && <ChatPanel onBubble={showBubble} />}

        {panel === 'care' && (
          <CarePanel pet={pet} onRefresh={refresh} onAdoptMore={() => setNoPet(true)} />
        )}

        {panel === 'rankings' && <RankingsPanel />}
        {panel === 'reminders' && <RemindersPanel onUnreadChange={setReminderUnread} />}

        {panel === 'center' && <ActivityCenterPanel onRefresh={refresh} />}

        {/* R19：双人协作 PVE（capability 显式关闭时面板内兜底"暂未开放"） */}
        {panel === 'pve' && (
          <PvePanel enabled={bootstrap?.capabilities?.cooperativePve !== false} currentUserId={currentUserId} />
        )}
        {/* R19：赛季通行证（capability 关闭时仅展示档位，不允许领取） */}
        {panel === 'seasonpass' && (
          <SeasonPassPanel claimEnabled={bootstrap?.capabilities?.seasonPass !== false} />
        )}
        {/* R19：实物商品双倍喂食（capability 关闭时权益照常展示，仅隐藏兑换码核销） */}
        {panel === 'linkedproduct' && (
          <LinkedProductPanel redeemEnabled={bootstrap?.capabilities?.linkedProduct !== false} />
        )}

        {panel === 'companion' && (
          <CompanionPanel
            petId={pet.petId}
            onRefresh={refresh}
            diaryEnabled={bootstrap?.capabilities?.diary ?? true}
            albumEnabled={bootstrap?.capabilities?.albumBinding ?? true}
          />
        )}

        {panel === 'achievements' && <AchievementsPanel />}
      </View>
      </CreamPanel>

      {/* 喂食食物选择器（长按喂食弹出；背包 FOOD 项） */}
      <FoodPickerModal
        visible={foodPickerOpen}
        onClose={closeFoodPicker}
        onFed={handleFoodFed}
        onEmpty={notifyFoodEmpty}
      />

      {/* 档案弹窗：改名 30 天冷却 + 外观 + 主页公开 */}
      <ProfileModal
        pet={pet}
        open={profileOpen}
        onClose={() => setProfileOpen(false)}
        onChanged={refresh}
        onPrivacyChanged={(next) => setPet((prev) => (prev ? { ...prev, isPublic: next } : prev))}
      />

      {/* 稀有/宠物/彩蛋瓶结果 */}
      <BottleResultModal result={bottleResult} onClose={() => setBottleResult(null)} />

      {/* 对战回合兜底浮层（未部署 Cocos 时也可完整看战斗过程） */}
      <BattleOverlayModal
        overlay={battleOverlay}
        activeRound={activeRound}
        onDismiss={() => setBattleOverlay(null)}
      />
    </ScrollView>
  )
}
