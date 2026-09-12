import { useRef, useState, useEffect } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getNotices,
  createNotice,
  updateNotice,
  deleteNotice,
  updateNoticeStatus,
  getDictData,
} from '@/api/admin/system'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'
import TiptapEditor from '@/components/TiptapEditor'

interface NoticeRecord {
  id: number
  noticeTitle: string
  noticeType: number
  noticeContent: string
  status: number
  createdAt: string
}

interface NoticeTypeOption {
  label: string
  value: number
  tagColor: string
}

// 字典 listClass（RuoYi 风格）→ antd Tag color
const LIST_CLASS_TO_TAG: Record<string, string> = {
  default: 'default',
  primary: 'blue',
  success: 'success',
  info: 'blue',
  warning: 'warning',
  danger: 'error',
}

// 公告类型兜底：admin_notice_type 字典不可用时保底（与字典种子数据一致）
const FALLBACK_TYPE_OPTIONS: NoticeTypeOption[] = [
  { label: '通知', value: 1, tagColor: 'warning' },
  { label: '公告', value: 2, tagColor: 'success' },
]

export default function Notices() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<NoticeRecord | null>(null)
  const [typeOptions, setTypeOptions] = useState<NoticeTypeOption[]>(FALLBACK_TYPE_OPTIONS)

  // 公告类型从 admin_notice_type 字典加载，字典管理新增类型后此处自动生效
  useEffect(() => {
    getDictData('admin_notice_type')
      .then(({ data: res }) => {
        const rows = ((res as { data?: Array<{ dictLabel: string; dictValue: string; listClass?: string; status: number }> }).data ?? [])
          .filter((row) => Number(row.status) === 1)
        if (rows.length > 0) {
          setTypeOptions(rows.map((row) => ({
            label: row.dictLabel,
            value: Number(row.dictValue),
            tagColor: LIST_CLASS_TO_TAG[row.listClass ?? 'default'] ?? 'default',
          })))
        }
      })
      .catch(() => {
        // 保持兜底选项，不打断页面
      })
  }, [])

  const typeLabel = (value: number) => typeOptions.find((option) => option.value === Number(value))

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateNotice(editingRecord.id, values)
        message.success('更新成功')
      } else {
        await createNotice(values)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteNotice(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const handleStatusChange = async (id: number, newStatus: number) => {
    try {
      await updateNoticeStatus(id, { status: newStatus })
      message.success('状态更新成功')
      actionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const columns: ProColumns<NoticeRecord>[] = [
    { title: '公告ID', dataIndex: 'id', width: 80, search: false },
    { title: '公告标题', dataIndex: 'noticeTitle', width: 200 },
    {
      title: '公告类型',
      dataIndex: 'noticeType',
      width: 100,
      valueEnum: Object.fromEntries(typeOptions.map((option) => [option.value, { text: option.label }])),
      render: (_, record) => {
        const typeInfo = typeLabel(record.noticeType)
        return <Tag color={typeInfo?.tagColor ?? 'default'}>{typeInfo?.label ?? '未知'}</Tag>
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (_, record) => (
        <Popconfirm
      title={`确定${Number(record.status) === 1 ? '关闭' : '启用'}吗？`}
      onConfirm={() => handleStatusChange(record.id, Number(record.status) === 1 ? 0 : 1)}
    >
      <Switch checked={Number(record.status) === 1} size="small" />
    </Popconfirm>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      fixed: 'right',
      render: (_, record) => [
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
        </Button>,
        <Popconfirm
          key="delete"
          title="确认删除该通知？"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<NoticeRecord>
        headerTitle="通知公告"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1000 }}
        request={async (params) => {
          return safeProTableRequest<NoticeRecord>(() =>
            getNotices({
              page: params.current,
              pageSize: params.pageSize,
              noticeTitle: params.noticeTitle,
              noticeType: params.noticeType,
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
            新增通知
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑通知' : '新增通知'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? editingRecord
            : { noticeType: 1, status: 1 }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={680}
      >
        <ProFormText
          name="noticeTitle"
          label="公告标题"
          placeholder="请输入公告标题"
          rules={[{ required: true, message: '请输入公告标题' }]}
        />
        <ProFormSelect
          name="noticeType"
          label="公告类型"
          options={typeOptions.map(({ label, value }) => ({ label, value }))}
          rules={[{ required: true, message: '请选择公告类型' }]}
        />
        <ProFormText name="noticeContent" label="内容" rules={[{ required: true, message: '请输入公告内容' }]}>
          <TiptapEditor placeholder="请输入公告内容" />
        </ProFormText>
      </ModalForm>
    </>
  )
}
