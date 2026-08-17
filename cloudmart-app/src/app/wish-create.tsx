import { View, Text, ScrollView, TouchableOpacity, Image, TextInput, Alert, ActivityIndicator } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import * as ImagePicker from 'expo-image-picker'
import { wishApi } from '@/api/wish'
import { fileApi } from '@/api/file'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type { WishCategory, WishVisibility } from '@/types'

const MAX_TITLE_LENGTH = 120
const MAX_TAGS = 5
const MAX_MEDIA = 9

interface UploadItem {
  key: string
  base64: string
  uri: string
  url?: string
  status: 'uploading' | 'success' | 'error'
}

const EXPECTED_OPTIONS = [
  { label: '7 天', days: 7 },
  { label: '30 天', days: 30 },
  { label: '半年', days: 182 },
  { label: '一年', days: 365 },
]

const VISIBILITY_OPTIONS: Array<{ value: WishVisibility; label: string; hint: string }> = [
  { value: 'PUBLIC', label: '公开', hint: '展示在心愿广场，所有人可见' },
  { value: 'PRIVATE', label: '私密', hint: '仅自己可见，不公开展示' },
]

export default function WishCreateScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [categories, setCategories] = useState<WishCategory[]>([])
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [tags, setTags] = useState<string[]>([])
  const [tagInput, setTagInput] = useState('')
  const [visibility, setVisibility] = useState<WishVisibility>('PUBLIC')
  const [expectedDays, setExpectedDays] = useState<number | null>(null)
  const [uploads, setUploads] = useState<UploadItem[]>([])
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
      return
    }
    const fetchCategories = async () => {
      try {
        const res = await wishApi.getCategories()
        if (res.data?.success) {
          setCategories(res.data.data)
        }
      } catch {
        // 错误已由 request 拦截器处理
      }
    }
    fetchCategories()
  }, [isLoggedIn])

  const pickImage = useCallback(async () => {
    if (uploads.length >= MAX_MEDIA) {
      Alert.alert('提示', `最多上传 ${MAX_MEDIA} 张图片`)
      return
    }
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync()
    if (!permission.granted) {
      Alert.alert('提示', '需要相册权限才能上传图片')
      return
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.8,
      base64: true,
      allowsMultipleSelection: true,
      selectionLimit: MAX_MEDIA - uploads.length,
    })
    if (result.canceled) return

    const items: UploadItem[] = result.assets
      .filter((asset) => asset.base64)
      .slice(0, MAX_MEDIA - uploads.length)
      .map((asset) => ({
        key: `${asset.assetId ?? asset.uri}-${Date.now()}`,
        base64: asset.base64!,
        uri: asset.uri,
        status: 'uploading' as const,
      }))
    if (items.length === 0) return

    setUploads((prev) => [...prev, ...items])
    items.forEach((item) => uploadItem(item.key, item.base64))
  }, [uploads.length])

  const uploadItem = async (key: string, base64: string) => {
    try {
      const res = await fileApi.upload({ file: base64, type: 'image/jpeg' })
      const url = res.data?.data?.url
      if (!url) throw new Error('upload failed')
      setUploads((prev) => prev.map((u) => (u.key === key ? { ...u, url, status: 'success' } : u)))
    } catch {
      setUploads((prev) => prev.map((u) => (u.key === key ? { ...u, status: 'error' } : u)))
    }
  }

  const retryUpload = (item: UploadItem) => {
    setUploads((prev) => prev.map((u) => (u.key === item.key ? { ...u, status: 'uploading' } : u)))
    uploadItem(item.key, item.base64)
  }

  const removeUpload = (key: string) => {
    setUploads((prev) => prev.filter((u) => u.key !== key))
  }

  const addTag = () => {
    const tag = tagInput.trim()
    if (!tag) return
    if (tags.length >= MAX_TAGS) {
      Alert.alert('提示', `最多添加 ${MAX_TAGS} 个标签`)
      return
    }
    if (tags.includes(tag)) {
      setTagInput('')
      return
    }
    setTags((prev) => [...prev, tag])
    setTagInput('')
  }

  const validate = (): string | null => {
    if (!title.trim()) return '请输入心愿标题'
    if (title.length > MAX_TITLE_LENGTH) return `标题不能超过 ${MAX_TITLE_LENGTH} 字`
    if (!description.trim()) return '请输入心愿描述'
    if (!categoryId) return '请选择心愿分类'
    return null
  }

  const submit = async () => {
    const error = validate()
    if (error) {
      Alert.alert('提示', error)
      return
    }
    if (uploads.some((u) => u.status === 'uploading')) {
      Alert.alert('提示', '图片还在上传中，请稍候')
      return
    }

    setSubmitting(true)
    try {
      const mediaUrls = uploads.filter((u) => u.status === 'success' && u.url).map((u) => u.url!)
      const expectedAt = expectedDays
        ? new Date(Date.now() + expectedDays * 24 * 60 * 60 * 1000).toISOString()
        : undefined
      const res = await wishApi.createWish({
        title: title.trim(),
        description: description.trim(),
        categoryId: categoryId!,
        visibility,
        mediaUrls: mediaUrls.length > 0 ? mediaUrls : undefined,
        tags: tags.length > 0 ? tags : undefined,
        expectedAt,
      })
      const wishId = res.data?.data?.id
      Alert.alert('成功', '心愿已种下，愿它发光 ✨', [
        { text: '好的', onPress: () => router.replace(wishId ? `/wish-detail?id=${wishId}` : '/wish-home') },
      ])
    } catch {
      Alert.alert('失败', '发布失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      {/* 导航栏 */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          padding: Spacing.md,
          borderBottomWidth: 1,
          borderBottomColor: WishColors.border,
        }}
      >
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.lg, color: WishColors.textSecondary }}>‹ 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginLeft: Spacing.md }}>
          种下心愿
        </Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: Spacing.md, paddingBottom: insets.bottom + 100 }}>
        {/* 标题 */}
        <FieldLabel label={`心愿标题 (${title.length}/${MAX_TITLE_LENGTH})`} required />
        <StyledTextInput
          value={title}
          onChangeText={setTitle}
          placeholder="如：今年去看一次极光"
          maxLength={MAX_TITLE_LENGTH}
        />

        {/* 描述 */}
        <FieldLabel label="心愿描述" required />
        <StyledTextInput
          value={description}
          onChangeText={setDescription}
          placeholder="写下这个心愿的故事..."
          multiline
          numberOfLines={4}
          style={{ height: 100, textAlignVertical: 'top' }}
        />

        {/* 分类 */}
        <FieldLabel label="选择分类" required />
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
          {categories.map((category) => (
            <TouchableOpacity
              key={category.id}
              onPress={() => setCategoryId(category.id)}
              style={{
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.xs + 2,
                borderRadius: 20,
                borderWidth: 1,
                borderColor: categoryId === category.id ? WishColors.primary : WishColors.border,
                backgroundColor: categoryId === category.id ? 'rgba(233,69,96,0.15)' : 'transparent',
              }}
            >
              <Text
                style={{
                  fontSize: FontSize.sm,
                  color: categoryId === category.id ? WishColors.primary : WishColors.textSecondary,
                }}
              >
                {category.name}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* 图片 */}
        <FieldLabel label={`添加图片 (${uploads.length}/${MAX_MEDIA})`} />
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
          {uploads.map((item) => (
            <View key={item.key} style={{ width: 90, height: 90 }}>
              <Image source={{ uri: item.uri }} style={{ width: '100%', height: '100%', borderRadius: BorderRadius.md }} />
              {item.status === 'uploading' && (
                <View
                  style={{
                    position: 'absolute',
                    inset: 0,
                    borderRadius: BorderRadius.md,
                    backgroundColor: 'rgba(0,0,0,0.5)',
                    justifyContent: 'center',
                    alignItems: 'center',
                  }}
                >
                  <ActivityIndicator size="small" color="#fff" />
                </View>
              )}
              {item.status === 'error' && (
                <TouchableOpacity
                  onPress={() => retryUpload(item)}
                  style={{
                    position: 'absolute',
                    inset: 0,
                    borderRadius: BorderRadius.md,
                    backgroundColor: 'rgba(233,69,96,0.7)',
                    justifyContent: 'center',
                    alignItems: 'center',
                  }}
                >
                  <Text style={{ color: '#fff', fontSize: FontSize.xs }}>重试</Text>
                </TouchableOpacity>
              )}
              <TouchableOpacity
                onPress={() => removeUpload(item.key)}
                style={{
                  position: 'absolute',
                  top: -6,
                  right: -6,
                  width: 20,
                  height: 20,
                  borderRadius: 10,
                  backgroundColor: WishColors.primary,
                  justifyContent: 'center',
                  alignItems: 'center',
                }}
              >
                <Text style={{ color: '#fff', fontSize: 12, lineHeight: 14 }}>×</Text>
              </TouchableOpacity>
            </View>
          ))}
          {uploads.length < MAX_MEDIA && (
            <TouchableOpacity
              onPress={pickImage}
              style={{
                width: 90,
                height: 90,
                borderRadius: BorderRadius.md,
                borderWidth: 1,
                borderStyle: 'dashed',
                borderColor: WishColors.border,
                justifyContent: 'center',
                alignItems: 'center',
              }}
            >
              <Text style={{ fontSize: 24, color: WishColors.textTertiary }}>＋</Text>
            </TouchableOpacity>
          )}
        </View>

        {/* 标签 */}
        <FieldLabel label={`标签 (${tags.length}/${MAX_TAGS})`} />
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm, marginBottom: Spacing.sm }}>
          {tags.map((tag) => (
            <View
              key={tag}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                paddingHorizontal: Spacing.md,
                paddingVertical: 4,
                borderRadius: 20,
                backgroundColor: 'rgba(0,212,255,0.12)',
              }}
            >
              <Text style={{ fontSize: FontSize.sm, color: WishColors.accentCyan }}>{tag}</Text>
              <TouchableOpacity onPress={() => setTags((prev) => prev.filter((t) => t !== tag))} style={{ marginLeft: 6 }}>
                <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>×</Text>
              </TouchableOpacity>
            </View>
          ))}
        </View>
        {tags.length < MAX_TAGS && (
          <StyledTextInput
            value={tagInput}
            onChangeText={setTagInput}
            placeholder="输入标签后按完成键添加"
            onSubmitEditing={addTag}
            returnKeyType="done"
          />
        )}

        {/* 可见性 */}
        <FieldLabel label="谁可以看到" />
        <View style={{ gap: Spacing.sm }}>
          {VISIBILITY_OPTIONS.map((option) => (
            <TouchableOpacity
              key={option.value}
              onPress={() => setVisibility(option.value)}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                padding: Spacing.md,
                borderRadius: BorderRadius.lg,
                borderWidth: 1,
                borderColor: visibility === option.value ? WishColors.primary : WishColors.border,
                backgroundColor: visibility === option.value ? 'rgba(233,69,96,0.1)' : 'transparent',
              }}
            >
              <View
                style={{
                  width: 18,
                  height: 18,
                  borderRadius: 9,
                  borderWidth: 2,
                  borderColor: visibility === option.value ? WishColors.primary : WishColors.textTertiary,
                  justifyContent: 'center',
                  alignItems: 'center',
                  marginRight: Spacing.sm,
                }}
              >
                {visibility === option.value && (
                  <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: WishColors.primary }} />
                )}
              </View>
              <View>
                <Text style={{ fontSize: FontSize.md, color: WishColors.text, fontWeight: '600' }}>{option.label}</Text>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: 2 }}>{option.hint}</Text>
              </View>
            </TouchableOpacity>
          ))}
        </View>

        {/* 预计完成时间 */}
        <FieldLabel label="预计完成时间（可选）" />
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
          {EXPECTED_OPTIONS.map((option) => (
            <TouchableOpacity
              key={option.days}
              onPress={() => setExpectedDays(expectedDays === option.days ? null : option.days)}
              style={{
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.xs + 2,
                borderRadius: 20,
                borderWidth: 1,
                borderColor: expectedDays === option.days ? WishColors.accentCyan : WishColors.border,
                backgroundColor: expectedDays === option.days ? 'rgba(0,212,255,0.12)' : 'transparent',
              }}
            >
              <Text
                style={{
                  fontSize: FontSize.sm,
                  color: expectedDays === option.days ? WishColors.accentCyan : WishColors.textSecondary,
                }}
              >
                {option.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </ScrollView>

      {/* 底部提交 */}
      <View
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 0,
          padding: Spacing.md,
          paddingBottom: insets.bottom + Spacing.md,
          backgroundColor: 'rgba(26,26,46,0.95)',
          borderTopWidth: 1,
          borderTopColor: WishColors.border,
        }}
      >
        <TouchableOpacity
          activeOpacity={0.85}
          onPress={submit}
          disabled={submitting}
          style={{
            paddingVertical: Spacing.md,
            borderRadius: 28,
            backgroundColor: submitting ? 'rgba(233,69,96,0.5)' : WishColors.primary,
            alignItems: 'center',
          }}
        >
          {submitting ? (
            <ActivityIndicator size="small" color="#fff" />
          ) : (
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#fff' }}>✨ 种下这颗心愿</Text>
          )}
        </TouchableOpacity>
      </View>
    </View>
  )
}

