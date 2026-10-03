import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/**
 * 宠物运营后台 API（社区宠物模块三期）。
 *
 * 全部经 mall-admin 代理（/admin/pet/**）：配置类接口"带 id 为更新、不带 id 为新增"；
 * 留言审核用状态流转（NORMAL/HIDDEN/DELETED），保留审计轨迹。
 */

/** 宠物配置类型（与后端 /admin/pet/{type} 一一对应） */
export type PetConfigType =
  | 'configs/foods'
  | 'configs/jobs'
  | 'configs/studies'
  | 'configs/equipment'
  | 'configs/skins'
  | 'configs/skills'
  | 'configs/evolutions'
  | 'configs/events'
  | 'careers'
  | 'furniture'
  | 'daily-quests'

/** 配置列表（全量，含停用/下架） */
export function listPetConfigs(configType: PetConfigType) {
  return request.get<ApiResponse<Record<string, unknown>[]>>(`/admin/pet/${configType}`)
}

/** 新增/更新配置（带 id 为更新） */
export function upsertPetConfig(configType: PetConfigType, data: Record<string, unknown>) {
  return request.post<ApiResponse<Record<string, unknown>>>(`/admin/pet/${configType}`, data)
}

/** 启停/上下架 */
export function togglePetConfig(configType: PetConfigType, id: number, enabled: boolean) {
  return request.put<ApiResponse<void>>(`/admin/pet/${configType}/${id}/enabled`, undefined, {
    params: { enabled },
  })
}

/** 留言墙留言列表（含隐藏/删除，审核溯源） */
export function listPetWallMessages(params: {
  petId?: number
  authorUserId?: number
  status?: string
  page?: number
  size?: number
}) {
  return request.get<ApiResponse<Record<string, unknown>[]>>('/admin/pet/wall/messages', { params })
}

/** 留言隐藏/恢复/删除（NORMAL / HIDDEN / DELETED） */
export function updatePetWallMessageStatus(id: number, status: string) {
  return request.put<ApiResponse<void>>(`/admin/pet/wall/messages/${id}/status`, { status })
}

/** 宠物数据看板（概览 + 趋势 + 分布） */
export function getPetDashboard(days = 14) {
  return request.get<ApiResponse<PetDashboard>>('/admin/pet/dashboard', { params: { days } })
}

/** 看板数据结构（与服务端 PetDashboardVO 对齐） */
export interface PetDashboard {
  overview: {
    totalPets: number
    newPetsToday: number
    newPets7d: number
    activePetsToday: number
    activePets7d: number
    totalBattles: number
    battlesToday: number
    totalBottles: number
    bottlesToday: number
    totalVisits: number
    totalFriendVisits: number
    totalWallMessages: number
    wallMessagesToday: number
    totalRelations: number
    totalFriends: number
    totalRooms: number
    avgComfort: number
    questsClaimedToday: number
    questsGeneratedToday: number
    careerHired: number
    purchasesByType: Record<string, number>
  }
  trend: Array<{
    date: string
    newPets: number
    activePets: number
    activities: number
    wallMessages: number
    visits: number
    battles: number
  }>
  distribution: {
    species: Array<{ name: string; value: number }>
    levelBuckets: Array<{ name: string; value: number }>
    careers: Array<{ name: string; value: number }>
    topFurniture: Array<{ name: string; value: number }>
    topIntimacy: Array<{ name: string; value: number }>
  }
  /** AI 聊天用量（P1-8） */
  aiUsage: {
    aiRepliesToday: number
    tokensToday: number
    fallbackTotal: number
  }
}

// ==================== W04 钱包管理（§8.4；/admin/pet/wallet/**） ====================

export interface AdminPetWalletAccount {
  accountId: number | string
  userId: number | string
  currency: string
  balance: number | string
  status: 'ACTIVE' | 'FROZEN'
  version: number | string
  createdAt: string
}

export interface AdminPetWalletTransaction {
  transactionId: number | string
  operationId: string
  userId: number | string
  petId: number | string | null
  bizType: string
  direction: 'EARN' | 'SPEND' | 'REFUND' | 'ADJUSTMENT'
  amount: number | string
  status: string
  currency: string
  createdAt: string
}

export interface AdminPetWalletAdjustment {
  id: number | string
  userId: number | string
  delta: number | string
  reason: string
  ticketNo?: string | null
  requestedBy: number | string
  approvedBy?: number | string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  version: number | string
  transactionId?: number | string | null
  createdAt: string
  reviewedAt?: string | null
}

export function listPetWalletAccounts(params: { userId?: number | string; status?: string; page?: number; size?: number } = {}) {
  return request.get<ApiResponse<AdminPetWalletAccount[]>>('/admin/pet/wallet/accounts', { params })
}

export function getPetWalletAccount(userId: number | string) {
  return request.get<ApiResponse<AdminPetWalletAccount>>(`/admin/pet/wallet/accounts/${userId}`)
}

