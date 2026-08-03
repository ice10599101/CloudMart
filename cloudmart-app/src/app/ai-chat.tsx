import { View, Text, TextInput, TouchableOpacity, FlatList, ActivityIndicator, KeyboardAvoidingView, Platform } from 'react-native'
import { useState, useRef, useCallback } from 'react'
import { router } from 'expo-router'
import { useTheme } from '@/hooks/use-theme-context'
import { aiApi } from '@/api/ai'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
}

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
  }, [input, isLoading, messages, scrollToBottom])

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
          placeholder="输入消息..."
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
