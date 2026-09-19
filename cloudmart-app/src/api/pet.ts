import request from '@/utils/request'
import type { ApiResponse } from '@/types'

// ========== 社区宠物（契约对齐 mall-pet，Web/App/小程序三端同构；实施文档 §4） ==========
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
  /** 进化阶段（0 未进化/1 一阶/2 二阶；原文档 §89） */
  evolutionStage: number
  /** 当前穿戴皮肤编码（null=原生外观） */
  skinCode: string | null
  /** 拥有的宠物数量（多宠物） */
  petCount: number
  /** 宠物数量上限 */
  maxPets: number
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
  /** 快照还原的挑战方宠物名（PvP 对手改名/放生不影响历史记录） */
  attackerPetName: string | null
  attackerUserId: number | string
  defenderPetId: number | string
  defenderPetName: string | null
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

export interface PetRankingItem {
  rank: number
  petId: number | string
  name: string
  species: PetSpecies
  level: number
  value: number
  ownerUserId: number | string
  ownerNickname: string
  isMe: boolean
}

export interface PetRankingResult {
  top20: PetRankingItem[]
  myValue: number | null
  myRank: number | null
}

export interface PetShareCard {
  type: string
  title: string
  content: string
  highlight: string | null
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

/** 宠物口吻提醒（复用 mall-notification type=PET，已读回写走 /notification 接口） */
export interface PetReminder {
  notificationId: number | string
  reminderType: string
  title: string
  content: string
  bizId: number | string | null
  isRead: boolean
  createdAt: string | null
  /** 原文档 §30：P0 重要 / P1 普通 / P2 低 */
  priority: 'P0' | 'P1' | 'P2' | null
}

/** 他人主页宠物卡片（宠物不公开时 404 PET_NOT_PUBLIC） */
export interface PetPublicCard {
  petId: number | string
  name: string
  species: PetSpecies
  level: number
  growthStage: PetGrowthStage
  personality: PetPersonality
  achievementCount: number
  ownerUserId: number | string
}

/** 排行榜维度 */
export type PetRankingType = 'LEVEL' | 'BATTLE_WIN' | 'BOTTLE'

/** 捞瓶结果（result JSON 反序列化；服务端计算，客户端只读） */
export interface PetBottleResult {
  outcome: 'CAUGHT' | 'EMPTY' | 'FAILED'
  rarity: 'NORMAL' | 'RARE' | 'PET' | 'EASTER_EGG'
  specialContent: string
  bottleId: number | string
  exp: number
  successRate: number
}

// ========== 二期能力（原文档 §1.1 宠物串门 / §89 多宠物·装备·技能·进化·皮肤商城·社区活动） ==========

/** 宠物摘要（多宠物切换列表） */
export interface PetSummary {
  petId: number | string
  name: string
  species: PetSpecies
  appearance: string
  personality: PetPersonality
  level: number
  growthStage: PetGrowthStage
  evolutionStage: number
  skinCode: string | null
  hp: number
  maxHp: number
  hunger: number
  happiness: number
  energy: number
  cleanliness: number
  isActive: boolean
}

export type PetItemType = 'EQUIPMENT' | 'SKIN' | 'SKILL_BOOK'

/** 商城商品（装备/皮肤/技能书统一结构） */
export interface PetShopItem {
  itemType: PetItemType
  code: string
  name: string
  description: string
  icon: string
  rarity: 'COMMON' | 'RARE' | 'EPIC'
  priceStarlight: number
  slot: string | null
  species: string | null
  color: string | null
  accessory: string | null
  skillType: 'ACTIVE' | 'PASSIVE' | null
  effect: string | null
  effectValue: number | null
  bonusStrength: number
  bonusIntelligence: number
  bonusAgility: number
  bonusCharm: number
  bonusMaxHp: number
  requiredLevel: number
  requiredEvolutionStage: number
  owned: boolean
  eligible: boolean
  lockReason: string | null
}

export interface PetShopResult {
  /** 星光余额（null=余额服务降级，前端隐藏） */
  starlightBalance: number | null
  items: PetShopItem[]
}

/** 背包物品 */
export interface PetInventoryItem {
  itemType: PetItemType
  code: string
  name: string
  description: string
  icon: string
  rarity: string
  slot: string | null
  color: string | null
  accessory: string | null
  effect: string | null
  effectValue: number | null
  bonusStrength: number
  bonusIntelligence: number
  bonusAgility: number
  bonusCharm: number
  bonusMaxHp: number
  equipped: boolean
  quantity: number
  used: boolean
  acquiredAt: string | null
}

/** 宠物技能 */
export interface PetSkillItem {
  code: string
  name: string
  description: string
  skillType: 'ACTIVE' | 'PASSIVE'
  effect: string
  effectValue: number
  effectText: string
  icon: string
  priceStarlight: number
  requiredLevel: number
  learned: boolean
  equipped: boolean
  bookOwned: boolean
  eligible: boolean
  lockReason: string | null
}

/** 进化状态 */
export interface PetEvolutionStatus {
  currentStage: number
  maxStage: number
  nextCode: string | null
  nextName: string | null
  nextDescription: string | null
  requiredLevel: number | null
  costStarlight: number | null
  bonusMaxHp: number | null
  bonusStrength: number | null
  bonusIntelligence: number | null
  bonusAgility: number | null
  bonusCharm: number | null
  unlockSkinCode: string | null
  icon: string | null
  canEvolve: boolean
  lockReason: string | null
}

/** 社区宠物活动 */
export interface PetEventItem {
  code: string
  name: string
  description: string
  eventType: 'BOTTLE' | 'BATTLE' | 'WORK' | 'STUDY' | 'FEED' | 'PLAY' | 'VISIT'
  targetValue: number
  progress: number
  completed: boolean
  claimable: boolean
  claimed: boolean
  expired: boolean
  rewardStarlight: number
  rewardExp: number
  rewardItemCode: string | null
  startsAt: string | null
  endsAt: string | null
  claimedAt: string | null
}

/** 串门邻居 */
export interface PetVisitNeighbor {
  petId: number | string
  name: string
  species: PetSpecies
  level: number
  growthStage: PetGrowthStage
  evolutionStage: number
  skinCode: string | null
  ownerUserId: number | string
  ownerNickname: string
  visitedToday: boolean
  lastVisitedAt: string | null
}

/** 串门结果 */
export interface PetVisitResult {
  neighborName: string
  ownerNickname: string
  happinessGain: number
  expGain: number
  message: string
  pet: PetInfo
}

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) return ''
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return qs ? `?${qs}` : ''
}

