import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

// ========== 社区宠物（契约对齐 mall-pet PetController 等，实施文档 §1.16） ==========
// 数值全部服务端计算：客户端只发意图（POST /feed 等），不携带任何数值字段

/** 宠物种类（2026-09 水果化，契约对齐 mall-pet V28 迁移后的 species 枚举） */
export type PetSpecies = 'STRAWBERRY' | 'ORANGE' | 'WATERMELON' | 'BLUEBERRY' | 'DRAGONFRUIT'

/** 宠物性别（领养时选择；服务端对未传默认 MALE） */
export type PetGender = 'MALE' | 'FEMALE'
export type PetPersonality = 'LIVELY' | 'GENTLE' | 'TSUNDERE' | 'SIMPLE' | 'COOL' | 'CHATTERBOX'
export type PetGrowthStage = 'BABY' | 'YOUNG' | 'ADULT'
export type PetStatusType = 'IDLE' | 'WORKING' | 'STUDYING' | 'FISHING' | 'RESTING'
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
  status: PetStatusType
  activityType: PetActivityType | null
  activityFinishedAt: string | null
  claimableActivityType: PetActivityType | null
  isPublic: boolean
  /** null 表示限流服务降级为不限次 */
  feedRemainingToday: number | null
  lastStateUpdateAt: string
  /** 进化阶段（0 未进化/1 一阶/2 二阶；原文档 §89） */
  evolutionStage: number
  /** 当前穿戴皮肤编码（null=原生外观） */
  skinCode: string | null
  /** 拥有的宠物数量（多宠物，原文档 §89） */
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
  /** 结果 JSON 字符串（领取后含奖励明细；捞瓶含 outcome/rarity/specialContent/bottleId） */
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
  attackerPetName: string | null
  attackerUserId: number | string
  defenderPetId: number | string
  defenderPetName: string | null
  defenderUserId: number | string | null
  winnerPetId: number | string | null
  /** 回合流水 JSON：[{round,actorName,damage,critical,dodged,targetName,targetRemainingHp}] */
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

/** 宠物人设卡（F8）：与注入 AI prompt 的身份信息同源 */
export interface PetPersona {
  name: string
  personality: string
  personalityText: string
  careerCode: string | null
  careerName: string | null
  phrase: string | null
  intimacyLevel: number
  intimacyLevelName: string | null
  ownerTitle: string | null
}

export function getPetChatPersona() {
  return request.get<ApiResponse<PetPersona>>('/pet/chat/persona')
}

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

/** 我的宠物（未领养 404 PET_NOT_FOUND） */
export function getMyPet() {
  return request.get<ApiResponse<PetInfo>>('/pet/me')
}

/** 设置主人称呼（宠物怎么叫主人，1~12 字；blank 重置默认「主人」） */
export function setOwnerTitle(ownerTitle: string) {
  return request.put<ApiResponse<PetInfo>>('/pet/owner-title', { ownerTitle })
}

/** 领养宠物（重复领养 409 PET_ALREADY_EXISTS；种类/性格/外观白名单校验 400） */
export function createPet(data: { name: string; species: PetSpecies; gender?: PetGender; color?: string; accessory?: string; personality: PetPersonality }) {
  return request.post<ApiResponse<PetInfo>>('/pet/create', data)
}

/** 改名（30 天一次，409 PET_RENAME_COOLDOWN） */
export function renamePet(data: { name: string }) {
  return request.put<ApiResponse<PetInfo>>('/pet/name', data)
}

/** 修改外观（一期免费） */
export function updateAppearance(data: { color: string; accessory: string }) {
  return request.put<ApiResponse<PetInfo>>('/pet/appearance', data)
}

/** 主页公开开关 */
export function updatePetPrivacy(data: { isPublic: boolean }) {
  return request.put<ApiResponse<void>>('/pet/privacy', data)
}

/** 基础互动（数值/限频/经验全部服务端结算） */
export function feedPet() {
  return request.post<ApiResponse<PetInfo>>('/pet/feed')
}

/** 喂养道具（F1）：消耗背包里的 FOOD 恢复状态（效果服务端权威，不占免费次数） */
export function feedPetItem(itemCode: string) {
  return request.post<ApiResponse<PetInfo>>('/pet/feed-item', { itemCode })
}
export function playWithPet() {
  return request.post<ApiResponse<PetInfo>>('/pet/play')
}
export function cleanPet() {
  return request.post<ApiResponse<PetInfo>>('/pet/clean')
}
export function restPet() {
  return request.post<ApiResponse<PetInfo>>('/pet/rest')
}

