import { useState, useCallback, useRef } from 'react'
import { useMessage } from '@/utils/useMessage'
import { ProTable, ModalForm, ProFormTextArea, ProFormText, ProFormSelect } from '@ant-design/pro-components'
import type { ProColumns, ActionType } from '@ant-design/pro-components'
import { Tabs, Tag, Button, Popconfirm, Modal, Descriptions } from 'antd'
import {
  CheckOutlined,
  CloseOutlined,
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import {
  getPendingReviewPosts,
  approvePost,
  rejectPost,
  getSensitiveWords,
  addSensitiveWord,
  deleteSensitiveWord,
  updateSensitiveWord,
  refreshSensitiveWordCache,
} from '@/api/admin/community'
import type { SensitiveWordRecord } from '@/api/admin/community'
import { safeProTableRequest } from '@/utils/proTable'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface PendingPostRecord {
  id: number
  title: string
  authorNickname: string
  contentSummary: string
  createdAt: string
}

const LEVEL_MAP: Record<number, { label: string; color: string }> = {
  1: { label: '替换', color: 'blue' },
  2: { label: '审核', color: 'orange' },
  3: { label: '拒绝', color: 'red' },
}

const CATEGORY_OPTIONS = [
  { label: '通用', value: 'GENERAL' },
  { label: '政治', value: 'POLITICAL' },
  { label: '色情', value: 'PORNOGRAPHIC' },
  { label: '广告', value: 'ADVERTISING' },
  { label: '侮辱', value: 'INSULT' },
]

const LEVEL_OPTIONS = [
  { label: '替换', value: 1 },
  { label: '审核', value: 2 },
  { label: '拒绝', value: 3 },
]

export default function ReviewManagement() {
  const message = useMessage()
  const [activeTab, setActiveTab] = useState('pending')
  const [rejectModalOpen, setRejectModalOpen] = useState(false)
  const [rejectingPostId, setRejectingPostId] = useState<number | null>(null)
  const [addWordModalOpen, setAddWordModalOpen] = useState(false)
  const [detailPost, setDetailPost] = useState<PendingPostRecord | null>(null)
  const [editWordModalOpen, setEditWordModalOpen] = useState(false)
  const [editingWord, setEditingWord] = useState<SensitiveWordRecord | null>(null)
  const pendingRef = useRef<ActionType>(null)
  const wordRef = useRef<ActionType>(null)
  const { confirmSubmit: confirmSubmit1, createHandleOpenChange: createHandleOpenChange1 } = useModalConfirm()
  const { confirmSubmit: confirmSubmit2, createHandleOpenChange: createHandleOpenChange2 } = useModalConfirm()

  const handleApprove = useCallback(async (id: number) => {
    try {
      await approvePost(id)
      message.success('已通过审核')
      pendingRef.current?.reload()
    } catch {
      message.error('操作失败')
    }
  }, [])

  const handleReject = useCallback(async (reason: string) => {
    return confirmSubmit1(async () => {
      if (rejectingPostId === null) return
      try {
        await rejectPost(rejectingPostId, { reason })
        message.success('已拒绝')
        setRejectingPostId(null)
        pendingRef.current?.reload()
      } catch {
        message.error('操作失败')
        throw new Error()
      }
    })
  }, [rejectingPostId])

  const handleDeleteWord = useCallback(async (id: number) => {
    try {
      await deleteSensitiveWord(id)
      message.success('已删除')
      wordRef.current?.reload()
    } catch {
      message.error('删除失败')
    }
  }, [])

  const handleRefreshCache = useCallback(async () => {
    try {
      await refreshSensitiveWordCache()
      message.success('缓存已刷新')
    } catch {
      message.error('刷新失败')
    }
  }, [])

  const pendingColumns: ProColumns<PendingPostRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '标题', dataIndex: 'title', width: 200, ellipsis: true },
    { title: '作者昵称', dataIndex: 'authorNickname', width: 140 },
    { title: '内容摘要', dataIndex: 'contentSummary', search: false, ellipsis: true },
    { title: '发布时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 180, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      render: (_, record) => (
        <div style={{ display: 'flex', gap: 8 }}>
          <Button type="link" size="small" onClick={() => setDetailPost(record)}>详情</Button>
          <Popconfirm
            title="确认通过审核？"
            onConfirm={() => handleApprove(record.id)}
            okText="确认"
            cancelText="取消"
          >
            <Button type="primary" size="small" icon={<CheckOutlined />}>
              通过
            </Button>
          </Popconfirm>
          <Button
            danger
            size="small"
            icon={<CloseOutlined />}
            onClick={() => {
              setRejectingPostId(record.id)
              setRejectModalOpen(true)
            }}
          >
            拒绝
          </Button>
        </div>
      ),
    },
  ]

  const wordColumns: ProColumns<SensitiveWordRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '敏感词', dataIndex: 'word', width: 180 },
    {
      title: '分类',
      dataIndex: 'category',
      width: 120,
      valueEnum: Object.fromEntries(CATEGORY_OPTIONS.map((o) => [o.value, o.label])),
    },
    {
      title: '级别',
      dataIndex: 'level',
      width: 100,
      valueEnum: Object.fromEntries(LEVEL_OPTIONS.map((o) => [o.value, o.label])),
      render: (_, record) => {
        const info = LEVEL_MAP[record.level]
        return info ? <Tag color={info.color}>{info.label}</Tag> : record.level
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 180, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, record) => (
        <div style={{ display: 'flex', gap: 8 }}>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingWord(record)
              setEditWordModalOpen(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除该敏感词？"
            onConfirm={() => handleDeleteWord(record.id)}
            okText="确认"
            cancelText="取消"
          >
            <Button danger size="small" icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </div>
      ),
    },
  ]

  return (
    <div>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: 'pending', label: '待审核帖子' },
          { key: 'sensitive', label: '敏感词管理' },
        ]}
      />

      {activeTab === 'pending' && (
        <ProTable<PendingPostRecord>
          columns={pendingColumns}
          actionRef={pendingRef}
          request={async (params) => {
            return safeProTableRequest<PendingPostRecord>(() =>
              getPendingReviewPosts({
                page: params.current,
                size: params.pageSize,
              })
            )
          }}
          rowKey="id"
          pagination={{ pageSize: 20 }}
          search={false}
          headerTitle="待审核帖子"
        />
      )}

      {activeTab === 'sensitive' && (
        <ProTable<SensitiveWordRecord>
          columns={wordColumns}
          actionRef={wordRef}
          request={async (params) => {
            return safeProTableRequest<SensitiveWordRecord>(() =>
              getSensitiveWords({
                category: params.category,
                page: params.current,
                size: params.pageSize,
              })
            )
          }}
          rowKey="id"
          pagination={{ pageSize: 20 }}
          search={{ filterType: 'light' }}
          headerTitle="敏感词管理"
          toolBarRender={() => [
            <Button
              key="refresh"
              icon={<ReloadOutlined />}
              onClick={handleRefreshCache}
            >
              刷新缓存
            </Button>,
            <Button
              key="add"
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setAddWordModalOpen(true)}
            >
              添加敏感词
            </Button>,
          ]}
        />
      )}

      <ModalForm
        title="拒绝原因"
        open={rejectModalOpen}
        onOpenChange={createHandleOpenChange1(setRejectModalOpen, () => setRejectingPostId(null))}
        onFinish={async (values) => {
          return handleReject(values.reason)
        }}
        modalProps={{ destroyOnClose: true, mask: { closable: false }, keyboard: false }}
      >
        <ProFormTextArea
          name="reason"
          label="拒绝原因"
          rules={[{ required: true, message: '请输入拒绝原因' }]}
          fieldProps={{ rows: 3, placeholder: '请输入拒绝原因' }}
        />
      </ModalForm>

      <ModalForm
        title="添加敏感词"
        open={addWordModalOpen}
        onOpenChange={createHandleOpenChange2(setAddWordModalOpen)}
        onFinish={async (values) => {
          return confirmSubmit2(async () => {
            try {
              await addSensitiveWord(values as { word: string; category: string; level: number })
              message.success('添加成功')
              wordRef.current?.reload()
            } catch {
              message.error('添加失败')
              throw new Error()
            }
          })
        }}
        modalProps={{ destroyOnClose: true, mask: { closable: false }, keyboard: false }}
      >
        <ProFormText
          name="word"
          label="敏感词"
          rules={[{ required: true, message: '请输入敏感词' }]}
          fieldProps={{ placeholder: '请输入敏感词' }}
        />
        <ProFormSelect
          name="category"
          label="分类"
          rules={[{ required: true, message: '请选择分类' }]}
          options={CATEGORY_OPTIONS}
          fieldProps={{ placeholder: '请选择分类' }}
        />
        <ProFormSelect
          name="level"
          label="级别"
          rules={[{ required: true, message: '请选择级别' }]}
          options={LEVEL_OPTIONS}
          fieldProps={{ placeholder: '请选择级别' }}
        />
      </ModalForm>

      <Modal
        title="帖子详情"
        open={!!detailPost}
        onCancel={() => setDetailPost(null)}
        footer={null}
      >
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="ID">{detailPost?.id}</Descriptions.Item>
          <Descriptions.Item label="标题">{detailPost?.title}</Descriptions.Item>
          <Descriptions.Item label="作者昵称">{detailPost?.authorNickname}</Descriptions.Item>
          <Descriptions.Item label="内容摘要">{detailPost?.contentSummary}</Descriptions.Item>
          <Descriptions.Item label="发布时间">{detailPost?.createdAt}</Descriptions.Item>
        </Descriptions>
      </Modal>

      <ModalForm
        title="编辑敏感词"
        open={editWordModalOpen}
        onOpenChange={(open) => {
          setEditWordModalOpen(open)
          if (!open) setEditingWord(null)
        }}
        onFinish={async (values) => {
          if (!editingWord) return false
          try {
            await updateSensitiveWord(editingWord.id, values as { word?: string; category?: string; level?: number })
            message.success('编辑成功')
            wordRef.current?.reload()
            return true
          } catch {
            message.error('编辑失败')
            return false
          }
        }}
        initialValues={editingWord ? { word: editingWord.word, category: editingWord.category, level: editingWord.level } : {}}
        modalProps={{ destroyOnClose: true, mask: { closable: false }, keyboard: false }}
      >
        <ProFormText
          name="word"
          label="敏感词"
          rules={[{ required: true, message: '请输入敏感词' }]}
          fieldProps={{ placeholder: '请输入敏感词' }}
        />
        <ProFormSelect
          name="category"
          label="分类"
          rules={[{ required: true, message: '请选择分类' }]}
          options={CATEGORY_OPTIONS}
          fieldProps={{ placeholder: '请选择分类' }}
        />
        <ProFormSelect
          name="level"
          label="级别"
          rules={[{ required: true, message: '请选择级别' }]}
          options={LEVEL_OPTIONS}
          fieldProps={{ placeholder: '请选择级别' }}
        />
      </ModalForm>
    </div>
  )
}
