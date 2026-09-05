import { useState, useRef, useEffect } from 'react'
import {
  PlusOutlined,
  DeleteOutlined,
  PictureOutlined,
  VideoCameraOutlined,
  SendOutlined,
  SaveOutlined,
  TagOutlined,
  ShoppingOutlined,
} from '@ant-design/icons'
import { history, useSearchParams } from 'umi'
import { Spin, Modal } from 'antd'
import { message } from '@/utils/appMessage'
import TiptapEditor from '@/components/TiptapEditor'
import { createPost, getPostDetail, updatePost, saveDraft } from '@/api/community'
import { uploadFile } from '@/api/file'
import { useAuthStore } from '@/stores/auth'

interface MediaItem {
  uid: string
  type: 'image' | 'video'
  url: string
  file?: File
}

export default function Publish() {
  const { isAuthenticated } = useAuthStore()

  useEffect(() => {
    if (!isAuthenticated) {
      message.warning('请先登录')
      history.replace('/login?redirect=/publish')
    }
  }, [isAuthenticated])

  // 认证守卫提前返回只出现在无 Hook 的外层；表单 Hook 全部收敛到内层组件，保证调用顺序恒定
  if (!isAuthenticated) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--color-bg-base)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" />
      </div>
    )
  }

  // PublishForm 为函数声明，存在提升，此处前向引用安全
  // eslint-disable-next-line @typescript-eslint/no-use-before-define
  return <PublishForm />
}

