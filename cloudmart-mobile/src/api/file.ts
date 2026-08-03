import Taro from '@tarojs/taro'
import request from '@/utils/request'

const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP
const API_BASE = IS_WEAPP ? 'http://localhost:8080' : '/api'

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
