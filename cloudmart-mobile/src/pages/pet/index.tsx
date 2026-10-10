import { useCallback, useEffect, useRef, useState } from 'react'
import { View, Text, Input, Button, ScrollView } from '@tarojs/components'
import Taro, { useRouter, useDidShow, useDidHide } from '@tarojs/taro'
import {
  petApi, petCompanionApi,
  type PetAnniversary,
  type PetChatPersona,
  type PetSeasonHistoryItem,
  type PetSeasonRanking,
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
  type SeasonPassVO,
  type PetRankingType,
  type PetReminder,
  type PetShareCard,
  type PetShopItem,
  type PetSkillItem,
  type PetStudyItem,
  type PetSummary,
  type PetVisitNeighbor,
  type PetVisitorLog,
  type PetIntimacyInfo,
  type PetOnboardingProgress,
  type PetActionItem,
  type PetEquipPreview,
} from '@/api/pet'
import { notificationApi } from '@/api/notification'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { useThemeClass } from '@/composables/useThemeClass'
import { PET_CREAM_STYLE, PET_CREAM_STAT_TONE, PET_CREAM_EXT_STYLE } from '@/styles/petCream'
import { PET_STAGE_URL } from '@/api/pet'
import { CreamToggle } from '@/components/pet-cream'
import { CreamCard, CreamButton, CreamChip, CreamStatBar, CreamMasthead, CreamSheet } from '@/components/pet-cream'
import cream from '@/components/pet-cream/pet-cream.module.scss'
import styles from './index.module.scss'
// P2-4：独立面板组件拆分（行为不变，见各 panels 文件）
import { CARE_ERROR_HINT } from './panels/shared'
import { CareerPanel } from './panels/CareerPanel'
import { DailyQuestPanel } from './panels/DailyQuestPanel'
import { SocialPanel } from './panels/SocialPanel'
import { HomePanel } from './panels/HomePanel'
import { MemoryPanel } from './panels/MemoryPanel'


/**
 * 社区宠物原生管理页（小程序/H5，实施文档 §5/§34）。
 *
 * 全平台可用的完整管理入口：领养/互动/打工/读书/捞瓶/对战/聊天/成就/提醒/档案/分享。
 * Cocos 舞台在独立页 petStage（H5 为完整双向桥；微信小程序 web-view 受平台限制只做
 * 观赏，游戏操作经 navigateTo 携 intent 回落本页执行，平台约束见实施文档 §2.4）。
 */

const SPECIES_OPTIONS = [
  { value: 'STRAWBERRY', emoji: '🍓', label: '草莓' },
  { value: 'ORANGE', emoji: '🍊', label: '橘子' },
  { value: 'WATERMELON', emoji: '🍉', label: '西瓜' },
  { value: 'BLUEBERRY', emoji: '🫐', label: '蓝莓' },
  { value: 'DRAGONFRUIT', emoji: '🐉', label: '火龙果' },
] as const

/** 领养可选性别（与服务端 PetGender 一致） */
const GENDER_OPTIONS = [
  { value: 'MALE', label: '♂ 男' },
  { value: 'FEMALE', label: '♀ 女' },
] as const

const PERSONALITY_OPTIONS = [
  { value: 'LIVELY', label: '活泼' },
  { value: 'GENTLE', label: '温柔' },
  { value: 'TSUNDERE', label: '傲娇' },
  { value: 'SIMPLE', label: '憨厚' },
  { value: 'COOL', label: '高冷' },
  { value: 'CHATTERBOX', label: '话痨' },
] as const

const SPECIES_EMOJI: Record<string, string> = {
  STRAWBERRY: '🍓',
  ORANGE: '🍊',
  WATERMELON: '🍉',
  BLUEBERRY: '🫐',
  DRAGONFRUIT: '🐉',
}
const STATUS_LABEL: Record<string, string> = {
  IDLE: '悠闲中', WORKING: '打工中', STUDYING: '读书中', FISHING: '捞瓶中', RESTING: '休息中',
  WEAK: '饿坏了！快喂食', SICK: '不开心病了，多陪陪它',
}
const GROWTH_STAGE_LABEL: Record<string, string> = { BABY: '幼年', YOUNG: '成长期', ADULT: '成年' }
const ACTIVITY_LABEL: Record<string, string> = { WORK: '打工', STUDY: '读书', BOTTLE_FISHING: '捞漂流瓶' }
const PET_COLORS = ['orange', 'white', 'black', 'gray', 'brown']
const ACCESSORIES = [
  { key: 'none', label: '无' },
  { key: 'bell', label: '铃铛' },
  { key: 'bow', label: '领结' },
  { key: 'glasses', label: '眼镜' },
  { key: 'scarf', label: '围巾' },
] as const
const PRIORITY_LABEL: Record<string, string> = { P0: '重要', P1: '普通', P2: '低' }
const SLOT_LABEL: Record<string, string> = { HAT: '帽子', NECKLACE: '项圈', SCARF: '围巾', BACKPACK: '背包' }
const ITEM_TYPE_LABEL: Record<string, string> = { EQUIPMENT: '装备', SKIN: '皮肤', SKILL_BOOK: '技能书' }
/** B06 动作可执行性的展示名（action 码 → 中文） */
const ACTION_LABEL: Record<string, string> = {
  FEED: '喂食', PLAY: '玩耍', CLEAN: '清洁', REST: '休息',
  WORK: '打工', STUDY: '读书', BOTTLE: '捞瓶', BATTLE: '对战',
}
const EVENT_TYPE_LABEL: Record<string, string> = {
  BOTTLE: '捞瓶', BATTLE: '对战胜场', WORK: '打工', STUDY: '读书', FEED: '喂食', PLAY: '玩耍', VISIT: '串门',
}

/** 养成面板子页签（原文档 §89 商城/背包/技能/进化/活动 + §1.1 串门 + 多宠物） */
type CareTab = 'shop' | 'inventory' | 'skills' | 'evolution' | 'events' | 'visit' | 'career' | 'pets'
const CARE_TABS: Array<{ key: CareTab; label: string }> = [
  { key: 'shop', label: '🛒 商城' },
  { key: 'inventory', label: '🎒 背包' },
  { key: 'skills', label: '🌟 技能' },
  { key: 'evolution', label: '🌠 进化' },
  { key: 'events', label: '🎯 活动' },
  { key: 'visit', label: '🚪 串门' },
  { key: 'pets', label: '🐾 宠物' },
]
const RARITY_LABEL: Record<string, string> = {
  NORMAL: '普通瓶', RARE: '稀有瓶', PET: '宠物瓶', EASTER_EGG: '彩蛋瓶',
}
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
  { key: 'COLLECTION', label: '图鉴卡片' },
]

type PanelKey =
  | 'home' | 'care' | 'work' | 'study' | 'bottle' | 'battle'
  | 'chat' | 'achievements' | 'rankings' | 'reminders'
  | 'daily' | 'social' | 'memory'

const PANELS: Array<{ key: PanelKey; label: string; emoji: string }> = [
  { key: 'home', label: '家园', emoji: '🏠' },
  { key: 'memory', label: '记忆', emoji: '🧠' },
  { key: 'daily', label: '任务', emoji: '✅' },
  { key: 'work', label: '打工', emoji: '💼' },
  { key: 'study', label: '读书', emoji: '📚' },
  { key: 'bottle', label: '捞瓶', emoji: '🍾' },
  { key: 'battle', label: '对战', emoji: '⚔️' },
  { key: 'care', label: '养成', emoji: '🎒' },
  { key: 'social', label: '社交', emoji: '🤝' },
  { key: 'chat', label: '聊天', emoji: '💬' },
  { key: 'achievements', label: '成就', emoji: '🏆' },
  { key: 'rankings', label: '榜单', emoji: '🥇' },
  { key: 'reminders', label: '提醒', emoji: '🔔' },
]

const PANEL_TITLE = Object.fromEntries(PANELS.map((item) => [item.key, item.label])) as Record<PanelKey, string>

/** 功能宫格分组（对齐 Web 端 PANEL_GROUPS：La Maison / Croissance / Les Amis / Archives） */
const MENU_GROUPS: Array<{ label: string; hint: string; keys: PanelKey[] }> = [
  { label: 'La Maison', hint: '小家与记忆', keys: ['home', 'memory'] },
  { label: 'Croissance', hint: '养成', keys: ['daily', 'work', 'study', 'bottle', 'battle', 'care'] },
  { label: 'Les Amis', hint: '往来', keys: ['social', 'chat'] },
  { label: 'Archives', hint: '记录', keys: ['achievements', 'rankings', 'reminders'] },
]

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

