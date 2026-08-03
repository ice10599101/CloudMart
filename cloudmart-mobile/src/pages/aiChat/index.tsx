import { useState } from 'react'
import { View, Text, Textarea, ScrollView } from '@tarojs/components'
import { aiApi } from '@/api/ai'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import styles from './index.module.scss'

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
}

export default function AiChatPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 0,
      role: 'assistant',
      content: '你好！我是宝贝小答 AI 助手，有什么可以帮你的吗？我可以帮你推荐商品、解答购物疑问、查找优惠信息等。',
    },
  ])
  const [sending, setSending] = useState(false)
  useAuthGuard()

  const handleSend = async () => {
    if (!input.trim() || sending) return

    const userMsg: ChatMessage = {
      id: Date.now(),
      role: 'user',
      content: input.trim(),
    }
    setMessages((prev) => [...prev, userMsg])
    setInput('')
    setSending(true)

    try {
      const res = await aiApi.chat({ message: userMsg.content })
      const aiContent = res.data?.data?.content || res.data?.data?.message || '抱歉，我暂时无法回答这个问题。'
      const aiMsg: ChatMessage = {
        id: Date.now() + 1,
        role: 'assistant',
        content: aiContent,
      }
      setMessages((prev) => [...prev, aiMsg])
    } catch {
      const errMsg: ChatMessage = {
        id: Date.now() + 1,
        role: 'assistant',
        content: '网络异常，请稍后再试。',
      }
      setMessages((prev) => [...prev, errMsg])
    } finally {
      setSending(false)
    }
  }

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      <ScrollView scrollY className={styles.messages}>
        {messages.map((msg) => (
          <View key={msg.id} className={msg.role === 'user' ? styles.myMessage : styles.aiMessage}>
            {msg.role === 'assistant' && (
              <Text className={styles.aiLabel}>AI 助手</Text>
            )}
            <View className={msg.role === 'user' ? styles.myBubble : styles.aiBubble}>
              <Text className={msg.role === 'user' ? styles.myText : styles.aiText}>{msg.content}</Text>
            </View>
          </View>
        ))}
        {sending && (
          <View className={styles.aiMessage}>
            <Text className={styles.aiLabel}>AI 助手</Text>
            <View className={styles.aiBubble}>
              <Text className={styles.aiText}>思考中...</Text>
            </View>
          </View>
        )}
      </ScrollView>
      <View className={styles.bottomBar}>
        <Textarea
          className={styles.input}
          placeholder='输入你的问题...'
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
