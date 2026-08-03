import request from '@/utils/request'

export function getGenTables(params?: Record<string, any>) {
  return request.get('/gen/tables', { params })
}

export function getGenTableDetail(tableName: string) {
  return request.get(`/gen/tables/${tableName}`)
}

export function previewGenCode(data: Record<string, any>) {
  return request.post('/gen/preview', data)
}

export function downloadGenCode(data: Record<string, any>) {
  return request.post('/gen/download', data, { responseType: 'blob' })
}
