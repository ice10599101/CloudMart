import request from '@/utils/request'
import type {
  CheckInStatus,
  UserLevel,
  ExpLog,
  LevelConfig,
  PaginatedResult,
  RankingItem,
  UserRanking,
  RankingSeason,
} from '@/types'

export const growthApi = {
  checkIn: () => request<CheckInStatus>({ url: '/community/growth/check-in', method: 'POST' }),
  getCheckInStatus: () => request<CheckInStatus>({ url: '/community/growth/check-in/status' }),
  getUserLevel: () => request<UserLevel>({ url: '/community/growth/level' }),
  getExpLogs: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<ExpLog>>({ url: `/community/growth/exp-logs${params ? `?page=${params.page || 1}&pageSize=${params.pageSize || 20}` : ''}` }),
  getLevelConfigs: () => request<LevelConfig[]>({ url: '/community/growth/level-configs' }),
  getCheckInCalendar: (year: number, month: number) =>
    request<string[]>({ url: `/community/growth/check-in/calendar?year=${year}&month=${month}` }),
  getContinuousDays: () => request<number>({ url: '/community/growth/check-in/continuous' }),

  // 排行榜
  getMonthlyRanking: (size?: number) =>
    request<RankingItem[]>({ url: `/community/growth/ranking${size ? `?size=${size}` : ''}` }),
  getMyRanking: () => request<UserRanking>({ url: '/community/growth/ranking/me' }),
  getRankingSeasons: (params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<RankingSeason>>({ url: `/community/growth/ranking/seasons${params ? `?page=${params.page || 1}&pageSize=${params.pageSize || 20}` : ''}` }),
  getSeasonRanking: (seasonId: number, params?: { page?: number; pageSize?: number }) =>
    request<PaginatedResult<RankingItem>>({ url: `/community/growth/ranking/seasons/${seasonId}${params ? `?page=${params.page || 1}&pageSize=${params.pageSize || 20}` : ''}` }),
}
