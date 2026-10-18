/**
 * T06：分页响应的显式适配（App 端）。
 *
 * <p>后端存在两种真实契约：{@code ApiResponse.data} 为数组 + {@code meta{page,pageSize,total}}，
 * 以及 IPage（{@code records/current/size/total}）。页面只允许读 {@code items}，
 * 不做 {@code data.list ?? data ?? []} 之类的猜测——契约不符必须抛错，避免把接口故障伪装成空列表。</p>
 */
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

interface Envelope {
  data?: unknown
  meta?: { page?: number; pageSize?: number; total?: number } | null
}

/** 解析分页响应；不匹配任何已知契约时抛出可诊断错误。 */
export function toPageResult<T>(
  envelope: unknown,
  fallback: { page?: number; pageSize?: number } = {},
): PageResult<T> {
  const body = (envelope ?? {}) as Envelope
  const data = body.data

  if (Array.isArray(data)) {
    return {
      items: data as T[],
      total: body.meta?.total ?? data.length,
      page: body.meta?.page ?? fallback.page ?? 1,
      pageSize: body.meta?.pageSize ?? fallback.pageSize ?? data.length,
    }
  }

  if (data && typeof data === 'object') {
    const pageData = data as { records?: unknown; current?: number; size?: number; total?: number }
    if (Array.isArray(pageData.records)) {
      return {
        items: pageData.records as T[],
        total: pageData.total ?? pageData.records.length,
        page: pageData.current ?? fallback.page ?? 1,
        pageSize: pageData.size ?? fallback.pageSize ?? pageData.records.length,
      }
    }
  }

  throw new Error('分页契约不匹配：data 既不是数组（+meta）也不是 IPage(records)')
}

/** 非分页列表（通知、会话等）：必须是数组，否则抛错。 */
export function requireList<T>(envelope: unknown): T[] {
  const data = ((envelope ?? {}) as Envelope).data
  if (!Array.isArray(data)) {
    throw new Error('列表契约不匹配：data 不是数组')
  }
  return data as T[]
}
