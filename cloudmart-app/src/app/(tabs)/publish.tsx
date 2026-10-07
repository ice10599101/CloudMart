import { View, Text, TextInput, TouchableOpacity, ScrollView, Alert, ActivityIndicator, Image, BackHandler, Switch, Modal } from 'react-native'
import { useState, useRef, useEffect } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import * as ImagePicker from 'expo-image-picker'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { communityApi } from '@/api/community'
import { productApi } from '@/api/product'
import { fileApi } from '@/api/file'
import type { Product } from '@/types'
import { RichTextEditor, RichTextEditorRef } from '@/components/RichTextEditor'
import { appendAttachmentNodes, generateAttachmentId, type SurveyAttachmentQuestion } from '@/utils/postAttachments'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

interface MediaItem {
  uid: string
  type: 'image' | 'video'
  url: string
  localUri?: string
  uploaded: boolean
}

/** P0-6 附件草稿配置（发布成功后按 UUID 幂等物化） */
interface PollDraft {
  id: string
  question: string
  options: string[]
  multiple: boolean
}

interface SurveyDraft {
  id: string
  title: string
  questions: SurveyAttachmentQuestion[]
}

const EMPTY_POLL_DRAFT = (): PollDraft => ({ id: generateAttachmentId(), question: '', options: ['', ''], multiple: false })
const EMPTY_SURVEY_DRAFT = (): SurveyDraft => ({
  id: generateAttachmentId(),
  title: '',
  questions: [{ text: '', type: 'single', options: ['', ''], required: true }],
})

