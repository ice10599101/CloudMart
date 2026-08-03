import { View, Text, TextInput, TouchableOpacity, ScrollView, Alert, ActivityIndicator, Image, BackHandler } from 'react-native'
import { useState, useRef, useEffect, useCallback } from 'react'
import { router, useLocalSearchParams, useGlobalSearchParams } from 'expo-router'
import * as ImagePicker from 'expo-image-picker'
import { useTheme } from '@/hooks/use-theme-context'
import { useAuthStore } from '@/store/auth'
import { communityApi } from '@/api/community'
import { fileApi } from '@/api/file'
import { RichTextEditor, RichTextEditorRef } from '@/components/RichTextEditor'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

interface MediaItem {
  uid: string
  type: 'image' | 'video'
  url: string
  localUri?: string
  uploaded: boolean
}

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
  const [publishing, setPublishing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [isEditing, setIsEditing] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [draftId, setDraftId] = useState<number | null>(null)

  const hasContent = title.trim() || content.trim() || mediaList.length > 0

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

  useEffect(() => {
    if (!isLoggedIn) {
      router.push('/login')
      return
    }
    if (params.edit && !isEditing) {
      loadPostForEdit(parseInt(params.edit))
    }
  }, [params.edit, isLoggedIn])

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
            const tagList = tags.trim() ? tags.split(/[,，\s]+/).filter(Boolean) : []
            const { mediaUrls, coverImage, mediaType } = await uploadMediaFiles()
            const postData: Record<string, unknown> = {
              title: title.trim(),
              content,
              tags: tagList,
              coverImage,
              mediaUrls,
              mediaType,
              status: 1,
            }

            if (isEditing && editingId) {
              await communityApi.updatePost(editingId, postData)
              Alert.alert('更新成功', '', [{ text: '确定', onPress: () => router.back() }])
            } else {
              await communityApi.createPost(postData as any)
              Alert.alert('发布成功', '', [{
                text: '确定',
                onPress: () => {
                  setTitle('')
                  setContent('')
                  setTags('')
                  setMediaList([])
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
            const tagList = tags.trim() ? tags.split(/[,，\s]+/).filter(Boolean) : []
            const { mediaUrls, coverImage, mediaType } = await uploadMediaFiles()
            const postData: Record<string, unknown> = {
              title: title.trim() || '未命名草稿',
              content,
              tags: tagList,
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
              const newId = (createRes.data as any)?.data?.id
              if (newId) setDraftId(newId)
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
    </View>
  )
}
