import { useState, useEffect, useCallback, useRef } from 'react'
import { Form, Input, Button, Upload, Card, App, Modal, Tag, Popconfirm } from 'antd'
import { PlusOutlined, ReloadOutlined, CloseOutlined, GiftOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import axios from 'axios'
import { history, useParams } from 'umi'
import { getWishDetail, submitFulfillment } from '@/api/wish'
import type { WishDetail, WishFulfillmentSubmitResult } from '@/api/wish'
import { uploadFile } from '@/api/file'
import { useAuthStore } from '@/stores/auth'
import styles from './WishFulfillment.module.css'
import WishBGM from '@/components/WishBGM'

const { TextArea } = Input
const MAX_MEDIA = 9
const MAX_FILE_SIZE = 10 * 1024 * 1024
const MAX_STORY = 5000
const MAX_FEELING = 1000

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

/** 绽放粒子（CSS 仪式感动效，与移动/APP 端节奏一致：0.9s 粒子炸裂 + 光晕扩散） */
function BloomCeremony({ starlight, badges }: { starlight: number; badges: { id: number; name: string }[] }) {
  return (
    <div className={styles.ceremony}>
      <div className={styles.bloomCore}>
        <span className={styles.bloomEmoji}>🌸</span>
        <div className={styles.bloomRing} />
        {Array.from({ length: 12 }).map((_, i) => (
          <span
            key={i}
            className={styles.bloomParticle}
            style={{ ['--angle' as string]: `${i * 30}deg`, ['--delay' as string]: `${(i % 4) * 0.06}s` }}
          />
        ))}
      </div>
      <h2 className={styles.ceremonyTitle}>心愿绽放</h2>
      <p className={styles.ceremonyText}>你的果实已经成熟，故事将照亮还在路上的人</p>
      <div className={styles.rewardRow}>
        <Tag color="gold" className={styles.rewardTag}>✨ 星光 +{starlight}</Tag>
        {badges.map((badge) => (
          <Tag key={badge.id} color="purple" className={styles.rewardTag}>🏅 {badge.name}</Tag>
        ))}
      </div>
    </div>
  )
}

export default function WishFulfillment() {
  const params = useParams<{ id: string }>()
  const wishId = params.id ?? ''
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetail | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitResult, setSubmitResult] = useState<WishFulfillmentSubmitResult | null>(null)
  const [uploads, setUploads] = useState<UploadItem[]>([])
  const [leaveConfirmOpen, setLeaveConfirmOpen] = useState(false)
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()
  const cancelTokenMapRef = useRef<Map<string, (message?: string) => void>>(new Map())

  const uploadedUrls = uploads.filter((u) => u.status === 'success' && u.url).map((u) => u.url!) as string[]
  const isUploading = uploads.some((u) => u.status === 'uploading')

  useEffect(() => {
    if (!user && !userLoading) {
      message.warning('请先登录后再还愿')
      history.push(`/login?redirect=/wish/${wishId}/fulfillment`)
      return
    }
    const fetchWish = async () => {
      try {
        const res = await getWishDetail(wishId)
        if (res.data.success) {
          setWish(res.data.data)
        }
      } catch {
        // 错误已由 request 拦截器处理
      } finally {
        setLoading(false)
      }
    }
    fetchWish()
  }, [user, wishId])

  const updateUpload = useCallback((id: string, patch: Partial<UploadItem>) => {
    setUploads((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)))
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
    setUploads((prev) => [...prev, item])
    performUpload(item)
    return false
  }

  const handleRetry = (id: string) => {
    const item = uploads.find((u) => u.id === id)
    if (!item) return
    performUpload(item)
  }

  const handleCancelUpload = (id: string) => {
    const cancel = cancelTokenMapRef.current.get(id)
    if (cancel) {
      cancel('用户取消上传')
    } else {
      setUploads((prev) => prev.filter((u) => u.id !== id))
    }
  }

  const handleRemoveMedia = (id: string) => {
    cancelTokenMapRef.current.delete(id)
    setUploads((prev) => prev.filter((u) => u.id !== id))
  }

  const handleSubmit = async (values: { story: string; feeling?: string }) => {
    if (isUploading) {
      message.warning('请等待图片上传完成')
      return
    }
    const failedCount = uploads.filter((u) => u.status === 'error').length
    if (failedCount > 0) {
      message.warning(`有 ${failedCount} 张图片上传失败，请重试或移除后再提交`)
      return
    }

    setSubmitting(true)
    try {
      const res = await submitFulfillment(wishId, {
        story: values.story,
        mediaUrls: uploadedUrls.length > 0 ? uploadedUrls : undefined,
        feeling: values.feeling?.trim() || undefined,
      })
      if (res.data.success) {
        setSubmitResult(res.data.data)
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setSubmitting(false)
    }
  }

  /** 已填写内容时离开需二次确认（编辑器操作守则） */
  const handleBack = () => {
    const story = form.getFieldValue('story') as string | undefined
    const hasDraft = Boolean(story?.trim()) || uploadedUrls.length > 0
    if (hasDraft && !submitResult) {
      setLeaveConfirmOpen(true)
      return
    }
    history.push(`/wish/${wishId}`)
  }

  if (loading) {
    return (
      <div className={`${styles.container} wish-universe-theme`}>
        <Card loading className={styles.formCard} />
      </div>
    )
  }

  if (!wish) {
    return (
      <div className={`${styles.container} wish-universe-theme`}>
        <Card className={styles.formCard}>
          <p className={styles.invalidText}>心愿不存在或已被删除</p>
          <Button onClick={() => history.push('/wish/my')}>返回我的心愿</Button>
        </Card>
      </div>
    )
  }

  const isAuthor = user?.id === wish.authorId
  if (!isAuthor) {
    return (
      <div className={`${styles.container} wish-universe-theme`}>
        <Card className={styles.formCard}>
          <p className={styles.invalidText}>仅心愿作者可以还愿</p>
          <Button onClick={() => history.push(`/wish/${wishId}`)}>查看心愿</Button>
        </Card>
      </div>
    )
  }

  const fulfillable = wish.status === 'ACTIVE' || wish.status === 'OVERDUE'
  if (!fulfillable && !submitResult) {
    return (
      <div className={`${styles.container} wish-universe-theme`}>
        <Card className={styles.formCard}>
          <p className={styles.invalidText}>
            当前心愿状态为「{wish.status === 'FULFILLED' ? '已还愿' : wish.status}」，无法再次还愿
          </p>
          <Button onClick={() => history.push(`/wish/${wishId}`)}>查看心愿</Button>
        </Card>
      </div>
    )
  }

  // 提交成功：绽放仪式 + 奖励展示
  if (submitResult) {
    return (
      <div className={`${styles.container} wish-universe-theme`}>
        <div className={styles.formWrap}>
          <Card className={styles.formCard}>
            <BloomCeremony starlight={submitResult.starlightReward} badges={submitResult.badgeAwarded} />
            <div className={styles.ceremonyActions}>
              <Button
                type="primary"
                size="large"
                className={styles.submitBtn}
                onClick={() => history.push(`/wish/${wishId}`)}
              >
                查看还愿故事
              </Button>
              <Button size="large" onClick={() => history.push('/wish/my')}>
                返回我的心愿
              </Button>
            </div>
          </Card>
        </div>
        <WishBGM />
      </div>
    )
  }

  return (
    <div className={`${styles.container} wish-universe-theme`}>
      <div className={styles.formWrap}>
        <div className={styles.backBar}>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={handleBack} className={styles.backBtn}>
            返回
          </Button>
        </div>
        <h1 className={styles.pageTitle}>
          <GiftOutlined /> 我要还愿
        </h1>
        <p className={styles.wishTitle}>「{wish.title}」</p>
        <Card className={styles.formCard}>
          <Form form={form} layout="vertical" onFinish={handleSubmit} className={styles.form}>
            <Form.Item
              name="story"
              label="还愿故事"
              rules={[
                { required: true, message: '请写下你的还愿故事' },
                { max: MAX_STORY, message: `故事不超过 ${MAX_STORY} 字符` },
              ]}
            >
              <TextArea
                placeholder="写下这段旅程的故事：如何开始、经历了什么、最终如何抵达……"
                showCount
                maxLength={MAX_STORY}
                rows={10}
              />
            </Form.Item>

            <Form.Item label={`完成照片（可选，最多 ${MAX_MEDIA} 张）`}>
              <div className={styles.uploadArea}>
                {uploads.map((item) => (
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
                          <button
                            type="button"
                            className={styles.mediaRemove}
                            onClick={() => handleCancelUpload(item.id)}
                            aria-label="取消上传"
                          >
                            <CloseOutlined style={{ fontSize: 10 }} />
                          </button>
                        )}
                        {item.status === 'error' && (
                          <div className={styles.errorOverlay}>
                            <span className={styles.errorText}>{item.errorMessage || '上传失败'}</span>
                            <Button size="small" type="primary" ghost icon={<ReloadOutlined />} onClick={() => handleRetry(item.id)}>
                              重试
                            </Button>
                            <Button size="small" type="text" onClick={() => handleRemoveMedia(item.id)} aria-label="移除失败项">
                              移除
                            </Button>
                          </div>
                        )}
                        {item.status === 'canceled' && (
                          <div className={styles.errorOverlay}>
                            <span className={styles.errorText}>已取消</span>
                            <Button size="small" type="primary" ghost icon={<ReloadOutlined />} onClick={() => handleRetry(item.id)}>
                              重试
                            </Button>
                            <Button size="small" type="text" onClick={() => handleRemoveMedia(item.id)}>
                              移除
                            </Button>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
                {uploadedUrls.length < MAX_MEDIA && (
                  <Upload accept="image/*,video/*" showUploadList={false} beforeUpload={handleUpload} disabled={isUploading} multiple>
                    <div className={styles.uploadTrigger}>
                      <PlusOutlined />
                    </div>
                  </Upload>
                )}
              </div>
            </Form.Item>

            <Form.Item
              name="feeling"
              label="感悟（可选）"
              rules={[{ max: MAX_FEELING, message: `感悟不超过 ${MAX_FEELING} 字符` }]}
            >
              <TextArea
                placeholder="这段旅程带给你最深的感悟是什么？"
                showCount
                maxLength={MAX_FEELING}
                rows={3}
              />
            </Form.Item>

            <p className={styles.rewardHint}>提交后心愿将绽放为「🌸 绽放」果实，并获得 ✨ 星光奖励</p>

            <Form.Item className={styles.submitArea}>
              <Popconfirm
                title="确定提交还愿吗？"
                description="提交后心愿状态将变为「已还愿」，不可再修改"
                onConfirm={() => form.submit()}
                okText="确定提交"
                cancelText="再想想"
              >
                <Button type="primary" size="large" loading={submitting} disabled={isUploading} className={styles.submitBtn}>
                  提交还愿
                </Button>
              </Popconfirm>
            </Form.Item>
          </Form>
        </Card>
      </div>

      <Modal
        title="确认离开"
        open={leaveConfirmOpen}
        okText="放弃并离开"
        cancelText="继续编辑"
        onOk={() => history.push(`/wish/${wishId}`)}
        onCancel={() => setLeaveConfirmOpen(false)}
      >
        <p>还愿故事尚未提交，离开后将丢失已填写的内容。</p>
      </Modal>
      <WishBGM />
    </div>
  )
}