function FieldLabel({ label, required }: { label: string; required?: boolean }) {
  return (
    <View style={{ flexDirection: 'row', marginTop: Spacing.lg, marginBottom: Spacing.sm }}>
      {required && <Text style={{ color: WishColors.primary, fontSize: FontSize.md, marginRight: 2 }}>*</Text>}
      <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.textSecondary }}>{label}</Text>
    </View>
  )
}

function StyledTextInput({
  value,
  onChangeText,
  placeholder,
  multiline,
  numberOfLines,
  maxLength,
  returnKeyType,
  onSubmitEditing,
  style,
}: {
  value: string
  onChangeText: (text: string) => void
  placeholder: string
  multiline?: boolean
  numberOfLines?: number
  maxLength?: number
  returnKeyType?: 'done' | 'next'
  onSubmitEditing?: () => void
  style?: object
}) {
  return (
    <TextInput
      value={value}
      onChangeText={onChangeText}
      placeholder={placeholder}
      placeholderTextColor={WishColors.textTertiary}
      multiline={multiline}
      numberOfLines={numberOfLines}
      maxLength={maxLength}
      returnKeyType={returnKeyType}
      onSubmitEditing={onSubmitEditing}
      style={{
        padding: Spacing.md,
        borderRadius: BorderRadius.lg,
        backgroundColor: WishColors.bgContainer,
        borderWidth: 1,
        borderColor: WishColors.border,
        color: WishColors.text,
        fontSize: FontSize.md,
        ...style,
      }}
    />
  )
}
