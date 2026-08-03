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
