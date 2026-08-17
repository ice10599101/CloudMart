import { useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormTextArea } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Descriptions, Image, Modal, Popconfirm, Tag } from 'antd'
import { CheckOutlined, CloseOutlined } from '@ant-design/icons'
import {
  getAdminWishes,
  getAdminWishDetail,
  auditAdminWish,
  getAdminWishCategories,
  WISH_STATUS_MAP,
  AUDIT_STATUS_MAP,
  VISIBILITY_MAP,
  FRUIT_TYPE_MAP,
} from '@/api/admin/wish'
import type { AdminWishRecord } from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/**
 * 心愿管理（管理后台）：列表查看 + 审核。
 *
 * 仅 PENDING 状态可执行审核（通过/拒绝），已审核记录后端返回 409 冲突。
 */
export default function Wishes() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [rejectModalOpen, setRejectModalOpen] = useState(false)
  const [rejectingId, setRejectingId] = useState<number | null>(null)
  const [detail, setDetail] = useState<AdminWishRecord | null>(null)

  const loadDetail = async (id: number) => {
    try {
      const res = await getAdminWishDetail(id)
      setDetail(res.data.data)
    } catch {
      message.error('加载详情失败')
    }
  }

  const handleApprove = async (id: number) => {
    try {
      await auditAdminWish(id, { auditStatus: 'APPROVED' })
      message.success('已通过审核')
      actionRef.current?.reload()
    } catch {
      message.error('审核操作失败')
    }
  }

  const handleReject = async (reason: string) => {
    if (rejectingId == null) return false
    try {
      await auditAdminWish(rejectingId, { auditStatus: 'REJECTED', rejectReason: reason })
      message.success('已拒绝')
      setRejectingId(null)
      actionRef.current?.reload()
      return true
    } catch {
      message.error('审核操作失败')
      return false
    }
  }

  const columns: ProColumns<AdminWishRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 90, search: false },
    { title: '标题', dataIndex: 'title', width: 200, ellipsis: true },
    { title: '作者ID', dataIndex: 'userId', width: 90, search: false },
    {
      title: '分类',
      dataIndex: 'categoryId',
      width: 120,
      valueType: 'select',
      fieldProps: { showSearch: true },
      request: async () => {
        try {
          const res = await getAdminWishCategories()
          return (res.data.data ?? []).map((c) => ({ label: c.name, value: c.id }))
        } catch {
          return []
        }
      },
      render: (_, record) => record.categoryName ?? '-',
    },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 90,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(VISIBILITY_MAP).map(([value, info]) => [value, { text: info.label }]),
      ),
      render: (_, record) => {
        const info = VISIBILITY_MAP[record.visibility]
        return info ? <Tag color={info.color}>{info.label}</Tag> : record.visibility
      },
    },
    {
      title: '心愿状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(WISH_STATUS_MAP).map(([value, info]) => [value, { text: info.label }]),
      ),
      render: (_, record) => {
        const info = WISH_STATUS_MAP[record.status]
        return info ? <Tag color={info.color}>{info.label}</Tag> : record.status
      },
    },
    {
      title: '审核状态',
      dataIndex: 'auditStatus',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(AUDIT_STATUS_MAP).map(([value, info]) => [value, { text: info.label }]),
      ),
      render: (_, record) => {
        const info = AUDIT_STATUS_MAP[record.auditStatus]
        return info ? <Tag color={info.color}>{info.label}</Tag> : record.auditStatus
      },
    },
    { title: '互动数', dataIndex: 'supportCount', width: 85, search: false, sorter: true },
    { title: '点亮数', dataIndex: 'lightCount', width: 85, search: false },
    { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <div style={{ display: 'flex', gap: 8 }}>
          <Button type="link" size="small" onClick={() => loadDetail(record.id)}>
            详情
          </Button>
          {record.auditStatus === 'PENDING' && (
            <>
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
                  setRejectingId(record.id)
                  setRejectModalOpen(true)
                }}
              >
                拒绝
              </Button>
            </>
          )}
        </div>
      ),
    },
  ]

  return (
    <>
      <ProTable<AdminWishRecord>
        headerTitle="心愿管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1300 }}
        request={async (params) => {
          return safeProTableRequest<AdminWishRecord>(() =>
            getAdminWishes({
              page: params.current,
              pageSize: params.pageSize,
              keyword: params.keyword || undefined,
              categoryId: params.categoryId,
              status: params.status,
              auditStatus: params.auditStatus,
              visibility: params.visibility,
            }),
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
      />

      <ModalForm
        title="拒绝原因"
        open={rejectModalOpen}
        onOpenChange={(open) => {
          setRejectModalOpen(open)
          if (!open) setRejectingId(null)
        }}
        onFinish={async (values) => handleReject(values.reason as string)}
        modalProps={{ destroyOnClose: true, maskClosable: false, keyboard: false }}
        width={480}
      >
        <ProFormTextArea
          name="reason"
          label="拒绝原因"
          rules={[
            { required: true, message: '请输入拒绝原因' },
            { max: 200, message: '原因不能超过200字符' },
          ]}
          fieldProps={{ rows: 3, placeholder: '请输入拒绝原因，将展示给作者' }}
        />
      </ModalForm>

      <Modal
        title={`心愿详情 #${detail?.id ?? ''}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={680}
      >
        {detail && (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="标题" span={2}>
              {detail.title}
            </Descriptions.Item>
            <Descriptions.Item label="作者ID">{detail.userId}</Descriptions.Item>
            <Descriptions.Item label="分类">{detail.categoryName}</Descriptions.Item>
            <Descriptions.Item label="果实类型">
              {FRUIT_TYPE_MAP[detail.fruitType]?.emoji} {FRUIT_TYPE_MAP[detail.fruitType]?.label ?? detail.fruitType}
            </Descriptions.Item>
            <Descriptions.Item label="可见性">
              {VISIBILITY_MAP[detail.visibility]?.label ?? detail.visibility}
            </Descriptions.Item>
            <Descriptions.Item label="心愿状态">
              {WISH_STATUS_MAP[detail.status]?.label ?? detail.status}
            </Descriptions.Item>
            <Descriptions.Item label="审核状态">
              {AUDIT_STATUS_MAP[detail.auditStatus]?.label ?? detail.auditStatus}
            </Descriptions.Item>
            <Descriptions.Item label="审核策略">{detail.auditStrategy}</Descriptions.Item>
            <Descriptions.Item label="是否用户可见">{detail.isVisible ? '是' : '否'}</Descriptions.Item>
            <Descriptions.Item label="点亮/同愿/祝福">
              {detail.lightCount} / {detail.sameWishCount} / {detail.blessCount}
            </Descriptions.Item>
            <Descriptions.Item label="总互动数">{detail.supportCount}</Descriptions.Item>
            <Descriptions.Item label="预计完成" span={2}>
              {detail.expectedAt ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="描述" span={2}>
              {detail.description || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="媒体" span={2}>
              {detail.mediaUrls?.length ? (
                <Image.PreviewGroup>
                  {detail.mediaUrls.map((url) => (
                    <Image key={url} src={url} width={80} height={80} style={{ objectFit: 'cover', marginRight: 8 }} />
                  ))}
                </Image.PreviewGroup>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="标签" span={2}>
              {detail.tags?.length ? detail.tags.join('、') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{detail.createdAt}</Descriptions.Item>
            <Descriptions.Item label="软删时间">{detail.deletedAt ?? '未删除'}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </>
  )
}
