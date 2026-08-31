import { useState, useEffect, useCallback, useRef } from 'react'
import { Form, Input, Select, DatePicker, Button, Upload, Tag, App, Radio, Card, Progress } from 'antd'
import { StarOutlined, PlusOutlined, ReloadOutlined, CloseOutlined } from '@ant-design/icons'
import axios from 'axios'
import { history } from 'umi'
import type { Dayjs } from 'dayjs'
import { createWish, getCategories } from '@/api/wish'
import type { Category, WishVisibility } from '@/api/wish'
import { uploadFile } from '@/api/file'
import { useAuthStore } from '@/stores/auth'
import styles from './WishCreate.module.css'
import WishBGM from '@/components/WishBGM'

const { TextArea } = Input
const MAX_TAGS = 5
const MAX_MEDIA = 9
const MAX_FILE_SIZE = 10 * 1024 * 1024

type UploadStatus = 'uploading' | 'success' | 'error' | 'canceled'

interface UploadItem {
  id: string
  file: File
  url?: string
  progress: number
  status: UploadStatus
  errorMessage?: string
}

function isRetryableError(error: unknown): boolean {
  if (axios.isCancel(error)) return false
  const status = (error as { response?: { status?: number } })?.response?.status
  if (status === undefined) return true
  return status >= 500 || status === 408 || status === 429
}

