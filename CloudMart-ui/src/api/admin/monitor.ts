import request from '@/utils/request'

export function getJobs(params?: Record<string, any>) {
  return request.get('/job/list', { params })
}

export function getJob(id: number | string) {
  return request.get(`/job/${id}`)
}

export function createJob(data: Record<string, any>) {
  return request.post('/job', data)
}

export function updateJob(id: number | string, data: Record<string, any>) {
  return request.put(`/job/${id}`, data)
}

export function deleteJob(id: number | string) {
  return request.delete(`/job/${id}`)
}

export function changeJobStatus(id: number | string, data: Record<string, any>) {
  return request.put(`/job/${id}/status`, data)
}

export function runJob(id: number | string) {
  return request.put(`/job/${id}/run`)
}

export function getJobLogs(params?: Record<string, any>) {
  return request.get('/job/log/page', { params })
}

export function deleteJobLog(id: number | string) {
  return request.delete(`/job/log/${id}`)
}

export function cleanJobLogs() {
  return request.delete('/job/log/clean')
}

export function getServerInfo() {
  return request.get('/admin/monitor/server')
}

export function getCacheInfo() {
  return request.get('/admin/monitor/cache')
}

export function getOnlineUsers(params?: Record<string, any>) {
  return request.get('/admin/online/list', { params })
}

export function forceLogout(tokenId: string) {
  return request.delete(`/admin/online/${tokenId}`)
}