export function listPetWalletTransactions(params: { userId?: number | string; bizType?: string; direction?: string; page?: number; size?: number } = {}) {
  return request.get<ApiResponse<AdminPetWalletTransaction[]>>('/admin/pet/wallet/transactions', { params })
}

export function freezePetWalletAccount(userId: number | string, data: { reason: string; expectedVersion?: number | string }) {
  return request.post<ApiResponse<void>>(`/admin/pet/wallet/accounts/${userId}/freeze`, data)
}

export function unfreezePetWalletAccount(userId: number | string, data: { reason: string; expectedVersion?: number | string }) {
  return request.post<ApiResponse<void>>(`/admin/pet/wallet/accounts/${userId}/unfreeze`, data)
}

export function createPetWalletAdjustment(data: { userId: number | string; delta: string; reason: string; ticketNo?: string }) {
  return request.post<ApiResponse<AdminPetWalletAdjustment>>('/admin/pet/wallet/adjustments', data)
}

export function approvePetWalletAdjustment(id: number | string, data: { reason?: string; expectedVersion?: number | string } = {}) {
  return request.post<ApiResponse<AdminPetWalletAdjustment>>(`/admin/pet/wallet/adjustments/${id}/approve`, data)
}

export function rejectPetWalletAdjustment(id: number | string, data: { reason?: string; expectedVersion?: number | string } = {}) {
  return request.post<ApiResponse<AdminPetWalletAdjustment>>(`/admin/pet/wallet/adjustments/${id}/reject`, data)
}

export function listPetWalletAdjustments(params: { status?: string; userId?: number | string; page?: number; size?: number } = {}) {
  return request.get<ApiResponse<AdminPetWalletAdjustment[]>>('/admin/pet/wallet/adjustments', { params })
}

export function listPetWalletReconciliations(params: { page?: number; size?: number } = {}) {
  return request.get<ApiResponse<Record<string, unknown>[]>>('/admin/pet/wallet/reconciliations', { params })
}

export function getPetWalletReconciliation(runId: number | string) {
  return request.get<ApiResponse<Record<string, unknown>[]>>(`/admin/pet/wallet/reconciliations/${runId}`)
}

export function runPetWalletReconciliation() {
  return request.post<ApiResponse<void>>('/admin/pet/wallet/reconciliations/run')
}

// ==================== P0-2 举报处理闭环 ====================

/** 举报条目（与后端 pet_report 对齐） */
export interface AdminPetReport {
  id: number | string
  reporterUserId: number | string
  targetType: 'WALL_MESSAGE' | 'BOTTLE_CONTENT' | 'NICKNAME' | 'CHAT_MESSAGE'
  targetId: number | string
  reason: string
  status: 'PENDING' | 'HANDLED' | 'REJECTED'
  handledBy?: number | string | null
  handledAt?: string | null
  handleAction?: 'CONTENT_REMOVED' | 'USER_WARNED' | 'USER_PET_BANNED' | 'DISMISSED' | null
  handleReason?: string | null
  isAuto?: number
  createdAt: string
}

/** 举报列表（status 过滤 + 分页） */
export function getPetReports(params: { page: number; size: number; status?: string }) {
  return request.get<ApiResponse<AdminPetReport[]>>('/admin/pet/reports', { params })
}

/** 闭环处理举报（处理后通知举报人；CONTENT_REMOVED 联动隐藏留言内容） */
export function resolvePetReport(id: number | string, data: { action: string; reason: string }) {
  return request.post<ApiResponse<void>>(`/admin/pet/reports/${id}/resolve`, data)
}

// ==================== F5 用户宠物查询与运营工具 ====================

/** 背包摘要行 */
export interface AdminUserPetInventoryLine {
  itemType: string
  itemCode: string
  quantity: number
}

/** 客服视角的宠物全貌 */
export interface AdminUserPet {
  petId: number | string
  userId: number | string
  name: string
  species: string
  gender: string
  level: number
  exp: number
  growthStage: string
  evolutionStage: number
  skinCode: string | null
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
  status: string | null
  isActive: boolean
  isPublic: boolean
  inventorySummary: AdminUserPetInventoryLine[]
  walletBalance: number | string | null
  walletStatus: string | null
}

/** 用户宠物全貌（客服工单查询用） */
export function getUserPets(userId: number | string) {
  return request.get<ApiResponse<AdminUserPet[]>>(`/admin/pet/users/${userId}/pets`)
}

/** 宠物数值调整（白名单字段 + 幅度上限 + 快照审计） */
export function adjustUserPet(
  userId: number | string,
  petId: number | string,
  data: { field: string; delta: number; reason: string },
) {
  return request.post<ApiResponse<Record<string, unknown>>>(
    `/admin/pet/users/${userId}/pets/${petId}/adjust`,
    data,
  )
}

