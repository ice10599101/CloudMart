import { View, Text, TextInput, TouchableOpacity, FlatList, ActivityIndicator, KeyboardAvoidingView, Platform, Image, ScrollView } from 'react-native'
import { useState, useRef, useCallback } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { aiApi } from '@/api/ai'
import type { Product } from '@/types'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  /** 搜索模式返回的商品卡片结果 */
  products?: Product[]
}

// 对齐 Web 端 AiChat：对话/搜索双模式 + 快捷提问 chips
const QUICK_QUESTIONS = ['推荐一款手机', '笔记本电脑怎么选', '春季穿搭推荐', '家居好物分享']

const WELCOME_MESSAGE: ChatMessage = {
  id: 0,
  role: 'assistant',
  content: '你好！我是 CloudMart AI 助手，有什么可以帮你的吗？我可以帮你推荐商品、解答购物疑问、查找优惠信息等。',
}

export default function AiChatScreen() {
  const theme = useTheme()
  const [messages, setMessages] = useState<ChatMessage[]>([WELCOME_MESSAGE])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [conversationId, setConversationId] = useState<string | undefined>(undefined)
  const [mode, setMode] = useState<'chat' | 'search'>('chat')
  const flatListRef = useRef<FlatList>(null)
  const nextId = useRef(1)

  const scrollToBottom = useCallback(() => {
    setTimeout(() => {
      flatListRef.current?.scrollToEnd({ animated: true })
    }, 100)
  }, [])

  const handleSend = useCallback(async () => {
    const trimmed = input.trim()
    if (!trimmed || isLoading) return

    const userMessage: ChatMessage = {
      id: nextId.current++,
      role: 'user',
      content: trimmed,
    }

    const updatedMessages = [...messages, userMessage]
    setMessages(updatedMessages)
    setInput('')
    setIsLoading(true)
    scrollToBottom()

    try {
      if (mode === 'search') {
        // 搜索模式：返回商品卡片结果（对齐 Web 端搜索模式）
        const res = await aiApi.search(trimmed)
        const products = (res.data as { data?: { list?: Product[] } })?.data?.list ?? []
        const assistantMessage: ChatMessage = {
          id: nextId.current++,
          role: 'assistant',
          content: products.length > 0 ? `为你找到 ${products.length} 件相关商品：` : '没有找到相关商品，换个关键词试试？',
          products: products.slice(0, 6),
        }
        setMessages((prev) => [...prev, assistantMessage])
        return
      }
      const res = await aiApi.chat({ message: trimmed, conversationId })
      const content = res.data?.data?.content || res.data?.data?.message || res.data?.data?.reply || '抱歉，我暂时无法回答，请稍后再试。'
      const returnedConversationId = res.data?.data?.conversationId
      if (returnedConversationId) setConversationId(returnedConversationId)
      const assistantMessage: ChatMessage = {
        id: nextId.current++,
        role: 'assistant',
        content,
      }
      setMessages((prev) => [...prev, assistantMessage])
    } catch {
      const errorMessage: ChatMessage = {
        id: nextId.current++,
        role: 'assistant',
        content: '网络异常，请稍后再试。',
      }
      setMessages((prev) => [...prev, errorMessage])
    } finally {
      setIsLoading(false)
      scrollToBottom()
    }
  }, [input, isLoading, messages, scrollToBottom, conversationId, mode])

  const renderItem = useCallback(
    ({ item }: { item: ChatMessage }) => {
      const isUser = item.role === 'user'
      return (
        <View
          style={{
            flexDirection: isUser ? 'row-reverse' : 'row',
            alignItems: 'flex-start',
            marginBottom: Spacing.lg,
            paddingHorizontal: Spacing.lg,
          }}
        >
          {!isUser && (
            <View
              style={{
                width: 36,
                height: 36,
                borderRadius: BorderRadius.full,
                backgroundColor: theme.primaryGlow,
                justifyContent: 'center',
                alignItems: 'center',
                marginRight: Spacing.sm,
              }}
            >
              <Text style={{ fontSize: FontSize.lg }}>🤖</Text>
            </View>
          )}
          <View style={{ maxWidth: '75%' }}>
            {!isUser && (
              <Text
                style={{
                  fontSize: FontSize.xs,
                  color: theme.textTertiary,
                  marginBottom: Spacing.xs,
                  marginLeft: Spacing.xs,
                }}
              >
                AI 助手
              </Text>
            )}
            <View
              style={{
                backgroundColor: isUser ? theme.primary : theme.bgContainer,
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.md,
                borderRadius: BorderRadius.lg,
                borderTopRightRadius: isUser ? BorderRadius.xs : BorderRadius.lg,
                borderTopLeftRadius: isUser ? BorderRadius.lg : BorderRadius.xs,
                borderWidth: isUser ? 0 : 1,
                borderColor: theme.border,
              }}
            >
              <Text
                style={{
                  fontSize: FontSize.md,
                  color: isUser ? '#FFFFFF' : theme.text,
                  lineHeight: 22,
                }}
              >
                {item.content}
              </Text>
            </View>
            {/* 搜索模式的商品卡片结果 */}
            {item.products && item.products.length > 0 ? (
              <View style={{ marginTop: Spacing.sm, gap: Spacing.sm }}>
                {item.products.map((product) => (
                  <TouchableOpacity
                    key={product.id}
                    activeOpacity={0.7}
                    onPress={() => router.push(`/product/${product.id}`)}
                    style={{ flexDirection: 'row', backgroundColor: theme.bgContainer, borderWidth: 1, borderColor: theme.border, borderRadius: BorderRadius.md, padding: Spacing.sm, gap: Spacing.sm }}
                  >
                    {product.mainImage ? (
                      <Image source={{ uri: product.mainImage }} style={{ width: 56, height: 56, borderRadius: BorderRadius.sm }} />
                    ) : null}
                    <View style={{ flex: 1 }}>
                      <Text numberOfLines={2} style={{ fontSize: FontSize.sm, color: theme.text }}>{product.name}</Text>
                      <Text style={{ marginTop: 4, fontSize: FontSize.md, fontWeight: '700', color: theme.accentRed }}>¥{product.price}</Text>
                    </View>
                  </TouchableOpacity>
                ))}
              </View>
            ) : null}
          </View>
        </View>
      )
    },
    [theme],
  )

  const ListFooter = isLoading ? (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        paddingHorizontal: Spacing.lg,
        marginBottom: Spacing.lg,
      }}
    >
      <View
        style={{
          width: 36,
          height: 36,
          borderRadius: BorderRadius.full,
          backgroundColor: theme.primaryGlow,
          justifyContent: 'center',
          alignItems: 'center',
          marginRight: Spacing.sm,
        }}
      >
        <Text style={{ fontSize: FontSize.lg }}>🤖</Text>
      </View>
      <View>
        <Text style={{ fontSize: FontSize.xs, color: theme.textTertiary, marginBottom: Spacing.xs, marginLeft: Spacing.xs }}>
          AI 助手
        </Text>
        <View
          style={{
            backgroundColor: theme.bgContainer,
            paddingHorizontal: Spacing.md,
            paddingVertical: Spacing.md,
            borderRadius: BorderRadius.lg,
            borderTopLeftRadius: BorderRadius.xs,
            borderWidth: 1,
            borderColor: theme.border,
            flexDirection: 'row',
            alignItems: 'center',
            gap: Spacing.sm,
          }}
        >
          <ActivityIndicator size="small" color={theme.primary} />
          <Text style={{ fontSize: FontSize.md, color: theme.textSecondary }}>思考中...</Text>
        </View>
      </View>
    </View>
  ) : null

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: theme.bgBase }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0}
    >
      {/* Header */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          paddingHorizontal: Spacing.lg,
          paddingVertical: Spacing.md,
          backgroundColor: theme.bgContainer,
          borderBottomWidth: 1,
          borderBottomColor: theme.border,
        }}
      >
        <TouchableOpacity activeOpacity={0.7} onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.xl, color: theme.text }}>←</Text>
        </TouchableOpacity>
        <Text
          style={{
            flex: 1,
            textAlign: 'center',
            fontSize: FontSize.lg,
            fontWeight: '600',
            color: theme.text,
            marginRight: FontSize.xl,
          }}
        >
          AI 助手
        </Text>
      </View>

      {/* Messages */}
      <FlatList
        ref={flatListRef}
        data={messages}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderItem}
        contentContainerStyle={{ paddingVertical: Spacing.lg }}
        ListFooterComponent={ListFooter}
        onContentSizeChange={scrollToBottom}
        keyboardShouldPersistTaps="handled"
      />

      {/* 模式切换（对齐 Web 端「对话 / 🔍搜索模式」） */}
      <View style={{ flexDirection: 'row', gap: Spacing.sm, paddingHorizontal: Spacing.lg, paddingVertical: Spacing.sm }}>
        {([
          { key: 'chat', label: '💬 对话' },
          { key: 'search', label: '🔍 搜索' },
        ] as const).map((tab) => (
          <TouchableOpacity
            key={tab.key}
            activeOpacity={0.7}
            onPress={() => setMode(tab.key)}
            style={{
              paddingHorizontal: Spacing.lg,
              paddingVertical: Spacing.xs,
              borderRadius: BorderRadius.xl,
              borderWidth: 1,
              borderColor: mode === tab.key ? theme.primary : theme.border,
              backgroundColor: mode === tab.key ? theme.primary + '14' : 'transparent',
            }}
          >
            <Text style={{ fontSize: FontSize.xs, fontWeight: mode === tab.key ? '600' : '400', color: mode === tab.key ? theme.primary : theme.textSecondary }}>
              {tab.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* 快捷提问 chips（对话模式展示） */}
      {mode === 'chat' && (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ paddingHorizontal: Spacing.lg, paddingBottom: Spacing.xs }}>
          {QUICK_QUESTIONS.map((question) => (
            <TouchableOpacity
              key={question}
              activeOpacity={0.7}
              onPress={() => {
                if (isLoading) return
                setInput(question)
                setTimeout(handleSend, 0)
              }}
              style={{
                paddingHorizontal: Spacing.md,
                paddingVertical: Spacing.xs,
                borderRadius: BorderRadius.xl,
                borderWidth: 1,
                borderColor: theme.border,
                marginRight: Spacing.sm,
              }}
            >
              <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary }}>{question}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      )}

      {/* Input Bar */}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          paddingHorizontal: Spacing.lg,
          paddingVertical: Spacing.md,
          backgroundColor: theme.bgContainer,
          borderTopWidth: 1,
          borderTopColor: theme.border,
          gap: Spacing.md,
        }}
      >
        <TextInput
          value={input}
          onChangeText={setInput}
          placeholder={mode === "search" ? "搜索你想找的商品..." : "输入消息..."}
          placeholderTextColor={theme.textTertiary}
          editable={!isLoading}
          style={{
            flex: 1,
            height: 42,
            backgroundColor: theme.bgInput,
            borderRadius: BorderRadius.xl,
            paddingHorizontal: Spacing.lg,
            color: theme.text,
            fontSize: FontSize.md,
            padding: 0,
          }}
        />
        <TouchableOpacity
          activeOpacity={0.7}
          onPress={handleSend}
          disabled={!input.trim() || isLoading}
          style={{
            backgroundColor: input.trim() && !isLoading ? theme.primary : theme.bgElevated,
            paddingHorizontal: Spacing.lg,
            height: 42,
            borderRadius: BorderRadius.xl,
            justifyContent: 'center',
            alignItems: 'center',
            borderWidth: 1,
            borderColor: input.trim() && !isLoading ? theme.primary : theme.border,
          }}
        >
          <Text
            style={{
              fontSize: FontSize.md,
              fontWeight: '600',
              color: input.trim() && !isLoading ? '#FFFFFF' : theme.textTertiary,
            }}
          >
            发送
          </Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  )
}
