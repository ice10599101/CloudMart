/**
 * 统一隐私中心（N05）：AI 授权 / 导出进度 / 注销阶段 / 默认关闭项一览。
 * 后端：GET /wish/v2/my/privacy（聚合视图，单一数据源）。
 */
import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Descriptions, Space, Tag, Typography } from 'antd'
import { history } from 'umi'
import request from '@/utils/request'

interface PrivacyView {
  aiDataProcessing: { granted: boolean; version: string; updatedAt: string }
  dataExport: { status: string; taskId?: string; expiresAt?: string }
  accountDeletion: { status: string; executeAfter?: string; executedAt?: string }
  defaults: { locationSharing: boolean; fulfillmentAutoShare: boolean; rmbPayment: boolean }
}

const DELETION_LABEL: Record<string, string> = {
  NONE: '未申请',
  PENDING: '宽限期内（30 天）',
  EXECUTING: '正在执行数据清理',
  EXECUTED: '已完成注销',
  CANCELED: '已取消',
}

export default function WishPrivacyCenter() {
  const { message } = App.useApp()
  const [view, setView] = useState<PrivacyView | null>(null)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await request.get('/wish/v2/my/privacy')
      setView((res as { data: PrivacyView }).data)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const cancelDeletion = async () => {
    await request.delete('/users/account-deletion')
    message.success('注销已取消')
    void load()
  }

  if (loading && !view) {
    return <Card loading style={{ maxWidth: 720, margin: '24px auto' }} />
  }

  return (
    <div style={{ maxWidth: 720, margin: '24px auto', display: 'grid', gap: 16 }}>
      <Card title={<Typography.Title level={4}>隐私中心</Typography.Title>}>
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="AI 数据处理授权">
            {view?.aiDataProcessing.granted
              ? <Tag color="green">已授权（v{view.aiDataProcessing.version}）</Tag>
              : <Tag>未授权</Tag>}
            <Button size="small" type="link" onClick={() => history.push('/wish/settings/ai')}>
              管理授权
            </Button>
          </Descriptions.Item>
          <Descriptions.Item label="数据导出">
            <Space>
              <Tag>{view?.dataExport.status ?? 'NONE'}</Tag>
              {view?.dataExport.status === 'SUCCESS' && (
                <Button size="small" type="link"
                  onClick={() => history.push('/wish/settings/export')}>去下载</Button>
              )}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="全账号注销">
            <Space>
              <Tag color={view?.accountDeletion.status === 'PENDING' ? 'red' : 'default'}>
                {DELETION_LABEL[view?.accountDeletion.status ?? 'NONE'] ?? view?.accountDeletion.status}
              </Tag>
              {view?.accountDeletion.status === 'PENDING' && (
                <Button size="small" danger onClick={() => void cancelDeletion()}>取消注销</Button>
              )}
            </Space>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="默认关闭项">
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="位置共享">
            <Tag color="default">{view?.defaults.locationSharing ? '开启' : '关闭（默认）'}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="还愿自动分享到社区">
            <Tag color="default">{view?.defaults.fulfillmentAutoShare ? '开启' : '关闭（默认）'}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="人民币支付">
            <Tag color="default">{view?.defaults.rmbPayment ? '开启' : '关闭（默认）'}</Tag>
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  )
}
