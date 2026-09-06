import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, history } from 'umi'
import { Spin, Dropdown } from 'antd'
import { message } from '@/utils/appMessage'
import { ArrowLeftOutlined, MessageOutlined, UndoOutlined } from '@ant-design/icons'
import Skeleton from '@/components/Skeleton'
import {
  getConversations,
  getMessages,
  sendMessage,
  createConversation,
  markConversationRead,
  recallMessage,
  type ChatConversation,
  type ChatMessage,
} from '@/api/chat'
import { searchUsers, type SearchUserResult } from '@/api/community'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { timeAgo, parseServerTime } from '@/utils/format'
import styles from './Chat.module.css'

function formatMessageTime(dateStr: string): string {
  const ts = parseServerTime(dateStr)
  if (ts === null) return ''
  const date = new Date(ts)
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}

function formatDateSeparator(dateStr: string): string {
  const ts = parseServerTime(dateStr)
  if (ts === null) return ''
  const date = new Date(ts)
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === today.toDateString()) return '今天'
  if (date.toDateString() === yesterday.toDateString()) return '昨天'
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function isSameDay(a: string, b: string): boolean {
  const ta = parseServerTime(a)
  const tb = parseServerTime(b)
  if (ta === null || tb === null) return false
  return new Date(ta).toDateString() === new Date(tb).toDateString()
}

function AvatarCircle({ name, avatar, size = 48 }: { name: string; avatar: string; size?: number }) {
  if (avatar) {
    return (
      <div className={styles.conversationAvatar} style={{ width: size, height: size, fontSize: size * 0.38 }}>
        <img src={avatar} alt={name} />
      </div>
    )
  }
  return (
    <div className={styles.conversationAvatar} style={{ width: size, height: size, fontSize: size * 0.38 }}>
      {name.charAt(0)}
    </div>
  )
}

