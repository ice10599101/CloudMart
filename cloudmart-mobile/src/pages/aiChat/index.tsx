import { useState, useRef } from 'react'
import { View, Text, Textarea, ScrollView, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { aiApi } from '@/api/ai'
import { useAuthGuard } from '@/composables/useAuthGuard'
import { useThemeClass } from '@/composables/useThemeClass'
import type { Product } from '@/types'
import styles from './index.module.scss'

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  products?: Product[]
}

// 对齐 Web 端 AiChat：对话/搜索双模式 + 快捷提问 chips
const QUICK_QUESTIONS = ['推荐一款手机', '笔记本电脑怎么选', '春季穿搭推荐', '家居好物分享']

export default function AiChatPage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const [mode, setMode] = useState<'chat' | 'search'>('chat')
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 0,
      role: 'assistant',
      content: '你好！我是宝贝小答 AI 助手，有什么可以帮你的吗？我可以帮你推荐商品、解答购物疑问、查找优惠信息等。',
    },
  ])
  const [sending, setSending] = useState(false)
  // 会话保持（对齐 Web 端：同一会话上下文，后端 conversationId 串联）
  const conversationIdRef = useRef<string | undefined>(undefined)
  useAuthGuard()

  const handleSend = async () => {
    if (!input.trim() || sending) return

    const userMsg: ChatMessage = {
      id: Date.now(),
      role: 'user',
      content: input.trim(),
    }
    setMessages((prev) => [...prev, userMsg])
    const query = input.trim()
    setInput('')
    setSending(true)

    try {
      if (mode === 'search') {
        // 搜索模式：返回商品卡片结果（对齐 Web 端搜索模式）
        const res = await aiApi.search(query)
        const products = res.data?.data?.list ?? res.data?.data ?? []
        const aiMsg: ChatMessage = {
          id: Date.now() + 1,
          role: 'assistant',
          content: products.length > 0 ? `为你找到 ${products.length} 件相关商品：` : '没有找到相关商品，换个关键词试试？',
          products: products.slice(0, 6),
        }
        setMessages((prev) => [...prev, aiMsg])
      } else {
        const res = await aiApi.chat({ message: query, conversationId: conversationIdRef.current })
        const aiContent = res.data?.data?.reply || res.data?.data?.content || res.data?.data?.message || '抱歉，我暂时无法回答这个问题。'
        if (res.data?.data?.conversationId) conversationIdRef.current = res.data.data.conversationId
        const aiMsg: ChatMessage = {
          id: Date.now() + 1,
          role: 'assistant',
          content: aiContent,
        }
        setMessages((prev) => [...prev, aiMsg])
      }
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

  const goProduct = (id: number) => Taro.navigateTo({ url: `/pages/productDetail/index?id=${id}` })

  return (
    <View data-theme={dataTheme} className={styles.page} style={themeStyle}>
      {/* 模式切换（对齐 Web 端「对话 / 🔍搜索模式」） */}
      <View className={styles.modeSwitch}>
        <View className={`${styles.modeChip} ${mode === 'chat' ? styles.modeChipActive : ''}`} onClick={() => setMode('chat')}>
          <Text className={mode === 'chat' ? styles.modeChipTextActive : styles.modeChipText}>💬 对话</Text>
        </View>
        <View className={`${styles.modeChip} ${mode === 'search' ? styles.modeChipActive : ''}`} onClick={() => setMode('search')}>
          <Text className={mode === 'search' ? styles.modeChipTextActive : styles.modeChipText}>🔍 搜索</Text>
        </View>
      </View>

      <ScrollView scrollY className={styles.messages}>
        {messages.map((msg) => (
          <View key={msg.id} className={msg.role === 'user' ? styles.myMessage : styles.aiMessage}>
            {msg.role === 'assistant' && (
              <Text className={styles.aiLabel}>AI 助手</Text>
            )}
            <View className={msg.role === 'user' ? styles.myBubble : styles.aiBubble}>
              <Text className={msg.role === 'user' ? styles.myText : styles.aiText}>{msg.content}</Text>
            </View>
            {/* 搜索模式的商品卡片结果 */}
            {msg.products && msg.products.length > 0 && (
              <View className={styles.productResults}>
                {msg.products.map((product) => (
                  <View key={product.id} className={styles.productResultCard} onClick={() => goProduct(product.id)}>
                    {product.mainImage && <Image className={styles.productResultImage} src={product.mainImage} mode='aspectFill' />}
                    <View className={styles.productResultInfo}>
                      <Text className={styles.productResultName}>{product.name}</Text>
                      <Text className={styles.productResultPrice}>¥{product.price}</Text>
                    </View>
                  </View>
                ))}
              </View>
            )}
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

      {/* 快捷提问 chips（对话模式展示） */}
      <ScrollView scrollX enhanced showScrollbar={false} className={styles.quickScroll}>
        <View className={styles.quickRow}>
          {QUICK_QUESTIONS.map((q) => (
            <View key={q} className={styles.quickChip} onClick={() => { setInput(q); setTimeout(handleSend, 0) }}>
              <Text className={styles.quickChipText}>{q}</Text>
            </View>
          ))}
        </View>
      </ScrollView>

      <View className={styles.bottomBar}>
        <Textarea
          className={styles.input}
          placeholder={mode === 'search' ? '搜索你想找的商品...' : '输入你的问题...'}
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