/** 打工岗位列表（参数服务端下发，前端禁止硬编码数值） */
export function listPetJobs() {
  return request.get<ApiResponse<PetJobItem[]>>('/pet/jobs')
}

/** 开始打工（进行中活动互斥 409；等级/精力/饥饿不足 409） */
export function startPetWork(configId: number | string) {
  return request.post<ApiResponse<PetActivityItem>>('/pet/work/start', { configId })
}

/** 领取打工奖励（CAS 幂等：重复领取 409；宠物币服务降级 503 可重试） */
export function claimPetWork() {
  return request.post<ApiResponse<PetActivityItem>>('/pet/work/claim')
}

/** 读书课程列表 */
export function listPetStudies() {
  return request.get<ApiResponse<PetStudyItem[]>>('/pet/studies')
}

/** 开始读书 */
export function startPetStudy(configId: number | string) {
  return request.post<ApiResponse<PetActivityItem>>('/pet/study/start', { configId })
}

/** 领取读书奖励（经验 + 智力） */
export function claimPetStudy() {
  return request.post<ApiResponse<PetActivityItem>>('/pet/study/claim')
}

/** 捞瓶状态（查询即触发到期惰性结算） */
export function getPetBottleStatus() {
  return request.get<ApiResponse<PetBottleStatus>>('/pet/bottle/status')
}

/** 开始捞瓶（30 分钟任务，服务端时间判定；冷却 10 分钟 409） */
export function startPetBottle() {
  return request.post<ApiResponse<PetActivityItem>>('/pet/bottle/start')
}

/** 领取捞瓶结果（result JSON 内含 outcome 与 bottleId；CAUGHT 后跳转漂流瓶页） */
export function claimPetBottle() {
  return request.post<ApiResponse<PetActivityItem>>('/pet/bottle/claim')
}

/** 对战候选（PvE 野生宠物 + PvP 真实宠物） */
export function listPetOpponents() {
  return request.get<ApiResponse<PetOpponent[]>>('/pet/battle/opponents')
}

/** 发起挑战：PvE 立即结算（rounds 可播放）；PvP 落 PENDING 通知防守方 */
export function challengePetBattle(data: { mode: 'PVE' | 'PVP'; defenderPetId?: number | string }) {
  return request.post<ApiResponse<PetBattleItem>>('/pet/battle/challenge', data)
}

/** 防守方接受挑战（按快照 + seed 计算，双方发奖） */
export function acceptPetBattle(battleId: number | string) {
  return request.post<ApiResponse<PetBattleItem>>(`/pet/battle/${battleId}/accept`)
}

/** 防守方拒绝挑战 */
export function declinePetBattle(battleId: number | string) {
  return request.post<ApiResponse<PetBattleItem>>(`/pet/battle/${battleId}/decline`)
}

/** 对战详情（仅双方可见） */
export function getPetBattle(battleId: number | string) {
  return request.get<ApiResponse<PetBattleItem>>(`/pet/battle/${battleId}`)
}

/** 我的对战历史（offset 分页） */
export function listPetBattleHistory(params: { page?: number; pageSize?: number }) {
  return request.get<ApiResponse<PetBattleItem[]>>('/pet/battle/history', { params })
}

/** 和宠物聊天（每日 20 次 429；AI 故障降级模板 isAiReply=false） */
export function sendPetChat(message: string) {
  return request.post<ApiResponse<PetChatMessage>>('/pet/chat', { message })
}

/** 聊天历史（cursor 分页，messageId 倒序） */
export function listPetChatHistory(params: { cursor?: number | string; pageSize?: number }) {
  return request.get<ApiResponse<PetChatMessage[]>>('/pet/chat/history', { params })
}

/** 宠物口吻提醒（复用通知系统 type=PET；已读走现有 /notification 接口） */
export function listPetReminders() {
  return request.get<ApiResponse<PetReminder[]>>('/pet/reminders')
}

/** 排行榜维度 */
export type PetRankingType = 'LEVEL' | 'BATTLE_WIN' | 'BOTTLE'

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

/** 宠物排行榜（仅公开宠物入榜，Top 20 + 我的名次；原文档 §80） */
export function getPetRankings(type: PetRankingType) {
  return request.get<ApiResponse<PetRankingResult>>('/pet/rankings', { params: { type } })
}

/** 赛季信息 */
export interface PetSeasonInfo {
  seasonId: number | string
  name: string
  startsAt: string | null
  endsAt: string | null
  status: string
}

/** 赛季排行榜（F2）：当前 ACTIVE 赛季 + Top50 + 我的实时名次；无进行中赛季 season=null */
export interface PetSeasonRanking {
  season: PetSeasonInfo | null
  top50: PetRankingItem[]
  myRank: number | null
  myLevel: number | null
}