function friendlyError(error: unknown): string {
  const code = (error as { code?: string }).code
  const hints: Record<string, string> = {
    PET_NOT_FOUND: '你还没有宠物，先领养一只吧',
    PET_ALREADY_EXISTS: '你已经有一只宠物啦',
    PET_STATE_FULL: '已经吃饱/很干净啦',
    PET_ENERGY_INSUFFICIENT: '没有力气了，先休息一下吧',
    PET_HUNGER_TOO_LOW: '肚子太空了，先喂点东西吧',
    PET_ACTIVITY_CONFLICT: '宠物正在忙别的事',
    PET_ACTIVITY_ALREADY_CLAIMED: '奖励已经领取过啦',
    PET_ACTIVITY_NOT_FINISHED: '任务还没完成，再等等',
    PET_LEVEL_REQUIRED: '等级还不够，先多养成一下',
    PET_BOTTLE_COOLDOWN: '宠物刚回来还在休息',
    PET_AI_RATE_LIMITED: '今天聊得够多啦，明天再来吧',
    PET_INTERACTION_RATE_LIMITED: '今天喂得够多啦',
    PET_RENAME_COOLDOWN: '30 天只能改一次名',
    WISH_SERVICE_UNAVAILABLE: '心愿服务暂时不可用，稍后再试',
  }
  return hints[code ?? ''] || (error as { message?: string })?.message || '操作失败，请稍后再试'
}

function parseResult<T>(raw: string | null | undefined): T | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

