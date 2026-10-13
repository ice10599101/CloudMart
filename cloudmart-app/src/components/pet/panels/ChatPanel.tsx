// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useEffect, useState } from 'react'
import { Alert, Text, TextInput, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { CreamButton, petCreamStyles } from '@/components/pet-cream'
import { petApi, petApiV2, type PetChatMessage } from '@/api/pet'
import { getPersistedIntentKey, errorMessage } from '@/utils/request'
import { ChipButton } from '../shared'

/** 聊天面板：自管 cursor 分页与 R25 断网查终态；宠物气泡经 onBubble 回传宿主舞台 */
export function ChatPanel({ onBubble }: { onBubble: (content: string) => void }) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [chatMessages, setChatMessages] = useState<PetChatMessage[]>([])
  const [chatInput, setChatInput] = useState('')
  const [chatCursor, setChatCursor] = useState<number | string | null>(null)
  const [chatHasMore, setChatHasMore] = useState(false)
  const [chatLoadingMore, setChatLoadingMore] = useState(false)

  useEffect(() => {
    void (async () => {
      try {
        const { data: res } = await petApi.chatHistory({ pageSize: 30 })
        const page = (res.data || []).slice().reverse()
        setChatMessages(page)
        const oldest = page[0]
        setChatCursor(oldest ? oldest.messageId : null)
        setChatHasMore((res.data || []).length >= 30 && Boolean(oldest))
      } catch {
        // 拦截器已提示
      }
    })()
  }, [])

  /** 加载更早的聊天记录（cursor 分页） */
  const loadMoreChat = async () => {
    if (!chatCursor || chatLoadingMore) return
    setChatLoadingMore(true)
    try {
      const { data: res } = await petApi.chatHistory({ cursor: chatCursor, pageSize: 30 })
      const older = (res.data || []).slice().reverse()
      if (older.length === 0) {
        setChatHasMore(false)
      } else {
        setChatMessages((prev) => [...older, ...prev])
        setChatCursor(older[0]?.messageId ?? null)
        setChatHasMore(older.length >= 30)
      }
    } catch {
      // 拦截器已提示
    } finally {
      setChatLoadingMore(false)
    }
  }

  const sendChat = async () => {
    const text = chatInput.trim()
    if (!text) return
    setChatInput('')
    try {
      const { data: res } = await petApi.chat(text)
      if (res.data) {
        setChatMessages((prev) => [...prev, {
          messageId: `${res.data.messageId}-u`, role: 'USER', content: text, isAiReply: false, createdAt: null,
        }, res.data])
        onBubble(res.data.content)
      }
    } catch (error) {
      // R25：处理中提示等待；断网/超时（结果未知）按持久意图原键查终态，不生成新意图
      const code = (error as { code?: string }).code
      if (code === 'PET_REQUEST_IN_PROGRESS') {
        Alert.alert('它还在想回复', '稍等片刻再查看结果')
        return
      }
      if (code === 'NETWORK_ERROR') {
        const requestKey = await getPersistedIntentKey('POST', '/pet/chat', { message: text })
        if (requestKey) {
          try {
            const { data: status } = await petApiV2.getChatRequestStatus(requestKey)
            if (status.data?.status === 'SUCCEEDED' && status.data.reply) {
              const reply = status.data.reply as unknown as PetChatMessage
              setChatMessages((prev) => [...prev, {
                messageId: `${reply.messageId}-u`, role: 'USER', content: text, isAiReply: false, createdAt: null,
              }, reply])
              onBubble(reply.content)
              return
            }
            if (status.data?.status === 'PROCESSING') {
              Alert.alert('它还在想回复', '稍后刷新即可看到')
              return
            }
          } catch {
            // 查询也失败：落到通用网络提示
          }
        }
        Alert.alert('网络不稳定', '稍后可按原消息重试（不会重复计费）')
        return
      }
      if (code !== 'UNAUTHORIZED') {
        Alert.alert('发送未成功', errorMessage(error, '请稍后再试'))
      }
    }
  }

  return (
    <View style={{ gap: Spacing.sm }}>
      {chatHasMore && (
        <View style={{ alignItems: 'center' }}>
          <ChipButton label={chatLoadingMore ? '加载中…' : '加载更早的对话'} onPress={loadMoreChat} />
        </View>
      )}
      <View style={{ maxHeight: 320, gap: 6 }}>
        {chatMessages.map((item) => (
          <View
            key={String(item.messageId)}
            style={item.role === 'USER' ? petCreamStyles.bubbleMine : petCreamStyles.bubblePet}
          >
            <Text style={item.role === 'USER' ? petCreamStyles.bubbleMineText : petCreamStyles.bubblePetText}>{item.content}</Text>
            {!item.isAiReply && item.role === 'PET' && (
              <Text style={{ color: colors.textTertiary, fontSize: 10, marginTop: 2 }}>模板回复</Text>
            )}
          </View>
        ))}
      </View>
      <View style={{ flexDirection: 'row', gap: Spacing.sm }}>
        <TextInput
          value={chatInput}
          onChangeText={setChatInput}
          maxLength={500}
          placeholder="跟宠物聊聊…（每日 20 条）"
          placeholderTextColor={colors.textTertiary}
          style={{
            flex: 1, paddingHorizontal: Spacing.md, paddingVertical: 10, borderRadius: BorderRadius.md,
            borderWidth: 1, borderColor: colors.border, color: colors.text, backgroundColor: colors.bgBase,
          }}
        />
        <CreamButton onPress={sendChat}>发送</CreamButton>
      </View>
    </View>
  )
}
