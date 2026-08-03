import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tag, Descriptions } from 'antd'
import { DeleteOutlined, EyeOutlined } from '@ant-design/icons'
import {
  getOperLogs,
  deleteOperLog,
  cleanOperLogs,
} from '@/api/admin/system'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

interface OperLogRecord {
  id: number
  title: string
  businessType: number
  method: string
  requestMethod: string
  operName: string
  operUrl: string
  operIp: string
  operLocation: string
  operParam: string
  jsonResult: string
  status: number
  errorMsg: string
  operTime: string
  costTime: number
}

const BUSINESS_TYPE_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '其它', color: 'default' },
  1: { label: '新增', color: 'green' },
  2: { label: '修改', color: 'blue' },
  3: { label: '删除', color: 'red' },
  4: { label: '授权', color: 'purple' },
  5: { label: '导出', color: 'cyan' },
  6: { label: '导入', color: 'orange' },
  7: { label: '强退', color: 'magenta' },
  8: { label: '生成代码', color: 'geekblue' },
}

export default function OperLog() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [detailVisible, setDetailVisible] = useState(false)
  const [detailRecord, setDetailRecord] = useState<OperLogRecord | null>(null)

  const handleClean = async () => {
    await cleanOperLogs()
    message.success('清空成功')
    actionRef.current?.reload()
  }

  const handleDelete = async (id: number) => {
    await deleteOperLog(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const handleViewDetail = (record: OperLogRecord) => {
    setDetailRecord(record)
    setDetailVisible(true)
  }

  const columns: ProColumns<OperLogRecord>[] = [
    { title: '日志ID', dataIndex: 'id', width: 80, search: false },
    { title: '系统模块', dataIndex: 'title', width: 120 },
    {
      title: '操作类型',
      dataIndex: 'businessType',
      width: 100,
      render: (_, record) => {
        const typeInfo = BUSINESS_TYPE_MAP[record.businessType] ?? { label: '未知', color: 'default' }
        return <Tag color={typeInfo.color}>{typeInfo.label}</Tag>
      },
    },
    { title: '请求方式', dataIndex: 'requestMethod', width: 90, search: false },
    { title: '操作人员', dataIndex: 'operName', width: 110 },
    { title: '操作地址', dataIndex: 'operIp', width: 140, search: false },
    {
      title: '操作状态',
      dataIndex: 'status',
      width: 90,
      valueType: 'select',
      valueEnum: {
        0: { text: '成功', status: 'Success' },
        1: { text: '失败', status: 'Error' },
      },
    },
    { title: '操作时间', dataIndex: 'operTime', width: 180, valueType: 'dateTime' },
    { title: '耗时(ms)', dataIndex: 'costTime', width: 90, search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="detail"
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleViewDetail(record)}
        >
          详情
        </Button>,
        <Popconfirm
          key="delete"
          title="确认删除该日志？"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<OperLogRecord>
        headerTitle="操作日志"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1300 }}
        request={async (params) => {
          return safeProTableRequest<OperLogRecord>(() =>
            getOperLogs({
              page: params.current,
              pageSize: params.pageSize,
              title: params.title,
              businessType: params.businessType,
              operName: params.operName,
              status: params.status,
              beginTime: params.operTime?.[0],
              endTime: params.operTime?.[1],
            })
          )
        }}
        toolBarRender={() => [
          <Popconfirm
            key="clean"
            title="确认清空所有操作日志？此操作不可恢复！"
            onConfirm={handleClean}
          >
            <Button icon={<DeleteOutlined />} danger>清空</Button>
          </Popconfirm>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title="操作日志详情"
        open={detailVisible}
        onOpenChange={setDetailVisible}
        onFinish={async () => true}
        submitter={false}
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={700}
      >
        {detailRecord && (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="系统模块">{detailRecord.title}</Descriptions.Item>
            <Descriptions.Item label="操作类型">
              <Tag color={BUSINESS_TYPE_MAP[detailRecord.businessType]?.color ?? 'default'}>
                {BUSINESS_TYPE_MAP[detailRecord.businessType]?.label ?? '未知'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="请求方式">{detailRecord.requestMethod}</Descriptions.Item>
            <Descriptions.Item label="操作人员">{detailRecord.operName}</Descriptions.Item>
            <Descriptions.Item label="操作地址">{detailRecord.operIp}</Descriptions.Item>
            <Descriptions.Item label="操作地点">{detailRecord.operLocation}</Descriptions.Item>
            <Descriptions.Item label="请求URL" span={2}>{detailRecord.operUrl}</Descriptions.Item>
            <Descriptions.Item label="请求方法" span={2}>
              <code style={{ fontSize: 12, wordBreak: 'break-all' }}>{detailRecord.method}</code>
            </Descriptions.Item>
            <Descriptions.Item label="请求参数" span={2}>
              <pre style={{ maxHeight: 200, overflow: 'auto', fontSize: 12, margin: 0 }}>
                {detailRecord.operParam || '-'}
              </pre>
            </Descriptions.Item>
            <Descriptions.Item label="返回结果" span={2}>
              <pre style={{ maxHeight: 200, overflow: 'auto', fontSize: 12, margin: 0 }}>
                {detailRecord.jsonResult || '-'}
              </pre>
            </Descriptions.Item>
            <Descriptions.Item label="操作状态">
              <Tag color={detailRecord.status === 0 ? 'success' : 'error'}>
                {detailRecord.status === 0 ? '成功' : '失败'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="耗时">{detailRecord.costTime}ms</Descriptions.Item>
            {detailRecord.errorMsg && (
              <Descriptions.Item label="错误信息" span={2}>
                <span style={{ color: '#FF4757' }}>{detailRecord.errorMsg}</span>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="操作时间" span={2}>{detailRecord.operTime}</Descriptions.Item>
          </Descriptions>
        )}
      </ModalForm>
    </>
  )
}
