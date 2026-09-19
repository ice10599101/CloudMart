import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ActivityIndicator,
  Alert,
  Modal,
  ScrollView,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native'
import { WebView } from 'react-native-webview'
import { router, useLocalSearchParams } from 'expo-router'
import * as Clipboard from 'expo-clipboard'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { notificationApi } from '@/api/notification'
import {
  petApi,
  type PetAchievement,
  type PetBattleItem,
  type PetBottleResult,
  type PetBottleStatus,
  type PetChatMessage,
  type PetEventItem,
  type PetEvolutionStatus,
  type PetInfo,
  type PetInventoryItem,
  type PetJobItem,
  type PetOpponent,
  type PetRankingResult,
  type PetRankingType,
  type PetReminder,
  type PetShareCard,
  type PetShopItem,
  type PetSkillItem,
  type PetStudyItem,
  type PetSummary,
  type PetVisitNeighbor,
} from '@/api/pet'
import { useAuthStore } from '@/store/auth'

/**
 * 社区宠物主页（App 端，实施文档 §4/§34）。
 *
 * Cocos 宠物舞台通过 react-native-webview 嵌入（产物 URL 取 EXPO_PUBLIC_PET_GAME_URL，
 * 未配置/加载超时自动 Fail-Open 原生降级舞台）；宿主负责页面、导航与 mall-pet API，
 * 数值全部服务端结算，游戏只发意图、不做任何业务计算。
 */

const SPECIES_EMOJI: Record<string, string> = { CAT: '🐱', DOG: '🐶', RABBIT: '🐰', FOX: '🦊', PANDA: '🐼' }
const PERSONALITY_LABEL: Record<string, string> = {
  LIVELY: '活泼', GENTLE: '温柔', TSUNDERE: '傲娇', SIMPLE: '憨厚', COOL: '高冷', CHATTERBOX: '话痨',
}
const STATUS_LABEL: Record<string, string> = {
  IDLE: '悠闲中', WORKING: '打工中', STUDYING: '读书中', FISHING: '捞瓶中', RESTING: '休息中',
}
const GROWTH_STAGE_LABEL: Record<string, string> = { BABY: '幼年', YOUNG: '成长期', ADULT: '成年' }
const PET_COLORS = ['orange', 'white', 'black', 'gray', 'brown']
const ACCESSORIES: Array<{ key: string; label: string }> = [
  { key: 'none', label: '无' },
  { key: 'bell', label: '铃铛' },
  { key: 'bow', label: '领结' },
  { key: 'glasses', label: '眼镜' },
  { key: 'scarf', label: '围巾' },
]
const PRIORITY_LABEL: Record<string, string> = { P0: '重要', P1: '普通', P2: '低' }
const RARITY_LABEL: Record<string, string> = {
  NORMAL: '普通瓶', RARE: '稀有瓶', PET: '宠物瓶', EASTER_EGG: '彩蛋瓶',
}

type PanelKey =
  | 'home' | 'care' | 'work' | 'study' | 'bottle' | 'battle'
  | 'chat' | 'achievements' | 'rankings' | 'reminders'

/** 养成面板子页签（原文档 §89：商城/背包/技能/进化/活动 + §1.1 串门 + 多宠物） */
type CareTab = 'shop' | 'inventory' | 'skills' | 'evolution' | 'events' | 'visit' | 'pets'

const CARE_TABS: Array<{ key: CareTab; label: string }> = [
  { key: 'shop', label: '🛒 商城' },
  { key: 'inventory', label: '🎒 背包' },
  { key: 'skills', label: '🌟 技能' },
  { key: 'evolution', label: '🌠 进化' },
  { key: 'events', label: '🎯 活动' },
  { key: 'visit', label: '🚪 串门' },
  { key: 'pets', label: '🐾 宠物' },
]

const SLOT_LABEL: Record<string, string> = { HAT: '帽子', NECKLACE: '项圈', SCARF: '围巾', BACKPACK: '背包' }
const ITEM_TYPE_LABEL: Record<string, string> = { EQUIPMENT: '装备', SKIN: '皮肤', SKILL_BOOK: '技能书' }
const EVENT_TYPE_LABEL: Record<string, string> = {
  BOTTLE: '捞瓶', BATTLE: '对战胜场', WORK: '打工', STUDY: '读书', FEED: '喂食', PLAY: '玩耍', VISIT: '串门',
}
/** 二期错误码 → 可读提示（服务端文案能力之外的补充） */
const CARE_ERROR_HINT: Record<string, string> = {
  PET_ITEM_ALREADY_OWNED: '已经拥有这个物品啦',
  PET_ITEM_NOT_OWNED: '先拥有才能使用哦',
  PET_SKILL_BOOK_REQUIRED: '先去商城买这本技能书',
  PET_SKILL_ALREADY_LEARNED: '这个技能已经学会啦',
  PET_LEVEL_REQUIRED: '等级还不够，再养养吧',
  PET_EVOLUTION_REQUIRED: '需要先完成进化',
  PET_EVOLUTION_MAX: '已经进化到最高阶段啦',
  PET_PET_LIMIT_REACHED: '宠物数量已达上限',
  PET_VISIT_COOLDOWN: '今天已经去过这家啦',
  PET_VISIT_SELF: '不能给自己串门哦',
  PET_EVENT_NOT_FINISHED: '活动还没完成哦',
  PET_EVENT_ALREADY_CLAIMED: '奖励已经领过啦',
  PET_EVENT_ENDED: '活动已经结束啦',
  WISH_STARLIGHT_INSUFFICIENT: '星光不够啦，让宠物去打工赚点吧',
}

const PANELS: Array<{ key: PanelKey; label: string; emoji: string }> = [
  { key: 'home', label: '小窝', emoji: '🏠' },
  { key: 'care', label: '养成', emoji: '🎒' },
  { key: 'work', label: '打工', emoji: '💼' },
  { key: 'study', label: '读书', emoji: '📚' },
  { key: 'bottle', label: '捞瓶', emoji: '🍾' },
  { key: 'battle', label: '对战', emoji: '⚔️' },
  { key: 'chat', label: '聊天', emoji: '💬' },
  { key: 'achievements', label: '成就', emoji: '🏆' },
  { key: 'rankings', label: '榜单', emoji: '🥇' },
  { key: 'reminders', label: '提醒', emoji: '🔔' },
]

const RANKING_TABS: Array<{ key: PetRankingType; label: string }> = [
  { key: 'LEVEL', label: '等级榜' },
  { key: 'BATTLE_WIN', label: '胜场榜' },
  { key: 'BOTTLE', label: '捞瓶榜' },
]

const SHARE_TYPES: Array<{ key: Parameters<typeof petApi.getShareCard>[0]; label: string }> = [
  { key: 'DAILY', label: '日常卡片' },
  { key: 'LEVEL_UP', label: '升级卡片' },
  { key: 'ACHIEVEMENT', label: '成就卡片' },
  { key: 'BOTTLE', label: '漂流瓶卡片' },
  { key: 'BATTLE', label: '对战卡片' },
]

/** 宿主 → Cocos 消息（与 pet-game PetGameBridge 同协议） */
type HostToGame =
  | { source: 'pet-host'; type: 'init'; pet: Record<string, unknown> }
  | { source: 'pet-host'; type: 'petState'; pet: Record<string, unknown> }
  | { source: 'pet-host'; type: 'actionResult'; action: string; ok: boolean; message?: string }
  | { source: 'pet-host'; type: 'battleRounds'; rounds: Array<Record<string, unknown>>; won: boolean }
  | { source: 'pet-host'; type: 'chatBubble'; content: string }

interface BattleRound {
  round: number
  actorName: string
  action: string
  damage: number
  critical: boolean
  dodged: boolean
  targetName: string
  targetRemainingHp: number
}

function toDisplayState(pet: PetInfo): Record<string, unknown> {
  return {
    name: pet.name,
    species: pet.species,
    growthStage: pet.growthStage,
    level: pet.level,
    expPercent: pet.expToNext > 0 ? (pet.exp / pet.expToNext) * 100 : 0,
    hp: pet.hp,
    maxHp: pet.maxHp,
    hunger: pet.hunger,
    happiness: pet.happiness,
    energy: pet.energy,
    cleanliness: pet.cleanliness,
    status: pet.status,
    activityName: pet.activityType ?? undefined,
  }
}