/** 钱包补偿申请（走调账审批流，须另一管理员审批入账） */
export function compensateUser(
  userId: number | string,
  data: { delta: number; reason: string; ticketNo?: string },
) {
  return request.post<ApiResponse<Record<string, unknown>>>(
    `/admin/pet/users/${userId}/compensation`,
    data,
  )
}

// ==================== F2 赛季管理 ====================

export interface AdminPetSeason {
  id: number | string
  name: string
  startsAt: string
  endsAt: string
  status: 'ACTIVE' | 'SETTLED'
  settledAt: string | null
  createdAt: string
}

export interface AdminPetSeasonReward {
  id?: number | string
  seasonId?: number | string
  rankMin: number
  rankMax: number
  rewardStarlight: number
  rewardExp: number
}

export function listPetSeasons(params: { page: number; size: number }) {
  return request.get<ApiResponse<AdminPetSeason[]>>('/admin/pet/seasons', { params })
}

export function upsertPetSeason(data: { id?: number | string; name: string; startsAt: string; endsAt: string }) {
  return request.post<ApiResponse<AdminPetSeason>>('/admin/pet/seasons', data)
}

export function listPetSeasonRewards(id: number | string) {
  return request.get<ApiResponse<AdminPetSeasonReward[]>>(`/admin/pet/seasons/${id}/rewards`)
}

export function savePetSeasonRewards(id: number | string, tiers: AdminPetSeasonReward[]) {
  return request.post<ApiResponse<void>>(`/admin/pet/seasons/${id}/rewards`, { tiers })
}

export function settlePetSeason(id: number | string) {
  return request.post<ApiResponse<void>>(`/admin/pet/seasons/${id}/settle`)
}

// ==================== P0-1 内容安全：敏感词库 ====================

export interface AdminPetSensitiveWord {
  id: number | string
  word: string
  category: 'POLITICS' | 'ABUSE' | 'AD' | 'CRISIS'
  status: number
  createdAt: string
}

export function listPetSensitiveWords(params: { page?: number; size?: number; status?: number }) {
  return request.get<ApiResponse<AdminPetSensitiveWord[]>>('/admin/pet/configs/sensitive-words', { params })
}

export function upsertPetSensitiveWord(data: {
  id?: number | string
  word: string
  category: string
  enabled?: boolean
}) {
  return request.post<ApiResponse<AdminPetSensitiveWord>>('/admin/pet/configs/sensitive-words', data)
}

export function deletePetSensitiveWord(id: number | string) {
  return request.delete<ApiResponse<void>>(`/admin/pet/configs/sensitive-words/${id}`)
}

// ==================== F8 口头禅配置（DB 权威 + 60s 定时同步） ====================

export interface AdminPetPersonaPhrase {
  personality: string
  phrase: string
  source: 'DB' | 'DEFAULT'
}

export function listPetPersonaPhrases() {
  return request.get<ApiResponse<AdminPetPersonaPhrase[]>>('/admin/pet/configs/persona-phrases')
}

export function upsertPetPersonaPhrase(data: { personality: string; phrase: string }) {
  return request.post<ApiResponse<void>>('/admin/pet/configs/persona-phrases', data)
}

// ==================== BE-11 相册审核 / B21 配置治理 ====================

/** 相册资源审核通过（仅审核链路可设 APPROVED；用户上传进入时为 PENDING） */
export function approvePetAlbumAsset(assetId: number | string) {
  return request.post<ApiResponse<Record<string, unknown>>>(`/admin/pet/album/${assetId}/approve`)
}

/** 配置治理支持的配置类型（与后端白名单一致） */
// R07：pet/pet_season 是运行实体——通用配置回退会覆盖经验/主宠标记/赛季状态等运行字段，
// 已在后端硬拒绝；此处同步移除选项，数值调整与赛季状态不走通用回退
export const PET_CONFIG_GOVERNANCE_TYPES = [
  'job', 'study', 'career', 'furniture', 'equipment', 'skin', 'skill',
  'evolution', 'event', 'daily_quest', 'sensitive_word', 'food',
] as const

/** 配置校验预览（数值上下限组合校验，不落库） */
export function validatePetConfigGovernance(data: { configType: string; data: Record<string, unknown> }) {
  return request.post<ApiResponse<void>>('/admin/pet/config-governance/validate', data)
}

/** 配置历史版本（发布/回退快照，最近 50 条） */
export interface AdminPetConfigVersion {
  id: number | string
  configType: string
  configId: number | string
  version: number
  operation: 'PUBLISH' | 'ROLLBACK'
  operator: string
  snapshot: string
  createdAt: string
}

export function listPetConfigGovernanceHistory(params: { configType: string; configId: number | string }) {
  return request.get<ApiResponse<AdminPetConfigVersion[]>>('/admin/pet/config-governance/history', { params })
}

/** 回退配置（将指定版本快照写回目标行；回退动作本身留版本审计） */
export function rollbackPetConfigGovernance(data: { configType: string; configId: number | string; version: number }) {
  return request.post<ApiResponse<void>>('/admin/pet/config-governance/rollback', data)
}
