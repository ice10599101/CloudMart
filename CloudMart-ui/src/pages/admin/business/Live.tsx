import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormTextArea,
  ProFormDateTimePicker,
  ProFormDigit,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { getLiveRooms, createLiveRoom, updateLiveRoom, startLive, endLive, deleteLiveRoom } from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface LiveRoomRecord {
  id: number
  title: string
  coverImage: string
  anchorName: string
  anchorUserId: number
  productId: number
  status: string
  currentViewers: number
  totalViewers: number
  startTime: string
  endTime: string
  createdAt: string
}

const LIVE_STATUS_MAP: Record<string, { text: string; color: string }> = {
  OFFLINE: { text: '未开始', color: 'default' },
  LIVE: { text: '直播中', color: 'red' },
  ENDED: { text: '已结束', color: 'default' },
}

export default function Live() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<LiveRoomRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateLiveRoom(editingRecord.id, values)
        message.success('编辑成功')
      } else {
        await createLiveRoom(values)
        message.success('直播间创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleStart = async (id: number) => {
    await startLive(id)
    message.success('直播已开始')
    actionRef.current?.reload()
  }

  const handleEnd = async (id: number) => {
    await endLive(id)
    message.success('直播已结束')
    actionRef.current?.reload()
  }

  const columns: ProColumns<LiveRoomRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '直播标题', dataIndex: 'title', width: 180, ellipsis: true },
    { title: '主播', dataIndex: 'anchorName', width: 120 },
    {
      title: '观看人数',
      dataIndex: 'currentViewers',
      width: 100,
      search: false,
      render: (_, record) => <Tag color="blue">{record.currentViewers}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = LIVE_STATUS_MAP[record.status]
        return <Tag color={statusInfo?.color ?? 'default'}>{statusInfo?.text ?? record.status}</Tag>
      },
    },
    { title: '开始时间', dataIndex: 'startTime', width: 170, valueType: 'dateTime', search: false },
    { title: '结束时间', dataIndex: 'endTime', width: 170, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      fixed: 'right',
      render: (_, record) => [
        record.status === 'OFFLINE' && (
          <Button
            key="edit"
            type="link"
            size="small"
            onClick={() => {
              setEditingRecord(record)
              setModalVisible(true)
            }}
          >
            编辑
          </Button>
        ),
        record.status === 'OFFLINE' && (
          <Popconfirm
            key="start"
            title="确认开始直播？"
            onConfirm={() => handleStart(record.id)}
          >
            <Button type="link" size="small">开始直播</Button>
          </Popconfirm>
        ),
        record.status === 'LIVE' && (
          <Popconfirm
            key="end"
            title="确认结束直播？"
            onConfirm={() => handleEnd(record.id)}
          >
            <Button type="link" size="small" danger>结束直播</Button>
          </Popconfirm>
        ),
        record.status === 'OFFLINE' && (
          <Popconfirm
            key="delete"
            title="确定删除吗？"
            onConfirm={async () => {
              await deleteLiveRoom(record.id!)
              message.success('删除成功')
              actionRef.current?.reload()
            }}
          >
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        ),
      ],
    },
  ]

  return (
    <>
      <ProTable<LiveRoomRecord>
        headerTitle="直播管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1400 }}
        request={async (params) => {
          return safeProTableRequest<LiveRoomRecord>(() =>
            getLiveRooms({
              page: params.current,
              pageSize: params.pageSize,
              title: params.title,
              anchorName: params.anchorName,
              status: params.status,
            })
          )
        }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingRecord(null)
              setModalVisible(true)
            }}
          >
            新增直播间
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑直播间' : '新增直播间'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={editingRecord ?? undefined}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={560}
      >
        <ProFormText
          name="title"
          label="直播标题"
          placeholder="请输入直播标题"
          rules={[{ required: true, message: '请输入直播标题' }]}
        />
        <ProFormText
          name="anchorName"
          label="主播名称"
          placeholder="请输入主播名称"
          rules={[{ required: true, message: '请输入主播名称' }]}
        />
        <ProFormDigit
          name="anchorUserId"
          label="主播用户ID"
          min={1}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入主播用户ID' }]}
        />
        <ProFormDigit
          name="productId"
          label="关联商品ID"
          min={1}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入关联商品ID' }]}
        />
        <ProFormText
          name="coverImage"
          label="封面图URL"
          placeholder="请输入封面图URL"
        />
        <ProFormTextArea
          name="description"
          label="直播描述"
          placeholder="请输入直播描述"
          fieldProps={{ rows: 3, maxLength: 500, showCount: true }}
        />
      </ModalForm>
    </>
  )
}
