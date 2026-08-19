import { View, Text, FlatList, TouchableOpacity, TextInput, ActivityIndicator, Alert } from 'react-native'
import { useCallback, useEffect, useRef, useState } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import * as Linking from 'expo-linking'
import { wishApi } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import type { AiResource } from '@/types'

/** AI 数据处理协议版本（协议文本管理模块上线前为静态版本） */
const AI_CONSENT_VERSION = 'v1.0'
const DAILY_LIMIT = 10

interface ChatMessage {
  id: number | string
  role: 'USER' | 'ASSISTANT'
  content: string
  resources?: AiResource[]
  isCrisis?: boolean
}

/** 从 axios 异常体提取业务错误信封 */
function extractBusinessError(error: unknown): { code?: string; message?: string } | undefined {
  return (error as { response?: { data?: { error?: { code?: string; message?: string } } } })
    ?.response?.data?.error
}

export default function TreeHoleScreen() {
  const insets = useSafeAreaInsets()
  const params = useLocalSearchParams<{ id?: string }>()
  const wishId = Number(params.id)
  const user = useAuthStore((s) => s.user)

  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(true)
  const listRef = useRef<FlatList<ChatMessage>>(null)
  const pendingMessageRef = useRef<string | null>(null)
  const sentTodayRef = useRef(0)

  useEffect(() => {
    const loadHistory = async () => {
      if (!user) {
        setLoadingHistory(false)
        return
      }
      try {
        const res = await wishApi.listAiConversations({ pageSize: 50 })
        if (res.data?.success && res.data.data.length > 0) {
          setMessages(
            [...res.data.data]
              .reverse()
              .map((item) => ({
                id: item.id,
                role: item.role,
                content: item.content,
                resources: item.resources,
              })),
          )
        }
      } catch {
        // 历史加载失败不阻断倾诉主流程
      } finally {
        setLoadingHistory(false)
      }
    }
    loadHistory()
  }, [user])

  const toastByCode = (code?: string, fallbackMessage?: string) => {
    const title =
      code === 'WISH_AI_RATE_LIMITED'
        ? '今日倾诉次数已达上限，明天再来好吗'
        : code === 'WISH_AI_UNAVAILABLE'
          ? '树洞守护者暂时不在，请稍后再来'
          : fallbackMessage || '发送失败，请稍后重试'
    Alert.alert('树洞', title)
  }

  /** 同意 AI 数据处理协议后自动发送待发消息 */
  const handleConsentAgree = useCallback(async () => {
    try {
      const res = await wishApi.grantConsent({
        consentType: 'AI_DATA_PROCESSING',
        version: AI_CONSENT_VERSION,
        action: 'GRANT',
      })
      if (res.data?.success) {
        const pending = pendingMessageRef.current
        pendingMessageRef.current = null
        if (pending) {
          setInput('')
          await doSend(pending)
        }
      } else {
        toastByCode(res.data.error?.code, res.data.error?.message)
      }
    } catch (error) {
      toastByCode(extractBusinessError(error)?.code, extractBusinessError(error)?.message)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wishId])

  /** 弹出 AI 数据处理协议确认（原生 Alert） */
  const showConsentDialog = (pending: string) => {
    pendingMessageRef.current = pending
    Alert.alert(
      'AI 数据处理协议',
      '在使用树洞 AI 陪伴前，请了解并同意：\n\n'
        + '· 你的倾诉内容将在脱敏处理后发送给 AI 服务（通义千问）生成回复\n'
        + '· 系统会自动移除消息中的手机号、邮箱、身份证号等个人信息\n'
        + '· 对话记录仅你自己可见，可随时联系客服删除\n'
        + '· 若你流露出伤害自己的念头，我们会优先为你提供专业心理援助渠道\n'
        + '· 你可以随时撤回本同意',
      [
        { text: '暂不使用', style: 'cancel', onPress: () => (pendingMessageRef.current = null) },
        { text: '同意并继续', onPress: handleConsentAgree },
      ],
    )
  }

  const doSend = useCallback(
    async (text: string) => {
      if (!text.trim() || sending) return
      if (!user) {
        router.push('/login')
        return
      }

      // 同意状态前置检查，未同意弹协议（后端 403 也会兜底触发）
      try {
        const statusRes = await wishApi.getConsentStatus()
        if (statusRes.data?.success && !statusRes.data.data.granted) {
          showConsentDialog(text)
          return
        }
      } catch {
        // 查询失败继续发送，由后端兜底
      }

      setInput('')
      setMessages((prev) => [...prev, { id: `local-${Date.now()}`, role: 'USER', content: text }])
      setSending(true)
      try {
        const res = await wishApi.sendTreeHoleMessage(wishId, text)
        if (res.data?.success) {
          const { reply, sentimentScore, resources } = res.data.data
          sentTodayRef.current += 1
          setMessages((prev) => [
            ...prev,
            {
              id: `reply-${Date.now()}`,
              role: 'ASSISTANT',
              content: reply,
              resources,
              isCrisis:
                sentimentScore !== null &&
                sentimentScore <= -0.9 &&
                resources.some((r) => r.type === 'HOTLINE'),
            },
          ])
          const remaining = DAILY_LIMIT - sentTodayRef.current
          if (remaining > 0 && remaining <= 3) {
            Alert.alert('树洞', `今天还可以倾诉 ${remaining} 次`)
          }
        } else {
          const code = res.data.error?.code
          if (code === 'WISH_CONSENT_REQUIRED') {
            showConsentDialog(text)
          } else {
            toastByCode(code, res.data.error?.message)
          }
        }
      } catch (error) {
        const bizError = extractBusinessError(error)
        if (bizError?.code === 'WISH_CONSENT_REQUIRED') {
          showConsentDialog(text)
        } else {
          toastByCode(bizError?.code, bizError?.message)
          // 发送失败移除本地用户消息
          setMessages((prev) =>
            prev.filter((m) => !(String(m.id).startsWith('local-') && m.content === text)),
          )
        }
      } finally {
        setSending(false)
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [sending, user, wishId],
  )

  const handleSend = () => doSend(input.trim())

  /** 热线拨号 / 文章链接打开 */
  const handleResourcePress = async (res: AiResource) => {
    try {
      if (res.type === 'HOTLINE') {
        await Linking.openURL(res.url)
      } else {
        await Linking.openURL(res.url)
      }
    } catch {
      Alert.alert('提示', `请手动访问：${res.url}`)
    }
  }

  const renderItem = ({ item }: { item: ChatMessage }) =>
    item.role === 'USER' ? (
      <View style={{ flexDirection: 'row', justifyContent: 'flex-end', marginBottom: Spacing.md }}>
        <View
          style={{
            maxWidth: '75%',
            padding: Spacing.md,
            borderTopLeftRadius: BorderRadius.xl,
            borderTopRightRadius: BorderRadius.xl,
            borderBottomLeftRadius: 4,
            borderBottomRightRadius: BorderRadius.xl,
            backgroundColor: WishColors.accentPurple,
          }}
        >
          <Text style={{ fontSize: FontSize.sm, lineHeight: 22, color: '#fff' }}>{item.content}</Text>
        </View>
      </View>
    ) : (
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: Spacing.sm, marginBottom: Spacing.md }}>
        <View
          style={{
            width: 36,
            height: 36,
            borderRadius: 18,
            backgroundColor: WishColors.bgContainer,
            borderWidth: 1,
            borderColor: WishColors.border,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Text style={{ fontSize: 18 }}>🌙</Text>
        </View>
        <View
          style={{
            maxWidth: '75%',
            padding: Spacing.md,
            borderTopLeftRadius: BorderRadius.xl,
            borderTopRightRadius: BorderRadius.xl,
            borderBottomLeftRadius: 4,
            borderBottomRightRadius: BorderRadius.xl,
            backgroundColor: item.isCrisis ? 'rgba(232,168,124,0.12)' : WishColors.bgContainer,
            borderWidth: 1,
            borderColor: item.isCrisis ? 'rgba(232,168,124,0.5)' : WishColors.border,
          }}
        >
          <Text style={{ fontSize: FontSize.sm, lineHeight: 24, color: WishColors.text }}>
            {item.content}
          </Text>
          {item.resources && item.resources.length > 0 && (
            <View style={{ marginTop: Spacing.sm, gap: Spacing.sm }}>
              {item.resources.map((res) => (
                <TouchableOpacity
                  key={res.url}
                  onPress={() => handleResourcePress(res)}
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: Spacing.sm,
                    padding: Spacing.sm + 2,
                    borderRadius: BorderRadius.md,
                    borderWidth: 1,
                    borderStyle: 'dashed',
                    borderColor: 'rgba(255,255,255,0.25)',
                  }}
                >
                  <Text style={{ fontSize: 15 }}>{res.type === 'HOTLINE' ? '📞' : '📖'}</Text>
                  <Text style={{ flex: 1, fontSize: FontSize.xs, color: WishColors.textSecondary }}>
                    {res.title}
                  </Text>
                  {res.type === 'HOTLINE' && (
                    <Text style={{ fontSize: 11, color: '#e8a87c' }}>24小时</Text>
                  )}
                </TouchableOpacity>
              ))}
            </View>
          )}
        </View>
      </View>
    )

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      {/* 顶栏 */}
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
          <Text style={{ fontSize: FontSize.md, color: WishColors.textSecondary }}>‹ 返回</Text>
        </TouchableOpacity>
        <Text style={{ flex: 1, textAlign: 'center', fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text }}>
          🌙 树洞
        </Text>
        <View style={{ width: 48 }} />
      </View>

      {/* 对话区 */}
      {loadingHistory ? (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <ActivityIndicator size="large" color={WishColors.accentPurple} />
          <Text style={{ marginTop: Spacing.md, fontSize: FontSize.sm, color: WishColors.textTertiary }}>
            树洞正在苏醒...
          </Text>
        </View>
      ) : (
        <FlatList
          ref={listRef}
          data={messages}
          renderItem={renderItem}
          keyExtractor={(item) => String(item.id)}
          contentContainerStyle={{ padding: Spacing.md }}
          ListEmptyComponent={
            <View style={{ alignItems: 'center', paddingTop: 120 }}>
              <Text style={{ fontSize: 56 }}>🌙</Text>
              <Text style={{ marginTop: Spacing.md, fontSize: FontSize.lg, fontWeight: '600', color: WishColors.text }}>
                这里只属于你
              </Text>
              <Text style={{ marginTop: Spacing.sm, fontSize: FontSize.sm, lineHeight: 22, textAlign: 'center', color: WishColors.textTertiary }}>
                把心事轻轻放进树洞，守护者会认真听。{'\n'}这里没有评判，只有陪伴。
              </Text>
            </View>
          }
          ListFooterComponent={
            sending ? (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, marginBottom: Spacing.md }}>
                <View
                  style={{
                    width: 36,
                    height: 36,
                    borderRadius: 18,
                    backgroundColor: WishColors.bgContainer,
                    borderWidth: 1,
                    borderColor: WishColors.border,
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Text style={{ fontSize: 18 }}>🌙</Text>
                </View>
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>守护者正在倾听...</Text>
              </View>
            ) : null
          }
          onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: true })}
        />
      )}

      {/* 输入区 */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'flex-end',
          gap: Spacing.sm,
          padding: Spacing.md,
          paddingBottom: Spacing.md + insets.bottom,
          borderTopWidth: 1,
          borderTopColor: WishColors.border,
          backgroundColor: WishColors.bgContainer,
        }}
      >
        <TextInput
          style={{
            flex: 1,
            minHeight: 40,
            maxHeight: 100,
            paddingHorizontal: Spacing.md,
            borderRadius: 20,
            backgroundColor: 'rgba(255,255,255,0.06)',
            borderWidth: 1,
            borderColor: WishColors.border,
            color: WishColors.text,
            fontSize: FontSize.sm,
            paddingTop: 10,
            paddingBottom: 10,
          }}
          value={input}
          onChangeText={setInput}
          placeholder="心事放在这里..."
          placeholderTextColor={WishColors.textTertiary}
          multiline
          maxLength={2000}
          editable={!sending}
        />
        <TouchableOpacity
          onPress={handleSend}
          disabled={sending || !input.trim()}
          style={{
            height: 40,
            paddingHorizontal: Spacing.lg,
            borderRadius: 20,
            backgroundColor: WishColors.accentPurple,
            alignItems: 'center',
            justifyContent: 'center',
            opacity: sending || !input.trim() ? 0.5 : 1,
          }}
        >
          <Text style={{ fontSize: FontSize.sm, color: '#fff' }}>{sending ? '...' : '倾诉'}</Text>
        </TouchableOpacity>
      </View>
    </View>
  )
}