export default function PublishPage() {
  const theme = useTheme()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const editorRef = useRef<RichTextEditorRef>(null)
  const params = useLocalSearchParams<{ edit?: string }>()
  const leavingRef = useRef(false)

  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [tags, setTags] = useState('')
  const [mediaList, setMediaList] = useState<MediaItem[]>([])
  // 关联好物（真实 productId，契约对齐后端 CreatePostRequest.productId）
  const [linkProduct, setLinkProduct] = useState(false)
  const [linkedProductId, setLinkedProductId] = useState<number | null>(null)
  const [linkedProduct, setLinkedProduct] = useState<Product | null>(null)
  const [productKeyword, setProductKeyword] = useState('')
  const [productOptions, setProductOptions] = useState<Product[]>([])
  const [searchingProducts, setSearchingProducts] = useState(false)
  const [productSearchOpen, setProductSearchOpen] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [isEditing, setIsEditing] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [draftId, setDraftId] = useState<number | null>(null)
  // P0-6：投票/问卷附件
  const [pollDrafts, setPollDrafts] = useState<PollDraft[]>([])
  const [surveyDrafts, setSurveyDrafts] = useState<SurveyDraft[]>([])
  const [pollModalOpen, setPollModalOpen] = useState(false)
  const [pollDraft, setPollDraft] = useState<PollDraft>(EMPTY_POLL_DRAFT)
  const [surveyModalOpen, setSurveyModalOpen] = useState(false)
  const [surveyDraft, setSurveyDraft] = useState<SurveyDraft>(EMPTY_SURVEY_DRAFT)

  const hasContent = title.trim() || content.trim() || mediaList.length > 0 || pollDrafts.length > 0 || surveyDrafts.length > 0

  // Intercept hardware back button
  useEffect(() => {
    const backHandler = BackHandler.addEventListener('hardwareBackPress', () => {
      if (!hasContent || leavingRef.current) return false
      Alert.alert('确认离开', '当前有未保存的内容，离开后将丢失，确认离开吗？', [
        { text: '继续编辑', style: 'cancel' },
        { text: '确认离开', style: 'destructive', onPress: () => { leavingRef.current = true; router.back() } },
      ])
      return true
    })
    return () => backHandler.remove()
  }, [hasContent])

  const loadPostForEdit = async (id: number) => {
    try {
      const res = await communityApi.getPost(id)
      const post = res.data?.data
      if (!post) return
      setIsEditing(true)
      setEditingId(id)
      setTitle(post.title || '')
      setContent(post.content || '')
      setTags(post.tags?.map((t: any) => t.name || t).join(' ') || '')
      // 编辑态回显关联好物
      if (post.productId) {
        setLinkProduct(true)
        setLinkedProductId(post.productId)
        productApi.getDetail(post.productId)
          .then((detailRes) => setLinkedProduct(detailRes.data?.data ?? null))
          .catch(() => {})
      }
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
      editorRef.current?.setHTML(post.content || '')
    } catch {
      Alert.alert('错误', '加载帖子失败')
    }
  }

  useEffect(() => {
    if (!isLoggedIn) {
      router.push('/login')
      return
    }
    if (params.edit && !isEditing) {
      loadPostForEdit(parseInt(params.edit))
    }
  }, [params.edit, isLoggedIn])

  const uploadMediaFiles = async (): Promise<{ mediaUrls: string[]; coverImage: string; mediaType: string }> => {
    const uploadedUrls: string[] = []
    for (const item of mediaList) {
      if (item.uploaded && item.url) {
        uploadedUrls.push(item.url)
      } else if (item.localUri) {
        try {
          const formData = new FormData()
          formData.append('file', {
            uri: item.localUri,
            type: 'image/jpeg',
            name: 'upload.jpg',
          } as any)
          const res = await fileApi.upload(formData)
          const url = (res.data as any)?.data?.url || (res.data as any)?.url
          if (url) uploadedUrls.push(url)
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
    if (!isLoggedIn) {
      router.push('/login')
      return
    }
    if (!title.trim()) {
      Alert.alert('提示', '请输入标题')
      return
    }
    if (!content.trim() && mediaList.length === 0) {
      Alert.alert('提示', '请输入内容或添加图片')
      return
    }

    Alert.alert('确认发布', '确认发布当前内容吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '发布',
        onPress: async () => {
          setPublishing(true)
          try {
            const tagNames = tags.trim() ? tags.split(/[,，\s]+/).filter(Boolean) : []
                        const { mediaUrls, coverImage, mediaType } = await uploadMediaFiles()
            // 标签名解析为 tagIds（后端只接收 tagIds；失败不阻断发布）
            let tagIds: number[] = []
            if (tagNames.length > 0) {
              try {
                const resolved = await communityApi.resolveTags(tagNames)
                tagIds = (resolved.data?.data ?? []).map((t) => t.id)
              } catch {
                Alert.alert('提示', '标签解析失败，本次不携带标签')
              }
            }

            const postData: Record<string, unknown> = {
              title: title.trim(),
              content: buildContentWithAttachments(),
              tagIds,
              coverImage,
              mediaUrls,
              mediaType,
              status: 1,
              productId: linkProduct && linkedProductId ? linkedProductId : undefined,
            }

            if (isEditing && editingId) {
              await communityApi.updatePost(editingId, postData)
              await materializeAttachments(editingId)
              Alert.alert('更新成功', '', [{ text: '确定', onPress: () => router.back() }])
            } else {
              const createRes = await communityApi.createPost(postData as any)
              const newPostId = (createRes.data as any)?.data?.id
              if (newPostId) await materializeAttachments(newPostId)
              Alert.alert('发布成功', '', [{
                text: '确定',
                onPress: () => {
                  setTitle('')
                  setContent('')
                  setTags('')
                  setMediaList([])
                  setPollDrafts([])
                  setSurveyDrafts([])
                  editorRef.current?.setHTML('')
                },
              }])
            }
          } catch (err: any) {
            Alert.alert('发布失败', err?.message || '请稍后重试')
          } finally {
            setPublishing(false)
          }
        },
      },
    ])
  }

  const handleSaveDraft = () => {
    Alert.alert('保存草稿', '确定要保存为草稿吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '保存',
        onPress: async () => {
          setSaving(true)
          try {
            // 标签名解析为 tagIds（草稿同样携带；失败静默）
            let tagIds: number[] = []
            const tagNames = tags.trim() ? tags.split(/[,，\s]+/).filter(Boolean) : []
            try {
              if (tagNames.length > 0) {
                const resolved = await communityApi.resolveTags(tagNames)
                tagIds = (resolved.data?.data ?? []).map((t) => t.id)
              }
            } catch {
              // 静默
            }
            const { mediaUrls, coverImage, mediaType } = await uploadMediaFiles()
            const postData: Record<string, unknown> = {
              title: title.trim() || '未命名草稿',
              content: buildContentWithAttachments(),
              tagIds,
              coverImage,
              mediaUrls,
              mediaType,
              status: 0,
              productId: linkProduct && linkedProductId ? linkedProductId : undefined,
            }

            const targetId = editingId || draftId
            if (targetId) {
              await communityApi.updatePost(targetId, postData)
              await materializeAttachments(targetId)
            } else {
              const createRes = await communityApi.createPost(postData as any)
              const newId = (createRes.data as any)?.data?.id
              if (newId) {
                setDraftId(newId)
                await materializeAttachments(newId)
              }
            }
            Alert.alert('提示', '已保存草稿')
          } catch {
            Alert.alert('错误', '保存失败')
          } finally {
            setSaving(false)
          }
        },
      },
    ])
  }

  const handleCancel = () => {
    if (hasContent) {
      Alert.alert('确认取消', '取消后未保存的内容将丢失，确认取消吗？', [
        { text: '继续编辑', style: 'cancel' },
        { text: '确认取消', style: 'destructive', onPress: () => { leavingRef.current = true; router.back() } },
      ])
    } else {
      leavingRef.current = true
      router.back()
    }
  }

  // ==================== P0-6：投票/问卷附件 ====================

  const confirmPollDraft = () => {
    const options = pollDraft.options.map((o) => o.trim()).filter(Boolean)
    if (!pollDraft.question.trim() || options.length < 2) {
      Alert.alert('提示', '请填写问题和至少 2 个选项')
      return
    }
    const finalized: PollDraft = { ...pollDraft, question: pollDraft.question.trim(), options }
    setPollDrafts((prev) => [...prev.filter((p) => p.id !== finalized.id), finalized])
    setPollModalOpen(false)
    setPollDraft(EMPTY_POLL_DRAFT())
  }

  const confirmSurveyDraft = () => {
    const title = surveyDraft.title.trim()
    const questions = surveyDraft.questions
      .map((q) => ({
        ...q,
        text: q.text.trim(),
        options: q.type === 'text' ? [] : q.options.map((o) => o.trim()).filter(Boolean),
      }))
      .filter((q) => q.text && (q.type === 'text' || q.options.length >= 2))
    if (!title || questions.length === 0) {
      Alert.alert('提示', '请填写问卷标题和完整题目')
      return
    }
    const finalized: SurveyDraft = { ...surveyDraft, title, questions }
    setSurveyDrafts((prev) => [...prev.filter((s) => s.id !== finalized.id), finalized])
    setSurveyModalOpen(false)
    setSurveyDraft(EMPTY_SURVEY_DRAFT())
  }

  /** 正文追加附件节点（data-poll / data-survey，契约对齐 Web 端编辑器序列化） */
  const buildContentWithAttachments = (): string =>
    appendAttachmentNodes(
      content,
      pollDrafts.map((p) => ({ id: p.id, config: { question: p.question, options: p.options, multiple: p.multiple } })),
      surveyDrafts.map((s) => ({ id: s.id, config: { title: s.title, questions: s.questions } })),
    )

  /** 发布成功后物化附件（幂等：UUID 为主键；失败不回滚宿主内容，重新编辑保存可重试） */
  const materializeAttachments = async (postId: number | string) => {
    const tasks: Promise<unknown>[] = [
      ...pollDrafts.map((p) =>
        communityApi.createPoll({
          id: p.id, targetType: 'POST', targetId: String(postId),
          question: p.question, multiple: p.multiple, options: p.options,
        }),
      ),
      ...surveyDrafts.map((s) =>
        communityApi.createSurvey({
          id: s.id, targetType: 'POST', targetId: String(postId),
          title: s.title, questions: s.questions,
        }),
      ),
    ]
    const results = await Promise.allSettled(tasks)
    if (results.some((r) => r.status === 'rejected')) {
      Alert.alert('提示', '部分投票/问卷创建失败，重新编辑保存可重试')
    }
  }

  const handleAddImage = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.8,
      allowsMultipleSelection: true,
      selectionLimit: 9 - mediaList.length,
    })

    if (result.canceled || !result.assets?.length) return

    const newItems: MediaItem[] = result.assets.map((asset) => ({
      uid: `new-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      type: 'image' as const,
      url: asset.uri,
      localUri: asset.uri,
      uploaded: false,
    }))
    setMediaList((prev) => [...prev, ...newItems])
  }

  /** 添加视频（对齐 Web 端发布：图片+视频混合上传，单视频 ≤50MB） */
  const handleAddVideo = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Videos,
      allowsMultipleSelection: false,
    })
    if (result.canceled || !result.assets?.[0]) return
    const asset = result.assets[0]
    if ((asset.fileSize ?? 0) > 50 * 1024 * 1024) {
      Alert.alert('提示', '视频不能超过 50MB')
      return
    }
    setMediaList((prev) => [
      ...prev,
      {
        uid: `new-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        type: 'video' as const,
        url: asset.uri,
        localUri: asset.uri,
        uploaded: false,
      },
    ])
  }

  /** 搜索可关联的好物 */
  const handleSearchProducts = async () => {
    if (!productKeyword.trim() || searchingProducts) return
    setSearchingProducts(true)
    try {
      const res = await productApi.search({ keyword: productKeyword.trim(), page: 1, size: 8 })
      setProductOptions((res.data as { data?: { products?: Product[] } })?.data?.products ?? [])
    } catch {
      Alert.alert('提示', '商品搜索失败')
    } finally {
      setSearchingProducts(false)
    }
  }

  const handleSelectProduct = (item: Product) => {
    setLinkProduct(true)
    setLinkedProductId(item.id)
    setLinkedProduct(item)
    setProductSearchOpen(false)
  }

  const handleRemoveLinkedProduct = () => {
    setLinkProduct(false)
    setLinkedProductId(null)
    setLinkedProduct(null)
  }

  const handleInsertImageToEditor = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.8,
      allowsMultipleSelection: false,
    })

    if (result.canceled || !result.assets?.[0]) return

    setUploading(true)
    try {
      const asset = result.assets[0]
      const formData = new FormData()
      formData.append('file', {
        uri: asset.uri,
        type: 'image/jpeg',
        name: 'upload.jpg',
      } as any)

      const res = await fileApi.upload(formData)
      const url = (res.data as any)?.data?.url || (res.data as any)?.url
      if (url) {
        editorRef.current?.insertImage(url)
      } else {
        Alert.alert('上传失败', '图片上传返回数据异常')
      }
    } catch (err: any) {
      Alert.alert('上传失败', err?.message || '请稍后重试')
    } finally {
      setUploading(false)
    }
  }

  const handleRemoveMedia = (uid: string) => {
    setMediaList((prev) => prev.filter((item) => item.uid !== uid))
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgBase }}>
      <ScrollView contentContainerStyle={{ padding: Spacing.lg, paddingBottom: 40 }} keyboardShouldPersistTaps="handled">
        {/* Header */}
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.xl }}>
          <TouchableOpacity onPress={handleCancel} style={{ paddingVertical: Spacing.sm, paddingHorizontal: Spacing.lg, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.lg }}>
            <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>取消</Text>
          </TouchableOpacity>
          <Text style={{ fontSize: FontSize.xxl, fontWeight: 'bold', color: theme.text }}>
            {isEditing ? '编辑内容' : '发布'}
          </Text>
          <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
            <TouchableOpacity onPress={saving ? undefined : handleSaveDraft} disabled={saving} style={{ paddingVertical: Spacing.sm, paddingHorizontal: Spacing.lg, borderWidth: 1, borderColor: theme.primary + '4D', borderRadius: BorderRadius.lg, backgroundColor: theme.primaryGlow }}>
              <Text style={{ fontSize: FontSize.md, color: theme.primary }}>
                {saving ? '保存中...' : '草稿'}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={handlePublish}
              disabled={publishing}
              style={{
                backgroundColor: publishing ? theme.textTertiary : theme.primary,
                borderRadius: BorderRadius.lg,
                paddingHorizontal: Spacing.xl,
                paddingVertical: Spacing.sm,
                shadowColor: theme.primary,
                shadowOffset: { width: 0, height: 2 },
                shadowOpacity: 0.3,
                shadowRadius: 8,
                elevation: 4,
              }}
            >
              <Text style={{ color: '#FFFFFF', fontSize: FontSize.md, fontWeight: '600' }}>
                {publishing ? '发布中...' : isEditing ? '更新' : '发布'}
              </Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Title */}
        <TextInput
          placeholder="填写标题会有更多赞哦~"
          placeholderTextColor={theme.textTertiary}
          value={title}
          onChangeText={setTitle}
          maxLength={50}
          style={{
            backgroundColor: theme.bgInput,
            color: theme.text,
            borderRadius: BorderRadius.md,
            padding: Spacing.lg,
            fontSize: FontSize.xl,
            fontWeight: '700',
            marginBottom: Spacing.lg,
          }}
        />

        {/* Rich Text Editor */}
        <View style={{ marginBottom: Spacing.lg }}>
          <RichTextEditor
            ref={editorRef}
            placeholder="分享你的想法、经验、发现..."
            onChange={setContent}
            minHeight={250}
          />
        </View>

        {/* Insert image to editor */}
        <TouchableOpacity
          onPress={handleInsertImageToEditor}
          disabled={uploading}
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: theme.bgContainer,
            borderRadius: BorderRadius.lg,
            padding: Spacing.md,
            marginBottom: Spacing.lg,
            borderWidth: 1,
            borderColor: theme.border,
            borderStyle: 'dashed',
          }}
        >
          {uploading ? (
            <ActivityIndicator color={theme.primary} size="small" />
          ) : (
            <>
              <Text style={{ fontSize: 18, marginRight: Spacing.sm }}>🖼️</Text>
              <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>
                插入图片到正文
              </Text>
            </>
          )}
        </TouchableOpacity>

        {/* Media Grid */}
        <View style={{ marginBottom: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, fontWeight: '600', marginBottom: Spacing.sm }}>
            图片/视频
          </Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
            {mediaList.map((item) => (
              <View key={item.uid} style={{ width: 100, height: 100, borderRadius: BorderRadius.md, overflow: 'hidden', position: 'relative' }}>
                <Image source={{ uri: item.url || item.localUri }} style={{ width: '100%', height: '100%', resizeMode: 'cover' }} />
                <TouchableOpacity
                  onPress={() => handleRemoveMedia(item.uid)}
                  style={{
                    position: 'absolute',
                    top: 2,
                    right: 2,
                    width: 22,
                    height: 22,
                    borderRadius: 11,
                    backgroundColor: 'rgba(0,0,0,0.6)',
                    justifyContent: 'center',
                    alignItems: 'center',
                  }}
                >
                  <Text style={{ color: '#FFFFFF', fontSize: 14, lineHeight: 16 }}>×</Text>
                </TouchableOpacity>
              </View>
            ))}
            {mediaList.length < 9 && (
              <TouchableOpacity
                onPress={handleAddVideo}
                style={{
                  width: 100,
                  height: 100,
                  borderRadius: BorderRadius.md,
                  borderWidth: 1,
                  borderColor: theme.border,
                  borderStyle: 'dashed',
                  justifyContent: 'center',
                  alignItems: 'center',
                  marginRight: Spacing.sm,
                }}
              >
                <Text style={{ fontSize: 22 }}>🎬</Text>
                <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>添加视频</Text>
              </TouchableOpacity>
            )}
            {mediaList.length < 9 && (
              <TouchableOpacity
                onPress={handleAddImage}
                style={{
                  width: 100,
                  height: 100,
                  borderRadius: BorderRadius.md,
                  borderWidth: 1,
                  borderColor: theme.border,
                  borderStyle: 'dashed',
                  justifyContent: 'center',
                  alignItems: 'center',
                  backgroundColor: theme.bgContainer,
                }}
              >
                <Text style={{ fontSize: 28, color: theme.textTertiary }}>+</Text>
                <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary }}>添加图片</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        {/* P0-6：投票/问卷附件 */}
        <View style={{ marginBottom: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary, fontWeight: '500', marginBottom: Spacing.sm }}>互动附件</Text>
          <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
            <TouchableOpacity
              activeOpacity={0.7}
              onPress={() => setPollModalOpen(true)}
              style={{ paddingHorizontal: Spacing.lg, paddingVertical: Spacing.sm, borderRadius: BorderRadius.full, borderWidth: 1, borderColor: theme.border, backgroundColor: theme.bgContainer }}
            >
              <Text style={{ fontSize: FontSize.sm, color: theme.text }}>📊 发投票</Text>
            </TouchableOpacity>
            <TouchableOpacity
              activeOpacity={0.7}
              onPress={() => setSurveyModalOpen(true)}
              style={{ paddingHorizontal: Spacing.lg, paddingVertical: Spacing.sm, borderRadius: BorderRadius.full, borderWidth: 1, borderColor: theme.border, backgroundColor: theme.bgContainer }}
            >
              <Text style={{ fontSize: FontSize.sm, color: theme.text }}>📝 发问卷</Text>
            </TouchableOpacity>
          </View>
          {pollDrafts.map((p) => (
            <View key={p.id} style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, marginTop: Spacing.sm, padding: Spacing.md, backgroundColor: theme.bgInput, borderRadius: BorderRadius.md }}>
              <Text numberOfLines={1} style={{ flex: 1, fontSize: FontSize.sm, color: theme.text }}>
                📊 {p.question}（{p.options.length} 选项{p.multiple ? '·多选' : ''}）
              </Text>
              <TouchableOpacity activeOpacity={0.7} onPress={() => setPollDrafts((prev) => prev.filter((x) => x.id !== p.id))}>
                <Text style={{ fontSize: FontSize.lg, color: theme.textTertiary }}>×</Text>
              </TouchableOpacity>
            </View>
          ))}
          {surveyDrafts.map((s) => (
            <View key={s.id} style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, marginTop: Spacing.sm, padding: Spacing.md, backgroundColor: theme.bgInput, borderRadius: BorderRadius.md }}>
              <Text numberOfLines={1} style={{ flex: 1, fontSize: FontSize.sm, color: theme.text }}>
                📝 {s.title}（{s.questions.length} 题）
              </Text>
              <TouchableOpacity activeOpacity={0.7} onPress={() => setSurveyDrafts((prev) => prev.filter((x) => x.id !== s.id))}>
                <Text style={{ fontSize: FontSize.lg, color: theme.textTertiary }}>×</Text>
              </TouchableOpacity>
            </View>
          ))}
        </View>

        {/* Linked Product（关联好物） */}
        <View style={{ marginBottom: Spacing.lg }}>
          {linkProduct && linkedProductId ? (
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                backgroundColor: theme.bgContainer,
                borderRadius: BorderRadius.lg,
                padding: Spacing.md,
                borderWidth: 1,
                borderColor: theme.primary + '55',
              }}
            >
              {linkedProduct?.mainImage ? (
                <Image source={{ uri: linkedProduct.mainImage }} style={{ width: 44, height: 44, borderRadius: BorderRadius.md, marginRight: Spacing.md }} />
              ) : (
                <Text style={{ fontSize: 22, marginRight: Spacing.md }}>🛍️</Text>
              )}
              <View style={{ flex: 1, marginRight: Spacing.sm }}>
                <Text numberOfLines={1} style={{ fontSize: FontSize.sm, color: theme.text, fontWeight: '600' }}>
                  {linkedProduct?.name || `商品 #${linkedProductId}`}
                </Text>
                {linkedProduct ? (
                  <Text style={{ fontSize: FontSize.sm, color: '#FFD700', fontWeight: '700', marginTop: 2 }}>¥{linkedProduct.price}</Text>
                ) : (
                  <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginTop: 2 }}>加载中...</Text>
                )}
              </View>
              <TouchableOpacity onPress={handleRemoveLinkedProduct} accessibilityLabel="移除关联好物">
                <Text style={{ fontSize: 18, color: theme.textTertiary, paddingHorizontal: Spacing.sm }}>×</Text>
              </TouchableOpacity>
            </View>
          ) : (
            <TouchableOpacity
              onPress={() => setProductSearchOpen(!productSearchOpen)}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: theme.bgContainer,
                borderRadius: BorderRadius.lg,
                padding: Spacing.md,
                borderWidth: 1,
                borderColor: theme.border,
                borderStyle: 'dashed',
              }}
            >
              <Text style={{ fontSize: 16, marginRight: Spacing.sm }}>🔗</Text>
              <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>关联好物（可选）</Text>
            </TouchableOpacity>
          )}

          {productSearchOpen && !(linkProduct && linkedProductId) && (
            <View style={{ marginTop: Spacing.md, backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.md }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                <TextInput
                  placeholder="搜索要关联的好物"
                  placeholderTextColor={theme.textTertiary}
                  value={productKeyword}
                  onChangeText={setProductKeyword}
                  onSubmitEditing={() => void handleSearchProducts()}
                  returnKeyType="search"
                  style={{ flex: 1, backgroundColor: theme.bgInput, color: theme.text, borderRadius: BorderRadius.md, padding: Spacing.md, fontSize: FontSize.sm }}
                />
                <TouchableOpacity
                  onPress={() => void handleSearchProducts()}
                  disabled={searchingProducts}
                  style={{ backgroundColor: theme.primary, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.lg, paddingVertical: Spacing.md, opacity: searchingProducts ? 0.6 : 1 }}
                >
                  <Text style={{ fontSize: FontSize.sm, color: '#FFFFFF', fontWeight: '600' }}>搜索</Text>
                </TouchableOpacity>
              </View>

              {searchingProducts ? (
                <ActivityIndicator color={theme.primary} style={{ marginTop: Spacing.lg }} />
              ) : productOptions.length > 0 ? (
                <View style={{ marginTop: Spacing.md }}>
                  {productOptions.map((item) => (
                    <TouchableOpacity
                      key={item.id}
                      onPress={() => handleSelectProduct(item)}
                      style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: Spacing.sm, borderBottomWidth: 1, borderBottomColor: theme.border }}
                    >
                      {item.mainImage ? (
                        <Image source={{ uri: item.mainImage }} style={{ width: 40, height: 40, borderRadius: BorderRadius.sm, marginRight: Spacing.sm }} />
                      ) : (
                        <Text style={{ fontSize: 18, marginRight: Spacing.sm }}>🛍️</Text>
                      )}
                      <View style={{ flex: 1, marginRight: Spacing.sm }}>
                        <Text numberOfLines={1} style={{ fontSize: FontSize.sm, color: theme.text }}>{item.name}</Text>
                        <Text style={{ fontSize: FontSize.xs, color: '#FFD700', marginTop: 2 }}>¥{item.price}</Text>
                      </View>
                      <Text style={{ fontSize: FontSize.sm, color: theme.primary }}>选择</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              ) : (
                productKeyword.trim() ? (
                  <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, marginTop: Spacing.md, textAlign: 'center' }}>
                    没有找到相关商品
                  </Text>
                ) : null
              )}
            </View>
          )}
        </View>

        {/* Tags */}
        <TextInput
          placeholder="多个标签用空格分隔，例如：穿搭 美食 旅行"
          placeholderTextColor={theme.textTertiary}
          value={tags}
          onChangeText={setTags}
          style={{
            backgroundColor: theme.bgInput,
            color: theme.text,
            borderRadius: BorderRadius.md,
            padding: Spacing.lg,
            fontSize: FontSize.md,
            marginBottom: Spacing.lg,
          }}
        />

        {/* Quick Tags */}
        <View style={{ marginBottom: Spacing.xl }}>
          <Text style={{ fontSize: FontSize.md, color: theme.textSecondary, marginBottom: Spacing.md }}>热门话题</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
            {['日常分享', '好物推荐', '美食探店', '穿搭灵感', '旅行攻略', '数码科技'].map((tag) => (
              <TouchableOpacity
                key={tag}
                onPress={() => setTags(tags ? `${tags} ${tag}` : tag)}
                style={{
                  backgroundColor: theme.primaryGlow,
                  borderRadius: BorderRadius.xl,
                  paddingHorizontal: Spacing.lg,
                  paddingVertical: Spacing.sm,
                  borderWidth: 1,
                  borderColor: theme.primary + '33',
                }}
              >
                <Text style={{ fontSize: FontSize.sm, color: theme.primary }}># {tag}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Tips */}
        <View style={{ backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.lg }}>
          <Text style={{ fontSize: FontSize.sm, color: theme.textTertiary, lineHeight: 20 }}>
            💡 发布提示：{'\n'}
            • 分享真实体验，获得更多互动{'\n'}
            • 添加话题标签，让更多人看到{'\n'}
            • 优质内容会被推荐到首页{'\n'}
            • 支持富文本排版、图片上传、链接插入
          </Text>
        </View>
      </ScrollView>

      {/* 投票配置弹窗（P0-6：2-10 选项，单/多选） */}
      <Modal visible={pollModalOpen} transparent animationType="fade" onRequestClose={() => setPollModalOpen(false)}>
        <TouchableOpacity activeOpacity={1} style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', alignItems: 'center', justifyContent: 'center' }} onPress={() => setPollModalOpen(false)}>
          <TouchableOpacity activeOpacity={1} style={{ width: '88%', backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.xl }}>
            <ScrollView showsVerticalScrollIndicator={false} style={{ maxHeight: 440 }}>
              <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text, marginBottom: Spacing.lg }}>发起投票</Text>
              <TextInput
                value={pollDraft.question}
                onChangeText={(text) => setPollDraft((prev) => ({ ...prev, question: text }))}
                placeholder="投票问题（必填）"
                placeholderTextColor={theme.textTertiary}
                maxLength={200}
                style={{ borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, color: theme.text, fontSize: FontSize.sm, backgroundColor: theme.bgInput, marginBottom: Spacing.md }}
              />
              {pollDraft.options.map((option, i) => (
                <View key={i} style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, marginBottom: Spacing.md }}>
                  <TextInput
                    value={option}
                    onChangeText={(text) => setPollDraft((prev) => ({ ...prev, options: prev.options.map((o, idx) => (idx === i ? text : o)) }))}
                    placeholder={`选项 ${i + 1}`}
                    placeholderTextColor={theme.textTertiary}
                    maxLength={200}
                    style={{ flex: 1, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, color: theme.text, fontSize: FontSize.sm, backgroundColor: theme.bgInput }}
                  />
                  {pollDraft.options.length > 2 && (
                    <TouchableOpacity activeOpacity={0.7} onPress={() => setPollDraft((prev) => ({ ...prev, options: prev.options.filter((_, idx) => idx !== i) }))}>
                      <Text style={{ fontSize: FontSize.xl, color: theme.textTertiary }}>×</Text>
                    </TouchableOpacity>
                  )}
                </View>
              ))}
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                {pollDraft.options.length < 10 ? (
                  <TouchableOpacity activeOpacity={0.7} onPress={() => setPollDraft((prev) => ({ ...prev, options: [...prev.options, ''] }))}>
                    <Text style={{ fontSize: FontSize.sm, color: theme.primary }}>＋ 添加选项</Text>
                  </TouchableOpacity>
                ) : <View />}
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs }}>
                  <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>多选</Text>
                  <Switch value={pollDraft.multiple} onValueChange={(value) => setPollDraft((prev) => ({ ...prev, multiple: value }))} />
                </View>
              </View>
            </ScrollView>
            <View style={{ flexDirection: 'row', gap: Spacing.md, marginTop: Spacing.lg }}>
              <TouchableOpacity activeOpacity={0.7} onPress={() => setPollModalOpen(false)} style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, borderRadius: BorderRadius.full, borderWidth: 1, borderColor: theme.border }}>
                <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity activeOpacity={0.8} onPress={confirmPollDraft} style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, borderRadius: BorderRadius.full, backgroundColor: theme.primary }}>
                <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>添加</Text>
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>

      {/* 问卷配置弹窗（P0-6：题目标题/类型/选项/必填） */}
      <Modal visible={surveyModalOpen} transparent animationType="fade" onRequestClose={() => setSurveyModalOpen(false)}>
        <TouchableOpacity activeOpacity={1} style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', alignItems: 'center', justifyContent: 'center' }} onPress={() => setSurveyModalOpen(false)}>
          <TouchableOpacity activeOpacity={1} style={{ width: '88%', backgroundColor: theme.bgContainer, borderRadius: BorderRadius.lg, padding: Spacing.xl }}>
            <ScrollView showsVerticalScrollIndicator={false} style={{ maxHeight: 480 }}>
              <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text, marginBottom: Spacing.lg }}>发起问卷</Text>
              <TextInput
                value={surveyDraft.title}
                onChangeText={(text) => setSurveyDraft((prev) => ({ ...prev, title: text }))}
                placeholder="问卷标题（必填）"
                placeholderTextColor={theme.textTertiary}
                maxLength={200}
                style={{ borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, color: theme.text, fontSize: FontSize.sm, backgroundColor: theme.bgInput, marginBottom: Spacing.md }}
              />
              {surveyDraft.questions.map((question, qi) => (
                <View key={qi} style={{ borderTopWidth: 1, borderTopColor: theme.border, paddingTop: Spacing.md, marginBottom: Spacing.md, gap: Spacing.md }}>
                  <TextInput
                    value={question.text}
                    onChangeText={(text) => setSurveyDraft((prev) => ({ ...prev, questions: prev.questions.map((q, idx) => (idx === qi ? { ...q, text } : q)) }))}
                    placeholder={`问题 ${qi + 1}`}
                    placeholderTextColor={theme.textTertiary}
                    maxLength={200}
                    style={{ borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, color: theme.text, fontSize: FontSize.sm, backgroundColor: theme.bgInput }}
                  />
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, flexWrap: 'wrap' }}>
                    {(['single', 'multi', 'text'] as const).map((type) => (
                      <TouchableOpacity
                        key={type}
                        activeOpacity={0.7}
                        onPress={() =>
                          setSurveyDraft((prev) => ({
                            ...prev,
                            questions: prev.questions.map((q, idx) =>
                              idx === qi ? { ...q, type, options: type === 'text' ? [] : q.options.length >= 2 ? q.options : ['', ''] } : q,
                            ),
                          }))
                        }
                        style={{ paddingHorizontal: Spacing.md, paddingVertical: 4, borderRadius: BorderRadius.full, borderWidth: 1, borderColor: question.type === type ? theme.primary : theme.border, backgroundColor: question.type === type ? theme.primary : 'transparent' }}
                      >
                        <Text style={{ fontSize: FontSize.xs, color: question.type === type ? '#FFFFFF' : theme.textSecondary }}>
                          {type === 'single' ? '单选' : type === 'multi' ? '多选' : '填空'}
                        </Text>
                      </TouchableOpacity>
                    ))}
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.xs }}>
                      <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>必填</Text>
                      <Switch
                        value={question.required}
                        onValueChange={(value) =>
                          setSurveyDraft((prev) => ({ ...prev, questions: prev.questions.map((q, idx) => (idx === qi ? { ...q, required: value } : q)) }))
                        }
                      />
                    </View>
                  </View>
                  {question.type !== 'text' &&
                    question.options.map((option, oi) => (
                      <View key={oi} style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>
                        <TextInput
                          value={option}
                          onChangeText={(text) =>
                            setSurveyDraft((prev) => ({
                              ...prev,
                              questions: prev.questions.map((q, idx) =>
                                idx === qi ? { ...q, options: q.options.map((o, oidx) => (oidx === oi ? text : o)) } : q,
                              ),
                            }))
                          }
                          placeholder={`选项 ${oi + 1}`}
                          placeholderTextColor={theme.textTertiary}
                          maxLength={200}
                          style={{ flex: 1, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm, color: theme.text, fontSize: FontSize.sm, backgroundColor: theme.bgInput }}
                        />
                        {question.options.length > 2 && (
                          <TouchableOpacity
                            activeOpacity={0.7}
                            onPress={() =>
                              setSurveyDraft((prev) => ({
                                ...prev,
                                questions: prev.questions.map((q, idx) =>
                                  idx === qi ? { ...q, options: q.options.filter((_, oidx) => oidx !== oi) } : q,
                                ),
                              }))
                            }
                          >
                            <Text style={{ fontSize: FontSize.xl, color: theme.textTertiary }}>×</Text>
                          </TouchableOpacity>
                        )}
                      </View>
                    ))}
                  {question.type !== 'text' && question.options.length < 10 && (
                    <TouchableOpacity
                      activeOpacity={0.7}
                      onPress={() =>
                        setSurveyDraft((prev) => ({
                          ...prev,
                          questions: prev.questions.map((q, idx) => (idx === qi ? { ...q, options: [...q.options, ''] } : q)),
                        }))
                      }
                    >
                      <Text style={{ fontSize: FontSize.sm, color: theme.primary }}>＋ 添加选项</Text>
                    </TouchableOpacity>
                  )}
                </View>
              ))}
              <TouchableOpacity
                activeOpacity={0.7}
                onPress={() => setSurveyDraft((prev) => ({ ...prev, questions: [...prev.questions, { text: '', type: 'single', options: ['', ''], required: true }] }))}
              >
                <Text style={{ fontSize: FontSize.sm, color: theme.primary }}>＋ 添加问题</Text>
              </TouchableOpacity>
            </ScrollView>
            <View style={{ flexDirection: 'row', gap: Spacing.md, marginTop: Spacing.lg }}>
              <TouchableOpacity activeOpacity={0.7} onPress={() => setSurveyModalOpen(false)} style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, borderRadius: BorderRadius.full, borderWidth: 1, borderColor: theme.border }}>
                <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>取消</Text>
              </TouchableOpacity>
              <TouchableOpacity activeOpacity={0.8} onPress={confirmSurveyDraft} style={{ flex: 1, alignItems: 'center', paddingVertical: Spacing.md, borderRadius: BorderRadius.full, backgroundColor: theme.primary }}>
                <Text style={{ fontSize: FontSize.md, color: '#FFFFFF', fontWeight: '600' }}>添加</Text>
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </View>
  )
}
