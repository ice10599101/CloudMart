import request from '@/utils/request'

// OPS-01：调度唯一事实来源是 XXL-JOB——任务生命周期（新建/编辑/启停/Cron）
// 走 XXL 调度中心控制台；本服务接口只做只读适配 + 白名单手动运行。
export function getJobs(params?: Record<string, any>) {
  return request.get('/job/list', { params })
}

export function getJob(id: number | string) {
  return request.get(`/job/${id}`)
}

export function runJob(id: number | string) {
  return request.post(`/job/${id}/run`)
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
