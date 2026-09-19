import { useCallback, useEffect, useState } from 'react'
import { View, Text, Input, Button, ScrollView, Switch } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
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
} from '@/api/pet'
import { notificationApi } from '@/api/notification'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

/**
 * 社区宠物原生管理页（小程序/H5，实施文档 §5/§34）。
 *
 * 全平台可用的完整管理入口：领养/互动/打工/读书/捞瓶/对战/聊天/成就/提醒/档案/分享。
 * Cocos 舞台在独立页 petStage（H5 为完整双向桥；微信小程序 web-view 受平台限制只做
 * 观赏，游戏操作经 navigateTo 携 intent 回落本页执行，平台约束见实施文档 §2.4）。
 */

const SPECIES_OPTIONS = [
  { value: 'CAT', emoji: '🐱', label: '橘猫' },
  { value: 'DOG', emoji: '🐶', label: '柴犬' },
  { value: 'RABBIT', emoji: '🐰', label: '兔子' },
  { value: 'FOX', emoji: '🦊', label: '小狐狸' },
  { value: 'PANDA', emoji: '🐼', label: '熊猫' },
] as const

const PERSONALITY_OPTIONS = [
  { value: 'LIVELY', label: '活泼' },
  { value: 'GENTLE', label: '温柔' },
  { value: 'TSUNDERE', label: '傲娇' },
  { value: 'SIMPLE', label: '憨厚' },
  { value: 'COOL', label: '高冷' },
  { value: 'CHATTERBOX', label: '话痨' },
] as const

const SPECIES_EMOJI: Record<string, string> = { CAT: '🐱', DOG: '🐶', RABBIT: '🐰', FOX: '🦊', PANDA: '🐼' }
const STATUS_LABEL: Record<string, string> = {
  IDLE: '悠闲中', WORKING: '打工中', STUDYING: '读书中', FISHING: '捞瓶中', RESTING: '休息中',
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
const EVENT_TYPE_LABEL: Record<string, string> = {
  BOTTLE: '捞瓶', BATTLE: '对战胜场', WORK: '打工', STUDY: '读书', FEED: '喂食', PLAY: '玩耍', VISIT: '串门',
}
/** 二期错误码 → 可读提示（原文档 §1.1/§89） */
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
]

type PanelKey =
  | 'home' | 'care' | 'work' | 'study' | 'bottle' | 'battle'
  | 'chat' | 'achievements' | 'rankings' | 'reminders'
  | 'daily' | 'social'

