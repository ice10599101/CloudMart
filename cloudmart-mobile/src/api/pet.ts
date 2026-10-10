import request from '@/utils/request'

// ========== 社区宠物（契约对齐 mall-pet，与 Web/App 端同构；实施文档 §5） ==========
// 数值全部服务端计算：客户端只发意图（POST /pet/feed 等），不携带任何数值字段

/** 宠物种类（V28 水果化后服务端只收五果码；对齐 CloudMart-ui 同名契约） */
export type PetSpecies = 'STRAWBERRY' | 'ORANGE' | 'WATERMELON' | 'BLUEBERRY' | 'DRAGONFRUIT'
/** 宠物性别（领养时选择；服务端对未传默认 MALE） */
export type PetGender = 'MALE' | 'FEMALE'
export type PetPersonality = 'LIVELY' | 'GENTLE' | 'TSUNDERE' | 'SIMPLE' | 'COOL' | 'CHATTERBOX'
export type PetGrowthStage = 'BABY' | 'YOUNG' | 'ADULT'
export type PetActivityType = 'WORK' | 'STUDY' | 'BOTTLE_FISHING' | 'REST'

/** 我的宠物（懒更新结算后的权威状态） */
export interface PetInfo {
  petId: number | string
  userId: number | string
  name: string
  species: PetSpecies
  gender: PetGender
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
  // ---- 三期：亲密度 / 陪伴 / 职业（服务端权威，前端只展示）----
  intimacy: number
  intimacyLevel: number
  intimacyLevelName: string
  intimacyToNext: number
  intimacyExpBonusPercent: number
  companionSeconds: number
  todayCompanionSeconds: number
  companionDays: number
  companionStreak: number
  careerCode: string | null
  careerName: string | null
  careerTier: number | null
  /** 主人称呼（宠物怎么叫主人） */
  ownerTitle?: string
  /** R20：业务日重置点（UTC，客户端倒计时以服务端为准） */
  nextResetAt?: string
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
  gender: PetGender
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

export type PetItemType = 'EQUIPMENT' | 'SKIN' | 'SKILL_BOOK' | 'FOOD' | 'FURNITURE'

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
  balance: number | null
  currency: 'PET_COIN' | 'STARLIGHT'
  items: PetShopItem[]
}

/** F7 纪念日卡片（与后端 AnniversaryVO 对齐） */
export interface PetAnniversary {
  adoptionDays: number
  companionStreak: number
  currentMilestone: { key: string; title: string; daysToGo: number } | null
  nextMilestone: { key: string; title: string; daysToGo: number } | null
}

/** F3 好友动态条目（与后端 FeedItemVO 对齐） */
export interface PetFriendFeedItem {
  feedId: number | string
  actorUserId: number | string
  actorPetId: number | string | null
  eventType: 'LEVEL_UP' | 'WORK_COMPLETED' | 'STUDY_COMPLETED' | 'BATTLE_WIN'
  text: string
  petName: string | null
  createdAt: string | null
}

/** F2 赛季榜单（与后端 SeasonResult 对齐） */
export interface PetSeasonRanking {
  season: { seasonId: number | string; name: string; startsAt: string; endsAt: string; status: string } | null
  top50: Array<{
    rank: number
    petId: number | string
    name: string
    species: string
    level: number
    value: number
    userId: number | string
    ownerNickname: string
    isMe: boolean
  }>
  myRank: number | null
  myLevel: number | null
}

export interface PetSeasonHistoryItem {
  seasonId: number | string
  seasonName: string
  endedAt: string | null
  rankNo: number
  level: number
}

