import { View, Text, ScrollView, TouchableOpacity, Image, TextInput, Alert, ActivityIndicator } from 'react-native'
import { useState, useEffect, useCallback, useRef } from 'react'
import { Animated, Easing } from 'react-native'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import * as ImagePicker from 'expo-image-picker'
import { wishApi } from '@/api/wish'
import { fileApi } from '@/api/file'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import WishBGM from '@/components/WishBGM'
import type { WishDetail, WishFulfillmentSubmitResult } from '@/types'

const MAX_MEDIA = 9
const MAX_STORY = 5000
const MAX_FEELING = 1000

interface UploadItem {
  key: string
  base64: string
  uri: string
  url?: string
  status: 'uploading' | 'success' | 'error'
}

/** 绽放粒子角度集合（12 粒均匀圆周分布，与 WEB/Mobile 端仪式感节奏一致） */
const PARTICLE_ANGLES = Array.from({ length: 12 }, (_, i) => i * 30)

/** 绽放仪式动效：emoji 弹出 + 粒子炸裂 + 光晕扩散 */
function BloomCeremony({ starlight, badges }: { starlight: number; badges: { id: number; name: string }[] }) {
  const emojiScale = useRef(new Animated.Value(0)).current
  const emojiOpacity = useRef(new Animated.Value(0)).current
  const ringScale = useRef(new Animated.Value(0.4)).current
  const ringOpacity = useRef(new Animated.Value(0.8)).current
  const textOpacity = useRef(new Animated.Value(0)).current

  useEffect(() => {
    Animated.parallel([
      Animated.spring(emojiScale, {
        toValue: 1,
        friction: 4,
        tension: 40,
        useNativeDriver: true,
      }),
      Animated.timing(emojiOpacity, {
        toValue: 1,
        duration: 300,
        easing: Easing.out(Easing.ease),
        useNativeDriver: true,
      }),
      Animated.timing(ringScale, {
        toValue: 1.6,
        duration: 900,
        easing: Easing.out(Easing.ease),
        useNativeDriver: true,
      }),
      Animated.timing(ringOpacity, {
        toValue: 0,
        duration: 900,
        easing: Easing.out(Easing.ease),
        useNativeDriver: true,
      }),
      Animated.timing(textOpacity, {
        toValue: 1,
        duration: 600,
        delay: 400,
        useNativeDriver: true,
      }),
    ]).start()
  }, [emojiScale, emojiOpacity, ringScale, ringOpacity, textOpacity])

  return (
    <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', padding: Spacing.lg }}>
      <View style={{ width: 160, height: 160, justifyContent: 'center', alignItems: 'center' }}>
        <Animated.View
          style={{
            position: 'absolute',
            width: 160,
            height: 160,
            borderRadius: 80,
            borderWidth: 2,
            borderColor: 'rgba(255,215,0,0.6)',
            transform: [{ scale: ringScale }],
            opacity: ringOpacity,
          }}
        />
        {PARTICLE_ANGLES.map((angle) => {
          const radian = (angle * Math.PI) / 180
          const distance = 74
          return (
            <BloomParticle key={angle} x={Math.cos(radian) * distance} y={Math.sin(radian) * distance} />
          )
        })}
        <Animated.Text
          style={{
            fontSize: 56,
            transform: [{ scale: emojiScale }],
            opacity: emojiOpacity,
          }}
        >
          🌸
        </Animated.Text>
      </View>

      <Animated.View style={{ alignItems: 'center', opacity: textOpacity, marginTop: Spacing.lg }}>
        <Text style={{ fontSize: 22, fontWeight: '700', color: WishColors.text }}>心愿绽放</Text>
        <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary, marginTop: Spacing.sm, textAlign: 'center', lineHeight: 22 }}>
          你的果实已经成熟，故事将照亮还在路上的人
        </Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: Spacing.sm, marginTop: Spacing.lg }}>
          <View style={{ paddingHorizontal: Spacing.md, paddingVertical: 4, borderRadius: 14, backgroundColor: 'rgba(255,215,0,0.15)' }}>
            <Text style={{ fontSize: FontSize.sm, color: WishColors.accentGold }}>✨ 星光 +{starlight}</Text>
          </View>
          {badges.map((badge) => (
            <View key={badge.id} style={{ paddingHorizontal: Spacing.md, paddingVertical: 4, borderRadius: 14, backgroundColor: 'rgba(147,112,219,0.2)' }}>
              <Text style={{ fontSize: FontSize.sm, color: WishColors.accentPurple }}>🏅 {badge.name}</Text>
            </View>
          ))}
        </View>
      </Animated.View>
    </View>
  )
}

/** 单个绽放粒子（从中心向外炸裂） */
function BloomParticle({ x, y }: { x: number; y: number }) {
  const translateValue = useRef(new Animated.Value(0)).current
  const opacityValue = useRef(new Animated.Value(0)).current

  useEffect(() => {
    Animated.parallel([
      Animated.timing(translateValue, {
        toValue: 1,
        duration: 900,
        easing: Easing.out(Easing.ease),
        useNativeDriver: true,
      }),
      Animated.sequence([
        Animated.timing(opacityValue, {
          toValue: 1,
          duration: 270,
          useNativeDriver: true,
        }),
        Animated.timing(opacityValue, {
          toValue: 0,
          duration: 630,
          useNativeDriver: true,
        }),
      ]),
    ]).start()
  }, [translateValue, opacityValue])

  return (
    <Animated.View
      style={{
        position: 'absolute',
        width: 8,
        height: 8,
        borderRadius: 4,
        backgroundColor: '#ffd700',
        opacity: opacityValue,
        transform: [
          { translateX: translateValue.interpolate({ inputRange: [0, 1], outputRange: [0, x] }) },
          { translateY: translateValue.interpolate({ inputRange: [0, 1], outputRange: [0, y] }) },
          { scale: translateValue.interpolate({ inputRange: [0, 1], outputRange: [0.4, 1] }) },
        ],
      }}
    />
  )
}

