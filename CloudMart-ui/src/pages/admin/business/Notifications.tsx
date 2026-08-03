import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormSelect,
  ProFormDigit,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Modal, Descriptions } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { getNotifications, sendNotification } from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'
import TiptapEditor from '@/components/TiptapEditor'

interface NotificationRecord {
  id: number
  userId: number
  username: string
  type: string
  title: string
  content: string
  isRead: number
  createdAt: string
}

const NOTIFICATION_TYPE_MAP: Record<string, { text: string; color: string }> = {
  SYSTEM: { text: '系统通知', color: 'blue' },
  ORDER: { text: '订单通知', color: 'green' },
  PROMOTION: { text: '促销通知', color: 'orange' },
  MARKETING: { text: '营销通知', color: 'purple' },
  PAYMENT: { text: '支付通知', color: 'cyan' },
  LOGISTICS: { text: '物流通知', color: 'geekblue' },
}

export default function Notifications() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [sendModalVisible, setSendModalVisible] = useState(false)
  const [detailRecord, setDetailRecord] = useState<NotificationRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSend = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      await sendNotification(values)
      message.success('通知发送成功')
      actionRef.current?.reload()
    })
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
    { title: '内容', dataIndex: 'content', width: 260, search: false, ellipsis: true },
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
      width: 80,
      fixed: 'right',
      render: (_, record) => [
        <Button key="detail" type="link" size="small" onClick={() => setDetailRecord(record)}>
          详情
        </Button>,
      ],
    },
  ]

  return (
    <>
      <ProTable<NotificationRecord>
        headerTitle="通知管理"
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
          <Button
            key="send"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setSendModalVisible(true)}
          >
            发送通知
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title="发送通知"
        open={sendModalVisible}
        onOpenChange={createHandleOpenChange(setSendModalVisible)}
        onFinish={handleSend}
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={560}
      >
        <ProFormSelect
          name="sendType"
          label="发送对象"
          placeholder="请选择发送对象类型"
          rules={[{ required: true, message: '请选择发送对象' }]}
          options={[
            { label: '指定用户', value: 'USER' },
            { label: '全部用户', value: 'ALL' },
            { label: '指定用户组', value: 'GROUP' },
          ]}
        />
        <ProFormDigit
          name="userId"
          label="用户ID"
          min={1}
          fieldProps={{ precision: 0 }}
          tooltip="发送对象为「指定用户」时必填"
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

      <Modal
        title="通知详情"
        open={!!detailRecord}
        onCancel={() => setDetailRecord(null)}
        footer={null}
        width={600}
      >
        {detailRecord && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="ID">{detailRecord.id}</Descriptions.Item>
            <Descriptions.Item label="用户ID">{detailRecord.userId}</Descriptions.Item>
            <Descriptions.Item label="用户名">{detailRecord.username}</Descriptions.Item>
            <Descriptions.Item label="类型">
              <Tag color={NOTIFICATION_TYPE_MAP[detailRecord.type]?.color ?? 'default'}>
                {NOTIFICATION_TYPE_MAP[detailRecord.type]?.text ?? detailRecord.type}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="标题">{detailRecord.title}</Descriptions.Item>
            <Descriptions.Item label="内容">{detailRecord.content}</Descriptions.Item>
            <Descriptions.Item label="已读">
              <Tag color={detailRecord.isRead === 1 ? 'green' : 'default'}>
                {detailRecord.isRead === 1 ? '已读' : '未读'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{detailRecord.createdAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </>
  )
}