/** F8 宠物人设卡（与后端 PetPersonaVO 对齐） */
export interface PetChatPersona {
  name: string
  personality: string
  personalityText: string
  careerCode: string | null
  careerName: string | null
  phrase: string
  intimacyLevel: number
  intimacyLevelName: string
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
/** 访客日志条目（§6 今日访客；契约对齐 mall-pet VisitorLogVO） */
export interface PetVisitorLog {
  visitorUserId: number
  visitorNickname: string
  visitorPetId: number
  visitorPetName: string
  source: string
  createdAt: string
}

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

/** 舞台产物所在源：显式覆盖 > H5 同源（走 dev 代理，桥接同源）> 网关 */
function resolveStageOrigin(): string {
  const override = process.env.TARO_APP_STAGE_ORIGIN?.trim()
  if (override) return override.replace(/\/+$/, '')
  if (process.env.TARO_ENV === 'h5') return ''
  const raw = (process.env.TARO_APP_API_HOST || 'http://127.0.0.1:8090').trim()
  if (/^https?:\/\//i.test(raw)) return raw.replace(/\/+$/, '')
  return `http://${raw}`
}

/** Cocos 舞台页地址（pet-game web-mobile 构建产物；H5 dev 经 /pet-game 代理同源加载）。
 *  ?v= 产物版本号：产物文件名固定无 hash，web-view/浏览器会缓存旧 js——
 *  每次重新构建 pet-game 后手动递增此值以击穿缓存。 */
const PET_STAGE_VERSION = 'v20261002_1'
export const PET_STAGE_URL = `${resolveStageOrigin()}/pet-game/index.html?${PET_STAGE_VERSION}`

export const petApi = {
  /** 我的宠物（未领养 404 PET_NOT_FOUND） */
  getMyPet: () => request<PetInfo>({ url: '/pet/me' }),

  /** 领养（重复领养 409；种类/性格/外观白名单校验 400） */
  createPet: (data: { name: string; species: PetSpecies; gender?: PetGender; color?: string; accessory?: string; personality: PetPersonality }) =>
    request<PetInfo>({ url: '/pet/create', method: 'POST', data }),

  /** 改名（30 天一次 409 PET_RENAME_COOLDOWN） */
  renamePet: (data: { name: string }) =>
    request<PetInfo>({ url: '/pet/name', method: 'PUT', data }),

  /** 主人称呼设置：宠物怎么叫主人（1~12 字，blank 重置默认「主人」） */
  setOwnerTitle: (ownerTitle: string) =>
    request<PetInfo>({ url: '/pet/owner-title', method: 'PUT', data: { ownerTitle } }),

  /** 基础互动（数值/限频/经验全部服务端结算） */
  feed: () => request<PetInfo>({ url: '/pet/feed', method: 'POST' }),

  /** F1 喂养道具：消耗背包食物恢复状态（效果服务端权威，默认不占免费次数） */
  feedItem: (itemCode: string) =>
    request<PetInfo>({ url: '/pet/feed-item', method: 'POST', data: { itemCode } }),

  /** F7 纪念日卡片：领养天数/陪伴连续/里程碑 */
  getAnniversaries: () =>
    request<PetAnniversary>({ url: '/pet/me/anniversaries', method: 'GET' }),
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
  getBattleDetail: (battleId: number | string) =>
    request<PetBattleItem>({ url: `/pet/battle/${battleId}` }),
  listBattleHistory: (params?: { page?: number; pageSize?: number }) =>
    request<PetBattleItem[]>({ url: '/pet/battle/history', data: params }),

  /** 聊天（每日 20 次 429；AI 故障降级模板 isAiReply=false） */
  chat: (message: string) =>
    request<PetChatMessage>({ url: '/pet/chat', method: 'POST', data: { message } }),
  chatHistory: (params?: { cursor?: number | string; pageSize?: number }) =>
    request<PetChatMessage[]>({ url: '/pet/chat/history', data: params }),

  /** F8 人设卡：名字/性格/口头禅/职业/亲密度（与 AI prompt 同源） */
  getChatPersona: () =>
    request<PetChatPersona>({ url: '/pet/chat/persona', method: 'GET' }),

  /** F2 赛季榜：当前赛季 + Top50 + 我的实时名次 */
  getSeasonRanking: () =>
    request<PetSeasonRanking>({ url: '/pet/rankings/season', method: 'GET' }),

  /** F2 历届我的名次 */
  getSeasonHistory: () =>
    request<PetSeasonHistoryItem[]>({ url: '/pet/rankings/season/history', method: 'GET' }),

  /** F3 好友动态：收件箱游标分页 */
  getFriendFeed: (params?: { beforeId?: number | string; size?: number }) =>
    request<PetFriendFeedItem[]>({ url: '/pet/friends/feed', data: params }),

  /** F3 好友动态未读数 */
  getFriendFeedUnread: () => request<number>({ url: '/pet/friends/feed/unread-count', method: 'GET' }),

  /** F3 标记好友动态已读 */
  markFriendFeedRead: () =>
    request<void>({ url: '/pet/friends/feed/read', method: 'POST' }),

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
    request<PetRankingResult>({ url: '/pet/rankings', data: { type } }),
  /** 宠物动态分享卡片（文案服务端生成） */
  getShareCard: (type: 'LEVEL_UP' | 'ACHIEVEMENT' | 'BOTTLE' | 'BATTLE' | 'DAILY' | 'COLLECTION') =>
    request<PetShareCard>({ url: '/pet/share/card', data: { type } }),
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
    request<PetInventoryItem>({ url: '/pet/shop/buy', method: 'POST', data }),

  /** 宠物背包 */
  listInventory: () => request<PetInventoryItem[]>({ url: '/pet/inventory' }),
  /** 穿戴装备（同部位自动换下旧的） */
  equipItem: (itemCode: string) =>
    request<PetInfo>({ url: '/pet/inventory/equip', method: 'POST', data: { itemCode } }),
  /** 卸下装备（slot: HAT/NECKLACE/SCARF/BACKPACK） */
  unequipItem: (slot: string) =>
    request<PetInfo>({ url: '/pet/inventory/unequip', method: 'POST', data: { slot } }),
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
  /** 访客日志（§6）：今日来访我家的访客列表 */
  listTodayVisitors: () => request<PetVisitorLog[]>({ url: '/pet/home/visitors/today' }),
  /** 让宠物去串门 */
  visitNeighbor: (petId: number | string) =>
    request<PetVisitResult>({ url: `/pet/visit/${petId}`, method: 'POST' }),

  // ==================== 三期：职业 / 家园 / 每日任务 / 关系 / 好友 / 留言墙 / 亲密度 ====================

  /** 亲密度与陪伴 */
  getIntimacy: () => request<PetIntimacyInfo>({ url: '/pet/intimacy' }),
  // ==================== W03：宠物币钱包（§7.8/§8.2） ====================
  // ==================== N 系列：小游戏/托管/摘要/合作/图鉴（§8.3） ====================
  startMinigameRound: (petId: number | string) =>
    request<MinigameRoundVO>({ url: `/pet/pets/${petId}/minigames`, method: 'POST' }),
  submitMinigameOps: (roundId: number | string, ops: Array<{ seq: number; windowIndex: number; slot: string }>) =>
    request<{ accepted: number; totalAccepted?: number; status: string }>({ url: `/pet/minigames/${roundId}/ops`, method: 'POST', data: { ops } }),
  /** R11：当前进行中对局查询（断线恢复，同 roundId 续玩；无局返回 null） */
  currentMinigameRound: () =>
    request<{ round: Record<string, unknown> | null }>({ url: '/pet/minigames/current' }),
  settleMinigame: (roundId: number | string) =>
    request<{ status: string; successCount: number; rewardEligible: boolean; validCompletion: boolean; reward: Record<string, number> }>({ url: `/pet/minigames/${roundId}/settle`, method: 'POST' }),
  startCustody: () => request<{ active: boolean; endsAt: string }>({ url: '/pet/custody/start', method: 'POST' }),
  getCustodyStatus: () =>
    request<{ active: boolean; endsAt?: string; careFeedUsed?: number; careCleanUsed?: number; weekUsed?: boolean }>({ url: '/pet/custody' }),
  endCustody: () => request<void>({ url: '/pet/custody/end', method: 'POST' }),
  getOfflineDigest: () =>
    request<{ from: string; throughAt: string; offlineHours: number; finishedTasks: number; claimableTasks: number; visits: number; milestones: number; hasCursor: boolean }>({ url: '/pet/offline-digest' }),
  confirmOfflineDigest: (throughAt?: string) =>
    request<{ confirmedAt: string }>({ url: '/pet/offline-digest/confirm', method: 'POST', data: throughAt ? { throughAt } : {} }),
  createCooperation: (inviteeUserId: number | string) =>
    request<{ cooperationId: number | string; inviteExpiresAt: string }>({ url: '/pet/cooperation', method: 'POST', data: { inviteeUserId } }),
  acceptCooperation: (cooperationId: number | string) =>
    request<{ status: string }>({ url: `/pet/cooperation/${cooperationId}/accept`, method: 'POST' }),
  leaveCooperation: (cooperationId: number | string) =>
    request<void>({ url: `/pet/cooperation/${cooperationId}/leave`, method: 'POST' }),
  claimCooperationReward: (cooperationId: number | string) =>
    request<{ reward: { type: string; itemCode?: string; amount?: number }; duplicate?: boolean }>({ url: `/pet/cooperation/${cooperationId}/claim`, method: 'POST' }),
  listCooperations: () => request<Array<Record<string, unknown>>>({ url: '/pet/cooperation' }),
  getCollection: (category?: string, page = 1, size = 20) =>
    request<Array<Record<string, unknown>>>({ url: '/pet/collection', params: { category, page, size } }),
  getCollectionStats: () => request<Record<string, number>>({ url: '/pet/collection/stats' }),

  /** 本人宠物币余额（懒创建期初 0；FROZEN 仍可读） */
  getWallet: () => request<PetWalletVO>({ url: '/pet/wallet' }),
  /** 收支明细（游标分页） */
  listWalletTransactions: (params?: { cursor?: number | string; size?: number; direction?: string; bizType?: string }) =>
    request<PetWalletTransactionVO[]>({ url: '/pet/wallet/transactions', data: params }),

  /** P1-16：宠物购买订单流水（R02 统一购买；keyset 翻页，cursor=上一页最后一条 orderId） */
  listPurchaseOrders: (params?: { cursor?: string; size?: number }) =>
    request<PetPurchaseOrderPage>({ url: '/pet/purchase-orders', data: params as Record<string, unknown> | undefined }),

  /** 陪伴心跳（B05/FE-02：返回会话视图，与亲密度 overview 分离） */
  companionHeartbeat: (seconds: number, seq?: number) =>
    request<PetCompanionSessionVO>({ url: '/pet/companion/heartbeat', method: 'POST', data: { seconds, seq } }),

  /** 职业面板 */
  getCareer: () => request<PetCareerPanel>({ url: '/pet/career' }),
  /** 入职/转职 */
  applyCareer: (careerCode: string) =>
    request<PetCareerItem>({ url: '/pet/career/apply', method: 'POST', data: { careerCode } }),
  /** 开始职业工作（与打工互斥） */
  startCareerWork: () => request<PetActivityItem>({ url: '/pet/career/work/start', method: 'POST' }),
  /** 领取职业工作奖励 */
  claimCareerWork: () => request<PetActivityItem>({ url: '/pet/career/work/claim', method: 'POST' }),
  /** 晋升（次数/等级/星光三条件） */
  promoteCareer: () => request<PetCareerItem>({ url: '/pet/career/promote', method: 'POST' }),

  /** 每日任务面板 */
  getDailyQuests: () => request<PetDailyQuestPanel>({ url: '/pet/daily-quests' }),
  /** PET-09/PET-23：任务集路由（setId 实体归属校验） */
  claimDailyQuestInSet: (setId: number | string, questId: number | string) =>
    request<PetDailyQuestItem>({ url: `/pet/daily-quest-sets/${setId}/quests/${questId}/claim`, method: 'POST' }),
  claimDailyQuestChestInSet: (setId: number | string) =>
    request<PetDailyQuestPanel>({ url: `/pet/daily-quest-sets/${setId}/chest/claim`, method: 'POST' }),
  /** 领取每日任务奖励 */
  claimDailyQuest: (code: string) =>
    request<PetDailyQuestItem>({ url: `/pet/daily-quests/${code}/claim`, method: 'POST' }),
  /** 领取全清宝箱 */
  claimDailyQuestChest: () =>
    request<PetDailyQuestPanel>({ url: '/pet/daily-quests/chest/claim', method: 'POST' }),

  /** 关系面板 */
  getRelations: () => request<PetRelationPanel>({ url: '/pet/relations' }),
  /** 申请关系 */
  requestRelation: (data: { toPetId: number; relType: string; message?: string }) =>
    request<PetRelationItem>({
      url: '/pet/relations/request',
      method: 'POST',
      data: data as unknown as Record<string, unknown>,
    }),
  /** 确认关系 */
  acceptRelation: (relationId: number) =>
    request<PetRelationItem>({ url: `/pet/relations/${relationId}/accept`, method: 'POST' }),
  /** 拒绝关系申请 */
  rejectRelation: (relationId: number) =>
    request<PetRelationItem>({ url: `/pet/relations/${relationId}/reject`, method: 'POST' }),
  /** 解除关系 */
  dissolveRelation: (relationId: number) =>
    request<PetRelationItem>({ url: `/pet/relations/${relationId}/dissolve`, method: 'POST' }),

  /** 我的家园（每日首次进入有奖励） */
  getHome: () => request<PetHome>({ url: '/pet/home' }),
  /** 购买家具（先入包再扣星光） */
  buyFurniture: (furnitureCode: string) =>
    request<PetInventoryItem>({ url: '/pet/home/furniture/buy', method: 'POST', data: { furnitureCode } }),
  /** 摆放家具 */
  placeFurniture: (data: { furnitureCode: string; posX: number; posY: number }) =>
    request<PetHome>({
      url: '/pet/home/furniture/place',
      method: 'POST',
      data: data as unknown as Record<string, unknown>,
    }),
  /** 卸下家具 */
  removeFurniture: (posX: number, posY: number) =>
    request<PetHome>({ url: `/pet/home/furniture?posX=${posX}&posY=${posY}`, method: 'DELETE' }),
  /** 更换墙纸/地板 */
  updateRoomTheme: (data: { wallCode?: string | null; floorCode?: string | null }) =>
    request<PetHome>({
      url: '/pet/home/theme',
      method: 'PUT',
      data: data as unknown as Record<string, unknown>,
    }),
  /** 家园设置（来访开关/欢迎语） */
  updateRoomSettings: (data: { isPublic?: boolean; welcomeMessage?: string }) =>
    request<PetHome>({
      url: '/pet/home/settings',
      method: 'PUT',
      data: data as unknown as Record<string, unknown>,
    }),
  /** 访问他人家园 */
  /** PET-19：只读预览他人家园（不产生拜访事实与奖励） */
  visitHome: (petId: number | string) => request<PetRoomVisit>({ url: `/pet/home/${petId}` }),
  /** PET-19：显式提交拜访命令（每日一次奖励口径，由该命令触发） */
  enterHome: (petId: number | string) =>
    request<PetRoomVisit>({ url: `/pet/home/${petId}/visits`, method: 'POST' }),
  /** 给他人房间点赞 */
  likeHome: (petId: number | string) => request<PetRoomLike>({ url: `/pet/home/${petId}/like`, method: 'POST' }),

  /** 好友面板 */
  getFriends: () => request<PetFriendPanel>({ url: '/pet/friends' }),
  /** 申请加好友 */
  requestFriend: (userId: number | string) => request<PetFriendItem>({ url: `/pet/friends/${userId}`, method: 'POST' }),
  /** 同意好友申请 */
  acceptFriend: (userId: number | string) =>
    request<PetFriendItem>({ url: `/pet/friends/${userId}/accept`, method: 'POST' }),
  /** 拒绝好友申请 */
  rejectFriend: (userId: number | string) =>
    request<PetFriendItem>({ url: `/pet/friends/${userId}/reject`, method: 'POST' }),
  /** 删除好友 */
  removeFriend: (userId: number | string) => request<void>({ url: `/pet/friends/${userId}`, method: 'DELETE' }),
  /** 好友互访 */
  visitFriend: (userId: number | string) =>
    request<PetFriendVisitResult>({ url: `/pet/friends/${userId}/visit`, method: 'POST' }),

  /** 留言墙分页 */
  getWall: (petId: number | string, page = 1, size = 10) =>
    request<PetWallPage>({ url: `/pet/wall/${petId}?page=${page}&size=${size}` }),
  /** 留言 */
  postWallMessage: (data: { petId: number | string; content: string; mood?: string }) =>
    request<PetWallMessage>({
      url: '/pet/wall/messages',
      method: 'POST',
      data: data as unknown as Record<string, unknown>,
    }),
  /** 主人回复留言 */
  replyWallMessage: (data: { messageId: number; content: string }) =>
    request<PetWallMessage>({
      url: '/pet/wall/messages/reply',
      method: 'POST',
      data: data as unknown as Record<string, unknown>,
    }),
  /** 删除留言 */
  deleteWallMessage: (messageId: number) =>
    request<void>({ url: `/pet/wall/messages/${messageId}`, method: 'DELETE' }),
  /** 点赞/取消点赞留言 */
  likeWallMessage: (messageId: number) =>
    request<PetWallLike>({ url: `/pet/wall/messages/${messageId}/like`, method: 'POST' }),
}

// ==================== 三期类型（与服务端 VO 对齐） ====================

export interface MinigameRoundVO {
  roundId: number | string
  rewardEligible: boolean
  deadlineAt: string
  ruleVersion: string
  sequence: Array<'LEFT' | 'CENTER' | 'RIGHT'>
}

/** 宠物币钱包视图（契约 §8.1：ID/余额为字符串） */
export interface PetWalletVO {
  accountId: number | string
  currency: 'PET_COIN'
  balance: number | string
  status: 'ACTIVE' | 'FROZEN'
  version: number | string
  serverNow: string
}

/** P1-16：宠物购买订单（契约对齐 PetPurchaseController.PurchaseOrderVO） */
export interface PetPurchaseOrder {
  orderId: string
  petId: string
  itemType: string
  itemCode: string
  quantity: number
  totalAmount: string
  currency: string
  /** PAID/DELIVERED/REFUNDED 等，服务端状态机 */
  status: string
  completedAt: string | null
}

export interface PetPurchaseOrderPage {
  items: PetPurchaseOrder[]
  nextCursor: string | null
  hasMore: boolean
}

export interface PetWalletTransactionVO {
  transactionId: number | string
  operationId: string
  petId: number | string | null
  bizType: string
  direction: 'EARN' | 'SPEND' | 'REFUND' | 'ADJUSTMENT'
  amount: number | string
  status: string
  currency: string
  occurredAt: string
}

/** 陪伴会话心跳视图（B05/FE-02：与亲密度 overview 分离） */
export interface PetCompanionSessionVO {
  sessionId: number | string
  status: 'ACTIVE' | 'EXPIRED' | 'STOPPED'
  serverNow: string
  accepted: boolean
  creditedSeconds: number
  todayAcceptedSeconds: number
  todayGrantedPoints: number
  dailyPointCap: number
  intimacy: number
  intimacyLevel: number
}

/** 亲密度与陪伴 */
export interface PetIntimacyInfo {
  intimacy: number
  level: number
  levelName: string
  levelFloor: number
  nextLevelAt: number | null
  toNext: number
  expBonusPercent: number
  companionSeconds: number
  todayCompanionSeconds: number
  dailyCompanionCapSeconds: number
  companionDays: number
  companionStreak: number
  levels: Array<{ level: number; name: string; threshold: number; achieved: boolean }>
}

/** 职业项 */
export interface PetCareerItem {
  code: string
  name: string
  description: string
  careerLine: string
  tier: number
  icon: string
  requiredLevel: number
  requiredIntelligence: number
  durationSeconds: number
  energyCost: number
  hungerCost: number
  expReward: number
  currencyReward: number
  workCount: number
  current: boolean
  eligible: boolean
  lockReason: string | null
  promoteToName: string | null
  promoteRequiredCount: number
  promoteStarCost: number
  promoteCurrentCount: number
  canPromote: boolean
  promoteLockReason: string | null
}

/** 职业面板 */
export interface PetCareerPanel {
  careerCode: string | null
  careerName: string | null
  careerLine: string | null
  tier: number | null
  icon: string | null
  workCount: number
  activeActivity: PetActivityItem | null
  canPromote: boolean
  promoteToName: string | null
  promoteRequiredCount: number
  promoteStarCost: number
  promoteLockReason: string | null
  careers: PetCareerItem[]
  history: Array<{
    code: string
    name: string
    workCount: number
    totalCurrency: number
    startedAt: string
    promotedAt: string
  }>
}

/** 每日任务项 */
export interface PetDailyQuestItem {
  code: string
  name: string
  description: string
  icon: string
  questType: string
  progress: number
  targetValue: number
  status: string
  statusLabel: string
  claimable: boolean
  expReward: number
  currencyReward: number
}

/** 每日任务面板 */
export interface PetDailyQuestPanel {
  questDate: string
  quests: PetDailyQuestItem[]
  completedCount: number
  claimedCount: number
  totalCount: number
  chestClaimable: boolean
  chestClaimed: boolean
  chestExp: number
  chestCurrency: number  /** PET-09：任务集实体 ID（按集领取/深链接用） */
  setId?: string | null
}

/** 关系项 */
export interface PetRelationItem {
  id: number | null
  relType: string | null
  relTypeLabel: string | null
  status: string | null
  direction: string
  intimacy: number
  intimacyLevel: number
  intimacyLevelName: string | null
  intimacyToNext: number
  petId: number
  petName: string
  species: string
  level: number
  growthStage: string
  evolutionStage: number
  skinCode: string | null
  ownerNickname: string
  message: string | null
  createdAt: string | null
  acceptedAt: string | null
}

/** 关系面板 */
export interface PetRelationPanel {
  relations: PetRelationItem[]
  incoming: PetRelationItem[]
  outgoing: PetRelationItem[]
  candidates: PetRelationItem[]
  limits: Array<{ relType: string; label: string; max: number; current: number; exclusive: boolean }>
}

/** 家园家具项 */
export interface PetHomeItem {
  code: string
  name: string
  description: string
  category: string
  categoryLabel: string
  icon: string
  rarity: string
  comfort: number
  priceStarlight: number
  requiredLevel: number
  posX: number | null
  posY: number | null
  owned: boolean
  eligible: boolean
  lockReason: string | null
  themeActive: boolean
}

/** 我的家园 */
export interface PetHome {
  petId: number
  petName: string
  wallCode: string | null
  floorCode: string | null
  welcomeMessage: string
  isPublic: boolean
  comfort: number
  visitCount: number
  likeCount: number
  gridWidth: number
  gridHeight: number
  comfortBonusThreshold: number
  comfortRestHappinessBonus: number
  dailyEnterRewarded: boolean
  placed: PetHomeItem[]
  inventory: PetHomeItem[]
  shop: PetHomeItem[]
}

/** 访问他人家园结果 */
export interface PetRoomVisit {
  petId: number
  petName: string
  species: string
  level: number
  evolutionStage: number
  skinCode: string | null
  ownerNickname: string
  welcomeMessage: string
  wallCode: string | null
  floorCode: string | null
  comfort: number
  visitCount: number
  likeCount: number
  liked: boolean
  visitedToday: boolean
  rewardHappiness: number
  rewardExp: number
  hostRewardExp: number
  friend: boolean
  message: string
  placed: PetHomeItem[]
}

/** 房间点赞结果 */
export interface PetRoomLike {
  petId: number
  likeCount: number
  newlyLiked: boolean
  rewardExp: number
  message: string
}

/** 好友项 */
export interface PetFriendItem {
  userId: number
  nickname: string
  petId: number | null
  petName: string | null
  species: string | null
  level: number | null
  evolutionStage: number
  skinCode: string | null
  status: string
  direction: string
  visitCount: number
  lastVisitAt: string | null
  visitedToday: boolean
}

/** 好友面板 */
export interface PetFriendPanel {
  friends: PetFriendItem[]
  incoming: PetFriendItem[]
  outgoing: PetFriendItem[]
  maxFriends: number
  dailyVisitLimit: number
  todayVisitCount: number
  remainingVisits: number
}

/** 好友互访结果 */
export interface PetFriendVisitResult {
  room: PetRoomVisit
  nickname: string
  visitCount: number
  relationIntimacyAdded: boolean
  message: string
}

/** 留言墙留言 */
export interface PetWallMessage {
  id: number
  petId: number
  parentId: number | null
  authorUserId: number
  authorNickname: string
  authorPetId: number | null
  authorPetName: string | null
  authorPetSpecies: string | null
  content: string
  mood: string | null
  status: string
  likeCount: number
  replyCount: number
  liked: boolean
  mine: boolean
  owner: boolean
  ownerReply: boolean
  createdAt: string
  replies: PetWallMessage[]
}

/** 留言墙分页 */
export interface PetWallPage {
  petId: number
  petName: string
  species: string
  level: number
  evolutionStage: number
  skinCode: string | null
  ownerNickname: string
  welcomeMessage: string | null
  roomPublic: boolean
  page: number
  size: number
  total: number
  dailyPostLimit: number
  messages: PetWallMessage[]
}

/** 留言点赞结果 */
export interface PetWallLike {
  messageId: number
  likeCount: number
  liked: boolean
  newlyLiked: boolean
  message: string
}

// ==================== 宠物陪伴功能面（N01 引导 / N02 日记·相册 / N03 记忆 / B19 通知偏好 + 审计补口） ====================

/** 新手引导进度（完成由领域事件驱动，客户端只能查看/跳过） */
export interface PetOnboardingProgress {
  currentStep: number
  totalSteps: number
  skippable: boolean
  completed: boolean
}

/** 成长日记条目（游标分页；他人仅见 PUBLIC） */
export interface PetDiaryEntry {
  id: number
  petId: number
  type: string
  content: string
  visibility: 'PUBLIC' | 'PRIVATE'
  assetIds: number[] | null
  createdAt: string
}

/** 日记游标分页信封（后端 Map：items/nextCursor/hasMore） */
export interface PetDiaryPage {
  items: PetDiaryEntry[]
  nextCursor: string | null
  hasMore: boolean
}

/** 相册资源（fileId 为 mall-file FILE-01 授权引用；每用户 100 张） */
export interface PetAlbumAsset {
  id: number
  userId: number
  petId: number
  diaryEntryId: number | null
  fileId: string
  auditStatus: string
  createdAt: string
  updatedAt: string
}

/** 宠物结构化记忆（仅主人可见；USER 编辑优先于 AUTO 抽取） */
export interface PetMemory {
  id: number
  userId: number
  petId: number
  memoryType: 'FAVORITE' | 'HABIT' | 'FACT'
  memoryKey: string
  memoryValue: string
  importance: number
  confidence: number
  source: 'AUTO' | 'USER'
  enabled: boolean
  createdAt: string
  updatedAt: string
}

/** 通知偏好（B19：免打扰/日常问候；仅影响日常 proactive 问候） */
export interface PetNotifyPref {
  id: number
  userId: number
  muteDailyGreeting: boolean
  dailyGreetingEnabled: boolean
  createdAt: string
  updatedAt: string
}

export const petCompanionApi = {
  getOnboarding: () => request<PetOnboardingProgress>({ url: '/pet/onboarding' }),
  /** 跳过引导（幂等；不伪造步骤与奖励） */
  skipOnboarding: () => request<void>({ url: '/pet/onboarding/skip', method: 'POST' }),

  listDiary: (petId: number | string, params?: { cursor?: number | string; size?: number }) => {
    const qs = Object.entries(params ?? {})
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
      .join('&')
    return request<PetDiaryPage>({ url: `/pet/pets/${petId}/diary${qs ? `?${qs}` : ''}` })
  },

  uploadAlbumAsset: (petId: number | string, fileId: string, diaryEntryId?: number | string) =>
    request<PetAlbumAsset>({
      url: `/pet/pets/${petId}/album`,
      method: 'POST',
      data: { fileId, diaryEntryId } as unknown as Record<string, unknown>,
    }),
  /** PET-13/T31：独立相册列表（服务端权威；含 BINDING/FAILED 与审核状态） */
  listAlbumAssets: (petId: number | string) =>
    request<Array<{ assetId: string; previewUrl: string | null; diaryEntryId: string | null;
                    auditStatus: string; bindStatus: string }>>(
      { url: `/pet/pets/${petId}/album` }),
  /** PET-13/T32：BINDING/FAILED 条目重试绑定（远端引用键幂等） */
  retryAlbumAsset: (petId: number | string, assetId: number | string) =>
    request<PetAlbumAsset>({ url: `/pet/pets/${petId}/album/${assetId}/retry-binding`, method: 'POST' }),
  deleteAlbumAsset: (petId: number | string, assetId: number | string) =>
    request<void>({ url: `/pet/pets/${petId}/album/${assetId}`, method: 'DELETE' }),

  listMemories: (petId: number | string) =>
    request<PetMemory[]>({ url: `/pet/pets/${petId}/memories` }),
  /** 编辑记忆（USER 来源优先于自动抽取，不被覆盖） */
  editMemory: (petId: number | string, memoryId: number | string, data: { value: string }) =>
    request<PetMemory>({
      url: `/pet/pets/${petId}/memories/${memoryId}`,
      method: 'PUT',
      data: data as unknown as Record<string, unknown>,
    }),
  deleteMemory: (petId: number | string, memoryId: number | string) =>
    request<void>({ url: `/pet/pets/${petId}/memories/${memoryId}`, method: 'DELETE' }),
  /** 批量清空记忆（全部软删，防复活标记） */
  clearMemories: (petId: number | string) =>
    request<void>({ url: `/pet/pets/${petId}/memories`, method: 'DELETE' }),
  /** 记忆开关（§7.2：expectedVersion CAS 冲突 409；响应带最新 version） */
  setMemorySettings: (petId: number | string, data: { extract: boolean; use: boolean; expectedVersion?: number }) =>
    request<{ extract: boolean; use: boolean; version: number }>({
      url: `/pet/pets/${petId}/memory-settings`,
      method: 'PUT',
      data: data as unknown as Record<string, unknown>,
    }),

  getNotifyPrefs: () => request<PetNotifyPref>({ url: '/pet/notify-settings' }),
  /** PET-23：我的举报结果（可公开处理结果） */
  listMyReports: () => request<PetReportMine[]>({ url: '/pet/reports/mine' }),
  updateNotifyPrefs: (data: { muteDailyGreeting: boolean; dailyGreetingEnabled: boolean; expectedVersion?: number }) =>
    request<{ muteDailyGreeting: boolean; dailyGreetingEnabled: boolean; version: number }>({
      url: '/pet/notify-settings',
      method: 'PUT',
      data: data as unknown as Record<string, unknown>,
    }),

  // ---- 审计补口（拉黑/举报/待战/一键领取/停止陪伴/装备预览/限时活动/小游戏历史/交互目录） ----
  listBlocks: () => request<number[]>({ url: '/pet/blocks' }),
  blockUser: (blockedUserId: number | string) =>
    request<void>({ url: `/pet/blocks/${blockedUserId}`, method: 'POST' }),
  unblockUser: (blockedUserId: number | string) =>
    request<void>({ url: `/pet/blocks/${blockedUserId}`, method: 'DELETE' }),
  reportTarget: (data: { targetType: string; targetId: number | string; reason: string; description?: string }) =>
    request<{ reportId: number | string; status: string; deduped: boolean }>({
      url: '/pet/reports', method: 'POST', data: data as unknown as Record<string, unknown> }),

  /** PET-09/PET-23：按集批领（绑定原 set/pet） */
  claimAllDailyQuestsInSet: (setId: number | string) =>
    request<{ results: Array<{ status: string }>; chest: { status: string } }>({ url: `/pet/daily-quest-sets/${setId}/claim-all`, method: 'POST' }),
  claimAllDailyQuests: () =>
    request<{ results: Array<{ status: string }>; chest: { status: string } }>({
      url: '/pet/daily-quests/claim-all', method: 'POST',
    }),
  stopCompanion: () =>
    request<Record<string, unknown>>({ url: '/pet/companion/stop', method: 'POST' }),
  /** B12 装备替换预览：后端收 itemCode（装备编码），不是 itemId */
  previewEquip: (itemCode: string) =>
    request<PetEquipPreview>({ url: `/pet/inventory/equip-preview?itemCode=${itemCode}` }),
  listActivities: () => request<PetActivityRow[]>({ url: '/pet/activities' }),
  claimActivity: (activityId: number | string) =>
    request<Record<string, unknown>>({ url: `/pet/activities/${activityId}/claim`, method: 'POST' }),
  /** N04 对局历史：后端收 page/size（offset 分页，size 上限 50），返回展示投影 VO */
  listMinigameRounds: (page = 1, size = 10) =>
    request<PetMinigameRoundItem[]>({
      url: `/pet/minigames?page=${page}&size=${size}`,
    }),
  getActions: (petId: number | string) =>
    request<PetActionItem[]>({ url: `/pet/pets/${petId}/actions` }),
  markAllRemindersRead: () => request<void>({ url: '/pet/reminders/read-all', method: 'PUT' }),
}

// ==================== 契约补齐（对齐 CloudMart-ui / 后端 VO） ====================

/** 装备替换预览的属性快照（hp/maxHp/strength/intelligence/agility/charm） */
export interface PetEquipStats {
  hp: number
  maxHp: number
  strength: number
  intelligence: number
  agility: number
  charm: number
}

/** 装备替换预览（B12，契约对齐后端 PetEquipPreviewVO） */
export interface PetEquipPreview {
  itemCode: string
  base: PetEquipStats
  current: PetEquipStats
  after: PetEquipStats
  delta: PetEquipStats
}

/** 小游戏对局历史项（契约对齐后端 PetMinigameRoundVO；roundId 为键，内部字段不出域） */
export interface PetMinigameRoundItem {
  roundId: number | string
  gameType: string
  status: string
  ruleVersion: string
  startedAt: string | null
  deadlineAt: string | null
  successCount: number
  rewardEligible: boolean
}

/** 动作可执行性（B06，契约对齐后端 PetActionVO） */
export interface PetActionItem {
  action: string
  allowed: boolean
  reasonCode: string | null
  reasonText: string | null
  nextAvailableAt: string | null
  rewardRemainingToday: number | null
}

/** 限时活动行（契约对齐后端 PetActivityVO 的展示子集） */
export interface PetActivityRow {
  activityId: number | string
  petName: string | null
  activityType: string
  configName: string | null
  status: string
  remainingSeconds: number
  canClaim: boolean
  claimedAt: string | null
}

// ==================== 批次 A–C2 新契约（§7.2；与 CloudMart-ui 同构） ====================

/** 启动聚合快照（§7.2）：无宠物返回空 pets 不抛错 */
export interface PetBootstrap {
  serverNow: string
  businessDate: string
  nextResetAt: string
  activePetId: number | string | null
  pets: Array<{ petId: number | string; name: string; level: number; isActive: boolean }>
  selectedPet: Record<string, unknown> | null
  walletSummary: { currency: string; balance: number | string; status: string } | null
  capabilities: Record<string, boolean>
  quotas: Record<string, number>
  actionAvailability: { busy: boolean; canFeed: boolean; canPlay: boolean; canWallPost: boolean }
  pendingOperations: { pendingActivityClaims: number; receivedInvites: number }
}

/** 活动中心聚合摘要（§7.2） */
export interface PetActivityCenterSummary {
  serverNow: string
  businessDate: string
  accountBusyActivity: { activity: boolean; custody: boolean }
  selectedPetActivity: Record<string, unknown> | null
  pendingClaimsCount: number
  dailySetSummary: Record<string, unknown> | null
  eventSummary: { totalCount: number; claimableCount: number; claimableCodes: string[] } | null
  cooperationSummary: Record<string, unknown> | null
}

/** 批量领取结果单项终态（§7.2：不忽略单项失败） */
export interface PetClaimBatchItem {
  activityId: number | string
  status: 'CLAIMED' | 'ALREADY_CLAIMED' | 'NOT_READY' | 'FAILED'
  activity?: Record<string, unknown>
  errorCode?: string
  message?: string
}

/** 我的举报（R05：公开处置摘要） */
export interface PetReportMine {
  reportId: number | string
  targetType: string
  targetId: number | string
  reason: string
  description: string | null
  status: string
  handleAction: string | null
  handleReason: string | null
  createdAt: string | null
  handledAt: string | null
}

/** 聊天请求状态（R22 意图恢复） */
export interface PetChatRequestStatus {
  status: 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'UNKNOWN'
  canRetry: boolean
  reply: Record<string, unknown> | null
}

/** 扩展后的限时活动条目（期次驱动活动带 occurrenceId/claimDeadlineAt） */
export interface PetEventOccurrenceView {
  occurrenceId: string | null
  claimDeadlineAt: string | null
}

export const petApiV2 = {
  getBootstrap: (petId?: number | string) =>
    request<PetBootstrap>({ url: '/pet/bootstrap', data: petId ? { petId } : undefined }),
  getActivityCenter: (petId?: number | string) =>
    request<PetActivityCenterSummary>({ url: '/pet/activity-center', data: petId ? { petId } : undefined }),
  claimActivitiesBatch: (activityIds: Array<number | string>) =>
    request<PetClaimBatchItem[]>({ url: '/pet/activities/claim-batch', method: 'POST', data: { activityIds } }),
  listMyReports: () => request<PetReportMine[]>({ url: '/pet/reports/mine' }),
  getChatRequestStatus: (requestKey: string) =>
    request<PetChatRequestStatus>({ url: `/pet/chat/requests/${encodeURIComponent(requestKey)}` }),
  updateDiaryVisibility: (
    petId: number | string, entryId: number | string,
    data: { visibility: 'PUBLIC' | 'OWNER_ONLY' | 'PRIVATE'; expectedVersion: number },
  ) =>
    request<{ entryId: number | string; visibility: string; version: number }>({
      url: `/pet/pets/${petId}/diary/${entryId}`, method: 'PATCH',
      data: data as unknown as Record<string, unknown>,
    }),
  updateAlbumAsset: (
    petId: number | string, assetId: number | string,
    data: { caption?: string; visibility?: 'PUBLIC' | 'OWNER_ONLY' | 'PRIVATE'; expectedVersion: number },
  ) =>
    request<Record<string, unknown>>({
      url: `/pet/pets/${petId}/album/${assetId}`, method: 'PATCH',
      data: data as unknown as Record<string, unknown>,
    }),
  listEventsByStatus: (status: 'AVAILABLE' | 'CLAIMABLE' | 'HISTORY') =>
    request<(PetEventItem & PetEventOccurrenceView)[]>({ url: `/pet/events?status=${status}` }),
  claimEventOccurrence: (occurrenceId: number | string) =>
    request<PetEventItem & PetEventOccurrenceView>({
      url: `/pet/event-occurrences/${occurrenceId}/claim`, method: 'POST',
    }),
  claimQuestInSet: (setId: string, questId: string) =>
    request<Record<string, unknown>>({
      url: `/pet/daily-quest-sets/${encodeURIComponent(setId)}/quests/${encodeURIComponent(questId)}/claim`,
      method: 'POST',
    }),
  claimAllQuestsInSet: (setId: string) =>
    request<{ results: Array<{ status: string }>; chest: { status: string } }>({
      url: `/pet/daily-quest-sets/${encodeURIComponent(setId)}/claim-all`, method: 'POST',
    }),
  claimQuestChestInSet: (setId: string) =>
    request<Record<string, unknown>>({
      url: `/pet/daily-quest-sets/${encodeURIComponent(setId)}/chest/claim`, method: 'POST',
    }),
}