const PANELS: Array<{ key: PanelKey; label: string }> = [
  { key: 'home', label: '🏠 家园' },
  { key: 'care', label: '🎒 养成' },
  { key: 'daily', label: '✅ 任务' },
  { key: 'social', label: '🤝 社交' },
  { key: 'work', label: '💼 打工' },
  { key: 'study', label: '📚 读书' },
  { key: 'bottle', label: '🍾 捞瓶' },
  { key: 'battle', label: '⚔️ 对战' },
  { key: 'chat', label: '💬 聊天' },
  { key: 'achievements', label: '🏆 成就' },
  { key: 'rankings', label: '📊 排行' },
  { key: 'reminders', label: '🔔 提醒' },
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
  const [panel, setPanel] = useState<PanelKey>('home')
  const [intimacy, setIntimacy] = useState<PetIntimacyInfo | null>(null)
  const [adoptSpecies, setAdoptSpecies] = useState('CAT')
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
  const [rankingType, setRankingType] = useState<PetRankingType>('LEVEL')
  const [reminders, setReminders] = useState<PetReminder[]>([])
  const [reminderUnread, setReminderUnread] = useState(0)
  const [shareCard, setShareCard] = useState<PetShareCard | null>(null)
  const [profileOpen, setProfileOpen] = useState(false)
  const [profileName, setProfileName] = useState('')
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
  const [myPets, setMyPets] = useState<PetSummary[]>([])
  const [careMessage, setCareMessage] = useState<string | null>(null)
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
      }
    } catch (error) {
      const code = (error as { code?: string }).code
      toast((code && CARE_ERROR_HINT[code]) || '请稍后再试')
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

  /**
   * 陪伴心跳：小程序在前台时每 60 秒上报一次（服务端按日封顶折算亲密度）。
   * 页面隐藏时不上报——"陪伴"必须是真实停留在宠物页。
   */
  useEffect(() => {
    const timer = setInterval(() => {
      void (async () => {
        try {
          const { data: res } = await petApi.companionHeartbeat(60)
          if (res.success && res.data) {
            setIntimacy(res.data)
          }
        } catch {
          // 心跳失败静默（下一轮重试）
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

  const refresh = useCallback(async () => {
    try {
      const { data: res } = await petApi.getMyPet()
      if (res.success && res.data) {
        setPet(res.data)
        setNoPet(false)
      }
    } catch (error) {
      setNoPet(true)
      if ((error as { code?: string }).code !== 'PET_NOT_FOUND') {
        toast(friendlyError(error))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  const loadPanelData = useCallback(async (key: PanelKey) => {
    try {
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
      } else if (key === 'rankings') {
        const { data: res } = await petApi.getRankings(rankingType)
        if (res.success) setRankings(res.data)
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

  useEffect(() => {
    if (pet) loadPanelData(panel)
  }, [panel, pet, loadPanelData])

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
      loadPanelData(panel)
    }, 1500)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activityKey, activeRemaining])

  const openProfile = useCallback(() => {
    if (!pet) return
    setProfileName(pet.name)
    const appearance = parseResult<{ color?: string; accessory?: string }>(pet.appearance)
    setProfileColor(appearance?.color ?? PET_COLORS[0])
    setProfileAccessory(appearance?.accessory ?? 'none')
    setProfileOpen(true)
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

  const runInteraction = async (action: 'feed' | 'play' | 'clean' | 'rest') => {
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
    try {
      const { data: appearanceRes } = await petApi.updateAppearance({ color: profileColor, accessory: profileAccessory })
      if (appearanceRes.success && appearanceRes.data) setPet(appearanceRes.data)
      if (wantRename) {
        const { data: renameRes } = await petApi.renamePet({ name: profileName.trim() })
        if (renameRes.success && renameRes.data) setPet(renameRes.data)
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
      await notificationApi.markRead(Number(item.notificationId))
      setReminders((prev) => prev.map((r) => (r.notificationId === item.notificationId ? { ...r, isRead: true } : r)))
      setReminderUnread((n) => Math.max(0, n - 1))
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  const markAllRemindersRead = async () => {
    try {
      await notificationApi.markAllRead()
      setReminders((prev) => prev.map((r) => ({ ...r, isRead: true })))
      setReminderUnread(0)
    } catch (error) {
      toast(friendlyError(error))
    }
  }

  if (!currentUser && !loading) {
    return (
      <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
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
      <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
        <CustomNavBar title="我的宠物" />
        <View className={styles.empty}><Text>加载中…</Text></View>
      </View>
    )
  }

  // 领养向导
  if (noPet || !pet) {
    return (
      <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
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
    <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
      <CustomNavBar title="我的宠物" />
      <ScrollView scrollY className={styles.body} style={{ paddingTop: statusBarHeight + navBarHeight }}>
        {/* 头部卡片 */}
        <View className={styles.heroCard}>
          <View className={styles.heroRow}>
            <Text className={styles.heroEmoji}>{SPECIES_EMOJI[pet.species] || '🐾'}</Text>
            <View className={styles.heroInfo}>
              <Text className={styles.heroName}>{pet.name} · Lv.{pet.level}</Text>
              <Text className={styles.heroStatus}>
                {STATUS_LABEL[pet.status]} · {GROWTH_STAGE_LABEL[pet.growthStage] ?? pet.growthStage}
              </Text>
              <Text className={styles.heroStatus}>
                💪{pet.strength} 🧠{pet.intelligence} 🌀{pet.agility} 💖{pet.charm}
                {pet.feedRemainingToday != null ? ` · 今日可喂食 ${pet.feedRemainingToday} 次` : ''}
              </Text>
            </View>
            <View className={styles.stageEntry} onClick={openStage}>
              <Text className={styles.stageEntryText}>进入舞台</Text>
            </View>
          </View>
          <View className={styles.bars}>
            {[
              { label: '❤️ 生命', value: pet.hp, max: pet.maxHp, color: '#ff6c6c' },
              { label: '🍖 饱食', value: pet.hunger, max: 100, color: '#ffb258' },
              { label: '💗 心情', value: pet.happiness, max: 100, color: '#ff69b4' },
              { label: '⚡ 精力', value: pet.energy, max: 100, color: '#62d88a' },
              { label: '🧼 清洁', value: pet.cleanliness, max: 100, color: '#60beff' },
            ].map((row) => (
              <View key={row.label} className={styles.barRow}>
                <Text className={styles.barLabel}>{row.label}</Text>
                <View className={styles.barTrack}>
                  <View className={styles.barFill} style={{ width: `${Math.min(100, (row.value / row.max) * 100)}%`, background: row.color }} />
                </View>
                <Text className={styles.barValue}>{row.value}/{row.max}</Text>
              </View>
            ))}
          </View>
          {/* 三期：亲密度与陪伴（服务端权威，前端只展示；陪伴时长由心跳累计） */}
          <Text className={styles.jobMeta}>
            💞 亲密度 {intimacy ? `${intimacy.intimacy}（${intimacy.levelName}）` : `${pet.intimacy}（${pet.intimacyLevelName}）`}
            {intimacy && intimacy.nextLevelAt !== null ? ` · 还差 ${intimacy.toNext}` : ''}
            {` · 经验加成 +${intimacy?.expBonusPercent ?? pet.intimacyExpBonusPercent}%`}
            {` · 已陪伴 ${Math.floor((intimacy?.companionSeconds ?? pet.companionSeconds) / 3600)} 小时`}
            {intimacy ? `（今日 ${Math.round(intimacy.todayCompanionSeconds / 60)} 分钟，连续 ${intimacy.companionStreak} 天）` : ''}
          </Text>
          {/* 互动按钮 */}
          <View className={styles.actionRow}>
            <Button className={styles.actionBtn} onClick={() => runInteraction('feed')}>🍖 喂食</Button>
            <Button className={styles.actionBtn} onClick={() => runInteraction('play')}>🎾 玩耍</Button>
            <Button className={styles.actionBtn} onClick={() => runInteraction('clean')}>🫧 清洁</Button>
            <Button className={styles.actionBtn} onClick={() => runInteraction('rest')}>💤 休息</Button>
          </View>
          <View className={styles.actionRow}>
            <Button className={styles.actionBtn} onClick={openProfile}>🎀 档案</Button>
          </View>
        </View>

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
                  loadPanelData(panel)
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

        {/* 面板切换 */}
        <ScrollView scrollX className={styles.panelTabs} enhanced showScrollbar={false}>
          <View className={styles.panelTabsInner}>
            {PANELS.map((item) => (
              <View
                key={item.key}
                className={`${styles.panelTab} ${panel === item.key ? styles.panelTabActive : ''}`}
                onClick={() => setPanel(item.key)}
              >
                <Text>{item.label}{item.key === 'reminders' && reminderUnread > 0 ? ` (${reminderUnread})` : ''}</Text>
              </View>
            ))}
          </View>
        </ScrollView>

        <View className={styles.panelBody}>
          {panel === 'daily' && <DailyQuestPanel onRefresh={refresh} />}
        {panel === 'social' && <SocialPanel pet={pet} onRefresh={refresh} />}
        {panel === 'home' && (
            <View className={styles.tips}>
              {/* 三期：家园（房间布置 / 家具 / 拜访） */}
              <HomePanel onRefresh={refresh} />
              <Text className={styles.tip}>🍖 饱食和清洁随时间下降，记得回来照顾它</Text>
              <Text className={styles.tip}>💼 打工赚星光，📚 读书涨智力</Text>
              <Text className={styles.tip}>🍾 宠物会定时帮你捞社区漂流瓶并主动提醒</Text>
              <Text className={styles.tip}>⚔️ 对战由服务端计算，输了也有经验</Text>
              <Text className={styles.tip}>💬 和它聊聊天，它会记住你的喜好哦</Text>
              <View className={styles.modalRow}>
                <Text className={styles.modalLabel}>在个人主页展示宠物</Text>
                <Switch checked={pet.isPublic} disabled={privacyBusy} onChange={(e) => void togglePrivacy(e.detail.value)} />
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
                    <Text className={styles.jobMeta}>⏱ {Math.round(job.durationSeconds / 60)} 分钟 · ⚡-{job.energyCost} · ✨+{job.expReward} · ⭐+{job.currencyReward}</Text>
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
                    <Text className={styles.jobMeta}>⏱ {Math.round(study.durationSeconds / 60)} 分钟 · ✨+{study.expReward} · 🧠+{study.intelligenceReward}</Text>
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
              <View className={styles.rankTabs}>
                {RANKING_TABS.map((tab) => (
                  <View
                    key={tab.key}
                    className={`${styles.panelTab} ${rankingType === tab.key ? styles.panelTabActive : ''}`}
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
              <View className={styles.careTabs}>
                {CARE_TABS.map((tab) => (
                  <View
                    key={tab.key}
                    className={`${styles.panelTab} ${careTab === tab.key ? styles.panelTabActive : ''}`}
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
                    ✨ 星光余额：{shopBalance === null ? '暂不可用' : shopBalance}
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
                          <Button className={styles.miniBtnGhost} onClick={() => runCare(`unequip-${item.slot}`, () => petApi.unequipItem(item.slot ?? ''), '已卸下')}>卸下</Button>
                        ) : (
                          <Button className={styles.miniBtn} onClick={() => runCare(`equip-${item.code}`, () => petApi.equipItem(item.code), '已穿戴')}>穿戴</Button>
                        ))}
                        {item.itemType === 'SKIN' && (item.equipped ? (
                          <Button className={styles.miniBtnGhost} onClick={() => runCare('remove-skin', () => petApi.removeSkin(), '已换回原生外观')}>卸下</Button>
                        ) : (
                          <Button className={styles.miniBtn} onClick={() => runCare(`skin-${item.code}`, () => petApi.wearSkin(item.code), '已穿上新皮肤')}>穿戴</Button>
                        ))}
                      </View>
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
            <Input
              className={styles.nameInput}
              value={profileName}
              maxlength={12}
              placeholder="宠物名（30 天可改一次）"
              onInput={(e) => setProfileName(e.detail.value)}
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
            <View className={styles.modalRow}>
              <Text className={styles.modalLabel}>在个人主页展示宠物</Text>
              <Switch checked={pet.isPublic} disabled={privacyBusy} onChange={(e) => void togglePrivacy(e.detail.value)} />
            </View>
            <View className={styles.modalActions}>
              <Button className={`${styles.miniBtnGhost} ${styles.modalAction}`} onClick={() => setProfileOpen(false)}>取消</Button>
              <Button className={`${styles.primaryBtn} ${styles.modalAction}`} onClick={saveProfile}>保存</Button>
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
function CareerPanel({ onRefresh }: { onRefresh: () => void }) {
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

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        Taro.showToast({ title: text, icon: 'success' })
        await load()
        onRefresh()
      }
    } catch (error) {
      Taro.showToast({ title: CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试', icon: 'none' })
    } finally {
      setPending(null)
    }
  }

  if (!panel) {
    return <Text className={styles.tip}>职业信息加载中…</Text>
  }
  const activity = panel.activeActivity

  return (
    <View>
      <Text className={styles.tip}>
        {panel.careerCode
          ? `当前职业：${panel.icon ?? ''} ${panel.careerName}（${panel.careerLine} · ${panel.tier} 阶）· 已工作 ${panel.workCount} 次`
          : '还没有工作，挑一份喜欢的职业入职吧'}
        {panel.canPromote && panel.promoteToName ? ` · 可晋升「${panel.promoteToName}」` : ''}
        {panel.promoteLockReason && panel.promoteToName ? ` · 晋升条件：${panel.promoteLockReason}` : ''}
      </Text>
      {panel.careerCode && (
        <View className={styles.actionRow}>
          {activity ? (
            <Button
              className={styles.miniBtn}
              disabled={!activity.canClaim || pending === 'claim'}
              onClick={() => run('claim', () => petApi.claimCareerWork(), '工钱到手啦！')}
            >
              {activity.canClaim ? '领取工作奖励' : `工作中 ${Math.max(0, Math.ceil(activity.remainingSeconds / 60))} 分钟`}
            </Button>
          ) : (
            <Button
              className={styles.miniBtn}
              disabled={pending === 'start'}
              onClick={() => run('start', () => petApi.startCareerWork(), '开始工作啦')}
            >
              去上班
            </Button>
          )}
          <Button
            className={styles.miniBtnGhost}
            disabled={!panel.canPromote || pending === 'promote'}
            onClick={() => run('promote', () => petApi.promoteCareer(), '晋升成功！')}
          >
            晋升
          </Button>
        </View>
      )}
      <View className={styles.jobList}>
        {panel.careers.map((career) => (
          <View key={career.code} className={styles.jobCard}>
            <View className={styles.jobInfo}>
              <Text className={styles.jobName}>
                {career.icon} {career.name} · {career.careerLine} {career.tier} 阶
              </Text>
              <Text className={styles.jobMeta}>{career.description}</Text>
              <Text className={styles.jobMeta}>
                Lv.{career.requiredLevel}
                {career.requiredIntelligence > 0 ? ` · 智力 ${career.requiredIntelligence}` : ''} ·{' '}
                {Math.round(career.durationSeconds / 60)} 分钟 · 经验+{career.expReward} ✨+{career.currencyReward}
                {career.workCount > 0 ? ` · 已工作 ${career.workCount} 次` : ''}
              </Text>
            </View>
            {career.current ? (
              <Text className={styles.jobMeta}>在职</Text>
            ) : (
              <Button
                className={career.eligible ? styles.miniBtn : styles.locked}
                disabled={!career.eligible || pending === `apply-${career.code}`}
                onClick={() => run(`apply-${career.code}`, () => petApi.applyCareer(career.code), '入职成功！')}
              >
                {career.eligible ? '入职' : career.lockReason ?? '未解锁'}
              </Button>
            )}
          </View>
        ))}
      </View>
      {panel.history.length > 0 && (
        <Text className={styles.jobMeta}>
          工作经历：{panel.history.map((item) => `${item.name}（${item.workCount} 次）`).join(' · ')}
        </Text>
      )}
    </View>
  )
}

/** 每日任务面板：进度 + 领奖 + 全清宝箱 */
function DailyQuestPanel({ onRefresh }: { onRefresh: () => void }) {
  const [panel, setPanel] = useState<PetDailyQuestPanel | null>(null)
  const [pending, setPending] = useState<string | null>(null)

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

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        Taro.showToast({ title: text, icon: 'success' })
        await load()
        onRefresh()
      }
    } catch (error) {
      Taro.showToast({ title: CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试', icon: 'none' })
    } finally {
      setPending(null)
    }
  }

  if (!panel) {
    return <Text className={styles.tip}>任务加载中…</Text>
  }

  return (
    <View>
      <Text className={styles.tip}>
        今日进度 {panel.claimedCount}/{panel.totalCount} · 全清宝箱 经验+{panel.chestExp} ✨+{panel.chestCurrency}
      </Text>
      <View className={styles.jobList}>
        {panel.quests.map((quest) => (
          <View key={quest.code} className={styles.jobCard}>
            <View className={styles.jobInfo}>
              <Text className={styles.jobName}>
                {quest.icon} {quest.name}
              </Text>
              <Text className={styles.jobMeta}>
                {quest.description} · 进度 {Math.min(quest.progress, quest.targetValue)}/{quest.targetValue} · 经验+
                {quest.expReward} ✨+{quest.currencyReward}
              </Text>
            </View>
            {quest.status === 'CLAIMED' ? (
              <Text className={styles.jobMeta}>已领取</Text>
            ) : (
              <Button
                className={quest.claimable ? styles.miniBtn : styles.locked}
                disabled={!quest.claimable || pending === `q-${quest.code}`}
                onClick={() => run(`q-${quest.code}`, () => petApi.claimDailyQuest(quest.code), '奖励到手啦！')}
              >
                {quest.claimable ? '领取' : quest.statusLabel}
              </Button>
            )}
          </View>
        ))}
      </View>
      {panel.chestClaimed ? (
        <Text className={styles.jobMeta}>宝箱已领取</Text>
      ) : (
        <Button
          className={panel.chestClaimable ? styles.miniBtn : styles.locked}
          disabled={!panel.chestClaimable || pending === 'chest'}
          onClick={() => run('chest', () => petApi.claimDailyQuestChest(), '宝箱开啦！')}
        >
          {panel.chestClaimable ? '开启全清宝箱' : '全部领取后可开宝箱'}
        </Button>
      )}
    </View>
  )
}

/** 社交面板：关系 / 好友 / 留言墙 */
function SocialPanel({ pet, onRefresh }: { pet: PetInfo; onRefresh: () => void }) {
  const [tab, setTab] = useState<'relation' | 'friend' | 'wall'>('relation')
  const [relations, setRelations] = useState<PetRelationPanel | null>(null)
  const [friends, setFriends] = useState<PetFriendPanel | null>(null)
  const [wall, setWall] = useState<PetWallPage | null>(null)
  const [wallInput, setWallInput] = useState('')
  const [replyTo, setReplyTo] = useState<number | null>(null)
  const [replyInput, setReplyInput] = useState('')
  const [tip, setTip] = useState<string | null>(null)
  const [pending, setPending] = useState<string | null>(null)

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
      } else {
        const { data: res } = await petApi.getWall(Number(pet.petId), 1, 10)
        if (res.success && res.data) {
          setWall(res.data)
        }
      }
    } catch {
      // 拦截器已提示
    }
  }, [tab, pet.petId])

  useEffect(() => {
    void loadActive()
  }, [loadActive])

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        Taro.showToast({ title: text, icon: 'success' })
        await loadActive()
        onRefresh()
      }
    } catch (error) {
      Taro.showToast({ title: CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试', icon: 'none' })
    } finally {
      setPending(null)
    }
  }

  return (
    <View>
      <View className={styles.actionRow}>
        {([
          ['relation', '💞 关系'],
          ['friend', '🫂 好友'],
          ['wall', '📝 留言墙'],
        ] as Array<[typeof tab, string]>).map(([key, label]) => (
          <Button
            key={key}
            className={tab === key ? styles.miniBtn : styles.miniBtnGhost}
            onClick={() => setTab(key)}
          >
            {label}
          </Button>
        ))}
      </View>
      {tip && <Text className={styles.tip}>{tip}</Text>}

      {tab === 'relation' && relations && (
        <View>
          <Text className={styles.jobMeta}>
            {relations.limits.map((limit) => `${limit.label} ${limit.current}/${limit.max}`).join(' · ')}
          </Text>
          <View className={styles.jobList}>
            {relations.incoming.map((item) => (
              <View key={String(item.id)} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {item.petName}（{item.ownerNickname}）
                  </Text>
                  <Text className={styles.jobMeta}>想成为{item.relTypeLabel}{item.message ? `：「${item.message}」` : ''}</Text>
                </View>
                <Button
                  className={styles.miniBtn}
                  disabled={pending === `a-${item.id}`}
                  onClick={() => run(`a-${item.id}`, () => petApi.acceptRelation(item.id as number), '关系建立啦！')}
                >
                  同意
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `r-${item.id}`}
                  onClick={() => run(`r-${item.id}`, () => petApi.rejectRelation(item.id as number), '已拒绝')}
                >
                  拒绝
                </Button>
              </View>
            ))}
            {relations.relations.map((item) => (
              <View key={String(item.id)} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {item.petName} · {item.relTypeLabel}
                  </Text>
                  <Text className={styles.jobMeta}>
                    {item.ownerNickname} · 亲密度 {item.intimacy}（{item.intimacyLevelName}）
                  </Text>
                </View>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `d-${item.id}`}
                  onClick={() => run(`d-${item.id}`, () => petApi.dissolveRelation(item.id as number), '已解除关系')}
                >
                  解除
                </Button>
              </View>
            ))}
          </View>
          {relations.candidates.length > 0 && <Text className={styles.sectionTitle}>可以认识的宠物</Text>}
          <View className={styles.jobList}>
            {relations.candidates.map((candidate) => (
              <View key={candidate.petId} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {candidate.petName}（Lv.{candidate.level} · {candidate.ownerNickname}）
                  </Text>
                </View>
                <Button
                  className={styles.miniBtn}
                  disabled={pending === `cr-${candidate.petId}`}
                  onClick={() =>
                    run(
                      `cr-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'COUPLE' }),
                      '申请已发出～',
                    )
                  }
                >
                  情侣
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `br-${candidate.petId}`}
                  onClick={() =>
                    run(
                      `br-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'BESTIE' }),
                      '申请已发出～',
                    )
                  }
                >
                  闺蜜
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `fr-${candidate.petId}`}
                  onClick={() =>
                    run(
                      `fr-${candidate.petId}`,
                      () => petApi.requestRelation({ toPetId: candidate.petId, relType: 'CONFIDANT' }),
                      '申请已发出～',
                    )
                  }
                >
                  死党
                </Button>
              </View>
            ))}
          </View>
        </View>
      )}

      {tab === 'friend' && friends && (
        <View>
          <Text className={styles.jobMeta}>
            今日互访 {friends.todayVisitCount}/{friends.dailyVisitLimit} · 好友上限 {friends.maxFriends}
          </Text>
          <View className={styles.jobList}>
            {friends.incoming.map((item) => (
              <View key={item.userId} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>{item.nickname}</Text>
                  <Text className={styles.jobMeta}>{item.petName ?? '还没有宠物'}</Text>
                </View>
                <Button
                  className={styles.miniBtn}
                  disabled={pending === `fa-${item.userId}`}
                  onClick={() => run(`fa-${item.userId}`, () => petApi.acceptFriend(item.userId), '成为好友啦！')}
                >
                  同意
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `fj-${item.userId}`}
                  onClick={() => run(`fj-${item.userId}`, () => petApi.rejectFriend(item.userId), '已拒绝')}
                >
                  拒绝
                </Button>
              </View>
            ))}
            {friends.friends.map((item) => (
              <View key={item.userId} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {item.nickname} · {item.petName ?? '—'}
                  </Text>
                  <Text className={styles.jobMeta}>互访 {item.visitCount} 次</Text>
                </View>
                <Button
                  className={styles.miniBtn}
                  disabled={pending === `fv-${item.userId}`}
                  onClick={() =>
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
                >
                  去互访
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `fd-${item.userId}`}
                  onClick={() => run(`fd-${item.userId}`, () => petApi.removeFriend(item.userId), '已删除好友')}
                >
                  删除
                </Button>
              </View>
            ))}
          </View>
        </View>
      )}

      {tab === 'wall' && wall && (
        <View>
          <Text className={styles.jobMeta}>
            {wall.petName} 的留言墙 · {wall.ownerNickname} · 共 {wall.total} 条（每日可留言 {wall.dailyPostLimit} 条）
          </Text>
          <View className={styles.actionRow}>
            <Input
              className={styles.nameInput}
              value={wallInput}
              maxlength={120}
              placeholder="写一句留言吧"
              onInput={(event) => setWallInput(event.detail.value)}
            />
            <Button
              className={styles.miniBtn}
              disabled={pending === 'post'}
              onClick={() =>
                run(
                  'post',
                  async () => {
                    const res = await petApi.postWallMessage({ petId: Number(pet.petId), content: wallInput })
                    if (res.data.success) {
                      setWallInput('')
                    }
                    return res
                  },
                  '留言成功！',
                )
              }
            >
              留言
            </Button>
          </View>
          <View className={styles.jobList}>
            {wall.messages.map((item) => (
              <View key={item.id} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobMeta}>
                    {item.authorNickname}
                    {item.ownerReply ? '（我的回复）' : ''} · {item.createdAt?.slice(5, 16).replace('T', ' ')}
                  </Text>
                  <Text className={styles.jobName}>{item.content}</Text>
                  {item.replies.map((reply) => (
                    <Text key={reply.id} className={styles.jobMeta}>
                      ↳ {reply.authorNickname}：{reply.content}
                    </Text>
                  ))}
                </View>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `wl-${item.id}`}
                  onClick={() => run(`wl-${item.id}`, () => petApi.likeWallMessage(item.id), '已更新点赞')}
                >
                  {item.liked ? '取消赞' : '点赞'} {item.likeCount}
                </Button>
                {item.owner && item.parentId === null && (
                  <Button
                    className={styles.miniBtnGhost}
                    onClick={() => {
                      setReplyTo(replyTo === item.id ? null : item.id)
                      setReplyInput('')
                    }}
                  >
                    回复
                  </Button>
                )}
                {(item.mine || item.owner) && (
                  <Button
                    className={styles.miniBtnGhost}
                    disabled={pending === `wd-${item.id}`}
                    onClick={() => run(`wd-${item.id}`, () => petApi.deleteWallMessage(item.id), '已删除')}
                  >
                    删除
                  </Button>
                )}
              </View>
            ))}
          </View>
          {replyTo !== null && (
            <View className={styles.actionRow}>
              <Input
                className={styles.nameInput}
                value={replyInput}
                maxlength={120}
                placeholder="回复这条留言"
                onInput={(event) => setReplyInput(event.detail.value)}
              />
              <Button
                className={styles.miniBtn}
                disabled={pending === `wr-${replyTo}`}
                onClick={() =>
                  run(
                    `wr-${replyTo}`,
                    async () => {
                      const res = await petApi.replyWallMessage({ messageId: replyTo, content: replyInput })
                      if (res.data.success) {
                        setReplyTo(null)
                      }
                      return res
                    },
                    '回复成功！',
                  )
                }
              >
                发送
              </Button>
            </View>
          )}
        </View>
      )}
    </View>
  )
}

/** 家园面板：房间布置 / 家具商城 / 拜访与设置 */
function HomePanel({ onRefresh }: { onRefresh: () => void }) {
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

  const run = async (key: string, action: () => Promise<{ data: { success: boolean } }>, text: string) => {
    setPending(key)
    try {
      const { data: res } = await action()
      if (res.success) {
        Taro.showToast({ title: text, icon: 'success' })
        await load()
        onRefresh()
      }
    } catch (error) {
      Taro.showToast({ title: CARE_ERROR_HINT[(error as { code?: string }).code ?? ''] ?? '请稍后再试', icon: 'none' })
    } finally {
      setPending(null)
    }
  }

  if (!home) {
    return <Text className={styles.tip}>家园加载中…</Text>
  }

  return (
    <View>
      <Text className={styles.tip}>
        🏡 舒适度 {home.comfort} · 来访 {home.visitCount} · 点赞 {home.likeCount}
        {home.comfort >= home.comfortBonusThreshold ? ` · 休息心情 +${home.comfortRestHappinessBonus}` : ''}
      </Text>
      <View className={styles.actionRow}>
        {([
          ['room', '🛋️ 布置'],
          ['shop', '🛒 家具'],
          ['visit', '🚪 拜访'],
        ] as Array<[typeof tab, string]>).map(([key, label]) => (
          <Button key={key} className={tab === key ? styles.miniBtn : styles.miniBtnGhost} onClick={() => setTab(key)}>
            {label}
          </Button>
        ))}
      </View>
      {tip && <Text className={styles.tip}>{tip}</Text>}

      {tab === 'room' && (
        <View>
          <View className={styles.jobList}>
            {home.placed.map((item) => (
              <View key={`${item.posX}-${item.posY}`} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {item.icon} {item.name}
                  </Text>
                  <Text className={styles.jobMeta}>
                    位置（{item.posX},{item.posY}）· 舒适度 +{item.comfort}
                  </Text>
                </View>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `rm-${item.posX}-${item.posY}`}
                  onClick={() =>
                    run(`rm-${item.posX}-${item.posY}`, () => petApi.removeFurniture(item.posX ?? 0, item.posY ?? 0), '已收回仓库')
                  }
                >
                  收回
                </Button>
              </View>
            ))}
          </View>
          <Text className={styles.jobMeta}>
            当前墙纸/地板：{home.wallCode ?? '默认'} / {home.floorCode ?? '默认'}
          </Text>
          <View className={styles.actionRow}>
            {home.shop
              .filter((item) => item.owned)
              .slice(0, 6)
              .map((item) => (
                <Button
                  key={`theme-${item.code}`}
                  className={styles.miniBtnGhost}
                  disabled={pending === `theme-${item.code}`}
                  onClick={() =>
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
                >
                  {item.icon}
                  {item.name}
                </Button>
              ))}
          </View>
        </View>
      )}

      {tab === 'shop' && (
        <View>
          <Text className={styles.jobMeta}>
            移动端简化交互：点「摆放」自动找空格；精细摆位请用 Web 端
          </Text>
          <View className={styles.jobList}>
            {home.shop.map((item) => (
              <View key={item.code} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {item.icon} {item.name} · {item.categoryLabel}
                  </Text>
                  <Text className={styles.jobMeta}>
                    舒适度 +{item.comfort} · 需要 Lv.{item.requiredLevel} · ✨ {item.priceStarlight}
                  </Text>
                </View>
                {item.owned ? (
                  <Button
                    className={styles.miniBtn}
                    disabled={pending === `place-${item.code}`}
                    onClick={() =>
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
                  >
                    摆放
                  </Button>
                ) : (
                  <Button
                    className={item.eligible ? styles.miniBtn : styles.locked}
                    disabled={!item.eligible || pending === `buy-${item.code}`}
                    onClick={() => run(`buy-${item.code}`, () => petApi.buyFurniture(item.code), '买到啦')}
                  >
                    {item.eligible ? '购买' : item.lockReason ?? '未解锁'}
                  </Button>
                )}
              </View>
            ))}
          </View>
        </View>
      )}

      {tab === 'visit' && (
        <View>
          <View className={styles.actionRow}>
            <Text className={styles.jobMeta}>允许来访</Text>
            <Switch
              checked={home.isPublic}
              onChange={(event) =>
                run('settings', () => petApi.updateRoomSettings({ isPublic: event.detail.value }), '设置已更新')
              }
            />
            <Input
              className={styles.nameInput}
              value={welcome}
              maxlength={40}
              placeholder="欢迎语"
              onInput={(event) => setWelcome(event.detail.value)}
            />
            <Button
              className={styles.miniBtnGhost}
              disabled={pending === 'welcome'}
              onClick={() => run('welcome', () => petApi.updateRoomSettings({ welcomeMessage: welcome }), '欢迎语已更新')}
            >
              保存
            </Button>
          </View>
          <View className={styles.jobList}>
            {neighbors.map((neighbor) => (
              <View key={String(neighbor.petId)} className={styles.jobCard}>
                <View className={styles.jobInfo}>
                  <Text className={styles.jobName}>
                    {neighbor.name}（Lv.{neighbor.level}）
                  </Text>
                  <Text className={styles.jobMeta}>{neighbor.ownerNickname}</Text>
                </View>
                <Button
                  className={styles.miniBtn}
                  disabled={pending === `v-${neighbor.petId}`}
                  onClick={() =>
                    run(
                      `v-${neighbor.petId}`,
                      async () => {
                        const res = await petApi.visitHome(Number(neighbor.petId))
                        if (res.data.success && res.data.data) {
                          setTip(res.data.data.message)
                        }
                        return res
                      },
                      '拜访成功！',
                    )
                  }
                >
                  去家里看看
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `l-${neighbor.petId}`}
                  onClick={() => run(`l-${neighbor.petId}`, () => petApi.likeHome(Number(neighbor.petId)), '点赞成功')}
                >
                  点赞
                </Button>
                <Button
                  className={styles.miniBtnGhost}
                  disabled={pending === `fq-${neighbor.petId}`}
                  onClick={() =>
                    run(`fq-${neighbor.petId}`, () => petApi.requestFriend(Number(neighbor.ownerUserId)), '好友申请已发出～')
                  }
                >
                  加好友
                </Button>
              </View>
            ))}
          </View>
        </View>
      )}
    </View>
  )
}
