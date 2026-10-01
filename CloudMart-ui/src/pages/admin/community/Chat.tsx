import { useState, useCallback } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ProColumns } from '@ant-design/pro-components'
import { Modal, Tag, Input, message } from 'antd'
import { getChatConversations, getChatMessages } from '@/api/admin/community'
import { safeProTableRequest } from '@/utils/proTable'

interface ConversationRecord {
  id: number
  otherUserId: number
  otherUserNickname: string
  lastMessage: string
  lastMessageTime: string
  unreadCount: number
}

interface MessageRecord {
  id: number
  conversationId: number
  senderId: number
  senderNickname: string
  senderAvatar: string
  content: string
  type: string
  isRecalled: boolean
  createdAt: string
}

const columns: ProColumns<ConversationRecord>[] = [
  { title: 'ID', dataIndex: 'id', width: 80, search: false },
  { title: '会话用户', dataIndex: 'otherUserNickname', width: 200 },
  { title: '最后消息', dataIndex: 'lastMessage', search: false, ellipsis: true },
  {
    title: '未读数',
    dataIndex: 'unreadCount',
    width: 90,
    search: false,
    render: (_, r) => (r.unreadCount > 0 ? <Tag color="red">{r.unreadCount}</Tag> : <Tag>0</Tag>),
  },
  {
    title: '最后消息时间',
    dataIndex: 'lastMessageTime',
    valueType: 'dateTime',
    width: 180,
    search: false,
  },
  {
    title: '操作',
    valueType: 'option',
    width: 100,
    render: (_, record, __, action) => (
      <a
        onClick={() => {
          action?.startEditable?.(record.id)
        }}
      >
        查看消息
      </a>
    ),
  },
]

export default function ChatManagement() {
  const [messagesModalOpen, setMessagesModalOpen] = useState(false)
  const [currentConversationId, setCurrentConversationId] = useState<number | null>(null)
  const [messages, setMessages] = useState<MessageRecord[]>([])
  const [messagesLoading, setMessagesLoading] = useState(false)

  // S02：私聊正文读取强制工单上下文（caseId/reason）——后端无此参数直接拒绝，行为入操作审计
  const fetchMessages = useCallback(async (convId: number, caseId: string, reason: string) => {
    setMessagesLoading(true)
    try {
      const res = await getChatMessages(convId, { page: 1, pageSize: 50, caseId, reason })
      setMessages(res.data.data ?? [])
    } catch {
      setMessages([])
      message.error('读取失败：请确认具备 chat:content:read 权限且工单信息有效')
    } finally {
      setMessagesLoading(false)
    }
  }, [message])

  const handleViewMessages = useCallback(
    (record: ConversationRecord) => {
      let caseIdInput = ''
      let reasonInput = ''
      Modal.confirm({
        title: '私聊正文读取登记',
        icon: null,
        content: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Input
              placeholder='工单 ID（必填，如 CASE-100）'
              onChange={(e) => { caseIdInput = e.target.value }}
            />
            <Input.TextArea
              placeholder='读取理由（必填，将记入操作审计）'
              onChange={(e) => { reasonInput = e.target.value }}
            />
          </div>
        ),
        okText: '登记并读取',
        cancelText: '取消',
        onOk: () => {
          if (!caseIdInput.trim() || !reasonInput.trim()) {
            message.error('工单 ID 与读取理由为必填项')
            return Promise.reject()
          }
          setCurrentConversationId(record.id)
          setMessagesModalOpen(true)
          return fetchMessages(record.id, caseIdInput.trim(), reasonInput.trim())
        },
      })
    },
    [fetchMessages, message],
  )

  const msgColumns: ProColumns<MessageRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '发送者', dataIndex: 'senderNickname', width: 120 },
    {
      title: '内容',
      dataIndex: 'content',
      ellipsis: true,
      render: (_, r) =>
        r.isRecalled ? <span style={{ color: '#999', fontStyle: 'italic' }}>已撤回</span> : r.content,
    },
    { title: '类型', dataIndex: 'type', width: 80, render: (_, r) => <Tag>{r.type}</Tag> },
    {
      title: '状态',
      dataIndex: 'isRecalled',
      width: 80,
      render: (_, r) => (r.isRecalled ? <Tag color="orange">已撤回</Tag> : <Tag color="green">正常</Tag>),
    },
    { title: '时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 170 },
  ]

  return (
    <div>
      <ProTable<ConversationRecord>
        columns={columns.map((col) => {
          if (col.valueType === 'option') {
            return {
              ...col,
              render: (_, record) => (
                <a onClick={() => handleViewMessages(record)}>查看消息</a>
              ),
            }
          }
          return col
        })}
        request={async (params) => {
          return safeProTableRequest<ConversationRecord>(() =>
            getChatConversations({
              page: params.current,
              pageSize: params.pageSize,
            })
          )
        }}
        rowKey="id"
        search={false}
        pagination={{ pageSize: 20 }}
        headerTitle="私信会话管理"
      />

      <Modal
        title={`会话消息 #${currentConversationId}`}
        open={messagesModalOpen}
        onCancel={() => setMessagesModalOpen(false)}
        footer={null}
        width={800}
        loading={messagesLoading}
      >
        <ProTable<MessageRecord>
          columns={msgColumns}
          dataSource={messages}
          rowKey="id"
          search={false}
          pagination={false}
          size="small"
          toolBarRender={false}
        />
      </Modal>
    </div>
  )
}
