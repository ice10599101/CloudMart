/**
 * PetStage 宿主侧桥（与 pet-game/assets/scripts/PetGameBridge.ts 同一协议，实施文档 §2.2）。
 *
 * 宿主 → 游戏：iframe.contentWindow.postMessage（同源）；
 * 游戏 → 宿主：window message 事件。
 */

export interface PetDisplayState {
  name: string
  species: string
  /** 性别: MALE/FEMALE（可选；旧数据缺省按 MALE 展示） */
  gender?: string
  growthStage: string
  level: number
  expPercent: number
  hp: number
  maxHp: number
  hunger: number
  happiness: number
  energy: number
  cleanliness: number
  status: string
  activityName?: string
  speech?: string
  /** 外观主色键（皮肤可改变；Cocos 调色板使用，缺省按种类配色） */
  color?: string
  /** 配饰键（皮肤可改变；Cocos 仅做展示性提示） */
  accessory?: string
  /** 进化阶段（0 未进化；Cocos 用于体型/光效强度） */
  evolutionStage?: number
}

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

export type PetIntentAction =
  // 三期：家园 / 每日任务 / 社交 / 职业（Cocos 场景按钮与宿主面板一一对应）
  | 'openRoom' | 'openDaily' | 'openSocial' | 'openCareer'
  | 'feed'
  | 'play'
  | 'clean'
  | 'rest'
  | 'openWork'
  | 'openStudy'
  | 'openBottle'
  | 'openBattle'
  | 'openChat'
  | 'openAchievements'
  | 'openProfile'
  | 'openRankings'
  /** 养成面板（商城/背包/技能/进化/活动/串门/多宠物；原文档 §89） */
  | 'openCare'

export type HostToGame =
  | { source: 'pet-host'; type: 'init'; pet: PetDisplayState; theme?: { dark: boolean } }
  | { source: 'pet-host'; type: 'petState'; pet: PetDisplayState }
  | { source: 'pet-host'; type: 'actionResult'; action: string; ok: boolean; message?: string }
  | { source: 'pet-host'; type: 'battleRounds'; rounds: BattleRound[]; won: boolean }
  | { source: 'pet-host'; type: 'chatBubble'; content: string }

export type GameToHost =
  | { source: 'pet-game'; type: 'ready' }
  | { source: 'pet-game'; type: 'intent'; action: PetIntentAction }
  | { source: 'pet-game'; type: 'petTapped' }

/** 游戏 → 宿主 的动作枚举白名单（FE-05：未知动作一律丢弃） */
const PET_INTENT_ACTIONS: ReadonlySet<string> = new Set([
  'openRoom', 'openDaily', 'openSocial', 'openCareer',
  'feed', 'play', 'clean', 'rest',
  'openWork', 'openStudy', 'openBottle', 'openBattle',
  'openChat', 'openAchievements', 'openProfile', 'openRankings', 'openCare',
])

/**
 * 游戏 → 宿主 消息类型守卫（FE-05/T34）：
 * - source 字段校验 + type 枚举白名单 + intent.action 白名单；
 * - 字段长度受限（title/content 类字段即使被伪造也不进入宿主业务）；
 * - 注意：仅凭 data 不足以信任来源——宿主侧必须同时校验 event.origin 与
 *   event.source === iframe.contentWindow（见 CocosStage 的 onMessage）。
 */
export function isGameToHost(data: unknown): data is GameToHost {
  if (typeof data !== 'object' || data === null) {
    return false
  }
  const msg = data as { source?: unknown; type?: unknown; action?: unknown }
  if (msg.source !== 'pet-game') {
    return false
  }
  switch (msg.type) {
    case 'ready':
    case 'petTapped':
      return true
    case 'intent':
      return typeof msg.action === 'string' && PET_INTENT_ACTIONS.has(msg.action)
    default:
      return false
  }
}

export const PET_GAME_FRAME_PATH = '/pet-game/index.html'
/** ready 超时（毫秒）：超时即判定构建产物缺失/加载失败，宿主 Fail-Open 切原生降级舞台 */
// Cocos 产物冷加载（引擎+场景+资源）本机也常超 15s，放宽到 30s；配合稳定缓存戳后常态命中 HTTP 缓存
export const PET_GAME_READY_TIMEOUT_MS = 30000
