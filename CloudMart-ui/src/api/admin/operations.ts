import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

/** T16 异常处理中心：跨域 outbox 失败任务（脱敏视图，不含 payload） */
export interface OperationsTask {
  eventId: string
  eventType: string
  aggregateId: string
  status: string
  attempts: number
  lastError: string | null
  createdAt: string | null
  updatedAt: string | null
}

/** T16：失败任务分页（service=mall-order/mall-wish；status 可选；附各状态计数） */
export function listOperationsTasks(
  service: 'mall-order' | 'mall-wish',
  params: { status?: string; page?: number; pageSize?: number },
) {
  return request.get<ApiResponse<{ records: OperationsTask[]; stats: Record<string, number> }>>(
    '/admin/operations/outbox',
    { params: { ...params, service } },
  )
}

/** T16：重试死信（仅 DEAD/DEAD_LETTER；受理≠成功；reason 随审计留痕） */
export function retryOperationsTask(service: 'mall-order' | 'mall-wish', eventId: string, reason: string) {
  return request.post<ApiResponse<{ eventId: string; accepted: boolean }>>(
    `/admin/operations/outbox/${service}/${eventId}/retry`,
    null,
    { params: { reason } },
  )
}
