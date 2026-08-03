import type { ApiResponse } from '@/types/api'

export interface ProTableResult<T> {
  data: T[]
  total: number
  success: boolean
}

export function resolveProTableData<T>(response: ApiResponse<T[] | unknown>): ProTableResult<T> {
  let data: T[] = []
  let total = response.meta?.total ?? 0

  if (Array.isArray(response.data)) {
    data = response.data
  } else if (response.data && typeof response.data === 'object') {
    const dataObj = response.data as Record<string, unknown>
    if (Array.isArray(dataObj.records)) {
      data = dataObj.records as T[]
      if (!total && typeof dataObj.total === 'number') {
        total = dataObj.total
      }
    }
  }

  return {
    data,
    total,
    success: response.success ?? true,
  }
}

export async function safeProTableRequest<T>(
  fetchFn: () => Promise<{ data: unknown }>,
): Promise<ProTableResult<T>> {
  try {
    const { data: res } = await fetchFn()
    return resolveProTableData<T>(res as ApiResponse<T[]>)
  } catch {
    return { data: [], total: 0, success: false }
  }
}
