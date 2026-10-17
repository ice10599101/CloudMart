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
  /** OPS-03：业务关联键（requestId）/下一次重试时间（运营列） */
  requestId: string | null
  nextRetryAt: string | null
}

/** R15：单层分页响应（records/total/stats，无 data.data 双层包装） */
export interface OperationsPage {
  records: OperationsTask[]
  total: number
  stats: Record<string, number>
}

/** T16：失败任务分页（service=mall-order/mall-wish；status 可选；附各状态计数） */
export function listOperationsTasks(
  service: 'mall-order' | 'mall-wish',
  params: { status?: string; page?: number; pageSize?: number },
) {
  return request.get<ApiResponse<OperationsPage>>(
    '/admin/operations/outbox',
    { params: { ...params, service } },
  )
}

/** T16：重试死信（仅 DEAD/DEAD_LETTER；受理≠成功；reason 必填随审计留痕；返回受理后实际状态） */
export function retryOperationsTask(service: 'mall-order' | 'mall-wish', eventId: string, reason: string) {
  return request.post<ApiResponse<{ eventId: string; accepted: boolean; status: string }>>(
    `/admin/operations/outbox/${service}/${eventId}/retry`,
    null,
    { params: { reason } },
  )
}

// ===== OPS-03：账号注销任务（mall-user）运维处置 =====

export interface DeletionTask {
  id: number | string
  userId: number | string
  status: string
  serviceProgress: string
  blockReason: string
  requestedAt: string
  executedAt: string
}

export function listDeletionTasks(params: { status?: string; page?: number; pageSize?: number }) {
  return request.get<ApiResponse<{ records: DeletionTask[]; total: number }>>(
    '/admin/operations/deletion',
    { params },
  )
}

/** 重试注销失败步骤（reason 必填随审计留痕；不跳过资金检查） */
export function retryDeletionTask(taskId: number | string, reason: string) {
  return request.post<ApiResponse<unknown>>(`/admin/operations/deletion/${taskId}/retry`, { reason })
}

// ===== OPS-03：心愿数据导出任务（mall-wish）运维处置 =====

export interface ExportTask {
  id: number | string
  userId: number | string
  status: string
  failReason: string
  createdAt: string
  updatedAt: string
  expiresAt: string
}

export function listExportTasks(params: { status?: string; page?: number; pageSize?: number }) {
  return request.get<ApiResponse<{ records: ExportTask[]; total: number }>>(
    '/admin/operations/exports',
    { params },
  )
}

/** 受控重试失败导出（FAILED→PENDING 重排；reason 必填随审计留痕；由持续 worker 认领执行） */
export function retryExportTask(id: number | string, reason: string) {
  return request.post<ApiResponse<unknown>>(`/admin/operations/exports/${id}/retry`, { reason })
}