export default function WishFulfillmentScreen() {
  const insets = useSafeAreaInsets()
  const params = useLocalSearchParams<{ id?: string }>()
  const wishId = Number(params.id)
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [loading, setLoading] = useState(true)
  const [wish, setWish] = useState<WishDetail | null>(null)
  const [story, setStory] = useState('')
  const [feeling, setFeeling] = useState('')
  const [uploads, setUploads] = useState<UploadItem[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [submitResult, setSubmitResult] = useState<WishFulfillmentSubmitResult | null>(null)

  useEffect(() => {
    if (!isLoggedIn) {
      router.replace('/login')
      return
    }
    const fetchWish = async () => {
      try {
        const res = await wishApi.getWishDetail(wishId)
        if (res.data?.success) {
          setWish(res.data.data)
        }
      } catch {
        // 错误已由 request 拦截器处理
      } finally {
        setLoading(false)
      }
    }
    fetchWish()
  }, [isLoggedIn, wishId])

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

  const validate = (): string | null => {
    if (!story.trim()) return '请写下你的还愿故事'
    if (story.length > MAX_STORY) return `还愿故事不能超过 ${MAX_STORY} 字`
    if (feeling.length > MAX_FEELING) return `感悟不能超过 ${MAX_FEELING} 字`
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
      const res = await wishApi.submitFulfillment(wishId, {
        story: story.trim(),
        mediaUrls: mediaUrls.length > 0 ? mediaUrls : undefined,
        feeling: feeling.trim() || undefined,
      })
      if (res.data?.success) {
        setSubmitResult(res.data.data)
      }
    } catch {
      // 错误已由 request 拦截器处理
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: WishColors.bgBase, justifyContent: 'center', alignItems: 'center', paddingTop: insets.top }}>
        <ActivityIndicator size="large" color={WishColors.primary} />
      </View>
    )
  }

  // 绽放仪式：提交成功后的动画与奖励展示
  if (submitResult) {
    return (
      <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
        <BloomCeremony starlight={submitResult.starlightReward} badges={submitResult.badgeAwarded} />
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
            onPress={() => router.replace(`/wish-detail?id=${wishId}`)}
            style={{
              paddingVertical: Spacing.md,
              borderRadius: 28,
              backgroundColor: WishColors.primary,
              alignItems: 'center',
            }}
          >
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#fff' }}>查看心愿</Text>
          </TouchableOpacity>
        </View>
      </View>
    )
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
        <TouchableOpacity
          onPress={() =>
            Alert.alert('确认离开', '离开后已填写的内容将丢失，确定离开吗？', [
              { text: '继续编辑', style: 'cancel' },
              { text: '离开', style: 'destructive', onPress: () => router.back() },
            ])
          }
        >
          <Text style={{ fontSize: FontSize.lg, color: WishColors.textSecondary }}>‹ 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text, marginLeft: Spacing.md }}>
          还愿 · 绽放果实
        </Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: Spacing.md, paddingBottom: insets.bottom + 100 }}>
        {/* 心愿标题提示 */}
        {wish && (
          <View
            style={{
              padding: Spacing.md,
              borderRadius: BorderRadius.lg,
              backgroundColor: 'rgba(15,52,96,0.35)',
              borderWidth: 1,
              borderColor: 'rgba(255,107,107,0.35)',
            }}
          >
            <Text style={{ fontSize: FontSize.sm, color: WishColors.textTertiary }}>即将还愿的心愿</Text>
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: WishColors.text, marginTop: Spacing.xs }}>
              {wish.title}
            </Text>
          </View>
        )}

        {/* 还愿故事 */}
        <FieldLabel label={`还愿故事 (${story.length}/${MAX_STORY})`} required />
        <TextInput
          value={story}
          onChangeText={setStory}
          placeholder="写下这段旅程的故事：它是如何实现的？有过哪些难忘的瞬间..."
          placeholderTextColor={WishColors.textTertiary}
          multiline
          textAlignVertical="top"
          maxLength={MAX_STORY}
          style={{
            height: 180,
            padding: Spacing.md,
            borderRadius: BorderRadius.lg,
            backgroundColor: WishColors.bgContainer,
            borderWidth: 1,
            borderColor: WishColors.border,
            color: WishColors.text,
            fontSize: FontSize.md,
            lineHeight: 24,
          }}
        />

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
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
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
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
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

        {/* 感悟 */}
        <FieldLabel label={`感悟 (${feeling.length}/${MAX_FEELING})`} />
        <TextInput
          value={feeling}
          onChangeText={setFeeling}
          placeholder="这一路走来想对自己或大家说的话（可选）"
          placeholderTextColor={WishColors.textTertiary}
          multiline
          textAlignVertical="top"
          maxLength={MAX_FEELING}
          style={{
            height: 100,
            padding: Spacing.md,
            borderRadius: BorderRadius.lg,
            backgroundColor: WishColors.bgContainer,
            borderWidth: 1,
            borderColor: WishColors.border,
            color: WishColors.text,
            fontSize: FontSize.md,
          }}
        />
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
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: '#fff' }}>🌸 绽放这颗心愿</Text>
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
