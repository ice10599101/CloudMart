import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tabs, Tag } from 'antd'
import {
  PlusOutlined,
  CaretRightOutlined,
  PauseOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import {
  getJobs,
  createJob,
  updateJob,
  deleteJob,
  changeJobStatus,
  runJob,
  getJobLogs,
  deleteJobLog,
  cleanJobLogs,
} from '@/api/admin/monitor'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface JobRecord {
  id: number
  jobName: string
  jobGroup: string
  invokeTarget: string
  cronExpression: string
  status: number
  misfirePolicy: number
  concurrent: number
  remark: string
  createdAt: string
  updatedAt: string
}

interface JobLogRecord {
  id: number
  jobId: number
  jobName: string
  jobGroup: string
  invokeTarget: string
  cronExpression: string
  status: number
  exceptionInfo: string
  startTime: string
  endTime: string
  duration: string
}

const STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '暂停', color: 'default' },
  1: { label: '正常', color: 'success' },
}

const LOG_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '失败', color: 'error' },
  1: { label: '成功', color: 'success' },
}

export default function Job() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const logActionRef = useRef<ActionType>(null)
  const [activeTab, setActiveTab] = useState('list')
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<JobRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSubmit = async (values: Record<string, string | number>) => {
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateJob(editingRecord.id, values)
        message.success('更新成功')
      } else {
        await createJob(values)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteJob(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const handleToggleStatus = async (record: JobRecord) => {
    const newStatus = record.status === 1 ? 0 : 1
    await changeJobStatus(record.id, { status: newStatus })
    message.success(newStatus === 1 ? '已恢复' : '已暂停')
    actionRef.current?.reload()
  }

  const handleRunOnce = async (id: number) => {
    await runJob(id)
    message.success('已触发执行')
    actionRef.current?.reload()
  }

  const handleDeleteLog = async (id: number) => {
    await deleteJobLog(id)
    message.success('删除成功')
    logActionRef.current?.reload()
  }

  const handleCleanLogs = async () => {
    await cleanJobLogs()
    message.success('清空成功')
    logActionRef.current?.reload()
  }

  const jobColumns: ProColumns<JobRecord>[] = [
    { title: '任务ID', dataIndex: 'id', width: 80, search: false },
    { title: '任务名称', dataIndex: 'jobName', width: 140 },
    { title: '任务分组', dataIndex: 'jobGroup', width: 120, search: false },
    { title: '执行类', dataIndex: 'invokeTarget', width: 200, search: false, ellipsis: true },
    { title: 'Cron表达式', dataIndex: 'cronExpression', width: 140, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Tag color={STATUS_MAP[record.status]?.color ?? 'default'}>
          {STATUS_MAP[record.status]?.label ?? '未知'}
        </Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 280,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="edit"
          type="link"
          size="small"
          onClick={() => {
            setEditingRecord(record)
            setModalVisible(true)
          }}
        >
          编辑
        </Button>,
        <Button
          key="run"
          type="link"
          size="small"
          icon={<CaretRightOutlined />}
          onClick={() => handleRunOnce(record.id)}
        >
          执行
        </Button>,
        <Button
          key="toggle"
          type="link"
          size="small"
          icon={record.status === 1 ? <PauseOutlined /> : <CaretRightOutlined />}
          onClick={() => handleToggleStatus(record)}
        >
          {record.status === 1 ? '暂停' : '恢复'}
        </Button>,
        <Popconfirm
          key="delete"
          title="确认删除该任务？"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  const logColumns: ProColumns<JobLogRecord>[] = [
    { title: '日志ID', dataIndex: 'id', width: 80, search: false },
    { title: '任务ID', dataIndex: 'jobId', width: 80 },
    { title: '任务名称', dataIndex: 'jobName', width: 140 },
    { title: '执行类', dataIndex: 'invokeTarget', width: 200, search: false, ellipsis: true },
    { title: 'Cron表达式', dataIndex: 'cronExpression', width: 140, search: false },
    {
      title: '执行状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Tag color={LOG_STATUS_MAP[record.status]?.color ?? 'default'}>
          {LOG_STATUS_MAP[record.status]?.label ?? '未知'}
        </Tag>
      ),
    },
    { title: '耗时', dataIndex: 'duration', width: 100, search: false },
    { title: '开始时间', dataIndex: 'startTime', width: 180, valueType: 'dateTime' },
    {
      title: '异常信息',
      dataIndex: 'exceptionInfo',
      width: 200,
      search: false,
      ellipsis: true,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      fixed: 'right',
      render: (_, record) => [
        <Popconfirm
          key="delete"
          title="确认删除该日志？"
          onConfirm={() => handleDeleteLog(record.id)}
        >
          <Button type="link" size="small" danger>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'list',
            label: '任务列表',
            children: (
              <ProTable<JobRecord>
                headerTitle="定时任务"
                actionRef={actionRef}
                rowKey="id"
                scroll={{ x: 1200 }}
                request={async (params) => {
                  return safeProTableRequest<JobRecord>(() =>
                    getJobs({
                      page: params.current,
                      pageSize: params.pageSize,
                      jobName: params.jobName,
                      status: params.status,
                    })
                  )
                }}
                toolBarRender={() => [
                  <Button
                    key="add"
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => {
                      setEditingRecord(null)
                      setModalVisible(true)
                    }}
                  >
                    新增任务
                  </Button>,
                ]}
                columns={jobColumns}
                pagination={{ defaultPageSize: 10, showSizeChanger: true }}
              />
            ),
          },
          {
            key: 'log',
            label: '执行日志',
            children: (
              <ProTable<JobLogRecord>
                headerTitle="执行日志"
                actionRef={logActionRef}
                rowKey="id"
                scroll={{ x: 1400 }}
                request={async (params) => {
                  return safeProTableRequest<JobLogRecord>(() =>
                    getJobLogs({
                      page: params.current,
                      pageSize: params.pageSize,
                      jobId: params.jobId,
                      jobName: params.jobName,
                      status: params.status,
                    })
                  )
                }}
                toolBarRender={() => [
                  <Popconfirm
                    key="clean"
                    title="确认清空所有执行日志？此操作不可恢复！"
                    onConfirm={handleCleanLogs}
                  >
                    <Button icon={<DeleteOutlined />} danger>
                      清空日志
                    </Button>
                  </Popconfirm>,
                ]}
                columns={logColumns}
                pagination={{ defaultPageSize: 10, showSizeChanger: true }}
              />
            ),
          },
        ]}
      />

      <ModalForm
        title={editingRecord ? '编辑任务' : '新增任务'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord ?? { status: 1, misfirePolicy: 1, concurrent: 1 }
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={560}
      >
        <ProFormText
          name="jobName"
          label="任务名称"
          placeholder="请输入任务名称"
          rules={[{ required: true, message: '请输入任务名称' }]}
        />
        <ProFormSelect
          name="jobGroup"
          label="任务分组"
          placeholder="请选择任务分组"
          rules={[{ required: true, message: '请选择任务分组' }]}
          options={[
            { label: '默认', value: 'DEFAULT' },
            { label: '系统', value: 'SYSTEM' },
          ]}
        />
        <ProFormText
          name="invokeTarget"
          label="执行类"
          placeholder="请输入执行类（如 com.example.task.MyTask）"
          rules={[{ required: true, message: '请输入执行类' }]}
        />
        <ProFormText
          name="cronExpression"
          label="Cron表达式"
          placeholder="请输入Cron表达式（如 0 0/5 * * * ?）"
          rules={[{ required: true, message: '请输入Cron表达式' }]}
        />
        <ProFormSelect
          name="misfirePolicy"
          label="执行策略"
          options={[
            { label: '立即执行', value: 1 },
            { label: '执行一次', value: 2 },
            { label: '放弃执行', value: 3 },
          ]}
        />
        <ProFormSelect
          name="concurrent"
          label="是否并发"
          options={[
            { label: '允许', value: 1 },
            { label: '禁止', value: 0 },
          ]}
        />
        <ProFormSelect
          name="status"
          label="状态"
          options={[
            { label: '正常', value: 1 },
            { label: '暂停', value: 0 },
          ]}
        />
      </ModalForm>
    </>
  )
}
