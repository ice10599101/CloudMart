import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tabs, Tag } from 'antd'
import { CaretRightOutlined } from '@ant-design/icons'
import { getJobs, runJob, getJobLogs } from '@/api/admin/monitor'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/**
 * OPS-01：任务管理（XXL-JOB 只读适配层）。
 * 调度唯一事实来源是 XXL-JOB——任务生命周期（新建/编辑/启停/Cron）走
 * XXL 调度中心控制台；本页展示实际配置与执行日志，支持白名单手动运行。
 */
interface XxlJobRow {
  id: number
  jobDesc: string
  executorHandler: string
  scheduleConf: string
  triggerStatus: number
  author: string
}

interface XxlJobLogRow {
  logId: number
  executorHandler: string
  triggerTime: string
  handleCode: number
  handleMsg: string
}

export default function Job() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [activeTab, setActiveTab] = useState('jobs')
  const [logJobId, setLogJobId] = useState<number | null>(null)
  const [logJobDesc, setLogJobDesc] = useState('')
  const [runningId, setRunningId] = useState<number | null>(null)

  const jobColumns: ProColumns<XxlJobRow>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '任务描述', dataIndex: 'jobDesc', ellipsis: true },
    { title: 'Handler', dataIndex: 'executorHandler', width: 240, ellipsis: true },
    { title: 'Cron', dataIndex: 'scheduleConf', width: 140 },
    {
      title: '状态',
      dataIndex: 'triggerStatus',
      width: 90,
      render: (_, r) =>
        r.triggerStatus === 1 ? <Tag color="green">运行中</Tag> : <Tag>已暂停</Tag>,
    },
    { title: '负责人', dataIndex: 'author', width: 90 },
    {
      title: '操作',
      valueType: 'option',
      width: 150,
      fixed: 'right',
      render: (_, r) => [
        <Button
          key="run"
          type="link"
          size="small"
          icon={<CaretRightOutlined />}
          loading={runningId === r.id}
          disabled={r.triggerStatus !== 1}
          onClick={() => void handleRun(r)}
        >
          手动运行
        </Button>,
        <Button
          key="log"
          type="link"
          size="small"
          onClick={() => {
            setLogJobId(r.id)
            setLogJobDesc(r.jobDesc)
            setActiveTab('logs')
          }}
        >
          执行日志
        </Button>,
      ],
    },
  ]

  const logColumns: ProColumns<XxlJobLogRow>[] = [
    { title: '日志 ID', dataIndex: 'logId', width: 90 },
    { title: 'Handler', dataIndex: 'executorHandler', width: 200, ellipsis: true },
    {
      title: '触发时间',
      dataIndex: 'triggerTime',
      width: 170,
      render: (_, r) => (r.triggerTime ?? '').slice(0, 19).replace('T', ' '),
    },
    {
      title: '执行结果',
      dataIndex: 'handleCode',
      width: 110,
      render: (_, r) =>
        r.handleCode === 200 ? (
          <Tag color="green">成功</Tag>
        ) : r.handleCode ? (
          <Tag color="red">失败</Tag>
        ) : (
          <Tag>未执行</Tag>
        ),
    },
    {
      title: '消息',
      dataIndex: 'handleMsg',
      ellipsis: true,
    },
  ]

  const handleRun = async (row: XxlJobRow) => {
    setRunningId(row.id)
    try {
      await runJob(row.id)
      message.success(`已触发「${row.jobDesc}」，执行结果见执行日志（handle_code）`)
      actionRef.current?.reload()
    } catch {
      message.error('触发失败')
    } finally {
      setRunningId(null)
    }
  }

  return (
    <Tabs
      activeKey={activeTab}
      onChange={setActiveTab}
      items={[
        {
          key: 'jobs',
          label: '任务列表',
          children: (
            <ProTable<XxlJobRow>
              headerTitle="XXL-JOB 任务（配置与启停请在 XXL 调度中心操作）"
              actionRef={actionRef}
              rowKey="id"
              search={false}
              request={async (params) =>
                safeProTableRequest<XxlJobRow>(() =>
                  getJobs({ page: params.current, pageSize: params.pageSize }),
                )
              }
              columns={jobColumns}
              pagination={{ defaultPageSize: 20 }}
              options={{ reload: true }}
            />
          ),
        },
        {
          key: 'logs',
          label: '执行日志',
          children:
            logJobId == null ? (
              <div
                style={{
                  padding: 32,
                  textAlign: 'center',
                  color: 'var(--color-text-tertiary)',
                }}
              >
                在任务列表点「执行日志」查看对应任务的 XXL 执行事实
              </div>
            ) : (
              <ProTable<XxlJobLogRow>
                headerTitle={`执行日志：${logJobDesc || logJobId}`}
                rowKey="logId"
                search={false}
                request={async (params) =>
                  safeProTableRequest<XxlJobLogRow>(() =>
                    getJobLogs({ jobId: logJobId, page: params.current, pageSize: params.pageSize }),
                  )
                }
                columns={logColumns}
                pagination={{ defaultPageSize: 20 }}
                toolBarRender={() => [
                  <Button key="back" onClick={() => setLogJobId(null)}>
                    返回任务列表
                  </Button>,
                ]}
              />
            ),
        },
      ]}
    />
  )
}
