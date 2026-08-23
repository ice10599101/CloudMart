import { useState, useEffect } from 'react'
import { View, Text, Input, Textarea, ScrollView, Image, Picker } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi } from '@/api/wish'
import { WISH_THEME_STYLE } from '@/styles/wish-theme'
import { useAuthStore } from '@/store/auth'
import { API_BASE } from '@/utils/request'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { getTimezoneId, localToUtcIso, reportTimezoneIfNeeded } from '@/utils/wish-timezone'
import styles from './index.module.scss'

const MAX_TITLE = 100
const MAX_CONTENT = 5000
const MAX_MEDIA = 9
const MAX_FILE_SIZE = 10 * 1024 * 1024

function formatToday(): string {
    const now = new Date()
    const m = String(now.getMonth() + 1).padStart(2, '0')
    const d = String(now.getDate()).padStart(2, '0')
    return `${now.getFullYear()}-${m}-${d}`
}

export default function CapsuleCreatePage() {
    const { statusBarHeight, navBarHeight } = getNavBarMetrics()
    const { isLoggedIn } = useAuthStore()
    const [title, setTitle] = useState('')
    const [content, setContent] = useState('')
    const [mediaUrls, setMediaUrls] = useState<string[]>([])
    const [uploadingCount, setUploadingCount] = useState(0)
    const [openDate, setOpenDate] = useState('')
    const [openTime, setOpenTime] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [sealed, setSealed] = useState(false)

    useEffect(() => {
        if (!isLoggedIn) {
            Taro.redirectTo({ url: '/pages/login/index' })
            return
        }
        reportTimezoneIfNeeded()
    }, [isLoggedIn])

    const performUpload = (filePath: string) => {
        setUploadingCount((n) => n + 1)
        Taro.uploadFile({
            url: `${API_BASE}/file/upload`,
            filePath,
            name: 'file',
            header: { Authorization: `Bearer ${Taro.getStorageSync('access_token')}` },
            success: (res) => {
                try {
                    const data = JSON.parse(res.data)
                    if (data.success && data.data?.url) {
                        setMediaUrls((prev) => [...prev, data.data.url as string])
                    } else {
                        Taro.showToast({ title: '图片上传失败', icon: 'none' })
                    }
                } catch {
                    Taro.showToast({ title: '图片上传失败', icon: 'none' })
                }
            },
            fail: () => {
                Taro.showToast({ title: '图片上传失败', icon: 'none' })
            },
            complete: () => {
                setUploadingCount((n) => n - 1)
            },
        })
    }

    const handleChooseImage = async () => {
        if (mediaUrls.length >= MAX_MEDIA) {
            Taro.showToast({ title: `最多上传 ${MAX_MEDIA} 张图片`, icon: 'none' })
            return
        }
        try {
            const result = await Taro.chooseImage({
                count: MAX_MEDIA - mediaUrls.length,
                sourceType: ['album', 'camera'],
                sizeType: ['compressed'],
            })
            for (const file of result.tempFiles) {
                if (file.size > MAX_FILE_SIZE) {
                    Taro.showToast({ title: '单张图片不能超过 10MB', icon: 'none' })
                    continue
                }
                performUpload(file.path)
            }
        } catch {
            // 用户取消选择
        }
    }

    const handleRemoveMedia = (url: string) => {
        setMediaUrls((prev) => prev.filter((u) => u !== url))
    }

    const handleSubmit = async () => {
        if (!title.trim()) {
            Taro.showToast({ title: '请给胶囊起个名字', icon: 'none' })
            return
        }
        if (title.length > MAX_TITLE) {
            Taro.showToast({ title: `标题不超过 ${MAX_TITLE} 字`, icon: 'none' })
            return
        }
        if (!content.trim()) {
            Taro.showToast({ title: '写下想封存的内容', icon: 'none' })
            return
        }
        if (content.length > MAX_CONTENT) {
            Taro.showToast({ title: `内容不超过 ${MAX_CONTENT} 字`, icon: 'none' })
            return
        }
        if (uploadingCount > 0) {
            Taro.showToast({ title: '请等待图片上传完成', icon: 'none' })
            return
        }
        if (!openDate || !openTime) {
            Taro.showToast({ title: '请选择开启时间', icon: 'none' })
            return
        }
        const openAt = localToUtcIso(openDate, openTime)
        if (new Date(openAt).getTime() <= Date.now()) {
            Taro.showToast({ title: '开启时间必须在未来', icon: 'none' })
            return
        }

        setSubmitting(true)
        try {
            const res = await wishApi.createCapsule({
                title: title.trim(),
                content: content.trim(),
                mediaUrls: mediaUrls.length > 0 ? mediaUrls : undefined,
                openAt,
                openAtTz: getTimezoneId(),
            })
            if (res.data.success) {
                // 封存仪式：火漆落下 → 跳转列表
                setSealed(true)
                Taro.vibrateShort({ type: 'medium' })
                setTimeout(() => {
                    Taro.redirectTo({ url: '/pages/capsuleList/index' })
                }, 1800)
            }
        } catch {
            // 错误已由 request 处理
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <View style={{ ...WISH_THEME_STYLE, paddingTop: `${statusBarHeight + navBarHeight}rpx`, minHeight: '100vh' }}>
            <CustomNavBar title='封存时间胶囊' back />
            <ScrollView scrollY className={styles.scroll}>
                {/* 标题 */}
                <View className={styles.field}>
                    <Text className={styles.label}>胶囊标题 <Text style={{ color: '#e94560' }}>*</Text></Text>
                    <Input
                        className={styles.input}
                        placeholder='如：写给一年后的自己'
                        value={title}
                        onInput={(e) => setTitle(e.detail.value)}
                        maxlength={MAX_TITLE}
                    />
                    <Text className={styles.count}>{title.length}/{MAX_TITLE}</Text>
                </View>

                {/* 内容 */}
                <View className={styles.field}>
                    <Text className={styles.label}>封存内容（开启前不可见）<Text style={{ color: '#e94560' }}>*</Text></Text>
                    <Textarea
                        className={styles.textarea}
                        placeholder='此刻的你想对未来的自己说什么？愿望、心情、约定……封存后到期前任何人无法查看'
                        value={content}
                        onInput={(e) => setContent(e.detail.value)}
                        maxlength={MAX_CONTENT}
                        autoHeight
                    />
                    <Text className={styles.count}>{content.length}/{MAX_CONTENT}</Text>
                </View>

                {/* 封存照片 */}
                <View className={styles.field}>
                    <Text className={styles.label}>封存照片（可选，最多 {MAX_MEDIA} 张）</Text>
                    <View className={styles.uploadArea}>
                        {mediaUrls.map((url) => (
                            <View key={url} className={styles.uploadItem}>
                                <Image className={styles.uploadPreview} src={url} mode='aspectFill' />
                                <View className={styles.removeBtn} onClick={() => handleRemoveMedia(url)}>
                                    <Text className={styles.removeIcon}>×</Text>
                                </View>
                            </View>
                        ))}
                        {mediaUrls.length < MAX_MEDIA && (
                            <View className={styles.uploadTrigger} onClick={handleChooseImage}>
                                <Text className={styles.plusIcon}>{uploadingCount > 0 ? '…' : '+'}</Text>
                            </View>
                        )}
                    </View>
                </View>

                {/* 开启日期 */}
                <View className={styles.field}>
                    <Text className={styles.label}>开启日期 <Text style={{ color: '#e94560' }}>*</Text></Text>
                    <Picker
                        mode='date'
                        start={formatToday()}
                        value={openDate || formatToday()}
                        onChange={(e) => setOpenDate(e.detail.value)}
                    >
                        <View className={styles.pickerValue}>
                            <Text className={openDate ? styles.pickerText : styles.pickerPlaceholder}>
                                {openDate || '选择未来某一天'}
                            </Text>
                            <Text className={styles.pickerArrow}>›</Text>
                        </View>
                    </Picker>
                </View>

                {/* 开启时刻 */}
                <View className={styles.field}>
                    <Text className={styles.label}>开启时刻 <Text style={{ color: '#e94560' }}>*</Text></Text>
                    <Picker
                        mode='time'
                        value={openTime || '12:00'}
                        onChange={(e) => setOpenTime(e.detail.value)}
                    >
                        <View className={styles.pickerValue}>
                            <Text className={openTime ? styles.pickerText : styles.pickerPlaceholder}>
                                {openTime || '选择那天的时间'}
                            </Text>
                            <Text className={styles.pickerArrow}>›</Text>
                        </View>
                    </Picker>
                </View>

                <Text className={styles.rewardHint}>
                    到期判定按 UTC，跨时区旅行不影响；封存后到期前无法查看内容，也不可修改
                </Text>

                <View style={{ height: '200rpx' }} />
            </ScrollView>

            {/* 封存仪式遮罩：火漆盖章 */}
            {sealed && (
                <View className={styles.sealOverlay}>
                    <View className={styles.sealCore}>
                        <Text className={styles.sealWax}>🔴</Text>
                        <Text className={styles.sealText}>封存成功</Text>
                    </View>
                    <Text className={styles.sealSub}>它将在未来等你</Text>
                </View>
            )}

            {/* 底部提交栏 */}
            <View className={styles.bottomBar}>
                <View className={styles.cancelBtn} onClick={() => Taro.navigateBack()}>
                    <Text className={styles.cancelBtnText}>取消</Text>
                </View>
                <View
                    className={`${styles.submitBtn} ${uploadingCount > 0 || submitting ? styles.submitBtnDisabled : ''}`}
                    onClick={handleSubmit}
                >
                    <Text className={styles.submitBtnText}>
                        {submitting ? '封存中...' : '🔒 封存胶囊'}
                    </Text>
                </View>
            </View>
        </View>
    )
}
