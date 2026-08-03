import { useState, useEffect } from 'react'
import { View, Text, Textarea, ScrollView } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { notificationApi } from '@/api/notification'
import { useAuthStore } from '@/store/auth'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

interface Message {
  id: number
  content: string
  senderId: number
  createdAt: string
  isMine: boolean
}

export default function ChatPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<Message[]>([])
  const [conversationId, setConversationId] = useState<number>(0)
  const [sending, setSending] = useState(false)
  const { user } = useAuthStore()
  useAuthGuard()

  const targetUserId = Number(Taro.getCurrentInstance().router?.params?.targetUserId) || 0

  useEffect(() => {
    if (targetUserId) {
      loadConversation()
    }
  }, [targetUserId])

  const loadConversation = async () => {
    try {
      const convRes = await notificationApi.createConversation({ otherUserId: targetUserId })
      const convId = convRes.data?.data?.id
      if (convId) {
        setConversationId(convId)
        const msgRes = await notificationApi.getMessages(convId, { page: 1, pageSize: 50 })
        const list = msgRes.data?.data?.list || []
        setMessages(list.map((m: any) => ({
          id: m.id,
          content: m.content,
          senderId: m.senderId,
          createdAt: m.createdAt,
          isMine: m.senderId === user?.id,
        })))
      }
    } catch {
      // Conversation may not exist yet
    }
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

    const tempMsg: Message = {
      id: Date.now(),
      content,
      senderId: user?.id || 0,
      createdAt: new Date().toISOString(),
      isMine: true,
    }
    setMessages((prev) => [...prev, tempMsg])

    try {
      await notificationApi.sendMessage(conversationId, { content })
    } catch {
      Taro.showToast({ title: '发送失败', icon: 'none' })
    } finally {
      setSending(false)
    }
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.messages}>
        {messages.length === 0 ? (
          <View className={styles.empty}>
            <Text className={styles.emptyText}>暂无消息记录，发送第一条消息吧</Text>
          </View>
        ) : (
          messages.map((msg) => (
            <View key={msg.id} className={msg.isMine ? styles.myMessage : styles.otherMessage}>
              <View className={msg.isMine ? styles.myBubble : styles.otherBubble}>
                <Text className={msg.isMine ? styles.myText : styles.otherText}>{msg.content}</Text>
              </View>
            </View>
          ))
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
          <Text className={styles.sendText}>发送</Text>
        </View>
      </View>
    </View>
  )
}