function PublishForm() {
  const [searchParams] = useSearchParams()
  const editPostId = searchParams.get('edit')
  const isEditing = editPostId !== null

  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [mediaList, setMediaList] = useState<MediaItem[]>([])
  const [tags, setTags] = useState('')
  const [linkProduct, setLinkProduct] = useState(false)
  const [productName, setProductName] = useState('')
  const [productPrice, setProductPrice] = useState('')
  const [publishing, setPublishing] = useState(false)
  const [savingDraft, setSavingDraft] = useState(false)
  const [draftId, setDraftId] = useState<number | string | null>(null)
  const [loadingPost, setLoadingPost] = useState(isEditing)
  const imageInputRef = useRef<HTMLInputElement>(null)
  const videoInputRef = useRef<HTMLInputElement>(null)
  const leavingRef = useRef(false)

  const hasContent = title.trim() || content.trim() || mediaList.length > 0

  // Intercept browser back/close when there's unsaved content
  useEffect(() => {
    if (!hasContent || leavingRef.current) return

    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault()
    }

    const handlePopState = () => {
      window.history.pushState(null, '', window.location.href)
      Modal.confirm({
        title: '确认离开',
        content: '当前有未保存的内容，离开后将丢失，确认离开吗？',
        okText: '确认离开',
        cancelText: '继续编辑',
        onOk: () => {
          leavingRef.current = true
          history.back()
        },
      })
    }

    window.addEventListener('beforeunload', handleBeforeUnload)
    window.history.pushState(null, '', window.location.href)
    window.addEventListener('popstate', handlePopState)

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload)
      window.removeEventListener('popstate', handlePopState)
    }
  }, [hasContent])

  useEffect(() => {
    if (!editPostId) return

    const fetchPost = async () => {
      setLoadingPost(true)
      try {
        const { data: res } = await getPostDetail(editPostId)
        const post = res.data
        setTitle(post.title)
        setContent(post.content)

        if (post.mediaUrls?.length) {
          const existingMedia: MediaItem[] = post.mediaUrls.map((url: string, index: number) => ({
            uid: `existing-${index}`,
            type: post.mediaType === 'VIDEO' ? 'video' : 'image',
            url,
          }))
          setMediaList(existingMedia)
        }

        if (post.tags?.length) {
          setTags(post.tags.map((t: { name: string }) => t.name).join(' '))
        }

        if (post.productId) {
          setLinkProduct(true)
        }
      } catch {
        message.error('加载帖子失败')
        history.back()
      } finally {
        setLoadingPost(false)
      }
    }

    fetchPost()
  }, [editPostId])

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (!files) return
    const newItems: MediaItem[] = Array.from(files).map((f) => ({
      uid: `${Date.now()}-${f.name}`,
      type: 'image' as const,
      url: URL.createObjectURL(f),
      file: f,
    }))
    setMediaList((prev) => [...prev, ...newItems])
    e.target.value = ''
  }

  const handleVideoSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setMediaList((prev) => [
      ...prev,
      {
        uid: `${Date.now()}-${file.name}`,
        type: 'video',
        url: URL.createObjectURL(file),
        file,
      },
    ])
    e.target.value = ''
  }

  const removeMedia = (uid: string) => {
    setMediaList((prev) => {
      const item = prev.find((i) => i.uid === uid)
      if (item) URL.revokeObjectURL(item.url)
      return prev.filter((i) => i.uid !== uid)
    })
  }

  const handlePublish = async () => {
    if (!title.trim()) {
      message.warning('请输入标题')
      return
    }
    if (!content.trim() && mediaList.length === 0) {
      message.warning('请输入内容或添加媒体文件')
      return
    }
    setPublishing(true)
    try {
      const uploadedUrls: string[] = []
      for (const item of mediaList) {
        if (item.file) {
          try {
            const res = await uploadFile(item.file)
            const url = res.data?.data?.url
            if (url) uploadedUrls.push(url)
          } catch {
            // Skip failed uploads
          }
        } else {
          uploadedUrls.push(item.url)
        }
      }

      const mediaType = uploadedUrls.length === 0
        ? 'IMAGE'
        : mediaList.some((m) => m.type === 'video') && mediaList.some((m) => m.type === 'image')
          ? 'MIXED'
          : mediaList.some((m) => m.type === 'video')
            ? 'VIDEO'
            : 'IMAGE'

      const coverImage = uploadedUrls.length > 0 ? uploadedUrls[0] : ''

      const postData = {
        title: title.trim(),
        content,
        coverImage,
        mediaUrls: JSON.stringify(uploadedUrls),
        mediaType,
        tagIds: [] as number[],
      }

      if (isEditing && editPostId) {
        await updatePost(editPostId, postData)
        message.success('更新成功！')
        history.push(`/post/${editPostId}`)
      } else {
        const res = await createPost(postData)
        message.success('发布成功！')
        const postId = res.data?.data?.id
        if (postId) {
          history.push(`/post/${postId}`)
        } else {
          history.push('/')
        }
      }
    } catch {
      message.error(isEditing ? '更新失败，请稍后重试' : '发布失败，请稍后重试')
    } finally {
      setPublishing(false)
    }
  }

  const handleSaveDraft = async () => {
    Modal.confirm({
      title: '保存草稿',
      content: '确认将当前内容保存为草稿吗？',
      okText: '确认保存',
      cancelText: '取消',
      onOk: async () => {
        setSavingDraft(true)
        try {
          const uploadedUrls: string[] = []
          for (const item of mediaList) {
            if (item.file) {
              try {
                const res = await uploadFile(item.file)
                const url = res.data?.data?.url
                if (url) uploadedUrls.push(url)
              } catch {
                // Skip failed uploads
              }
            } else {
              uploadedUrls.push(item.url)
            }
          }

          const mediaType = uploadedUrls.length === 0
            ? 'IMAGE'
            : mediaList.some((m) => m.type === 'video') && mediaList.some((m) => m.type === 'image')
              ? 'MIXED'
              : mediaList.some((m) => m.type === 'video')
                ? 'VIDEO'
                : 'IMAGE'

          const coverImage = uploadedUrls.length > 0 ? uploadedUrls[0] : ''

          const res = await saveDraft({
            id: draftId ?? (isEditing ? editPostId : undefined),
            title: title.trim() || '未命名草稿',
            content,
            coverImage,
            mediaUrls: uploadedUrls,
            mediaType,
            tagIds: [],
          })
          const newDraftId = res.data?.data?.id
          if (newDraftId) setDraftId(newDraftId)
          message.success('草稿已保存')
        } catch {
          message.error('保存草稿失败')
        } finally {
          setSavingDraft(false)
        }
      },
    })
  }

  if (loadingPost) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--color-bg-base)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg-base)', padding: '24px' }}>
      <div style={{ maxWidth: 800, margin: '0 auto' }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}>
          <h1 style={{ fontSize: 22, fontWeight: 800, color: 'var(--color-text-secondary)', margin: 0 }}>
            {isEditing ? '编辑内容' : '发布内容'}
          </h1>
          <div style={{ display: 'flex', gap: 12 }}>
            <button
              type="button"
              onClick={() => {
                Modal.confirm({
                  title: '确认取消',
                  content: '取消后未保存的内容将丢失，确认取消吗？',
                  okText: '确认取消',
                  cancelText: '继续编辑',
                  onOk: () => { leavingRef.current = true; history.back() },
                })
              }}
              style={{
                padding: '8px 20px',
                border: '1px solid var(--color-border)',
                borderRadius: '8px',
                background: 'transparent',
                color: 'var(--color-text-secondary)',
                fontSize: 14,
                cursor: 'pointer',
              }}
            >
              取消
            </button>
            <button
              type="button"
              onClick={handleSaveDraft}
              disabled={savingDraft}
              style={{
                padding: '8px 20px',
                border: '1px solid rgba(var(--color-primary-rgb), 0.3)',
                borderRadius: '8px',
                background: 'rgba(var(--color-primary-rgb), 0.08)',
                color: 'var(--color-primary)',
                fontSize: 14,
                cursor: savingDraft ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}
            >
              <SaveOutlined />
              {savingDraft ? '保存中...' : '保存草稿'}
            </button>
            <button
              type="button"
              onClick={() => {
                Modal.confirm({
                  title: '确认发布',
                  content: '确认发布当前内容吗？',
                  okText: '确认发布',
                  cancelText: '取消',
                  onOk: handlePublish,
                })
              }}
              disabled={publishing || !title.trim()}
              style={{
                padding: '8px 24px',
                border: 'none',
                borderRadius: '8px',
                background: 'var(--color-gradient-primary)',
                color: 'var(--color-bg-base)',
                fontSize: 14,
                fontWeight: 600,
                cursor: publishing || !title.trim() ? 'not-allowed' : 'pointer',
                boxShadow: '0 2px 12px rgba(var(--color-primary-rgb), 0.3)',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}
            >
              <SendOutlined />
              {publishing
                ? (isEditing ? '更新中...' : '发布中...')
                : (isEditing ? '更新' : '发布')}
            </button>
          </div>
        </div>

        <div style={{
          background: 'var(--color-bg-container)',
          borderRadius: '14px',
          border: '1px solid var(--color-border)',
          padding: 24,
        }}>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="添加标题，让大家更容易找到你的内容"
            style={{
              width: '100%',
              padding: '12px 0',
              border: 'none',
              borderBottom: '1px solid var(--color-border)',
              background: 'transparent',
              color: 'var(--color-text-secondary)',
              fontSize: 20,
              fontWeight: 700,
              outline: 'none',
              marginBottom: 20,
            }}
          />

          <TiptapEditor
            value={content}
            onChange={setContent}
            placeholder="分享你的想法、经验、发现..."
          />

          <div style={{ marginTop: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
              <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--color-text-secondary)' }}>
                媒体文件
              </span>
              <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                （支持图片和视频混合上传）
              </span>
            </div>

            <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
              <input
                ref={imageInputRef}
                type="file"
                accept="image/*"
                multiple
                style={{ display: 'none' }}
                onChange={handleImageSelect}
              />
              <input
                ref={videoInputRef}
                type="file"
                accept="video/*"
                style={{ display: 'none' }}
                onChange={handleVideoSelect}
              />
              <button
                type="button"
                onClick={() => imageInputRef.current?.click()}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '8px 16px',
                  border: '1px dashed rgba(var(--color-primary-rgb), 0.3)',
                  borderRadius: '8px',
                  background: 'rgba(var(--color-primary-rgb), 0.05)',
                  color: 'var(--color-primary)',
                  fontSize: 13,
                  cursor: 'pointer',
                  transition: 'all 0.2s',
                }}
              >
                <PictureOutlined /> 添加图片
              </button>
              <button
                type="button"
                onClick={() => videoInputRef.current?.click()}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '8px 16px',
                  border: '1px dashed rgba(160, 120, 255, 0.3)',
                  borderRadius: '8px',
                  background: 'rgba(160, 120, 255, 0.05)',
                  color: '#A078FF',
                  fontSize: 13,
                  cursor: 'pointer',
                  transition: 'all 0.2s',
                }}
              >
                <VideoCameraOutlined /> 添加视频
              </button>
            </div>

            {mediaList.length > 0 && (
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))',
                gap: 10,
              }}>
                {mediaList.map((item) => (
                  <div
                    key={item.uid}
                    style={{
                      position: 'relative',
                      borderRadius: '8px',
                      overflow: 'hidden',
                      aspectRatio: '1',
                      background: 'var(--color-bg-input)',
                    }}
                  >
                    {item.type === 'image' ? (
                      <img
                        src={item.url}
                        alt=""
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                    ) : (
                      <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: 6,
                      }}>
                        <VideoCameraOutlined style={{ fontSize: 24, color: '#A078FF' }} />
                        <span style={{ fontSize: 11, color: 'var(--color-text-secondary)' }}>视频</span>
                      </div>
                    )}
                    <button
                      type="button"
                      onClick={() => removeMedia(item.uid)}
                      style={{
                        position: 'absolute',
                        top: 4,
                        right: 4,
                        width: 22,
                        height: 22,
                        borderRadius: '50%',
                        border: 'none',
                        background: 'rgba(0,0,0,0.6)',
                        color: 'var(--color-text-secondary)',
                        fontSize: 12,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <DeleteOutlined />
                    </button>
                  </div>
                ))}
                <button
                  type="button"
                  onClick={() => imageInputRef.current?.click()}
                  style={{
                    aspectRatio: '1',
                    borderRadius: '8px',
                    border: '1px dashed var(--color-border)',
                    background: 'transparent',
                    color: 'var(--color-text-tertiary)',
                    fontSize: 24,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    transition: 'all 0.2s',
                  }}
                >
                  <PlusOutlined />
                </button>
              </div>
            )}
          </div>

          <div style={{
            marginTop: 20,
            paddingTop: 20,
            borderTop: '1px solid var(--color-border)',
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
          }}>
            <div>
              <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
                <TagOutlined /> 话题标签
              </label>
              <input
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                placeholder="多个标签用空格分隔，例如：穿搭 美食 旅行"
                style={{
                  width: '100%',
                  padding: '10px 16px',
                  border: '1px solid var(--color-border)',
                  borderRadius: '10px',
                  background: 'var(--color-bg-input)',
                  color: 'var(--color-text-secondary)',
                  fontSize: 14,
                  outline: 'none',
                }}
                onFocus={(e) => { e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.4)' }}
                onBlur={(e) => { e.currentTarget.style.borderColor = 'var(--color-border)' }}
              />
            </div>

            <div>
              <label
                style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', color: 'var(--color-text-secondary)', fontSize: 13 }}
                onClick={() => setLinkProduct(!linkProduct)}
              >
                <input
                  type="checkbox"
                  checked={linkProduct}
                  onChange={(e) => setLinkProduct(e.target.checked)}
                  style={{ accentColor: 'var(--color-primary)' }}
                />
                <ShoppingOutlined /> 关联好物推荐
              </label>
              {linkProduct && (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 12 }}>
                  <input
                    value={productName}
                    onChange={(e) => setProductName(e.target.value)}
                    placeholder="商品名称"
                    style={{
                      padding: '10px 16px',
                      border: '1px solid var(--color-border)',
                      borderRadius: '10px',
                      background: 'var(--color-bg-input)',
                      color: 'var(--color-text-secondary)',
                      fontSize: 14,
                      outline: 'none',
                    }}
                    onFocus={(e) => { e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.4)' }}
                    onBlur={(e) => { e.currentTarget.style.borderColor = 'var(--color-border)' }}
                  />
                  <input
                    value={productPrice}
                    onChange={(e) => setProductPrice(e.target.value)}
                    placeholder="价格"
                    type="number"
                    style={{
                      padding: '10px 16px',
                      border: '1px solid var(--color-border)',
                      borderRadius: '10px',
                      background: 'var(--color-bg-input)',
                      color: 'var(--color-text-secondary)',
                      fontSize: 14,
                      outline: 'none',
                    }}
                    onFocus={(e) => { e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.4)' }}
                    onBlur={(e) => { e.currentTarget.style.borderColor = 'var(--color-border)' }}
                  />
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
