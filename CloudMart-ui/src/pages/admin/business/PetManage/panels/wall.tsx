import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  DatePicker,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  listPetWallMessages,
  updatePetWallMessageStatus,
} from '@/api/admin/pet'

const { Text } = Typography

/** 留言审核面板 */
export function WallPanel() {
  const [messageApi, contextHolder] = message.useMessage()
  const [rows, setRows] = useState<Record<string, unknown>[]>([])
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState<string | undefined>(undefined)
  const [petId, setPetId] = useState<number | undefined>(undefined)
  // F6：时间范围筛选 + CSV 导出（后端流式拼装，20 万行上限）
  const [dateRange, setDateRange] = useState<[string, string] | null>(null)

  const exportCsv = () => {
    const params = new URLSearchParams()
    if (dateRange) {
      params.set('from', dateRange[0])
      params.set('to', dateRange[1])
    }
    // 走网关同源下载（携带会话 Cookie；浏览器原生处理文件流）
    window.open(`/api/admin/pet/wall/messages/export?${params.toString()}`, '_blank')
    messageApi.success('已开始导出（大数据量浏览器可能需要数秒）')
  }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await listPetWallMessages({ status, petId, page: 1, size: 50 })
      if (res.success) {
        setRows(res.data || [])
      }
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false)
    }
  }, [status, petId])

  useEffect(() => {
    void load()
  }, [load])

  const changeStatus = async (row: Record<string, unknown>, next: string) => {
    try {
      const { data: res } = await updatePetWallMessageStatus(String(row.id), next)
      if (res.success) {
        messageApi.success('已更新')
        void load()
      }
    } catch {
      // 拦截器已提示
    }
  }

  const columns: ColumnsType<Record<string, unknown>> = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    { title: '宠物ID', dataIndex: 'petId', width: 90 },
    { title: '作者用户', dataIndex: 'authorUserId', width: 110 },
    { title: '内容', dataIndex: 'content', ellipsis: true },
    { title: '点赞', dataIndex: 'likeCount', width: 70 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: unknown) => {
        const text = String(value)
        const color = text === 'NORMAL' ? 'green' : text === 'HIDDEN' ? 'orange' : 'default'
        const label = text === 'NORMAL' ? '正常' : text === 'HIDDEN' ? '已隐藏' : '已删除'
        return <Tag color={color}>{label}</Tag>
      },
    },
    { title: '时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作',
      width: 200,
      render: (_: unknown, row) => (
        <Space>
          {row.status !== 'HIDDEN' && (
            <Popconfirm title="隐藏这条留言？用户端将立即不可见" onConfirm={() => changeStatus(row, 'HIDDEN')}>
              <Button type="link" size="small" danger>
                隐藏
              </Button>
            </Popconfirm>
          )}
          {row.status !== 'NORMAL' && (
            <Button type="link" size="small" onClick={() => changeStatus(row, 'NORMAL')}>
              恢复
            </Button>
          )}
          {row.status !== 'DELETED' && (
            <Popconfirm title="删除这条留言？（保留审计）" onConfirm={() => changeStatus(row, 'DELETED')}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      {contextHolder}
      <Space style={{ marginBottom: 12 }}>
        <InputNumber
          placeholder="宠物 ID"
          value={petId}
          onChange={(value) => setPetId(value ?? undefined)}
          style={{ width: 140 }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 160 }}
          value={status}
          onChange={setStatus}
          options={[
            { value: 'NORMAL', label: '正常' },
            { value: 'HIDDEN', label: '已隐藏' },
            { value: 'DELETED', label: '已删除' },
          ]}
        />
        <Button type="primary" onClick={() => void load()}>
          查询
        </Button>
        <DatePicker.RangePicker
          onChange={(values) =>
            setDateRange(
              values && values[0] && values[1]
                ? [values[0].format('YYYY-MM-DD'), values[1].format('YYYY-MM-DD')]
                : null,
            )
          }
        />
        <Button onClick={exportCsv}>导出 CSV</Button>
        <Text type="secondary">隐藏后用户端立即不可见，管理端仍保留（可溯源、可恢复）</Text>
      </Space>
      <Table
        rowKey={(row) => String(row.id)}
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 10, showSizeChanger: false }}
        scroll={{ x: 1000 }}
      />
    </div>
  )
}
