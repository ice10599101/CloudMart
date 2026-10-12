import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

// ========== 星光转赠（R19，契约对齐 mall-wish StarlightTransferController） ==========

export type StarlightTransferDirection = 'SENT' | 'RECEIVED'

export interface StarlightTransferRecord {
  id: number | string
  fromUserId: number
  toUserId: number
  amount: number
  message: string | null
  /** 当前查看者视角：SENT=发出，RECEIVED=收到 */
  direction: StarlightTransferDirection
  createdAt: string
  status: string
  /** 转赠后当日剩余可转额度 */
  remainingToday: number
}

export interface StarlightTransferLimits {
  /** 每日转赠上限 */
  dailyCap: number
  /** 今日已用额度 */
  usedToday: number
  /** 今日剩余额度 */
  remainingToday: number
  /** 额度所属业务日期（ISO 8601） */
  businessDate: string
}

export interface CreateTransferRequest {
  toUserId: number
  /** 10-100 的整数 */
  amount: number
  /** 附言，最长 100 字 */
  message?: string
  /** 幂等键：同一键重试收敛为同一笔转赠 */
  requestKey: string
}

/** 今日转赠额度 */
export function getTransferLimits() {
  return request.get<ApiResponse<StarlightTransferLimits>>('/wish/starlight/transfers/limits')
}

/** 我的转赠记录（发出/收到，page 分页） */
export function getMyTransfers(params: { page: number; pageSize: number }) {
  return request.get<ApiResponse<{ records: StarlightTransferRecord[]; total: number }>>(
    '/wish/starlight/transfers/my',
    { params },
  )
}

/** 发起星光转赠（必须携带 requestKey 保证幂等） */
export function createTransfer(data: CreateTransferRequest) {
  return request.post<ApiResponse<StarlightTransferRecord>>('/wish/starlight/transfers', data)
}
