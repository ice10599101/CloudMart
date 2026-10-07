import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface CheckInResult {
  checkedIn: boolean
  continuousDays: number
  expReward: number
  totalExp: number
  currentLevel: number
  levelTitle: string
  levelIcon: string
}

export interface UserLevelInfo {
  userId: number
  level: number
  exp: number
  totalExp: number
  levelTitle: string
  levelIcon: string
  nextLevelExp: number
  nextLevelTitle: string
  expProgress: number
}

export interface LevelConfig {
  id: number
  level: number
  title: string
  minExp: number
  icon: string
  benefits: string
  status: number
}

export interface ExpLogRecord {
  id: number
  expChange: number
  source: string
  bizId: number | null
  description: string
  createdAt: string
}

export function checkIn() {
  return request.post<ApiResponse<CheckInResult>>('/community/growth/check-in')
}

export function getCheckInStatus() {
  return request.get<ApiResponse<boolean>>('/community/growth/check-in/status')
}

export function getUserLevel() {
  return request.get<ApiResponse<UserLevelInfo>>('/community/growth/level')
}

export function getExpLogs(page = 1, size = 20) {
  return request.get<ApiResponse<ExpLogRecord[]>>('/community/growth/exp-logs', { params: { page, size } })
}

export function getLevelConfigs() {
  return request.get<ApiResponse<LevelConfig[]>>('/community/growth/level-configs')
}

export function getCheckInCalendar(year: number, month: number) {
  return request.get<ApiResponse<string[]>>('/community/growth/check-in/calendar', {
    params: { year, month },
  })
}

export function getContinuousDays() {
  return request.get<ApiResponse<number>>('/community/growth/check-in/continuous')
}

export interface UserDecoration {
  userId: number
  level: number
  levelTitle: string
  levelIcon: string
  avatarFrame: string
  badgeCount: number
  /** 用户头像 URL（供无 src 的头像展示场景直接使用，如消息通知列表） */
  avatar?: string | null
}

/** 批量查询用户头像装饰（公开接口，供全站头像处展示） */
export function getUserDecorations(ids: Array<number | string>) {
  return request.get<ApiResponse<Record<string, UserDecoration>>>('/community/growth/decorations', {
    params: { ids: ids.join(',') },
  })
}

/** 设置当前用户头像框（Lv2+ 权益） */
export function setAvatarFrame(frame: string) {
  return request.put<ApiResponse<void>>('/community/growth/avatar-frame', null, { params: { frame } })
}

// ========== P1-18 社区排行榜（契约对齐 mall-community RankingController /growth/ranking） ==========

export interface RankingItem {
  userId: number
  expValue: number
  rankNo: number
}

export interface MyRanking {
  userId: number
  expValue: number
  rankNo: number
}

export interface RankingSeason {
  id: number
  name: string
  seasonKey: string
  startDate: string
  endDate: string
  status: number
}

/** 当月经验榜 Top N */
export function getMonthlyRanking(size = 50) {
  return request.get<ApiResponse<RankingItem[]>>('/community/growth/ranking', { params: { size } })
}

/** 我的当月排名 */
export function getMyRanking() {
  return request.get<ApiResponse<MyRanking>>('/community/growth/ranking/me')
}

/** 历史赛季列表（分页） */
export function getRankingSeasons(page = 1, size = 20) {
  return request.get<ApiResponse<RankingSeason[]>>('/community/growth/ranking/seasons', { params: { page, size } })
}

/** 赛季榜单详情（分页） */
export function getSeasonRanking(seasonId: number | string, page = 1, size = 50) {
  return request.get<ApiResponse<RankingItem[]>>(`/community/growth/ranking/seasons/${seasonId}`, { params: { page, size } })
}