export default function Chat() {
  const { conversationId: urlConversationId } = useParams<{ conversationId?: string }>()
  const currentUser = useAuthStore((s) => s.user)
  const notificationWs = useNotificationStore((s) => s.ws)

  const [conversations, setConversations] = useState<ChatConversation[]>([])
  const [activeConversationId, setActiveConversationId] = useState<number | null>(
    urlConversationId ? Number(urlConversationId) : null,
  )
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [inputText, setInputText] = useState('')
  const [searchText, setSearchText] = useState('')
  const [loading, setLoading] = useState(true)
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [hasMoreMessages, setHasMoreMessages] = useState(false)
  const [sending, setSending] = useState(false)
  const [showNewChat, setShowNewChat] = useState(false)
  const [userSearchText, setUserSearchText] = useState('')
  const [userSearchResults, setUserSearchResults] = useState<SearchUserResult[]>([])

  const messageListRef = useRef<HTMLDivElement>(null)
  const textInputRef = useRef<HTMLTextAreaElement>(null)
  const shouldScrollToBottom = useRef(true)

  const activeConversation = conversations.find((c) => c.id === activeConversationId) ?? null

  useEffect(() => {
    if (!notificationWs) return

    const handleMessage = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type !== 'CHAT_MESSAGE') return

        const incomingMsg: ChatMessage = {
          id: data.messageId,
          conversationId: data.conversationId,
          senderId: data.senderId,
          senderNickname: '',
          senderAvatar: '',
          content: data.content,
          type: data.msgType || 'TEXT',
          isRecalled: false,
          createdAt: data.createdAt,
        }

        if (data.conversationId === activeConversationId) {
          setMessages((prev) => {
            if (prev.some((m) => m.id === incomingMsg.id)) return prev
            return [...prev, incomingMsg]
          })
          shouldScrollToBottom.current = true
          markConversationRead(data.conversationId).catch(() => {})
        }

        setConversations((prev) =>
          prev.map((c) =>
            c.id === data.conversationId
              ? { ...c, lastMessage: data.content, lastMessageTime: data.createdAt, unreadCount: c.unreadCount + (c.id === activeConversationId ? 0 : 1) }
              : c,
          ),
        )
      } catch {
        // ignore non-JSON messages
      }
    }

    const handleRecall = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type !== 'CHAT_RECALL') return

        setMessages((prev) =>
          prev.map((m) =>
            m.id === data.messageId ? { ...m, isRecalled: true, content: '该消息已撤回' } : m,
          ),
        )
      } catch {
        // ignore
      }
    }

    notificationWs.addEventListener('message', handleMessage)
    notificationWs.addEventListener('message', handleRecall)
    return () => {
      notificationWs.removeEventListener('message', handleMessage)
      notificationWs.removeEventListener('message', handleRecall)
    }
  }, [notificationWs, activeConversationId])

  const fetchConversations = useCallback(async () => {
    try {
      const res = await getConversations()
      setConversations(res.data.data ?? [])
    } catch {
      setConversations([])
    }
  }, [])

  const fetchMessages = useCallback(async (convId: number, beforeId?: number) => {
    setMessagesLoading(true)
    try {
      const res = await getMessages(convId, beforeId, 30)
      const items = (res.data.data ?? []) as ChatMessage[]
      if (beforeId) {
        setMessages((prev) => [...items, ...prev])
      } else {
        setMessages(items)
      }
      setHasMoreMessages(items.length >= 30)
    } catch {
      if (!beforeId) setMessages([])
      setHasMoreMessages(false)
    } finally {
      setMessagesLoading(false)
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    async function init() {
      setLoading(true)
      await fetchConversations()
      if (!cancelled) setLoading(false)
    }
    init()
    return () => { cancelled = true }
  }, [fetchConversations])

  useEffect(() => {
    if (urlConversationId) {
      setActiveConversationId(Number(urlConversationId))
    }
  }, [urlConversationId])

  useEffect(() => {
    if (!activeConversationId) return
    fetchMessages(activeConversationId)
    markConversationRead(activeConversationId).catch(() => {})
    setConversations((prev) =>
      prev.map((c) => (c.id === activeConversationId ? { ...c, unreadCount: 0 } : c)),
    )
  }, [activeConversationId, fetchMessages])

  useEffect(() => {
    if (shouldScrollToBottom.current && messageListRef.current) {
      messageListRef.current.scrollTop = messageListRef.current.scrollHeight
    }
  }, [messages])

  const handleSelectConversation = useCallback((convId: number) => {
    setActiveConversationId(convId)
    setInputText('')
    shouldScrollToBottom.current = true
    setShowNewChat(false)
  }, [])

  const handleSendMessage = useCallback(async () => {
    const text = inputText.trim()
    if (!text || !activeConversationId || sending) return

    const optimisticMessage: ChatMessage = {
      id: Date.now(),
      conversationId: activeConversationId,
      senderId: currentUser?.id ?? 0,
      senderNickname: currentUser?.nickname ?? '我',
      senderAvatar: currentUser?.avatar ?? '',
      content: text,
      type: 'TEXT',
      isRecalled: false,
      createdAt: new Date().toISOString(),
    }

    setMessages((prev) => [...prev, optimisticMessage])
    setInputText('')
    shouldScrollToBottom.current = true

    setSending(true)
    try {
      const res = await sendMessage(activeConversationId, text)
      const serverMessage = res.data.data
      if (serverMessage) {
        setMessages((prev) =>
          prev.map((m) => (m.id === optimisticMessage.id ? { ...serverMessage, isRecalled: serverMessage.isRecalled ?? false } : m)),
        )
      }
    } catch {
      // keep optimistic message
    } finally {
      setSending(false)
    }

    setConversations((prev) =>
      prev.map((c) =>
        c.id === activeConversationId
          ? { ...c, lastMessage: text, lastMessageTime: new Date().toISOString() }
          : c,
      ),
    )
  }, [inputText, activeConversationId, sending, currentUser])

  const handleRecallMessage = useCallback(async (msgId: number) => {
    try {
      await recallMessage(msgId)
      setMessages((prev) =>
        prev.map((m) => (m.id === msgId ? { ...m, isRecalled: true, content: '该消息已撤回' } : m)),
      )
      message.success('消息已撤回')
    } catch {
      message.error('撤回失败，可能已超过2分钟')
    }
  }, [])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        handleSendMessage()
      }
    },
    [handleSendMessage],
  )

  const handleTextareaInput = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInputText(e.target.value)
    const textarea = e.target
    textarea.style.height = 'auto'
    textarea.style.height = `${Math.min(textarea.scrollHeight, 90)}px`
  }, [])

  const handleScrollUp = useCallback(() => {
    if (!messageListRef.current || !activeConversationId || !hasMoreMessages) return
    const { scrollTop } = messageListRef.current
    if (scrollTop < 100) {
      const oldScrollHeight = messageListRef.current.scrollHeight
      fetchMessages(activeConversationId, messages[0]?.id).then(() => {
        shouldScrollToBottom.current = false
        requestAnimationFrame(() => {
          if (messageListRef.current) {
            messageListRef.current.scrollTop = messageListRef.current.scrollHeight - oldScrollHeight
          }
        })
      })
    }
  }, [activeConversationId, hasMoreMessages, messages, fetchMessages])

  const handleMobileBack = useCallback(() => {
    setActiveConversationId(null)
    history.replace('/chat')
  }, [])

  const handleSearchUsers = useCallback(async () => {
    if (!userSearchText.trim()) {
      setUserSearchResults([])
      return
    }
    try {
      const res = await searchUsers(userSearchText.trim())
      setUserSearchResults(res.data.data ?? [])
    } catch {
      setUserSearchResults([])
    }
  }, [userSearchText])

  const handleStartChat = useCallback(async (otherUserId: number) => {
    try {
      const res = await createConversation(otherUserId)
      const conv = res.data.data
      if (conv) {
        setConversations((prev) => {
          if (prev.some((c) => c.id === conv.id)) return prev
          return [conv, ...prev]
        })
        setActiveConversationId(conv.id)
        setShowNewChat(false)
        setUserSearchText('')
        setUserSearchResults([])
      }
    } catch {
      message.error('创建会话失败')
    }
  }, [])

  const filteredConversations = searchText
    ? conversations.filter((c) =>
        c.otherUserNickname.toLowerCase().includes(searchText.toLowerCase()),
      )
    : conversations

  const renderMessages = () => {
    const elements: React.ReactNode[] = []
    let lastDateStr = ''

    messages.forEach((msg) => {
      if (!isSameDay(msg.createdAt, lastDateStr)) {
        elements.push(
          <div key={`date-${msg.id}`} className={styles.dateSeparator}>
            <span className={styles.dateSeparatorText}>{formatDateSeparator(msg.createdAt)}</span>
          </div>,
        )
        lastDateStr = msg.createdAt
      }

      const isSelf = msg.senderId === (currentUser?.id ?? 0)
      const canRecall = isSelf && !msg.isRecalled && (Date.now() - (parseServerTime(msg.createdAt) ?? 0) < 120000)

      if (msg.isRecalled) {
        elements.push(
          <div key={msg.id} className={`${styles.messageRow} ${styles.messageRowCenter}`}>
            <span className={styles.recalledText}>该消息已撤回</span>
          </div>,
        )
        return
      }

      elements.push(
        <div
          key={msg.id}
          className={`${styles.messageRow} ${isSelf ? styles.messageRowSelf : styles.messageRowOther}`}
        >
          {!isSelf && (
            <AvatarCircle name={msg.senderNickname} avatar={msg.senderAvatar} size={36} />
          )}
          <div className={styles.messageContent}>
            <Dropdown
              menu={{
                items: canRecall
                  ? [{ key: 'recall', label: '撤回', icon: <UndoOutlined /> }]
                  : [],
                onClick: ({ key }) => {
                  if (key === 'recall') handleRecallMessage(msg.id)
                },
              }}
              trigger={['contextMenu']}
            >
              <div>
                {msg.type === 'IMAGE' ? (
                  <div className={styles.messageImage}>
                    <img src={msg.content} alt="图片消息" />
                  </div>
                ) : (
                  <div className={`${styles.messageBubble} ${isSelf ? styles.messageBubbleSelf : styles.messageBubbleOther}`}>
                    {msg.content}
                  </div>
                )}
              </div>
            </Dropdown>
            <div className={`${styles.messageTime} ${isSelf ? styles.messageTimeSelf : styles.messageTimeOther}`}>
              {formatMessageTime(msg.createdAt)}
            </div>
          </div>
          {isSelf && (
            <AvatarCircle name={msg.senderNickname} avatar={msg.senderAvatar} size={36} />
          )}
        </div>,
      )
    })

    return elements
  }

  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <div className={styles.sidebar}>
          <div className={styles.sidebarHeader}>
            <h1 className={styles.sidebarTitle}>私信</h1>
            <button
              type="button"
              className={styles.newChatBtn}
              onClick={() => setShowNewChat(!showNewChat)}
            >
              +
            </button>
          </div>

          {showNewChat && (
            <div className={styles.newChatPanel}>
              <div className={styles.newChatSearch}>
                <input
                  type="text"
                  className={styles.searchInput}
                  placeholder="搜索用户..."
                  value={userSearchText}
                  onChange={(e) => setUserSearchText(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') handleSearchUsers() }}
                />
                <button type="button" className={styles.searchBtn} onClick={handleSearchUsers}>
                  搜索
                </button>
              </div>
              {userSearchResults.length > 0 && (
                <div className={styles.userSearchResults}>
                  {userSearchResults.map((u) => (
                    <div
                      key={u.id}
                      className={styles.userSearchItem}
                      onClick={() => handleStartChat(u.id)}
                    >
                      <AvatarCircle name={u.nickname} avatar={u.avatar} size={36} />
                      <span className={styles.userSearchName}>{u.nickname}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className={styles.searchWrap}>
            <input
              type="text"
              className={styles.searchInput}
              placeholder="搜索联系人..."
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
            />
          </div>
          <div className={styles.conversationList}>
            {loading ? (
              <div className={styles.loadingWrap}>
                <Skeleton variant="list" count={5} />
              </div>
            ) : filteredConversations.length === 0 ? (
              <div className={styles.emptyState}>
                <MessageOutlined className={styles.emptyIcon} />
                <span className={styles.emptyText}>暂无对话</span>
              </div>
            ) : (
              filteredConversations.map((conv) => (
                <div
                  key={conv.id}
                  className={`${styles.conversationItem} ${activeConversationId === conv.id ? styles.conversationItemActive : ''}`}
                  onClick={() => handleSelectConversation(conv.id)}
                >
                  <AvatarCircle name={conv.otherUserNickname} avatar={conv.otherUserAvatar} />
                  <div className={styles.conversationInfo}>
                    <div className={styles.conversationTop}>
                      <span className={styles.conversationNickname}>{conv.otherUserNickname}</span>
                      <span className={styles.conversationTime}>
                        {timeAgo(conv.lastMessageTime)}
                      </span>
                    </div>
                    <div className={styles.conversationBottom}>
                      <span className={styles.conversationPreview}>{conv.lastMessage}</span>
                      {conv.unreadCount > 0 && (
                        <span className={styles.unreadBadge}>{conv.unreadCount}</span>
                      )}
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className={`${styles.chatArea} ${activeConversationId ? styles.chatAreaVisible : ''}`}>
          {activeConversation ? (
            <>
              <div className={styles.chatHeader}>
                <button
                  type="button"
                  className={styles.mobileBackBtn}
                  onClick={handleMobileBack}
                >
                  <ArrowLeftOutlined />
                </button>
                <AvatarCircle
                  name={activeConversation.otherUserNickname}
                  avatar={activeConversation.otherUserAvatar}
                  size={36}
                />
                <span className={styles.chatHeaderNickname}>
                  {activeConversation.otherUserNickname}
                </span>
              </div>

              <div
                className={styles.messageList}
                ref={messageListRef}
                onScroll={handleScrollUp}
              >
                {hasMoreMessages && (
                  <div className={styles.loadMoreWrap}>
                    <button
                      type="button"
                      className={styles.loadMoreBtn}
                      onClick={() => {
                        if (activeConversationId && messages.length > 0) {
                          const oldScrollHeight = messageListRef.current?.scrollHeight ?? 0
                          fetchMessages(activeConversationId, messages[0].id).then(() => {
                            shouldScrollToBottom.current = false
                            requestAnimationFrame(() => {
                              if (messageListRef.current) {
                                messageListRef.current.scrollTop =
                                  messageListRef.current.scrollHeight - oldScrollHeight
                              }
                            })
                          })
                        }
                      }}
                    >
                      加载更早消息
                    </button>
                  </div>
                )}
                {messagesLoading ? (
                  <div className={styles.loadingWrap}>
                    <Spin />
                  </div>
                ) : (
                  renderMessages()
                )}
              </div>

              <div className={styles.inputArea}>
                <div className={styles.inputWrap}>
                  <textarea
                    ref={textInputRef}
                    className={styles.textInput}
                    placeholder="输入消息..."
                    value={inputText}
                    onChange={handleTextareaInput}
                    onKeyDown={handleKeyDown}
                    rows={1}
                  />
                </div>
                <button
                  type="button"
                  className={styles.sendBtn}
                  disabled={!inputText.trim() || sending}
                  onClick={handleSendMessage}
                >
                  发送
                </button>
              </div>
            </>
          ) : (
            <div className={styles.emptyState}>
              <MessageOutlined className={styles.emptyIcon} />
              <span className={styles.emptyText}>选择一个对话开始聊天</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
