import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  AppState,
  ActivityIndicator,
  Platform,
  Alert,
  Image,
  Modal,
  ScrollView,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native'
import { WebView } from 'react-native-webview'
import { router, useLocalSearchParams, useFocusEffect } from 'expo-router'
import * as Clipboard from 'expo-clipboard'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme, PetCreamSemantic } from '@/constants/pet-cream'
import { CreamCard, CreamButton, CreamChip, CreamStatBar, CreamMasthead, CreamMenuGrid, CreamPanel, petCreamStyles } from '@/components/pet-cream'
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
  type PetCareerPanel,
  type PetDailyQuestPanel,
  type PetFriendPanel,
  type PetHome,
  type PetIntimacyInfo,
  type PetRelationPanel,
  type PetWallPage,
  type PetActionItem,
  type PetEquipPreview,
  type PetAnniversary,
  type PetPersona,
  type PetFriendFeedItem,
  type PetDiaryPage,
  type PetMemory,
  type PetSeasonRanking,
  type PetSeasonHistoryItem,
  type PetNotifyPref,
  type PetOnboardingProgress,
} from '@/api/pet'
import { growthApi } from '@/api/growth'
import type { CheckInStatus } from '@/types'
import { fileApi } from '@/api/file'
import * as ImagePicker from 'expo-image-picker'
import { useAuthStore } from '@/store/auth'

/**
 * 社区宠物主页（App 端，实施文档 §4/§34）。
 *
 * Cocos 宠物舞台通过 react-native-webview 嵌入（产物 URL 取 EXPO_PUBLIC_PET_GAME_URL，
 * 未配置/加载超时自动 Fail-Open 原生降级舞台）；宿主负责页面、导航与 mall-pet API，
 * 数值全部服务端结算，游戏只发意图、不做任何业务计算。
 */

