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