export default function WishCreate() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [categories, setCategories] = useState<Category[]>([])
  const [tags, setTags] = useState<string[]>([])
  const [tagInput, setTagInput] = useState('')
  const [uploads, setUploads] = useState<UploadItem[]>([])
  const { message, modal } = App.useApp()
  const { user } = useAuthStore()
  const cancelTokenMapRef = useRef<Map<string, (message?: string) => void>>(new Map())

  const uploadedUrls = uploads.filter(u => u.status === 'success' && u.url).map(u => u.url!) as string[]
  const isUploading = uploads.some(u => u.status === 'uploading')

  useEffect(() => {
    if (!user) {
      message.warning('请先登录后再发布心愿')
      history.push('/login?redirect=/wish/create')
      return
    }
    const fetchCategories = async () => {
      try {
        const res = await getCategories()
        if (res.data.success) {
          setCategories(res.data.data)
        }
      } catch {
        // ignore
      }
    }
    fetchCategories()
  }, [user])

  const updateUpload = useCallback((id: string, patch: Partial<UploadItem>) => {
    setUploads(prev => prev.map(item => item.id === id ? { ...item, ...patch } : item))
  }, [])

  const performUpload = useCallback(async (item: UploadItem) => {
    const source = axios.CancelToken.source()
    cancelTokenMapRef.current.set(item.id, source.cancel)
    updateUpload(item.id, { status: 'uploading', progress: 0, errorMessage: undefined })

    try {
      const res = await uploadFile(item.file, {
        onProgress: (percent) => updateUpload(item.id, { progress: percent }),
        cancelToken: source.token,
      })
      if (res.data.success && res.data.data?.url) {
        updateUpload(item.id, { status: 'success', progress: 100, url: res.data.data.url })
      } else {
        updateUpload(item.id, { status: 'error', errorMessage: '上传失败，请重试' })
      }
    } catch (error) {
      if (axios.isCancel(error)) {
        updateUpload(item.id, { status: 'canceled' })
      } else {
        const retryable = isRetryableError(error)
        updateUpload(item.id, {
          status: 'error',
          errorMessage: retryable ? '网络异常，请重试' : '文件被拒绝或损坏',
        })
      }
    } finally {
      cancelTokenMapRef.current.delete(item.id)
    }
  }, [updateUpload])

  const handleUpload = async (file: File) => {
    if (uploadedUrls.length >= MAX_MEDIA) {
      message.warning(`最多上传 ${MAX_MEDIA} 张图片`)
      return false
    }
    if (!file.type.startsWith('image/')) {
      message.error('仅支持图片文件')
      return false
    }
    if (file.size > MAX_FILE_SIZE) {
      message.error('单张图片不能超过 10MB')
      return false
    }

    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const item: UploadItem = { id, file, progress: 0, status: 'uploading' }
    setUploads(prev => [...prev, item])
    performUpload(item)
    return false
  }

  const handleRetry = (id: string) => {
    const item = uploads.find(u => u.id === id)
    if (!item) return
    performUpload(item)
  }

  const handleCancelUpload = (id: string) => {
    const cancel = cancelTokenMapRef.current.get(id)
    if (cancel) {
      cancel('用户取消上传')
    } else {
      setUploads(prev => prev.filter(u => u.id !== id))
    }
  }

  const handleRemoveMedia = (id: string) => {
    cancelTokenMapRef.current.delete(id)
    setUploads(prev => prev.filter(u => u.id !== id))
  }

  interface SubmitValues {
    title: string
    description: string
    categoryId: number
    visibility: WishVisibility
    expectedAt?: Dayjs
  }

  const doCreateWish = async (values: SubmitValues) => {
    setLoading(true)
    try {
      const res = await createWish({
        title: values.title,
        description: values.description,
        categoryId: values.categoryId,
        visibility: values.visibility,
        mediaUrls: uploadedUrls.length > 0 ? uploadedUrls : undefined,
        tags: tags.length > 0 ? tags : undefined,
        expectedAt: values.expectedAt?.toISOString(),
      })
      if (res.data.success) {
        message.success('心愿发布成功！')
        history.push(`/wish/${res.data.data.id}`)
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = (values: SubmitValues) => {
    if (isUploading) {
      message.warning('请等待图片上传完成')
      return
    }
    const failedCount = uploads.filter(u => u.status === 'error').length
    if (failedCount > 0) {
      message.warning(`有 ${failedCount} 张图片上传失败，请重试或移除后再发布`)
      return
    }

    modal.confirm({
      title: '确认发布心愿？',
      content: '发布后其他用户将可以看到这条心愿。',
      okText: '确认发布',
      cancelText: '再检查一下',
      onOk: () => doCreateWish(values),
    })
  }

  const handleCancelPublish = () => {
    modal.confirm({
      title: '确认取消发布？',
      content: '已填写的内容和上传的图片将不会被保存。',
      okText: '确认取消',
      cancelText: '继续编辑',
      onOk: () => history.back(),
    })
  }

  const handleAddTag = () => {
    const trimmed = tagInput.trim()
    if (!trimmed) return
    if (tags.length >= MAX_TAGS) {
      message.warning(`标签最多 ${MAX_TAGS} 个`)
      return
    }
    if (tags.includes(trimmed)) {
      message.warning('标签已存在')
      return
    }
    if (trimmed.length > 20) {
      message.warning('单个标签不超过 20 字符')
      return
    }
    setTags([...tags, trimmed])
    setTagInput('')
  }

  const handleRemoveTag = (tag: string) => {
    setTags(tags.filter(t => t !== tag))
  }

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.formWrap}>
        <h1 className={styles.pageTitle}>
          <StarOutlined /> 许下心愿
        </h1>
        <Card className={styles.formCard}>
          <Form
            form={form}
            layout="vertical"
            onFinish={handleSubmit}
            initialValues={{ visibility: 'PUBLIC' }}
            className={styles.form}
          >
            <Form.Item
              name="title"
              label="心愿标题"
              rules={[
                { required: true, message: '请输入心愿标题' },
                { max: 120, message: '标题不超过 120 字符' },
              ]}
            >
              <Input
                placeholder="给你的心愿起个名字..."
                showCount
                maxLength={120}
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="description"
              label="心愿描述"
              rules={[
                { required: true, message: '请描述你的心愿' },
                { max: 2000, message: '描述不超过 2000 字符' },
              ]}
            >
              <TextArea
                placeholder="详细描述你的心愿、计划或梦想..."
                showCount
                maxLength={2000}
                rows={6}
              />
            </Form.Item>

            <Form.Item
              name="categoryId"
              label="心愿分类"
              rules={[{ required: true, message: '请选择心愿分类' }]}
            >
              <Select
                placeholder="选择一个分类"
                size="large"
                options={categories.map(c => ({ label: c.name, value: c.id }))}
              />
            </Form.Item>

            <Form.Item label="图片/媒体（可选，最多 9 张）">
              <div className={styles.uploadArea}>
                {uploads.map(item => (
                  <div
                    key={item.id}
                    className={`${styles.mediaItem} ${item.status === 'error' ? styles.mediaItemError : ''}`}
                  >
                    {item.status === 'success' && item.url ? (
                      <>
                        <img src={item.url} alt="media" className={styles.mediaPreview} />
                        <button
                          type="button"
                          className={styles.mediaRemove}
                          onClick={() => handleRemoveMedia(item.id)}
                          aria-label="移除图片"
                        >
                          ×
                        </button>
                      </>
                    ) : (
                      <div className={styles.uploadProgress}>
                        {item.status === 'uploading' && (
                          <>
                            <Progress
                              type="circle"
                              size={48}
                              percent={item.progress}
                              strokeColor={{ '0%': '#00D4FF', '100%': '#9370DB' }}
                              strokeWidth={8}
                            />
                            <button
                              type="button"
                              className={styles.mediaRemove}
                              onClick={() => handleCancelUpload(item.id)}
                              aria-label="取消上传"
                            >
                              <CloseOutlined style={{ fontSize: 10 }} />
                            </button>
                          </>
                        )}
                        {item.status === 'error' && (
                          <div className={styles.errorOverlay}>
                            <span className={styles.errorText}>{item.errorMessage || '上传失败'}</span>
                            <Button
                              size="small"
                              type="primary"
                              ghost
                              icon={<ReloadOutlined />}
                              onClick={() => handleRetry(item.id)}
                            >
                              重试
                            </Button>
                            <Button
                              size="small"
                              type="text"
                              onClick={() => handleRemoveMedia(item.id)}
                              aria-label="移除失败项"
                            >
                              移除
                            </Button>
                          </div>
                        )}
                        {item.status === 'canceled' && (
                          <div className={styles.errorOverlay}>
                            <span className={styles.errorText}>已取消</span>
                            <Button
                              size="small"
                              type="primary"
                              ghost
                              icon={<ReloadOutlined />}
                              onClick={() => handleRetry(item.id)}
                            >
                              重试
                            </Button>
                            <Button
                              size="small"
                              type="text"
                              onClick={() => handleRemoveMedia(item.id)}
                            >
                              移除
                            </Button>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
                {uploadedUrls.length < MAX_MEDIA && (
                  <Upload
                    accept="image/*"
                    showUploadList={false}
                    beforeUpload={handleUpload}
                    disabled={isUploading}
                    multiple
                  >
                    <div className={styles.uploadTrigger}>
                      {isUploading ? <Progress type="circle" size={48} percent={uploads.find(u => u.status === 'uploading')?.progress ?? 0} strokeWidth={8} strokeColor={{ '0%': '#00D4FF', '100%': '#9370DB' }} /> : <PlusOutlined />}
                    </div>
                  </Upload>
                )}
              </div>
            </Form.Item>

            <Form.Item label="标签（可选，最多 5 个）">
              <div className={styles.tagArea}>
                {tags.map(tag => (
                  <Tag
                    key={tag}
                    closable
                    onClose={() => handleRemoveTag(tag)}
                    className={styles.tag}
                  >
                    {tag}
                  </Tag>
                ))}
                {tags.length < MAX_TAGS && (
                  <Input
                    size="small"
                    placeholder="输入标签后回车"
                    value={tagInput}
                    onChange={e => setTagInput(e.target.value)}
                    onPressEnter={handleAddTag}
                    className={styles.tagInput}
                    maxLength={20}
                  />
                )}
              </div>
            </Form.Item>

            <Form.Item name="visibility" label="可见性">
              <Radio.Group>
                <Radio value="PUBLIC">公开（所有人可见）</Radio>
                <Radio value="PRIVATE">私密（仅自己可见）</Radio>
                <Radio value="TREE_HOLE">树洞（匿名+AI回复）</Radio>
              </Radio.Group>
            </Form.Item>

            <Form.Item name="expectedAt" label="预计完成时间（可选）">
              <DatePicker
                showTime
                style={{ width: '100%' }}
                size="large"
              />
            </Form.Item>

            <Form.Item className={styles.submitArea}>
              <Button
                type="default"
                size="large"
                onClick={handleCancelPublish}
                className={styles.cancelBtn}
              >
                取消
              </Button>
              <Button
                type="primary"
                htmlType="submit"
                size="large"
                loading={loading}
                disabled={isUploading}
                className={styles.submitBtn}
              >
                发布心愿
              </Button>
            </Form.Item>
          </Form>
        </Card>
      </div>
      <WishBGM />
    </div>
  )
}
