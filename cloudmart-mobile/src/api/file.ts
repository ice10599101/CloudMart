import Taro from '@tarojs/taro'
import request from '@/utils/request'

const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP
const API_BASE = IS_WEAPP ? 'http://localhost:8080' : '/api'
export { API_BASE }

/**
 * 将后端返回的本地存储相对 URL（/files/...）解析为当前环境可访问的地址：
 * - 小程序：域名白名单限制，必须返回完整 http 地址（经 web 主站 nginx 8080 的 /files 反代）
 * - H5：保持相对路径，由 dev server proxy / nginx 的 /files 反代提供
 */
export function resolveFileUrl(url: string): string {
  if (url.startsWith('/files/') && IS_WEAPP) {
    return `http://localhost:8080${url}`
  }
  return url
}

export const fileApi = {
  upload: (filePath: string) => {
    return new Promise<{ data: { data: { url: string } } }>((resolve, reject) => {
      const token = Taro.getStorageSync('access_token')
      const uploadTask = Taro.uploadFile({
        url: `${API_BASE}/file/upload`,
        filePath,
        name: 'file',
        header: token ? { Authorization: `Bearer ${token}` } : {},
        success: (res) => {
          try {
            const data = JSON.parse(res.data)
            if (data?.data?.url) {
              data.data.url = resolveFileUrl(data.data.url)
            }
            resolve({ data })
          } catch {
            reject(new Error('Parse upload response failed'))
          }
        },
        fail: (err) => reject(err),
      })
      // Return upload task for progress tracking if needed
      return uploadTask
    })
  },
  delete: (fileUrl: string) => request({ url: '/file/delete', method: 'DELETE', data: { url: fileUrl } }),
}
