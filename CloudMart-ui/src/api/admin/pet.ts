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
