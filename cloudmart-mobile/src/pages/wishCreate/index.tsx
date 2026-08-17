import { useState, useEffect, useCallback, useRef } from 'react'
import { View, Text, Input, Textarea, ScrollView, Image, Picker } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { fileApi } from '@/api/file'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import type { WishCategory, WishVisibility } from '@/types'
import styles from './index.module.scss'

const MAX_TAGS = 5
const MAX_MEDIA = 9
const MAX_FILE_SIZE = 10 * 1024 * 1024

type UploadStatus = 'uploading' | 'success' | 'error'

interface UploadItem {
  id: string
  filePath: string
  url?: string
  progress: number
  status: UploadStatus
}

const VISIBILITY_OPTIONS: { value: WishVisibility; label: string; desc: string }[] = [
  { value: 'PUBLIC', label: '公开', desc: '所有人可见' },
  { value: 'PRIVATE', label: '私密', desc: '仅自己可见' },
  { value: 'TREE_HOLE', label: '树洞', desc: '匿名+AI回复' },
]

export default function WishCreatePage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [categoryId, setCategoryId] = useState<number | undefined>()
  const [visibility, setVisibility] = useState<WishVisibility>('PUBLIC')
  const [tags, setTags] = useState<string[]>([])
  const [tagInput, setTagInput] = useState('')
  const [uploads, setUploads] = useState<UploadItem[]>([])
  const [expectedAt, setExpectedAt] = useState<string>('')
  const [categories, setCategories] = useState<WishCategory[]>([])
  const [submitting, setSubmitting] = useState(false)
  const uploadTasksRef = useRef<Map<string, Taro.UploadTask>>(new Map())

  useEffect(() => {
    if (!isLoggedIn) {
      Taro.redirectTo({ url: '/pages/login/index' })
      return
    }
    const fetchCategories = async () => {
      try {
        const res = await wishApi.getCategories()
        if (res.data.success) {
          setCategories(res.data.data)
        }
      } catch {
        // ignore
      }
    }
    fetchCategories()
  }, [isLoggedIn])

  const uploadedUrls = uploads.filter(u => u.status === 'success' && u.url).map(u => u.url!) as string[]
  const isUploading = uploads.some(u => u.status === 'uploading')

  const updateUpload = useCallback((id: string, patch: Partial<UploadItem>) => {
    setUploads(prev => prev.map(item => item.id === id ? { ...item, ...patch } : item))
  }, [])

  const performUpload = useCallback(async (item: UploadItem) => {
    updateUpload(item.id, { status: 'uploading', progress: 0 })

    try {
      const uploadTask = Taro.uploadFile({
        url: `${fileApi.upload.toString()}`,
        filePath: item.filePath,
        name: 'file',
        header: { Authorization: `Bearer ${Taro.getStorageSync('access_token')}` },
        success: (res) => {
          try {
            const data = JSON.parse(res.data)
            if (data.success && data.data?.url) {
              updateUpload(item.id, { status: 'success', progress: 100, url: data.data.url })
            } else {
              updateUpload(item.id, { status: 'error' })
            }
          } catch {
            updateUpload(item.id, { status: 'error' })
          }
        },
        fail: () => {
          updateUpload(item.id, { status: 'error' })
        },
      })

      uploadTasksRef.current.set(item.id, uploadTask)

      uploadTask.progress(({ progress }) => {
        updateUpload(item.id, { progress: progress || 0 })
      })
    } catch {
      updateUpload(item.id, { status: 'error' })
    } finally {
      uploadTasksRef.current.delete(item.id)
    }
  }, [updateUpload])

  const handleChooseImage = async () => {
    if (uploadedUrls.length >= MAX_MEDIA) {
      Taro.showToast({ title: `最多上传 ${MAX_MEDIA} 张图片`, icon: 'none' })
      return
    }

    try {
      const result = await Taro.chooseImage({
        count: MAX_MEDIA - uploadedUrls.length,
        sourceType: ['album', 'camera'],
        sizeType: ['compressed'],
      })

      for (const filePath of result.tempFilePaths) {
        const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
        const item: UploadItem = { id, filePath, progress: 0, status: 'uploading' }
        setUploads(prev => [...prev, item])
        performUpload(item)
      }
    } catch {
      // 用户取消选择
    }
  }

  const handleRetryUpload = (id: string) => {
    const item = uploads.find(u => u.id === id)
    if (item) {
      performUpload(item)
    }
  }

  const handleRemoveMedia = (id: string) => {
    const task = uploadTasksRef.current.get(id)
    if (task) {
      task.abort()
      uploadTasksRef.current.delete(id)
    }
    setUploads(prev => prev.filter(u => u.id !== id))
  }

  const handleAddTag = () => {
    const trimmed = tagInput.trim()
    if (!trimmed) return
    if (tags.length >= MAX_TAGS) {
      Taro.showToast({ title: `标签最多 ${MAX_TAGS} 个`, icon: 'none' })
      return
    }
    if (tags.includes(trimmed)) {
      Taro.showToast({ title: '标签已存在', icon: 'none' })
      return
    }
    if (trimmed.length > 20) {
      Taro.showToast({ title: '单个标签不超过 20 字符', icon: 'none' })
      return
    }
    setTags([...tags, trimmed])
    setTagInput('')
  }

  const handleRemoveTag = (tag: string) => {
    setTags(tags.filter(t => t !== tag))
  }

  const handleSubmit = async () => {
    if (!title.trim()) {
      Taro.showToast({ title: '请输入心愿标题', icon: 'none' })
      return
    }
    if (title.length > 120) {
      Taro.showToast({ title: '标题不超过 120 字符', icon: 'none' })
      return
    }
    if (!description.trim()) {
      Taro.showToast({ title: '请描述你的心愿', icon: 'none' })
      return
    }
    if (description.length > 2000) {
      Taro.showToast({ title: '描述不超过 2000 字符', icon: 'none' })
      return
    }
    if (!categoryId) {
      Taro.showToast({ title: '请选择心愿分类', icon: 'none' })
      return
    }
    if (isUploading) {
      Taro.showToast({ title: '请等待图片上传完成', icon: 'none' })
      return
    }
    const failedCount = uploads.filter(u => u.status === 'error').length
    if (failedCount > 0) {
      Taro.showToast({ title: `有 ${failedCount} 张图片上传失败`, icon: 'none' })
      return
    }

    setSubmitting(true)
    try {
      const res = await wishApi.createWish({
        title: title.trim(),
        description: description.trim(),
        categoryId,
        visibility,
        mediaUrls: uploadedUrls.length > 0 ? uploadedUrls : undefined,
        tags: tags.length > 0 ? tags : undefined,
        expectedAt: expectedAt || undefined,
      })
      if (res.data.success) {
        Taro.showToast({ title: '心愿发布成功！', icon: 'success' })
        setTimeout(() => {
          Taro.redirectTo({ url: `/pages/wishDetail/index?id=${res.data.data.id}` })
        }, 1500)
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setSubmitting(false)
    }
  }

  const categoryIndex = categories.findIndex(c => c.id === categoryId)
  const visibilityIndex = VISIBILITY_OPTIONS.findIndex(v => v.value === visibility)

  return (
    <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
      <CustomNavBar title='许下心愿' showBack />
      <ScrollView scrollY className={styles.scroll}>
        {/* 标题 */}
        <View className={styles.field}>
          <Text className={styles.label}>心愿标题 <Text style={{ color: '#e94560' }}>*</Text></Text>
          <Input
            className={styles.input}
            placeholder='给你的心愿起个名字...'
            value={title}
            onInput={e => setTitle(e.detail.value)}
            maxLength={120}
          />
          <Text className={styles.count}>{title.length}/120</Text>
        </View>

        {/* 描述 */}
        <View className={styles.field}>
          <Text className={styles.label}>心愿描述 <Text style={{ color: '#e94560' }}>*</Text></Text>
          <Textarea
            className={styles.textarea}
            placeholder='详细描述你的心愿、计划或梦想...'
            value={description}
            onInput={e => setDescription(e.detail.value)}
            maxlength={2000}
            autoHeight
          />
          <Text className={styles.count}>{description.length}/2000</Text>
        </View>

        {/* 分类 */}
        <View className={styles.field}>
          <Text className={styles.label}>心愿分类 <Text style={{ color: '#e94560' }}>*</Text></Text>
          <Picker
            mode='selector'
            range={categories}
            rangeKey='name'
            value={categoryIndex >= 0 ? categoryIndex : 0}
            onChange={e => setCategoryId(categories[Number(e.detail.value)]?.id)}
          >
            <View className={styles.pickerValue}>
              <Text className={categoryIndex >= 0 ? styles.pickerText : styles.pickerPlaceholder}>
                {categoryIndex >= 0 ? categories[categoryIndex].name : '选择一个分类'}
              </Text>
              <Text className={styles.pickerArrow}>›</Text>
            </View>
          </Picker>
        </View>

        {/* 图片上传 */}
        <View className={styles.field}>
          <Text className={styles.label}>图片/媒体（可选，最多 {MAX_MEDIA} 张）</Text>
          <View className={styles.uploadArea}>
            {uploads.map(item => (
              <View key={item.id} className={styles.uploadItem}>
                {item.status === 'success' && item.url ? (
                  <>
                    <Image className={styles.uploadPreview} src={item.url} mode='aspectFill' />
                    <View className={styles.removeBtn} onClick={() => handleRemoveMedia(item.id)}>
                      <Text className={styles.removeIcon}>×</Text>
                    </View>
                  </>
                ) : (
                  <View className={styles.uploadProgress}>
                    {item.status === 'uploading' && (
                      <>
                        <Text className={styles.progressText}>{item.progress}%</Text>
                        <View className={styles.progressBar}>
                          <View className={styles.progressBarFill} style={{ width: `${item.progress}%` }} />
                        </View>
                        <View className={styles.cancelBtn} onClick={() => handleRemoveMedia(item.id)}>
                          <Text className={styles.cancelIcon}>×</Text>
                        </View>
                      </>
                    )}
                    {item.status === 'error' && (
                      <View className={styles.errorOverlay} onClick={() => handleRetryUpload(item.id)}>
                        <Text className={styles.errorText}>上传失败</Text>
                        <Text className={styles.retryText}>点击重试</Text>
                      </View>
                    )}
                  </View>
                )}
              </View>
            ))}
            {uploadedUrls.length < MAX_MEDIA && (
              <View className={styles.uploadTrigger} onClick={handleChooseImage}>
                <Text className={styles.plusIcon}>+</Text>
              </View>
            )}
          </View>
        </View>

        {/* 标签 */}
        <View className={styles.field}>
          <Text className={styles.label}>标签（可选，最多 {MAX_TAGS} 个）</Text>
          <View className={styles.tagArea}>
            {tags.map(tag => (
              <View key={tag} className={styles.tagItem} onClick={() => handleRemoveTag(tag)}>
                <Text className={styles.tagText}>{tag}</Text>
                <Text className={styles.tagClose}>×</Text>
              </View>
            ))}
            {tags.length < MAX_TAGS && (
              <Input
                className={styles.tagInput}
                placeholder='输入标签'
                value={tagInput}
                onInput={e => setTagInput(e.detail.value)}
                onConfirm={handleAddTag}
                confirmType='done'
                maxLength={20}
              />
            )}
          </View>
        </View>

        {/* 可见性 */}
        <View className={styles.field}>
          <Text className={styles.label}>可见性</Text>
          <View className={styles.visibilityOptions}>
            {VISIBILITY_OPTIONS.map(opt => (
              <View
                key={opt.value}
                className={`${styles.visibilityItem} ${visibility === opt.value ? styles.visibilityItemActive : ''}`}
                onClick={() => setVisibility(opt.value)}
              >
                <Text className={styles.visibilityLabel}>{opt.label}</Text>
                <Text className={styles.visibilityDesc}>{opt.desc}</Text>
              </View>
            ))}
          </View>
        </View>

        {/* 预计完成时间 */}
        <View className={styles.field}>
          <Text className={styles.label}>预计完成时间（可选）</Text>
          <Picker
            mode='date'
            value={expectedAt || ''}
            onChange={e => setExpectedAt(e.detail.value)}
          >
            <View className={styles.pickerValue}>
              <Text className={expectedAt ? styles.pickerText : styles.pickerPlaceholder}>
                {expectedAt || '选择日期'}
              </Text>
              <Text className={styles.pickerArrow}>›</Text>
            </View>
          </Picker>
        </View>

        <View style={{ height: '180rpx' }} />
      </ScrollView>

      {/* 底部提交栏 */}
      <View className={styles.bottomBar}>
        <View
          className={`${styles.cancelBtn}`}
          onClick={() => Taro.navigateBack()}
        >
          <Text className={styles.cancelBtnText}>取消</Text>
        </View>
        <View
          className={`${styles.submitBtn} ${(isUploading || submitting) ? styles.submitBtnDisabled : ''}`}
          onClick={handleSubmit}
        >
          <Text className={styles.submitBtnText}>
            {submitting ? '发布中...' : '发布心愿'}
          </Text>
        </View>
      </View>
    </View>
  )
}
