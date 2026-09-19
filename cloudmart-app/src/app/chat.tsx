import {
  View,
  Text,
  Image,
  ScrollView,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from 'react-native'
import { useState, useEffect, useRef, useCallback } from 'react'
import { router, useLocalSearchParams } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { notificationApi } from '@/api/notification'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import type { ChatMessage } from '@/types'

function formatMessageTime(time: string): string {
  const date = new Date(time)
  const now = new Date()
  const isToday =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()

  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const timeStr = `${hours}:${minutes}`

  if (isToday) return timeStr

  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}/${day} ${timeStr}`
}

/** 撤回时限（对齐 Web 端：发送 2 分钟内可撤回） */
const RECALL_WINDOW_MS = 2 * 60 * 1000
/** 新消息轮询间隔（移动端无 WS，轮询保证能收到新消息） */
const POLL_INTERVAL_MS = 5_000
export default function ChatPage() {
  const theme = useTheme()
  const { targetUserId, conversationId: conversationIdParam } =
    useLocalSearchParams<{ targetUserId?: string; conversationId?: string }>()
  const currentUser = useAuthStore((s) => s.user)

  const [conversationId, setConversationId] = useState<number | null>(
    conversationIdParam ? Number(conversationIdParam) : null,
  )
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [inputText, setInputText] = useState('')
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)

  const scrollViewRef = useRef<ScrollView>(null)
  const currentUserId = currentUser?.id
  const messagesRef = useRef<ChatMessage[]>([])
  messagesRef.current = messages

  const loadMessages = useCallback(async (convId: number) => {
    try {
      const res = await notificationApi.getMessages(convId, { page: 1, pageSize: 50 })
      const list = res.data?.data?.list ?? res.data?.data ?? []
      setMessages(Array.isArray(list) ? list : [])
    } catch {
      setMessages([])
    } finally {
      setLoading(false)
    }
  }, [])

  // 初始化：创建会话或直接加载消息
  useEffect(() => {
    const init = async () => {
      if (conversationId) {
        await loadMessages(conversationId)
        notificationApi.markConversationRead(conversationId).catch(() => {})
        return
      }

      if (targetUserId) {
        try {
          const res = await notificationApi.createConversation({
            otherUserId: Number(targetUserId),
          })
          const convId = res.data?.data?.id ?? res.data?.data
          if (convId) {
            setConversationId(Number(convId))
            await loadMessages(Number(convId))
            notificationApi.markConversationRead(Number(convId)).catch(() => {})
          }
        } catch {
          Alert.alert('错误', '创建会话失败')
        } finally {
          setLoading(false)
        }
        return
      }

      setLoading(false)
    }

    init()
  }, [conversationId, targetUserId, loadMessages])

  // 新消息自动滚动到底部
  useEffect(() => {
    if (messages.length > 0) {
      requestAnimationFrame(() => {
        scrollViewRef.current?.scrollToEnd({ animated: true })
      })
    }
  }, [messages.length])

  // 新消息轮询（移动端无 WS：5s 增量拉取，保证能收到对方新消息）
  useEffect(() => {
    if (!conversationId) return
    const timer = setInterval(async () => {
      try {
        const res = await notificationApi.getMessages(conversationId, { page: 1, pageSize: 50 })
        const list = res.data?.data?.list ?? res.data?.data ?? []
        if (Array.isArray(list) && list.length > 0) {
          const latestId = list[list.length - 1].id
          if (latestId !== messagesRef.current[messagesRef.current.length - 1]?.id) {
            setMessages(list)
          }
        }
      } catch {
        // 轮询失败静默
      }
    }, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [conversationId])

  /** 撤回自己的消息（2 分钟内，对齐 Web 端；WS 同步「该消息已撤回」） */
  const handleRecall = (msg: ChatMessage) => {
    if (Date.now() - new Date(msg.createdAt).getTime() > RECALL_WINDOW_MS) {
      Alert.alert('提示', '超过 2 分钟，无法撤回')
      return
    }
    Alert.alert('撤回消息', '确定撤回这条消息吗？', [
      { text: '取消', style: 'cancel' },
      {
        text: '撤回',
        style: 'destructive',
        onPress: async () => {
          try {
            await notificationApi.recallMessage(msg.id)
            setMessages((prev) => prev.map((m) => (m.id === msg.id ? { ...m, isRecalled: true } : m)))
          } catch (err) {
            const message =
              (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
            Alert.alert('提示', message || '撤回失败')
          }
        },
      },
    ])
  }

  const handleSend = async () => {
    const content = inputText.trim()
    if (!content || !conversationId || sending) return

    setSending(true)
    try {
      await notificationApi.sendMessage(conversationId, { content })
      setInputText('')
      await loadMessages(conversationId)
    } catch {
      Alert.alert('错误', '发送失败')
    } finally {
      setSending(false)
    }
  }

  if (loading) {
    return (
      <View
        style={{
          flex: 1,
          backgroundColor: theme.bgBase,
          justifyContent: 'center',
          alignItems: 'center',
        }}
      >
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    )
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: theme.bgBase }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0}
    >
      {/* 消息列表 */}
      <ScrollView
        ref={scrollViewRef}
        contentContainerStyle={{ flexGrow: 1, padding: Spacing.lg, paddingBottom: 80 }}
        keyboardShouldPersistTaps="handled"
      >
        {messages.length === 0 ? (
          <View
            style={{
              flex: 1,
              justifyContent: 'center',
              alignItems: 'center',
            }}
          >
            <Text style={{ color: theme.textTertiary, fontSize: FontSize.md }}>
              暂无消息记录，发送第一条消息吧
            </Text>
          </View>
        ) : (
          messages.map((msg) => {
            const isMine = msg.senderId === currentUserId
            return (
              <View
                key={msg.id}
                style={{
                  flexDirection: isMine ? 'row-reverse' : 'row',
                  alignItems: 'flex-end',
                  marginBottom: Spacing.md,
                }}
              >
                {msg.isRecalled ? (
                  <Text
                    style={{
                      fontSize: FontSize.xs,
                      color: theme.textTertiary,
                      paddingVertical: Spacing.sm,
                      textAlign: 'center',
                      flex: 1,
                    }}
                  >
                    该消息已撤回
                  </Text>
                ) : msg.type === 'IMAGE' ? (
                  <TouchableOpacity activeOpacity={0.9} onPress={() => { /* 图片消息点击预览 */ }}>
                    <Image
                      source={{ uri: msg.content }}
                      style={{ maxWidth: '75%', height: 180, borderRadius: BorderRadius.lg }}
                      resizeMode="cover"
                    />
                  </TouchableOpacity>
                ) : (
                <View
                  style={{
                    maxWidth: '75%',
                    backgroundColor: isMine
                      ? theme.primary
                      : theme.bgContainer,
                    borderRadius: BorderRadius.lg,
                    paddingHorizontal: Spacing.md,
                    paddingVertical: Spacing.sm,
                  }}
                >
                  <Text
                    style={{
                      fontSize: FontSize.md,
                      color: isMine ? '#FFFFFF' : theme.text,
                      lineHeight: 22,
                    }}
                  >
                    {msg.content}
                  </Text>
                  <Text
                    style={{
                      fontSize: FontSize.xs,
                      color: isMine
                        ? 'rgba(255,255,255,0.7)'
                        : theme.textTertiary,
                      marginTop: Spacing.xs,
                      textAlign: isMine ? 'right' : 'left',
                    }}
                  >
                    {formatMessageTime(msg.createdAt)}
                  </Text>
                </View>
                )}
                {/* 撤回入口：长按自己 2 分钟内的消息 */}
                {!msg.isRecalled && isMine && (
                  <TouchableOpacity
                    hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                    onLongPress={() => handleRecall(msg)}
                    style={{ justifyContent: 'flex-end', paddingBottom: Spacing.sm }}
                  >
                    <Text style={{ fontSize: 12, color: theme.textTertiary }}>⏱</Text>
                  </TouchableOpacity>
                )}
              </View>
            )
          })
        )}
      </ScrollView>

      {/* 底部输入栏 */}
      <View
        style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          flexDirection: 'row',
          alignItems: 'center',
          padding: Spacing.md,
          backgroundColor: theme.bgContainer,
          borderTopWidth: 1,
          borderTopColor: theme.border,
          gap: Spacing.md,
        }}
      >
        <TextInput
          placeholder="输入消息..."
          placeholderTextColor={theme.textTertiary}
          value={inputText}
          onChangeText={setInputText}
          onSubmitEditing={handleSend}
          returnKeyType="send"
          editable={!sending}
          style={{
            flex: 1,
            height: 40,
            backgroundColor: theme.bgInput,
            borderRadius: BorderRadius.xl,
            paddingHorizontal: Spacing.lg,
            color: theme.text,
            fontSize: FontSize.md,
          }}
        />
        <TouchableOpacity
          onPress={handleSend}
          disabled={sending || !inputText.trim()}
          style={{
            paddingHorizontal: Spacing.lg,
            paddingVertical: Spacing.sm,
            borderRadius: BorderRadius.xl,
            backgroundColor:
              sending || !inputText.trim()
                ? theme.bgElevated
                : theme.primary,
            opacity: sending || !inputText.trim() ? 0.6 : 1,
          }}
        >
          <Text
            style={{
              fontSize: FontSize.md,
              color: '#FFFFFF',
              fontWeight: '600',
            }}
          >
            发送
          </Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  )
}
