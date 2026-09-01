import { useEffect, useState } from 'react'
import { Card, Col, Row, Progress, Descriptions, Spin } from 'antd'
import { getServerInfo } from '@/api/admin/monitor'
import type { ApiResponse } from '@/types/api'

interface CpuInfo {
  cpuNum: number
  total: number
  sys: number
  user: number
  wait: number
  free: number
  usage: number
}

interface MemInfo {
  total: number
  used: number
  free: number
  usage: number
}

interface JvmInfo {
  total: number
  max: number
  free: number
  used: number
  usage: number
  name: string
  version: string
  home: string
  startTime: string
  runTime: string
  inputArgs: string[]
}

interface SysInfo {
  computerName: string
  osName: string
  osArch: string
  computerIp: string
  userDir: string
}

interface DiskInfo {
  dirName: string
  sysTypeName: string
  typeName: string
  total: string
  free: string
  used: string
  usage: number
}

interface ServerData {
  cpu: CpuInfo
  mem: MemInfo
  jvm: JvmInfo
  sys: SysInfo
  disk: DiskInfo[]
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${units[i]}`
}

function getProgressStatus(percent: number): 'success' | 'normal' | 'exception' {
  if (percent < 60) return 'success'
  if (percent < 80) return 'normal'
  return 'exception'
}

export default function Server() {
  const [serverData, setServerData] = useState<ServerData | null>(null)
  const [loading, setLoading] = useState(true)

  async function fetchServerInfo() {
    setLoading(true)
    try {
      const { data: res } = await getServerInfo()
      const response = res as ApiResponse<ServerData>
      setServerData(response.data)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchServerInfo()
  }, [])

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <Spin size="large" description="加载中..." />
      </div>
    )
  }

  if (!serverData) return null

  const { cpu, mem, jvm, sys, disk } = serverData

  return (
    <div style={{ padding: 24 }}>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="CPU 使用率" style={{ borderRadius: 10 }}>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 24 }}>
              <Progress
                type="dashboard"
                percent={Math.round(cpu?.usage ?? 0)}
                status={getProgressStatus(cpu?.usage ?? 0)}
                format={(percent) => `${percent}%`}
                size={160}
              />
            </div>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="核心数">{cpu?.cpuNum ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="用户使用率">{(cpu?.user ?? 0).toFixed(2)}%</Descriptions.Item>
              <Descriptions.Item label="系统使用率">{(cpu?.sys ?? 0).toFixed(2)}%</Descriptions.Item>
              <Descriptions.Item label="当前空闲率">{(cpu?.free ?? 0).toFixed(2)}%</Descriptions.Item>
              <Descriptions.Item label="IO 等待率">{(cpu?.wait ?? 0).toFixed(2)}%</Descriptions.Item>
              <Descriptions.Item label="总使用率">{(cpu?.usage ?? 0).toFixed(2)}%</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="内存使用情况" style={{ borderRadius: 10 }}>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 24 }}>
              <Progress
                type="dashboard"
                percent={Math.round(mem?.usage ?? 0)}
                status={getProgressStatus(mem?.usage ?? 0)}
                format={(percent) => `${percent}%`}
                size={160}
              />
            </div>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="总内存">{formatBytes(mem?.total ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="已用内存">{formatBytes(mem?.used ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="剩余内存">{formatBytes(mem?.free ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="使用率">{(mem?.usage ?? 0).toFixed(2)}%</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="JVM 信息" style={{ borderRadius: 10 }}>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 24 }}>
              <Progress
                type="dashboard"
                percent={Math.round(jvm?.usage ?? 0)}
                status={getProgressStatus(jvm?.usage ?? 0)}
                format={(percent) => `${percent}%`}
                size={160}
              />
            </div>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="JVM 名称">{jvm?.name ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="JVM 版本">{jvm?.version ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="JVM 主目录">{jvm?.home ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="启动时间">{jvm?.startTime ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="运行时长">{jvm?.runTime ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="最大内存">{formatBytes(jvm?.max ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="已用内存">{formatBytes(jvm?.used ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="空闲内存">{formatBytes(jvm?.free ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="总内存">{formatBytes(jvm?.total ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="使用率">{(jvm?.usage ?? 0).toFixed(2)}%</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="服务器信息" style={{ borderRadius: 10 }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="服务器名称">{sys?.computerName ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="服务器IP">{sys?.computerIp ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="操作系统">{sys?.osName ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="系统架构">{sys?.osArch ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="项目路径" span={2}>{sys?.userDir ?? '-'}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24}>
          <Card title="磁盘信息" style={{ borderRadius: 10 }}>
            <Descriptions column={1} size="small" bordered>
              {(disk ?? []).map((item) => (
                <Descriptions.Item
                  key={item.dirName}
                  label={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span>{item.dirName}</span>
                      <Progress
                        percent={Math.round(item.usage ?? 0)}
                        size="small"
                        style={{ width: 120 }}
                        status={getProgressStatus(item.usage ?? 0)}
                      />
                    </div>
                  }
                >
                  <span>
                    盘符类型：{item.typeName ?? '-'} | 文件系统：{item.sysTypeName ?? '-'} |
                    总大小：{item.total ?? '-'} | 已用：{item.used ?? '-'} | 可用：{item.free ?? '-'} |
                    使用率：{(item.usage ?? 0).toFixed(2)}%
                  </span>
                </Descriptions.Item>
              ))}
            </Descriptions>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
