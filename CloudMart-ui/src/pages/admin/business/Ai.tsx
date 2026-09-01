import { useState } from 'react'
import { Button, Card, Input, Popconfirm, Space, Table, Tag, Typography } from 'antd'
import {
  CloudUploadOutlined,
  DeleteOutlined,
  CloudSyncOutlined,
} from '@ant-design/icons'
import { triggerFullVectorSync, syncProductVector, deleteProductVector } from '@/api/admin/business'
import { useMessage } from '@/utils/useMessage'

interface SyncLog {
  id: number
  action: string
  target: string
  status: string
  message: string
  timestamp: string
}

const { Title, Text } = Typography

export default function Ai() {
  const message = useMessage()
  const [productId, setProductId] = useState('')
  const [fullSyncLoading, setFullSyncLoading] = useState(false)
  const [singleSyncLoading, setSingleSyncLoading] = useState(false)
  const [deleteLoading, setDeleteLoading] = useState(false)
  const [syncLogs, setSyncLogs] = useState<SyncLog[]>([])
  const addLog = (action: string, target: string, status: string, logMessage: string) => {
    setSyncLogs((prev) => [
      {
        id: prev.length + 1,
        action,
        target,
        status,
        message: logMessage,
        timestamp: new Date().toLocaleString('zh-CN'),
      },
      ...prev,
    ])
  }

  const handleFullSync = async () => {
    setFullSyncLoading(true)
    addLog('全量同步', '全部商品', 'PROCESSING', '全量向量同步已发起...')
    try {
      await triggerFullVectorSync()
      addLog('全量同步', '全部商品', 'SUCCESS', '全量向量同步完成')
      message.success('全量同步完成')
    } catch {
      addLog('全量同步', '全部商品', 'FAILED', '全量向量同步失败')
    } finally {
      setFullSyncLoading(false)
    }
  }

  const handleSingleSync = async () => {
    const trimmedId = productId.trim()
    if (!trimmedId) {
      message.warning('请输入商品ID')
      return
    }
    setSingleSyncLoading(true)
    addLog('单商品同步', `商品 ${trimmedId}`, 'PROCESSING', `商品 ${trimmedId} 向量同步已发起...`)
    try {
      await syncProductVector(trimmedId)
      addLog('单商品同步', `商品 ${trimmedId}`, 'SUCCESS', `商品 ${trimmedId} 向量同步完成`)
      message.success('同步完成')
      setProductId('')
    } catch {
      addLog('单商品同步', `商品 ${trimmedId}`, 'FAILED', `商品 ${trimmedId} 向量同步失败`)
    } finally {
      setSingleSyncLoading(false)
    }
  }

  const handleDeleteVector = async () => {
    const trimmedId = productId.trim()
    if (!trimmedId) {
      message.warning('请输入商品ID')
      return
    }
    setDeleteLoading(true)
    addLog('删除向量', `商品 ${trimmedId}`, 'PROCESSING', `商品 ${trimmedId} 向量删除已发起...`)
    try {
      await deleteProductVector(trimmedId)
      addLog('删除向量', `商品 ${trimmedId}`, 'SUCCESS', `商品 ${trimmedId} 向量删除完成`)
      message.success('删除完成')
      setProductId('')
    } catch {
      addLog('删除向量', `商品 ${trimmedId}`, 'FAILED', `商品 ${trimmedId} 向量删除失败`)
    } finally {
      setDeleteLoading(false)
    }
  }

  const logColumns = [
    {
      title: '时间',
      dataIndex: 'timestamp',
      width: 180,
    },
    {
      title: '操作',
      dataIndex: 'action',
      width: 120,
      render: (text: string) => <Tag color="blue">{text}</Tag>,
    },
    {
      title: '目标',
      dataIndex: 'target',
      width: 140,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => {
        const colorMap: Record<string, string> = {
          PROCESSING: 'processing',
          SUCCESS: 'green',
          FAILED: 'red',
        }
        const textMap: Record<string, string> = {
          PROCESSING: '处理中',
          SUCCESS: '成功',
          FAILED: '失败',
        }
        return <Tag color={colorMap[status] ?? 'default'}>{textMap[status] ?? status}</Tag>
      },
    },
    {
      title: '消息',
      dataIndex: 'message',
      ellipsis: true,
    },
  ]

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card>
        <Title level={5} style={{ marginTop: 0 }}>向量同步操作</Title>

        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <Text strong>全量同步</Text>
            <div style={{ marginTop: 8 }}>
              <Popconfirm
                title="全量同步会重新索引所有商品向量，确认执行？"
                onConfirm={handleFullSync}
              >
                <Button
                  type="primary"
                  icon={<CloudSyncOutlined />}
                  loading={fullSyncLoading}
                >
                  全量向量同步
                </Button>
              </Popconfirm>
            </div>
          </div>

          <div>
            <Text strong>单商品操作</Text>
            <div style={{ marginTop: 8 }}>
              <Space>
                <Input
                  placeholder="输入商品ID"
                  value={productId}
                  onChange={(e) => setProductId(e.target.value)}
                  style={{ width: 200 }}
                  onPressEnter={handleSingleSync}
                />
                <Button
                  type="primary"
                  icon={<CloudUploadOutlined />}
                  loading={singleSyncLoading}
                  onClick={handleSingleSync}
                >
                  同步向量
                </Button>
                <Popconfirm
                  title="确认删除该商品的向量数据？"
                  onConfirm={handleDeleteVector}
                >
                  <Button
                    danger
                    icon={<DeleteOutlined />}
                    loading={deleteLoading}
                  >
                    删除向量
                  </Button>
                </Popconfirm>
              </Space>
            </div>
          </div>
        </Space>
      </Card>

      <Card title="操作日志">
        <Table<SyncLog>
          columns={logColumns}
          dataSource={syncLogs}
          rowKey="id"
          pagination={{ pageSize: 10 }}
          size="small"
          locale={{ emptyText: '暂无操作记录' }}
        />
      </Card>
    </Space>
  )
}
