import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Descriptions, Modal, Tag } from 'antd'
import { getRiskRecords, getRiskRecord } from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import type { ApiResponse } from '@/types/api'

interface RiskRecordItem {
  id: number
  userId: number
  riskType: string
  riskLevel: string
  description: string
  createdAt: string
  updatedAt: string
}

interface RiskRecordDetail {
  id: number
  userId: number
  username: string
  riskType: string
  riskLevel: string
  description: string
  ruleName: string
  ipAddress: string
  deviceId: string
  createdAt: string
  updatedAt: string
}

const RISK_TYPE_MAP: Record<string, { text: string; color: string }> = {
  FRAUD: { text: '欺诈', color: 'red' },
  ABUSE: { text: '滥用', color: 'orange' },
  BRUTE_FORCE: { text: '暴力破解', color: 'volcano' },
  SCALPING: { text: '黄牛', color: 'purple' },
  COLLUSION: { text: '串通', color: 'magenta' },
}

const RISK_LEVEL_MAP: Record<string, { text: string; color: string }> = {
  LOW: { text: '低', color: 'green' },
  MEDIUM: { text: '中', color: 'orange' },
  HIGH: { text: '高', color: 'red' },
  CRITICAL: { text: '严重', color: 'volcano' },
}

export default function RiskRecords() {
  const actionRef = useRef<ActionType>(null)
  const [detailVisible, setDetailVisible] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [currentDetail, setCurrentDetail] = useState<RiskRecordDetail | null>(null)

  const handleViewDetail = async (id: number) => {
    setDetailLoading(true)
    setDetailVisible(true)
    try {
      const { data: res } = await getRiskRecord(id)
      const response = res as ApiResponse<RiskRecordDetail>
      setCurrentDetail(response.data ?? null)
    } finally {
      setDetailLoading(false)
    }
  }

  const columns: ProColumns<RiskRecordItem>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '用户ID', dataIndex: 'userId', width: 100 },
    {
      title: '风险类型',
      dataIndex: 'riskType',
      width: 120,
      render: (_, record) => {
        const typeInfo = RISK_TYPE_MAP[record.riskType]
        return <Tag color={typeInfo?.color ?? 'default'}>{typeInfo?.text ?? record.riskType}</Tag>
      },
    },
    {
      title: '风险等级',
      dataIndex: 'riskLevel',
      width: 100,
      render: (_, record) => {
        const levelInfo = RISK_LEVEL_MAP[record.riskLevel]
        return <Tag color={levelInfo?.color ?? 'default'}>{levelInfo?.text ?? record.riskLevel}</Tag>
      },
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 260,
      search: false,
      ellipsis: true,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      fixed: 'right',
      render: (_, record) => [
        <Button key="detail" type="link" size="small" onClick={() => handleViewDetail(record.id)}>
          详情
        </Button>,
      ],
    },
  ]

  return (
    <>
      <ProTable<RiskRecordItem>
        headerTitle="风险记录"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1100 }}
        request={async (params) => {
          return safeProTableRequest<RiskRecordItem>(() =>
            getRiskRecords({
              page: params.current,
              pageSize: params.pageSize,
              userId: params.userId,
              riskType: params.riskType,
              riskLevel: params.riskLevel,
            })
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title="风险记录详情"
        open={detailVisible}
        onCancel={() => {
          setDetailVisible(false)
          setCurrentDetail(null)
        }}
        footer={null}
        width={700}
        loading={detailLoading}
      >
        {currentDetail && (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="记录ID">{currentDetail.id}</Descriptions.Item>
            <Descriptions.Item label="用户ID">{currentDetail.userId}</Descriptions.Item>
            <Descriptions.Item label="用户名">{currentDetail.username || '-'}</Descriptions.Item>
            <Descriptions.Item label="风险类型">
              <Tag color={RISK_TYPE_MAP[currentDetail.riskType]?.color ?? 'default'}>
                {RISK_TYPE_MAP[currentDetail.riskType]?.text ?? currentDetail.riskType}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="风险等级">
              <Tag color={RISK_LEVEL_MAP[currentDetail.riskLevel]?.color ?? 'default'}>
                {RISK_LEVEL_MAP[currentDetail.riskLevel]?.text ?? currentDetail.riskLevel}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="触发规则">{currentDetail.ruleName || '-'}</Descriptions.Item>
            <Descriptions.Item label="IP 地址">{currentDetail.ipAddress || '-'}</Descriptions.Item>
            <Descriptions.Item label="设备标识">{currentDetail.deviceId || '-'}</Descriptions.Item>
            <Descriptions.Item label="描述" span={2}>
              {currentDetail.description || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{currentDetail.createdAt}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{currentDetail.updatedAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </>
  )
}
