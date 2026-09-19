import { useState, useEffect, useRef } from 'react'
import { View, Text, Textarea, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { notificationApi } from '@/api/notification'
import { useAuthStore } from '@/store/auth'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import type { ChatMessage } from '@/types'
import styles from './index.module.scss'

/** 撤回时限（对齐 Web 端：发送 2 分钟内可撤回） */
const RECALL_WINDOW_MS = 2 * 60 * 1000
/** 新消息轮询间隔（移动端无 WS，用轮询保证可收到新消息） */
const POLL_INTERVAL_MS = 5_000

export default function ChatPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [conversationId, setConversationId] = useState<number>(0)
  const [sending, setSending] = useState(false)
  const { user } = useAuthStore()
  useAuthGuard()

  const routerParams = Taro.getCurrentInstance().router?.params ?? {}
  // 兼容三个入口的参数：message 页传会话 id(?id=)、userProfile 传对方用户 id(?userId=)、旧深链 ?targetUserId=
  const routeConversationId = Number(routerParams.id) || 0
  const routeTargetUserId = Number(routerParams.userId || routerParams.targetUserId) || 0

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const lastLoadedIdRef = useRef(0)

  useEffect(() => {
    initConversation()
    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [])

  const initConversation = async () => {
    try {
      let convId = routeConversationId
      if (!convId && routeTargetUserId) {
        const convRes = await notificationApi.createConversation({ otherUserId: routeTargetUserId })
        convId = convRes.data?.data?.id ?? 0
      }
      if (!convId) return
      setConversationId(convId)
      await loadMessages(convId, true)
      // 进入会话即清零未读（对齐 Web 端）
      notificationApi.markConversationRead(convId).catch(() => {})
      startPolling(convId)
    } catch {
      // Conversation may not exist yet
    }
  }

  const loadMessages = async (convId: number, reset: boolean) => {
    try {
      const msgRes = await notificationApi.getMessages(convId, { page: 1, pageSize: 50 })
      const list = msgRes.data?.data?.list || []
      setMessages(list)
      if (list.length > 0) lastLoadedIdRef.current = list[list.length - 1].id
    } catch {
      if (reset) setMessages([])
    }
  }

  /** 轮询增量消息（移动端无 WS：只拉新消息追加） */
  const startPolling = (convId: number) => {
    if (pollRef.current) clearInterval(pollRef.current)
    pollRef.current = setInterval(async () => {
      try {
        const msgRes = await notificationApi.getMessages(convId, { page: 1, pageSize: 50 })
        const list = msgRes.data?.data?.list || []
        if (list.length > 0) {
          const latestId = list[list.length - 1].id
          if (latestId !== lastLoadedIdRef.current) {
            lastLoadedIdRef.current = latestId
            setMessages(list)
          }
        }
      } catch {
        // 轮询失败静默
      }
    }, POLL_INTERVAL_MS)
  }

  const handleSend = async () => {
    if (!input.trim() || sending) return
    if (!conversationId) {
      Taro.showToast({ title: '会话创建中，请稍后', icon: 'none' })
      return
    }

    setSending(true)
    const content = input.trim()
    setInput('')

    try {
      const res = await notificationApi.sendMessage(conversationId, { content })
      const sent = res.data?.data
      if (sent) {
        setMessages((prev) => [...prev, sent])
        lastLoadedIdRef.current = sent.id
      } else {
        await loadMessages(conversationId, true)
      }
    } catch {
      Taro.showToast({ title: '发送失败', icon: 'none' })
    } finally {
      setSending(false)
    }
  }

  /** 撤回自己的消息（2 分钟内，对齐 Web 端） */
  const handleRecall = (msg: ChatMessage) => {
    if (Date.now() - new Date(msg.createdAt).getTime() > RECALL_WINDOW_MS) {
      Taro.showToast({ title: '超过 2 分钟，无法撤回', icon: 'none' })
      return
    }
    Taro.showModal({
      title: '撤回消息',
      content: '确定撤回这条消息吗？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await notificationApi.recallMessage(msg.id)
          setMessages((prev) => prev.map((m) => (m.id === msg.id ? { ...m, isRecalled: true } : m)))
        } catch (err) {
          const message =
            (err as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message || '撤回失败'
          Taro.showToast({ title: message, icon: 'none' })
        }
      },
    })
  }

  const formatTime = (time: string) => {
    const d = new Date(time)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.messages}>
        {messages.length === 0 ? (
          <View className={styles.empty}>
            <Text className={styles.emptyText}>暂无消息记录，发送第一条消息吧</Text>
          </View>
        ) : (
          messages.map((msg, idx) => {
            const isMine = msg.senderId === user?.id
            // 按天分组（对齐 Web 端：今天/昨天/日期分隔）
            const prev = messages[idx - 1]
            const dayOf = (t: string) => new Date(t).toDateString()
            const showDay = !prev || dayOf(prev.createdAt) !== dayOf(msg.createdAt)
            const dayLabel = (() => {
              const d = new Date(msg.createdAt)
              const today = new Date()
              const yesterday = new Date(today.getTime() - 86_400_000)
              if (d.toDateString() === today.toDateString()) return '今天'
              if (d.toDateString() === yesterday.toDateString()) return '昨天'
              return `${d.getMonth() + 1}月${d.getDate()}日`
            })()
            return (
              <View key={msg.id}>
              {showDay && (
                <View className={styles.dayDivider}>
                  <Text className={styles.dayDividerText}>{dayLabel}</Text>
                </View>
              )}
              <View className={isMine ? styles.myMessage : styles.otherMessage}>
                {msg.isRecalled ? (
                  <Text className={styles.recalledText}>该消息已撤回</Text>
                ) : msg.type === 'IMAGE' ? (
                  <Image
                    className={styles.imageMessage}
                    src={msg.content}
                    mode='aspectFill'
                    onClick={() => Taro.previewImage({ urls: [msg.content] })}
                  />
                ) : (
                  <View
                    className={isMine ? styles.myBubble : styles.otherBubble}
                    onLongPress={isMine ? () => handleRecall(msg) : undefined}
                  >
                    <Text className={isMine ? styles.myText : styles.otherText}>{msg.content}</Text>
                  </View>
                )}
                <Text className={styles.messageTime}>{formatTime(msg.createdAt)}</Text>
              </View>
              </View>
            )
          })
        )}
      </ScrollView>
      <View className={styles.bottomBar}>
        <Textarea
          className={styles.input}
          placeholder='输入消息...'
          value={input}
          onInput={(e) => setInput(e.detail.value)}
        />
        <View className={styles.sendBtn} onClick={handleSend}>
          <Text className={styles.sendText}>{sending ? '...' : '发送'}</Text>
        </View>
      </View>
    </View>
  )
}
