import request from '@/utils/request'

// ========== 社区宠物（契约对齐 mall-pet，与 Web/App 端同构；实施文档 §5） ==========
// 数值全部服务端计算：客户端只发意图（POST /pet/feed 等），不携带任何数值字段

export type PetSpecies = 'CAT' | 'DOG' | 'RABBIT' | 'FOX' | 'PANDA'
export type PetPersonality = 'LIVELY' | 'GENTLE' | 'TSUNDERE' | 'SIMPLE' | 'COOL' | 'CHATTERBOX'
export type PetGrowthStage = 'BABY' | 'YOUNG' | 'ADULT'
export type PetActivityType = 'WORK' | 'STUDY' | 'BOTTLE_FISHING' | 'REST'

/** 我的宠物（懒更新结算后的权威状态） */
export interface PetInfo {
  petId: number | string
  userId: number | string
  name: string
  species: PetSpecies
  appearance: string
  personality: PetPersonality
  level: number
  exp: number
  expToNext: number
  growthStage: PetGrowthStage
  hp: number
  maxHp: number
  hunger: number
  happiness: number
  energy: number
  cleanliness: number
  strength: number
  intelligence: number
  agility: number
  charm: number
  status: string
  activityType: PetActivityType | null
  activityFinishedAt: string | null
  claimableActivityType: PetActivityType | null
  isPublic: boolean
  feedRemainingToday: number | null
  lastStateUpdateAt: string
}

export interface PetActivityItem {
  activityId: number | string
  activityType: PetActivityType
  configId: number | string | null
  configName: string | null
  status: 'IN_PROGRESS' | 'COMPLETED' | 'CLAIMED' | 'EXPIRED'
  startedAt: string | null
  finishedAt: string | null
  remainingSeconds: number
  canClaim: boolean
  claimedAt: string | null
  result: string | null
}

export interface PetJobItem {
  configId: number | string
  name: string
  description: string
  durationSeconds: number
  energyCost: number
  hungerCost: number
  expReward: number
  currencyReward: number
  requiredLevel: number
  eligible: boolean
}

export interface PetStudyItem {
  configId: number | string
  name: string
  description: string
  category: string
  durationSeconds: number
  energyCost: number
  expReward: number
  intelligenceReward: number
  requiredLevel: number
  eligible: boolean
}

export interface PetBottleStatus {
  fishing: boolean
  activityId: number | string | null
  startedAt: string | null
  finishedAt: string | null
  remainingSeconds: number
  canClaim: boolean
  cooldownRemainingSeconds: number
  lastOutcome: 'CAUGHT' | 'EMPTY' | 'FAILED' | null
  lastBottleId: number | string | null
  estimatedSuccessRate: number
  unlockedArea: string
}

export interface PetBattleItem {
  battleId: number | string
  mode: 'PVE' | 'PVP'
  status: 'PENDING' | 'FINISHED' | 'DECLINED' | 'EXPIRED'
  role: 'ATTACKER' | 'DEFENDER'
  attackerPetId: number | string
  attackerUserId: number | string
  defenderPetId: number | string
  defenderUserId: number | string | null
  winnerPetId: number | string | null
  rounds: string | null
  expReward: number
  currencyReward: number
  startedAt: string | null
  finishedAt: string | null
}

export interface PetOpponent {
  petId: number | string
  name: string
  species: string
  level: number
  growthStage: string
  isWild: boolean
  ownerUserId: number | string | null
  ownerNickname: string
}

export interface PetChatMessage {
  messageId: number | string
  role: 'USER' | 'PET' | 'SYSTEM'
  content: string
  isAiReply: boolean
  createdAt: string | null
}

export interface PetAchievement {
  achievementId: number | string
  code: string
  name: string
  description: string
  icon: string
  conditionValue: number
  expReward: number
  achieved: boolean
  achievedAt: string | null
}

