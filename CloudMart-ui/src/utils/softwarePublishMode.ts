export type SoftwarePublishMode = 'DENIED' | 'DIRECT' | 'REVIEW'

export const PUBLISH_MODE_LABELS: Record<SoftwarePublishMode, string> = {
  DENIED: '禁止发布',
  DIRECT: '可直接发布',
  REVIEW: '发布后需审核',
}

export function getPublishMode(status?: { canPublish: boolean; publishMode?: SoftwarePublishMode } | null): SoftwarePublishMode {
  if (!status?.canPublish) return 'DENIED'
  return status.publishMode === 'DIRECT' ? 'DIRECT' : 'REVIEW'
}
