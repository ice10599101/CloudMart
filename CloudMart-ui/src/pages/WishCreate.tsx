import { useState, useEffect, useCallback, useRef } from 'react'
import { Form, Input, Modal, Select, DatePicker, Button, Upload, Tag, App, Radio, Card, Progress, Popconfirm } from 'antd'
import { StarOutlined, PlusOutlined, ReloadOutlined, CloseOutlined } from '@ant-design/icons'
import axios from 'axios'
import { history } from 'umi'
import dayjs, { type Dayjs } from 'dayjs'
import { createWish, getCategories, saveWishDraft, listMyWishDrafts, deleteWishDraft, publishWishDraft, type WishDraft } from '@/api/wish'
import { materializeAttachments } from '@/utils/attachmentMaterialize'
import type { Category, WishVisibility } from '@/api/wish'
import { uploadFile } from '@/api/file'
import { useAuthStore } from '@/stores/auth'
import { stripHtml } from '@/utils/format'
import styles from './WishCreate.module.css'
import WishBGM from '@/components/WishBGM'
import TiptapEditor from '@/components/TiptapEditor'

const MAX_TAGS = 5
const MAX_MEDIA = 9
const MAX_FILE_SIZE = 10 * 1024 * 1024
// 富文本 HTML 长度上限，与后端 CreateWishRequest.description @Size(max = 20000) 对齐
const MAX_DESCRIPTION_HTML_LENGTH = 20000

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
  // ---- 心愿草稿 v2（clientDraftId 幂等 + version 乐观锁）----
  const draftKeyRef = useRef<string>(`web-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`)
  const draftVersionRef = useRef<number | undefined>(undefined)
  const [draftsOpen, setDraftsOpen] = useState(false)
  const [drafts, setDrafts] = useState<WishDraft[]>([])
  const [draftsLoading, setDraftsLoading] = useState(false)
  const [draftSaving, setDraftSaving] = useState(false)
  const { message, modal } = App.useApp()
  const { user, userLoading } = useAuthStore()
  const cancelTokenMapRef = useRef<Map<string, (message?: string) => void>>(new Map())

  const uploadedUrls = uploads.filter(u => u.status === 'success' && u.url).map(u => u.url!) as string[]
  const isUploading = uploads.some(u => u.status === 'uploading')

  useEffect(() => {
    if (!user && !userLoading) {
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

  const parseDraftJson = (raw: string | null): string[] => {
    if (!raw) return []
    try {
      const parsed = JSON.parse(raw)
      return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string') : []
    } catch {
      return []
    }
  }

  const handleSaveDraft = async () => {
    const values = form.getFieldsValue() as Partial<SubmitValues>
    if (!values.title?.trim() && !values.description?.trim()) {
      message.warning('标题或描述至少填写一项再保存草稿')
      return
    }
    setDraftSaving(true)
    try {
      const res = await saveWishDraft({
        clientDraftId: draftKeyRef.current,
        title: values.title ?? '（无标题草稿）',
        description: values.description,
        categoryId: values.categoryId,
        visibility: values.visibility,
        expectedAt: values.expectedAt?.toISOString(),
        mediaUrls: uploadedUrls.length > 0 ? uploadedUrls : undefined,
        tags: tags.length > 0 ? tags : undefined,
        version: draftVersionRef.current,
      })
      if (res.data.success && res.data.data) {
        draftVersionRef.current = res.data.data.version
        message.success('草稿已保存（可在「我的草稿」中继续编辑）')
      }
    } catch (err) {
      // W01/QA27：两设备并发保存 → 409 WISH_VERSION_CONFLICT（含服务端版本）。
      // 本地内容不丢弃：提供「重新载入」或「保留本地副本」两种选择
      const code = (err as { code?: string })?.code
      if (code === 'WISH_VERSION_CONFLICT') {
        Modal.confirm({
          title: '草稿已被其他端修改',
          content: '本地内容未覆盖服务端。可选择重新载入最新草稿（覆盖本地编辑），或保留本地内容自行处理。',
          okText: '重新载入最新草稿',
          cancelText: '保留本地内容',
          onOk: async () => {
            const list = await listMyWishDrafts()
            const latest = (list.data.data ?? []).find((d) => d.clientDraftId === draftKeyRef.current)
            if (latest) {
              draftVersionRef.current = latest.version
              form.setFieldsValue({
                title: latest.title,
                description: latest.description,
                categoryId: latest.categoryId,
                visibility: latest.visibility,
              })
              message.success('已载入服务端最新草稿')
            }
          },
        })
      }
    } finally {
      setDraftSaving(false)
    }
  }

  const openDrafts = async () => {
    setDraftsOpen(true)
    setDraftsLoading(true)
    try {
      const res = await listMyWishDrafts()
      setDrafts(res.data.data ?? [])
    } finally {
      setDraftsLoading(false)
    }
  }

  const handleLoadDraft = (draft: WishDraft) => {
    form.setFieldsValue({
      title: draft.title,
      description: draft.description ?? undefined,
      categoryId: draft.categoryId ?? undefined,
      visibility: (draft.visibility as WishVisibility) || 'PUBLIC',
      expectedAt: draft.expectedAt ? dayjs(draft.expectedAt) : undefined,
    })
    setTags(parseDraftJson(draft.tags))
    setUploads(parseDraftJson(draft.mediaUrls).map((url, index) => ({
      id: `draft-${draft.id}-${index}`,
      // 草稿媒体是已上传资产：file 仅为类型占位，不再参与上传流程
      file: new File([], `draft-asset-${index}`),
      url,
      progress: 100,
      status: 'success' as const,
    })))
    draftKeyRef.current = draft.clientDraftId
    draftVersionRef.current = draft.version
    setDraftsOpen(false)
    message.success('草稿已载入，继续编辑后可发布')
  }

  const handleDeleteDraft = async (draft: WishDraft) => {
    const res = await deleteWishDraft(draft.id)
    if (res.data.success) {
      message.success('草稿已删除')
      setDrafts((prev) => prev.filter((d) => d.id !== draft.id))
    }
  }

  const handlePublishDraft = async (draft: WishDraft) => {
    const res = await publishWishDraft(draft.id)
    if (res.data.success) {
      message.success('草稿已发布')
      setDraftsOpen(false)
      const wishId = res.data.data?.wishId
      if (wishId) history.push(`/wish/${wishId}`)
    }
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
        message.success('心愿发布成功！审核通过后将展示在心愿广场')
        await materializeAttachments(values.description, 'WISH', res.data.data.id)
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
                {
                  required: true,
                  // Tiptap 空文档的 HTML 是 <p></p>，必须剥掉标签后判断是否真的有内容
                  validator: (_, value: string) => {
                    if (!stripHtml(value)) return Promise.reject(new Error('请描述你的心愿'))
                    return Promise.resolve()
                  },
                },
                { max: MAX_DESCRIPTION_HTML_LENGTH, message: `描述不超过 ${MAX_DESCRIPTION_HTML_LENGTH} 字符` },
              ]}
            >
              <TiptapEditor placeholder="详细描述你的心愿、计划或梦想..." />
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
              <Button size="large" onClick={handleSaveDraft} loading={draftSaving} disabled={isUploading}>
                保存草稿
              </Button>
              <Button size="large" onClick={openDrafts}>
                我的草稿
              </Button>
            </Form.Item>
            <Form.Item noStyle>
              <Modal
                title="我的草稿（最多 20 份）"
                open={draftsOpen}
                onCancel={() => setDraftsOpen(false)}
                footer={null}
                width={560}
              >
                {draftsLoading ? (
                  <div style={{ textAlign: 'center', padding: 32 }}>加载中...</div>
                ) : drafts.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: 32, color: 'var(--color-text-tertiary)' }}>
                    还没有草稿；填写表单后点「保存草稿」
                  </div>
                ) : (
                  <div style={{ display: 'grid', gap: 12 }}>
                    {drafts.map((draft) => (
                      <div
                        key={draft.id}
                        style={{
                          border: '1px solid var(--color-border)',
                          borderRadius: 8,
                          padding: 12,
                          display: 'flex',
                          justifyContent: 'space-between',
                          gap: 12,
                          alignItems: 'center',
                        }}
                      >
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {draft.title || '（无标题）'}
                          </div>
                          <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2 }}>
                            更新于 {draft.updatedAt ? new Date(draft.updatedAt).toLocaleString('zh-CN') : '—'} · v{draft.version}
                            {draft.publishedWishId ? ' · 已发布过' : ''}
                          </div>
                        </div>
                        <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                          <Button size="small" type="primary" onClick={() => handleLoadDraft(draft)}>
                            载入
                          </Button>
                          <Button size="small" onClick={() => handlePublishDraft(draft)}>
                            直接发布
                          </Button>
                          <Popconfirm title="删除这份草稿？" onConfirm={() => handleDeleteDraft(draft)}>
                            <Button size="small" danger>
                              删除
                            </Button>
                          </Popconfirm>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </Modal>
            </Form.Item>
          </Form>
        </Card>
      </div>
      <WishBGM />
    </div>
  )
}