const SPECIES_EMOJI: Record<string, string> = {
  STRAWBERRY: '🍓',
  ORANGE: '🍊',
  WATERMELON: '🍉',
  BLUEBERRY: '🫐',
  DRAGONFRUIT: '🐉',
}
const PERSONALITY_LABEL: Record<string, string> = {
  LIVELY: '活泼', GENTLE: '温柔', TSUNDERE: '傲娇', SIMPLE: '憨厚', COOL: '高冷', CHATTERBOX: '话痨',
}
const STATUS_LABEL: Record<string, string> = {
  IDLE: '悠闲中', WORKING: '打工中', STUDYING: '读书中', FISHING: '捞瓶中', RESTING: '休息中',
}
const GROWTH_STAGE_LABEL: Record<string, string> = { BABY: '幼年', YOUNG: '成长期', ADULT: '成年' }
const PET_COLORS = ['orange', 'white', 'black', 'gray', 'brown']
const ACCESSORIES: { key: string; label: string }[] = [
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
  | 'home' | 'care' | 'daily' | 'social' | 'work' | 'study' | 'bottle' | 'battle'
  | 'chat' | 'achievements' | 'rankings' | 'reminders' | 'companion'

/** 养成面板子页签（原文档 §89：商城/背包/技能/进化/活动 + §1.1 串门 + 多宠物 + 三期职业） */
type CareTab = 'shop' | 'inventory' | 'skills' | 'evolution' | 'events' | 'visit' | 'career' | 'pets'

const CARE_TABS: { key: CareTab; label: string }[] = [
  { key: 'shop', label: '🛒 商城' },
  { key: 'inventory', label: '🎒 背包' },
  { key: 'skills', label: '🌟 技能' },
  { key: 'evolution', label: '🌠 进化' },
  { key: 'events', label: '🎯 活动' },
  { key: 'visit', label: '🚪 串门' },
  { key: 'career', label: '💼 职业' },
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
  WISH_STARLIGHT_INSUFFICIENT: '宠物币不够啦，让宠物去打工赚点吧',
}

/** B06 动作可执行性的展示名（action 码 → 中文） */
const ACTION_LABEL: Record<string, string> = {
  FEED: '喂食', PLAY: '玩耍', CLEAN: '清洁', REST: '休息',
  WORK: '打工', STUDY: '读书', BOTTLE: '捞瓶', BATTLE: '对战',
}

const PANELS: { key: PanelKey; label: string; emoji: string }[] = [
  { key: 'home', label: '家园', emoji: '🏠' },
  { key: 'care', label: '养成', emoji: '🎒' },
  { key: 'daily', label: '任务', emoji: '✅' },
  { key: 'social', label: '社交', emoji: '🤝' },
  { key: 'work', label: '打工', emoji: '💼' },
  { key: 'study', label: '读书', emoji: '📚' },
  { key: 'bottle', label: '捞瓶', emoji: '🍾' },
  { key: 'battle', label: '对战', emoji: '⚔️' },
  { key: 'chat', label: '聊天', emoji: '💬' },
  { key: 'achievements', label: '成就', emoji: '🏆' },
  { key: 'rankings', label: '榜单', emoji: '🥇' },
  { key: 'reminders', label: '提醒', emoji: '🔔' },
  { key: 'companion', label: '陪伴', emoji: '🫶' },
]

const PANEL_TITLE = Object.fromEntries(PANELS.map((item) => [item.key, item.label])) as Record<PanelKey, string>
const PANEL_ITEM = Object.fromEntries(PANELS.map((item) => [item.key, item])) as unknown as Record<PanelKey, { emoji: string; label: string }>

/** 分组宫格（对齐 Web/Taro：La Maison / Croissance / Les Amis / Archives） */
const MENU_GROUPS: Array<{ label: string; hint: string; keys: PanelKey[] }> = [
  { label: 'La Maison', hint: '小家与陪伴', keys: ['home', 'companion'] },
  { label: 'Croissance', hint: '养成', keys: ['daily', 'work', 'study', 'bottle', 'battle', 'care'] },
  { label: 'Les Amis', hint: '往来', keys: ['social', 'chat'] },
  { label: 'Archives', hint: '记录', keys: ['achievements', 'rankings', 'reminders'] },
]

const RANKING_TABS: { key: PetRankingType; label: string }[] = [
  { key: 'LEVEL', label: '等级榜' },
  { key: 'BATTLE_WIN', label: '胜场榜' },
  { key: 'BOTTLE', label: '捞瓶榜' },
]

const SHARE_TYPES: { key: Parameters<typeof petApi.getShareCard>[0]; label: string }[] = [
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
  | { source: 'pet-host'; type: 'battleRounds'; rounds: Record<string, unknown>[]; won: boolean }
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
    gender: pet.gender,
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


function ChipButton({ label, onPress, disabled, primary }: { label: string; onPress: () => void; disabled?: boolean; primary?: boolean }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
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
  const colors = { ...useTheme(), ...PetCreamTheme }
  const params = useLocalSearchParams<{ intent?: string }>()
  const gameUrl = process.env.EXPO_PUBLIC_PET_GAME_URL
  const webRef = useRef<WebView>(null)
  const isLoggedIn = useAuthStore((state) => state.isLoggedIn)

  const [loading, setLoading] = useState(true)
  const [pet, setPet] = useState<PetInfo | null>(null)
  const [noPet, setNoPet] = useState(false)
  const [gameReady, setGameReady] = useState(false)
  const [gameFailed, setGameFailed] = useState(!gameUrl)
  const stageFrameRef = useRef<any>(null)
  const [panel, setPanel] = useState<PanelKey | null>(null)
  const [adoptSpecies, setAdoptSpecies] = useState('STRAWBERRY')
  const [adoptGender, setAdoptGender] = useState('MALE')
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
  // F2 赛季排行（懒加载：切到"赛季"时拉取一次）
  const [showSeason, setShowSeason] = useState(false)
  const [season, setSeason] = useState<PetSeasonRanking | null>(null)
  const [seasonHistory, setSeasonHistory] = useState<PetSeasonHistoryItem[]>([])
  const [reminders, setReminders] = useState<PetReminder[]>([])
  const [reminderUnread, setReminderUnread] = useState(0)
  const [intimacy, setIntimacy] = useState<PetIntimacyInfo | null>(null)
  // B06 动作可执行性（辅助展示：失败清空，不打断主流程）
  const [actions, setActions] = useState<PetActionItem[]>([])
  // 喂食食物选择器（长按喂食弹出；对齐 Taro 端 openFoodPicker）
  const [foodPickerOpen, setFoodPickerOpen] = useState(false)
  const [foodOptions, setFoodOptions] = useState<PetInventoryItem[] | null>(null)
  const [foodBusy, setFoodBusy] = useState<string | null>(null)

  const [equipPreview, setEquipPreview] = useState<PetEquipPreview | null>(null)
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
  // 主人称呼（宠物怎么叫主人）草稿
  const [profileOwnerTitle, setProfileOwnerTitle] = useState('')
  const [privacyBusy, setPrivacyBusy] = useState(false)

  // 分享卡片（文案服务端生成）
  const [shareCard, setShareCard] = useState<PetShareCard | null>(null)

  // 对战回合兜底：未部署 Cocos 时宿主自行逐回合播放（服务端回合流水）
  const [battleOverlay, setBattleOverlay] = useState<{ rounds: BattleRound[]; won: boolean } | null>(null)
  const [roundIndex, setRoundIndex] = useState(0)

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
  }, [postToGame])

  // R23/T36：加载代际——迟到的旧响应不覆盖新状态
  const refreshGenerationRef = useRef(0)
  // PET-03/T38：陪伴会话镜像——AppState 与页面聚焦状态放组件顶层（原实现在 useEffect 内
  // 调 useRef 违反 Hook 规则，登录后触发 invalid hook call），值变更不触发渲染，仅心跳与清理回调读取
  const appStateRef = useRef(AppState.currentState)
  const petScreenFocusedRef = useRef(false)
  const companionActiveRef = useRef(false)
  const refresh = useCallback(async () => {
    const generation = ++refreshGenerationRef.current
    try {
      const { data: res } = await petApi.getMyPet()
      if (generation !== refreshGenerationRef.current) {
        return
      }
      if (res.success && res.data) {
        setPet(res.data)
        setNoPet(false)
        if (gameReady) syncStage(res.data)
      }
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
    refresh()
  }, [isLoggedIn, refresh])

  // B06 动作可执行性：随主宠刷新（喂食/玩耍等当前能否执行与原因）
  useEffect(() => {
    if (!pet) return
    void (async () => {
      try {
        const { data: res } = await petApi.getActions(pet.petId)
        if (res.success) setActions(res.data)
      } catch {
        setActions([])
      }
    })()
  }, [pet?.petId])

  // F2 赛季排行：切到"赛季"且未加载时拉取（无进行中赛季 season=null）
  useEffect(() => {
    if (panel !== 'rankings' || !showSeason || season) return
    void (async () => {
      try {
        const [{ data: sr }, { data: sh }] = await Promise.all([
          petApi.getSeasonRanking(),
          petApi.getSeasonHistory(),
        ])
        if (sr.success && sr.data) setSeason(sr.data)
        if (sh.success) setSeasonHistory(sh.data ?? [])
      } catch {
        // 展示型数据：忽略
      }
    })()
  }, [panel, season, showSeason])

  /** B12 装备替换预览：同码再点收起（只读不写入） */
  const toggleEquipPreview = useCallback(async (itemCode: string) => {
    if (equipPreview?.itemCode === itemCode) {
      setEquipPreview(null)
      return
    }
    try {
      const { data: res } = await petApi.previewEquip(itemCode)
      if (res.success) setEquipPreview(res.data)
    } catch {
      Alert.alert('暂时不能预览', '请稍后再试')
    }
  }, [equipPreview?.itemCode])

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
        if (res.success && res.data) {
          setIntimacy(res.data)
        }
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
          const { data: res } = await petApi.companionHeartbeat(60)
          if (res.success && res.data) {
            companionActiveRef.current = true
            // FE-02：心跳返回会话视图，亲密度面板另查 overview，禁止互相覆盖
            const { data: overview } = await petApi.getIntimacy()
            if (overview.success && overview.data) {
              setIntimacy(overview.data)
            }
          }
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
    if (pet && panel) loadPanelData(panel)
  }, [panel, pet, loadPanelData])

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
      } else {
        // 业务失败（精力不足/次数用尽等）：气泡可见反馈
        showBubbleTimed(res.error?.message ?? '现在不行哦')
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

  const openFoodPicker = useCallback(async () => {
    if (!pet) return
    setFoodPickerOpen(true)
    setFoodOptions(null)
    try {
      const { data: res } = await petApi.listInventory()
      if (res.success && res.data) {
        const foods = res.data.filter((it) => it.itemType === 'FOOD' && (it.quantity ?? 0) > 0)
        setFoodOptions(foods)
        if (foods.length === 0) {
          setTimeout(() => { setStageBubble('背包里没有食物，去商城买一点吧'); setFoodPickerOpen(false) }, 400)
        }
      }
    } catch { /* 拦截器已提示 */ }
  }, [pet])

  const feedFood = useCallback(async (code: string) => {
    setFoodBusy(code)
    try {
      const { data: res } = await petApi.feedItem(code)
      if (res.success && res.data) {
        setPet(res.data)
        setFoodPickerOpen(false)
        if (gameReady) {
          syncStage(res.data)
          postToGame({ source: 'pet-host', type: 'actionResult', action: 'feed', ok: true })
        }
      }
    } catch { /* 拦截器已提示 */ } finally {
      setFoodBusy(null)
    }
  }, [gameReady, postToGame, syncStage])

  const openProfile = useCallback(() => {
    if (!pet) return
    setProfileName(pet.name)
    setProfileOwnerTitle(pet.ownerTitle ?? '主人')
    const appearance = parseResult<{ color?: string; accessory?: string }>(pet.appearance)
    setProfileColor(appearance?.color ?? PET_COLORS[0])
    setProfileAccessory(appearance?.accessory ?? 'none')
    setProfileOpen(true)
  }, [pet])

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
    const timer = setTimeout(() => { refresh(); if (panel) loadPanelData(panel) }, 1500)
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

  const adopt = async () => {
    if (!adoptName.trim()) return
    try {
      const { data: res } = await petApi.createPet({
        name: adoptName.trim(),
        species: adoptSpecies as PetInfo['species'],
        gender: adoptGender as PetInfo['gender'],
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

  const saveProfile = async () => {
    if (!pet) return
    const wantRename = profileName.trim() && profileName.trim() !== pet.name
    const wantTitle = profileOwnerTitle.trim() && profileOwnerTitle.trim() !== (pet.ownerTitle ?? '主人')
    try {
      const { data: appearanceRes } = await petApi.updateAppearance({ color: profileColor, accessory: profileAccessory })
      if (appearanceRes.success && appearanceRes.data) setPet(appearanceRes.data)
      if (wantRename) {
        const { data: renameRes } = await petApi.renamePet({ name: profileName.trim() })
        if (renameRes.success && renameRes.data) setPet(renameRes.data)
      }
      if (wantTitle) {
        const { data: titleRes } = await petApi.setOwnerTitle(profileOwnerTitle.trim())
        if (titleRes.success && titleRes.data) setPet(titleRes.data)
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
      setGameFailed(false)
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
      await notificationApi.markRead(item.notificationId)
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
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs, marginTop: Spacing.md, marginBottom: 4 }}>性别</Text>
        <View style={{ flexDirection: 'row', gap: Spacing.xs, justifyContent: 'center' }}>
          {([['MALE', '♂ 男'], ['FEMALE', '♀ 女']] as const).map(([key, label]) => (
            <TouchableOpacity
              key={key}
              onPress={() => setAdoptGender(key)}
              style={{
                paddingVertical: 6, paddingHorizontal: 18, borderRadius: 999,
                borderWidth: 2, borderColor: adoptGender === key ? colors.primary : colors.border,
                backgroundColor: adoptGender === key ? colors.primary : 'transparent',
              }}
            >
              <Text style={{ color: adoptGender === key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs, fontWeight: '600' }}>{label}</Text>
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
            onPress={async () => {
              try {
                if (pet.claimableActivityType === 'WORK') await petApi.claimWork()
                else if (pet.claimableActivityType === 'STUDY') await petApi.claimStudy()
                else await openBottleResult()
                refresh()
                if (panel) loadPanelData(panel)
              } catch { /* 已领取 409 */ }
            }}
          />
        </View>
      )}

      {/* 互动按钮（Cocos 意图与原生按钮共用同一 API） */}
      <View style={{ flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.md, flexWrap: 'wrap' }}>
        <CreamButton onPress={() => runInteraction('feed')} onLongPress={() => void openFoodPicker()}>🍖 喂食</CreamButton>
        <ChipButton label="🎾 玩耍" primary onPress={() => runInteraction('play')} />
        <ChipButton label="🫧 清洁" primary onPress={() => runInteraction('clean')} />
        <ChipButton label="💤 休息" primary onPress={() => runInteraction('rest')} />
        <ChipButton label="🎀 档案" onPress={openProfile} />
      </View>

      {/* 面板切换 */}
      {/* 分组功能宫格 */}
      <CreamMenuGrid
        groups={MENU_GROUPS}
        items={PANEL_ITEM}
        active={panel}
        badgeOf={(key) => (key === 'reminders' ? reminderUnread : 0)}
        onSelect={(key) => setPanel(key as PanelKey)}
      />

      {/* 面板层（全屏奶油） */}
      <CreamPanel title={panel ? PANEL_TITLE[panel] : ''} visible={panel != null} onClose={() => setPanel(null)}>
      <View style={{ gap: Spacing.sm }}>
        {panel === 'daily' && <DailyQuestPanel onRefresh={refresh} />}
        {panel === 'social' && <SocialPanel pet={pet} onRefresh={refresh} />}
        {panel === 'home' && (
          <View style={{ gap: 6 }}>
            <HomePanel onRefresh={refresh} />
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>养成小贴士</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🍖 饱食和清洁随时间下降，记得回来照顾它</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>💼 打工赚宠物币，📚 读书涨智力</Text>
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
              <View key={String(job.configId)} style={petCreamStyles.questCard}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{job.name}</Text>
                    <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                      {`⏱ ${Math.round(job.durationSeconds / 60)} 分钟 · ⚡-${job.energyCost}`}
                    </Text>
                    <View style={petCreamStyles.rewardRow}>
                      <Text style={petCreamStyles.rewardChip}>{`经验 +${job.expReward}`}</Text>
                      <Text style={petCreamStyles.rewardChip}>{`宠物币 +${job.currencyReward}`}</Text>
                    </View>
                  </View>
                  {job.eligible ? (
                    <CreamButton onPress={async () => {
                      try {
                        const { data: res } = await petApi.startWork(job.configId)
                        if (res.success) { refresh(); if (panel) loadPanelData(panel) }
                      } catch { /* 拦截器已提示 */ }
                    }}>接单</CreamButton>
                  ) : (
                    <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, flexShrink: 0 }}>{`Lv.${job.requiredLevel} 解锁`}</Text>
                  )}
                </View>
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
              <View key={String(study.configId)} style={petCreamStyles.questCard}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{study.name}</Text>
                    <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                      {`⏱ ${Math.round(study.durationSeconds / 60)} 分钟`}
                    </Text>
                    <View style={petCreamStyles.rewardRow}>
                      <Text style={petCreamStyles.rewardChip}>{`经验 +${study.expReward}`}</Text>
                      <Text style={petCreamStyles.rewardChip}>{`智力 +${study.intelligenceReward}`}</Text>
                    </View>
                  </View>
                  {study.eligible ? (
                    <CreamButton onPress={async () => {
                      try {
                        const { data: res } = await petApi.startStudy(study.configId)
                        if (res.success) { refresh(); loadPanelData('study') }
                      } catch { /* 拦截器已提示 */ }
                    }}>上课</CreamButton>
                  ) : (
                    <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs, flexShrink: 0 }}>{`Lv.${study.requiredLevel} 解锁`}</Text>
                  )}
                </View>
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
          <View style={petCreamStyles.questCard}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm }}>🌊 {bottle.unlockedArea} · 估算成功率 {Math.round(bottle.estimatedSuccessRate * 100)}%</Text>
            {bottle.fishing
              ? <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>🐾 捞瓶中…剩余 {formatClock(bottleRemaining)}</Text>
              : (
                <CreamButton
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
                >
                  {bottle.canClaim ? '查看捞瓶结果' : bottle.cooldownRemainingSeconds > 0 ? `冷却中 ${Math.ceil(bottle.cooldownRemainingSeconds / 60)} 分钟` : '让宠物去捞漂流瓶'}
                </CreamButton>
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
              <View key={String(battle.battleId)} style={[petCreamStyles.questCard, { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }]}>
                <Text style={{ color: colors.text, fontSize: FontSize.sm, flex: 1 }}>
                  ⚔️ 收到 {battle.attackerPetName ?? `宠物 #${battle.attackerPetId}`} 的挑战
                </Text>
                <View style={petCreamStyles.rowActions}>
                  <CreamButton onPress={() => battleRespond(battle, true)}>应战</CreamButton>
                  <CreamButton variant="ghost" onPress={() => battleRespond(battle, false)}>婉拒</CreamButton>
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
                  style={petCreamStyles.tile}
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
                style={[petCreamStyles.questCard, { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10 }]}
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
                  style={item.role === 'USER' ? petCreamStyles.bubbleMine : petCreamStyles.bubblePet}
                >
                  <Text style={item.role === 'USER' ? petCreamStyles.bubbleMineText : petCreamStyles.bubblePetText}>{item.content}</Text>
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
              <CreamButton onPress={sendChat}>发送</CreamButton>
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

            <TouchableOpacity
              onPress={() => router.push('/pet-play')}
              style={{
                alignItems: 'center', paddingVertical: 10, borderRadius: BorderRadius.lg,
                borderWidth: 1, borderColor: PetCreamSemantic.brassSoftBorder, backgroundColor: PetCreamSemantic.brassSoftBg,
              }}
            >
              <Text style={{ color: PetCreamTheme.primary, fontSize: FontSize.sm, fontWeight: '600' }}>🪙 宠物币钱包</Text>
            </TouchableOpacity>

            <TouchableOpacity
              onPress={() => router.push('/pet-play')}
              style={{
                alignItems: 'center', paddingVertical: 10, borderRadius: BorderRadius.lg,
                borderWidth: 1, borderColor: colors.primary, backgroundColor: 'rgba(0, 212, 255, 0.08)',
              }}
            >
              <Text style={{ color: colors.primary, fontSize: FontSize.sm, fontWeight: '600' }}>🎮 玩法中心</Text>
            </TouchableOpacity>

            {careTab === 'shop' && (
              <View style={{ gap: Spacing.sm }}>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                  🪙 宠物币余额：{shopBalance === null ? '暂不可用' : shopBalance}
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
                          <>
                            <ChipButton label="卸下" onPress={() => runCare(`unequip-${item.slot}`, () => petApi.unequipItem(item.slot ?? ''), '已卸下')} />
                            <ChipButton label={equipPreview?.itemCode === item.code ? '收起预览' : '预览'}
                              onPress={() => void toggleEquipPreview(item.code)} />
                          </>
                        ) : (
                          <>
                            <ChipButton label="穿戴" primary onPress={() => runCare(`equip-${item.code}`, () => petApi.equipItem(item.code), '已穿戴')} />
                            <ChipButton label={equipPreview?.itemCode === item.code ? '收起预览' : '预览'}
                              onPress={() => void toggleEquipPreview(item.code)} />
                          </>
                        )
                      )}
                      {item.itemType === 'SKIN' && (
                        item.equipped ? (
                          <ChipButton label="卸下" onPress={() => runCare('remove-skin', () => petApi.removeSkin(), '已换回原生外观')} />
                        ) : (
                          <ChipButton label="穿戴" primary onPress={() => runCare(`skin-${item.code}`, () => petApi.wearSkin(item.code), '已穿上新皮肤')} />
                        )
                      )}
                      {item.itemType === 'FOOD' && (
                        <ChipButton label="喂食" primary onPress={() => runCare(`feed-${item.code}`, () => petApi.feedItem(item.code), '吃掉了，状态好多了')} />
                      )}
                    </View>
                    {item.itemType === 'EQUIPMENT' && equipPreview?.itemCode === item.code && (
                      <Text style={{ color: colors.textSecondary, fontSize: 10 }}>
                        替换后 HP {equipPreview.after.maxHp}（{equipPreview.delta.maxHp >= 0 ? '+' : ''}{equipPreview.delta.maxHp}）
                        · 力 {equipPreview.after.strength}（{equipPreview.delta.strength >= 0 ? '+' : ''}{equipPreview.delta.strength}）
                        · 智 {equipPreview.after.intelligence}（{equipPreview.delta.intelligence >= 0 ? '+' : ''}{equipPreview.delta.intelligence}）
                        · 敏 {equipPreview.after.agility}（{equipPreview.delta.agility >= 0 ? '+' : ''}{equipPreview.delta.agility}）
                        · 魅 {equipPreview.after.charm}（{equipPreview.delta.charm >= 0 ? '+' : ''}{equipPreview.delta.charm}）
                      </Text>
                    )}
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

            {careTab === 'career' && <CareerPanel onRefresh={refresh} />}
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
              <TouchableOpacity
                onPress={() => setShowSeason((prev) => !prev)}
                style={{
                  paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
                  backgroundColor: showSeason ? colors.primary : colors.bgBase,
                  borderWidth: 1, borderColor: showSeason ? colors.primary : colors.border,
                }}
              >
                <Text style={{ color: showSeason ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>赛季</Text>
              </TouchableOpacity>
            </View>
            {showSeason ? (
              !season ? <ActivityIndicator color={colors.primary} /> : (
                <View style={{ gap: Spacing.sm }}>
                  {season.season === null ? (
                    <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>
                      当前没有进行中的赛季，开赛后这里会亮起来
                    </Text>
                  ) : (
                    <>
                      <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                        {`${season.season.name} · ${season.season.startsAt?.slice(5, 10)} ~ ${season.season.endsAt?.slice(5, 10)} · 我的等级 ${season.myLevel ?? '—'} · 名次 ${season.myRank ?? '未上榜'}`}
                      </Text>
                      {season.top50.map((item) => (
                        <View key={String(item.petId)} style={{
                          flexDirection: 'row', alignItems: 'center', gap: Spacing.sm,
                          padding: Spacing.sm, borderRadius: BorderRadius.md,
                          backgroundColor: item.isMe ? PetCreamSemantic.meHighlight : colors.bgBase,
                        }}>
                          <Text style={{ width: 36, fontWeight: '700', color: colors.text }}>
                            {item.rank <= 3 ? ['🥇', '🥈', '🥉'][item.rank - 1] : `#${item.rank}`}
                          </Text>
                          <Text style={{ flex: 1, color: colors.text, fontSize: FontSize.sm }}>
                            {SPECIES_EMOJI[item.species] || '🐾'} {item.name}
                            <Text style={{ color: colors.textTertiary, fontSize: 10 }}> @{item.ownerNickname}</Text>
                          </Text>
                          <Text style={{ color: colors.primary, fontWeight: '600' }}>Lv.{item.level}</Text>
                        </View>
                      ))}
                      {seasonHistory.length > 0 && (
                        <View style={{ gap: Spacing.xs }}>
                          <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>历届我的名次</Text>
                          {seasonHistory.map((item) => (
                            <Text key={String(item.seasonId)} style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                              {`${item.seasonName} · 第 ${item.rankNo} 名 · Lv.${item.level}${item.endedAt ? ` · 结算 ${item.endedAt.slice(0, 10)}` : ''}`}
                            </Text>
                          ))}
                        </View>
                      )}
                    </>
                  )}
                </View>
              )
            ) : (
              <>
                {rankings?.top20.map((item) => (
                  <View key={String(item.petId)} style={[petCreamStyles.questCard, {
                    flexDirection: 'row', alignItems: 'center', gap: Spacing.sm,
                    paddingVertical: 10,
                    backgroundColor: item.isMe ? PetCreamSemantic.meHighlight : '#FFFDF8',
                  }]}>
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
              </>
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
                  backgroundColor: item.isRead ? colors.bgBase : PetCreamSemantic.unreadHighlight,
                  borderWidth: 1, borderColor: colors.border,
                }}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs }}>
                  <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600', flex: 1 }}>{item.title}</Text>
                  <Text style={{
                    fontSize: 10, paddingHorizontal: 6, paddingVertical: 2, borderRadius: 999,
                    color: item.priority === 'P0' ? '#fff' : colors.textTertiary,
                    backgroundColor: item.priority === 'P0' ? PetCreamSemantic.danger : item.priority === 'P1' ? colors.primary : colors.border,
                  }}>
                    {PRIORITY_LABEL[item.priority ?? 'P2']}
                  </Text>
                </View>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{item.content}</Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {panel === 'companion' && pet && (
          <CompanionPanel petId={pet.petId} onRefresh={refresh} />
        )}

        {panel === 'achievements' && (
          <View style={{ gap: Spacing.sm }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>🫧 宠物动态</Text>
            {reminders.length === 0 ? (
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有动态，去陪它玩一会吧～</Text>
            ) : (
              reminders.slice(0, 5).map((item) => (
                <View key={String(item.notificationId)} style={petCreamStyles.questCard}>
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
                style={[petCreamStyles.questCard, {
                  width: '47%', alignItems: 'center', opacity: item.achieved ? 1 : 0.45,
                }]}
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
      </CreamPanel>

      {/* 喂食食物选择器（长按喂食弹出；背包 FOOD 项） */}
      <Modal visible={foodPickerOpen} transparent animationType="slide" onRequestClose={() => setFoodPickerOpen(false)}>
        <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)' }}>
          <View style={{ backgroundColor: colors.bgBase, borderTopLeftRadius: BorderRadius.xl, borderTopRightRadius: BorderRadius.xl, padding: Spacing.lg, gap: Spacing.sm, maxHeight: '60%' }}>
            <Text style={{ color: colors.text, fontSize: FontSize.lg, fontWeight: '700' }}>🍖 选一份食物</Text>
            <ScrollView style={{ maxHeight: 360 }} showsVerticalScrollIndicator={false}>
              {foodOptions === null ? (
                <ActivityIndicator color={colors.primary} />
              ) : (
                foodOptions.map((item) => (
                  <TouchableOpacity
                    key={item.code}
                    disabled={foodBusy === item.code}
                    onPress={() => void feedFood(item.code)}
                    style={{
                      flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm,
                      padding: Spacing.sm, marginBottom: Spacing.xs, borderRadius: BorderRadius.md,
                      borderWidth: 1, borderColor: 'rgba(200, 155, 90, 0.35)', backgroundColor: '#FFFDF8',
                      opacity: foodBusy === item.code ? 0.5 : 1,
                    }}
                  >
                    <Text style={{ color: colors.text, fontSize: FontSize.sm, flex: 1 }}>
                      {item.icon} {item.name} <Text style={{ color: colors.textTertiary, fontSize: 10 }}>×{item.quantity ?? 0}</Text>
                    </Text>
                    <Text style={{ color: PetCreamTheme.primary, fontSize: FontSize.sm, fontWeight: '600' }}>
                      {foodBusy === item.code ? '喂食中…' : '喂食'}
                    </Text>
                  </TouchableOpacity>
                ))
              )}
            </ScrollView>
            <ChipButton label="关闭" onPress={() => setFoodPickerOpen(false)} />
          </View>
        </View>
      </Modal>

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
            <TextInput
              value={profileOwnerTitle}
              onChangeText={setProfileOwnerTitle}
              maxLength={12}
              placeholder="宠物怎么叫你（默认「主人」）"
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

// ==================== 三期面板：职业 / 每日任务 / 社交 / 家园（服务端权威，前端只发意图） ====================

/** 职业面板：入职 / 职业工作 / 晋升 / 工作历史（挂在「养成 → 职业」页签） */
function CareerPanel({ onRefresh }: { onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [panel, setPanel] = useState<PetCareerPanel | null>(null)
  const [pending, setPending] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getCareer()
      if (res.success && res.data) {
        setPanel(res.data)
      }
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const run = async (
    key: string,
    action: () => Promise<{ data: { success: boolean } }>,
    text: string | ((data: unknown) => string),
  ) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        // R13：文案可为函数（按逐项结果汇总），静态文案行为不变
        Alert.alert('成功', typeof text === 'function' ? text(res) : text)
        await load()
        onRefresh()
      }
    } catch (error) {
      Alert.alert('暂时不能这么做', CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试')
    } finally {
      setPending(null)
    }
  }

  if (!panel) {
    return <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>职业信息加载中…</Text>
  }
  const activity = panel.activeActivity

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={petCreamStyles.questCard}>
      <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
        {panel.careerCode
          ? `当前职业：${panel.icon ?? ''} ${panel.careerName}（${panel.careerLine} · ${panel.tier} 阶）· 已工作 ${panel.workCount} 次`
          : '还没有工作，挑一份喜欢的职业入职吧'}
        {panel.canPromote && panel.promoteToName ? ` · 可晋升「${panel.promoteToName}」` : ''}
        {panel.promoteLockReason && panel.promoteToName ? ` · 晋升条件：${panel.promoteLockReason}` : ''}
      </Text>
      {panel.careerCode && (
        <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
          {activity ? (
            <ChipButton
              label={activity.canClaim ? '领取工作奖励' : `工作中 ${Math.max(0, Math.ceil(activity.remainingSeconds / 60))} 分钟`}
              primary
              disabled={!activity.canClaim || pending === 'claim'}
              onPress={() => run('claim', () => petApi.claimCareerWork(), '工钱到手啦！')}
            />
          ) : (
            <ChipButton
              label="去上班"
              primary
              disabled={pending === 'start'}
              onPress={() => run('start', () => petApi.startCareerWork(), '开始工作啦')}
            />
          )}
          <ChipButton
            label="晋升"
            disabled={!panel.canPromote || pending === 'promote'}
            onPress={() => run('promote', () => petApi.promoteCareer(), '晋升成功！')}
          />
        </View>
      )}
      </View>
      {panel.careers.map((career) => (
        <View
          key={career.code}
          style={petCreamStyles.questCard}
        >
          <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
            {career.icon} {career.name} · {career.careerLine} {career.tier} 阶
          </Text>
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            {career.description}
          </Text>
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            {`Lv.${career.requiredLevel} · ${Math.round(career.durationSeconds / 60)} 分钟 · 精力 ${career.energyCost}${career.workCount > 0 ? ` · 已工作 ${career.workCount} 次` : ''}`}
          </Text>
          <View style={petCreamStyles.rewardRow}>
            <Text style={petCreamStyles.rewardChip}>{`经验 +${career.expReward}`}</Text>
            <Text style={petCreamStyles.rewardChip}>{`宠物币 +${career.currencyReward}`}</Text>
          </View>
          <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
            {career.current ? (
              <Text style={{ color: colors.primary, fontSize: FontSize.xs }}>在职</Text>
            ) : (
              <ChipButton
                label={career.eligible ? '入职' : career.lockReason ?? '未解锁'}
                primary
                disabled={!career.eligible || pending === `apply-${career.code}`}
                onPress={() => run(`apply-${career.code}`, () => petApi.applyCareer(career.code), '入职成功！')}
              />
            )}
          </View>
        </View>
      ))}
      {panel.history.length > 0 && (
        <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
          工作经历：{panel.history.map((item) => `${item.name}（${item.workCount} 次）`).join(' · ')}
        </Text>
      )}
    </View>
  )
}

/** 每日任务面板：进度 + 领奖 + 全清宝箱 */
function DailyQuestPanel({ onRefresh }: { onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [panel, setPanel] = useState<PetDailyQuestPanel | null>(null)
  const [pending, setPending] = useState<string | null>(null)
  const [checkin, setCheckin] = useState<CheckInStatus | null>(null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getDailyQuests()
      if (res.success && res.data) {
        setPanel(res.data)
      }
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    growthApi.getCheckInStatus().then(({ data: res }) => {
      if (res.success && res.data) setCheckin(res.data)
    }).catch(() => undefined)
  }, [])

  const doCheckin = useCallback(async () => {
    setPending('checkin')
    try {
      const { data: res } = await growthApi.checkIn()
      if (res.success && res.data) {
        setCheckin(res.data)
        Alert.alert('成功', `签到成功 · 连续 ${res.data.continuousDays} 天`)
        await load()
      } else {
        Alert.alert('提示', res.error?.message ?? '今日已签到')
      }
    } catch (error) {
      Alert.alert('暂时不能这么做', CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '签到未成功')
    } finally {
      setPending(null)
    }
  }, [load])


  const run = async (
    key: string,
    action: () => Promise<{ data: { success: boolean } }>,
    text: string | ((data: unknown) => string),
  ) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        // R13：文案可为函数（按逐项结果汇总），静态文案行为不变
        Alert.alert('成功', typeof text === 'function' ? text(res) : text)
        await load()
        onRefresh()
      }
    } catch (error) {
      Alert.alert('暂时不能这么做', CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试')
    } finally {
      setPending(null)
    }
  }

  if (!panel) {
    return <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>任务加载中…</Text>
  }

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={petCreamStyles.questCard}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: Spacing.sm }}>
          <View style={{ flex: 1, minWidth: 0 }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>🎂 每日签到</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
              {checkin
                ? checkin.isCheckedIn
                  ? `今日已签到 · 连续 ${checkin.continuousDays} 天 · 经验 +${checkin.todayExp}`
                  : '今天还没签到，连续签到经验更多'
                : '签到状态加载中…'}
            </Text>
          </View>
          <CreamButton
            variant={checkin?.isCheckedIn ? 'ghost' : 'primary'}
            disabled={!!checkin?.isCheckedIn || pending === 'checkin'}
            onPress={() => void doCheckin()}
          >
            {checkin?.isCheckedIn ? '已签到' : '签到'}
          </CreamButton>
        </View>
      </View>
      <View style={petCreamStyles.summaryRow}>
        <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs, flex: 1 }}>
          {`今日进度 ${panel.claimedCount}/${panel.totalCount} · 全清宝箱 经验+${panel.chestExp} 宠物币+${panel.chestCurrency}`}
        </Text>
        {(panel.quests.some((quest) => quest.claimable) || panel.chestClaimable) && (
          <CreamButton disabled={pending === 'claim-all'} onPress={() => run('claim-all', () =>
            panel.setId ? petApi.claimAllDailyQuestsInSet(panel.setId) : petApi.claimAllDailyQuests(), (data) => {
            // R13：按逐项结果汇总提示，失败项不掩盖
            const results = (data as { results?: Array<{ status: string }>; chest?: { status: string } }).results ?? []
            const claimed = results.filter((item) => item.status === 'CLAIMED').length
            const failed = results.filter((item) => item.status === 'FAILED').length
            const chestClaimed = (data as { chest?: { status: string } }).chest?.status === 'CLAIMED'
            const text = [`${claimed} 项任务成功`]
            if (chestClaimed) text.push('宝箱已开启')
            if (failed > 0) text.push(`${failed} 项失败，可稍后重试`)
            if (claimed === 0 && failed === 0 && !chestClaimed) return '奖励已经领过了'
            return text.join('，')
          })}>
            一键领取
          </CreamButton>
        )}
      </View>
      {panel.quests.map((quest) => {
        const percent = quest.targetValue > 0 ? Math.min(100, Math.round((quest.progress / quest.targetValue) * 100)) : 0
        return (
          <View key={quest.code} style={petCreamStyles.questCard}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
              {quest.icon} {quest.name}
            </Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>{quest.description}</Text>
            <View style={petCreamStyles.questProgressRow}>
              <View style={petCreamStyles.questTrack}>
                <View style={[petCreamStyles.questFill, { width: `${percent}%` }]} />
              </View>
              <Text style={petCreamStyles.questProgressText}>{`${Math.min(quest.progress, quest.targetValue)}/${quest.targetValue}`}</Text>
            </View>
            <View style={petCreamStyles.rewardRow}>
              <Text style={petCreamStyles.rewardChip}>{`经验 +${quest.expReward}`}</Text>
              <Text style={petCreamStyles.rewardChip}>{`宠物币 +${quest.currencyReward}`}</Text>
            </View>
            <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
              {quest.status === 'CLAIMED' ? (
                <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>已领取</Text>
              ) : (
                <CreamButton
                  variant={quest.claimable ? 'primary' : 'ghost'}
                  disabled={!quest.claimable || pending === `quest-${quest.code}`}
                  onPress={() => run(`quest-${quest.code}`, () =>
                    panel.setId ? petApi.claimDailyQuestInSet(panel.setId, quest.code) : petApi.claimDailyQuest(quest.code), '奖励到手啦！')}
                >
                  {quest.claimable ? '领取奖励' : quest.statusLabel}
                </CreamButton>
              )}
            </View>
          </View>
        )
      })}
      {panel.chestClaimed ? (
        <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>宝箱已领取</Text>
      ) : (
        <CreamButton
          variant={panel.chestClaimable ? 'primary' : 'ghost'}
          disabled={!panel.chestClaimable || pending === 'chest'}
          onPress={() => run('chest', () =>
            panel.setId ? petApi.claimDailyQuestChestInSet(panel.setId) : petApi.claimDailyQuestChest(), '宝箱开啦！')}
        >
          {panel.chestClaimable ? '开启全清宝箱' : '全部领取后可开宝箱'}
        </CreamButton>
      )}
    </View>
  )
}

/** 社交面板：宠物关系 / 好友互访 / 留言墙 */
function SocialPanel({ pet, onRefresh }: { pet: PetInfo; onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [tab, setTab] = useState<'relation' | 'friend' | 'wall' | 'safety'>('relation')
  const [relations, setRelations] = useState<PetRelationPanel | null>(null)
  const [friends, setFriends] = useState<PetFriendPanel | null>(null)
  const [wall, setWall] = useState<PetWallPage | null>(null)
  const [wallInput, setWallInput] = useState('')
  const [replyTo, setReplyTo] = useState<number | null>(null)
  const [replyInput, setReplyInput] = useState('')
  const [tip, setTip] = useState<string | null>(null)
  const [pending, setPending] = useState<string | null>(null)
  // B14 屏蔽与举报（名单接口只返回 userId 数组；举报 targetType 白名单 WALL_MESSAGE/BOTTLE_CONTENT/NICKNAME）
  const [blocks, setBlocks] = useState<number[]>([])
  const [blockInput, setBlockInput] = useState('')
  const [reportType, setReportType] = useState('WALL_MESSAGE')
  const [reportTargetId, setReportTargetId] = useState('')
  const [reportReason, setReportReason] = useState('')

  const loadActive = useCallback(async () => {
    try {
      if (tab === 'relation') {
        const { data: res } = await petApi.getRelations()
        if (res.success && res.data) {
          setRelations(res.data)
        }
      } else if (tab === 'friend') {
        const { data: res } = await petApi.getFriends()
        if (res.success && res.data) {
          setFriends(res.data)
        }
      } else if (tab === 'wall') {
        const { data: res } = await petApi.getWall(pet.petId, 1, 10)
        if (res.success && res.data) {
          setWall(res.data)
        }
      } else if (tab === 'safety') {
        const { data: res } = await petApi.listBlocks()
        if (res.success) setBlocks(res.data ?? [])
      }
    } catch {
      // 拦截器已提示
    }
  }, [tab, pet.petId])

  useEffect(() => {
    void loadActive()
  }, [loadActive])

  const doBlock = async () => {
    const target = blockInput.trim()
    if (!target) {
      setTip('先填要拉黑的用户 ID')
      return
    }
    await run('block', () => petApi.blockUser(target), '已拉黑')
    setBlockInput('')
  }

  const doUnblock = async (userId: number) => {
    await run(`unblock-${userId}`, () => petApi.unblockUser(userId), '已解除拉黑')
  }

  const doReport = async () => {
    const targetId = reportTargetId.trim()
    const reason = reportReason.trim()
    if (!targetId || !reason) {
      setTip('目标 ID 和理由都要填')
      return
    }
    await run('report', () => petApi.reportTarget({ targetType: reportType, targetId, reason }), '已提交，管理员会处理')
    setReportTargetId('')
    setReportReason('')
    void loadMyReports()
  }

  /** PET-23：我的举报结果（可公开的处理结果；不含内部审核字段） */
  const [myReports, setMyReports] = useState<import('@/api/pet').PetReportMine[] | null>(null)
  const loadMyReports = useCallback(async () => {
    try {
      const { data: res } = await petApi.listMyReports()
      if (res.success) setMyReports(res.data ?? [])
    } catch {
      setMyReports([])
    }
  }, [])

  useEffect(() => {
    void loadMyReports()
  }, [loadMyReports])

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        Alert.alert('成功', text)
        await loadActive()
        onRefresh()
      }
    } catch (error) {
      Alert.alert('暂时不能这么做', CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试')
    } finally {
      setPending(null)
    }
  }

  const cardStyle = {
    padding: Spacing.sm,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(200, 155, 90, 0.35)',
    backgroundColor: '#FFFDF8',
    gap: 4,
  }

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
        {([
          ['relation', '💞 关系'],
          ['friend', '🫂 好友'],
          ['wall', '📝 留言墙'],
          ['safety', '🛡️ 安全'],
        ] as [typeof tab, string][]).map(([key, label]) => (
          <TouchableOpacity
            key={key}
            onPress={() => setTab(key)}
            style={{
              paddingVertical: 6,
              paddingHorizontal: 12,
              borderRadius: 999,
              backgroundColor: tab === key ? colors.primary : colors.bgBase,
              borderWidth: 1,
              borderColor: tab === key ? colors.primary : colors.border,
            }}
          >
            <Text style={{ color: tab === key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{label}</Text>
          </TouchableOpacity>
        ))}
      </View>
      {tip && <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{tip}</Text>}

      {tab === 'relation' && relations && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            {relations.limits.map((limit) => `${limit.label} ${limit.current}/${limit.max}`).join(' · ')}
          </Text>
          {relations.incoming.map((item) => (
            <View key={String(item.id)} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {item.petName}（{item.ownerNickname}）想成为{item.relTypeLabel}
              </Text>
              <View style={{ flexDirection: 'row', gap: Spacing.xs, justifyContent: 'flex-end' }}>
                <ChipButton
                  label="同意"
                  primary
                  disabled={pending === `a-${item.id}`}
                  onPress={() => run(`a-${item.id}`, () => petApi.acceptRelation(item.id as number), '关系建立啦！')}
                />
                <ChipButton
                  label="拒绝"
                  disabled={pending === `r-${item.id}`}
                  onPress={() => run(`r-${item.id}`, () => petApi.rejectRelation(item.id as number), '已拒绝')}
                />
              </View>
            </View>
          ))}
          {relations.relations.length === 0 && (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有关系，去下面认识一只新宠物吧</Text>
          )}
          {relations.relations.map((item) => (
            <View key={String(item.id)} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {item.petName} · {item.relTypeLabel}
              </Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                {item.ownerNickname} · 亲密度 {item.intimacy}（{item.intimacyLevelName}）
              </Text>
              <View style={{ flexDirection: 'row', justifyContent: 'flex-end' }}>
                <ChipButton
                  label="解除"
                  disabled={pending === `d-${item.id}`}
                  onPress={() => run(`d-${item.id}`, () => petApi.dissolveRelation(item.id as number), '已解除关系')}
                />
              </View>
            </View>
          ))}
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>可以认识的宠物</Text>
          {relations.candidates.map((candidate) => (
            <View key={candidate.petId} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {candidate.petName}（Lv.{candidate.level} · {candidate.ownerNickname}）
              </Text>
              <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.xs }}>
                <ChipButton
                  label="情侣"
                  disabled={pending === `cr-${candidate.petId}`}
                  onPress={() =>
                    run(
                      `cr-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'COUPLE' }),
                      '申请已发出～',
                    )
                  }
                />
                <ChipButton
                  label="闺蜜"
                  disabled={pending === `br-${candidate.petId}`}
                  onPress={() =>
                    run(
                      `br-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'BESTIE' }),
                      '申请已发出～',
                    )
                  }
                />
                <ChipButton
                  label="死党"
                  disabled={pending === `dr-${candidate.petId}`}
                  onPress={() =>
                    run(
                      `dr-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'CONFIDANT' }),
                      '申请已发出～',
                    )
                  }
                />
              </View>
            </View>
          ))}
        </View>
      )}

      {tab === 'friend' && friends && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            今日互访 {friends.todayVisitCount}/{friends.dailyVisitLimit} · 好友上限 {friends.maxFriends}
          </Text>
          {friends.incoming.map((item) => (
            <View key={item.userId} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{item.nickname}</Text>
              <View style={{ flexDirection: 'row', gap: Spacing.xs, justifyContent: 'flex-end' }}>
                <ChipButton
                  label="同意"
                  primary
                  disabled={pending === `fa-${item.userId}`}
                  onPress={() => run(`fa-${item.userId}`, () => petApi.acceptFriend(item.userId), '成为好友啦！')}
                />
                <ChipButton
                  label="拒绝"
                  disabled={pending === `fr-${item.userId}`}
                  onPress={() => run(`fr-${item.userId}`, () => petApi.rejectFriend(item.userId), '已拒绝')}
                />
              </View>
            </View>
          ))}
          {friends.friends.length === 0 && (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有好友，先互相串个门吧</Text>
          )}
          {friends.friends.map((item) => (
            <View key={item.userId} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {item.nickname} · {item.petName ?? '—'}
              </Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>互访 {item.visitCount} 次</Text>
              <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: Spacing.xs }}>
                <ChipButton
                  label="去互访"
                  primary
                  disabled={pending === `fv-${item.userId}`}
                  onPress={() =>
                    run(
                      `fv-${item.userId}`,
                      async () => {
                        const res = await petApi.visitFriend(item.userId)
                        if (res.data.success && res.data.data) {
                          setTip(res.data.data.message)
                        }
                        return res
                      },
                      '互访成功！',
                    )
                  }
                />
                <ChipButton
                  label="邀请协作"
                  disabled={pending === `coop-${item.userId}`}
                  onPress={() => run(`coop-${item.userId}`, () => petApi.createCooperation(item.userId), '协作邀请已发出')}
                />
                <ChipButton
                  label="删除"
                  disabled={pending === `fd-${item.userId}`}
                  onPress={() => run(`fd-${item.userId}`, () => petApi.removeFriend(item.userId), '已删除好友')}
                />
              </View>
            </View>
          ))}
        </View>
      )}

      {tab === 'wall' && wall && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            {wall.petName} 的留言墙 · {wall.ownerNickname} · 共 {wall.total} 条（每日可留言 {wall.dailyPostLimit} 条）
          </Text>
          <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
            <TextInput
              value={wallInput}
              onChangeText={setWallInput}
              maxLength={120}
              placeholder="写一句留言吧"
              placeholderTextColor={colors.textTertiary}
              style={{ flex: 1, color: colors.text, borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, paddingHorizontal: 8 }}
            />
            <ChipButton
              label="留言"
              primary
              disabled={pending === 'post'}
              onPress={() =>
                run(
                  'post',
                  async () => {
                    const res = await petApi.postWallMessage({ petId: pet.petId, content: wallInput })
                    if (res.data.success) {
                      setWallInput('')
                    }
                    return res
                  },
                  '留言成功！',
                )
              }
            />
          </View>
          {wall.messages.length === 0 && (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有留言，说点什么吧～</Text>
          )}
          {wall.messages.map((item) => (
            <View key={item.id} style={cardStyle}>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                {item.authorNickname}
                {item.ownerReply ? '（我的回复）' : ''} · {item.createdAt?.slice(5, 16).replace('T', ' ')}
              </Text>
              <Text style={{ color: colors.text, fontSize: FontSize.sm }}>{item.content}</Text>
              <View style={{ flexDirection: 'row', gap: Spacing.xs, justifyContent: 'flex-end' }}>
                <ChipButton
                  label={`${item.liked ? '取消赞' : '点赞'} ${item.likeCount}`}
                  disabled={pending === `wl-${item.id}`}
                  onPress={() => run(`wl-${item.id}`, () => petApi.likeWallMessage(item.id), '已更新点赞')}
                />
                {item.owner && item.parentId === null && (
                  <ChipButton
                    label="回复"
                    onPress={() => {
                      setReplyTo(replyTo === item.id ? null : item.id)
                      setReplyInput('')
                    }}
                  />
                )}
                {(item.mine || item.owner) && (
                  <ChipButton
                    label="删除"
                    disabled={pending === `wd-${item.id}`}
                    onPress={() => run(`wd-${item.id}`, () => petApi.deleteWallMessage(item.id), '已删除')}
                  />
                )}
              </View>
              {replyTo === item.id && (
                <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
                  <TextInput
                    value={replyInput}
                    onChangeText={setReplyInput}
                    maxLength={120}
                    placeholder="回复这条留言"
                    placeholderTextColor={colors.textTertiary}
                    style={{ flex: 1, color: colors.text, borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, paddingHorizontal: 8 }}
                  />
                  <ChipButton
                    label="发送"
                    primary
                    disabled={pending === `wr-${item.id}`}
                    onPress={() =>
                      run(
                        `wr-${item.id}`,
                        async () => {
                          const res = await petApi.replyWallMessage({ messageId: item.id, content: replyInput })
                          if (res.data.success) {
                            setReplyTo(null)
                          }
                          return res
                        },
                        '回复成功！',
                      )
                    }
                  />
                </View>
              )}
              {item.replies.map((reply) => (
                <Text key={reply.id} style={{ color: colors.textTertiary, fontSize: 10 }}>
                  ↳ {reply.authorNickname}：{reply.content}
                </Text>
              ))}
            </View>
          ))}
        </View>
      )}
      {tab === 'safety' && (
        <View style={{ gap: Spacing.sm }}>
          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>屏蔽名单</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>屏蔽后双方不能新增拜访收益、挑战、留言与好友申请</Text>
            {blocks.length === 0 ? (
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>名单是空的</Text>
            ) : blocks.map((userId) => (
              <View key={userId} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{`用户 #${userId}`}</Text>
                <ChipButton label="解除" disabled={pending === `unblock-${userId}`} onPress={() => void doUnblock(userId)} />
              </View>
            ))}
            <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
              <TextInput
                value={blockInput}
                onChangeText={setBlockInput}
                placeholder="用户 ID"
                placeholderTextColor={colors.textTertiary}
                keyboardType="numeric"
                style={{ flex: 1, borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, color: colors.text, padding: Spacing.xs, fontSize: FontSize.xs }}
              />
              <ChipButton label="拉黑" disabled={pending === 'block'} onPress={() => void doBlock()} />
            </View>
          </View>

          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>举报</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>进入管理员处理队列；类型：留言墙内容 / 漂流瓶内容 / 昵称</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6 }}>
              {([['WALL_MESSAGE', '留言墙内容'], ['BOTTLE_CONTENT', '漂流瓶内容'], ['NICKNAME', '昵称']] as [string, string][]).map(([value, label]) => (
                <TouchableOpacity
                  key={value}
                  onPress={() => setReportType(value)}
                  style={{
                    paddingVertical: 4, paddingHorizontal: 10, borderRadius: 999,
                    backgroundColor: reportType === value ? colors.primary : colors.bgBase,
                    borderWidth: 1, borderColor: reportType === value ? colors.primary : colors.border,
                  }}
                >
                  <Text style={{ fontSize: 10, color: reportType === value ? '#fff' : colors.textSecondary }}>{label}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <TextInput
              value={reportTargetId}
              onChangeText={setReportTargetId}
              placeholder="目标 ID（留言 / 瓶子 / 用户）"
              placeholderTextColor={colors.textTertiary}
              style={{ borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, color: colors.text, padding: Spacing.xs, fontSize: FontSize.xs }}
            />
            <TextInput
              value={reportReason}
              onChangeText={setReportReason}
              placeholder="理由（200 字内）"
              placeholderTextColor={colors.textTertiary}
              maxLength={200}
              multiline
              style={{ borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, color: colors.text, padding: Spacing.xs, fontSize: FontSize.xs, minHeight: 60 }}
            />
            <ChipButton label="提交举报" primary disabled={pending === 'report'} onPress={() => void doReport()} />
            {myReports && myReports.length > 0 ? (
              <View style={{ marginTop: Spacing.sm, gap: Spacing.xs }}>
                <Text style={{ color: colors.textTertiary, fontSize: 10, fontWeight: '600' }}>我的举报</Text>
                {myReports.slice(0, 10).map((report) => {
                  const statusLabel: Record<string, string> = {
                    PENDING: '处理中', RESOLVED: '已处理', REJECTED: '未成立',
                  }
                  return (
                    <View key={String(report.reportId)} style={{ borderTopWidth: 1, borderTopColor: colors.border, paddingTop: Spacing.xs, gap: 2 }}>
                      <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                        {`#${String(report.reportId)} · ${report.reason} · ${statusLabel[report.status] ?? report.status}`}
                      </Text>
                      {report.handleReason ? (
                        <Text style={{ color: colors.textTertiary, fontSize: 10 }}>处理结果：{report.handleReason}</Text>
                      ) : null}
                    </View>
                  )
                })}
              </View>
            ) : null}
          </View>
        </View>
      )}
    </View>
  )
}

/** 家园面板：房间布置 / 家具商城 / 邻里拜访与设置 */
function HomePanel({ onRefresh }: { onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [home, setHome] = useState<PetHome | null>(null)
  const [tab, setTab] = useState<'room' | 'shop' | 'visit'>('room')
  const [pending, setPending] = useState<string | null>(null)
  const [welcome, setWelcome] = useState('')
  const [neighbors, setNeighbors] = useState<PetVisitNeighbor[]>([])
  const [tip, setTip] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const { data: res } = await petApi.getHome()
      if (res.success && res.data) {
        setHome(res.data)
        setWelcome(res.data.welcomeMessage)
      }
    } catch {
      // 拦截器已提示
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (tab !== 'visit') {
      return
    }
    void (async () => {
      try {
        const { data: res } = await petApi.listVisitNeighbors()
        if (res.success) {
          setNeighbors(res.data ?? [])
        }
      } catch {
        // 展示型数据
      }
    })()
  }, [tab])

  const run = async (
    key: string,
    action: () => Promise<{ data: { success: boolean } }>,
    text: string | ((data: unknown) => string),
  ) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        // R13：文案可为函数（按逐项结果汇总），静态文案行为不变
        Alert.alert('成功', typeof text === 'function' ? text(res) : text)
        await load()
        onRefresh()
      }
    } catch (error) {
      Alert.alert('暂时不能这么做', CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试')
    } finally {
      setPending(null)
    }
  }

  if (!home) {
    return <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>家园加载中…</Text>
  }

  const cardStyle = {
    padding: Spacing.sm,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(200, 155, 90, 0.35)',
    backgroundColor: '#FFFDF8',
    gap: 4,
  }
  const themeOptions = home.shop.filter((item) => item.owned)

  return (
    <View style={{ gap: Spacing.sm }}>
      <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
        🏡 舒适度 {home.comfort} · 来访 {home.visitCount} · 点赞 {home.likeCount}
        {home.comfort >= home.comfortBonusThreshold ? ` · 休息心情 +${home.comfortRestHappinessBonus}` : ''}
      </Text>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
        {([
          ['room', '🛋️ 布置'],
          ['shop', '🛒 家具'],
          ['visit', '🚪 拜访'],
        ] as [typeof tab, string][]).map(([key, label]) => (
          <TouchableOpacity
            key={key}
            onPress={() => setTab(key)}
            style={{
              paddingVertical: 6,
              paddingHorizontal: 12,
              borderRadius: 999,
              backgroundColor: tab === key ? colors.primary : colors.bgBase,
              borderWidth: 1,
              borderColor: tab === key ? colors.primary : colors.border,
            }}
          >
            <Text style={{ color: tab === key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {tab === 'room' && (
        <View style={{ gap: Spacing.xs }}>
          {home.placed.map((item) => (
            <View key={`${item.posX}-${item.posY}`} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <Text style={{ color: colors.text, fontSize: FontSize.xs }}>
                {item.icon} {item.name}（{item.posX},{item.posY}）舒适度 +{item.comfort}
              </Text>
              <ChipButton
                label="收回"
                disabled={pending === `rm-${item.posX}-${item.posY}`}
                onPress={() =>
                  run(`rm-${item.posX}-${item.posY}`, () => petApi.removeFurniture(item.posX ?? 0, item.posY ?? 0), '已收回仓库')
                }
              />
            </View>
          ))}
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            当前墙纸/地板：{home.wallCode ?? '默认'} / {home.floorCode ?? '默认'}
          </Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
            {themeOptions.slice(0, 6).map((item) => (
              <ChipButton
                key={`theme-${item.code}`}
                label={`${item.icon}${item.name}`}
                disabled={pending === `theme-${item.code}`}
                onPress={() =>
                  run(
                    `theme-${item.code}`,
                    () =>
                      petApi.updateRoomTheme(
                        item.category === 'WALL'
                          ? { wallCode: item.code, floorCode: home.floorCode }
                          : { wallCode: home.wallCode, floorCode: item.code },
                      ),
                    '已更换主题',
                  )
                }
              />
            ))}
          </View>
        </View>
      )}

      {tab === 'shop' && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
            摆放：在「布置」页点家具即可（自动找空格）——移动端简化交互，精细摆位请用 Web 端
          </Text>
          {home.shop.map((item) => (
            <View key={item.code} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {item.icon} {item.name} · {item.categoryLabel} · 舒适度 +{item.comfort}
              </Text>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={{ color: colors.primary, fontSize: FontSize.xs }}>✨ {item.priceStarlight}</Text>
                {item.owned ? (
                  <ChipButton
                    label="摆放"
                    disabled={pending === `place-${item.code}`}
                    onPress={() =>
                      run(
                        `place-${item.code}`,
                        () => {
                          const occupied = new Set(home.placed.map((placed) => `${placed.posX}-${placed.posY}`))
                          for (let y = 0; y < home.gridHeight; y += 1) {
                            for (let x = 0; x < home.gridWidth; x += 1) {
                              if (!occupied.has(`${x}-${y}`)) {
                                return petApi.placeFurniture({ furnitureCode: item.code, posX: x, posY: y })
                              }
                            }
                          }
                          return petApi.placeFurniture({ furnitureCode: item.code, posX: 0, posY: 0 })
                        },
                        '摆放好啦',
                      )
                    }
                  />
                ) : (
                  <ChipButton
                    label={item.eligible ? '购买' : item.lockReason ?? '未解锁'}
                    primary
                    disabled={!item.eligible || pending === `buy-${item.code}`}
                    onPress={() => run(`buy-${item.code}`, () => petApi.buyFurniture(item.code), '买到啦')}
                  />
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {tab === 'visit' && (
        <View style={{ gap: Spacing.sm }}>
          {tip && <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{tip}</Text>}
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs }}>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>允许来访</Text>
            <Switch
              value={home.isPublic}
              onValueChange={(checked) => run('settings', () => petApi.updateRoomSettings({ isPublic: checked }), '设置已更新')}
            />
            <TextInput
              value={welcome}
              onChangeText={setWelcome}
              maxLength={40}
              placeholder="欢迎语"
              placeholderTextColor={colors.textTertiary}
              style={{ flex: 1, color: colors.text, borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm, paddingHorizontal: 8 }}
            />
            <ChipButton
              label="保存"
              disabled={pending === 'welcome'}
              onPress={() => run('welcome', () => petApi.updateRoomSettings({ welcomeMessage: welcome }), '欢迎语已更新')}
            />
          </View>
          {neighbors.map((neighbor) => (
            <View key={String(neighbor.petId)} style={cardStyle}>
              <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                {neighbor.name}（Lv.{neighbor.level} · {neighbor.ownerNickname}）
              </Text>
              <View style={{ flexDirection: 'row', gap: Spacing.xs, justifyContent: 'flex-end' }}>
                <ChipButton
                  label="去家里看看"
                  primary
                  disabled={pending === `v-${neighbor.petId}`}
                  onPress={() =>
                    run(
                      `v-${neighbor.petId}`,
                      async () => {
                        const res = await petApi.enterHome(neighbor.petId)
                        if (res.data.success && res.data.data) {
                          setTip(res.data.data.message)
                        }
                        return res
                      },
                      '拜访成功！',
                    )
                  }
                />
                <ChipButton
                  label="点赞"
                  disabled={pending === `l-${neighbor.petId}`}
                  onPress={() => run(`l-${neighbor.petId}`, () => petApi.likeHome(neighbor.petId), '点赞成功')}
                />
                <ChipButton
                  label="加好友"
                  disabled={pending === `fq-${neighbor.petId}`}
                  onPress={() =>
                    run(`fq-${neighbor.petId}`, () => petApi.requestFriend(neighbor.ownerUserId), '好友申请已发出～')
                  }
                />
              </View>
            </View>
          ))}
        </View>
      )}
    </View>
  )
}

// ==================== 陪伴面板（方案 B：纪念日/人设卡/好友动态/日记·相册/记忆/引导与通知） ====================

type CompanionTab = 'anniversary' | 'persona' | 'feed' | 'diary' | 'memory' | 'settings'

const COMPANION_TABS: { key: CompanionTab; label: string }[] = [
  { key: 'anniversary', label: '纪念日' },
  { key: 'persona', label: '人设卡' },
  { key: 'feed', label: '动态' },
  { key: 'diary', label: '日记·相册' },
  { key: 'memory', label: '记忆' },
  { key: 'settings', label: '引导·通知' },
]

const MEMORY_TYPE_LABEL: Record<string, string> = { FAVORITE: '喜好', HABIT: '习惯', FACT: '事实' }
const FEED_EVENT_LABEL: Record<string, string> = {
  LEVEL_UP: '升级', WORK_COMPLETED: '打工完成', STUDY_COMPLETED: '读书完成', BATTLE_WIN: '对战获胜',
}

function fmtCompanionSeconds(seconds: number): string {
  const total = Math.max(0, Math.round(seconds))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  if (h > 0) return `${h} 小时 ${m} 分`
  return m > 0 ? `${m} 分钟` : `${total} 秒`
}

function CompanionPanel({ petId, onRefresh }: { petId: number | string; onRefresh: () => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [tab, setTab] = useState<CompanionTab>('anniversary')
  const [pending, setPending] = useState<string | null>(null)
  const [ann, setAnn] = useState<PetAnniversary | null>(null)
  const [persona, setPersona] = useState<PetPersona | null>(null)
  const [feed, setFeed] = useState<PetFriendFeedItem[]>([])
  const [feedUnread, setFeedUnread] = useState(0)
  const [diary, setDiary] = useState<PetDiaryPage | null>(null)
  const [diaryCursor, setDiaryCursor] = useState<string | null>(null)
  const [assets, setAssets] = useState<{ id: number | string; url: string; diaryEntryId: number | string | null; bindStatus?: string }[]>([])
  const [memories, setMemories] = useState<PetMemory[]>([])
  const [editId, setEditId] = useState<number | null>(null)
  const [editValue, setEditValue] = useState('')
  // 记忆开关草稿：服务端无读取端点，默认取库表默认值 1/1（界面已注明）
  const [extractDraft, setExtractDraft] = useState(true)
  const [useDraft, setUseDraft] = useState(true)
  const [onboarding, setOnboarding] = useState<PetOnboardingProgress | null>(null)
  const [pref, setPref] = useState<PetNotifyPref | null>(null)

  const run = useCallback(async <T,>(
    key: string,
    task: () => Promise<{ data: { success: boolean; data?: T; error?: { message?: string } } }>,
  ): Promise<{ success: boolean; data?: T } | null> => {
    setPending(key)
    try {
      const { data: res } = await task()
      if (!res.success) {
        Alert.alert('暂时不能这么做', res.error?.message ?? '请稍后再试')
      }
      return res
    } finally {
      setPending(null)
    }
  }, [])

  const loadAnn = useCallback(async () => {
    const { data: res } = await petApi.getAnniversaries()
    if (res.success) setAnn(res.data)
  }, [])

  const loadPersona = useCallback(async () => {
    const { data: res } = await petApi.getChatPersona()
    if (res.success) setPersona(res.data)
  }, [])

  const loadFeed = useCallback(async () => {
    try {
      const [{ data: list }, { data: unread }] = await Promise.all([
        petApi.listFriendFeed({ size: 20 }),
        petApi.getFriendFeedUnreadCount(),
      ])
      if (list.success) setFeed(list.data ?? [])
      if (unread.success) setFeedUnread(Number(unread.data ?? 0))
    } catch {
      // 展示型数据：忽略
    }
  }, [])

  const loadDiary = useCallback(async (reset: boolean) => {
    const { data: res } = await petApi.listDiary(petId, {
      cursor: reset ? undefined : (diaryCursor ?? undefined),
      size: 20,
    })
    if (res.success && res.data) {
      setDiary((prev) => reset
        ? res.data
        : { items: [...(prev?.items ?? []), ...res.data.items], nextCursor: res.data.nextCursor, hasMore: res.data.hasMore })
      setDiaryCursor(res.data.nextCursor)
    }
  }, [diaryCursor, petId])

  const loadMemories = useCallback(async () => {
    const { data: res } = await petApi.listMemories(petId)
    if (res.success) setMemories(res.data ?? [])
  }, [petId])

  const loadSettings = useCallback(async () => {
    try {
      const [{ data: prefRes }, { data: onboardingRes }] = await Promise.all([
        petApi.getNotifyPrefs(),
        petApi.getOnboarding(),
      ])
      if (prefRes.success && prefRes.data) setPref(prefRes.data)
      if (onboardingRes.success && onboardingRes.data) setOnboarding(onboardingRes.data)
    } catch {
      // 展示型数据：忽略
    }
  }, [])

  useEffect(() => {
    // 各页签懒加载：切换到对应页签时拉取一次（数据量小，不做缓存失效）
    if (tab === 'anniversary' && !ann) void loadAnn()
    if (tab === 'persona' && !persona) void loadPersona()
    if (tab === 'feed') void loadFeed()
    if (tab === 'diary' && !diary) void loadDiary(true)
    if (tab === 'memory') void loadMemories()
    if (tab === 'settings') void loadSettings()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab])

  const readFeed = useCallback(async () => {
    const res = await run('feed', () => petApi.markFriendFeedRead())
    if (res?.success) setFeedUnread(0)
  }, [run])

  /** PET-13/T31：独立相册列表（服务端权威；BINDING/FAILED 照实展示并给重试入口） */
  const loadAlbum = useCallback(async () => {
    if (!petId) return
    try {
      const { data: res } = await petApi.listAlbumAssets(petId)
      if (res.success && res.data) {
        setAssets(res.data
            .filter((item) => item.bindStatus !== 'DELETED')
            .map((item) => ({
              id: item.assetId,
              url: item.bindStatus === 'BOUND' ? (item.previewUrl ?? '') : '',
              diaryEntryId: item.diaryEntryId,
              bindStatus: item.bindStatus,
            })))
      }
    } catch {
      // 展示型数据：忽略
    }
  }, [petId])

  // PET-13/T34：previewUrl 为 60 秒短效签名——相册数据所在页签可见时每 45 秒静默刷新
  useEffect(() => {
    if (tab !== 'feed') return
    const timer = setInterval(() => void loadAlbum(), 45_000)
    return () => clearInterval(timer)
  }, [tab, loadAlbum])

  const uploadPhoto = useCallback(async (diaryEntryId?: number) => {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync()
    if (!perm.granted) {
      Alert.alert('需要相册权限', '请在系统设置里允许访问相册')
      return
    }
    const picked = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 })
    if (picked.canceled || !picked.assets?.[0]) return
    const asset = picked.assets[0]
    setPending('album')
    try {
      const form = new FormData()
      // RN FormData：以 uri 引用本地图片，随 multipart 上传
      form.append('file', { uri: asset.uri, name: 'album.jpg', type: 'image/jpeg' } as unknown as Blob)
      // PET-13/T31：相册资产上传即 PRIVATE（绑定要求 PRIVATE，缺省 PUBLIC 会导致绑定失败/隐私泄露）
      const { data: up } = await fileApi.upload(form, 'PRIVATE')
      if (!up.success || !up.data) {
        Alert.alert('上传未成功', up.error?.message ?? '请稍后再试')
        return
      }
      const res = await run('album', () => petApi.uploadAlbumAsset(petId, up.data!.fileId, diaryEntryId))
      if (res?.success && res.data) {
        setAssets((prev) => [{ id: res.data!.id, url: up.data!.url ?? asset.uri, diaryEntryId: res.data!.diaryEntryId }, ...prev])
        // PET-13/T31：以服务端相册列表校准（重启/刷新后仍在，本地数组不再是唯一事实）
        void loadAlbum()
        Alert.alert('成功', '照片已存入相册')
      }
    } catch {
      Alert.alert('上传未成功', '请稍后再试')
    } finally {
      setPending(null)
    }
  }, [petId, run, loadAlbum])

  /** PET-13/T32：BINDING/FAILED 条目重试绑定（远端幂等；成功后刷新列表） */
  const retryAssetBinding = useCallback(async (assetId: number | string) => {
    try {
      await petApi.retryAlbumAsset(petId, assetId)
      await loadAlbum()
    } catch {
      Alert.alert('重试未成功', '稍后再试或删除该条目')
    }
  }, [petId, loadAlbum])

  const removeAsset = useCallback(async (assetId: number | string) => {
    const res = await run(`album-del:${assetId}`, () => petApi.deleteAlbumAsset(petId, assetId))
    if (res?.success) setAssets((prev) => prev.filter((item) => item.id !== assetId))
  }, [petId, run])

  const saveMemory = useCallback(async (memory: PetMemory) => {
    const value = editValue.trim()
    if (!value) {
      Alert.alert('提示', '记忆内容不能为空')
      return
    }
    const res = await run(`memory:${memory.id}`,
      () => petApi.editMemory(petId, memory.id, { value: value }))
    if (res?.success) {
      setEditId(null)
      void loadMemories()
    }
  }, [editValue, loadMemories, petId, run])

  const removeMemory = useCallback(async (memoryId: number) => {
    const res = await run(`memory-del:${memoryId}`, () => petApi.deleteMemory(petId, memoryId))
    if (res?.success) void loadMemories()
  }, [loadMemories, petId, run])

  const clearAllMemories = useCallback(() => {
    Alert.alert('清空全部记忆？', '所有结构化记忆会被移除（含自动抽取的），且不可恢复。', [
      { text: '再想想', style: 'cancel' },
      {
        text: '清空',
        style: 'destructive',
        onPress: () => {
          void (async () => {
            const res = await run('memory-clear', () => petApi.clearMemories(petId))
            if (res?.success) void loadMemories()
          })()
        },
      },
    ])
  }, [loadMemories, petId, run])

  const toggleMemorySetting = useCallback(async (extract: boolean, use: boolean) => {
    const res = await run('memory-setting', () => petApi.setMemorySettings(petId, { extract, use }))
    if (res?.success) Alert.alert('成功', '记忆设置已更新')
  }, [petId, run])

  const togglePref = useCallback(async (next: { muteDailyGreeting: boolean; dailyGreetingEnabled: boolean }) => {
    // §7.2：带当前 version 做 CAS，冲突由错误链路提示
    const res = await run('pref', () => petApi.updateNotifyPrefs({ ...next, expectedVersion: pref?.version }))
    if (res?.success && res.data && pref) {
      setPref({ ...pref, ...res.data })
    }
  }, [pref, run])

  const skipOnboarding = useCallback(async () => {
    const res = await run('onboarding', () => petApi.skipOnboarding())
    if (res?.success) {
      const { data: onboardingRes } = await petApi.getOnboarding()
      if (onboardingRes.success && onboardingRes.data) setOnboarding(onboardingRes.data)
      onRefresh()
    }
  }, [onRefresh, run])

  const milestoneText = (daysToGo: number): string => {
    if (daysToGo === 0) return '就是今天！'
    return daysToGo > 0 ? `还有 ${daysToGo} 天` : `已达成 ${-daysToGo} 天`
  }

  const cardStyle = {
    padding: Spacing.sm, borderRadius: 16, borderWidth: 1, borderColor: 'rgba(200, 155, 90, 0.35)',
    backgroundColor: '#FFFDF8', gap: 4,
  } as const

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
        {COMPANION_TABS.map((item) => (
          <TouchableOpacity
            key={item.key}
            onPress={() => setTab(item.key)}
            style={{
              paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
              backgroundColor: tab === item.key ? colors.primary : colors.bgBase,
              borderWidth: 1, borderColor: tab === item.key ? colors.primary : colors.border,
            }}
          >
            <Text style={{ color: tab === item.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{item.label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {tab === 'anniversary' && (
        !ann ? <ActivityIndicator color={colors.primary} /> : (
          <View style={{ gap: Spacing.sm }}>
            <View style={[cardStyle, { alignItems: 'center', paddingVertical: Spacing.md }]}>
              <Text style={{ fontSize: 26, color: colors.text, fontWeight: '600' }}>
                {`${ann.adoptionDays} 天`}
              </Text>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                {`领养至今 · 连续陪伴 ${ann.companionStreak} 天`}
              </Text>
            </View>
            <View style={cardStyle}>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                {`当前里程碑：${ann.currentMilestone ? `${ann.currentMilestone.title}（${milestoneText(ann.currentMilestone.daysToGo)}）` : '暂无'}`}
              </Text>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                {`下一个：${ann.nextMilestone ? `${ann.nextMilestone.title}（${milestoneText(ann.nextMilestone.daysToGo)}）` : '全部达成'}`}
              </Text>
            </View>
          </View>
        )
      )}

      {tab === 'persona' && (
        !persona ? <ActivityIndicator color={colors.primary} /> : (
          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{`${persona.name} 的人设卡`}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{persona.personalityText}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
              {`职业：${persona.careerName ?? '无业游民'} · 亲密度：${persona.intimacyLevelName ?? `Lv.${persona.intimacyLevel}`}`}
            </Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
              {`${persona.ownerTitle ? `它叫你「${persona.ownerTitle}」` : '它还没学会怎么叫你'}${persona.phrase ? ` · 口头禅：“${persona.phrase}”` : ''}`}
            </Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>与注入 AI 的身份信息同源（F8）</Text>
          </View>
        )
      )}

      {tab === 'feed' && (
        <View style={{ gap: Spacing.xs }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{`未读 ${feedUnread} 条`}</Text>
            {feedUnread > 0 && <ChipButton label="标记已读" onPress={() => void readFeed()} />}
          </View>
          {feed.length === 0 ? (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>暂无动态</Text>
          ) : feed.map((item) => (
            <View key={item.feedId} style={cardStyle}>
              <Text style={{ color: colors.primary, fontSize: 10 }}>
                {FEED_EVENT_LABEL[item.eventType] ?? item.eventType}
              </Text>
              <Text style={{ color: colors.text, fontSize: FontSize.xs }}>{item.text}</Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                {item.createdAt ? item.createdAt.slice(5, 16).replace('T', ' ') : ''}
              </Text>
            </View>
          ))}
        </View>
      )}

      {tab === 'diary' && (
        <View style={{ gap: Spacing.sm }}>
          <View style={{ gap: Spacing.xs }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>成长日记</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>由宠物的大事自动生成，客户端不能代写</Text>
            {(diary?.items.length ?? 0) === 0 ? (
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有日记，陪它做点什么吧</Text>
            ) : diary!.items.map((entry) => (
              <View key={entry.id} style={cardStyle}>
                <Text style={{ color: colors.primary, fontSize: 10 }}>
                  {`${entry.type}${entry.visibility !== 'PUBLIC' ? ' · 仅自己可见' : ''}${entry.assetIds?.length ? ` · 含 ${entry.assetIds.length} 张照片` : ''} · ${entry.createdAt.slice(5, 16).replace('T', ' ')}`}
                </Text>
                <Text style={{ color: colors.text, fontSize: FontSize.xs, lineHeight: 18 }}>{entry.content}</Text>
              </View>
            ))}
            {diary?.hasMore && (
              <ChipButton label="加载更多" onPress={() => void loadDiary(false)} />
            )}
          </View>

          <View style={{ gap: Spacing.xs }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>相册</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
              每只宠物 100 张；服务端暂无相册列表接口，这里只显示本次上传的
            </Text>
            <ChipButton
              label={pending === 'album' ? '上传中…' : '上传照片'}
              disabled={pending === 'album'}
              onPress={() => void uploadPhoto()}
            />
            {assets.length > 0 && assets.map((item) => (
              <View key={item.id} style={[cardStyle, { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }]}>
                {item.bindStatus === 'BOUND' ? (
                  <Image
                    source={{ uri: item.url }}
                    style={{ width: 64, height: 64, borderRadius: BorderRadius.sm, backgroundColor: colors.bgBase }}
                  />
                ) : (
                  <View style={{ width: 64, height: 64, borderRadius: BorderRadius.sm, backgroundColor: colors.bgBase, alignItems: 'center', justifyContent: 'center' }}>
                    <Text style={{ color: colors.textTertiary, fontSize: 9, textAlign: 'center' }}>
                      {item.bindStatus === 'FAILED' ? '绑定失败' : '绑定中'}
                    </Text>
                  </View>
                )}
                <View style={{ flex: 1, gap: 2 }}>
                  <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                    {item.diaryEntryId ? `日记 #${item.diaryEntryId}` : '未挂日记'}
                  </Text>
                  {item.bindStatus && item.bindStatus !== 'BOUND' ? (
                    <ChipButton label={item.bindStatus === 'FAILED' ? '重试绑定' : '绑定处理中…'}
                      onPress={() => item.bindStatus === 'FAILED' ? void retryAssetBinding(item.id) : undefined} />
                  ) : null}
                  <ChipButton label="删除" onPress={() => void removeAsset(item.id)} />
                </View>
              </View>
            ))}
          </View>
        </View>
      )}

      {tab === 'memory' && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            {`共 ${memories.length} 条 · USER 编辑优先于 AUTO 抽取`}
          </Text>
          {memories.length === 0 ? (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有记忆，多陪它聊聊就会慢慢积累</Text>
          ) : memories.map((item) => (
            <View key={item.id} style={cardStyle}>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}>
                <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{item.memoryKey}</Text>
                <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                  {`${MEMORY_TYPE_LABEL[item.memoryType] ?? item.memoryType} · ${item.source === 'USER' ? '我写的' : '自动抽取'} · 重要度 ${item.importance}`}
                </Text>
              </View>
              {editId === item.id ? (
                <View style={{ gap: Spacing.xs }}>
                  <TextInput
                    value={editValue}
                    onChangeText={setEditValue}
                    maxLength={200}
                    placeholder="记忆内容"
                    placeholderTextColor={colors.textTertiary}
                    style={{
                      borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm,
                      color: colors.text, padding: Spacing.xs, fontSize: FontSize.xs,
                    }}
                  />
                  <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
                    <ChipButton label="保存" primary disabled={pending === `memory:${item.id}`} onPress={() => void saveMemory(item)} />
                    <ChipButton label="取消" onPress={() => setEditId(null)} />
                  </View>
                </View>
              ) : (
                <>
                  <Text style={{ color: colors.text, fontSize: FontSize.xs, lineHeight: 18 }}>{item.memoryValue}</Text>
                  <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                    {`置信度 ${item.confidence} · 更新于 ${item.updatedAt.slice(5, 16).replace('T', ' ')}`}
                  </Text>
                  <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
                    <ChipButton label="编辑" onPress={() => { setEditId(item.id); setEditValue(item.memoryValue) }} />
                    <ChipButton label="删除" disabled={pending === `memory-del:${item.id}`} onPress={() => void removeMemory(item.id)} />
                  </View>
                </>
              )}
            </View>
          ))}

          {memories.length > 0 && (
            <ChipButton label="清空全部记忆" disabled={pending === 'memory-clear'} onPress={clearAllMemories} />
          )}

          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>记忆开关</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
              两开关相互独立（默认都开启）；服务端没有查询端点，保存后即以本次设置为准
            </Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>自动抽取</Text>
              <Switch value={extractDraft} onValueChange={setExtractDraft} />
            </View>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>注入上下文</Text>
              <Switch value={useDraft} onValueChange={setUseDraft} />
            </View>
            <ChipButton label="保存记忆开关" primary disabled={pending === 'memory-setting'} onPress={() => void toggleMemorySetting(extractDraft, useDraft)} />
          </View>
        </View>
      )}

      {tab === 'settings' && (
        <View style={{ gap: Spacing.sm }}>
          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>通知偏好</Text>
            {pref ? (
              <>
                <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                    {`每日问候（${pref.dailyGreetingEnabled ? '已开启' : '已关闭'}）`}
                  </Text>
                  <Switch
                    value={pref.dailyGreetingEnabled}
                    onValueChange={(value) => void togglePref({ muteDailyGreeting: pref.muteDailyGreeting, dailyGreetingEnabled: value })}
                  />
                </View>
                <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                    {`免打扰（${pref.muteDailyGreeting ? '已开启' : '正常提醒'}）`}
                  </Text>
                  <Switch
                    value={pref.muteDailyGreeting}
                    onValueChange={(value) => void togglePref({ muteDailyGreeting: value, dailyGreetingEnabled: pref.dailyGreetingEnabled })}
                  />
                </View>
              </>
            ) : <ActivityIndicator color={colors.primary} />}
          </View>

          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>新手引导</Text>
            {onboarding ? (
              <>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                  {`进度 ${onboarding.currentStep}/${onboarding.totalSteps}${onboarding.completed ? ' · 已完成' : ''}`}
                </Text>
                <ChipButton
                  label="跳过引导"
                  disabled={!onboarding.skippable || onboarding.completed || pending === 'onboarding'}
                  onPress={() => void skipOnboarding()}
                />
              </>
            ) : <ActivityIndicator color={colors.primary} />}
          </View>
        </View>
      )}
    </View>
  )
}