/** 我的宠物（未领养 404 PET_NOT_FOUND） */
export const petApi = {
  getMyPet: () => request<PetInfo>({ url: '/pet/me' }),

  /** 领养（重复领养 409；种类/性格/外观白名单校验 400） */
  createPet: (data: { name: string; species: PetSpecies; color?: string; accessory?: string; personality: PetPersonality }) =>
    request<PetInfo>({ url: '/pet/create', method: 'POST', data: data as unknown as Record<string, unknown> }),

  /** 改名（30 天一次 409 PET_RENAME_COOLDOWN） */
  renamePet: (data: { name: string }) =>
    request<PetInfo>({ url: '/pet/name', method: 'PUT', data: data as unknown as Record<string, unknown> }),

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
    request<PetBattleItem>({ url: '/pet/battle/challenge', method: 'POST', data: data as unknown as Record<string, unknown> }),
  acceptBattle: (battleId: number | string) =>
    request<PetBattleItem>({ url: `/pet/battle/${battleId}/accept`, method: 'POST' }),
  declineBattle: (battleId: number | string) =>
    request<PetBattleItem>({ url: `/pet/battle/${battleId}/decline`, method: 'POST' }),
  getBattleDetail: (battleId: number | string) =>
    request<PetBattleItem>({ url: `/pet/battle/${battleId}` }),
  listBattleHistory: (params?: { page?: number; pageSize?: number }) =>
    request<PetBattleItem[]>({ url: `/pet/battle/history${buildQuery(params)}` }),

  /** 聊天（每日 20 次 429；AI 故障降级模板 isAiReply=false） */
  chat: (message: string) =>
    request<PetChatMessage>({ url: '/pet/chat', method: 'POST', data: { message } }),
  chatHistory: (params?: { cursor?: number | string; pageSize?: number }) =>
    request<PetChatMessage[]>({ url: `/pet/chat/history${buildQuery(params)}` }),

  /** 宠物口吻提醒（原文档 §27；已读回写走 notificationApi） */
  listReminders: () => request<PetReminder[]>({ url: '/pet/reminders' }),
  getReminderUnreadCount: () => request<number>({ url: '/pet/reminders/unread-count' }),

  /** 主页公开开关（未公开不入排行榜、他人主页不可见） */
  updatePrivacy: (data: { isPublic: boolean }) =>
    request<void>({ url: '/pet/privacy', method: 'PUT', data }),

  /** 成就墙（未达成灰显） */
  listAchievements: () => request<PetAchievement[]>({ url: '/pet/achievements' }),

  /** 宠物排行榜（原文档 §80；仅公开宠物入榜） */
  getRankings: (type: PetRankingType) =>
    request<PetRankingResult>({ url: `/pet/rankings${buildQuery({ type })}` }),
  /** 宠物动态分享卡片（文案服务端生成） */
  getShareCard: (type: 'LEVEL_UP' | 'ACHIEVEMENT' | 'BOTTLE' | 'BATTLE' | 'DAILY') =>
    request<PetShareCard>({ url: `/pet/share/card${buildQuery({ type })}` }),
  /** 修改外观（档案编辑用） */
  updateAppearance: (data: { color: string; accessory: string }) =>
    request<PetInfo>({ url: '/pet/appearance', method: 'PUT', data }),

  /** 他人主页宠物卡片（未公开/无宠物 404） */
  getPublicCard: (userId: number | string) =>
    request<PetPublicCard>({ url: `/pet/public/${userId}` }),

  // ---- 二期：多宠物 / 商城 / 背包 / 技能 / 进化 / 活动 / 串门 ----

  /** 我的宠物列表（多宠物切换） */
  listMyPets: () => request<PetSummary[]>({ url: '/pet/pets' }),
  /** 切换主宠（日常玩法作用于主宠） */
  activatePet: (petId: number | string) =>
    request<PetSummary>({ url: `/pet/pets/${petId}/activate`, method: 'POST' }),

  /** 宠物商城（装备/皮肤/技能书 + 星光余额） */
  getShop: () => request<PetShopResult>({ url: '/pet/shop' }),
  /** 购买物品（先入包再扣星光；余额不足 402，重复购买 409） */
  buyItem: (data: { itemType: PetItemType; itemCode: string }) =>
    request<PetInventoryItem>({ url: '/pet/shop/buy', method: 'POST', data: data as unknown as Record<string, unknown> }),

  /** 宠物背包 */
  listInventory: () => request<PetInventoryItem[]>({ url: '/pet/inventory' }),
  /** 穿戴装备（同部位自动换下旧的） */
  equipItem: (itemCode: string) =>
    request<PetInfo>({ url: '/pet/inventory/equip', method: 'POST', data: { itemCode } }),
  /** 卸下装备（slot: HAT/NECKLACE/SCARF/BACKPACK） */
  unequipItem: (slot: string) =>
    request<PetInfo>({ url: `/pet/inventory/unequip${buildQuery({ slot })}`, method: 'POST' }),
  /** 穿戴皮肤（种类不匹配 400） */
  wearSkin: (skinCode: string) =>
    request<PetInfo>({ url: '/pet/inventory/skin', method: 'POST', data: { skinCode } }),
  /** 卸下皮肤（恢复原生外观） */
  removeSkin: () => request<PetInfo>({ url: '/pet/inventory/skin/remove', method: 'POST' }),

  /** 技能列表（含学习/背包状态） */
  listSkills: () => request<PetSkillItem[]>({ url: '/pet/skills' }),
  /** 学习技能（需背包已有技能书） */
  learnSkill: (skillCode: string) =>
    request<PetSkillItem>({ url: '/pet/skills/learn', method: 'POST', data: { skillCode } }),

  /** 进化状态 */
  getEvolution: () => request<PetEvolutionStatus>({ url: '/pet/evolution' }),
  /** 执行进化（等级不足/已满阶 409；星光不足 402） */
  evolve: () => request<PetEvolutionStatus>({ url: '/pet/evolution/evolve', method: 'POST' }),

  /** 社区宠物活动列表 */
  listEvents: () => request<PetEventItem[]>({ url: '/pet/events' }),
  /** 领取活动奖励 */
  claimEvent: (eventCode: string) =>
    request<PetEventItem>({ url: `/pet/events/${eventCode}/claim`, method: 'POST' }),

  /** 串门邻居列表 */
  listVisitNeighbors: () => request<PetVisitNeighbor[]>({ url: '/pet/visit/neighbors' }),
  /** 让宠物去串门 */
  visitNeighbor: (petId: number | string) =>
    request<PetVisitResult>({ url: `/pet/visit/${petId}`, method: 'POST' }),
}
