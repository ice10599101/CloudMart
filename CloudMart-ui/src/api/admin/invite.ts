import request from '@/utils/request'
import { type ApiResponse } from '@/types/api'

const BASE = '/admin/business/invites'

export interface AdminInviteRelation {
  id: string
  inviterId: string
  inviteeId: string
  rewardStatus: string
  rewardReason: string
  eligibleAt: string
  ruleVersion: number
}

export function listInviteRelations(
  rewardStatus: string | null,
  page = 1,
  size = 20,
) {
  return request.get<ApiResponse<{ items: AdminInviteRelation[]; total: number }>>(
    `${BASE}`,
    { params: { rewardStatus: rewardStatus ?? undefined, page, size } },
  )
}

/** HOLD 复核释放（重新核验资格；原因必填留痕；不直接改发放标记） */
export function releaseInviteHold(relationId: string, reason: string) {
  return request.post<ApiResponse<{ result: string }>>(
    `${BASE}/relations/${relationId}/release-hold`,
    { reason },
  )
}
