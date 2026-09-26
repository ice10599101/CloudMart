import { useState, useEffect, useRef } from 'react'
import { View,
  Switch, Text, Input, Image, ScrollView } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { communityApi } from '@/api/community'
import { productApi } from '@/api/product'
import type { Product } from '@/types'
import { fileApi } from '@/api/file'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import CustomTabBar from '@/components/CustomTabBar'
import styles from './index.module.scss'

// 编辑器组件：H5 用 TiptapEditor，小程序用 MiniProgramEditor
// 使用 Taro 条件编译在构建时静态选择，避免 Tiptap/ProseMirror 被打包进小程序
// #ifdef H5
import TiptapEditor from '@/components/TiptapEditor'
// #endif
// #ifdef WEAPP
import MiniProgramEditor from '@/components/MiniProgramEditor'
// #endif

const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP

let EditorComponent: React.FC<{ value?: string; onChange?: (v: string) => void; placeholder?: string }> | null = null
// #ifdef H5
EditorComponent = TiptapEditor
// #endif
// #ifdef WEAPP
EditorComponent = MiniProgramEditor
// #endif

interface MediaItem {
  uid: string
  type: 'image' | 'video'
  url: string
  file?: string
  uploaded?: boolean
}

export default function PublishPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [tagInput, setTagInput] = useState('')
  // 关联好物（真实 productId，契约对齐后端 CreatePostRequest.productId）
  const [linkProduct, setLinkProduct] = useState(false)
  const [linkedProductId, setLinkedProductId] = useState<number | null>(null)
  const [linkedProduct, setLinkedProduct] = useState<Product | null>(null)
  const [productKeyword, setProductKeyword] = useState('')
  const [productOptions, setProductOptions] = useState<Product[]>([])
  const [searchingProducts, setSearchingProducts] = useState(false)
  const [productSearchOpen, setProductSearchOpen] = useState(false)
  const [mediaList, setMediaList] = useState<MediaItem[]>([])
  const [publishing, setPublishing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [isEditing, setIsEditing] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [draftId, setDraftId] = useState<number | null>(null)
  const leavingRef = useRef(false)
  useAuthGuard()

  const hasContent = title.trim() || content.trim() || mediaList.length > 0

  // Intercept back navigation when there's unsaved content
  useEffect(() => {
    if (!hasContent || leavingRef.current) return

    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault()
    }
    window.addEventListener('beforeunload', handleBeforeUnload)

    const interceptBack = () => {
      Taro.showModal({
        title: '确认离开',
        content: '当前有未保存的内容，离开后将丢失，确认离开吗？',
      }).then((res) => {
        if (res.confirm) {
          leavingRef.current = true
          Taro.navigateBack()
        }
      })
    }

    // For H5, intercept browser back button via popstate
    if (!IS_WEAPP) {
      window.history.pushState(null, '', window.location.href)
      const popstateHandler = () => {
        window.history.pushState(null, '', window.location.href)
        interceptBack()
      }
      window.addEventListener('popstate', popstateHandler)
      return () => {
        window.removeEventListener('beforeunload', handleBeforeUnload)
        window.removeEventListener('popstate', popstateHandler)
      }
    }
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload)
    }
  }, [hasContent])

  useDidShow(() => {
    const params = Taro.getCurrentInstance().router?.params
    const editId = params?.edit ? parseInt(params.edit) : null
    if (editId && !isEditing) {
      loadPostForEdit(editId)
    }
  })

  const loadPostForEdit = async (id: number) => {
    try {
      const res = await communityApi.getPost(id)
      const post = res.data?.data
      if (!post) return
      setIsEditing(true)
      setEditingId(id)
      setTitle(post.title || '')
      setContent(post.content || '')
      setTagInput(post.tags?.map((t: any) => t.name || t).join(' ') || '')
      if (post.coverImage) {
        setMediaList([{ uid: 'existing-0', type: 'image', url: post.coverImage, uploaded: true }])
      }
      if (post.mediaUrls?.length) {
        const existingMedia = post.mediaUrls.map((url: string, idx: number) => ({
          uid: `existing-${idx + 1}`,
          type: 'image' as const,
          url,
          uploaded: true,
        }))
        setMediaList((prev) => [...prev, ...existingMedia])
      }
    } catch {
      Taro.showToast({ title: '加载失败', icon: 'none' })
    }
  }

  const uploadMediaFiles = async (): Promise<{ mediaUrls: string[]; coverImage: string; mediaType: string }> => {
    const uploadedUrls: string[] = []
    for (const item of mediaList) {
      if (item.uploaded && item.url) {
        uploadedUrls.push(item.url)
      } else if (item.file) {
        try {
          const uploadRes = await fileApi.upload(item.file)
          const url = uploadRes.data?.data?.url
          if (url) {
            uploadedUrls.push(url)
          }
        } catch {
          // Skip failed uploads
        }
      }
    }
    const coverImage = uploadedUrls[0] || ''
    const hasVideo = mediaList.some((m) => m.type === 'video')
    const hasImage = mediaList.some((m) => m.type === 'image')
    const mediaType = hasVideo && hasImage ? 'MIXED' : hasVideo ? 'VIDEO' : 'IMAGE'
    return { mediaUrls: uploadedUrls, coverImage, mediaType }
  }

  const handlePublish = async () => {
    if (!title.trim()) {
      Taro.showToast({ title: '请输入标题', icon: 'none' })
      return
    }
    if (!content.trim() && mediaList.length === 0) {
      Taro.showToast({ title: '请输入内容或添加图片', icon: 'none' })
      return
    }

    const res = await Taro.showModal({
      title: '确认发布',
      content: '确认发布当前内容吗？',
    })
    if (!res.confirm) return

    setPublishing(true)
    try {
      const tagNames = tagInput.trim() ? tagInput.split(/[\s,，]+/).map((t) => t.trim()).filter(Boolean) : []
      const { mediaUrls, coverImage, mediaType } = await uploadMediaFiles()
      // 标签名解析为 tagIds（后端 CreatePostRequest 只接收 tagIds；解析失败不阻断发布）
      let tagIds: number[] = []
      if (tagNames.length > 0) {
        try {
          const resolved = await communityApi.resolveTags(tagNames)
          tagIds = (resolved.data?.data ?? []).map((t) => t.id)
        } catch {
          Taro.showToast({ title: '标签解析失败，本次不携带标签', icon: 'none' })
        }
      }

      const postData: Record<string, unknown> = {
        title: title.trim(),
        content,
        tagIds,
        coverImage,
        mediaUrls,
        mediaType,
        status: 1,
        productId: linkProduct && linkedProductId ? linkedProductId : undefined,
      }

      if (isEditing && editingId) {
        await communityApi.updatePost(editingId, postData)
        Taro.showToast({ title: '更新成功', icon: 'success' })
      } else {
        await communityApi.createPost(postData as any)
        Taro.showToast({ title: '发布成功', icon: 'success' })
      }
      setTimeout(() => Taro.switchTab({ url: '/pages/home/index' }), 1500)
    } catch {
      Taro.showToast({ title: '发布失败', icon: 'none' })
    } finally {
      setPublishing(false)
    }
  }

  const handleSaveDraft = async () => {
    const res = await Taro.showModal({
      title: '保存草稿',
      content: '确定要保存为草稿吗？',
    })
    if (!res.confirm) return

    setSaving(true)
    try {
      const tags = tagInput.trim() ? tagInput.split(/[\s,，]+/).map((t) => t.trim()).filter(Boolean) : []
      const { mediaUrls, coverImage, mediaType } = await uploadMediaFiles()
      const postData: Record<string, unknown> = {
        title: title.trim() || '未命名草稿',
        content,
        tags,
        coverImage,
        mediaUrls,
        mediaType,
        status: 0,
      }

      const targetId = editingId || draftId
      if (targetId) {
        await communityApi.updatePost(targetId, postData)
      } else {
        const createRes = await communityApi.createPost(postData as any)
        const newId = createRes.data?.data?.id
        if (newId) setDraftId(newId)
      }
      Taro.showToast({ title: '已保存草稿', icon: 'success' })
    } catch {
      Taro.showToast({ title: '保存失败', icon: 'none' })
    } finally {
      setSaving(false)
    }
  }

  const handleCancel = async () => {
    if (hasContent) {
      const res = await Taro.showModal({
        title: '确认取消',
        content: '取消后未保存的内容将丢失，确认取消吗？',
      })
      if (!res.confirm) return
    }
    leavingRef.current = true
    Taro.navigateBack()
  }

  const handleAddImage = async () => {
    try {
      const res = await Taro.chooseImage({
        count: 9 - mediaList.length,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
      })
      const newItems: MediaItem[] = res.tempFilePaths.map((path) => ({
        uid: `new-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        type: 'image',
        url: path,
        file: path,
        uploaded: false,
      }))
      setMediaList((prev) => [...prev, ...newItems])
    } catch {
      // User cancelled
    }
  }

  /** 添加视频（对齐 Web 端发布：图片+视频混合上传，单视频 ≤50MB） */
  const handleAddVideo = async () => {
    try {
      const res = await Taro.chooseVideo({
        sourceType: ['album', 'camera'],
        maxDuration: 60,
      })
      const filePath = res.tempFilePath
      if (!filePath) return
      const size = res.size ?? 0
      if (size > 50 * 1024 * 1024) {
        Taro.showToast({ title: '视频不能超过 50MB', icon: 'none' })
        return
      }
      setMediaList((prev) => [
        ...prev,
        {
          uid: `new-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          type: 'video',
          url: filePath,
          file: filePath,
          uploaded: false,
        },
      ])
    } catch {
      // User cancelled
    }
  }

  /** 搜索可关联的好物 */
  const handleSearchProducts = async () => {
    if (!productKeyword.trim() || searchingProducts) return
    setSearchingProducts(true)
    try {
      const res = await productApi.search({ keyword: productKeyword.trim(), page: 1, size: 8 })
      setProductOptions((res.data?.data as unknown as { products?: Product[] })?.products || [])
    } catch {
      Taro.showToast({ title: '商品搜索失败', icon: 'none' })
    } finally {
      setSearchingProducts(false)
    }
  }

  const handleRemoveMedia = (uid: string) => {
    setMediaList((prev) => prev.filter((item) => item.uid !== uid))
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={{ ...themeStyle, paddingTop: `${statusBarHeight + navBarHeight}px` }}>
      <CustomNavBar title="CloudMart" />
      <View className={styles.header}>
        <Text className={styles.cancelBtn} onClick={handleCancel}>取消</Text>
        <Text className={styles.pageTitle}>{isEditing ? '编辑内容' : '发布帖子'}</Text>
        <View className={styles.headerActions}>
          <Text className={styles.draftBtn} onClick={saving ? undefined : handleSaveDraft}>
            {saving ? '保存中...' : '草稿'}
          </Text>
          <View className={styles.publishBtn} onClick={publishing ? undefined : handlePublish}>
            <Text className={styles.publishBtnText}>{publishing ? '发布中...' : isEditing ? '更新' : '发布'}</Text>
          </View>
        </View>
      </View>

      <ScrollView scrollY className={styles.scrollContent}>
        <View className={styles.titleWrap}>
          <Input
            className={styles.titleInput}
            placeholder='填写标题会有更多赞哦~'
            placeholderStyle="color: var(--color-text-tertiary)"
            value={title}
            onInput={(e) => setTitle(e.detail.value)}
            maxlength={50}
          />
        </View>

        <View className={styles.editorWrap}>
          {EditorComponent ? (
            <EditorComponent
              value={content}
              onChange={setContent}
              placeholder='分享你的想法...'
            />
          ) : (
            <Input
              className={styles.fallbackInput}
              placeholder='分享你的想法...'
              placeholderStyle="color: var(--color-text-tertiary)"
              value={content}
              onInput={(e) => setContent(e.detail.value)}
            />
          )}
        </View>

        {/* Media upload area */}
        <View className={styles.mediaSection}>
          <Text className={styles.sectionLabel}>图片/视频</Text>
          <View className={styles.mediaGrid}>
            {mediaList.map((item) => (
              <View key={item.uid} className={styles.mediaItem}>
                <Image className={styles.mediaThumb} src={item.url} mode='aspectFill' />
                <View className={styles.mediaRemove} onClick={() => handleRemoveMedia(item.uid)}>
                  <Text className={styles.mediaRemoveText}>×</Text>
                </View>
              </View>
            ))}
            {mediaList.length < 9 && (
              <View className={styles.mediaAdd} onClick={handleAddVideo}>
                <Text className={styles.mediaAddIcon}>🎬</Text>
                <Text className={styles.mediaAddText}>添加视频</Text>
              </View>
            )}
            {mediaList.length < 9 && (
              <View className={styles.mediaAdd} onClick={handleAddImage}>
                <Text className={styles.mediaAddIcon}>+</Text>
                <Text className={styles.mediaAddText}>添加图片</Text>
              </View>
            )}
          </View>
        </View>

        {/* 关联好物（真实 productId，对齐 Web 端发布） */}
        <View className={styles.tagWrap}>
          <View className={styles.linkProductRow}>
            <Text className={styles.sectionLabel}>关联好物推荐</Text>
            <Switch checked={linkProduct} onChange={(e) => setLinkProduct(e.detail.value)} color='#4a90d9' />
          </View>
          {linkProduct && (
            linkedProductId && linkedProduct ? (
              <View className={styles.linkedProduct}>
                {linkedProduct.mainImage && <Image className={styles.linkedProductImage} src={linkedProduct.mainImage} mode='aspectFill' />}
                <View className={styles.linkedProductInfo}>
                  <Text className={styles.linkedProductName} numberOfLines={1}>{linkedProduct.name}</Text>
                  <Text className={styles.linkedProductPrice}>¥{linkedProduct.price}</Text>
                </View>
                <Text
                  className={styles.linkedProductReset}
                  onClick={() => { setLinkedProductId(null); setLinkedProduct(null) }}
                >
                  重新选择
                </Text>
              </View>
            ) : (
              <View className={styles.linkProductSearch} onClick={() => setProductSearchOpen(true)}>
                <Text className={styles.linkProductSearchText}>🔍 搜索并选择要关联的好物</Text>
              </View>
            )
          )}
        </View>

        {/* 好物搜索弹层 */}
        {productSearchOpen && (
          <View className={styles.productModalMask} onClick={() => setProductSearchOpen(false)}>
            <View className={styles.productModal} onClick={(e) => e.stopPropagation()}>
              <View className={styles.productSearchRow}>
                <Input
                  className={styles.productSearchInput}
                  placeholder='搜索商品名称'
                  value={productKeyword}
                  onInput={(e) => setProductKeyword(e.detail.value)}
                  onConfirm={() => handleSearchProducts()}
                />
                <Text className={styles.productSearchBtn} onClick={() => handleSearchProducts()}>搜索</Text>
              </View>
              <ScrollView scrollY className={styles.productResults}>
                {productOptions.map((product) => (
                  <View
                    key={product.id}
                    className={styles.productResultRow}
                    onClick={() => {
                      setLinkedProductId(product.id)
                      setLinkedProduct(product)
                      setProductSearchOpen(false)
                    }}
                  >
                    {product.mainImage && <Image className={styles.productResultImage} src={product.mainImage} mode='aspectFill' />}
                    <View className={styles.productResultInfo}>
                      <Text className={styles.productResultName} numberOfLines={1}>{product.name}</Text>
                      <Text className={styles.productResultPrice}>¥{product.price}</Text>
                    </View>
                  </View>
                ))}
                {productOptions.length === 0 && (
                  <Text className={styles.productResultEmpty}>{searchingProducts ? '搜索中...' : '输入关键词搜索商品'}</Text>
                )}
              </ScrollView>
            </View>
          </View>
        )}

        <View className={styles.tagWrap}>
          <Text className={styles.sectionLabel}>话题标签</Text>
          <Input
            className={styles.tagInput}
            placeholder='多个标签用空格分隔，例如：穿搭 美食 旅行'
            placeholderStyle="color: var(--color-text-tertiary)"
            value={tagInput}
            onInput={(e) => setTagInput(e.detail.value)}
          />
        </View>

        {/* Quick tags */}
        <View className={styles.quickTags}>
          {['日常分享', '好物推荐', '美食探店', '穿搭灵感', '旅行攻略', '数码科技'].map((tag) => (
            <View
              key={tag}
              className={styles.quickTag}
              onClick={() => setTagInput(tagInput ? `${tagInput} ${tag}` : tag)}
            >
              <Text className={styles.quickTagText}># {tag}</Text>
            </View>
          ))}
        </View>
      </ScrollView>
      <CustomTabBar />
    </View>
  )
}