/** Cocos 舞台页地址（pet-game web-mobile 构建产物；部署在网关同源静态目录） */
export const PET_STAGE_URL = `${process.env.TARO_APP_API_HOST || '127.0.0.1:8090'}/pet-game/index.html`

export const petApi = {
  /** 我的宠物（未领养 404 PET_NOT_FOUND） */
  getMyPet: () => request<PetInfo>({ url: '/pet/me' }),

  /** 领养（重复领养 409；种类/性格/外观白名单校验 400） */
  createPet: (data: { name: string; species: PetSpecies; color?: string; accessory?: string; personality: PetPersonality }) =>
    request<PetInfo>({ url: '/pet/create', method: 'POST', data }),

  /** 改名（30 天一次 409 PET_RENAME_COOLDOWN） */
  renamePet: (data: { name: string }) =>
    request<PetInfo>({ url: '/pet/name', method: 'PUT', data }),

  /** 基础互动（数值/限频/经验全部服务端结算） */
  feed: () => request<PetInfo>({ url: '/pet/feed', method: 'POST' }),
  play: () => request<PetInfo>({ url: '/pet/play', method: 'POST' }),
  clean: () => request<PetInfo>({ url: '/pet/clean', method: 'POST' }),
  rest: () => request<PetInfo>({ url: '/pet/rest', method: 'POST' }),

  /** 打工岗位列表（参数服务端下发，前端禁止硬编码数值） */
  listJobs: () => request<PetJobItem[]>({ url: '/pet/jobs' }),
  startWork: (configId: number | string) =>
    request<PetActivityItem>({ url: '/pet/work/start', method: 'POST', data: { configId } }),
  claimWork: () => request<PetActivityItem>({ url: '/pet/work/claim', method: 'POST' }),

  /** 读书课程列表 */
  listStudies: () => request<PetStudyItem[]>({ url: '/pet/studies' }),
  startStudy: (configId: number | string) =>
    request<PetActivityItem>({ url: '/pet/study/start', method: 'POST', data: { configId } }),
  claimStudy: () => request<PetActivityItem>({ url: '/pet/study/claim', method: 'POST' }),

  /** 捞瓶状态（查询即触发到期惰性结算） */
  getBottleStatus: () => request<PetBottleStatus>({ url: '/pet/bottle/status' }),
  startBottle: () => request<PetActivityItem>({ url: '/pet/bottle/start', method: 'POST' }),
  claimBottle: () => request<PetActivityItem>({ url: '/pet/bottle/claim', method: 'POST' }),

  /** 对战候选（PvE 野生 + PvP 真实宠物） */
  listOpponents: () => request<PetOpponent[]>({ url: '/pet/battle/opponents' }),
  challenge: (data: { mode: 'PVE' | 'PVP'; defenderPetId?: number | string }) =>
    request<PetBattleItem>({ url: '/pet/battle/challenge', method: 'POST', data }),
  acceptBattle: (battleId: number | string) =>
    request<PetBattleItem>({ url: `/pet/battle/${battleId}/accept`, method: 'POST' }),
  declineBattle: (battleId: number | string) =>
    request<PetBattleItem>({ url: `/pet/battle/${battleId}/decline`, method: 'POST' }),
  listBattleHistory: (params?: { page?: number; pageSize?: number }) =>
    request<PetBattleItem[]>({ url: '/pet/battle/history', data: params }),

  /** 聊天（每日 20 次 429；AI 故障降级模板 isAiReply=false） */
  chat: (message: string) =>
    request<PetChatMessage>({ url: '/pet/chat', method: 'POST', data: { message } }),
  chatHistory: (params?: { cursor?: number | string; pageSize?: number }) =>
    request<PetChatMessage[]>({ url: '/pet/chat/history', data: params }),

  /** 成就墙（未达成灰显） */
  listAchievements: () => request<PetAchievement[]>({ url: '/pet/achievements' }),

  /** 他人主页宠物卡片（未公开/无宠物 404） */
  getPublicCard: (userId: number | string) =>
    request<PetInfo>({ url: `/pet/public/${userId}` }),
}
