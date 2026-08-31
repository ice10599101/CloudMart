import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormSelect,
  ProFormTextArea,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Modal, Descriptions } from 'antd'
import {
  getAdminReports,
  handleReport,
} from '@/api/admin/community'
import type { AdminReportRecord } from '@/api/admin/community'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

const TARGET_TYPE_MAP: Record<string, { label: string; color: string }> = {
  POST: { label: '帖子', color: 'blue' },
  COMMENT: { label: '评论', color: 'green' },
  USER: { label: '用户', color: 'orange' },
}

const REPORT_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '待处理', color: 'orange' },
  1: { label: '处理中', color: 'blue' },
  2: { label: '已驳回', color: 'default' },
  3: { label: '已处理', color: 'green' },
}

export default function Reports() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [handleModalVisible, setHandleModalVisible] = useState(false)
  const [currentReport, setCurrentReport] = useState<AdminReportRecord | null>(null)
  const [detailRecord, setDetailRecord] = useState<AdminReportRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleHandleReport = async (values: Record<string, any>) => {
    if (!currentReport) return false
    return confirmSubmit(async () => {
      await handleReport(currentReport.id, values)
      message.success(values.status === 3 ? '处理成功' : '已驳回')
      setCurrentReport(null)
      actionRef.current?.reload()
    })
  }

  const columns: ProColumns<AdminReportRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '举报人ID', dataIndex: 'reporterId', width: 100, search: false },
    {
      title: '目标类型',
      dataIndex: 'targetType',
      width: 100,
      render: (_, record) => {
        const typeInfo = TARGET_TYPE_MAP[record.targetType] ?? { label: record.targetType, color: 'default' }
        return <Tag color={typeInfo.color}>{typeInfo.label}</Tag>
      },
    },
    { title: '目标ID', dataIndex: 'targetId', width: 100, search: false },
    {
      title: '举报原因',
      dataIndex: 'reason',
      width: 200,
      ellipsis: true,
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = REPORT_STATUS_MAP[record.status] ?? { label: '未知', color: 'default' }
        return <Tag color={statusInfo.color}>{statusInfo.label}</Tag>
      },
    },
    { title: '处理人ID', dataIndex: 'handlerId', width: 100, search: false },
    {
      title: '处理备注',
      dataIndex: 'handleNote',
      width: 160,
      ellipsis: true,
      search: false,
    },
    {
      title: '处理时间',
      dataIndex: 'handleTime',
      width: 180,
      valueType: 'dateTime',
      search: false,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      fixed: 'right',
      render: (_, record) => {
        const actions: React.ReactNode[] = [
          <Button key="detail" type="link" size="small" onClick={() => setDetailRecord(record)}>详情</Button>,
        ]
        if (record.status === 0 || record.status === 1) {
          actions.push(
            <Button
              key="handle"
              type="link"
              size="small"
              onClick={() => {
                setCurrentReport(record)
                setHandleModalVisible(true)
              }}
            >
              处理
            </Button>,
          )
        }
        return actions
      },
    },
  ]

  return (
    <>
      <ProTable<AdminReportRecord>
        headerTitle="举报管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1400 }}
        request={async (params) => {
          return safeProTableRequest<AdminReportRecord>(() =>
            getAdminReports({
              page: params.current,
              pageSize: params.pageSize,
              status: params.status,
              targetType: params.targetType,
            })
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title="处理举报"
        open={handleModalVisible}
        onOpenChange={createHandleOpenChange(setHandleModalVisible, () => setCurrentReport(null))}
        onFinish={handleHandleReport}
        initialValues={{ status: 3 }}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={480}
      >
        <ProFormSelect
          name="status"
          label="处理结果"
          options={[
            { label: '驳回', value: 2 },
            { label: '已处理', value: 3 },
          ]}
          rules={[{ required: true, message: '请选择处理结果' }]}
        />
        <ProFormTextArea
          name="handleNote"
          label="处理备注"
          placeholder="请输入处理备注"
          fieldProps={{ rows: 4 }}
        />
      </ModalForm>

      <Modal
        title="举报详情"
        open={!!detailRecord}
        onCancel={() => setDetailRecord(null)}
        footer={null}
      >
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="ID">{detailRecord?.id}</Descriptions.Item>
          <Descriptions.Item label="举报人ID">{detailRecord?.reporterId}</Descriptions.Item>
          <Descriptions.Item label="目标类型">
            {(() => {
              const info = TARGET_TYPE_MAP[detailRecord?.targetType ?? '']
              return info ? <Tag color={info.color}>{info.label}</Tag> : detailRecord?.targetType
            })()}
          </Descriptions.Item>
          <Descriptions.Item label="目标ID">{detailRecord?.targetId}</Descriptions.Item>
          <Descriptions.Item label="举报原因">{detailRecord?.reason}</Descriptions.Item>
          <Descriptions.Item label="状态">
            {(() => {
              const info = REPORT_STATUS_MAP[detailRecord?.status ?? -1]
              return info ? <Tag color={info.color}>{info.label}</Tag> : '未知'
            })()}
          </Descriptions.Item>
          <Descriptions.Item label="处理人ID">{detailRecord?.handlerId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="处理备注">{detailRecord?.handleNote ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="处理时间">{detailRecord?.handleTime ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{detailRecord?.createdAt}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{detailRecord?.updatedAt}</Descriptions.Item>
        </Descriptions>
      </Modal>
    </>
  )
}
