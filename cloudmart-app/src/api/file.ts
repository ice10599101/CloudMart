import request from '@/utils/request'
import { resolveMediaUrl } from '@/utils/mediaUrl'
import { Platform } from 'react-native'

/** Web 需要真实 Blob；原生端由网络适配器读取 URI。 */
export async function createUploadForm(file: { uri: string; name: string; type: string }) {
  const form = new FormData()
  const filename = (mime: string) => {
    const extension = ({ 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp',
      'image/gif': 'gif', 'image/bmp': 'bmp' } as Record<string, string>)[mime]
    return extension ? `${file.name.replace(/\.[^.]+$/, '')}.${extension}` : file.name
  }
  if (Platform.OS === 'web') {
    const response = await fetch(file.uri)
    if (!response.ok) throw new Error('读取上传文件失败')
    const blob = await response.blob()
    form.append('file', blob, filename(blob.type || file.type))
  } else {
    form.append('file', { ...file, name: filename(file.type) } as unknown as Blob)
  }
  return form
}

export interface FileUploadResult {
  fileId: string
  url: string | null
  mime: string
  fileSize: number
  visibility: 'PUBLIC' | 'PRIVATE'
  status: string
  [key: string]: unknown
}

export const fileApi = {
  // S01/LC05：唯一资产上传入口 /file/assets（旧 /file/upload 已删除）；
  // PET-13/T31：相册等私密资产必须显式传 PRIVATE（缺省 = 服务端默认 PUBLIC）
  upload: async (data: FormData | { file: string; type?: string }, visibility?: 'PUBLIC' | 'PRIVATE') => {
    const mime = data instanceof FormData ? '' : data.type || 'image/jpeg'
    const body = data instanceof FormData ? data : await createUploadForm({
      uri: data.file.startsWith('data:') ? data.file : `data:${mime};base64,${data.file}`,
      name: `upload.${mime.split('/')[1] === 'jpeg' ? 'jpg' : mime.split('/')[1]}`,
      type: mime,
    })
    const res = await request<FileUploadResult>({
      url: visibility ? `/file/assets?visibility=${visibility}` : '/file/assets',
      method: 'POST',
      data: body,
      // Web 自动生成 boundary；原生 Axios 适配器需要 multipart 类型。
      header: Platform.OS === 'web' ? undefined : { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    })
    // 后端本地存储返回相对 URL：Native 端（RN Image/Audio）需要完整地址
    const url = res.data?.data?.url
    if (url) {
      res.data.data.url = resolveMediaUrl(url)
    }
    return res
  },
}
