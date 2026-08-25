import { View, Text, ScrollView, TouchableOpacity, Image, TextInput, Alert, ActivityIndicator, Modal } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import * as ImagePicker from 'expo-image-picker'
import * as Haptics from 'expo-haptics'
import { wishApi } from '@/api/wish'
import { fileApi } from '@/api/file'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import WishBGM from '@/components/WishBGM'
import { WishColors } from '@/constants/wish-theme'
import { getTimezoneId, localToUtcIso, reportTimezoneIfNeeded } from '@/utils/wish-timezone'
import { ensureNotificationPermission, scheduleCapsuleReminder } from '@/utils/capsule-notifications'
import type { CapsuleItem } from '@/types'

const MAX_TITLE = 100
const MAX_CONTENT = 5000
const MAX_MEDIA = 9

interface UploadItem {
    key: string
    base64: string
    uri: string
    url?: string
    status: 'uploading' | 'success' | 'error'
}

/** 快捷时长 + 自定义（日期滚轮按天近似：7/30/182/365 天后 12:00 本地时间） */
const DURATION_OPTIONS = [
    { label: '7 天', days: 7 },
    { label: '30 天', days: 30 },
    { label: '半年', days: 182 },
    { label: '一年', days: 365 },
]

function defaultOpenAt(days: number): { dateStr: string; timeStr: string } {
    const target = new Date(Date.now() + days * 24 * 60 * 60 * 1000)
    target.setHours(12, 0, 0, 0)
    const pad = (n: number) => String(n).padStart(2, '0')
    return {
        dateStr: `${target.getFullYear()}-${pad(target.getMonth() + 1)}-${pad(target.getDate())}`,
        timeStr: '12:00',
    }
}

