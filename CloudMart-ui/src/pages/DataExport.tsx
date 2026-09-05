import { useState, useEffect, useCallback } from 'react'
import { Button, Card, Empty, Popconfirm, Table, Tag, App } from 'antd'
import { ArrowLeftOutlined, DownloadOutlined, ReloadOutlined } from '@ant-design/icons'
import { history } from 'umi'
import {
  createDataExport,
  listMyExportTasks,
  downloadMyExport,
  type DataExportTask,
} from '@/api/wish'
import { useAuthStore } from '@/stores/auth'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING: { label: '排队中', color: 'default' },
  PROCESSING: { label: '生成中', color: 'processing' },
  SUCCESS: { label: '已完成', color: 'success' },
  FAILED: { label: '已失效', color: 'default' },
}

/**
 * 数据导出（合规 34.2，四AB B5 WEB 端）：
 * 触发导出 → 任务列表（状态轮询）→ 下载 JSON（7 天有效期）。
 */
export default function DataExportPage() {
  const { message } = App.useApp()
  const { user, userLoading } = useAuthStore()
  const [tasks, setTasks] = useState<DataExportTask[]>([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [downloadingId, setDownloadingId] = useState<string | number | null>(null)

  const load = useCallback(async () => {
    if (!user) return
    try {
      const res = await listMyExportTasks()
      if (res.data.success) setTasks(res.data.data ?? [])
    } catch {
      // 静默
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    if (!user && !userLoading) {
      history.push('/login?redirect=/settings/export')
      return
    }
    if (user) load()
  }, [user, userLoading, load])

  // PROCESSING/PENDING 任务每 3s 轮询至完成
  useEffect(() => {
    const pending = tasks.some((t) => t.status === 'PENDING' || t.status === 'PROCESSING')
    if (!pending) return
    const timer = setInterval(load, 3000)
    return () => clearInterval(timer)
  }, [tasks, load])

  const handleCreate = async () => {
    setCreating(true)
    try {
      const res = await createDataExport()
      if (res.data.success) {
        message.success('导出任务已创建，正在后台生成')
        load()
      }
    } catch {
      // 拦截器已提示
    } finally {
      setCreating(false)
    }
  }

  const handleDownload = async (task: DataExportTask) => {
    setDownloadingId(task.id)
    try {
      const res = await downloadMyExport(task.id)
      const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `wish-data-export-${task.id}.json`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
    } catch {
      message.error('下载失败（可能已过期），请重新发起导出')
      load()
    } finally {
      setDownloadingId(null)
    }
  }

  return (
    <div style={{ maxWidth: 860, margin: '24px auto', padding: '0 16px' }}>
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => history.push('/user/center')} style={{ marginBottom: 12 }}>
        个人中心
      </Button>
      <Card
        title="数据导出"
        extra={
          <div style={{ display: 'flex', gap: 8 }}>
            <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
            <Popconfirm title="将聚合你的心愿/成长/互动等数据生成 JSON（7 天有效），确认发起？" onConfirm={handleCreate}>
              <Button type="primary" loading={creating}>发起导出</Button>
            </Popconfirm>
          </div>
        }
      >
        <p style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>
          依据合规要求提供个人数据副本导出：包含你的心愿、成长记录、还愿故事、互动与收藏等。文件为 JSON 格式，生成后 7 天内可下载，逾期自动清除。
        </p>
        {loading ? (
          <Empty description="加载中" />
        ) : tasks.length === 0 ? (
          <Empty description="还没有导出任务" />
        ) : (
          <Table
            rowKey="id"
            size="small"
            dataSource={tasks}
            pagination={{ pageSize: 8 }}
            columns={[
              { title: '任务 ID', dataIndex: 'id', width: 200, ellipsis: true },
              {
                title: '状态',
                dataIndex: 'status',
                width: 100,
                render: (v: string) => {
                  const m = STATUS_MAP[v]
                  return m ? <Tag color={m.color}>{m.label}</Tag> : v
                },
              },
              {
                title: '发起时间',
                dataIndex: 'createdAt',
                width: 180,
                render: (v: string) => new Date(v).toLocaleString('zh-CN'),
              },
              {
                title: '有效期至',
                dataIndex: 'expiresAt',
                width: 180,
                render: (v: string | null) => (v ? new Date(v).toLocaleString('zh-CN') : '-'),
              },
              {
                title: '操作',
                width: 120,
                render: (_, record) =>
                  record.status === 'SUCCESS' ? (
                    <Button
                      size="small"
                      icon={<DownloadOutlined />}
                      loading={downloadingId === record.id}
                      onClick={() => handleDownload(record)}
                    >
                      下载
                    </Button>
                  ) : (
                    <span style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>
                      {record.status === 'FAILED' ? '已失效，请重新发起' : '生成中…'}
                    </span>
                  ),
              },
            ]}
          />
        )}
      </Card>
    </div>
  )
}
