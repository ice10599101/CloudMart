import { useState, useEffect, useCallback, useRef } from 'react'
import { View, Text, Textarea, ScrollView, Image } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { fileApi } from '@/api/file'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import WishBGM from '@/components/WishBGM'
import type { WishDetail, WishFulfillmentSubmitResult } from '@/types'
import styles from './index.module.scss'

const MAX_MEDIA = 9
const MAX_FILE_SIZE = 10 * 1024 * 1024
const MAX_STORY = 5000
const MAX_FEELING = 1000

type UploadStatus = 'uploading' | 'success' | 'error'

interface UploadItem {
  id: string
  filePath: string
  url?: string
  progress: number
  status: UploadStatus
}

export default function WishFulfillmentPage() {
  const router = useRouter()
  const wishId = router.params.id ?? ''
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { user, isLoggedIn } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetail | null>(null)
  const [story, setStory] = useState('')
  const [feeling, setFeeling] = useState('')
  const [uploads, setUploads] = useState<UploadItem[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [submitResult, setSubmitResult] = useState<WishFulfillmentSubmitResult | null>(null)
  const uploadTasksRef = useRef<Map<string, Taro.UploadTask>>(new Map())

  useEffect(() => {
    if (!isLoggedIn) {
      Taro.redirectTo({ url: '/pages/login/index' })
      return
    }
    const fetchWish = async () => {
      try {
        const res = await wishApi.getWishDetail(wishId)
        if (res.data.success) {
          setWish(res.data.data)
        }
      } catch {
        // 错误已由 request 处理
      } finally {
        setLoading(false)
      }
    }
    fetchWish()
  }, [isLoggedIn, wishId])

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

      for (const file of result.tempFiles) {
        if (file.size > MAX_FILE_SIZE) {
          Taro.showToast({ title: '单张图片不能超过 10MB', icon: 'none' })
          continue
        }
        const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
        const item: UploadItem = { id, filePath: file.path, progress: 0, status: 'uploading' }
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

  /** 已填写内容时离开需二次确认（编辑器操作守则） */
  const handleCancel = async () => {
    if (story.trim() || uploadedUrls.length > 0) {
      const res = await Taro.showModal({
        title: '确认离开',
        content: '还愿故事尚未提交，离开后将丢失已填写的内容',
        confirmText: '放弃并离开',
        cancelText: '继续编辑',
      })
      if (!res.confirm) return
    }
    Taro.navigateBack()
  }

  const handleSubmit = async () => {
    if (!story.trim()) {
      Taro.showToast({ title: '请写下你的还愿故事', icon: 'none' })
      return
    }
    if (story.length > MAX_STORY) {
      Taro.showToast({ title: `故事不超过 ${MAX_STORY} 字符`, icon: 'none' })
      return
    }
    if (feeling.length > MAX_FEELING) {
      Taro.showToast({ title: `感悟不超过 ${MAX_FEELING} 字符`, icon: 'none' })
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

    const confirmRes = await Taro.showModal({
      title: '确认提交还愿',
      content: '提交后心愿状态将变为「已还愿」，不可再修改',
      confirmText: '确定提交',
      cancelText: '再想想',
    })
    if (!confirmRes.confirm) return

    setSubmitting(true)
    try {
      const res = await wishApi.submitFulfillment(wishId, {
        story: story.trim(),
        mediaUrls: uploadedUrls.length > 0 ? uploadedUrls : undefined,
        feeling: feeling.trim() || undefined,
      })
      if (res.data.success) {
        setSubmitResult(res.data.data)
      }
    } catch {
      // 错误已由 request 处理
    } finally {
      setSubmitting(false)
    }
  }

  const gotoDetail = () => {
    Taro.redirectTo({ url: `/pages/wishDetail/index?id=${wishId}` })
  }

  if (loading) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
        <CustomNavBar title='我要还愿' back />
        <View className={styles.loading}>
          <View className={styles.spinner} />
        </View>
      </View>
    )
  }

  if (!wish) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
        <CustomNavBar title='我要还愿' back />
        <View className={styles.invalid}>
          <Text className={styles.invalidText}>心愿不存在或已被删除</Text>
          <Text className={styles.invalidAction} onClick={() => Taro.navigateBack()}>返回</Text>
        </View>
      </View>
    )
  }

  const isAuthor = user?.id === wish.authorId
  const fulfillable = wish.status === 'ACTIVE' || wish.status === 'OVERDUE'

  if (!isAuthor || (!fulfillable && !submitResult)) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
        <CustomNavBar title='我要还愿' back />
        <View className={styles.invalid}>
          <Text className={styles.invalidText}>
            {!isAuthor ? '仅心愿作者可以还愿' : '当前状态不可还愿'}
          </Text>
          <Text className={styles.invalidAction} onClick={gotoDetail}>查看心愿</Text>
        </View>
      </View>
    )
  }

  // 提交成功：绽放仪式 + 奖励展示
  if (submitResult) {
    return (
      <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
        <CustomNavBar title='我要还愿' back />
        <View className={styles.ceremony}>
          <View className={styles.bloomCore}>
            <Text className={styles.bloomEmoji}>🌸</Text>
            {Array.from({ length: 12 }).map((_, i) => (
              <View
                key={i}
                style={{ position: 'absolute', left: '50%', top: '50%', transform: `rotate(${i * 30}deg)` }}
              >
                <View className={styles.bloomParticle} style={{ animationDelay: `${(i % 4) * 0.06}s` }} />
              </View>
            ))}
          </View>
          <Text className={styles.ceremonyTitle}>心愿绽放</Text>
          <Text className={styles.ceremonyText}>你的果实已经成熟，故事将照亮还在路上的人</Text>
          <View className={styles.rewardRow}>
            <Text className={styles.rewardStarlight}>✨ 星光 +{submitResult.starlightReward}</Text>
            {submitResult.badgeAwarded.map(badge => (
              <Text key={badge.id} className={styles.rewardBadge}>🏅 {badge.name}</Text>
            ))}
          </View>
          <View className={styles.ceremonyActions}>
            <View className={styles.primaryAction} onClick={gotoDetail}>
              <Text className={styles.primaryActionText}>查看还愿故事</Text>
            </View>
            <View
              className={styles.secondaryAction}
              onClick={() => Taro.redirectTo({ url: '/pages/myWishes/index' })}
            >
              <Text className={styles.secondaryActionText}>我的心愿</Text>
            </View>
          </View>
        </View>
        <WishBGM />
      </View>
    )
  }

  return (
    <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
      <CustomNavBar title='我要还愿' back />
      <ScrollView scrollY className={styles.scroll}>
        {/* 心愿标题 */}
        <View className={styles.wishHeader}>
          <Text className={styles.wishFruit}>🌸</Text>
          <Text className={styles.wishTitle}>{wish.title}</Text>
        </View>

        {/* 还愿故事 */}
        <View className={styles.field}>
          <Text className={styles.label}>还愿故事 <Text style={{ color: '#e94560' }}>*</Text></Text>
          <Textarea
            className={styles.textarea}
            placeholder='写下这段旅程的故事：如何开始、经历了什么、最终如何抵达……'
            value={story}
            onInput={e => setStory(e.detail.value)}
            maxlength={MAX_STORY}
            autoHeight
          />
          <Text className={styles.count}>{story.length}/{MAX_STORY}</Text>
        </View>

        {/* 完成照片 */}
        <View className={styles.field}>
          <Text className={styles.label}>完成照片（可选，最多 {MAX_MEDIA} 张）</Text>
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

        {/* 感悟 */}
        <View className={styles.field}>
          <Text className={styles.label}>感悟（可选）</Text>
          <Textarea
            className={styles.feelingTextarea}
            placeholder='这段旅程带给你最深的感悟是什么？'
            value={feeling}
            onInput={e => setFeeling(e.detail.value)}
            maxlength={MAX_FEELING}
            autoHeight
          />
          <Text className={styles.count}>{feeling.length}/{MAX_FEELING}</Text>
        </View>

        <Text className={styles.rewardHint}>提交后心愿将绽放为「🌸 绽放」果实，并获得 ✨ 星光奖励</Text>

        <View style={{ height: '180rpx' }} />
      </ScrollView>

      {/* 底部提交栏 */}
      <View className={styles.bottomBar}>
        <View className={styles.bottomCancelBtn} onClick={handleCancel}>
          <Text className={styles.bottomCancelBtnText}>取消</Text>
        </View>
        <View
          className={`${styles.submitBtn} ${(isUploading || submitting) ? styles.submitBtnDisabled : ''}`}
          onClick={handleSubmit}
        >
          <Text className={styles.submitBtnText}>
            {submitting ? '提交中...' : '提交还愿'}
          </Text>
        </View>
      </View>
      <WishBGM />
    </View>
  )
}
