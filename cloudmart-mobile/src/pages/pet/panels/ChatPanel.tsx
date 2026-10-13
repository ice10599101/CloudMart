import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Input, Text, View } from '@tarojs/components'
import {
  petApi, petApiV2,
  type PetChatMessage,
  type PetChatPersona,
} from '@/api/pet'
import { getPersistedIntentKey } from '@/utils/request'
import { friendlyError } from '../constants'
import styles from '../index.module.scss'

/**
 * 聊天面板：人设卡 / 历史 cursor 分页 / 发送（R30 自 index.tsx 拆出，行为不变）。
 * 断网/超时（结果未知）按持久意图原键查终态，绝不生成新意图（R25）。
 */
export function ChatPanel() {
  const [persona, setPersona] = useState<PetChatPersona | null>(null)
  const [chatMessages, setChatMessages] = useState<PetChatMessage[]>([])
  const [chatInput, setChatInput] = useState('')
  // 聊天历史分页（cursor）
  const [chatCursor, setChatCursor] = useState<number | string | null>(null)
  const [chatHasMore, setChatHasMore] = useState(false)
  const [chatLoadingMore, setChatLoadingMore] = useState(false)

  const toast = (msg: string) => Taro.showToast({ title: msg, icon: 'none' })

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
    } catch (error) {
      toast(friendlyError(error))
    } finally {
      setChatLoadingMore(false)
    }
  }

  const load = useCallback(async () => {
    // F8：人设卡与首屏历史并行（人设展示型数据，失败静默）
    void (async () => {
      try {
        const { data: res } = await petApi.getChatPersona()
        setPersona(res.data)
      } catch {
        // 人设卡展示型数据：忽略
      }
    })()
    try {
      const { data: res } = await petApi.chatHistory({ pageSize: 30 })
      const page = (res.data || []).slice().reverse()
      setChatMessages(page)
      const oldest = page[0]
      setChatCursor(oldest ? oldest.messageId : null)
      setChatHasMore((res.data || []).length >= 30 && Boolean(oldest))
    } catch (error) {
      toast(friendlyError(error))
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <View className={styles.chatPanel}>
      {persona && (
        <View className={styles.chatPersonaCard}>
          <Text className={styles.chatPersonaTitle}>
            {persona.name} · {persona.intimacyLevelName}（Lv.{persona.intimacyLevel}）
          </Text>
          <Text className={styles.chatPersonaText}>
            {persona.phrase ? `「${persona.phrase}」` : ''}
            {persona.careerName ? ` · 现任${persona.careerName}` : ''}
          </Text>
        </View>
      )}
      {chatHasMore && (
        <View className={styles.chatMoreRow} onClick={loadMoreChat}>
          <Text className={styles.chatMoreText}>{chatLoadingMore ? '加载中…' : '加载更早的对话'}</Text>
        </View>
      )}
      <View className={styles.chatList}>
        {chatMessages.map((item) => (
          <View key={String(item.messageId)} className={item.role === 'USER' ? styles.chatMine : styles.chatPet}>
            <Text className={styles.chatText}>{item.content}</Text>
            {/* AI 降级标识：模板回复（isAiReply=false）时提示，避免用户以为宠物"变笨了" */}
            {item.role === 'PET' && !item.isAiReply && (
              <Text className={styles.chatTag}>模板回复</Text>
            )}
          </View>
        ))}
      </View>
      <View className={styles.chatInputRow}>
        <Input
          className={styles.chatInput}
          value={chatInput}
          maxlength={500}
          placeholder="跟宠物聊聊…（每日 20 条）"
          onInput={(e) => setChatInput(e.detail.value)}
        />
        <Button className={styles.miniBtn} onClick={async () => {
          const text = chatInput.trim()
          if (!text) return
          setChatInput('')
          try {
            const { data: res } = await petApi.chat(text)
            if (res.data) {
              setChatMessages((prev) => [...prev, {
                messageId: `${res.data.messageId}-u`, role: 'USER', content: text, isAiReply: false, createdAt: null,
              }, res.data])
            }
          } catch (error) {
            // R25：处理中提示等待；断网/超时（结果未知）按持久意图原键查终态，不生成新意图
            const code = (error as { code?: string }).code
            if (code === 'PET_REQUEST_IN_PROGRESS') {
              toast('它还在想回复…稍等片刻再查看结果')
              return
            }
            if (code === 'NETWORK_ERROR') {
              const requestKey = getPersistedIntentKey('POST', '/pet/chat', { message: text })
              if (requestKey) {
                try {
                  const { data: status } = await petApiV2.getChatRequestStatus(requestKey)
                  if (status.data?.status === 'SUCCEEDED' && status.data.reply) {
                    const reply = status.data.reply as unknown as PetChatMessage
                    setChatMessages((prev) => [...prev, {
                      messageId: `${reply.messageId}-u`, role: 'USER', content: text, isAiReply: false, createdAt: null,
                    }, reply])
                    return
                  }
                  if (status.data?.status === 'PROCESSING') {
                    toast('它还在想回复…稍后刷新即可看到')
                    return
                  }
                } catch {
                  // 查询也失败：落到通用网络提示
                }
              }
              toast('网络不稳定，稍后可按原消息重试（不会重复计费）')
              return
            }
            toast(friendlyError(error))
          }
        }}>发送</Button>
      </View>
    </View>
  )
}
