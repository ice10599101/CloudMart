import { useEffect, useState } from 'react'
import { Text, TouchableOpacity } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { petApi, type PetInfo, type PetRankingType } from '@/api/pet'
import { generateAttachmentId } from '@/utils/postAttachments'

/**
 * App 端宠物页共享常量 / 纯函数 / hook / 基元（R30 自 app/pet.tsx 拆出，行为不变）。
 */

export const SPECIES_EMOJI: Record<string, string> = {
  STRAWBERRY: '🍓',
  ORANGE: '🍊',
  WATERMELON: '🍉',
  BLUEBERRY: '🫐',
  DRAGONFRUIT: '🐉',
}
export const PERSONALITY_LABEL: Record<string, string> = {
  LIVELY: '活泼', GENTLE: '温柔', TSUNDERE: '傲娇', SIMPLE: '憨厚', COOL: '高冷', CHATTERBOX: '话痨',
}
export const STATUS_LABEL: Record<string, string> = {
  IDLE: '悠闲中', WORKING: '打工中', STUDYING: '读书中', FISHING: '捞瓶中', RESTING: '休息中',
}
export const GROWTH_STAGE_LABEL: Record<string, string> = { BABY: '幼年', YOUNG: '成长期', ADULT: '成年' }
export const PET_COLORS = ['orange', 'white', 'black', 'gray', 'brown']
export const ACCESSORIES: { key: string; label: string }[] = [
  { key: 'none', label: '无' },
  { key: 'bell', label: '铃铛' },
  { key: 'bow', label: '领结' },
  { key: 'glasses', label: '眼镜' },
  { key: 'scarf', label: '围巾' },
]
export const PRIORITY_LABEL: Record<string, string> = { P0: '重要', P1: '普通', P2: '低' }
export const RARITY_LABEL: Record<string, string> = {
  NORMAL: '普通瓶', RARE: '稀有瓶', PET: '宠物瓶', EASTER_EGG: '彩蛋瓶',
}

export type PanelKey =
  | 'home' | 'care' | 'daily' | 'center' | 'social' | 'work' | 'study' | 'bottle' | 'battle'
  | 'chat' | 'achievements' | 'rankings' | 'reminders' | 'companion'
  // R19：双人协作 PVE / 赛季通行证 / 实物商品双倍喂食权益
  | 'pve' | 'seasonpass' | 'linkedproduct'

/** 养成面板子页签（原文档 §89：商城/背包/技能/进化/活动 + §1.1 串门 + 多宠物 + 三期职业） */
export type CareTab = 'shop' | 'inventory' | 'skills' | 'evolution' | 'events' | 'visit' | 'career' | 'pets'

export const CARE_TABS: { key: CareTab; label: string }[] = [
  { key: 'shop', label: '🛒 商城' },
  { key: 'inventory', label: '🎒 背包' },
  { key: 'skills', label: '🌟 技能' },
  { key: 'evolution', label: '🌠 进化' },
  { key: 'events', label: '🎯 活动' },
  { key: 'visit', label: '🚪 串门' },
  { key: 'career', label: '💼 职业' },
  { key: 'pets', label: '🐾 宠物' },
]

