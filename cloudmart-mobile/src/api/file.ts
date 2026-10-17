import Taro from '@tarojs/taro'
import { API_BASE } from '@/utils/request'
import { BusinessError } from '@/utils/businessError'

export { API_BASE }

export function resolveFileUrl(url: string): string {
  return url.startsWith('/files/') && API_BASE.startsWith('http')
    ? API_BASE.replace(/\/api\/?$/, '') + url
    : url
}

interface FileUploadResult {
  fileId: string
  url: string | null
  mime: string
  fileSize: number
}

function upload(filePath: string, visibility: 'PUBLIC' | 'PRIVATE' = 'PUBLIC') {
  return new Promise<{ data: { data: FileUploadResult } }>((resolve, reject) => {
    const token = Taro.getStorageSync('access_token')
    Taro.uploadFile({
      url: API_BASE + '/file/assets',
      filePath,
      name: 'file',
      formData: { visibility },
      timeout: 60000,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success: (res) => {
        try {
          const data = JSON.parse(res.data)
          if (res.statusCode >= 400 || data.success !== true) {
            reject(new BusinessError(data.error?.code || 'HTTP_' + res.statusCode,
              data.error?.message || '文件上传失败', { status: res.statusCode }))
            return
          }
          if (data.data?.url) data.data.url = resolveFileUrl(data.data.url)
          resolve({ data })
        } catch {
          reject(new BusinessError('INVALID_RESPONSE', '上传响应格式错误，请重试'))
        }
      },
      fail: () => reject(new BusinessError('NETWORK_ERROR', '文件上传失败，请检查网络连接')),
    })
  })
}

export const fileApi = { upload, uploadAsset: upload }
