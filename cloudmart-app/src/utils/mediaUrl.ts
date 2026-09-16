import { API_BASE } from '@/utils/request'

/**
 * 将后端返回的本地存储相对 URL（/files/...）解析为当前环境可访问的地址。
 * - Web：保持相对路径（metro/nginx 的 /files 反代）
 * - Native：拼上 API 基址的主机部分（dev 走 metro 代理 host，生产走 EXPO_PUBLIC_API_HOST）
 * 绝对 URL（http/https，如存量 OSS 链接）原样返回。
 */
export function resolveMediaUrl(url: string | null | undefined): string {
  if (!url) return ''
  if (!url.startsWith('/files/')) return url
  if (API_BASE.startsWith('http')) {
    return `${API_BASE.replace(/\/api\/?$/, '')}${url}`
  }
  return url
}