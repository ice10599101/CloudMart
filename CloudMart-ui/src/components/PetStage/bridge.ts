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

/** 游戏 → 宿主 消息类型守卫（外部 postMessage 一律先过这里） */
export function isGameToHost(data: unknown): data is GameToHost {
  return (
    typeof data === 'object' && data !== null && (data as { source?: string }).source === 'pet-game'
  )
}

export const PET_GAME_FRAME_PATH = '/pet-game/index.html'
/** ready 超时（毫秒）：超时即判定构建产物缺失/加载失败，宿主 Fail-Open 切原生降级舞台 */
export const PET_GAME_READY_TIMEOUT_MS = 2500
