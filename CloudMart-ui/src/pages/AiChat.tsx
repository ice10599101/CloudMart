import { useState, useRef, useCallback, useEffect } from 'react'
import { history } from 'umi'
import { sendChatMessage, aiSearch } from '@/api/ai'
import type { ChatResponse, SearchResult } from '@/api/ai'
import { useAuthStore } from '@/stores/auth'

interface ChatMessageItem {
  id: number
  role: 'user' | 'assistant'
  content: string
  products?: SearchResult[]
  timestamp: number
}

function ProductCard({ product }: { product: SearchResult }) {
  return (
    <div
      style={{
        background: 'var(--color-bg-container)',
        border: '1px solid var(--color-border)',
        borderRadius: '10px',
        padding: 12,
        marginTop: 8,
        cursor: 'pointer',
        transition: 'all 0.3s ease',
        display: 'flex',
        gap: 12,
      }}
      onClick={() => history.push(`/products/${product.productId}`)}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
        e.currentTarget.style.boxShadow = '0 4px 16px rgba(0,0,0,0.3)'
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = 'var(--color-border)'
        e.currentTarget.style.boxShadow = 'none'
      }}
    >
      <div style={{
        width: 56,
        height: 56,
        borderRadius: '6px',
        overflow: 'hidden',
        flexShrink: 0,
        background: 'rgba(var(--color-primary-rgb), 0.05)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}>
        {product.image ? (
          <img src={product.image} alt={product.productName} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        ) : (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="rgba(var(--color-primary-rgb), 0.3)" strokeWidth="1.5">
            <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
            <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
          </svg>
        )}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {product.productName}
        </div>
        <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--color-primary)', marginTop: 2 }}>¥{product.price.toFixed(2)}</div>
        {product.reason && (
          <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)', marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {product.reason}
          </div>
        )}
      </div>
    </div>
  )
}