function formatClock(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds))
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, '0')}`
}

/** 服务端返回的时间为 UTC（无时区标记），此处统一补 Z 再参与倒计时计算 */
function parseUtc(value: string): number {
  return Date.parse(value.length === 19 ? `${value}Z` : value)
}

function useCountdown(finishedAt: string | null): number {
  const [remaining, setRemaining] = useState(0)
  useEffect(() => {
    if (!finishedAt) {
      setRemaining(0)
      return
    }
    const compute = () => {
      const target = parseUtc(finishedAt)
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

export default function PetPage() {
  const { params } = useRouter()
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
  const [adoptSpecies, setAdoptSpecies] = useState('STRAWBERRY')
  const [adoptGender, setAdoptGender] = useState('MALE')
  const [adoptPersonality, setAdoptPersonality] = useState('LIVELY')
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
  // 赛季通行证（§6）：榜单页签惰性加载
  const [seasonPass, setSeasonPass] = useState<SeasonPassVO | null>(null)
  // 实物商品联动（§6）：双倍喂食权益 + 兑换码输入
  const [feedDoubled, setFeedDoubled] = useState(false)
  const [redeemCode, setRedeemCode] = useState('')
  const [redeeming, setRedeeming] = useState(false)
  // F2：赛季榜与历届名次
  const [season, setSeason] = useState<PetSeasonRanking | null>(null)
  const [seasonHistory, setSeasonHistory] = useState<PetSeasonHistoryItem[]>([])
  const [rankingType, setRankingType] = useState<PetRankingType>('LEVEL')
  const [reminders, setReminders] = useState<PetReminder[]>([])
  const [reminderUnread, setReminderUnread] = useState(0)
  const [onboarding, setOnboarding] = useState<PetOnboardingProgress | null>(null)

  useEffect(() => {
    petCompanionApi
      .getOnboarding()
      .then(({ data: res }) => {
        if (res.success && res.data) setOnboarding(res.data)
      })
      .catch(() => undefined)
  }, [])
  const [shareCard, setShareCard] = useState<PetShareCard | null>(null)
  const [profileOpen, setProfileOpen] = useState(false)
  const [profileName, setProfileName] = useState('')
  // 主人称呼（宠物怎么叫主人）草稿
  const [profileOwnerTitle, setProfileOwnerTitle] = useState('')
  const [profileColor, setProfileColor] = useState(PET_COLORS[0])
  const [profileAccessory, setProfileAccessory] = useState('none')
  const [privacyBusy, setPrivacyBusy] = useState(false)
  // 领养外观（原文档 §6 外观系统）
  const [adoptColor, setAdoptColor] = useState('orange')
  const [adoptAccessory, setAdoptAccessory] = useState('none')
  // 养成面板（原文档 §1.1 串门 / §89 商城·背包·技能·进化·活动·多宠物）
  const [careTab, setCareTab] = useState<CareTab>('shop')
  const [carePending, setCarePending] = useState<string | null>(null)
  const [shopBalance, setShopBalance] = useState<number | null>(null)
  const [shopItems, setShopItems] = useState<PetShopItem[]>([])
  const [inventory, setInventory] = useState<PetInventoryItem[]>([])
  const [skills, setSkills] = useState<PetSkillItem[]>([])
  const [evolution, setEvolution] = useState<PetEvolutionStatus | null>(null)
  const [events, setEvents] = useState<PetEventItem[]>([])
  const [neighbors, setNeighbors] = useState<PetVisitNeighbor[]>([])
  // 访客日志（§6 今日访客）
  const [todayVisitors, setTodayVisitors] = useState<PetVisitorLog[]>([])
  const [myPets, setMyPets] = useState<PetSummary[]>([])
  const [careMessage, setCareMessage] = useState<string | null>(null)
  // B06 动作可执行性（随主宠刷新；拉取失败按"不可知"隐藏，不打断主流程）
  const [actions, setActions] = useState<PetActionItem[]>([])
  // B12 装备替换预览（按行切换显示替换增量；只读不写入）
  const [equipPreview, setEquipPreview] = useState<PetEquipPreview | null>(null)
  // 对战回合流水回放浮层（服务端计算，客户端只播放）
  const [roundsView, setRoundsView] = useState<{ rounds: BattleRound[]; won: boolean } | null>(null)
  // 聊天历史分页（cursor）
  const [chatCursor, setChatCursor] = useState<number | string | null>(null)
  const [chatHasMore, setChatHasMore] = useState(false)
  const [chatLoadingMore, setChatLoadingMore] = useState(false)

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

  /** 养成面板数据（按子页签惰性加载） */
  const loadCareData = useCallback(async (tab: CareTab) => {
    try {
      if (tab === 'shop') {
        const { data: res } = await petApi.getShop()
        if (res.success && res.data) {
          setShopBalance(res.data.balance)
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
        // 访客日志（§6）：随串门页签加载，失败静默
        petApi.listTodayVisitors()
          .then(({ data: r }) => { if (r.success) setTodayVisitors(r.data || []) })
          .catch(() => undefined)
      } else {
        const { data: res } = await petApi.listMyPets()
        if (res.success) setMyPets(res.data || [])
      }
    } catch (error) {
      toast(friendlyError(error))
    }
  }, [])

  /** 养成面板统一动作：服务端权威 → 提示 → 刷新面板与主宠状态 */
  const runCare = async (key: string, action: () => Promise<{ data: { success: boolean } }>, successText: string) => {
    setCarePending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        toast(successText)
        await loadCareData(careTab)
        refresh()
        // 喂食可能消耗了双倍权益（§6）
        if (key.startsWith('feed-')) {
          petApi.getLinkedProductEntitlement()
            .then(({ data: r }) => { if (r.success) setFeedDoubled(r.data?.usable === true) })
            .catch(() => undefined)
        }
      }
    } catch (error) {
      const code = (error as { code?: string }).code
      toast((code && CARE_ERROR_HINT[code]) || '请稍后再试')
    } finally {
      setCarePending(null)
    }
  }

  /** 实物商品联动（§6）：核销商城实物零食兑换码 → 双倍喂食权益 */
  const handleRedeem = async () => {
    if (redeeming || !redeemCode.trim()) {
      if (!redeemCode.trim()) toast('请输入兑换码')
      return
    }
    setRedeeming(true)
    try {
      const res = await petApi.redeemLinkedProduct(redeemCode.trim())
      if (res.data.success) {
        setFeedDoubled(res.data.data?.usable === true)
        setRedeemCode('')
        toast('核销成功！下一次喂食效果翻倍')
      }
    } catch (error) {
      const code = (error as { code?: string }).code
      toast(code === 'PET_VALIDATION_ERROR' ? '兑换码无效或已核销' : '核销失败，请稍后再试')
    } finally {
      setRedeeming(false)
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
    } catch (error) {
      toast(friendlyError(error))
    } finally {
      setChatLoadingMore(false)
    }
  }

  // 三期：亲密度概览（展示型数据，失败保持原值）
  const loadIntimacy = useCallback(async () => {
    try {
      const { data: res } = await petApi.getIntimacy()
      if (res.success && res.data) {
        setIntimacy(res.data)
      }
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
          const { data: res } = await petApi.companionHeartbeat(60)
          if (res.success && res.data) {
            // FE-02：心跳返回会话视图，亲密度面板另查 overview，禁止互相覆盖
            heartbeatFailuresRef.current = 0
            setCompanionWarn(false)
            await loadIntimacy()
          }
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
    if (pet) {
      void loadIntimacy()
    }
  }, [pet?.petId, loadIntimacy])

  /** B06 动作可执行性：喂食/玩耍等当前能否执行与原因，随主宠刷新 */
  const loadActions = useCallback(async () => {
    if (!pet) return
    try {
      const { data: res } = await petCompanionApi.getActions(pet.petId)
      if (res.success) setActions(res.data)
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

  /** B12 装备替换预览：同码再点收起 */
  const toggleEquipPreview = useCallback(async (itemCode: string) => {
    if (equipPreview?.itemCode === itemCode) {
      setEquipPreview(null)
      return
    }
    try {
      const { data: res } = await petCompanionApi.previewEquip(itemCode)
      if (res.success) setEquipPreview(res.data)
    } catch {
      toast('预览未成功，稍后再试')
    }
  }, [equipPreview?.itemCode])

  // R23/T36：加载代际——迟到的旧响应不覆盖新状态
  const refreshGenerationRef = useRef(0)
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
      }
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

  const loadPanelData = useCallback(async (key: PanelKey) => {
    try {
      if (key === 'chat' && !personaLoaded) {
        setPersonaLoaded(true)
        void (async () => {
          try {
            const { data: res } = await petApi.getChatPersona()
            if (res.success && res.data) {
              setPersona(res.data)
            }
          } catch {
            // 人设卡展示型数据：忽略
          }
        })()
      }
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
        const [opponentRes, historyRes] = await Promise.all([
          petApi.listOpponents(),
          petApi.listBattleHistory({ page: 1, pageSize: 10 }),
        ])
        if (opponentRes.data.success) setOpponents(opponentRes.data.data || [])
        if (historyRes.data.success) setHistory(historyRes.data.data || [])
      } else if (key === 'achievements') {
        // 成就墙 + 宠物动态（提醒即动态数据源，与 Web 端同一口径）
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
        // 实物商品联动（§6）：权益随养成页签刷新
        petApi.getLinkedProductEntitlement()
          .then(({ data: r }) => { if (r.success) setFeedDoubled(r.data?.usable === true) })
          .catch(() => undefined)
      } else if (key === 'rankings') {
        const { data: res } = await petApi.getRankings(rankingType)
        if (res.success) setRankings(res.data)
        // F2：赛季榜 + 历届名次惰性加载（失败静默）
        try {
          const [seasonRes, historyRes] = await Promise.all([
            petApi.getSeasonRanking(),
            petApi.getSeasonHistory(),
          ])
          if (seasonRes.data.success) setSeason(seasonRes.data.data)
          // 赛季通行证（§6）
          petApi.getSeasonPass()
            .then(({ data: r }) => { if (r.success) setSeasonPass(r.data) })
            .catch(() => undefined)
          if (historyRes.data.success) setSeasonHistory(historyRes.data.data || [])
        } catch {
          // 赛季为可选增强，失败不阻断排行榜
        }
      } else if (key === 'reminders') {
        const { data: res } = await petApi.listReminders()
        if (res.success) setReminders(res.data || [])
      }
    } catch (error) {
      toast(friendlyError(error))
    }
  }, [rankingType, careTab, loadCareData])

  useEffect(() => {
    if (currentUser) {
      refresh()
    }
  }, [currentUser, refresh])

  // P2-4：面板级缓存——记录每个面板最近一次加载时间，60s 内切回直接复用已有状态，
  // 不重复请求；动作后的 loadPanelData 直调与下拉刷新绕过该守卫（保持强制刷新语义）
  const lastPanelLoadAtRef = useRef<Partial<Record<PanelKey, number>>>({})
  const PANEL_CACHE_TTL_MS = 60_000
  const loadPanelDataIfStale = useCallback(async (key: PanelKey) => {
    const last = lastPanelLoadAtRef.current[key] ?? 0
    if (Date.now() - last < PANEL_CACHE_TTL_MS) {
      return
    }
    lastPanelLoadAtRef.current[key] = Date.now()
    await loadPanelData(key)
  }, [loadPanelData])

  useEffect(() => {
    if (pet && panel) loadPanelDataIfStale(panel)
  }, [panel, pet, loadPanelDataIfStale])

  // 提醒未读角标
  useEffect(() => {
    if (!pet) return
    petApi.getReminderUnreadCount()
      .then(({ data: res }) => { if (res.success) setReminderUnread(res.data ?? 0) })
      .catch(() => setReminderUnread(0))
  }, [pet, reminders])

  const activeRemaining = useCountdown(pet?.activityType ? pet.activityFinishedAt : null)
  const bottleRemaining = useCountdown(bottle?.fishing ? bottle.finishedAt : null)

  // 任务到期后自动拉一次，让"可领取"自然出现（用户不必手动刷新）
  const activityKey = pet?.activityFinishedAt ?? null
  useEffect(() => {
    if (!activityKey || activeRemaining > 0) return
    const target = parseUtc(activityKey)
    if (Number.isNaN(target) || target > Date.now()) return
    const timer = setTimeout(() => {
      refresh()
      panel && loadPanelData(panel)
    }, 1500)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activityKey, activeRemaining])

  // F8：聊天人设卡（进聊天面板时惰性拉取一次；数据与 AI prompt 同源）
  const [persona, setPersona] = useState<PetChatPersona | null>(null)
  const [personaLoaded, setPersonaLoaded] = useState(false)

  // F7：纪念日卡片（档案弹窗打开时惰性拉取，失败静默）
  const [anniversary, setAnniversary] = useState<PetAnniversary | null>(null)
  const openProfile = useCallback(() => {
    if (!pet) return
    setProfileName(pet.name)
    setProfileOwnerTitle(pet.ownerTitle ?? '主人')
    const appearance = parseResult<{ color?: string; accessory?: string }>(pet.appearance)
    setProfileColor(appearance?.color ?? PET_COLORS[0])
    setProfileAccessory(appearance?.accessory ?? 'none')
    setProfileOpen(true)
    void (async () => {
      try {
        const { data: res } = await petApi.getAnniversaries()
        if (res.success && res.data) {
          setAnniversary(res.data)
        }
      } catch {
        // 展示型数据：忽略
      }
    })()
  }, [pet])

  // 微信 web-view 舞台页的实时意图回落：navigateTo('/pages/pet/index?intent=feed')
  useEffect(() => {
    const intent = params.intent
    if (!intent || !pet) return
    setPanel('home')
    if (intent === 'feed' || intent === 'play' || intent === 'clean' || intent === 'rest') {
      void runInteraction(intent)
    } else if (intent === 'openProfile') {
      openProfile()
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
  }, [params.intent, pet, openProfile])

  // F1：长按喂食打开背包食物选择（ActionSheet 展示 FOOD 数量，选中即消耗并恢复状态）
  const openFoodPicker = async () => {
    try {
      const { data: res } = await petApi.listInventory()
      const foods = (res.data || []).filter(
        (it) => it.itemType === 'FOOD' && (it.quantity ?? 0) > 0,
      )
      if (!res.success || foods.length === 0) {
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
        if (feedRes.success && feedRes.data) {
          setPet(feedRes.data)
          toast('好嘞！')
        }
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
    setActing(true)
    try {
      const { data: res } =
        action === 'feed' ? await petApi.feed()
          : action === 'play' ? await petApi.play()
            : action === 'clean' ? await petApi.clean()
              : await petApi.rest()
      if (res.success && res.data) {
        setPet(res.data)
        toast('好嘞！')
      }
    } catch (error) {
      toast(friendlyError(error))
    } finally {
      // P1-6：300ms 节流窗口，防手速极限连点（请求已返回后仍短暂锁定）
      setTimeout(() => setActing(false), 300)
    }
  }

  const adopt = async () => {
    if (!adoptName.trim()) {
      toast('先给宠物取个名字吧')
      return
    }
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
        Taro.showToast({ title: '领养成功！', icon: 'success' })
        setAdoptName('')
        refresh()
      }
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  const openStage = () => {
    // H5 为 iframe 完整桥；微信小程序 web-view 只做观赏+状态展示（操作回本页）
    Taro.navigateTo({ url: '/pages/petStage/index' })
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
      refresh()
    } catch (error) {
      // 改名冷却时外观已保存，仅提示剩余改动
      toast(friendlyError(error))
    }
  }

  const togglePrivacy = async (next: boolean) => {
    if (!pet) return
    setPrivacyBusy(true)
    try {
      await petApi.updatePrivacy({ isPublic: next })
      setPet({ ...pet, isPublic: next })
    } catch (error) {
      toast(friendlyError(error))
    } finally {
      setPrivacyBusy(false)
    }
  }

  const openBottleResult = async () => {
    try {
      const { data: res } = await petApi.claimBottle()
      if (!res.success) return
      const result = parseResult<PetBottleResult>(res.data.result)
      await loadPanelData('bottle')
      await refresh()
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
  }

  /** 对战结果：回合流水由服务端生成，此处只做文字回放（Cocos 舞台另有动画演出） */
  const presentBattle = (battle: PetBattleItem) => {
    if (battle.status !== 'FINISHED') {
      toast('已发起挑战，等对方应战')
      return
    }
    const rounds = parseResult<BattleRound[]>(battle.rounds)
    const myPetId = battle.role === 'ATTACKER' ? battle.attackerPetId : battle.defenderPetId
    const won = String(battle.winnerPetId) === String(myPetId)
    if (!rounds || rounds.length === 0) {
      toast(won ? '对战胜利！' : '对战惜败，下次再战')
      return
    }
    // 回合流水回放浮层（服务端计算，客户端只展示）
    setRoundsView({ rounds, won })
  }

  const loadShareCard = async (type: Parameters<typeof petApi.getShareCard>[0]) => {
    try {
      const { data: res } = await petApi.getShareCard(type)
      if (res.success && res.data) setShareCard(res.data)
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  const copyShare = () => {
    if (!shareCard) return
    Taro.setClipboardData({
      data: `${shareCard.title}\n${shareCard.content}${shareCard.highlight ? `\n${shareCard.highlight}` : ''}`,
      success: () => toast('已复制，去发布页分享吧'),
    })
  }

  const markReminderRead = async (item: PetReminder) => {
    if (item.isRead) return
    try {
      await notificationApi.markRead(item.notificationId)
      setReminders((prev) => prev.map((r) => (r.notificationId === item.notificationId ? { ...r, isRead: true } : r)))
      setReminderUnread((n) => Math.max(0, n - 1))
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  const markAllRemindersRead = async () => {
    try {
      // 宠物提醒与站内通知是两条链路：此处必须走 /pet/reminders/read-all
      await petCompanionApi.markAllRemindersRead()
      setReminders((prev) => prev.map((r) => ({ ...r, isRead: true })))
      setReminderUnread(0)
    } catch (error) {
      toast(friendlyError(error))
    }
  }

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
    return (
      <View className={`${styles.page} ${dataTheme}`} style={{ ...themeStyle, ...PET_CREAM_STYLE, ...PET_CREAM_EXT_STYLE }}>
        <CustomNavBar title="我的宠物" />
        <ScrollView scrollY className={styles.adoptScroll} style={{ paddingTop: statusBarHeight + navBarHeight }}>
          <Text className={styles.adoptTitle}>🐾 领养一只属于你的宠物</Text>
          <Text className={styles.adoptDesc}>陪你打工、读书、捞漂流瓶、聊天</Text>
          <View className={styles.speciesRow}>
            {SPECIES_OPTIONS.map((option) => (
              <View
                key={option.value}
                className={`${styles.speciesCard} ${adoptSpecies === option.value ? styles.speciesActive : ''}`}
                onClick={() => setAdoptSpecies(option.value)}
              >
                <Text className={styles.speciesEmoji}>{option.emoji}</Text>
                <Text className={styles.speciesLabel}>{option.label}</Text>
              </View>
            ))}
          </View>
          <Text className={styles.sectionTitle}>性别</Text>
          <View className={styles.personalityRow}>
            {GENDER_OPTIONS.map((option) => (
              <View
                key={option.value}
                className={`${styles.personalityChip} ${styles.genderChip} ${adoptGender === option.value ? styles.personalityActive : ''}`}
                onClick={() => setAdoptGender(option.value)}
              >
                <Text>{option.label}</Text>
              </View>
            ))}
          </View>
          <View className={styles.personalityRow}>
            {PERSONALITY_OPTIONS.map((option) => (
              <View
                key={option.value}
                className={`${styles.personalityChip} ${adoptPersonality === option.value ? styles.personalityActive : ''}`}
                onClick={() => setAdoptPersonality(option.value)}
              >
                <Text>{option.label}</Text>
              </View>
            ))}
          </View>
          <Text className={styles.sectionTitle}>外观颜色</Text>
          <View className={styles.personalityRow}>
            {PET_COLORS.map((color) => (
              <View
                key={color}
                className={`${styles.personalityChip} ${adoptColor === color ? styles.personalityActive : ''}`}
                onClick={() => setAdoptColor(color)}
              >
                <Text>{color}</Text>
              </View>
            ))}
          </View>
          <Text className={styles.sectionTitle}>配饰</Text>
          <View className={styles.personalityRow}>
            {ACCESSORIES.map((item) => (
              <View
                key={item.key}
                className={`${styles.personalityChip} ${adoptAccessory === item.key ? styles.personalityActive : ''}`}
                onClick={() => setAdoptAccessory(item.key)}
              >
                <Text>{item.label}</Text>
              </View>
            ))}
          </View>
          <Text className={styles.sectionTitle}>名字</Text>
          <Input
            className={styles.nameInput}
            value={adoptName}
            maxlength={12}
            placeholder="给它取个名字（1-12 字）"
            onInput={(e) => setAdoptName(e.detail.value)}
          />
          <Button className={styles.primaryBtn} onClick={adopt}>领养它</Button>
        </ScrollView>
      </View>
    )
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
          <CreamButton disabled={acting} onClick={() => runInteraction('feed')} onLongPress={() => void openFoodPicker()}>🍖 喂食</CreamButton>
          <CreamButton disabled={acting} onClick={() => runInteraction('play')}>🎾 玩耍</CreamButton>
          <CreamButton disabled={acting} onClick={() => runInteraction('clean')}>🫧 清洁</CreamButton>
          <CreamButton disabled={acting} onClick={() => runInteraction('rest')}>💤 休息</CreamButton>
        </View>
        <View className={cream.actionsRow}>
          <CreamButton variant="ghost" onClick={openProfile}>🎀 档案</CreamButton>
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
                    await openBottleResult()
                  }
                  panel && loadPanelData(panel)
                  refresh()
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
                return (
                  <View key={key} className={cream.menuItem} onClick={() => setPanel(key)}>
                    <Text className={cream.menuItemEmoji}>{meta.emoji}</Text>
                    <Text className={cream.menuItemLabel}>
                      {meta.label}{key === 'reminders' && reminderUnread > 0 ? `(${reminderUnread})` : ''}
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
          {panel === 'daily' && <DailyQuestPanel onRefresh={refresh} />}
        {panel === 'social' && <SocialPanel pet={pet} onRefresh={refresh} />}
        {panel === 'memory' && <MemoryPanel petId={pet?.petId ?? null} onRefresh={refresh} />}
        {panel === 'home' && (
            <View className={styles.tips}>
              {/* 三期：家园（房间布置 / 家具 / 拜访） */}
              <HomePanel onRefresh={refresh} />
              <Text className={styles.tip}>🍖 饱食和清洁随时间下降，记得回来照顾它</Text>
              <Text className={styles.tip}>💼 打工赚宠物币，📚 读书涨智力</Text>
              <Text className={styles.tip}>🍾 宠物会定时帮你捞社区漂流瓶并主动提醒</Text>
              <Text className={styles.tip}>⚔️ 对战由服务端计算，输了也有经验</Text>
              <Text className={styles.tip}>💬 和它聊聊天，它会记住你的喜好哦</Text>
              <View className={styles.modalRow}>
                <Text className={styles.modalLabel}>在个人主页展示宠物</Text>
                <CreamToggle on={pet.isPublic} disabled={privacyBusy} onChange={() => void togglePrivacy(!pet.isPublic)} />
              </View>
              <Text className={styles.tip}>关闭后不会进入宠物榜单，其他用户也看不到它</Text>
              <Text className={styles.sectionTitle}>宠物动态卡片</Text>
              <View className={styles.optionRow}>
                {SHARE_TYPES.map((item) => (
                  <View
                    key={item.key}
                    className={styles.optionChip}
                    onClick={() => loadShareCard(item.key)}
                  >
                    <Text>{item.label}</Text>
                  </View>
                ))}
              </View>
            </View>
          )}

          {panel === 'work' && (
            <View className={styles.jobList}>
              {jobs.map((job) => (
                <View key={String(job.configId)} className={styles.jobCard}>
                  <View className={styles.jobInfo}>
                    <Text className={styles.jobName}>{job.name}</Text>
                    <Text className={styles.jobMeta}>⏱ {Math.round(job.durationSeconds / 60)} 分钟 · ⚡-{job.energyCost}</Text>
                    <View className={cream.rewardRow}>
                      <Text className={cream.rewardChip}>{`经验 +${job.expReward}`}</Text>
                      <Text className={cream.rewardChip}>{`宠物币 +${job.currencyReward}`}</Text>
                    </View>
                  </View>
                  {job.eligible ? (
                    <Button className={styles.miniBtn} onClick={async () => {
                      try {
                        const { data: res } = await petApi.startWork(job.configId)
                        if (res.success) { toast('开工啦！'); loadPanelData('work'); refresh() }
                      } catch (error) { toast(friendlyError(error)) }
                    }}>接单</Button>
                  ) : (
                    <Text className={styles.locked}>Lv.{job.requiredLevel}</Text>
                  )}
                </View>
              ))}
              {pet.claimableActivityType === 'WORK' && (
                <Button className={styles.primaryBtn} onClick={async () => {
                  try {
                    const { data: res } = await petApi.claimWork()
                    if (res.success) { toast('奖励已领取！'); loadPanelData('work'); refresh() }
                  } catch (error) { toast(friendlyError(error)) }
                }}>领取打工奖励</Button>
              )}
            </View>
          )}

          {panel === 'study' && (
            <View className={styles.jobList}>
              {studies.map((study) => (
                <View key={String(study.configId)} className={styles.jobCard}>
                  <View className={styles.jobInfo}>
                    <Text className={styles.jobName}>{study.name}</Text>
                    <Text className={styles.jobMeta}>⏱ {Math.round(study.durationSeconds / 60)} 分钟</Text>
                    <View className={cream.rewardRow}>
                      <Text className={cream.rewardChip}>{`经验 +${study.expReward}`}</Text>
                      <Text className={cream.rewardChip}>{`智力 +${study.intelligenceReward}`}</Text>
                    </View>
                  </View>
                  {study.eligible ? (
                    <Button className={styles.miniBtn} onClick={async () => {
                      try {
                        const { data: res } = await petApi.startStudy(study.configId)
                        if (res.success) { toast('开始读书啦'); loadPanelData('study'); refresh() }
                      } catch (error) { toast(friendlyError(error)) }
                    }}>上课</Button>
                  ) : (
                    <Text className={styles.locked}>Lv.{study.requiredLevel}</Text>
                  )}
                </View>
              ))}
              {pet.claimableActivityType === 'STUDY' && (
                <Button className={styles.primaryBtn} onClick={async () => {
                  try {
                    const { data: res } = await petApi.claimStudy()
                    if (res.success) { toast('学习奖励已领取！'); loadPanelData('study'); refresh() }
                  } catch (error) { toast(friendlyError(error)) }
                }}>领取学习奖励</Button>
              )}
            </View>
          )}

          {panel === 'bottle' && bottle && (
            <View className={styles.bottlePanel}>
              <Text className={styles.tip}>🌊 捞瓶区域：{bottle.unlockedArea}</Text>
              <Text className={styles.tip}>估算成功率 {Math.round(bottle.estimatedSuccessRate * 100)}%（敏捷与等级加成）</Text>
              {bottle.fishing ? (
                <Text className={styles.tip}>🐾 捞瓶中…剩余 {formatClock(bottleRemaining)}</Text>
              ) : (
                <Button
                  className={styles.primaryBtn}
                  disabled={bottle.cooldownRemainingSeconds > 0 && !bottle.canClaim}
                  onClick={async () => {
                    if (bottle.canClaim) {
                      await openBottleResult()
                      return
                    }
                    try {
                      const { data: res } = await petApi.startBottle()
                      if (res.success) {
                        toast('宠物出发啦！30 分钟后回来看结果')
                        loadPanelData('bottle')
                        refresh()
                      }
                    } catch (error) { toast(friendlyError(error)) }
                  }}
                >
                  {bottle.canClaim ? '查看捞瓶结果' : bottle.cooldownRemainingSeconds > 0 ? `冷却中 ${Math.ceil(bottle.cooldownRemainingSeconds / 60)} 分钟` : '让宠物去捞漂流瓶（30 分钟）'}
                </Button>
              )}
              {bottle.lastOutcome === 'EMPTY' && <Text className={styles.tip}>这次空手而归啦，休息一下再试试～</Text>}
              {bottle.lastOutcome === 'FAILED' && <Text className={styles.tip}>心愿服务暂时不可用，点上面的按钮可以重试领取</Text>}
            </View>
          )}

          {panel === 'battle' && (
            <View className={styles.battlePanel}>
              {history.filter((battle) => battle.status === 'PENDING' && battle.role === 'DEFENDER').map((battle) => (
                <View key={String(battle.battleId)} className={styles.pendingRow}>
                  <Text className={styles.tip}>⚔️ 收到 {battle.attackerPetName ?? `宠物 #${battle.attackerPetId}`} 的挑战</Text>
                  <View className={styles.pendingBtns}>
                    <Button className={styles.miniBtn} onClick={async () => {
                      try {
                        const { data: res } = await petApi.acceptBattle(battle.battleId)
                        if (res.success) { presentBattle(res.data); loadPanelData('battle'); refresh() }
                      } catch (error) { toast(friendlyError(error)) }
                    }}>应战</Button>
                    <Button className={styles.miniBtnGhost} onClick={async () => {
                      try {
                        await petApi.declineBattle(battle.battleId)
                        toast('已婉拒')
                        loadPanelData('battle')
                      } catch (error) { toast(friendlyError(error)) }
                    }}>婉拒</Button>
                  </View>
                </View>
              ))}
              <Text className={styles.sectionTitle}>选择对手</Text>
              <View className={styles.opponentGrid}>
                {opponents.map((opponent) => (
                  <View key={`${String(opponent.petId)}-${opponent.name}`} className={styles.opponentCard}
                    onClick={async () => {
                      try {
                        const { data: res } = await petApi.challenge(opponent.isWild ? { mode: 'PVE' } : { mode: 'PVP', defenderPetId: opponent.petId })
                        if (res.success) {
                          presentBattle(res.data)
                          loadPanelData('battle')
                          refresh()
                        }
                      } catch (error) { toast(friendlyError(error)) }
                    }}
                  >
                    <Text className={styles.opponentEmoji}>{SPECIES_EMOJI[opponent.species] || '🐾'}</Text>
                    <Text className={styles.opponentName}>{opponent.name}</Text>
                    <Text className={styles.opponentMeta}>Lv.{opponent.level} · {opponent.isWild ? '野生' : opponent.ownerNickname}</Text>
                  </View>
                ))}
              </View>
              <Text className={styles.sectionTitle}>最近对战</Text>
              {history.filter((battle) => battle.status !== 'PENDING').slice(0, 5).map((battle) => {
                const opponent = battle.role === 'ATTACKER' ? battle.defenderPetName : battle.attackerPetName
                const won = battle.winnerPetId === (battle.role === 'ATTACKER' ? battle.attackerPetId : battle.defenderPetId)
                return (
                  <View key={String(battle.battleId)} className={styles.historyRow} onClick={async () => {
                    try {
                      const { data: res } = await petApi.getBattleDetail(battle.battleId)
                      if (res.success && res.data) presentBattle(res.data)
                    } catch (error) { toast(friendlyError(error)) }
                  }}>
                    <Text className={styles.historyText}>
                      {battle.status === 'FINISHED' ? (won ? '🏆 胜利' : '💧 战败') : battle.status === 'DECLINED' ? '🚫 被婉拒' : '⌛ 未应战'}
                      {' · '}{opponent ?? '对手'}
                    </Text>
                    <Text className={styles.historyMeta}>+{battle.expReward} 经验</Text>
                  </View>
                )
              })}
            </View>
          )}

          {panel === 'chat' && (
            <View className={styles.chatPanel}>
              {persona && (
                <View className={styles.chatPersonaCard}>
                  <Text className={styles.chatPersonaTitle}>
                    {persona.name} · {persona.intimacyLevelName}（Lv.{persona.intimacyLevel}）
                  </Text>
                  <Text className={styles.chatPersonaText}>
                    {persona.phrase ? `「${persona.phrase}」` : ''}
                    {persona.careerName ? ` · 现任${persona.careerName}` : ''}
                  </Text>
                </View>
              )}
              {chatHasMore && (
                <View className={styles.chatMoreRow} onClick={loadMoreChat}>
                  <Text className={styles.chatMoreText}>{chatLoadingMore ? '加载中…' : '加载更早的对话'}</Text>
                </View>
              )}
              <View className={styles.chatList}>
                {chatMessages.map((item) => (
                  <View key={String(item.messageId)} className={item.role === 'USER' ? styles.chatMine : styles.chatPet}>
                    <Text className={styles.chatText}>{item.content}</Text>
                    {/* AI 降级标识：模板回复（isAiReply=false）时提示，避免用户以为宠物"变笨了" */}
                    {item.role === 'PET' && !item.isAiReply && (
                      <Text className={styles.chatTag}>模板回复</Text>
                    )}
                  </View>
                ))}
              </View>
              <View className={styles.chatInputRow}>
                <Input
                  className={styles.chatInput}
                  value={chatInput}
                  maxlength={500}
                  placeholder="跟宠物聊聊…（每日 20 条）"
                  onInput={(e) => setChatInput(e.detail.value)}
                />
                <Button className={styles.miniBtn} onClick={async () => {
                  const text = chatInput.trim()
                  if (!text) return
                  setChatInput('')
                  try {
                    const { data: res } = await petApi.chat(text)
                    if (res.success && res.data) {
                      setChatMessages((prev) => [...prev, {
                        messageId: `${res.data.messageId}-u`, role: 'USER', content: text, isAiReply: false, createdAt: null,
                      }, res.data])
                    }
                  } catch (error) { toast(friendlyError(error)) }
                }}>发送</Button>
              </View>
            </View>
          )}

          {panel === 'rankings' && (
            <View className={styles.rankList}>
              {/* 赛季通行证（§6）：入口卡（点击进通行证页） */}
              {seasonPass && seasonPass.seasonId != null && (
                <View className={styles.passEntryCard} onClick={() => Taro.navigateTo({ url: '/pages/seasonPass/index' })}>
                  <View className={styles.passEntryInfo}>
                    <Text className={styles.passEntryTitle}>🎟️ 赛季通行证</Text>
                    <Text className={styles.passEntryExp}>经验 {seasonPass.passExp} · 完成每日任务累积</Text>
                  </View>
                  <Text className={styles.passEntryArrow}>›</Text>
                </View>
              )}
              {season?.season && (
                <View className={styles.seasonCard}>
                  <Text className={styles.seasonTitle}>
                    🏆 {season.season.name}（{season.season.startsAt} ~ {season.season.endsAt}）
                  </Text>
                  {season.top50.slice(0, 3).map((item) => (
                    <Text key={String(item.petId)} className={styles.seasonRow}>
                      {['🥇', '🥈', '🥉'][item.rank - 1]} {item.name} Lv.{item.level}
                    </Text>
                  ))}
                  {season.myRank != null && (
                    <Text className={styles.seasonRow}>我的实时名次：第 {season.myRank} 名</Text>
                  )}
                  {seasonHistory.length > 0 && (
                    <Text className={styles.seasonRow}>
                      历届最好成绩：第 {Math.min(...seasonHistory.map((h) => h.rankNo))} 名
                    </Text>
                  )}
                </View>
              )}
              <View className={cream.metaRow}>
                {RANKING_TABS.map((tab) => (
                  <View
                    key={tab.key}
                    className={`${cream.tab} ${rankingType === tab.key ? cream.tabActive : ""}`}
                    onClick={() => setRankingType(tab.key)}
                  >
                    <Text>{tab.label}</Text>
                  </View>
                ))}
              </View>
              {rankings?.top20.map((item) => (
                <View key={String(item.petId)} className={`${styles.rankRow} ${item.isMe ? styles.rankRowMe : ''}`}>
                  <Text className={styles.rankNo}>
                    {item.rank <= 3 ? ['🥇', '🥈', '🥉'][item.rank - 1] : '#' + item.rank}
                  </Text>
                  <Text className={styles.rankName}>
                    {SPECIES_EMOJI[item.species] || '🐾'} {item.name}
                    <Text className={styles.rankOwner}> @{item.ownerNickname}</Text>
                  </Text>
                  <Text className={styles.rankValue}>{rankingType === 'LEVEL' ? `Lv.${item.value}` : item.value}</Text>
                </View>
              ))}
              {rankings?.myRank != null && (
                <Text className={styles.tip}>我的成绩 {rankings.myValue} · 全服第 {rankings.myRank} 名</Text>
              )}
            </View>
          )}

          {panel === 'reminders' && (
            <View>
              <View className={styles.modalRow}>
                <Text className={styles.modalLabel}>宠物想对你说（{reminderUnread} 条未读）</Text>
                <Button className={styles.miniBtnGhost} onClick={markAllRemindersRead}>全部已读</Button>
              </View>
              {reminders.length === 0 && (
                <Text className={styles.tip}>还没有提醒～宠物会在打工完成、捞到漂流瓶、有人评论你时主动开口</Text>
              )}
              {reminders.map((item) => (
                <View
                  key={String(item.notificationId)}
                  className={`${styles.reminderCard} ${item.isRead ? styles.reminderRead : styles.reminderUnread}`}
                  onClick={() => markReminderRead(item)}
                >
                  <View className={styles.reminderTitleRow}>
                    <Text className={styles.reminderTitle}>{item.title}</Text>
                    <Text className={`${styles.priorityTag} ${item.priority === 'P0' ? styles.priorityP0 : item.priority === 'P1' ? styles.priorityP1 : ''}`}>
                      {PRIORITY_LABEL[item.priority ?? 'P2']}
                    </Text>
                  </View>
                  <Text className={styles.reminderContent}>{item.content}</Text>
                </View>
              ))}
            </View>
          )}

          {panel === 'care' && (
            <View className={styles.carePanel}>
              <View className={cream.metaRow}>
                {CARE_TABS.map((tab) => (
                  <View
                    key={tab.key}
                    className={`${cream.tab} ${careTab === tab.key ? cream.tabActive : ""}`}
                    onClick={() => setCareTab(tab.key)}
                  >
                    <Text>{tab.label}</Text>
                  </View>
                ))}
              </View>
              {careMessage && <Text className={styles.tip}>{careMessage}</Text>}

              {careTab === 'shop' && (
                <View>
                  <Text className={styles.tip}>
                    🪙 宠物币余额：{shopBalance === null ? '暂不可用' : shopBalance}
                  </Text>
                  {shopItems.map((item) => (
                    <View key={`${item.itemType}-${item.code}`} className={styles.careCard}>
                      <Text className={styles.careTitle}>
                        {item.icon} {item.name} · {ITEM_TYPE_LABEL[item.itemType]}
                        {item.slot ? ` · ${SLOT_LABEL[item.slot] ?? item.slot}` : ''}
                      </Text>
                      <Text className={styles.careMeta}>
                        需要 Lv.{item.requiredLevel}
                        {item.requiredEvolutionStage > 0 ? ` · 进化${item.requiredEvolutionStage}阶` : ''}
                        {' · '}✨{item.priceStarlight}
                      </Text>
                      <View className={styles.careActions}>
                        {item.owned ? (
                          <Text className={styles.careMeta}>已拥有</Text>
                        ) : (
                          <Button
                            className={styles.miniBtn}
                            disabled={!item.eligible || carePending === `buy-${item.code}`}
                            onClick={() => runCare(`buy-${item.code}`, () => petApi.buyItem({ itemType: item.itemType, itemCode: item.code }), '购买成功！')}
                          >
                            {item.eligible ? '购买' : (item.lockReason || '未解锁')}
                          </Button>
                        )}
                      </View>
                    </View>
                  ))}
                </View>
              )}

              {careTab === 'inventory' && (
                <View>
                  {/* 实物商品联动（§6）：双倍喂食权益 + 兑换码核销 */}
                  <View className={styles.linkedProductCard}>
                    <Text className={styles.linkedProductBadge}>
                      {feedDoubled ? '🎁 双倍喂食权益已就绪（下次喂食效果 x2）' : '🐱 买实物零食可兑换双倍喂食'}
                    </Text>
                    <View className={styles.redeemRow}>
                      <Input
                        className={styles.redeemInput}
                        placeholder='输入兑换码'
                        value={redeemCode}
                        onInput={(e) => setRedeemCode(e.detail.value)}
                        maxlength={64}
                      />
                      <View className={styles.redeemBtn} onClick={redeeming ? undefined : handleRedeem}>
                        <Text className={styles.redeemBtnText}>{redeeming ? '核销中...' : '核销'}</Text>
                      </View>
                    </View>
                  </View>
                  {inventory.length === 0 && <Text className={styles.tip}>背包还是空的，去商城逛逛吧～</Text>}
                  {inventory.map((item) => (
                    <View key={`${item.itemType}-${item.code}`} className={styles.careCard}>
                      <Text className={styles.careTitle}>{item.icon} {item.name}</Text>
                      <Text className={styles.careMeta}>
                        {ITEM_TYPE_LABEL[item.itemType]}{item.slot ? ` · ${SLOT_LABEL[item.slot] ?? item.slot}` : ''}
                        {item.equipped ? ' · 使用中' : ''}{item.used ? ' · 已学习' : ''}
                      </Text>
                      <View className={styles.careActions}>
                        {item.itemType === 'EQUIPMENT' && (item.equipped ? (
                          <>
                            <Button className={styles.miniBtnGhost} onClick={() => runCare(`unequip-${item.slot}`, () => petApi.unequipItem(item.slot ?? ''), '已卸下')}>卸下</Button>
                            <Button className={styles.miniBtnGhost} onClick={() => void toggleEquipPreview(item.code)}>
                              {equipPreview?.itemCode === item.code ? '收起预览' : '预览'}
                            </Button>
                          </>
                        ) : (
                          <>
                            <Button className={styles.miniBtn} onClick={() => runCare(`equip-${item.code}`, () => petApi.equipItem(item.code), '已穿戴')}>穿戴</Button>
                            <Button className={styles.miniBtnGhost} onClick={() => void toggleEquipPreview(item.code)}>
                              {equipPreview?.itemCode === item.code ? '收起预览' : '预览'}
                            </Button>
                          </>
                        ))}
                        {item.itemType === 'SKIN' && (item.equipped ? (
                          <Button className={styles.miniBtnGhost} onClick={() => runCare('remove-skin', () => petApi.removeSkin(), '已换回原生外观')}>卸下</Button>
                        ) : (
                          <Button className={styles.miniBtn} onClick={() => runCare(`skin-${item.code}`, () => petApi.wearSkin(item.code), '已穿上新皮肤')}>穿戴</Button>
                        ))}
                        {item.itemType === 'FOOD' && (
                          <Button className={styles.miniBtn} onClick={() => runCare(`feed-${item.code}`, () => petApi.feedItem(item.code), '吃掉了，状态好多了')}>喂食</Button>
                        )}
                      </View>
                      {item.itemType === 'EQUIPMENT' && equipPreview?.itemCode === item.code && (
                        <Text className={styles.careMeta}>
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
                <View>
                  {skills.map((skill) => (
                    <View key={skill.code} className={styles.careCard}>
                      <Text className={styles.careTitle}>
                        {skill.icon} {skill.name} · {skill.skillType === 'ACTIVE' ? '主动技' : '被动技'}
                      </Text>
                      <Text className={styles.careMeta}>{skill.effectText} · ✨{skill.priceStarlight} · 需要 Lv.{skill.requiredLevel}</Text>
                      <Text className={styles.careMeta}>{skill.description}</Text>
                      <View className={styles.careActions}>
                        {skill.learned ? (
                          <Text className={styles.careMeta}>已学会</Text>
                        ) : skill.bookOwned ? (
                          <Button
                            className={styles.miniBtn}
                            disabled={carePending === `learn-${skill.code}`}
                            onClick={() => runCare(`learn-${skill.code}`, () => petApi.learnSkill(skill.code), '学会新技能啦！')}
                          >
                            学习
                          </Button>
                        ) : (
                          <Text className={styles.careMeta}>需要技能书（商城购买）</Text>
                        )}
                      </View>
                    </View>
                  ))}
                </View>
              )}

              {careTab === 'evolution' && evolution && (
                <View>
                  <Text className={styles.tip}>当前进化阶段：{evolution.currentStage} / {evolution.maxStage}</Text>
                  {evolution.nextCode === null ? (
                    <Text className={styles.tip}>🎉 已经进化到最高阶段啦</Text>
                  ) : (
                    <View className={styles.careCard}>
                      <Text className={styles.careTitle}>{evolution.icon} {evolution.nextName}</Text>
                      <Text className={styles.careMeta}>{evolution.nextDescription}</Text>
                      <Text className={styles.careMeta}>
                        需要 Lv.{evolution.requiredLevel} · 消耗 ✨{evolution.costStarlight} · 生命+{evolution.bonusMaxHp}
                        力量+{evolution.bonusStrength} 智力+{evolution.bonusIntelligence} 敏捷+{evolution.bonusAgility} 魅力+{evolution.bonusCharm}
                      </Text>
                      <View className={styles.careActions}>
                        <Button
                          className={styles.miniBtn}
                          disabled={!evolution.canEvolve || carePending === 'evolve'}
                          onClick={() => runCare('evolve', () => petApi.evolve(), '进化成功！')}
                        >
                          {evolution.canEvolve ? '进化' : (evolution.lockReason || '条件未满足')}
                        </Button>
                      </View>
                    </View>
                  )}
                </View>
              )}

              {careTab === 'events' && (
                <View>
                  {events.length === 0 && <Text className={styles.tip}>暂时没有进行中的活动</Text>}
                  {events.map((event) => (
                    <View key={event.code} className={styles.careCard}>
                      <Text className={styles.careTitle}>🎯 {event.name}</Text>
                      <Text className={styles.careMeta}>
                        {EVENT_TYPE_LABEL[event.eventType] ?? event.eventType} 进度 {event.progress}/{event.targetValue}
                        {' · '}奖励 经验+{event.rewardExp} ✨+{event.rewardStarlight}
                      </Text>
                      <View className={styles.careActions}>
                        {event.claimed ? (
                          <Text className={styles.careMeta}>已领取</Text>
                        ) : event.expired ? (
                          <Text className={styles.careMeta}>已结束</Text>
                        ) : (
                          <Button
                            className={styles.miniBtn}
                            disabled={!event.claimable || carePending === `event-${event.code}`}
                            onClick={() => runCare(`event-${event.code}`, () => petApi.claimEvent(event.code), '奖励到手啦！')}
                          >
                            {event.claimable ? '领取奖励' : `还差 ${Math.max(0, event.targetValue - event.progress)} 次`}
                          </Button>
                        )}
                      </View>
                    </View>
                  ))}
                </View>
              )}

              {careTab === 'visit' && (
                <View>
                  {/* 访客日志（§6 今日访客） */}
                  <View className={styles.visitorLogBox}>
                    <Text className={styles.visitorLogTitle}>👣 今日访客（{todayVisitors.length}）</Text>
                    {todayVisitors.length === 0 ? (
                      <Text className={styles.tip}>今天还没有小伙伴来串门</Text>
                    ) : (
                      todayVisitors.map((v) => (
                        <Text key={`${v.visitorUserId}-${v.visitorPetId}`} className={styles.visitorLogItem}>
                          {v.visitorNickname} 的 {v.visitorPetName} 来串过门啦
                        </Text>
                      ))
                    )}
                  </View>
                  {neighbors.length === 0 && <Text className={styles.tip}>暂时没有可串门的邻居</Text>}
                  {neighbors.map((neighbor) => (
                    <View key={String(neighbor.petId)} className={styles.careCard}>
                      <Text className={styles.careTitle}>
                        {SPECIES_EMOJI[neighbor.species] || '🐾'} {neighbor.name}
                      </Text>
                      <Text className={styles.careMeta}>
                        Lv.{neighbor.level} · {neighbor.ownerNickname}
                        {neighbor.evolutionStage > 0 ? ` · 进化${neighbor.evolutionStage}阶` : ''}
                      </Text>
                      <View className={styles.careActions}>
                        {neighbor.visitedToday ? (
                          <Text className={styles.careMeta}>今日已去过</Text>
                        ) : (
                          <Button
                            className={styles.miniBtn}
                            disabled={carePending === `visit-${neighbor.petId}`}
                            onClick={() => runCare(`visit-${neighbor.petId}`, async () => {
                              const res = await petApi.visitNeighbor(neighbor.petId)
                              if (res.data.success && res.data.data) setCareMessage(res.data.data.message)
                              return res
                            }, '串门成功！')}
                          >
                            去串门
                          </Button>
                        )}
                      </View>
                    </View>
                  ))}
                </View>
              )}

              {careTab === 'career' && <CareerPanel onRefresh={refresh} />}
        {careTab === 'pets' && (
                <View>
                  <Text className={styles.tip}>宠物 {pet.petCount} / {pet.maxPets}（日常玩法作用于主宠）</Text>
                  {myPets.map((item) => (
                    <View key={String(item.petId)} className={styles.careCard}>
                      <Text className={styles.careTitle}>
                        {SPECIES_EMOJI[item.species] || '🐾'} {item.name}{item.isActive ? ' · 主宠' : ''}
                      </Text>
                      <Text className={styles.careMeta}>
                        Lv.{item.level} · {GROWTH_STAGE_LABEL[item.growthStage] ?? item.growthStage}
                        {item.evolutionStage > 0 ? ` · 进化${item.evolutionStage}阶` : ''}
                      </Text>
                      <View className={styles.careActions}>
                        {!item.isActive && (
                          <Button
                            className={styles.miniBtn}
                            disabled={carePending === `activate-${item.petId}`}
                            onClick={() => runCare(`activate-${item.petId}`, () => petApi.activatePet(item.petId), `已切换为 ${item.name}`)}
                          >
                            设为主宠
                          </Button>
                        )}
                      </View>
                    </View>
                  ))}
                  {pet.petCount < pet.maxPets && (
                    <Button className={styles.miniBtnGhost} onClick={() => setPanel('home')}>回小窝看看领养入口</Button>
                  )}
                </View>
              )}
            </View>
          )}

          {panel === 'achievements' && (
            <View>
              <Text className={styles.sectionTitle}>🫧 宠物动态</Text>
              {reminders.length === 0 ? (
                <Text className={styles.tip}>还没有动态，去陪它玩一会吧～</Text>
              ) : (
                reminders.slice(0, 5).map((item) => (
                  <View key={String(item.notificationId)} className={styles.careCard}>
                    <Text className={styles.careTitle}>{item.title}</Text>
                    <Text className={styles.careMeta}>{item.content}</Text>
                  </View>
                ))
              )}
              <Text className={styles.sectionTitle}>🏆 成就墙</Text>
              <View className={styles.achGrid}>
              {achievements.map((item) => (
                <View key={String(item.achievementId)} className={`${styles.achCard} ${item.achieved ? '' : styles.achLocked}`}>
                  <Text className={styles.achIcon}>{item.icon}</Text>
                  <Text className={styles.achName}>{item.name}</Text>
                  <Text className={styles.achDesc}>{item.description}</Text>
                  <Text className={styles.historyMeta}>{item.achieved ? `已达成 +${item.expReward}` : `未达成 +${item.expReward}`}</Text>
                </View>
              ))}
              </View>
            </View>
          )}
        </View>
        </CreamSheet>
        )}
      </ScrollView>

      {/* 对战回合流水回放（服务端计算，客户端只展示；Cocos 舞台另有动画演出） */}
      {roundsView && (
        <View className={styles.modalMask} onClick={() => setRoundsView(null)}>
          <View className={styles.modalCard} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>
              {roundsView.won ? '🏆 对战大获全胜' : '💧 对战惜败'}
            </Text>
            <ScrollView className={styles.roundsScroll}>
              {roundsView.rounds.map((round, index) => (
                <Text key={`${round.round}-${index}`} className={styles.roundLine}>
                  第 {round.round} 回合：{round.actorName}
                  {round.action === 'skill' ? ' 使用技能' : ''}
                  {round.dodged ? ' 被闪避' : ` 造成 ${round.damage} 点伤害`}
                  {round.critical ? '（暴击）' : ''} → {round.targetName} 剩余 {round.targetRemainingHp}
                </Text>
              ))}
            </ScrollView>
            <Button className={styles.primaryBtn} onClick={() => setRoundsView(null)}>关闭</Button>
          </View>
        </View>
      )}

      {/* 档案弹窗：改名 30 天冷却 + 外观 + 主页公开 */}
      {profileOpen && (
        <View className={styles.modalMask} onClick={() => setProfileOpen(false)}>
          <View className={styles.modalCard} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>宠物档案</Text>
            <Text className={styles.modalLabel}>昵称</Text>
            <Input
              className={styles.nameInput}
              value={profileName}
              maxlength={12}
              placeholder="宠物名（30 天可改一次）"
              onInput={(e) => setProfileName(e.detail.value)}
            />
            <Text className={styles.modalLabel}>它怎么叫你</Text>
            <Input
              className={styles.nameInput}
              value={profileOwnerTitle}
              maxlength={12}
              placeholder="宠物怎么叫你（默认「主人」）"
              onInput={(e) => setProfileOwnerTitle(e.detail.value)}
            />
            <View className={styles.optionRow}>
              {ACCESSORIES.map((item) => (
                <View
                  key={item.key}
                  className={`${styles.optionChip} ${profileAccessory === item.key ? styles.optionChipActive : ''}`}
                  onClick={() => setProfileAccessory(item.key)}
                >
                  <Text>{item.label}</Text>
                </View>
              ))}
            </View>
            <View className={styles.optionRow}>
              {PET_COLORS.map((color) => (
                <View
                  key={color}
                  className={`${styles.optionChip} ${profileColor === color ? styles.optionChipActive : ''}`}
                  onClick={() => setProfileColor(color)}
                >
                  <Text>{color}</Text>
                </View>
              ))}
            </View>
            {anniversary && (
              <View className={styles.modalRow}>
                <Text className={styles.modalLabel}>
                  🎂 相遇第 {anniversary.adoptionDays} 天 · 连续陪伴 {anniversary.companionStreak} 天
                  {anniversary.nextMilestone ? ` · ${anniversary.nextMilestone.title}` : ''}
                </Text>
              </View>
            )}
            <View className={styles.modalRow}>
              <Text className={styles.modalLabel}>在个人主页展示宠物</Text>
              <CreamToggle on={pet.isPublic} disabled={privacyBusy} onChange={() => void togglePrivacy(!pet.isPublic)} />
            </View>
            <View className={styles.modalActions}>
              <CreamButton variant="ghost" style={{ flex: 1 }} onClick={() => setProfileOpen(false)}>取消</CreamButton>
              <CreamButton style={{ flex: 1 }} onClick={saveProfile}>保存</CreamButton>
            </View>
          </View>
        </View>
      )}

      {/* 稀有/宠物/彩蛋瓶结果 */}
      {bottleResult && (
        <View className={styles.modalMask} onClick={() => setBottleResult(null)}>
          <View className={styles.modalCard} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>🍾 捞到了一只{RARITY_LABEL[bottleResult.rarity]}！</Text>
            {bottleResult.specialContent
              ? <Text className={styles.modalText}>{bottleResult.specialContent}</Text>
              : null}
            <Text className={styles.modalText}>
              宠物经验 +{bottleResult.exp} · 成功率 {Math.round(bottleResult.successRate * 100)}%
            </Text>
            <Button className={styles.primaryBtn} onClick={() => setBottleResult(null)}>知道啦</Button>
          </View>
        </View>
      )}

      {/* 分享卡片（文案服务端生成） */}
      {shareCard && (
        <View className={styles.modalMask} onClick={() => setShareCard(null)}>
          <View className={styles.modalCard} onClick={(e) => e.stopPropagation()}>
            <Text className={styles.modalTitle}>{shareCard.title}</Text>
            <Text className={styles.modalText}>{shareCard.content}</Text>
            {shareCard.highlight ? <Text className={styles.shareHighlight}>{shareCard.highlight}</Text> : null}
            <View className={styles.modalActions}>
              <Button className={`${styles.miniBtnGhost} ${styles.modalAction}`} onClick={() => setShareCard(null)}>关闭</Button>
              <Button className={`${styles.primaryBtn} ${styles.modalAction}`} onClick={copyShare}>复制文案</Button>
            </View>
          </View>
        </View>
      )}
    </View>
  )
}

// ==================== 三期面板：职业 / 每日任务 / 社交 / 家园（服务端权威，前端只发意图） ====================
// 错误码文案复用文件顶部的 CARE_ERROR_HINT（三期新增错误码由后端返回 message 兜底展示）

/** 职业面板：入职 / 职业工作 / 晋升 / 历史（养成 → 职业） */
