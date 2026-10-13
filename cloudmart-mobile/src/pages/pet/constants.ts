import { useEffect, useState } from 'react'
import { petApi, type PetRankingType } from '@/api/pet'

/**
 * 宠物页共享常量 / 纯函数 / 倒计时 hook（R30 自 index.tsx 拆出，行为不变）。
 */

export const SPECIES_OPTIONS = [
  { value: 'STRAWBERRY', emoji: '🍓', label: '草莓' },
  { value: 'ORANGE', emoji: '🍊', label: '橘子' },
  { value: 'WATERMELON', emoji: '🍉', label: '西瓜' },
  { value: 'BLUEBERRY', emoji: '🫐', label: '蓝莓' },
  { value: 'DRAGONFRUIT', emoji: '🐉', label: '火龙果' },
] as const

/** 领养可选性别（与服务端 PetGender 一致） */
export const GENDER_OPTIONS = [
  { value: 'MALE', label: '♂ 男' },
  { value: 'FEMALE', label: '♀ 女' },
] as const

export const PERSONALITY_OPTIONS = [
  { value: 'LIVELY', label: '活泼' },
  { value: 'GENTLE', label: '温柔' },
  { value: 'TSUNDERE', label: '傲娇' },
  { value: 'SIMPLE', label: '憨厚' },
  { value: 'COOL', label: '高冷' },
  { value: 'CHATTERBOX', label: '话痨' },
] as const

export const SPECIES_EMOJI: Record<string, string> = {
  STRAWBERRY: '🍓',
  ORANGE: '🍊',
  WATERMELON: '🍉',
  BLUEBERRY: '🫐',
  DRAGONFRUIT: '🐉',
}
export const STATUS_LABEL: Record<string, string> = {
  IDLE: '悠闲中', WORKING: '打工中', STUDYING: '读书中', FISHING: '捞瓶中', RESTING: '休息中',
  WEAK: '饿坏了！快喂食', SICK: '不开心病了，多陪陪它',
}
export const GROWTH_STAGE_LABEL: Record<string, string> = { BABY: '幼年', YOUNG: '成长期', ADULT: '成年' }
export const ACTIVITY_LABEL: Record<string, string> = { WORK: '打工', STUDY: '读书', BOTTLE_FISHING: '捞漂流瓶' }
export const PET_COLORS = ['orange', 'white', 'black', 'gray', 'brown']
export const ACCESSORIES = [
  { key: 'none', label: '无' },
  { key: 'bell', label: '铃铛' },
  { key: 'bow', label: '领结' },
  { key: 'glasses', label: '眼镜' },
  { key: 'scarf', label: '围巾' },
] as const
export const PRIORITY_LABEL: Record<string, string> = { P0: '重要', P1: '普通', P2: '低' }
export const SLOT_LABEL: Record<string, string> = { HAT: '帽子', NECKLACE: '项圈', SCARF: '围巾', BACKPACK: '背包' }
export const ITEM_TYPE_LABEL: Record<string, string> = { EQUIPMENT: '装备', SKIN: '皮肤', SKILL_BOOK: '技能书' }
/** B06 动作可执行性的展示名（action 码 → 中文） */
export const ACTION_LABEL: Record<string, string> = {
  FEED: '喂食', PLAY: '玩耍', CLEAN: '清洁', REST: '休息',
  WORK: '打工', STUDY: '读书', BOTTLE: '捞瓶', BATTLE: '对战',
}
export const EVENT_TYPE_LABEL: Record<string, string> = {
  BOTTLE: '捞瓶', BATTLE: '对战胜场', WORK: '打工', STUDY: '读书', FEED: '喂食', PLAY: '玩耍', VISIT: '串门',
}

/** 养成面板子页签（原文档 §89 商城/背包/技能/进化/活动 + §1.1 串门 + 多宠物） */
export type CareTab = 'shop' | 'inventory' | 'skills' | 'evolution' | 'events' | 'visit' | 'career' | 'pets'
export const CARE_TABS: Array<{ key: CareTab; label: string }> = [
  { key: 'shop', label: '🛒 商城' },
  { key: 'inventory', label: '🎒 背包' },
  { key: 'skills', label: '🌟 技能' },
  { key: 'evolution', label: '🌠 进化' },
  { key: 'events', label: '🎯 活动' },
  { key: 'visit', label: '🚪 串门' },
  { key: 'pets', label: '🐾 宠物' },
]
export const RARITY_LABEL: Record<string, string> = {
  NORMAL: '普通瓶', RARE: '稀有瓶', PET: '宠物瓶', EASTER_EGG: '彩蛋瓶',
}
export const RANKING_TABS: Array<{ key: PetRankingType; label: string }> = [
  { key: 'LEVEL', label: '等级榜' },
  { key: 'BATTLE_WIN', label: '胜场榜' },
  { key: 'BOTTLE', label: '捞瓶榜' },
]
export const SHARE_TYPES: Array<{ key: Parameters<typeof petApi.getShareCard>[0]; label: string }> = [
  { key: 'DAILY', label: '日常卡片' },
  { key: 'LEVEL_UP', label: '升级卡片' },
  { key: 'ACHIEVEMENT', label: '成就卡片' },
  { key: 'BOTTLE', label: '漂流瓶卡片' },
  { key: 'BATTLE', label: '对战卡片' },
  { key: 'COLLECTION', label: '图鉴卡片' },
]

export type PanelKey =
  | 'home' | 'care' | 'work' | 'study' | 'bottle' | 'battle'
  | 'chat' | 'achievements' | 'rankings' | 'reminders'
  | 'daily' | 'social' | 'memory' | 'center' | 'pve'

export const PANELS: Array<{ key: PanelKey; label: string; emoji: string }> = [
  { key: 'home', label: '家园', emoji: '🏠' },
  { key: 'memory', label: '记忆', emoji: '🧠' },
  { key: 'daily', label: '任务', emoji: '✅' },
  { key: 'center', label: '活动中心', emoji: '📋' },
  { key: 'pve', label: '协作副本', emoji: '🗡️' },
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

export const PANEL_TITLE = Object.fromEntries(PANELS.map((item) => [item.key, item.label])) as Record<PanelKey, string>

/** 功能宫格分组（对齐 Web 端 PANEL_GROUPS：La Maison / Croissance / Les Amis / Archives） */
export const MENU_GROUPS: Array<{ label: string; hint: string; keys: PanelKey[] }> = [
  { label: 'La Maison', hint: '小家与记忆', keys: ['home', 'memory'] },
  { label: 'Croissance', hint: '养成', keys: ['daily', 'center', 'pve', 'work', 'study', 'bottle', 'battle', 'care'] },
  { label: 'Les Amis', hint: '往来', keys: ['social', 'chat'] },
  { label: 'Archives', hint: '记录', keys: ['achievements', 'rankings', 'reminders'] },
]

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

export function friendlyError(error: unknown): string {
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

export function parseResult<T>(raw: string | null | undefined): T | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function formatClock(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds))
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, '0')}`
}

/** 服务端返回的时间为 UTC（无时区标记），此处统一补 Z 再参与倒计时计算 */
export function parseUtc(value: string): number {
  return Date.parse(value.length === 19 ? `${value}Z` : value)
}

export function useCountdown(finishedAt: string | null): number {
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