export default function CapsuleCreateScreen() {
    const insets = useSafeAreaInsets()
    const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
    const [title, setTitle] = useState('')
    const [content, setContent] = useState('')
    const [uploads, setUploads] = useState<UploadItem[]>([])
    const [durationDays, setDurationDays] = useState<number | null>(null)
    const [customDate, setCustomDate] = useState('')
    const [customTime, setCustomTime] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const [sealedCapsule, setSealedCapsule] = useState<CapsuleItem | null>(null)

    useEffect(() => {
        if (!isLoggedIn) {
            router.replace('/login')
            return
        }
        reportTimezoneIfNeeded()
        // 提前申请推送权限（拒绝则静默降级，不阻断创建）
        ensureNotificationPermission()
    }, [isLoggedIn])

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
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [uploads.length])

    const retryUpload = (item: UploadItem) => {
        setUploads((prev) => prev.map((u) => (u.key === item.key ? { ...u, status: 'uploading' } : u)))
        uploadItem(item.key, item.base64)
    }

    const removeUpload = (key: string) => {
        setUploads((prev) => prev.filter((u) => u.key !== key))
    }

    const resolveOpenAt = (): { openAt: string; error: string | null } => {
        if (durationDays !== null) {
            const { dateStr, timeStr } = defaultOpenAt(durationDays)
            return { openAt: localToUtcIso(dateStr, timeStr), error: null }
        }
        if (!customDate || !customTime) {
            return { openAt: '', error: '请选择开启时间' }
        }
        const openAt = localToUtcIso(customDate, customTime)
        if (new Date(openAt).getTime() <= Date.now()) {
            return { openAt: '', error: '开启时间必须在未来' }
        }
        return { openAt, error: null }
    }

    const submit = async () => {
        if (!title.trim()) return Alert.alert('提示', '请给胶囊起个名字')
        if (title.length > MAX_TITLE) return Alert.alert('提示', `标题不超过 ${MAX_TITLE} 字`)
        if (!content.trim()) return Alert.alert('提示', '写下想封存的内容')
        if (content.length > MAX_CONTENT) return Alert.alert('提示', `内容不超过 ${MAX_CONTENT} 字`)
        if (uploads.some((u) => u.status === 'uploading')) return Alert.alert('提示', '图片还在上传中，请稍候')

        const { openAt, error } = resolveOpenAt()
        if (error) return Alert.alert('提示', error)

        setSubmitting(true)
        try {
            const mediaUrls = uploads.filter((u) => u.status === 'success' && u.url).map((u) => u.url!)
            const res = await wishApi.createCapsule({
                title: title.trim(),
                content: content.trim(),
                mediaUrls: mediaUrls.length > 0 ? mediaUrls : undefined,
                openAt,
                openAtTz: getTimezoneId(),
            })
            const capsule = res.data?.data
            if (capsule) {
                // 到期本地推送（失败静默降级）
                scheduleCapsuleReminder(capsule).catch(() => undefined)
                // 封印仪式：Haptics + 火漆遮罩
                Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => undefined)
                setSealedCapsule(capsule)
            }
        } catch {
            Alert.alert('失败', '封存失败，请稍后重试')
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
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
                    封存时间胶囊
                </Text>
            </View>

            <ScrollView contentContainerStyle={{ padding: Spacing.md, paddingBottom: insets.bottom + 100 }}>
                <FieldLabel label={`胶囊标题 (${title.length}/${MAX_TITLE})`} required />
                <StyledTextInput value={title} onChangeText={setTitle} placeholder="如：写给一年后的自己" maxLength={MAX_TITLE} />

                <FieldLabel label={`封存内容 (${content.length}/${MAX_CONTENT})`} required />
                <StyledTextInput
                    value={content}
                    onChangeText={setContent}
                    placeholder="此刻的你想对未来的自己说什么？愿望、心情、约定……封存后到期前任何人无法查看"
                    multiline
                    numberOfLines={8}
                    style={{ height: 180, textAlignVertical: 'top' }}
                />

                <FieldLabel label={`封存照片 (${uploads.length}/${MAX_MEDIA})`} />
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

                <FieldLabel label="开启时间" required />
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.sm }}>
                    {DURATION_OPTIONS.map((option) => (
                        <TouchableOpacity
                            key={option.days}
                            onPress={() => {
                                setDurationDays(durationDays === option.days ? null : option.days)
                                setCustomDate('')
                                setCustomTime('')
                            }}
                            style={{
                                paddingHorizontal: Spacing.md,
                                paddingVertical: 6,
                                borderRadius: 20,
                                borderWidth: 1,
                                borderColor: durationDays === option.days ? WishColors.accentCyan : WishColors.border,
                                backgroundColor: durationDays === option.days ? 'rgba(0,212,255,0.12)' : 'transparent',
                            }}
                        >
                            <Text
                                style={{
                                    fontSize: FontSize.sm,
                                    color: durationDays === option.days ? WishColors.accentCyan : WishColors.textSecondary,
                                }}
                            >
                                {option.label}
                            </Text>
                        </TouchableOpacity>
                    ))}
                </View>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.sm, marginBottom: Spacing.sm }}>
                    或自定义日期（YYYY-MM-DD）与时刻（HH:mm）
                </Text>
                <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
                    <StyledTextInput
                        value={customDate}
                        onChangeText={(text) => {
                            setCustomDate(text)
                            setDurationDays(null)
                        }}
                        placeholder="2027-01-01"
                        style={{ flex: 1 }}
                    />
                    <StyledTextInput
                        value={customTime}
                        onChangeText={(text) => {
                            setCustomTime(text)
                            setDurationDays(null)
                        }}
                        placeholder="12:00"
                        style={{ flex: 1 }}
                    />
                </View>

                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.md }}>
                    到期判定按 UTC，跨时区旅行不影响；封存后到期前无法查看内容，也不可修改
                </Text>
            </ScrollView>

            {/* 封印仪式遮罩：火漆盖章 */}
            <Modal visible={sealedCapsule !== null} transparent animationType="fade">
                <View
                    style={{
                        flex: 1,
                        backgroundColor: 'rgba(5,8,20,0.94)',
                        justifyContent: 'center',
                        alignItems: 'center',
                    }}
                >
                    <Text style={{ fontSize: 64 }}>🔴</Text>
                    <Text style={{ fontSize: FontSize.xl, fontWeight: '700', color: '#fff', marginTop: Spacing.md }}>封存成功</Text>
                    <Text style={{ fontSize: FontSize.md, color: 'rgba(255,255,255,0.6)', marginTop: Spacing.sm }}>它将在未来等你</Text>
                    <TouchableOpacity
                        onPress={() => router.replace('/capsule/list')}
                        style={{
                            marginTop: Spacing.xl,
                            paddingHorizontal: Spacing.xl,
                            paddingVertical: Spacing.sm + 4,
                            borderRadius: 28,
                            borderWidth: 1,
                            borderColor: 'rgba(255,255,255,0.3)',
                        }}
                    >
                        <Text style={{ color: '#fff', fontSize: FontSize.md }}>查看我的胶囊</Text>
                    </TouchableOpacity>
                </View>
            </Modal>

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
                        backgroundColor: submitting ? 'rgba(78,205,196,0.5)' : '#2a9d8f',
                        alignItems: 'center',
                    }}
                >
                    {submitting ? (
                        <ActivityIndicator size="small" color="#fff" />
                    ) : (
                        <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#fff' }}>🔒 封存胶囊</Text>
                    )}
                </TouchableOpacity>
            </View>

            <WishBGM />
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
                             style,
                         }: {
    value: string
    onChangeText: (text: string) => void
    placeholder: string
    multiline?: boolean
    numberOfLines?: number
    maxLength?: number
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
