import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

// ========== 社区宠物（契约对齐 mall-pet PetController 等，实施文档 §1.16） ==========
// 数值全部服务端计算：客户端只发意图（POST /feed 等），不携带任何数值字段

export type PetSpecies = 'CAT' | 'DOG' | 'RABBIT' | 'FOX' | 'PANDA'
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
  /** 结果 JSON 字符串（领取后含奖励明细/捞瓶 outcome 与 bottleId） */
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

export interface PetReminder {
  notificationId: number | string
  reminderType: string
  title: string
  content: string
  bizId: number | string | null
  isRead: boolean
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

/** 领养宠物（重复领养 409 PET_ALREADY_EXISTS；种类/性格/外观白名单校验 400） */
export function createPet(data: { name: string; species: PetSpecies; color?: string; accessory?: string; personality: PetPersonality }) {
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

/** 领取打工奖励（CAS 幂等：重复领取 409；星光服务降级 503 可重试） */
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

/** 成就墙（未达成灰显） */
export function listPetAchievements() {
  return request.get<ApiResponse<PetAchievement[]>>('/pet/achievements')
}

/** 他人主页宠物卡片（未公开/无宠物 404） */
export function getPetPublicCard(userId: number | string) {
  return request.get<ApiResponse<PetPublicCard>>(`/pet/public/${userId}`)
}
