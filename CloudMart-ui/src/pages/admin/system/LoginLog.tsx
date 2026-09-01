import { useRef } from 'react'
import {
  ProTable,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tag } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import {
  getLoginLogs,
  deleteLoginLog,
  cleanLoginLogs,
} from '@/api/admin/system'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

interface LoginLogRecord {
  id: number
  username: string
  ipaddr: string
  loginLocation: string
  browser: string
  os: string
  status: number
  msg: string
  loginTime: string
}

export default function LoginLog() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)

  const handleClean = async () => {
    await cleanLoginLogs()
    message.success('清空成功')
    actionRef.current?.reload()
  }

  const handleDelete = async (id: number) => {
    await deleteLoginLog(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const columns: ProColumns<LoginLogRecord>[] = [
    { title: '日志ID', dataIndex: 'id', width: 80, search: false },
    { title: '用户名', dataIndex: 'username', width: 120 },
    { title: '登录IP', dataIndex: 'ipaddr', width: 140, search: false },
    { title: '登录地点', dataIndex: 'loginLocation', width: 140, search: false },
    { title: '浏览器', dataIndex: 'browser', width: 120, search: false },
    { title: '操作系统', dataIndex: 'os', width: 120, search: false },
    {
      title: '登录状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Tag color={record.status === 1 ? 'success' : 'error'}>
          {record.status === 1 ? '成功' : '失败'}
        </Tag>
      ),
    },
    { title: '提示消息', dataIndex: 'msg', width: 160, search: false, ellipsis: true },
    { title: '登录时间', dataIndex: 'loginTime', width: 180, valueType: 'dateTime' },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      fixed: 'right',
      render: (_, record) => [
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
    <ProTable<LoginLogRecord>
      headerTitle="登录日志"
      actionRef={actionRef}
      rowKey="id"
      scroll={{ x: 1200 }}
      request={async (params) => {
        return safeProTableRequest<LoginLogRecord>(() =>
          getLoginLogs({
            page: params.current,
            pageSize: params.pageSize,
            username: params.username,
            status: params.status,
            beginTime: params.loginTime?.[0],
            endTime: params.loginTime?.[1],
          })
        )
      }}
      toolBarRender={() => [
        <Popconfirm
          key="clean"
          title="确认清空所有登录日志？此操作不可恢复！"
          onConfirm={handleClean}
        >
          <Button icon={<DeleteOutlined />} danger>清空</Button>
        </Popconfirm>,
      ]}
      columns={columns}
      pagination={{ defaultPageSize: 10, showSizeChanger: true }}
    />
  )
}