function formatClock(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds))
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, '0')}`
}

function parseResult<T>(raw: string | null | undefined): T | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

interface BarProps {
  label: string
  value: number
  max: number
  color: string
}

function StateBar({ label, value, max, color }: BarProps) {
  const colors = useTheme()
  const ratio = max > 0 ? Math.max(0, Math.min(1, value / max)) : 0
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
      <Text style={{ width: 64, fontSize: FontSize.xs, color: colors.textSecondary }}>{label}</Text>
      <View style={{ flex: 1, height: 8, borderRadius: 999, backgroundColor: colors.border, overflow: 'hidden' }}>
        <View style={{ width: `${ratio * 100}%`, height: '100%', borderRadius: 999, backgroundColor: color }} />
      </View>
      <Text style={{ width: 56, fontSize: FontSize.xs, color: colors.textTertiary, textAlign: 'right' }}>
        {value}/{max}
      </Text>
    </View>
  )
}

function ChipButton({ label, onPress, disabled, primary }: { label: string; onPress: () => void; disabled?: boolean; primary?: boolean }) {
  const colors = useTheme()
  return (
    <TouchableOpacity
      onPress={onPress}
      disabled={disabled}
      style={{
        paddingVertical: 10,
        paddingHorizontal: 16,
        borderRadius: BorderRadius.md,
        backgroundColor: disabled ? colors.border : primary ? colors.primary : colors.bgContainer,
        borderWidth: primary ? 0 : 1,
        borderColor: colors.border,
        alignItems: 'center',
      }}
    >
      <Text style={{ color: disabled ? colors.textTertiary : primary ? '#fff' : colors.text, fontSize: FontSize.sm }}>
        {label}
      </Text>
    </TouchableOpacity>
  )
}

/** 进行中任务倒计时：严格按服务端 finishedAt 计算，客户端不做任何数值推断 */
function useCountdown(finishedAt: string | null): number {
  const [remaining, setRemaining] = useState(0)
  useEffect(() => {
    if (!finishedAt) {
      setRemaining(0)
      return
    }
    const compute = () => {
      const target = Date.parse(finishedAt.length === 19 ? `${finishedAt}Z` : finishedAt)
      return Number.isNaN(target) ? 0 : Math.max(0, Math.floor((target - Date.now()) / 1000))
    }
    setRemaining(compute())
    const timer = setInterval(() => {
      const next = compute()
      setRemaining(next)
      if (next <= 0) clearInterval(timer)
    }, 1000)
    return () => clearInterval(timer)
  }, [finishedAt])
  return remaining
}

export default function PetScreen() {
  const colors = useTheme()
  const params = useLocalSearchParams<{ intent?: string }>()
  const gameUrl = process.env.EXPO_PUBLIC_PET_GAME_URL
  const webRef = useRef<WebView>(null)
  const isLoggedIn = useAuthStore((state) => state.isLoggedIn)

  const [loading, setLoading] = useState(true)
  const [pet, setPet] = useState<PetInfo | null>(null)
  const [noPet, setNoPet] = useState(false)
  const [gameReady, setGameReady] = useState(false)
  const [gameFailed, setGameFailed] = useState(!gameUrl)
  const [panel, setPanel] = useState<PanelKey>('home')
  const [adoptSpecies, setAdoptSpecies] = useState('CAT')
  const [adoptPersonality, setAdoptPersonality] = useState('LIVELY')
  const [adoptColor, setAdoptColor] = useState('orange')
  const [adoptAccessory, setAdoptAccessory] = useState('none')
  const [adoptName, setAdoptName] = useState('')
  const [jobs, setJobs] = useState<PetJobItem[]>([])
  const [studies, setStudies] = useState<PetStudyItem[]>([])
  const [opponents, setOpponents] = useState<PetOpponent[]>([])
  const [history, setHistory] = useState<PetBattleItem[]>([])
  const [bottle, setBottle] = useState<PetBottleStatus | null>(null)
  const [bottleResult, setBottleResult] = useState<PetBottleResult | null>(null)
  const [achievements, setAchievements] = useState<PetAchievement[]>([])
  const [chatMessages, setChatMessages] = useState<PetChatMessage[]>([])
  const [chatInput, setChatInput] = useState('')
  const [rankings, setRankings] = useState<PetRankingResult | null>(null)
  const [rankingType, setRankingType] = useState<PetRankingType>('LEVEL')
  const [reminders, setReminders] = useState<PetReminder[]>([])
  const [reminderUnread, setReminderUnread] = useState(0)
  const [stageBubble, setStageBubble] = useState<string | null>(null)

  // 养成面板（商城/背包/技能/进化/活动/串门/多宠物）
  const [careTab, setCareTab] = useState<CareTab>('shop')
  const [carePending, setCarePending] = useState<string | null>(null)
  const [shopBalance, setShopBalance] = useState<number | null>(null)
  const [shopItems, setShopItems] = useState<PetShopItem[]>([])
  const [inventory, setInventory] = useState<PetInventoryItem[]>([])
  const [skills, setSkills] = useState<PetSkillItem[]>([])
  const [evolution, setEvolution] = useState<PetEvolutionStatus | null>(null)
  const [events, setEvents] = useState<PetEventItem[]>([])
  const [neighbors, setNeighbors] = useState<PetVisitNeighbor[]>([])
  const [myPets, setMyPets] = useState<PetSummary[]>([])
  const [careMessage, setCareMessage] = useState<string | null>(null)

  // 聊天历史分页（cursor）
  const [chatCursor, setChatCursor] = useState<number | string | null>(null)
  const [chatHasMore, setChatHasMore] = useState(false)
  const [chatLoadingMore, setChatLoadingMore] = useState(false)

  // 档案编辑（改名 30 天冷却；冷却中仅更新外观）
  const [profileOpen, setProfileOpen] = useState(false)
  const [profileName, setProfileName] = useState('')
  const [profileColor, setProfileColor] = useState(PET_COLORS[0])
  const [profileAccessory, setProfileAccessory] = useState('none')
  const [privacyBusy, setPrivacyBusy] = useState(false)

  // 分享卡片（文案服务端生成）
  const [shareCard, setShareCard] = useState<PetShareCard | null>(null)

  // 对战回合兜底：未部署 Cocos 时宿主自行逐回合播放（服务端回合流水）
  const [battleOverlay, setBattleOverlay] = useState<{ rounds: BattleRound[]; won: boolean } | null>(null)
  const [roundIndex, setRoundIndex] = useState(0)

  const postToGame = useCallback((message: HostToGame) => {
    webRef.current?.injectJavaScript(
      `window.__petHostMessage && window.__petHostMessage(${JSON.stringify(JSON.stringify(message))}); true;`,
    )
  }, [])

  const syncStage = useCallback((next: PetInfo) => {
    postToGame({ source: 'pet-host', type: 'petState', pet: toDisplayState(next) })
  }, [postToGame])

  const showBubble = useCallback((content: string) => {
    setStageBubble(content)
    postToGame({ source: 'pet-host', type: 'chatBubble', content })
  }, [postToGame])

  const refresh = useCallback(async () => {
    try {
      const { data: res } = await petApi.getMyPet()
      if (res.success && res.data) {
        setPet(res.data)
        setNoPet(false)
        if (gameReady) syncStage(res.data)
      }
    } catch {
      setNoPet(true)
    } finally {
      setLoading(false)
    }
  }, [gameReady, syncStage])

  // 登录守卫：未登录直接跳登录页（与 Web/小程序端一致，避免 401 后页面空白）
  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
      return
    }
    refresh()
  }, [isLoggedIn, refresh])

  useEffect(() => {
    if (!gameUrl) return
    const timer = setTimeout(() => setGameFailed((f) => !gameReady), 2500)
    return () => clearTimeout(timer)
  }, [gameUrl, gameReady])

  // 提醒未读角标
  useEffect(() => {
    if (!pet) return
    petApi.getReminderUnreadCount()
      .then(({ data: res }) => { if (res.success) setReminderUnread(res.data ?? 0) })
      .catch(() => setReminderUnread(0))
  }, [pet, reminders])

  /** 养成面板数据（按子页签惰性加载，减少无谓请求） */
  const loadCareData = useCallback(async (tab: CareTab) => {
    try {
      if (tab === 'shop') {
        const { data: res } = await petApi.getShop()
        if (res.success && res.data) {
          setShopBalance(res.data.starlightBalance)
          setShopItems(res.data.items || [])
        }
      } else if (tab === 'inventory') {
        const { data: res } = await petApi.listInventory()
        if (res.success) setInventory(res.data || [])
      } else if (tab === 'skills') {
        const { data: res } = await petApi.listSkills()
        if (res.success) setSkills(res.data || [])
      } else if (tab === 'evolution') {
        const { data: res } = await petApi.getEvolution()
        if (res.success) setEvolution(res.data)
      } else if (tab === 'events') {
        const { data: res } = await petApi.listEvents()
        if (res.success) setEvents(res.data || [])
      } else if (tab === 'visit') {
        const { data: res } = await petApi.listVisitNeighbors()
        if (res.success) setNeighbors(res.data || [])
      } else {
        const { data: res } = await petApi.listMyPets()
        if (res.success) setMyPets(res.data || [])
      }
    } catch {
      // 拦截器已提示
    }
  }, [])

  /** 养成面板统一动作：服务端权威 → 提示 → 刷新面板与主宠状态 */
  const runCare = async (key: string, action: () => Promise<{ data: { success: boolean } }>, successText: string) => {
    setCarePending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        Alert.alert('操作成功', successText)
        await loadCareData(careTab)
        refresh()
      }
    } catch (error) {
      const code = (error as { code?: string }).code
      Alert.alert('暂时不能这么做', (code && CARE_ERROR_HINT[code]) || '请稍后再试')
    } finally {
      setCarePending(null)
    }
  }

  /** 加载更早的聊天记录（cursor 分页） */
  const loadMoreChat = async () => {
    if (!chatCursor || chatLoadingMore) return
    setChatLoadingMore(true)
    try {
      const { data: res } = await petApi.chatHistory({ cursor: chatCursor, pageSize: 30 })
      if (res.success) {
        const older = (res.data || []).slice().reverse()
        if (older.length === 0) {
          setChatHasMore(false)
        } else {
          setChatMessages((prev) => [...older, ...prev])
          setChatCursor(older[0]?.messageId ?? null)
          setChatHasMore(older.length >= 30)
        }
      }
    } catch {
      // 拦截器已提示
    } finally {
      setChatLoadingMore(false)
    }
  }

  const loadPanelData = useCallback(async (key: PanelKey) => {
    if (key === 'work') {
      const { data: res } = await petApi.listJobs()
      if (res.success) setJobs(res.data || [])
    } else if (key === 'study') {
      const { data: res } = await petApi.listStudies()
      if (res.success) setStudies(res.data || [])
    } else if (key === 'bottle') {
      const { data: res } = await petApi.getBottleStatus()
      if (res.success) setBottle(res.data)
    } else if (key === 'battle') {
      const [opponentRes, historyRes] = await Promise.all([petApi.listOpponents(), petApi.listBattleHistory({ page: 1, pageSize: 10 })])
      if (opponentRes.data.success) setOpponents(opponentRes.data.data || [])
      if (historyRes.data.success) setHistory(historyRes.data.data || [])
    } else if (key === 'achievements') {
      // 成就墙 + 宠物动态（提醒即"宠物动态"数据源，与 Web 端同一展示口径）
      const [achRes, remRes] = await Promise.all([petApi.listAchievements(), petApi.listReminders()])
      if (achRes.data.success) setAchievements(achRes.data.data || [])
      if (remRes.data.success) setReminders(remRes.data.data || [])
    } else if (key === 'chat') {
      const { data: res } = await petApi.chatHistory({ pageSize: 30 })
      if (res.success) {
        const page = (res.data || []).slice().reverse()
        setChatMessages(page)
        const oldest = page[0]
        setChatCursor(oldest ? oldest.messageId : null)
        setChatHasMore((res.data || []).length >= 30 && Boolean(oldest))
      }
    } else if (key === 'care') {
      await loadCareData(careTab)
    } else if (key === 'rankings') {
      const { data: res } = await petApi.getRankings(rankingType)
      if (res.success) setRankings(res.data)
    } else if (key === 'reminders') {
      const { data: res } = await petApi.listReminders()
      if (res.success) setReminders(res.data || [])
    }
  }, [rankingType, careTab, loadCareData])

  useEffect(() => {
    if (pet) loadPanelData(panel)
  }, [panel, pet, loadPanelData])

  // 微信 web-view 宿主页 navigateTo 落地：intent 参数触发对应动作
  useEffect(() => {
    const intent = params.intent
    if (!intent) return
    setPanel('home')
    if (['feed', 'play', 'clean', 'rest'].includes(intent)) {
      runInteraction(intent as 'feed' | 'play' | 'clean' | 'rest')
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
  const bottleRemaining = useCountdown(bottle?.fishing ? bottle.finishedAt : null)

  // 任务到期后自动刷新一次，让"可领取"自然出现
  const activityKey = pet?.activityFinishedAt ?? null
  useEffect(() => {
    if (!activityKey || activeRemaining > 0) return
    const target = Date.parse(activityKey.length === 19 ? `${activityKey}Z` : activityKey)
    if (Number.isNaN(target) || target > Date.now()) return
    const timer = setTimeout(() => { refresh(); loadPanelData(panel) }, 1500)
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

  const runInteraction = async (action: 'feed' | 'play' | 'clean' | 'rest') => {
    if (!pet) return
    try {
      const { data: res } =
        action === 'feed' ? await petApi.feed()
          : action === 'play' ? await petApi.play()
            : action === 'clean' ? await petApi.clean()
              : await petApi.rest()
      if (res.success && res.data) {
        setPet(res.data)
        if (gameReady) {
          syncStage(res.data)
          postToGame({ source: 'pet-host', type: 'actionResult', action, ok: true })
        }
      }
    } catch {
      if (gameReady) {
        postToGame({ source: 'pet-host', type: 'actionResult', action, ok: false, message: '现在不行哦' })
      }
    }
  }

  const adopt = async () => {
    if (!adoptName.trim()) return
    try {
      const { data: res } = await petApi.createPet({
        name: adoptName.trim(),
        species: adoptSpecies as PetInfo['species'],
        personality: adoptPersonality as PetInfo['personality'],
        color: adoptColor,
        accessory: adoptAccessory,
      })
      if (res.success) {
        setNoPet(false)
        setAdoptName('')
        refresh()
      }
    } catch {
      // 拦截器已提示
    }
  }

  const openProfile = useCallback(() => {
    if (!pet) return
    setProfileName(pet.name)
    const appearance = parseResult<{ color?: string; accessory?: string }>(pet.appearance)
    setProfileColor(appearance?.color ?? PET_COLORS[0])
    setProfileAccessory(appearance?.accessory ?? 'none')
    setProfileOpen(true)
  }, [pet])

  const saveProfile = async () => {
    if (!pet) return
    const wantRename = profileName.trim() && profileName.trim() !== pet.name
    try {
      const { data: appearanceRes } = await petApi.updateAppearance({ color: profileColor, accessory: profileAccessory })
      if (appearanceRes.success && appearanceRes.data) setPet(appearanceRes.data)
      if (wantRename) {
        const { data: renameRes } = await petApi.renamePet({ name: profileName.trim() })
        if (renameRes.success && renameRes.data) setPet(renameRes.data)
      }
      setProfileOpen(false)
      const latest = await petApi.getMyPet()
      if (latest.data.success && latest.data.data) {
        setPet(latest.data.data)
        if (gameReady) syncStage(latest.data.data)
      }
    } catch (e) {
      // 改名冷却（409 PET_RENAME_COOLDOWN）时外观已保存，剩余弹窗保留给用户
      Alert.alert('宠物档案', (e as Error)?.message ?? '保存失败，请稍后重试')
    }
  }

  const togglePrivacy = async (next: boolean) => {
    if (!pet) return
    setPrivacyBusy(true)
    try {
      await petApi.updatePrivacy({ isPublic: next })
      setPet({ ...pet, isPublic: next })
    } catch {
      // 拦截器已提示
    } finally {
      setPrivacyBusy(false)
    }
  }

  const onWebViewMessage = useCallback((event: { nativeEvent: { data: string } }) => {
    try {
      const message = JSON.parse(event.nativeEvent.data)
      if (message?.source !== 'pet-game') return
      if (message.type === 'ready') {
        setGameReady(true)
        if (pet) postToGame({ source: 'pet-host', type: 'init', pet: toDisplayState(pet) })
      } else if (message.type === 'intent') {
        const action = message.action as string
        if (['feed', 'play', 'clean', 'rest'].includes(action)) {
          runInteraction(action as 'feed' | 'play' | 'clean' | 'rest')
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
      postToGame({ source: 'pet-host', type: 'battleRounds', rounds: rounds as unknown as Array<Record<string, unknown>>, won })
      return
    }
    setRoundIndex(0)
    setBattleOverlay({ rounds, won })
  }, [gameReady, postToGame])

  const battleRespond = async (battle: PetBattleItem, accept: boolean) => {
    try {
      const { data: res } = accept ? await petApi.acceptBattle(battle.battleId) : await petApi.declineBattle(battle.battleId)
      if (res.success) {
        presentBattle(res.data)
        await loadPanelData('battle')
        refresh()
      }
    } catch {
      // 拦截器已提示
    }
  }

  const sendChat = async () => {
    const text = chatInput.trim()
    if (!text) return
    setChatInput('')
    try {
      const { data: res } = await petApi.chat(text)
      if (res.success && res.data) {
        setChatMessages((prev) => [...prev, {
          messageId: `${res.data.messageId}-u`, role: 'USER', content: text, isAiReply: false, createdAt: null,
        }, res.data])
        showBubble(res.data.content)
      }
    } catch {
      // 拦截器已提示（限频 429）
    }
  }

  const openBottleResult = async () => {
    try {
      const { data: res } = await petApi.claimBottle()
      if (!res.success) return
      const result = parseResult<PetBottleResult>(res.data.result)
      await loadPanelData('bottle')
      refresh()
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

  const loadShareCard = async (type: Parameters<typeof petApi.getShareCard>[0]) => {
    try {
      const { data: res } = await petApi.getShareCard(type)
      if (res.success && res.data) setShareCard(res.data)
    } catch {
      // 拦截器已提示
    }
  }

  const markReminderRead = async (item: PetReminder) => {
    if (item.isRead) return
    try {
      await notificationApi.markRead(Number(item.notificationId))
      setReminders((prev) => prev.map((r) => (r.notificationId === item.notificationId ? { ...r, isRead: true } : r)))
      setReminderUnread((n) => Math.max(0, n - 1))
    } catch {
      // 拦截器已提示
    }
  }

  const markAllRemindersRead = async () => {
    try {
      await notificationApi.markAllRead()
      setReminders((prev) => prev.map((r) => ({ ...r, isRead: true })))
      setReminderUnread(0)
    } catch {
      // 拦截器已提示
    }
  }

  const copyShare = async () => {
    if (!shareCard) return
    await Clipboard.setStringAsync(`${shareCard.title}\n${shareCard.content}${shareCard.highlight ? `\n${shareCard.highlight}` : ''}`)
    setShareCard(null)
    // 复制后直接跳到发布页（与 Web 端一致：不自动发帖，用户自行确认发布）
    router.push('/publish')
  }

  const barRows = useMemo(() => {
    if (!pet) return []
    return [
      { label: '❤️ 生命', value: pet.hp, max: pet.maxHp, color: '#ff6c6c' },
      { label: '🍖 饱食', value: pet.hunger, max: 100, color: '#ffb258' },
      { label: '💗 心情', value: pet.happiness, max: 100, color: '#ff69b4' },
      { label: '⚡ 精力', value: pet.energy, max: 100, color: '#62d88a' },
      { label: '🧼 清洁', value: pet.cleanliness, max: 100, color: '#60beff' },
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
    return (
      <ScrollView style={{ flex: 1, backgroundColor: colors.bgBase }} contentContainerStyle={{ padding: Spacing.lg }}>
        <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: colors.text, textAlign: 'center', marginTop: Spacing.xl }}>
          🐾 领养一只属于你的宠物
        </Text>
        <Text style={{ fontSize: FontSize.sm, color: colors.textSecondary, textAlign: 'center', marginTop: Spacing.xs, marginBottom: Spacing.lg }}>
          陪你打工、读书、捞漂流瓶、聊天
        </Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, justifyContent: 'center' }}>
          {Object.entries(SPECIES_EMOJI).map(([key, emoji]) => (
            <TouchableOpacity
              key={key}
              onPress={() => setAdoptSpecies(key)}
              style={{
                width: 88, alignItems: 'center', padding: Spacing.sm, borderRadius: BorderRadius.lg,
                borderWidth: 2, borderColor: adoptSpecies === key ? colors.primary : colors.border,
              }}
            >
              <Text style={{ fontSize: 36 }}>{emoji}</Text>
            </TouchableOpacity>
          ))}
        </View>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, justifyContent: 'center', marginTop: Spacing.md }}>
          {Object.entries(PERSONALITY_LABEL).map(([key, label]) => (
            <TouchableOpacity
              key={key}
              onPress={() => setAdoptPersonality(key)}
              style={{
                paddingVertical: 6, paddingHorizontal: 14, borderRadius: 999,
                borderWidth: 1, borderColor: adoptPersonality === key ? colors.primary : colors.border,
                backgroundColor: adoptPersonality === key ? colors.primary : 'transparent',
              }}
            >
              <Text style={{ color: adoptPersonality === key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{label}</Text>
            </TouchableOpacity>
          ))}
        </View>
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs, marginTop: Spacing.md, marginBottom: 4 }}>外观颜色</Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, justifyContent: 'center' }}>
          {PET_COLORS.map((key) => (
            <TouchableOpacity
              key={key}
              onPress={() => setAdoptColor(key)}
              style={{
                paddingVertical: 6, paddingHorizontal: 14, borderRadius: 999,
                borderWidth: 1, borderColor: adoptColor === key ? colors.primary : colors.border,
                backgroundColor: adoptColor === key ? colors.primary : 'transparent',
              }}
            >
              <Text style={{ color: adoptColor === key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{key}</Text>
            </TouchableOpacity>
          ))}
        </View>
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs, marginTop: Spacing.md, marginBottom: 4 }}>配饰</Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, justifyContent: 'center' }}>
          {ACCESSORIES.map((item) => (
            <TouchableOpacity
              key={item.key}
              onPress={() => setAdoptAccessory(item.key)}
              style={{
                paddingVertical: 6, paddingHorizontal: 14, borderRadius: 999,
                borderWidth: 1, borderColor: adoptAccessory === item.key ? colors.primary : colors.border,
                backgroundColor: adoptAccessory === item.key ? colors.primary : 'transparent',
              }}
            >
              <Text style={{ color: adoptAccessory === item.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{item.label}</Text>
            </TouchableOpacity>
          ))}
        </View>
        <TextInput
          value={adoptName}
          onChangeText={setAdoptName}
          maxLength={12}
          placeholder="给它取个名字（1-12 字）"
          placeholderTextColor={colors.textTertiary}
          style={{
            marginTop: Spacing.lg, paddingHorizontal: Spacing.md, paddingVertical: 12,
            borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border,
            color: colors.text, backgroundColor: colors.bgContainer,
          }}
        />
        <View style={{ marginTop: Spacing.md }}>
          <ChipButton label="领养它" primary onPress={adopt} disabled={!adoptName.trim()} />
        </View>
      </ScrollView>
    )
  }

  const activeRound = battleOverlay && roundIndex < battleOverlay.rounds.length
    ? battleOverlay.rounds[roundIndex]
    : null

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bgBase }} contentContainerStyle={{ padding: Spacing.md, paddingBottom: 48 }}>
      {/* Cocos 舞台（未部署 Fail-Open 原生降级） */}
      <View style={{ height: 320, borderRadius: BorderRadius.lg, overflow: 'hidden', marginBottom: Spacing.md, backgroundColor: '#18223a' }}>
        {gameUrl && !gameFailed
          ? (
            <WebView
              ref={webRef}
              source={{ uri: gameUrl }}
              onMessage={onWebViewMessage}
              scrollEnabled={false}
              style={{ flex: 1, backgroundColor: 'transparent' }}
            />
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

      {/* 头部信息 */}
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: colors.text }}>
          {SPECIES_EMOJI[pet.species]} {pet.name} · Lv.{pet.level}
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

      {/* 状态条 */}
      <View style={{ marginTop: Spacing.md, gap: 8 }}>
        {barRows.map((row) => <StateBar key={row.label} {...row} />)}
      </View>

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
            onPress={async () => {
              try {
                if (pet.claimableActivityType === 'WORK') await petApi.claimWork()
                else if (pet.claimableActivityType === 'STUDY') await petApi.claimStudy()
                else await openBottleResult()
                refresh()
                loadPanelData(panel)
              } catch { /* 已领取 409 */ }
            }}
          />
        </View>
      )}

      {/* 互动按钮（Cocos 意图与原生按钮共用同一 API） */}
      <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.md, flexWrap: 'wrap' }}>
        <ChipButton label="🍖 喂食" primary onPress={() => runInteraction('feed')} />
        <ChipButton label="🎾 玩耍" primary onPress={() => runInteraction('play')} />
        <ChipButton label="🫧 清洁" primary onPress={() => runInteraction('clean')} />
        <ChipButton label="💤 休息" primary onPress={() => runInteraction('rest')} />
        <ChipButton label="🎀 档案" onPress={openProfile} />
      </View>

      {/* 面板切换 */}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: Spacing.md }}>
        {PANELS.map((item) => (
          <TouchableOpacity
            key={item.key}
            onPress={() => setPanel(item.key)}
            style={{
              paddingVertical: 8, paddingHorizontal: 14, borderRadius: 999,
              backgroundColor: panel === item.key ? colors.primary : colors.bgContainer,
              borderWidth: 1, borderColor: panel === item.key ? colors.primary : colors.border,
            }}
          >
            <Text style={{ color: panel === item.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>
              {item.emoji} {item.label}
              {item.key === 'reminders' && reminderUnread > 0 ? ` (${reminderUnread})` : ''}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* 面板内容 */}
      <View style={{ marginTop: Spacing.md, backgroundColor: colors.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.md, borderWidth: 1, borderColor: colors.border }}>
        {panel === 'home' && (
          <View style={{ gap: 6 }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>养成小贴士</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🍖 饱食和清洁随时间下降，记得回来照顾它</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>💼 打工赚星光，📚 读书涨智力</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🍾 宠物会定时帮你捞社区漂流瓶并主动提醒</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>⚔️ 对战由服务端计算，输了也有经验</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: Spacing.sm }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm }}>在个人主页展示宠物</Text>
              <Switch value={pet.isPublic} disabled={privacyBusy} onValueChange={togglePrivacy} />
            </View>
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
              关闭后不会进入宠物榜单，其他用户也看不到它
            </Text>
            <View style={{ marginTop: Spacing.sm }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>宠物动态卡片</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs, marginTop: 6 }}>
                {SHARE_TYPES.map((item) => (
                  <ChipButton key={item.key} label={item.label} onPress={() => loadShareCard(item.key)} />
                ))}
              </View>
            </View>
          </View>
        )}

        {panel === 'work' && (
          <View style={{ gap: Spacing.sm }}>
            {jobs.map((job) => (
              <View key={String(job.configId)} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{job.name}</Text>
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                    ⏱ {Math.round(job.durationSeconds / 60)} 分钟 · ⚡-{job.energyCost} · ✨+{job.expReward} · ⭐+{job.currencyReward}
                  </Text>
                </View>
                {job.eligible
                  ? (
                    <ChipButton label="接单" primary onPress={async () => {
                      try {
                        const { data: res } = await petApi.startWork(job.configId)
                        if (res.success) { refresh(); loadPanelData('work') }
                      } catch { /* 拦截器已提示 */ }
                    }} />
                  )
                  : <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>Lv.{job.requiredLevel} 解锁</Text>}
              </View>
            ))}
            {pet.claimableActivityType === 'WORK' && (
              <ChipButton label="领取打工奖励" primary onPress={async () => {
                try {
                  const { data: res } = await petApi.claimWork()
                  if (res.success) { refresh(); loadPanelData('work') }
                } catch { /* 已领取 409 */ }
              }} />
            )}
          </View>
        )}

        {panel === 'study' && (
          <View style={{ gap: Spacing.sm }}>
            {studies.map((study) => (
              <View key={String(study.configId)} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{study.name}</Text>
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                    ⏱ {Math.round(study.durationSeconds / 60)} 分钟 · ✨+{study.expReward} · 🧠+{study.intelligenceReward}
                  </Text>
                </View>
                {study.eligible
                  ? (
                    <ChipButton label="上课" primary onPress={async () => {
                      try {
                        const { data: res } = await petApi.startStudy(study.configId)
                        if (res.success) { refresh(); loadPanelData('study') }
                      } catch { /* 拦截器已提示 */ }
                    }} />
                  )
                  : <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>Lv.{study.requiredLevel} 解锁</Text>}
              </View>
            ))}
            {pet.claimableActivityType === 'STUDY' && (
              <ChipButton label="领取学习奖励" primary onPress={async () => {
                try {
                  const { data: res } = await petApi.claimStudy()
                  if (res.success) { refresh(); loadPanelData('study') }
                } catch { /* 已领取 409 */ }
              }} />
            )}
          </View>
        )}

        {panel === 'bottle' && bottle && (
          <View style={{ gap: Spacing.sm }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm }}>🌊 {bottle.unlockedArea} · 估算成功率 {Math.round(bottle.estimatedSuccessRate * 100)}%</Text>
            {bottle.fishing
              ? <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🐾 捞瓶中…剩余 {formatClock(bottleRemaining)}</Text>
              : (
                <ChipButton
                  label={bottle.canClaim ? '查看捞瓶结果' : bottle.cooldownRemainingSeconds > 0 ? `冷却中 ${Math.ceil(bottle.cooldownRemainingSeconds / 60)} 分钟` : '让宠物去捞漂流瓶'}
                  primary={!bottle.canClaim && bottle.cooldownRemainingSeconds <= 0}
                  disabled={bottle.cooldownRemainingSeconds > 0 && !bottle.canClaim}
                  onPress={async () => {
                    try {
                      if (bottle.canClaim) {
                        await openBottleResult()
                        return
                      }
                      const { data: res } = await petApi.startBottle()
                      if (res.success) {
                        loadPanelData('bottle')
                        refresh()
                      }
                    } catch { /* 拦截器已提示 */ }
                  }}
                />
              )}
            {bottle.lastOutcome === 'EMPTY' && (
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>这次空手而归啦，休息一下再试试～</Text>
            )}
            {bottle.lastOutcome === 'FAILED' && (
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>心愿服务暂时不可用，点上面的按钮可以重试领取</Text>
            )}
          </View>
        )}

        {panel === 'battle' && (
          <View style={{ gap: Spacing.md }}>
            {history.filter((battle) => battle.status === 'PENDING' && battle.role === 'DEFENDER').map((battle) => (
              <View key={String(battle.battleId)} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={{ color: colors.text, fontSize: FontSize.sm }}>
                  ⚔️ 收到 {battle.attackerPetName ?? `宠物 #${battle.attackerPetId}`} 的挑战
                </Text>
                <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
                  <ChipButton label="应战" primary onPress={() => battleRespond(battle, true)} />
                  <ChipButton label="婉拒" onPress={() => battleRespond(battle, false)} />
                </View>
              </View>
            ))}
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>选择对手</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
              {opponents.map((opponent) => (
                <TouchableOpacity
                  key={`${String(opponent.petId)}-${opponent.name}`}
                  onPress={async () => {
                    try {
                      const { data: res } = await petApi.challenge(opponent.isWild ? { mode: 'PVE' } : { mode: 'PVP', defenderPetId: opponent.petId })
                      if (res.success) presentBattle(res.data)
                      loadPanelData('battle')
                      refresh()
                    } catch { /* 拦截器已提示 */ }
                  }}
                  style={{ width: 104, alignItems: 'center', padding: Spacing.sm, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: colors.border }}
                >
                  <Text style={{ fontSize: 30 }}>{SPECIES_EMOJI[opponent.species] || '🐾'}</Text>
                  <Text style={{ color: colors.text, fontSize: FontSize.xs }}>{opponent.name}</Text>
                  <Text style={{ color: colors.textTertiary, fontSize: 10 }}>Lv.{opponent.level} · {opponent.isWild ? '野生' : opponent.ownerNickname}</Text>
                </TouchableOpacity>
              ))}
            </View>
            {history.filter((battle) => battle.status !== 'PENDING').slice(0, 5).map((battle) => (
              <TouchableOpacity
                key={String(battle.battleId)}
                onPress={async () => {
                  const { data: res } = await petApi.getBattleDetail(battle.battleId).catch(() => ({ data: { success: false, data: null as PetBattleItem | null } }))
                  if (res.success && res.data) presentBattle(res.data)
                }}
                style={{ flexDirection: 'row', justifyContent: 'space-between' }}
              >
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                  {battle.status === 'FINISHED'
                    ? (battle.winnerPetId === (battle.role === 'ATTACKER' ? battle.attackerPetId : battle.defenderPetId) ? '🏆 胜利' : '💧 战败')
                    : battle.status === 'DECLINED' ? '🚫 被婉拒' : '⌛ 未应战'}
                  {' · '}{battle.role === 'ATTACKER' ? battle.defenderPetName : battle.attackerPetName ?? '对手'}
                </Text>
                <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>+{battle.expReward} 经验</Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {panel === 'chat' && (
          <View style={{ gap: Spacing.sm }}>
            {chatHasMore && (
              <View style={{ alignItems: 'center' }}>
                <ChipButton label={chatLoadingMore ? '加载中…' : '加载更早的对话'} onPress={loadMoreChat} />
              </View>
            )}
            <View style={{ maxHeight: 320, gap: 6 }}>
              {chatMessages.map((item) => (
                <View
                  key={String(item.messageId)}
                  style={{
                    alignSelf: item.role === 'USER' ? 'flex-end' : 'flex-start',
                    maxWidth: '85%',
                    padding: 10,
                    borderRadius: BorderRadius.lg,
                    backgroundColor: item.role === 'USER' ? colors.primary : colors.bgBase,
                  }}
                >
                  <Text style={{ color: item.role === 'USER' ? '#fff' : colors.text, fontSize: FontSize.sm }}>{item.content}</Text>
                  {!item.isAiReply && item.role === 'PET' && (
                    <Text style={{ color: colors.textTertiary, fontSize: 10, marginTop: 2 }}>模板回复</Text>
                  )}
                </View>
              ))}
            </View>
            <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
              <TextInput
                value={chatInput}
                onChangeText={setChatInput}
                maxLength={500}
                placeholder="跟宠物聊聊…（每日 20 条）"
                placeholderTextColor={colors.textTertiary}
                style={{
                  flex: 1, paddingHorizontal: Spacing.md, paddingVertical: 10, borderRadius: BorderRadius.md,
                  borderWidth: 1, borderColor: colors.border, color: colors.text, backgroundColor: colors.bgBase,
                }}
              />
              <ChipButton label="发送" primary onPress={sendChat} />
            </View>
          </View>
        )}

        {panel === 'care' && (
          <View style={{ gap: Spacing.sm }}>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
              {CARE_TABS.map((tab) => (
                <TouchableOpacity
                  key={tab.key}
                  onPress={() => setCareTab(tab.key)}
                  style={{
                    paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
                    backgroundColor: careTab === tab.key ? colors.primary : colors.bgBase,
                    borderWidth: 1, borderColor: careTab === tab.key ? colors.primary : colors.border,
                  }}
                >
                  <Text style={{ color: careTab === tab.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{tab.label}</Text>
                </TouchableOpacity>
              ))}
            </View>
            {careMessage && <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{careMessage}</Text>}

            {careTab === 'shop' && (
              <View style={{ gap: Spacing.sm }}>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                  ✨ 星光余额：{shopBalance === null ? '暂不可用' : shopBalance}
                </Text>
                {shopItems.map((item) => (
                  <View key={`${item.itemType}-${item.code}`} style={{
                    padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2,
                  }}>
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                      {item.icon} {item.name} <Text style={{ color: colors.textTertiary, fontSize: 10 }}>{ITEM_TYPE_LABEL[item.itemType]}{item.slot ? ` · ${SLOT_LABEL[item.slot] ?? item.slot}` : ''}</Text>
                    </Text>
                    <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                      需要 Lv.{item.requiredLevel}{item.requiredEvolutionStage > 0 ? ` · 进化 ${item.requiredEvolutionStage} 阶` : ''}
                      {(item.bonusStrength + item.bonusIntelligence + item.bonusAgility + item.bonusCharm + item.bonusMaxHp) > 0
                        ? ` · 力量+${item.bonusStrength} 智力+${item.bonusIntelligence} 敏捷+${item.bonusAgility} 魅力+${item.bonusCharm} 生命+${item.bonusMaxHp}`
                        : ''}
                    </Text>
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                      <Text style={{ color: colors.primary, fontSize: FontSize.xs }}>✨ {item.priceStarlight}</Text>
                      {item.owned ? (
                        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>已拥有</Text>
                      ) : (
                        <ChipButton
                          label={item.eligible ? '购买' : (item.lockReason ?? '未解锁')}
                          primary
                          disabled={!item.eligible || carePending === `buy-${item.code}`}
                          onPress={() => runCare(`buy-${item.code}`, () => petApi.buyItem({ itemType: item.itemType, itemCode: item.code }), '购买成功！')}
                        />
                      )}
                    </View>
                  </View>
                ))}
              </View>
            )}

            {careTab === 'inventory' && (
              <View style={{ gap: Spacing.sm }}>
                {inventory.length === 0 && <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>背包还是空的，去商城逛逛吧～</Text>}
                {inventory.map((item) => (
                  <View key={`${item.itemType}-${item.code}`} style={{
                    padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2,
                  }}>
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                      {item.icon} {item.name}
                      {item.equipped ? ' · 使用中' : ''}{item.used ? ' · 已学习' : ''}
                    </Text>
                    <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                      {ITEM_TYPE_LABEL[item.itemType]}{item.slot ? ` · ${SLOT_LABEL[item.slot] ?? item.slot}` : ''}
                    </Text>
                    <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.xs }}>
                      {item.itemType === 'EQUIPMENT' && (
                        item.equipped ? (
                          <ChipButton label="卸下" onPress={() => runCare(`unequip-${item.slot}`, () => petApi.unequipItem(item.slot ?? ''), '已卸下')} />
                        ) : (
                          <ChipButton label="穿戴" primary onPress={() => runCare(`equip-${item.code}`, () => petApi.equipItem(item.code), '已穿戴')} />
                        )
                      )}
                      {item.itemType === 'SKIN' && (
                        item.equipped ? (
                          <ChipButton label="卸下" onPress={() => runCare('remove-skin', () => petApi.removeSkin(), '已换回原生外观')} />
                        ) : (
                          <ChipButton label="穿戴" primary onPress={() => runCare(`skin-${item.code}`, () => petApi.wearSkin(item.code), '已穿上新皮肤')} />
                        )
                      )}
                    </View>
                  </View>
                ))}
              </View>
            )}

            {careTab === 'skills' && (
              <View style={{ gap: Spacing.sm }}>
                {skills.map((skill) => (
                  <View key={skill.code} style={{
                    padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2,
                  }}>
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                      {skill.icon} {skill.name} <Text style={{ color: colors.textTertiary, fontSize: 10 }}>{skill.skillType === 'ACTIVE' ? '主动技' : '被动技'} · {skill.effectText}</Text>
                    </Text>
                    <Text style={{ color: colors.textTertiary, fontSize: 10 }}>{skill.description}</Text>
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                      <Text style={{ color: colors.primary, fontSize: FontSize.xs }}>✨ {skill.priceStarlight} · 需要 Lv.{skill.requiredLevel}</Text>
                      {skill.learned ? (
                        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>已学会</Text>
                      ) : skill.bookOwned ? (
                        <ChipButton
                          label="学习"
                          primary
                          disabled={carePending === `learn-${skill.code}`}
                          onPress={() => runCare(`learn-${skill.code}`, () => petApi.learnSkill(skill.code), '学会新技能啦！')}
                        />
                      ) : (
                        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>需要技能书</Text>
                      )}
                    </View>
                  </View>
                ))}
              </View>
            )}

            {careTab === 'evolution' && evolution && (
              <View style={{ gap: Spacing.sm }}>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                  当前进化阶段：{evolution.currentStage} / {evolution.maxStage}
                </Text>
                {evolution.nextCode === null ? (
                  <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>🎉 已经进化到最高阶段啦</Text>
                ) : (
                  <View style={{ padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2 }}>
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{evolution.icon} {evolution.nextName}</Text>
                    <Text style={{ color: colors.textTertiary, fontSize: 10 }}>{evolution.nextDescription}</Text>
                    <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                      需要 Lv.{evolution.requiredLevel} · 消耗 ✨{evolution.costStarlight} · 生命+{evolution.bonusMaxHp} 力量+{evolution.bonusStrength} 智力+{evolution.bonusIntelligence} 敏捷+{evolution.bonusAgility} 魅力+{evolution.bonusCharm}
                    </Text>
                    <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
                      <ChipButton
                        label={evolution.canEvolve ? '进化' : (evolution.lockReason ?? '条件未满足')}
                        primary
                        disabled={!evolution.canEvolve || carePending === 'evolve'}
                        onPress={() => runCare('evolve', () => petApi.evolve(), '进化成功！')}
                      />
                    </View>
                  </View>
                )}
              </View>
            )}

            {careTab === 'events' && (
              <View style={{ gap: Spacing.sm }}>
                {events.length === 0 && <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>暂时没有进行中的活动</Text>}
                {events.map((event) => (
                  <View key={event.code} style={{
                    padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2,
                  }}>
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>🎯 {event.name}</Text>
                    <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                      {EVENT_TYPE_LABEL[event.eventType] ?? event.eventType} 进度 {event.progress}/{event.targetValue} · 奖励 经验+{event.rewardExp} ✨+{event.rewardStarlight}
                    </Text>
                    <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
                      {event.claimed ? (
                        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>已领取</Text>
                      ) : (
                        <ChipButton
                          label={event.claimable ? '领取奖励' : `还差 ${Math.max(0, event.targetValue - event.progress)} 次`}
                          primary
                          disabled={!event.claimable || carePending === `event-${event.code}`}
                          onPress={() => runCare(`event-${event.code}`, () => petApi.claimEvent(event.code), '奖励到手啦！')}
                        />
                      )}
                    </View>
                  </View>
                ))}
              </View>
            )}

            {careTab === 'visit' && (
              <View style={{ gap: Spacing.sm }}>
                {neighbors.length === 0 && <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>暂时没有可串门的邻居</Text>}
                {neighbors.map((neighbor) => (
                  <View key={String(neighbor.petId)} style={{
                    padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border, gap: 2,
                  }}>
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                      {SPECIES_EMOJI[neighbor.species] ?? '🐾'} {neighbor.name}
                    </Text>
                    <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                      Lv.{neighbor.level} · {neighbor.ownerNickname}{neighbor.evolutionStage > 0 ? ` · 进化${neighbor.evolutionStage}阶` : ''}
                    </Text>
                    <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
                      {neighbor.visitedToday ? (
                        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>今日已去过</Text>
                      ) : (
                        <ChipButton
                          label="去串门"
                          primary
                          disabled={carePending === `visit-${neighbor.petId}`}
                          onPress={() => runCare(`visit-${neighbor.petId}`, async () => {
                            const res = await petApi.visitNeighbor(neighbor.petId)
                            if (res.data.success && res.data.data) setCareMessage(res.data.data.message)
                            return res
                          }, '串门成功！')}
                        />
                      )}
                    </View>
                  </View>
                ))}
              </View>
            )}

            {careTab === 'pets' && (
              <View style={{ gap: Spacing.sm }}>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                  宠物 {pet.petCount} / {pet.maxPets}（日常玩法作用于主宠）
                </Text>
                {myPets.map((item) => (
                  <View key={String(item.petId)} style={{
                    padding: Spacing.sm, borderRadius: BorderRadius.md, borderWidth: 1, borderColor: colors.border,
                    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
                  }}>
                    <View>
                      <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                        {SPECIES_EMOJI[item.species] ?? '🐾'} {item.name}{item.isActive ? ' · 主宠' : ''}
                      </Text>
                      <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                        Lv.{item.level} · {GROWTH_STAGE_LABEL[item.growthStage] ?? item.growthStage}{item.evolutionStage > 0 ? ` · 进化${item.evolutionStage}阶` : ''}
                      </Text>
                    </View>
                    {!item.isActive && (
                      <ChipButton
                        label="设为主宠"
                        disabled={carePending === `activate-${item.petId}`}
                        onPress={() => runCare(`activate-${item.petId}`, () => petApi.activatePet(item.petId), `已切换为 ${item.name}`)}
                      />
                    )}
                  </View>
                ))}
                {pet.petCount < pet.maxPets && (
                  <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
                    <ChipButton label="再领养一只" onPress={() => setNoPet(true)} />
                  </View>
                )}
              </View>
            )}
          </View>
        )}

        {panel === 'rankings' && (
          <View style={{ gap: Spacing.sm }}>
            <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
              {RANKING_TABS.map((tab) => (
                <TouchableOpacity
                  key={tab.key}
                  onPress={() => setRankingType(tab.key)}
                  style={{
                    paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
                    backgroundColor: rankingType === tab.key ? colors.primary : colors.bgBase,
                    borderWidth: 1, borderColor: rankingType === tab.key ? colors.primary : colors.border,
                  }}
                >
                  <Text style={{ color: rankingType === tab.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{tab.label}</Text>
                </TouchableOpacity>
              ))}
            </View>
            {rankings?.top20.map((item) => (
              <View key={String(item.petId)} style={{
                flexDirection: 'row', alignItems: 'center', gap: Spacing.sm,
                padding: Spacing.sm, borderRadius: BorderRadius.md,
                backgroundColor: item.isMe ? 'rgba(255,214,102,0.25)' : colors.bgBase,
              }}>
                <Text style={{ width: 36, fontWeight: '700', color: colors.text }}>
                  {item.rank <= 3 ? ['🥇', '🥈', '🥉'][item.rank - 1] : `#${item.rank}`}
                </Text>
                <Text style={{ flex: 1, color: colors.text, fontSize: FontSize.sm }}>
                  {SPECIES_EMOJI[item.species] || '🐾'} {item.name}
                  <Text style={{ color: colors.textTertiary, fontSize: 10 }}> @{item.ownerNickname}</Text>
                </Text>
                <Text style={{ color: colors.primary, fontWeight: '600' }}>
                  {rankingType === 'LEVEL' ? `Lv.${item.value}` : item.value}
                </Text>
              </View>
            ))}
            {rankings?.myRank != null && (
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                我的成绩：{rankings.myValue} · 全服第 {rankings.myRank} 名
              </Text>
            )}
          </View>
        )}

        {panel === 'reminders' && (
          <View style={{ gap: Spacing.sm }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>宠物想对你说（{reminderUnread} 条未读）</Text>
              <ChipButton label="全部已读" onPress={markAllRemindersRead} />
            </View>
            {reminders.length === 0 && (
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                还没有提醒～宠物会在打工完成、捞到漂流瓶、有人评论你时主动开口
              </Text>
            )}
            {reminders.map((item) => (
              <TouchableOpacity
                key={String(item.notificationId)}
                onPress={() => markReminderRead(item)}
                style={{
                  padding: Spacing.sm, borderRadius: BorderRadius.md, gap: 4,
                  backgroundColor: item.isRead ? colors.bgBase : 'rgba(255,214,102,0.18)',
                  borderWidth: 1, borderColor: colors.border,
                }}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs }}>
                  <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600', flex: 1 }}>{item.title}</Text>
                  <Text style={{
                    fontSize: 10, paddingHorizontal: 6, paddingVertical: 2, borderRadius: 999,
                    color: item.priority === 'P0' ? '#fff' : colors.textTertiary,
                    backgroundColor: item.priority === 'P0' ? '#ff6c6c' : item.priority === 'P1' ? colors.primary : colors.border,
                  }}>
                    {PRIORITY_LABEL[item.priority ?? 'P2']}
                  </Text>
                </View>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{item.content}</Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {panel === 'achievements' && (
          <View style={{ gap: Spacing.sm }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>🫧 宠物动态</Text>
            {reminders.length === 0 ? (
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有动态，去陪它玩一会吧～</Text>
            ) : (
              reminders.slice(0, 5).map((item) => (
                <View key={String(item.notificationId)} style={{
                  padding: Spacing.sm, borderRadius: BorderRadius.md, backgroundColor: colors.bgBase, gap: 2,
                }}>
                  <Text style={{ color: colors.text, fontSize: FontSize.xs, fontWeight: '600' }}>{item.title}</Text>
                  <Text style={{ color: colors.textSecondary, fontSize: 10 }}>{item.content}</Text>
                </View>
              ))
            )}
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>🏆 成就墙</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
            {achievements.map((item) => (
              <View
                key={String(item.achievementId)}
                style={{
                  width: '47%', alignItems: 'center', padding: Spacing.sm, borderRadius: BorderRadius.lg,
                  borderWidth: 1, borderColor: colors.border, opacity: item.achieved ? 1 : 0.45,
                }}
              >
                <Text style={{ fontSize: 30 }}>{item.icon}</Text>
                <Text style={{ color: colors.text, fontSize: FontSize.xs, fontWeight: '600' }}>{item.name}</Text>
                <Text style={{ color: colors.textTertiary, fontSize: 10, textAlign: 'center' }}>{item.description}</Text>
                <Text style={{ color: colors.primary, fontSize: 10, marginTop: 2 }}>
                  {item.achieved ? `已达成 +${item.expReward}` : `未达成 +${item.expReward}`}
                </Text>
              </View>
            ))}
            </View>
          </View>
        )}
      </View>

      {/* 档案弹窗：改名 30 天冷却 + 外观 + 主页公开 */}
      <Modal visible={profileOpen} transparent animationType="slide" onRequestClose={() => setProfileOpen(false)}>
        <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)' }}>
          <View style={{ backgroundColor: colors.bgBase, borderTopLeftRadius: BorderRadius.xl, borderTopRightRadius: BorderRadius.xl, padding: Spacing.lg, gap: Spacing.md }}>
            <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700' }}>宠物档案</Text>
            <TextInput
              value={profileName}
              onChangeText={setProfileName}
              maxLength={12}
              placeholder="宠物名（30 天可改一次）"
              placeholderTextColor={colors.textTertiary}
              style={{
                paddingHorizontal: Spacing.md, paddingVertical: 10, borderRadius: BorderRadius.md,
                borderWidth: 1, borderColor: colors.border, color: colors.text, backgroundColor: colors.bgContainer,
              }}
            />
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
              {ACCESSORIES.map((item) => (
                <TouchableOpacity
                  key={item.key}
                  onPress={() => setProfileAccessory(item.key)}
                  style={{
                    paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
                    borderWidth: 1, borderColor: profileAccessory === item.key ? colors.primary : colors.border,
                    backgroundColor: profileAccessory === item.key ? colors.primary : 'transparent',
                  }}
                >
                  <Text style={{ color: profileAccessory === item.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{item.label}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
              {PET_COLORS.map((color) => (
                <TouchableOpacity
                  key={color}
                  onPress={() => setProfileColor(color)}
                  style={{
                    paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
                    borderWidth: 2, borderColor: profileColor === color ? colors.primary : colors.border,
                  }}
                >
                  <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{color}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm }}>在个人主页展示宠物</Text>
              <Switch value={pet.isPublic} disabled={privacyBusy} onValueChange={togglePrivacy} />
            </View>
            <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
              <ChipButton label="取消" onPress={() => setProfileOpen(false)} />
              <ChipButton label="保存" primary onPress={saveProfile} />
            </View>
          </View>
        </View>
      </Modal>

      {/* 稀有/宠物/彩蛋瓶结果 */}
      <Modal visible={bottleResult != null} transparent animationType="fade" onRequestClose={() => setBottleResult(null)}>
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(0,0,0,0.5)', padding: Spacing.lg }}>
          <View style={{ width: '100%', backgroundColor: colors.bgBase, borderRadius: BorderRadius.xl, padding: Spacing.lg, gap: Spacing.sm }}>
            <Text style={{ fontSize: 44, textAlign: 'center' }}>🍾</Text>
            <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700', textAlign: 'center' }}>
              捞到了一只{RARITY_LABEL[bottleResult?.rarity ?? 'NORMAL']}！
            </Text>
            {bottleResult?.specialContent
              ? <Text style={{ color: colors.textSecondary, fontSize: FontSize.sm, textAlign: 'center' }}>{bottleResult.specialContent}</Text>
              : null}
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, textAlign: 'center' }}>
              宠物经验 +{bottleResult?.exp ?? 0} · 成功率 {Math.round((bottleResult?.successRate ?? 0) * 100)}%
            </Text>
            <ChipButton label="知道啦" primary onPress={() => setBottleResult(null)} />
          </View>
        </View>
      </Modal>

      {/* 分享卡片（文案服务端生成，复制到剪贴板后去发布页） */}
      <Modal visible={shareCard != null} transparent animationType="fade" onRequestClose={() => setShareCard(null)}>
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(0,0,0,0.5)', padding: Spacing.lg }}>
          <View style={{ width: '100%', backgroundColor: colors.bgBase, borderRadius: BorderRadius.xl, padding: Spacing.lg, gap: Spacing.sm }}>
            <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700' }}>{shareCard?.title}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.sm }}>{shareCard?.content}</Text>
            {shareCard?.highlight
              ? <Text style={{ color: colors.primary, fontSize: FontSize.sm, fontWeight: '600' }}>{shareCard.highlight}</Text>
              : null}
            <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
              <ChipButton label="关闭" onPress={() => setShareCard(null)} />
              <ChipButton label="复制文案" primary onPress={copyShare} />
            </View>
          </View>
        </View>
      </Modal>

      {/* 对战回合兜底浮层（未部署 Cocos 时也可完整看战斗过程） */}
      {battleOverlay && (
        <Modal visible transparent animationType="fade">
          <TouchableOpacity
            activeOpacity={1}
            onPress={() => setBattleOverlay(null)}
            style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(0,0,0,0.55)', padding: Spacing.lg }}
          >
            <View style={{ width: '100%', backgroundColor: colors.bgBase, borderRadius: BorderRadius.xl, padding: Spacing.lg, gap: Spacing.sm }}>
              <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700', textAlign: 'center' }}>
                ⚔️ 对战回放
              </Text>
              {activeRound
                ? (
                  <>
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, textAlign: 'center' }}>第 {activeRound.round} 回合</Text>
                    <Text style={{ color: colors.text, fontSize: FontSize.md, textAlign: 'center' }}>
                      {activeRound.actorName} {activeRound.action}
                    </Text>
                    <Text style={{ color: colors.primary, fontSize: FontSize.sm, textAlign: 'center' }}>
                      {activeRound.dodged
                        ? '被灵活地闪开了'
                        : `造成 ${activeRound.damage} 点伤害${activeRound.critical ? '（暴击！）' : ''}`}
                    </Text>
                    <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, textAlign: 'center' }}>
                      {activeRound.targetName} 剩余生命 {Math.max(0, activeRound.targetRemainingHp)}
                    </Text>
                  </>
                )
                : (
                  <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700', textAlign: 'center' }}>
                    {battleOverlay.won ? '🏆 我们赢啦！' : '💧 这次惜败，再来一局！'}
                  </Text>
                )}
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, textAlign: 'center' }}>点击任意位置跳过</Text>
            </View>
          </TouchableOpacity>
        </Modal>
      )}
    </ScrollView>
  )
}