export function getPetSeasonRanking() {
  return request.get<ApiResponse<PetSeasonRanking>>('/pet/rankings/season')
}

/** 历届名次条目（已结算赛季快照） */
export interface PetSeasonHistoryItem {
  seasonId: number | string
  seasonName: string
  endedAt: string | null
  rankNo: number
  level: number
}

export function getPetSeasonHistory() {
  return request.get<ApiResponse<PetSeasonHistoryItem[]>>('/pet/rankings/season/history')
}

/** 宠物动态分享卡片（文案服务端生成，前端复制后跳转发帖页；原文档 §36） */
export function getPetShareCard(type: 'LEVEL_UP' | 'ACHIEVEMENT' | 'BOTTLE' | 'BATTLE' | 'DAILY') {
  return request.get<ApiResponse<PetShareCard>>('/pet/share/card', { params: { type } })
}

/** 成就墙（未达成灰显） */
export function listPetAchievements() {
  return request.get<ApiResponse<PetAchievement[]>>('/pet/achievements')
}

/** 他人主页宠物卡片（未公开/无宠物 404） */
export function getPetPublicCard(userId: number | string) {
  return request.get<ApiResponse<PetPublicCard>>(`/pet/public/${userId}`)
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

export type PetItemType = 'EQUIPMENT' | 'SKIN' | 'SKILL_BOOK' | 'FURNITURE' | 'FOOD'

/** 商城商品（装备/皮肤/技能书统一结构，字段按 itemType 取舍） */
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
  /** 不可购买原因（服务端生成，可直接展示） */
  lockReason: string | null
}

export interface PetShopResult {
  /** 宠物币余额（null=余额服务降级，前端隐藏） */
  balance: number | null
  currency: 'PET_COIN' | 'STARLIGHT'
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
  /** 技能书是否已学习（学会后仍留背包作收藏） */
  used: boolean
  acquiredAt: string | null
}

