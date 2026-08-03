import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getNotifications,
  sendNotification,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'
import TiptapEditor from '@/components/TiptapEditor'

interface AdminNotificationRecord {
  id: number
  userId: number | null
  type: string
  title: string
  content: string
  isRead: boolean
  bizId: number | null
  bizType: string | null
  createdAt: string
}

const NOTIFICATION_TYPE_MAP: Record<string, { label: string; color: string }> = {
  SYSTEM: { label: '系统通知', color: 'blue' },
  ACCOUNT: { label: '账户通知', color: 'green' },
  BADGE: { label: '徽章通知', color: 'gold' },
}

export default function Notifications() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSend = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      await sendNotification({
        userId: values.userId ? Number(values.userId) : undefined,
        type: values.type,
        title: values.title,
        content: values.content,
      })
      message.success('通知发送成功')
      actionRef.current?.reload()
    })
  }

  const columns: ProColumns<AdminNotificationRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    {
      title: '接收用户',
      dataIndex: 'userId',
      width: 120,
      search: false,
      render: (_, record) => record.userId ? record.userId : <Tag color="orange">全体广播</Tag>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 120,
      render: (_, record) => {
        const typeInfo = NOTIFICATION_TYPE_MAP[record.type] ?? { label: record.type, color: 'default' }
        return <Tag color={typeInfo.color}>{typeInfo.label}</Tag>
      },
    },
    { title: '标题', dataIndex: 'title', width: 200, ellipsis: true },
    {
      title: '内容',
      dataIndex: 'content',
      width: 300,
      ellipsis: true,
      search: false,
    },
    {
      title: '已读',
      dataIndex: 'isRead',
      width: 80,
      search: false,
      render: (_, record) => record.isRead ? <Tag color="success">已读</Tag> : <Tag>未读</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
  ]

  return (
    <>
      <ProTable<AdminNotificationRecord>
        headerTitle="通知发送"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1000 }}
        request={async (params) => {
          return safeProTableRequest<AdminNotificationRecord>(() =>
            getNotifications({
              page: params.current,
              pageSize: params.pageSize,
            })
          )
        }}
        toolBarRender={() => [
          <Button
            key="send"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setModalVisible(true)}
          >
            发送通知
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title="发送通知"
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible)}
        onFinish={handleSend}
        initialValues={{ type: 'SYSTEM' }}
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={520}
      >
        <ProFormText
          name="userId"
          label="接收用户ID"
          placeholder="留空则为全体广播"
          fieldProps={{ type: 'number' }}
        />
        <ProFormSelect
          name="type"
          label="通知类型"
          options={[
            { label: '系统通知', value: 'SYSTEM' },
            { label: '账户通知', value: 'ACCOUNT' },
            { label: '徽章通知', value: 'BADGE' },
          ]}
          rules={[{ required: true, message: '请选择通知类型' }]}
        />
        <ProFormText
          name="title"
          label="标题"
          placeholder="请输入通知标题"
          rules={[{ required: true, message: '请输入通知标题' }]}
        />
        <ProFormText name="content" label="内容" rules={[{ required: true, message: '请输入通知内容' }]}>
          <TiptapEditor placeholder="请输入通知内容" />
        </ProFormText>
      </ModalForm>
    </>
  )
}
