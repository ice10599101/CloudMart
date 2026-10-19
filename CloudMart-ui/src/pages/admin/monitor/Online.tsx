import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tabs } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { getOnlineUsers, forceLogout } from '@/api/admin/monitor'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

interface OnlineUserRecord {
  tokenId: string
  username: string
  nickname?: string
  deptName: string | null
  ipaddr: string
  loginLocation: string
  browser: string
  os: string
  loginTime: string
  userType: 'USER' | 'ADMIN'
}

export default function Online() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [userType, setUserType] = useState<'USER' | 'ADMIN'>('USER')

  const handleForceLogout = async (tokenId: string) => {
    await forceLogout(tokenId)
    message.success('已强制下线')
    actionRef.current?.reload()
  }

  const columns: ProColumns<OnlineUserRecord>[] = [
    { title: '会话ID', dataIndex: 'tokenId', width: 200, search: false, ellipsis: true },
    { title: '用户名', dataIndex: 'username', width: 120 },
    { title: '昵称', dataIndex: 'nickname', width: 120, search: false },
    { title: '部门', dataIndex: 'deptName', width: 120, search: false },
    { title: '登录IP', dataIndex: 'ipaddr', width: 140 },
    { title: '登录地点', dataIndex: 'loginLocation', width: 140, search: false },
    { title: '浏览器', dataIndex: 'browser', width: 140, search: false },
    { title: '操作系统', dataIndex: 'os', width: 140, search: false },
    { title: '登录时间', dataIndex: 'loginTime', width: 180, valueType: 'dateTime' },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      fixed: 'right',
      render: (_, record) => [
        <Popconfirm
          key="logout"
          title="确认强制下线该用户？"
          onConfirm={() => handleForceLogout(record.tokenId)}
        >
          <Button type="link" size="small" danger icon={<LogoutOutlined />}>
            强制下线
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <Tabs
        activeKey={userType}
        onChange={(key) => setUserType(key as 'USER' | 'ADMIN')}
        items={[
          { key: 'USER', label: '前台在线用户' },
          { key: 'ADMIN', label: '后台管理在线用户' },
        ]}
      />
      <ProTable<OnlineUserRecord>
        key={userType}
        headerTitle="在线用户"
        actionRef={actionRef}
        rowKey="tokenId"
        scroll={{ x: 1200 }}
        request={async (params) =>
          safeProTableRequest<OnlineUserRecord>(async () => {
            const response = await getOnlineUsers()
            const rows = response.data?.data ?? []
            const keyword = String(params.username ?? '').trim().toLowerCase()
            const ip = String(params.ipaddr ?? '').trim().toLowerCase()
            const filteredRows = rows.filter((row: OnlineUserRecord) =>
              row.userType === userType
              && (!keyword || row.username?.toLowerCase().includes(keyword))
              && (!ip || row.ipaddr?.toLowerCase().includes(ip)),
            )
            return {
              data: {
                ...response.data,
                data: filteredRows,
                meta: { ...response.data.meta, total: filteredRows.length },
              },
            }
          })
        }
        columns={userType === 'ADMIN'
          ? columns
          : columns.filter((column) => column.dataIndex !== 'deptName')}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />
    </>
  )
}