/** 宠物技能（含学习/背包状态） */
export interface PetSkillItem {
  code: string
  name: string
  description: string
  skillType: 'ACTIVE' | 'PASSIVE'
  effect: string
  effectValue: number
  /** 效果文案（服务端生成） */
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

/** 进化状态（下一阶条件为空表示已满阶） */
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

/** 我的宠物列表（多宠物） */
export function listMyPets() {
  return request.get<ApiResponse<PetSummary[]>>('/pet/pets')
}

/** 切换主宠（日常玩法作用于主宠；非本人宠物 403） */
export function activatePet(petId: number | string) {
  return request.post<ApiResponse<PetSummary>>(`/pet/pets/${petId}/activate`)
}

/** 宠物商城（装备/皮肤/技能书 + 宠物币余额） */
export function getPetShop() {
  return request.get<ApiResponse<PetShopResult>>('/pet/shop')
}

/** 购买物品（先入包再扣宠物币；余额不足 402；重复购买 409 PET_ITEM_ALREADY_OWNED） */
export function buyPetItem(data: { itemType: PetItemType; itemCode: string }) {
  return request.post<ApiResponse<PetInventoryItem>>('/pet/shop/buy', data)
}

/** 宠物背包 */
export function listPetInventory() {
  return request.get<ApiResponse<PetInventoryItem[]>>('/pet/inventory')
}

/** 穿戴装备（同部位自动换下旧的） */
export function equipPetItem(itemCode: string) {
  return request.post<ApiResponse<PetInfo>>('/pet/inventory/equip', { itemCode })
}

/** 卸下装备（slot: HAT/NECKLACE/SCARF/BACKPACK） */
export function unequipPetItem(slot: string) {
  return request.post<ApiResponse<PetInfo>>('/pet/inventory/unequip', undefined, { params: { slot } })
}

/** 穿戴皮肤（种类不匹配 400 PET_SKIN_SPECIES_MISMATCH） */
export function wearPetSkin(skinCode: string) {
  return request.post<ApiResponse<PetInfo>>('/pet/inventory/skin', { skinCode })
}

/** 卸下皮肤（恢复种类原生外观） */
export function removePetSkin() {
  return request.post<ApiResponse<PetInfo>>('/pet/inventory/skin/remove')
}

/** 技能列表（含学习/背包状态） */
export function listPetSkills() {
  return request.get<ApiResponse<PetSkillItem[]>>('/pet/skills')
}

/** 学习技能（需背包已有技能书，否则 409 PET_SKILL_BOOK_REQUIRED） */
export function learnPetSkill(skillCode: string) {
  return request.post<ApiResponse<PetSkillItem>>('/pet/skills/learn', { skillCode })
}

/** 进化状态 */
export function getPetEvolution() {
  return request.get<ApiResponse<PetEvolutionStatus>>('/pet/evolution')
}

/** 执行进化（等级不足/已满阶 409；宠物币不足 402） */
export function evolvePet() {
  return request.post<ApiResponse<PetEvolutionStatus>>('/pet/evolution/evolve')
}

/** 社区宠物活动列表（进度惰性统计） */
export function listPetEvents() {
  return request.get<ApiResponse<PetEventItem[]>>('/pet/events')
}

/** 领取活动奖励（未完成/已领/已结束 409） */
export function claimPetEvent(eventCode: string) {
  return request.post<ApiResponse<PetEventItem>>(`/pet/events/${eventCode}/claim`)
}

/** 串门邻居列表（他人公开宠物） */
export function listPetVisitNeighbors() {
  return request.get<ApiResponse<PetVisitNeighbor[]>>('/pet/visit/neighbors')
}

/** 让宠物去串门（同一邻居每日一次；每日次数上限 429） */
export function visitNeighborPet(petId: number | string) {
  return request.post<ApiResponse<PetVisitResult>>(`/pet/visit/${petId}`)
}

/** 宠物提醒未读数（宠物入口角标；服务降级返回 0） */
export function getPetReminderUnreadCount() {
  return request.get<ApiResponse<number>>('/pet/reminders/unread-count')
}

// ==================== 三期：职业 / 家园 / 每日任务 / 关系 / 好友 / 留言墙 / 亲密度 ====================

/** 亲密度与陪伴（等级/进度/加成/陪伴时长） */
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
  chestCurrency: number
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

/** 职业面板 */
export function getPetCareer() {
  return request.get<ApiResponse<PetCareerPanel>>('/pet/career')
}

/** 入职/转职（等级/智力不满足 409 PET_CAREER_LOCKED） */
export function applyPetCareer(careerCode: string) {
  return request.post<ApiResponse<PetCareerItem>>('/pet/career/apply', { careerCode })
}

/** 开始职业工作（与打工互斥） */
export function startPetCareerWork() {
  return request.post<ApiResponse<PetActivityItem>>('/pet/career/work/start')
}

/** 领取职业工作奖励（CAS 幂等） */
export function claimPetCareerWork() {
  return request.post<ApiResponse<PetActivityItem>>('/pet/career/work/claim')
}

/** 晋升（次数/等级/宠物币三条件；最高阶 409 PET_CAREER_MAX_TIER） */
export function promotePetCareer() {
  return request.post<ApiResponse<PetCareerItem>>('/pet/career/promote')
}

/** 每日任务面板 */
export function getPetDailyQuests() {
  return request.get<ApiResponse<PetDailyQuestPanel>>('/pet/daily-quests')
}

/** 领取每日任务奖励 */
export function claimPetDailyQuest(code: string) {
  return request.post<ApiResponse<PetDailyQuestItem>>(`/pet/daily-quests/${code}/claim`)
}

/** 领取全清宝箱（有未领任务时 409 PET_QUEST_CHEST_NOT_READY） */
export function claimPetDailyQuestChest() {
  return request.post<ApiResponse<PetDailyQuestPanel>>('/pet/daily-quests/chest/claim')
}

/** 宠物关系面板 */
export function getPetRelations() {
  return request.get<ApiResponse<PetRelationPanel>>('/pet/relations')
}

/** 申请关系（情侣已有一段 409 PET_RELATION_EXCLUSIVE） */
export function requestPetRelation(data: { toPetId: number; relType: string; message?: string }) {
  return request.post<ApiResponse<PetRelationItem>>('/pet/relations/request', data)
}

/** 确认关系 */
export function acceptPetRelation(relationId: number) {
  return request.post<ApiResponse<PetRelationItem>>(`/pet/relations/${relationId}/accept`)
}

/** 拒绝关系申请 */
export function rejectPetRelation(relationId: number) {
  return request.post<ApiResponse<PetRelationItem>>(`/pet/relations/${relationId}/reject`)
}

/** 解除关系 */
export function dissolvePetRelation(relationId: number) {
  return request.post<ApiResponse<PetRelationItem>>(`/pet/relations/${relationId}/dissolve`)
}

/** 我的家园（每日首次进入有奖励） */
export function getPetHome() {
  return request.get<ApiResponse<PetHome>>('/pet/home')
}

/** 购买家具（先入包再扣宠物币） */
export function buyPetFurniture(furnitureCode: string) {
  return request.post<ApiResponse<PetInventoryItem>>('/pet/home/furniture/buy', { furnitureCode })
}

/** 摆放家具（越界 400 / 格子占用 409） */
export function placePetFurniture(data: { furnitureCode: string; posX: number; posY: number }) {
  return request.post<ApiResponse<PetHome>>('/pet/home/furniture/place', data)
}

/** 卸下家具（按格子） */
export function removePetFurniture(posX: number, posY: number) {
  return request.delete<ApiResponse<PetHome>>('/pet/home/furniture', { params: { posX, posY } })
}

/** 更换墙纸/地板 */
export function updatePetRoomTheme(data: { wallCode?: string | null; floorCode?: string | null }) {
  return request.put<ApiResponse<PetHome>>('/pet/home/theme', data)
}

/** 家园设置（来访开关 / 欢迎语） */
export function updatePetRoomSettings(data: { isPublic?: boolean; welcomeMessage?: string }) {
  return request.put<ApiResponse<PetHome>>('/pet/home/settings', data)
}

/** 访问他人家园（未公开 403；每日次数上限）；ID 为雪花字符串，禁止 Number 转换 */
export function visitPetHome(petId: number | string) {
  return request.get<ApiResponse<PetRoomVisit>>(`/pet/home/${petId}`)
}

/** 给他人房间点赞（uk 幂等）；ID 为雪花字符串，禁止 Number 转换 */
export function likePetHome(petId: number | string) {
  return request.post<ApiResponse<PetRoomLike>>(`/pet/home/${petId}/like`)
}

/** 好友面板 */
export function getPetFriends() {
  return request.get<ApiResponse<PetFriendPanel>>('/pet/friends')
}

/** 申请加好友（对方已申请则直接互相确认）；ID 为雪花字符串，禁止 Number 转换 */
export function requestPetFriend(userId: number | string) {
  return request.post<ApiResponse<PetFriendItem>>(`/pet/friends/${userId}`)
}

/** 同意好友申请 */
export function acceptPetFriend(userId: number) {
  return request.post<ApiResponse<PetFriendItem>>(`/pet/friends/${userId}/accept`)
}

/** 拒绝好友申请 */
export function rejectPetFriend(userId: number) {
  return request.post<ApiResponse<PetFriendItem>>(`/pet/friends/${userId}/reject`)
}

/** 删除好友（双向） */
export function removePetFriend(userId: number) {
  return request.delete<ApiResponse<void>>(`/pet/friends/${userId}`)
}

/** 好友互访（每日上限；双方受益） */
export function visitPetFriend(userId: number) {
  return request.post<ApiResponse<PetFriendVisitResult>>(`/pet/friends/${userId}/visit`)
}

/** 好友动态条目（升级 / 打工完成 / 读书完成 / 对战获胜） */
export interface PetFriendFeedItem {
  feedId: number
  actorUserId: number
  actorPetId: number
  eventType: string
  text: string
  petName: string | null
  createdAt: string | null
}

/** 好友动态（游标分页：beforeId = 上一页最后一条） */
export function listPetFriendFeed(params: { beforeId?: number | string; size?: number } = {}) {
  return request.get<ApiResponse<PetFriendFeedItem[]>>('/pet/friends/feed', { params })
}

/** 好友动态未读数（100 封顶） */
export function getPetFriendFeedUnreadCount() {
  return request.get<ApiResponse<number>>('/pet/friends/feed/unread-count')
}

/** 标记好友动态已读（水位只前进） */
export function markPetFriendFeedRead() {
  return request.post<ApiResponse<void>>('/pet/friends/feed/read')
}

/** 留言墙分页 */
export function getPetWall(petId: number | string, page = 1, size = 10) {
  return request.get<ApiResponse<PetWallPage>>(`/pet/wall/${petId}`, { params: { page, size } })
}

/** 留言（1-120 字；每日上限 429） */
export function postPetWallMessage(data: { petId: number | string; content: string; mood?: string }) {
  return request.post<ApiResponse<PetWallMessage>>('/pet/wall/messages', data)
}

/** 主人回复留言 */
export function replyPetWallMessage(data: { messageId: number; content: string }) {
  return request.post<ApiResponse<PetWallMessage>>('/pet/wall/messages/reply', data)
}

/** 删除留言（作者或墙主人） */
export function deletePetWallMessage(messageId: number) {
  return request.delete<ApiResponse<void>>(`/pet/wall/messages/${messageId}`)
}

/** 点赞/取消点赞留言 */
export function likePetWallMessage(messageId: number) {
  return request.post<ApiResponse<PetWallLike>>(`/pet/wall/messages/${messageId}/like`)
}

/** 亲密度与陪伴 */
export function getPetIntimacy() {
  return request.get<ApiResponse<PetIntimacyInfo>>('/pet/intimacy')
}

/**
 * 陪伴会话心跳视图（B05/FE-02）：与亲密度 overview 是两个 VO——
 * 心跳返回会话与今日累计，不含 levelName/toNext 等面板字段；
 * 亲密度面板必须另调 getPetIntimacy()，禁止把心跳结果 set 进亲密度状态。
 */
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

// ==================== W03：宠物币钱包（§7.8/§8.2） ====================

/** 宠物币钱包视图（契约 §8.1：ID/余额为字符串，Long→String 序列化保证） */
export interface PetWalletVO {
  accountId: string
  currency: 'PET_COIN'
  balance: string
  status: 'ACTIVE' | 'FROZEN'
  version: string
  serverNow: string
}

export interface PetWalletTransactionVO {
  transactionId: string
  operationId: string
  petId: string | null
  bizType: string
  direction: 'EARN' | 'SPEND' | 'REFUND' | 'ADJUSTMENT'
  amount: string
  status: string
  currency: string
  occurredAt: string
}

// ==================== N 系列：小游戏/托管/摘要/合作/图鉴（§8.3） ====================

export interface MinigameRoundVO {
  roundId: number | string
  rewardEligible: boolean
  deadlineAt: string
  ruleVersion: string
  /** 目标序列（展示数据；LEFT/CENTER/RIGHT × 10 窗） */
  sequence: Array<'LEFT' | 'CENTER' | 'RIGHT'>
}

/** 开始接球局（显式 petId；每日 5 局有收益，超限训练局） */
export function startMinigameRound(petId: number | string) {
  return request.post<ApiResponse<MinigameRoundVO>>(`/pet/pets/${petId}/minigames`)
}

export function submitMinigameOps(roundId: number | string, ops: Array<{ seq: number; windowIndex: number; slot: string }>) {
  return request.post<ApiResponse<{ accepted: number; status: string }>>(`/pet/minigames/${roundId}/ops`, { ops })
}

export function settleMinigame(roundId: number | string) {
  return request.post<ApiResponse<{ status: string; successCount: number; rewardEligible: boolean; validCompletion: boolean; reward: Record<string, number> }>>(`/pet/minigames/${roundId}/settle`)
}

export function startCustody() {
  return request.post<ApiResponse<{ active: boolean; endsAt: string }>>('/pet/custody/start')
}

export function getCustodyStatus() {
  return request.get<ApiResponse<{ active: boolean; endsAt?: string; careFeedUsed?: number; careCleanUsed?: number; weekUsed?: boolean; nextAvailableAt?: string }>>('/pet/custody')
}

export function endCustody() {
  return request.post<ApiResponse<void>>('/pet/custody/end')
}

export function getOfflineDigest() {
  return request.get<ApiResponse<{ from: string; throughAt: string; offlineHours: number; finishedTasks: number; claimableTasks: number; visits: number; milestones: number; hasCursor: boolean }>>('/pet/offline-digest')
}

export function confirmOfflineDigest(throughAt?: string) {
  return request.post<ApiResponse<{ confirmedAt: string }>>('/pet/offline-digest/confirm', throughAt ? { throughAt } : {})
}

export function createCooperation(inviteeUserId: number | string) {
  return request.post<ApiResponse<{ cooperationId: number | string; inviteExpiresAt: string }>>('/pet/cooperation', { inviteeUserId })
}

export function acceptCooperation(cooperationId: number | string) {
  return request.post<ApiResponse<{ status: string }>>(`/pet/cooperation/${cooperationId}/accept`)
}

export function leaveCooperation(cooperationId: number | string) {
  return request.post<ApiResponse<void>>(`/pet/cooperation/${cooperationId}/leave`)
}

export function claimCooperationReward(cooperationId: number | string) {
  return request.post<ApiResponse<{ reward: { type: string; itemCode?: string; amount?: number }; duplicate?: boolean }>>(`/pet/cooperation/${cooperationId}/claim`)
}

export function listCooperations() {
  return request.get<ApiResponse<Array<Record<string, unknown>>>>('/pet/cooperation')
}

export function getCollection(category?: string, page = 1, size = 20) {
  return request.get<ApiResponse<Array<Record<string, unknown>>>>('/pet/collection', { params: { category, page, size } })
}

export function getCollectionStats() {
  return request.get<ApiResponse<Record<string, number>>>('/pet/collection/stats')
}

/** 本人宠物币余额（懒创建期初 0；FROZEN 仍可读） */
export function getPetWallet() {
  return request.get<ApiResponse<PetWalletVO>>('/pet/wallet')
}

/** 收支明细（游标分页；meta.nextCursor/hasMore 表达分页状态） */
export function listPetWalletTransactions(
  params: { cursor?: number | string; size?: number; direction?: string; bizType?: string } = {},
) {
  return request.get<ApiResponse<PetWalletTransactionVO[]>>('/pet/wallet/transactions', { params })
}

/** 陪伴心跳（前端按间隔上报秒数，服务端按日封顶；seq 为会话内单调递增序号，幂等） */
export function sendPetCompanionHeartbeat(seconds: number, seq?: number) {
  return request.post<ApiResponse<PetCompanionSessionVO>>('/pet/companion/heartbeat', { seconds, seq })
}

// ==================== 陪伴功能面（N01 引导 / N02 日记·相册 / N03 记忆 / B19 通知偏好） ====================

/** 新手引导进度（完成由领域事件驱动，客户端只能查看/跳过） */
export interface PetOnboardingProgress {
  currentStep: number
  totalSteps: number
  skippable: boolean
  completed: boolean
}

export function getPetOnboarding() {
  return request.get<ApiResponse<PetOnboardingProgress>>('/pet/onboarding')
}

/** 跳过引导（幂等；不伪造步骤与奖励） */
export function skipPetOnboarding() {
  return request.post<ApiResponse<void>>('/pet/onboarding/skip')
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

export function listPetDiary(petId: number | string, cursor?: number | string, size = 20) {
  return request.get<ApiResponse<PetDiaryPage>>(`/pet/pets/${petId}/diary`, {
    params: { cursor, size },
  })
}

/** 相册条目（R04：fileId 不透明 ID；预览为 60 秒短期授权地址，禁止持久化） */
export interface PetAlbumAsset {
  assetId: string
  petId: string | null
  diaryEntryId: string | null
  fileId: string
  auditStatus: string
  bindStatus: string | null
  reviewReason: string | null
  previewUrl: string | null
  createdAt: string | null
}

/** 相册列表（本人含审核状态与驳回理由；访客仅见 APPROVED+BOUND） */
export function listPetAlbum(petId: number | string) {
  return request.get<ApiResponse<PetAlbumAsset[]>>(`/pet/pets/${petId}/album`)
}

/** 上传相册资源（R04：PRIVATE fileId 绑定；失败留 BINDING 可重试） */
export function uploadPetAlbumAsset(petId: number | string, fileId: string, diaryEntryId?: number | string) {
  return request.post<ApiResponse<PetAlbumAsset>>(`/pet/pets/${petId}/album`, { fileId, diaryEntryId })
}

/** BINDING 条目重试绑定（上传响应丢失/远程失败的恢复入口） */
export function retryPetAlbumBinding(petId: number | string, assetId: string) {
  return request.post<ApiResponse<PetAlbumAsset>>(`/pet/pets/${petId}/album/${assetId}/retry-binding`)
}

export function deletePetAlbumAsset(petId: number | string, assetId: number | string) {
  return request.delete<ApiResponse<void>>(`/pet/pets/${petId}/album/${assetId}`)
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

export function listPetMemories(petId: number | string) {
  return request.get<ApiResponse<PetMemory[]>>(`/pet/pets/${petId}/memories`)
}

export function editPetMemory(petId: number | string, memoryId: number | string, data: { value: string }) {
  return request.put<ApiResponse<PetMemory>>(`/pet/pets/${petId}/memories/${memoryId}`, data)
}

export function deletePetMemory(petId: number | string, memoryId: number | string) {
  return request.delete<ApiResponse<void>>(`/pet/pets/${petId}/memories/${memoryId}`)
}

/** 批量清空记忆（全部软删，防复活标记） */
export function clearPetMemories(petId: number | string) {
  return request.delete<ApiResponse<void>>(`/pet/pets/${petId}/memories`)
}

/** 记忆设置读取（R03/T11：先 GET 再编辑，不得以默认值覆盖服务端已关闭设置） */
export function getPetMemorySettings(petId: number | string) {
  return request.get<ApiResponse<{ extract: boolean; use: boolean }>>(`/pet/pets/${petId}/memory-settings`)
}

/** 记忆开关（契约 MemoryToggleRequest：extract=自动抽取 / use=注入上下文，二者独立） */
export function setPetMemorySettings(petId: number | string, data: { extract: boolean; use: boolean }) {
  return request.put<ApiResponse<{ extract: boolean; use: boolean }>>(`/pet/pets/${petId}/memory-settings`, data)
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

export function getPetNotifyPrefs() {
  return request.get<ApiResponse<PetNotifyPref>>('/pet/notify-settings')
}

export function updatePetNotifyPrefs(data: { muteDailyGreeting: boolean; dailyGreetingEnabled: boolean }) {
  return request.put<ApiResponse<PetNotifyPref>>('/pet/notify-settings', data)
}

// ==================== 已有能力的补口（拉黑列表 / 待应战 / 一键领取 / 停止陪伴 / 装备预览） ====================

/** 宠物社交拉黑列表（屏蔽后双方不能新增拜访收益/挑战/留言/申请） */
export function listPetBlocks() {
  return request.get<ApiResponse<number[]>>('/pet/blocks')
}

export function unblockPetUser(blockedUserId: number | string) {
  return request.delete<ApiResponse<void>>(`/pet/blocks/${blockedUserId}`)
}

/** 待我应战的挑战（未决对战） */
export function listPendingBattles() {
  return request.get<ApiResponse<Array<Record<string, unknown>>>>('/pet/battle/pending')
}

/** 一键领取全部已完成每日任务（逐项独立 CAS + 幂等，单项失败跳过可重试） */
export function claimAllDailyQuests() {
  return request.post<ApiResponse<Array<Record<string, unknown>>>>('/pet/daily-quests/claim-all')
}

/** 停止陪伴会话（结算有效窗口内未计入时间；幂等） */
export function stopCompanionSession() {
  return request.post<ApiResponse<Record<string, unknown>>>('/pet/companion/stop')
}

/** 装备替换预览的属性快照（hp/maxHp/strength/intelligence/agility/charm） */
export interface PetEquipStats {
  hp: number
  maxHp: number
  strength: number
  intelligence: number
  agility: number
  charm: number
}

/** 装备替换预览（服务端复算，无写入） */
export interface PetEquipPreview {
  itemCode: string
  base: PetEquipStats
  current: PetEquipStats
  after: PetEquipStats
  delta: PetEquipStats
}

/**
 * 装备替换预览（B12）。
 * 注意：后端收的是 `itemCode`（@RequestParam("itemCode")），不是 itemId——
 * 早期版本误传 itemId 会直接校验失败，这里按接口契约传编码。
 */
export function previewPetEquip(itemCode: string) {
  return request.get<ApiResponse<PetEquipPreview>>('/pet/inventory/equip-preview', {
    params: { itemCode },
  })
}

// ==================== 审计补口第二批（限时活动 / 待战 / 拉黑与举报 / 交互目录 / 小游戏历史） ====================

/** 宠物限时活动列表（与养成面板的常驻事件 /pet/events 是两套体系） */
export function listPetActivities() {
  return request.get<ApiResponse<Array<Record<string, unknown>>>>('/pet/activities')
}

/** 领取限时活动奖励（幂等） */
export function claimPetActivity(activityId: number | string) {
  return request.post<ApiResponse<Record<string, unknown>>>(`/pet/activities/${activityId}/claim`)
}

/** 小游戏历史回合（结果/收益回顾） */
/** 小游戏对局历史项（契约对齐后端 PetMinigameRoundVO：roundId/gameType/status/ruleVersion/…） */
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

/** 对局历史（N04，offset 分页：后端收 page/size，size 上限 50） */
export function listMinigameRounds(page = 1, size = 10) {
  return request.get<ApiResponse<PetMinigameRoundItem[]>>('/pet/minigames', {
    params: { page, size },
  })
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

/** 当前宠物可用互动目录（长按食物背包等入口的动态项） */
export function getPetActions(petId: number | string) {
  return request.get<ApiResponse<PetActionItem[]>>(`/pet/pets/${petId}/actions`)
}

/** 拉黑用户（幂等；屏蔽后双方不能新增拜访收益/挑战/留言/申请） */
export function blockPetUser(blockedUserId: number | string) {
  return request.post<ApiResponse<void>>(`/pet/blocks/${blockedUserId}`)
}

/** 举报用户/内容（宠物社交场景） */
export function reportPetTarget(data: { targetType: string; targetId: number | string; reason: string }) {
  return request.post<ApiResponse<void>>('/pet/reports', data)
}

/** 全部宠物提醒标记已读 */
export function markAllPetRemindersRead() {
  return request.put<ApiResponse<void>>('/pet/reminders/read-all')
}

// ==================== 纪念日 / 分享卡片补口 ====================

/** 纪念日里程碑（key 如 100_DAYS / ANNIVERSARY_1；daysToGo 负数表示已过） */
export interface PetMilestone {
  key: string
  title: string
  daysToGo: number
}

/** 宠物纪念日（纯计算端点：领养天数 / 连续陪伴 / 当前与下一个里程碑） */
export interface PetAnniversary {
  adoptionDays: number
  companionStreak: number
  currentMilestone: PetMilestone | null
  nextMilestone: PetMilestone | null
}

export function getPetAnniversaries() {
  return request.get<ApiResponse<PetAnniversary>>('/pet/me/anniversaries')
}