export default function AiChatPage() {
  const { isAuthenticated } = useAuthStore()
  const [messages, setMessages] = useState<ChatMessageItem[]>([])
  const [inputValue, setInputValue] = useState('')
  const [sending, setSending] = useState(false)
  const [conversationId, setConversationId] = useState('')
  const [searchMode, setSearchMode] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const msgIdRef = useRef(0)

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  useEffect(() => {
    scrollToBottom()
  }, [messages, scrollToBottom])

  const addMessage = useCallback((role: 'user' | 'assistant', content: string, products?: SearchResult[]) => {
    msgIdRef.current += 1
    setMessages((prev) => [
      ...prev,
      { id: msgIdRef.current, role, content, products, timestamp: Date.now() },
    ])
  }, [])

  const handleSend = async () => {
    const content = inputValue.trim()
    if (!content || sending) return

    if (!isAuthenticated) {
      history.push('/login')
      return
    }

    addMessage('user', content)
    setInputValue('')
    setSending(true)

    try {
      if (searchMode) {
        const { data: res } = await aiSearch(content)
        const productResults: SearchResult[] = res.data ?? []
        const replyText = productResults.length > 0
          ? `为您找到 ${productResults.length} 个相关商品`
          : '未找到相关商品，换个关键词试试？'
        addMessage('assistant', replyText, productResults)
      } else {
        const { data: res } = await sendChatMessage({ message: content, conversationId: conversationId || undefined })
        const chatResponse: ChatResponse = res.data
        setConversationId(chatResponse.conversationId)
        addMessage('assistant', chatResponse.reply)
      }
    } catch {
      addMessage('assistant', '抱歉，服务暂时不可用，请稍后再试。')
    } finally {
      setSending(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div style={{
      height: 'calc(100vh - 64px)',
      display: 'flex',
      background: 'var(--color-bg-base)',
      overflow: 'hidden',
    }}>
      {sidebarOpen && (
        <div style={{
          width: 260,
          background: 'var(--color-bg-footer)',
          borderRight: '1px solid var(--color-border)',
          display: 'flex',
          flexDirection: 'column',
          flexShrink: 0,
        }}>
          <div style={{
            padding: '20px 20px 16px',
            borderBottom: '1px solid var(--color-border)',
          }}>
            <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--color-text-secondary)', marginBottom: 4 }}>
              AI 智能导购
            </div>
            <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
              您的专属购物助手
            </div>
          </div>

          <div style={{ flex: 1, overflowY: 'auto', padding: '12px 12px' }}>
            <div style={{
              padding: '12px 16px',
              borderRadius: '10px',
              background: 'rgba(var(--color-primary-rgb), 0.08)',
              border: '1px solid rgba(var(--color-primary-rgb), 0.15)',
              marginBottom: 8,
            }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-primary)', marginBottom: 2 }}>当前对话</div>
              <div style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                {messages.length > 0 ? `${messages.length} 条消息` : '开始新对话'}
              </div>
            </div>
          </div>

          <div style={{ padding: 16, borderTop: '1px solid var(--color-border)' }}>
            <button
              onClick={() => setSearchMode(!searchMode)}
              style={{
                width: '100%',
                padding: '10px 0',
                border: '1px solid',
                borderColor: searchMode ? 'rgba(var(--color-primary-rgb), 0.3)' : 'var(--color-border)',
                borderRadius: '10px',
                background: searchMode ? 'rgba(var(--color-primary-rgb), 0.08)' : 'transparent',
                color: searchMode ? 'var(--color-primary)' : 'var(--color-text-secondary)',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.3s ease',
              }}
            >
              {searchMode ? '🔍 搜索模式' : '💬 对话模式'}
            </button>
          </div>
        </div>
      )}

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <div style={{
          padding: '12px 20px',
          borderBottom: '1px solid var(--color-border)',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
        }}>
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            style={{
              background: 'transparent',
              border: '1px solid var(--color-border)',
              borderRadius: '6px',
              color: 'var(--color-text-secondary)',
              padding: '6px 8px',
              cursor: 'pointer',
              fontSize: 14,
            }}
          >
            {sidebarOpen ? '◀' : '▶'}
          </button>
          <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--color-text-secondary)' }}>
            {searchMode ? '商品搜索' : 'AI 对话'}
          </div>
        </div>

        <div style={{
          flex: 1,
          overflowY: 'auto',
          padding: '24px 28px',
        }}>
          {messages.length === 0 && (
            <div style={{ textAlign: 'center', padding: '80px 0' }}>
              <div style={{
                width: 72,
                height: 72,
                borderRadius: '50%',
                background: 'rgba(var(--color-primary-rgb), 0.1)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 20px',
              }}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="1.5">
                  <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12 6.48 2 12 2z" />
                  <path d="M8 14s1.5 2 4 2 4-2 4-2" />
                  <line x1="9" y1="9" x2="9.01" y2="9" />
                  <line x1="15" y1="9" x2="15.01" y2="9" />
                </svg>
              </div>
              <div style={{ fontSize: 22, fontWeight: 700, color: 'var(--color-text-secondary)', marginBottom: 8 }}>
                你好，我是 AI 导购助手
              </div>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: 15, marginBottom: 32 }}>
                {searchMode ? '输入关键词，快速搜索商品' : '告诉我你想找什么，我来帮你推荐'}
              </div>
              <div style={{ display: 'flex', gap: 10, justifyContent: 'center', flexWrap: 'wrap' }}>
                {['推荐手机', '笔记本电脑', '春季穿搭', '家居好物'].map((suggestion) => (
                  <button
                    key={suggestion}
                    onClick={() => setInputValue(suggestion)}
                    style={{
                      padding: '8px 18px',
                      border: '1px solid var(--color-border)',
                      borderRadius: 20,
                      background: 'transparent',
                      color: 'var(--color-text-secondary)',
                      fontSize: 13,
                      cursor: 'pointer',
                      transition: 'all 0.3s ease',
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.3)'
                      e.currentTarget.style.color = 'var(--color-primary)'
                      e.currentTarget.style.background = 'rgba(var(--color-primary-rgb), 0.05)'
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.borderColor = 'var(--color-border)'
                      e.currentTarget.style.color = 'var(--color-text-secondary)'
                      e.currentTarget.style.background = 'transparent'
                    }}
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          )}

          {messages.map((msg) => (
            <div
              key={msg.id}
              style={{
                display: 'flex',
                justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
                marginBottom: 20,
              }}
            >
              <div style={{
                display: 'flex',
                gap: 12,
                maxWidth: '70%',
                flexDirection: msg.role === 'user' ? 'row-reverse' : 'row',
              }}>
                <div style={{
                  width: 36,
                  height: 36,
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                  background: msg.role === 'user'
                    ? 'var(--color-gradient-primary)'
                    : 'rgba(var(--color-primary-rgb), 0.1)',
                }}>
                  {msg.role === 'user' ? (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                      <circle cx="12" cy="7" r="4" />
                    </svg>
                  ) : (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="2">
                      <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12 6.48 2 12 2z" />
                      <path d="M8 14s1.5 2 4 2 4-2 4-2" />
                    </svg>
                  )}
                </div>
                <div>
                  <div style={{
                    padding: '12px 16px',
                    borderRadius: msg.role === 'user'
                      ? '16px 16px 4px 16px'
                      : '16px 16px 16px 4px',
                    background: msg.role === 'user'
                      ? 'var(--color-gradient-primary)'
                      : 'var(--color-bg-container)',
                    border: msg.role === 'user' ? 'none' : '1px solid var(--color-border)',
                    color: msg.role === 'user' ? '#fff' : '#FFFFFF',
                    fontSize: 14,
                    lineHeight: 1.7,
                    wordBreak: 'break-word',
                  }}>
                    {msg.content}
                  </div>
                  {msg.products && msg.products.length > 0 && (
                    <div style={{ marginTop: 8 }}>
                      {msg.products.map((product) => (
                        <ProductCard key={product.productId} product={product} />
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}

          {sending && (
            <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
              <div style={{
                width: 36,
                height: 36,
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'rgba(var(--color-primary-rgb), 0.1)',
              }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" strokeWidth="2">
                  <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12 6.48 2 12 2z" />
                  <path d="M8 14s1.5 2 4 2 4-2 4-2" />
                </svg>
              </div>
              <div style={{
                padding: '12px 16px',
                borderRadius: '16px 16px 16px 4px',
                background: 'var(--color-bg-container)',
                border: '1px solid var(--color-border)',
              }}>
                <div style={{
                  width: 20,
                  height: 20,
                  border: '2px solid var(--color-border)',
                  borderTopColor: 'var(--color-primary)',
                  borderRadius: '50%',
                  animation: 'spin 0.8s linear infinite',
                }} />
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <div style={{
          padding: '16px 28px 20px',
          borderTop: '1px solid var(--color-border)',
          display: 'flex',
          gap: 12,
        }}>
          <input
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={searchMode ? '输入关键词搜索商品...' : '输入你想咨询的问题...'}
            style={{
              flex: 1,
              padding: '12px 20px',
              border: '1px solid var(--color-border)',
              borderRadius: '16px',
              background: 'var(--color-bg-input)',
              color: 'var(--color-text-secondary)',
              fontSize: 14,
              outline: 'none',
              transition: 'border-color 0.3s ease',
            }}
            onFocus={(e) => {
              e.currentTarget.style.borderColor = 'rgba(var(--color-primary-rgb), 0.4)'
            }}
            onBlur={(e) => {
              e.currentTarget.style.borderColor = 'var(--color-border)'
            }}
          />
          <button
            onClick={handleSend}
            disabled={!inputValue.trim() || sending}
            style={{
              padding: '12px 24px',
              border: 'none',
              borderRadius: '16px',
              background: !inputValue.trim() || sending
                ? 'var(--color-bg-elevated)'
                : 'var(--color-gradient-primary)',
              color: !inputValue.trim() || sending ? 'var(--color-text-tertiary)' : '#fff',
              fontSize: 14,
              fontWeight: 600,
              cursor: !inputValue.trim() || sending ? 'not-allowed' : 'pointer',
              boxShadow: !inputValue.trim() || sending ? 'none' : '0 4px 16px rgba(var(--color-primary-rgb), 0.3)',
              transition: 'all 0.3s ease',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
            发送
          </button>
        </div>
      </div>
    </div>
  )
}
