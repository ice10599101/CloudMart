import { useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit, ProFormTextArea, ProFormSelect } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tabs, Tag, Typography, Upload } from 'antd'
import { GiftOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons'
import {
  getAdminGifts,
  createAdminGift,
  updateAdminGift,
  updateAdminGiftStatus,
  deleteAdminGift,
  getAdminGiftRecords,
} from '@/api/admin/wish'
import type { AdminGiftRecord, AdminGiftRecordItem } from '@/api/admin/wish'
import { uploadFile } from '@/api/file'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

/**
 * 礼物管理（全站虚拟礼物：目录维护 + 送礼记录）。
 *
 * 目录 Tab：礼物 CRUD/上下架（图标经 mall-file 上传后登记 URL，默认未上架）。
 * 记录 Tab：全站送礼记录（可按送礼人/收礼人/场景筛选，cursor 分页）。
 * 送礼记录为星光消费凭证快照，目录变更不影响历史记录。
 */

interface GiftFormValues {
  name: string
  priceStarlight: number
  status: 'ON_SHELF' | 'OFF_SHELF'
  sort: number
  description?: string
}

const MAX_ICON_SIZE_BYTES = 5 * 1024 * 1024

const TARGET_TYPE_LABEL: Record<AdminGiftRecordItem['targetType'], string> = {
  WISH: '心愿',
  POST: '帖子',
  LIVE_ROOM: '直播间',
}

export default function Gifts() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<AdminGiftRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  // 图标上传（新增/编辑共用，编辑时预填已有图标）
  const [iconUrl, setIconUrl] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  const resetUpload = () => {
    setIconUrl(null)
    setUploading(false)
  }

  const handleCreate = async (values: GiftFormValues) => {
    return confirmSubmit(async () => {
      await createAdminGift({
        name: values.name,
        priceStarlight: values.priceStarlight,
        status: values.status,
        sort: values.sort,
        description: values.description,
        iconUrl: iconUrl ?? undefined,
      })
      message.success('新增成功（默认未上架，需再点击「上架」）')
      actionRef.current?.reload()
    })
  }

  const handleUpdate = async (values: GiftFormValues) => {
    if (!editingRecord) return false
    return confirmSubmit(async () => {
      await updateAdminGift(editingRecord.id, {
        name: values.name,
        priceStarlight: values.priceStarlight,
        status: values.status,
        sort: values.sort,
        description: values.description,
        iconUrl: iconUrl ?? undefined,
      })
      message.success('更新成功')
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleToggleStatus = async (record: AdminGiftRecord) => {
    await updateAdminGiftStatus(record.id, record.status !== 'ON_SHELF')
    message.success(record.status === 'ON_SHELF' ? '已下架' : '已上架')
    actionRef.current?.reload()
  }

  const handleDelete = async (record: AdminGiftRecord) => {
    await deleteAdminGift(record.id)
    message.success('已删除（软删，历史送礼记录不受影响）')
    actionRef.current?.reload()
  }

  const customUpload = async (options: { file: unknown; onSuccess?: (body: unknown) => void; onError?: (e: Error) => void }) => {
    const file = options.file as File
    setUploading(true)
    try {
      const response = await uploadFile(file)
      const result = response.data
      if (result?.success && result.data?.url) {
        setIconUrl(result.data.url)
        options.onSuccess?.(result)
      } else {
        throw new Error(result?.error?.message ?? '上传失败')
      }
    } catch (error) {
      options.onError?.(error as Error)
      message.error((error as { code?: string })?.code === 'UPLOAD_DAILY_LIMIT_EXCEEDED'
        ? '今日上传已达上限'
        : '上传失败，请重试')
    } finally {
      setUploading(false)
    }
  }

  const giftColumns: ProColumns<AdminGiftRecord>[] = [
    {
      title: '图标',
      dataIndex: 'iconUrl',
      width: 70,
      search: false,
      render: (_, record) =>
        record.iconUrl ? (
          <img src={record.iconUrl} alt={record.name} style={{ width: 32, height: 32, objectFit: 'contain' }} />
        ) : (
          <GiftOutlined style={{ fontSize: 22, color: '#faad14' }} />
        ),
    },
    { title: '名称', dataIndex: 'name', width: 140, ellipsis: true },
    {
      title: '单价（星光）',
      dataIndex: 'priceStarlight',
      width: 110,
      render: (_, record) => <Tag color="gold">✦ {record.priceStarlight}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      search: false,
      render: (_, record) =>
        record.status === 'ON_SHELF' ? <Tag color="success">上架</Tag> : <Tag color="default">下架</Tag>,
    },
    { title: '排序', dataIndex: 'sort', width: 70, search: false },
    { title: '描述', dataIndex: 'description', search: false, ellipsis: true },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="edit"
          type="link"
          size="small"
          onClick={() => {
            setEditingRecord(record)
            setIconUrl(record.iconUrl)
            setModalVisible(true)
          }}
        >
          编辑
        </Button>,
        <Popconfirm
          key="toggle"
          title={record.status === 'ON_SHELF' ? '确认下架？用户端礼物选择器将不可见' : '确认上架？'}
          onConfirm={() => handleToggleStatus(record)}
        >
          <Button type="link" size="small">
            {record.status === 'ON_SHELF' ? '下架' : '上架'}
          </Button>
        </Popconfirm>,
        <Popconfirm
          key="delete"
          title="确认删除？（软删，历史送礼记录持有快照不受影响）"
          onConfirm={() => handleDelete(record)}
        >
          <Button type="link" size="small" danger>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  const recordColumns: ProColumns<AdminGiftRecordItem>[] = [
    { title: 'ID', dataIndex: 'id', width: 150, search: false, ellipsis: true },
    { title: '礼物', dataIndex: 'giftName', width: 120, search: false },
    {
      title: '数量',
      dataIndex: 'count',
      width: 70,
      search: false,
      render: (_, record) => `×${record.count}`,
    },
    {
      title: '消耗（星光）',
      dataIndex: 'totalPrice',
      width: 100,
      search: false,
      render: (_, record) => <Tag color="gold">✦ {record.totalPrice}</Tag>,
    },
    {
      title: '送礼人',
      width: 130,
      search: false,
      render: (_, record) => record.senderNickname ?? `#${record.senderId}`,
    },
    { title: '送礼人ID', dataIndex: 'senderId', width: 110, hideInTable: true },
    {
      title: '收礼人',
      width: 130,
      search: false,
      render: (_, record) => record.receiverNickname ?? `#${record.receiverId}`,
    },
    { title: '收礼人ID', dataIndex: 'receiverId', width: 110, hideInTable: true },
    {
      title: '场景',
      dataIndex: 'targetType',
      width: 90,
      valueType: 'select',
      fieldProps: {
        options: [
          { label: '心愿', value: 'WISH' },
          { label: '帖子', value: 'POST' },
          { label: '直播间', value: 'LIVE_ROOM' },
        ],
      },
      render: (_, record) => TARGET_TYPE_LABEL[record.targetType] ?? record.targetType,
    },
    { title: '对象ID', dataIndex: 'targetId', width: 130, search: false, ellipsis: true },
    { title: '留言', dataIndex: 'message', search: false, ellipsis: true },
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 160,
      search: false,
      render: (_, record) => new Date(record.createdAt).toLocaleString(),
    },
  ]

  return (
    <Tabs
      defaultActiveKey="catalog"
      items={[
        {
          key: 'catalog',
          label: '礼物目录',
          children: (
            <>
              <ProTable<AdminGiftRecord>
                headerTitle="礼物目录（全站虚拟礼物）"
                actionRef={actionRef}
                rowKey="id"
                search={false}
                scroll={{ x: 900 }}
                request={async () => safeProTableRequest<AdminGiftRecord>(() => getAdminGifts())}
                toolBarRender={() => [
                  <Button
                    key="add"
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => {
                      setEditingRecord(null)
                      resetUpload()
                      setModalVisible(true)
                    }}
                  >
                    新增礼物
                  </Button>,
                ]}
                columns={giftColumns}
                pagination={false}
              />

              <ModalForm<GiftFormValues>
                title={editingRecord ? `编辑礼物：${editingRecord.name}` : '新增礼物'}
                open={modalVisible}
                onOpenChange={createHandleOpenChange(setModalVisible, () => {
                  setEditingRecord(null)
                  resetUpload()
                })}
                onFinish={editingRecord ? handleUpdate : handleCreate}
                initialValues={
                  editingRecord
                    ? {
                        name: editingRecord.name,
                        priceStarlight: editingRecord.priceStarlight,
                        status: editingRecord.status,
                        sort: editingRecord.sort,
                        description: editingRecord.description ?? undefined,
                      }
                    : { status: 'OFF_SHELF', sort: 0 }
                }
                modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
                width={520}
              >
                <Upload
                  accept="image/*"
                  maxCount={1}
                  beforeUpload={(file) => {
                    if (file.size > MAX_ICON_SIZE_BYTES) {
                      message.error(`图标 ${(file.size / 1024 / 1024).toFixed(1)} MB 超过 5MB 上限`)
                      return Upload.LIST_IGNORE
                    }
                    return true
                  }}
                  customRequest={customUpload}
                  onRemove={resetUpload}
                >
                  <Button icon={<UploadOutlined />} loading={uploading}>
                    {iconUrl ? '重新上传图标' : '上传礼物图标'}
                  </Button>
                </Upload>
                {iconUrl && (
                  <Typography.Text type="secondary" style={{ display: 'block', marginTop: 4, fontSize: 12 }}>
                    已上传：{iconUrl}
                  </Typography.Text>
                )}
                <ProFormText
                  name="name"
                  label="礼物名称"
                  rules={[{ required: true, message: '请输入礼物名称' }, { max: 50, message: '名称不能超过50字符' }]}
                />
                <ProFormDigit
                  name="priceStarlight"
                  label="星光单价"
                  min={1}
                  max={100000}
                  fieldProps={{ precision: 0 }}
                  rules={[{ required: true, message: '请输入星光单价' }]}
                />
                <ProFormSelect
                  name="status"
                  label="状态"
                  options={[
                    { label: '上架', value: 'ON_SHELF' },
                    { label: '下架', value: 'OFF_SHELF' },
                  ]}
                />
                <ProFormDigit name="sort" label="排序（越小越靠前）" min={0} fieldProps={{ precision: 0 }} />
                <ProFormTextArea name="description" label="描述" fieldProps={{ maxLength: 200, showCount: true }} />
              </ModalForm>
            </>
          ),
        },
        {
          key: 'records',
          label: '送礼记录',
          children: (
            <ProTable<AdminGiftRecordItem>
              headerTitle="送礼记录（星光消费凭证）"
              rowKey="id"
              scroll={{ x: 1300 }}
              request={async (params) => {
                const { current, pageSize, senderId, receiverId, targetType, targetId } = params as Record<string, unknown> & {
                  current?: number
                  pageSize?: number
                  senderId?: number
                  receiverId?: number
                  targetType?: string
                  targetId?: number
                }
                return safeProTableRequest<AdminGiftRecordItem>(() =>
                  getAdminGiftRecords({
                    senderId: senderId || undefined,
                    receiverId: receiverId || undefined,
                    targetType: targetType || undefined,
                    targetId: targetId || undefined,
                    page: current,
                    pageSize,
                  }),
                )
              }}
              columns={recordColumns}
            />
          ),
        },
      ]}
    />
  )
}
