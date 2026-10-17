import RichText from '@/components/RichText'
import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tag, Modal, Descriptions, Tabs, Space, Typography } from 'antd'
import { DeleteOutlined, PlusOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons'
import {
  createBroadcastJob,
  deleteNotification,
  getNotifications,
  listBroadcastJobs,
  retryBroadcastJob,
  sendNotification,
  type BroadcastJobSummary,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'
import { useAdminAuthStore } from '@/stores/adminAuth'
import TiptapEditor from '@/components/TiptapEditor'

/**
 * ADMIN-03：消息中心（唯一通知运营入口）。
 *
 * <p>标签：通知列表（查询/删除）、单人通知（显式 string 用户 ID）、
 * 公告推送（群发批次任务：requestId 幂等 + 目标/成功/失败/跳过计数 + 预览后提交）、
 * 群发批次（独立查看权限 business:notification:batch:list，失败项可重试不重复投递）。
 * 伪 GROUP 受众已删除；社区域旧通知入口已下线（V6 迁移）。</p>
 */

interface NotificationRecord {
  id: number | string
  userId: number | string
  username: string
  type: string
  title: string
  content: string
  isRead: number
  createdAt: string
}

interface BroadcastDraft {
  type: string
  title: string
  content: string
}

const NOTIFICATION_TYPE_MAP: Record<string, { text: string; color: string }> = {
  SYSTEM: { text: '系统通知', color: 'blue' },
  ORDER: { text: '订单通知', color: 'green' },
  PROMOTION: { text: '促销通知', color: 'orange' },
  MARKETING: { text: '营销通知', color: 'purple' },
  PAYMENT: { text: '支付通知', color: 'cyan' },
  LOGISTICS: { text: '物流通知', color: 'geekblue' },
  NOTICE: { text: '公告推送', color: 'gold' },
}

const JOB_STATUS_META: Record<string, { text: string; color: string }> = {
  RUNNING: { text: '执行中', color: 'processing' },
  COMPLETED: { text: '全部成功', color: 'green' },
  PARTIAL: { text: '部分失败', color: 'orange' },
  FAILED: { text: '全部失败', color: 'red' },
}

/** 提交幂等键：一次「确认」一个键（同键重复提交返回原批次） */
function genRequestId(): string {
  const uuid = typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
  return `bc-${uuid.replace(/-/g, '')}`
}

export default function Notifications() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const batchActionRef = useRef<ActionType>(null)
  const [sendModalVisible, setSendModalVisible] = useState(false)
  const [broadcastModalVisible, setBroadcastModalVisible] = useState(false)
  const [broadcastDraft, setBroadcastDraft] = useState<BroadcastDraft | null>(null)
  const [broadcastRequestId, setBroadcastRequestId] = useState(genRequestId)
  const [broadcastSubmitting, setBroadcastSubmitting] = useState(false)
  const [detailRecord, setDetailRecord] = useState<NotificationRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  // ADMIN-03：发送与批次查看权限分离；无权限仅能查看记录
  const canSend = useAdminAuthStore(
    (state) =>
      state.permissions.includes('*:*:*') ||
      state.permissions.includes('business:notification:send'),
  )
  const canViewBatch = useAdminAuthStore(
    (state) =>
      state.permissions.includes('*:*:*') ||
      state.permissions.includes('business:notification:batch:list'),
  )

  const handleSend = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      // 受众为显式 string 用户 ID（BASE-01：不做数字转换，后端 Jackson 解析十进制字符串）
      await sendNotification({
        userId: String(values.userId ?? '').trim(),
        type: values.type,
        title: values.title,
        content: values.content,
      })
      message.success('通知发送成功')
      actionRef.current?.reload()
    })
  }

  const handleBroadcastSubmit = async () => {
    if (!broadcastDraft) return
    setBroadcastSubmitting(true)
    try {
      const { data: res } = await createBroadcastJob({
        type: broadcastDraft.type,
        title: broadcastDraft.title,
        content: broadcastDraft.content,
        requestId: broadcastRequestId,
      })
      if (!res.success) {
        message.error(res.error?.message ?? '群发批次提交失败')
        return
      }
      const job = res.data
      message.success(
        `群发批次 ${job.jobNo} 已完成：目标 ${job.target}，成功 ${job.delivered}，失败 ${job.failed}，跳过 ${job.skipped}`,
        6,
      )
      setBroadcastModalVisible(false)
      setBroadcastDraft(null)
      // 同一表单再次提交视为新批次：换新幂等键
      setBroadcastRequestId(genRequestId())
      batchActionRef.current?.reload()
    } catch (error) {
      message.error((error as Error).message || '群发批次提交失败')
    } finally {
      setBroadcastSubmitting(false)
    }
  }

  const handleRetry = async (jobNo: string) => {
    try {
      const { data: res } = await retryBroadcastJob(jobNo)
      if (!res.success) {
        message.error(res.error?.message ?? '重试失败')
        return
      }
      message.success(
        `批次 ${jobNo} 重试完成：成功 ${res.data.delivered}，失败 ${res.data.failed}，跳过 ${res.data.skipped}`,
        6,
      )
      batchActionRef.current?.reload()
    } catch (error) {
      message.error((error as Error).message || '重试失败')
    }
  }

  const columns: ProColumns<NotificationRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '用户ID', dataIndex: 'userId', width: 80, search: false },
    { title: '用户名', dataIndex: 'username', width: 120 },
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      render: (_, record) => {
        const typeInfo = NOTIFICATION_TYPE_MAP[record.type]
        return <Tag color={typeInfo?.color ?? 'default'}>{typeInfo?.text ?? record.type}</Tag>
      },
    },
    { title: '标题', dataIndex: 'title', width: 180, ellipsis: true },
    { title: '内容', dataIndex: 'content', width: 260, search: false, render: (_, record) => <RichText content={record.content} clamp={2} variant="preview" /> },
    {
      title: '已读',
      dataIndex: 'isRead',
      width: 80,
      search: false,
      render: (_, record) => (
        <Tag color={record.isRead === 1 ? 'green' : 'default'}>
          {record.isRead === 1 ? '已读' : '未读'}
        </Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      fixed: 'right',
      render: (_, record) => [
        <Button key="detail" type="link" size="small" onClick={() => setDetailRecord(record)}>
          详情
        </Button>,
        <Popconfirm
          key="delete"
          title="确认删除该通知？"
          description="删除后该用户消息中心将不再显示此条通知。"
          onConfirm={async () => {
            try {
              await deleteNotification(record.id)
              message.success('删除成功')
              actionRef.current?.reload()
            } catch {
              // 失败提示由 request 拦截器统一呈现
            }
          }}
        >
          <Button key="delete" type="link" size="small" danger icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  const batchColumns: ProColumns<BroadcastJobSummary>[] = [
    { title: '批次号', dataIndex: 'jobNo', width: 230, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      render: (_, record) => {
        const typeInfo = NOTIFICATION_TYPE_MAP[record.type]
        return <Tag color={typeInfo?.color ?? 'default'}>{typeInfo?.text ?? record.type}</Tag>
      },
    },
    { title: '标题', dataIndex: 'title', width: 200, ellipsis: true },
    { title: '目标', dataIndex: 'target', width: 80, search: false },
    { title: '成功', dataIndex: 'delivered', width: 80, search: false },
    { title: '失败', dataIndex: 'failed', width: 80, search: false, render: (_, record) => (record.failed > 0 ? <Tag color="red">{record.failed}</Tag> : '0') },
    { title: '跳过', dataIndex: 'skipped', width: 80, search: false, render: (_, record) => (record.skipped > 0 ? <Tag color="orange">{record.skipped}</Tag> : '0') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const meta = JOB_STATUS_META[record.status] ?? { text: record.status, color: 'default' }
        return <Tag color={meta.color}>{meta.text}</Tag>
      },
    },
    { title: '操作人', dataIndex: 'createdBy', width: 110, search: false, render: (_, record) => record.createdBy ?? '-' },
    { title: '提交时间', dataIndex: 'createdAt', width: 180, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 110,
      fixed: 'right',
      render: (_, record) =>
        (record.status === 'PARTIAL' || record.status === 'FAILED') && canSend ? (
          <Popconfirm
            key="retry"
            title="仅重试失败项，不会重复投递已成功用户。确认执行？"
            onConfirm={() => handleRetry(record.jobNo)}
          >
            <Button type="link" size="small" icon={<ReloadOutlined />}>
              重试失败项
            </Button>
          </Popconfirm>
        ) : null,
    },
  ]

  const listTab = (
    <>
      <ProTable<NotificationRecord>
        headerTitle="通知列表"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1200 }}
        request={async (params) => {
          return safeProTableRequest<NotificationRecord>(() =>
            getNotifications({
              page: params.current,
              pageSize: params.pageSize,
              username: params.username,
              type: params.type,
            })
          )
        }}
        toolBarRender={() => [
          canSend && (
            <Button
              key="send"
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setSendModalVisible(true)}
            >
              单人通知
            </Button>
          ),
        ].filter(Boolean)}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />
      <ModalForm
        title="发送单人通知"
        open={sendModalVisible}
        onOpenChange={createHandleOpenChange(setSendModalVisible)}
        onFinish={handleSend}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={560}
      >
        <ProFormText
          name="userId"
          label="用户 ID"
          placeholder="请输入目标用户 ID（字符串原样提交）"
          rules={[
            { required: true, message: '请输入用户 ID' },
            { pattern: /^\d{1,19}$/, message: '用户 ID 应为 1-19 位数字' },
          ]}
          tooltip="ADMIN-03：受众必须显式指定；不做数字转换（BASE-01 防精度损失）"
        />
        <ProFormSelect
          name="type"
          label="通知类型"
          placeholder="请选择通知类型"
          rules={[{ required: true, message: '请选择通知类型' }]}
          options={Object.entries(NOTIFICATION_TYPE_MAP).map(([key, val]) => ({
            label: val.text,
            value: key,
          }))}
        />
        <ProFormText
          name="title"
          label="通知标题"
          placeholder="请输入通知标题"
          rules={[{ required: true, message: '请输入通知标题' }]}
        />
        <ProFormText name="content" label="通知内容" rules={[{ required: true, message: '请输入通知内容' }]}>
          <TiptapEditor placeholder="请输入通知内容" />
        </ProFormText>
      </ModalForm>
    </>
  )

  const broadcastTab = (
    <>
      <ProTable<BroadcastJobSummary>
        headerTitle="群发批次"
        actionRef={batchActionRef}
        rowKey="jobNo"
        search={false}
        request={async (params) => {
          return safeProTableRequest<BroadcastJobSummary>(() =>
            listBroadcastJobs({ page: params.current ?? 1, size: params.pageSize ?? 20 }),
          )
        }}
        toolBarRender={() => [
          canSend && (
            <Button
              key="broadcast"
              type="primary"
              icon={<SendOutlined />}
              onClick={() => {
                setBroadcastRequestId(genRequestId())
                setBroadcastModalVisible(true)
              }}
            >
              公告推送
            </Button>
          ),
        ].filter(Boolean)}
        columns={batchColumns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />
      <ModalForm
        title="公告推送（全站用户，禁用账号跳过）"
        open={broadcastModalVisible}
        onOpenChange={(visible) => {
          if (!visible) setBroadcastDraft(null)
          createHandleOpenChange(setBroadcastModalVisible)(visible)
        }}
        onFinish={async (values: Record<string, any>) => {
          // ADMIN-03：提交前强制预览，明确受众与内容后再落库（返回 false 阻止自动关闭）
          setBroadcastDraft({
            type: values.type,
            title: values.title,
            content: values.content,
          })
          return false
        }}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={560}
      >
        <ProFormSelect
          name="type"
          label="通知类型"
          placeholder="请选择通知类型"
          rules={[{ required: true, message: '请选择通知类型' }]}
          options={Object.entries(NOTIFICATION_TYPE_MAP).map(([key, val]) => ({
            label: val.text,
            value: key,
          }))}
        />
        <ProFormText
          name="title"
          label="公告标题"
          placeholder="请输入公告标题"
          rules={[{ required: true, message: '请输入公告标题' }]}
        />
        <ProFormText name="content" label="公告内容" rules={[{ required: true, message: '请输入公告内容' }]}>
          <TiptapEditor placeholder="请输入公告内容（提交前将预览）" />
        </ProFormText>
      </ModalForm>

      <Modal
        title="公告预览（确认受众与内容后提交）"
        open={broadcastDraft !== null}
        onCancel={() => setBroadcastDraft(null)}
        footer={[
          <Button key="cancel" onClick={() => setBroadcastDraft(null)}>返回修改</Button>,
          <Button key="submit" type="primary" loading={broadcastSubmitting} onClick={handleBroadcastSubmit}>
            确认推送全站
          </Button>,
        ]}
        width={620}
      >
        {broadcastDraft && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="受众">全站用户（禁用账号自动跳过）</Descriptions.Item>
            <Descriptions.Item label="类型">
              <Tag color={NOTIFICATION_TYPE_MAP[broadcastDraft.type]?.color ?? 'default'}>
                {NOTIFICATION_TYPE_MAP[broadcastDraft.type]?.text ?? broadcastDraft.type}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="标题">{broadcastDraft.title}</Descriptions.Item>
            <Descriptions.Item label="内容">
              <RichText content={broadcastDraft.content} variant="full" />
            </Descriptions.Item>
            <Descriptions.Item label="幂等键">
              <Typography.Text code>{broadcastRequestId}</Typography.Text>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </>
  )

  return (
    <Tabs
      defaultActiveKey="list"
      items={[
        { key: 'list', label: '通知列表', children: listTab },
        ...(canViewBatch
          ? [{ key: 'broadcast', label: '公告推送与群发批次', children: broadcastTab }]
          : []),
      ]}
    />
  )
}