export const SLOT_LABEL: Record<string, string> = { HAT: '帽子', NECKLACE: '项圈', SCARF: '围巾', BACKPACK: '背包' }
export const ITEM_TYPE_LABEL: Record<string, string> = { EQUIPMENT: '装备', SKIN: '皮肤', SKILL_BOOK: '技能书' }
export const EVENT_TYPE_LABEL: Record<string, string> = {
  BOTTLE: '捞瓶', BATTLE: '对战胜场', WORK: '打工', STUDY: '读书', FEED: '喂食', PLAY: '玩耍', VISIT: '串门',
}
/** 二期错误码 → 可读提示（服务端文案能力之外的补充） */
export const CARE_ERROR_HINT: Record<string, string> = {
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
export const ACTION_LABEL: Record<string, string> = {
  FEED: '喂食', PLAY: '玩耍', CLEAN: '清洁', REST: '休息',
  WORK: '打工', STUDY: '读书', BOTTLE: '捞瓶', BATTLE: '对战',
}

export const PANELS: { key: PanelKey; label: string; emoji: string }[] = [
  { key: 'home', label: '家园', emoji: '🏠' },
  { key: 'care', label: '养成', emoji: '🎒' },
  { key: 'daily', label: '任务', emoji: '✅' },
  { key: 'center', label: '活动中心', emoji: '📋' },
  { key: 'social', label: '社交', emoji: '🤝' },
  { key: 'work', label: '打工', emoji: '💼' },
  { key: 'study', label: '读书', emoji: '📚' },
  { key: 'bottle', label: '捞瓶', emoji: '🍾' },
  { key: 'battle', label: '对战', emoji: '⚔️' },
  // R19：协作副本用 🗡️ 与旧单宠对战 ⚔️ 区分
  { key: 'pve', label: '协作副本', emoji: '🗡️' },
  { key: 'chat', label: '聊天', emoji: '💬' },
  { key: 'achievements', label: '成就', emoji: '🏆' },
  { key: 'rankings', label: '榜单', emoji: '🥇' },
  { key: 'seasonpass', label: '赛季通行证', emoji: '🎫' },
  { key: 'linkedproduct', label: '实物权益', emoji: '🎁' },
  { key: 'reminders', label: '提醒', emoji: '🔔' },
  { key: 'companion', label: '陪伴', emoji: '🫶' },
]

export const PANEL_TITLE = Object.fromEntries(PANELS.map((item) => [item.key, item.label])) as Record<PanelKey, string>
export const PANEL_ITEM = Object.fromEntries(PANELS.map((item) => [item.key, item])) as unknown as Record<PanelKey, { emoji: string; label: string }>

/** 分组宫格（对齐 Web/Taro：La Maison / Croissance / Les Amis / Archives） */
export const MENU_GROUPS: Array<{ label: string; hint: string; keys: PanelKey[] }> = [
  { label: 'La Maison', hint: '小家与陪伴', keys: ['home', 'companion'] },
  // R19：seasonpass 随每日任务成长、pve 邻接旧对战、linkedproduct 随喂养养成
  { label: 'Croissance', hint: '养成', keys: ['daily', 'center', 'seasonpass', 'work', 'study', 'bottle', 'battle', 'pve', 'care', 'linkedproduct'] },
  { label: 'Les Amis', hint: '往来', keys: ['social', 'chat'] },
  { label: 'Archives', hint: '记录', keys: ['achievements', 'rankings', 'reminders'] },
]

/**
 * R19 菜单门控：capability 显式 false 才隐藏入口；bootstrap 缺失（加载失败）不隐藏，
 * 写操作仍由服务端权威裁决（未知不隐藏，避免快照故障锁死功能）。
 * cooperativePve 为整功能开关（入口隐藏 + 面板内"暂未开放"兜底）；
 * seasonPass/linkedProduct 关闭时入口保留，面板内分别降级为只读/隐藏兑换。
 */
export const MENU_HIDDEN_BY_CAPABILITY: Partial<Record<PanelKey, string>> = {
  pve: 'cooperativePve',
}

export const RANKING_TABS: { key: PetRankingType; label: string }[] = [
  { key: 'LEVEL', label: '等级榜' },
  { key: 'BATTLE_WIN', label: '胜场榜' },
  { key: 'BOTTLE', label: '捞瓶榜' },
]

export const SHARE_TYPES: { key: Parameters<typeof petApi.getShareCard>[0]; label: string }[] = [
  { key: 'DAILY', label: '日常卡片' },
  { key: 'LEVEL_UP', label: '升级卡片' },
  { key: 'ACHIEVEMENT', label: '成就卡片' },
  { key: 'BOTTLE', label: '漂流瓶卡片' },
  { key: 'BATTLE', label: '对战卡片' },
]

/** 宿主 → Cocos 消息（与 pet-game PetGameBridge 同协议） */
export type HostToGame =
  | { source: 'pet-host'; type: 'init'; pet: Record<string, unknown> }
  | { source: 'pet-host'; type: 'petState'; pet: Record<string, unknown> }
  | { source: 'pet-host'; type: 'actionResult'; action: string; ok: boolean; message?: string }
  | { source: 'pet-host'; type: 'battleRounds'; rounds: Record<string, unknown>[]; won: boolean }
  | { source: 'pet-host'; type: 'chatBubble'; content: string }

export interface BattleRound {
  round: number
  actorName: string
  action: string
  damage: number
  critical: boolean
  dodged: boolean
  targetName: string
  targetRemainingHp: number
}

export function toDisplayState(pet: PetInfo): Record<string, unknown> {
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

export function formatClock(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds))
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, '0')}`
}

export function parseResult<T>(raw: string | null | undefined): T | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function ChipButton({ label, onPress, disabled, primary }: { label: string; onPress: () => void; disabled?: boolean; primary?: boolean }) {
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

/** R19：协作副本/通行证等写操作的前端请求键（服务端按键幂等收敛） */
export function newR19RequestKey(): string {
  return generateAttachmentId().replace(/-/g, '')
}

/** 进行中任务倒计时：严格按服务端 finishedAt 计算，客户端不做任何数值推断 */
export function useCountdown(finishedAt: string | null): number {
  const [remaining, setRemaining] = useState(0)
  useEffect(() => {
    if (!finishedAt) {
      // 异步边界内重置（避免 effect 同步 setState 级联渲染）
      async function reset() {
        setRemaining(0)
      }
      void reset()
      return
    }
    let active = true
    const compute = () => {
      const target = Date.parse(finishedAt.length === 19 ? `${finishedAt}Z` : finishedAt)
      return Number.isNaN(target) ? 0 : Math.max(0, Math.floor((target - Date.now()) / 1000))
    }
    async function init() {
      if (active) setRemaining(compute())
    }
    void init()
    const timer = setInterval(() => {
      if (!active) return
      const next = compute()
      setRemaining(next)
      if (next <= 0) clearInterval(timer)
    }, 1000)
    return () => {
      active = false
      clearInterval(timer)
    }
  }, [finishedAt])
  return remaining
}
