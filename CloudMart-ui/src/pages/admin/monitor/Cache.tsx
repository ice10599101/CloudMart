import { useEffect, useState } from 'react'
import { Card, Col, Row, Statistic, Descriptions, Spin } from 'antd'
import {
  DatabaseOutlined,
  ClockCircleOutlined,
  LinkOutlined,
  CloudOutlined,
} from '@ant-design/icons'
import { getCacheInfo } from '@/api/admin/monitor'
import type { ApiResponse } from '@/types/api'

interface RedisInfo {
  redisVersion: string
  uptimeInSeconds: number
  connectedClients: number
  usedMemory: string
  usedMemoryHuman: string
  totalSystemMemory: string
  maxMemory: string
  maxMemoryHuman: string
  usedMemoryPercent: number
  dbSize: number
  keyspaceHits: number
  keyspaceMisses: number
  hitRate: number
  commandsProcessed: number
  opsPerSec: number
  avgTtl: number
  dbKeys: Record<string, { keys: number; expires: number; avgTtl: number }>
  commandStats: Array<{ name: string; value: number; count: number }>
}

function formatUptime(seconds: number): string {
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const parts: string[] = []
  if (days > 0) parts.push(`${days}天`)
  if (hours > 0) parts.push(`${hours}小时`)
  if (minutes > 0) parts.push(`${minutes}分钟`)
  return parts.join('') || '不到1分钟'
}

export default function Cache() {
  const [cacheData, setCacheData] = useState<RedisInfo | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchCacheInfo()
  }, [])

  async function fetchCacheInfo() {
    setLoading(true)
    try {
      const { data: res } = await getCacheInfo()
      const response = res as ApiResponse<RedisInfo>
      setCacheData(response.data)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <Spin size="large" description="加载中..." />
      </div>
    )
  }

  if (!cacheData) return null

  const topStats = [
    {
      title: 'Redis 版本',
      value: cacheData.redisVersion,
      icon: <DatabaseOutlined style={{ fontSize: 24, color: 'var(--color-primary)' }} />,
      bg: 'rgba(var(--color-primary-rgb), 0.12)',
    },
    {
      title: '运行天数',
      value: formatUptime(cacheData.uptimeInSeconds),
      icon: <ClockCircleOutlined style={{ fontSize: 24, color: '#2ED573' }} />,
      bg: 'rgba(46, 213, 115, 0.1)',
    },
    {
      title: '连接数',
      value: cacheData.connectedClients,
      icon: <LinkOutlined style={{ fontSize: 24, color: '#A78BFA' }} />,
      bg: 'rgba(167, 139, 250, 0.1)',
    },
    {
      title: '内存使用',
      value: cacheData.usedMemoryHuman,
      icon: <CloudOutlined style={{ fontSize: 24, color: '#FFA502' }} />,
      bg: 'rgba(255, 165, 2, 0.1)',
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Row gutter={[16, 16]}>
        {topStats.map((stat) => (
          <Col xs={24} sm={12} lg={6} key={stat.title}>
            <Card hoverable style={{ borderRadius: 10 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Statistic
                  title={stat.title}
                  value={stat.value}
                  styles={{ content: { fontSize: 22 } }}
                />
                <div
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: 10,
                    background: stat.bg,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {stat.icon}
                </div>
              </div>
            </Card>
          </Col>
        ))}

        <Col xs={24} lg={12}>
          <Card title="Redis 信息" style={{ borderRadius: 10 }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="Redis 版本">{cacheData.redisVersion}</Descriptions.Item>
              <Descriptions.Item label="运行天数">{formatUptime(cacheData.uptimeInSeconds)}</Descriptions.Item>
              <Descriptions.Item label="连接客户端数">{cacheData.connectedClients}</Descriptions.Item>
              <Descriptions.Item label="已用内存">{cacheData.usedMemoryHuman}</Descriptions.Item>
              <Descriptions.Item label="系统总内存">{cacheData.totalSystemMemory}</Descriptions.Item>
              <Descriptions.Item label="最大内存限制">{cacheData.maxMemoryHuman || '无限制'}</Descriptions.Item>
              <Descriptions.Item label="内存使用率">{(cacheData.usedMemoryPercent ?? 0).toFixed(2)}%</Descriptions.Item>
              <Descriptions.Item label="每秒处理命令数">{cacheData.opsPerSec}</Descriptions.Item>
              <Descriptions.Item label="累计处理命令数">{cacheData.commandsProcessed}</Descriptions.Item>
              <Descriptions.Item label="平均 TTL">{cacheData.avgTtl > 0 ? `${Math.round(cacheData.avgTtl)} ms` : '-'}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="Key 统计" style={{ borderRadius: 10 }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="Key 总数">{cacheData.dbSize}</Descriptions.Item>
              <Descriptions.Item label="命中率">{(cacheData.hitRate ?? 0).toFixed(2)}%</Descriptions.Item>
              <Descriptions.Item label="命中次数">{cacheData.keyspaceHits}</Descriptions.Item>
              <Descriptions.Item label="未命中次数">{cacheData.keyspaceMisses}</Descriptions.Item>
            </Descriptions>
            {Object.entries(cacheData.dbKeys ?? {}).map(([dbName, dbInfo]) => (
              <Descriptions
                key={dbName}
                column={3}
                size="small"
                bordered
                title={dbName}
                style={{ marginTop: 16 }}
              >
                <Descriptions.Item label="Key 数量">{dbInfo.keys}</Descriptions.Item>
                <Descriptions.Item label="带过期时间">{dbInfo.expires}</Descriptions.Item>
                <Descriptions.Item label="平均 TTL">{dbInfo.avgTtl > 0 ? `${Math.round(dbInfo.avgTtl)} ms` : '-'}</Descriptions.Item>
              </Descriptions>
            ))}
          </Card>
        </Col>

        <Col xs={24}>
          <Card title="命令统计" style={{ borderRadius: 10 }}>
            <Descriptions column={3} size="small" bordered>
              {(cacheData.commandStats ?? []).map((cmd) => (
                <Descriptions.Item key={cmd.name} label={cmd.name}>
                  {cmd.count} 次
                </Descriptions.Item>
              ))}
            </Descriptions>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
