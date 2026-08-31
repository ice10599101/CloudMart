import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormTextArea,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm, Input, Space, Card, Result, Select } from 'antd'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { getBlacklist, addToBlacklist, removeFromBlacklist, checkBlacklist } from '@/api/admin/business'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface BlacklistRecord {
  id: number
  type: string
  value: string
  reason: string
  createdBy: string
  createdAt: string
}

const BLACKLIST_TYPE_MAP: Record<string, { text: string; color: string }> = {
  USER: { text: '用户', color: 'blue' },
  IP: { text: 'IP', color: 'orange' },
  DEVICE: { text: '设备', color: 'purple' },
}

export default function Blacklist() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [addModalVisible, setAddModalVisible] = useState(false)
  const [checkValue, setCheckValue] = useState('')
  const [checkType, setCheckType] = useState<string>('USER')
  const [checkResult, setCheckResult] = useState<{ isBlacklisted: boolean; reason?: string } | null>(null)
  const [checkLoading, setCheckLoading] = useState(false)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleAdd = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      await addToBlacklist(values)
      message.success('已添加到黑名单')
      actionRef.current?.reload()
    })
  }

  const handleRemove = async (type: string, value: string) => {
    await removeFromBlacklist(type, value)
    message.success('已从黑名单移除')
    actionRef.current?.reload()
  }

  const handleCheck = async () => {
    if (!checkValue.trim()) {
      message.warning('请输入要检查的值')
      return
    }
    setCheckLoading(true)
    try {
      const { data: res } = await checkBlacklist({ type: checkType, value: checkValue.trim() })
      const response = res as ApiResponse<{ isBlacklisted: boolean; reason?: string }>
      setCheckResult(response.data ?? null)
    } finally {
      setCheckLoading(false)
    }
  }

  const columns: ProColumns<BlacklistRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      render: (_, record) => {
        const typeInfo = BLACKLIST_TYPE_MAP[record.type]
        return <Tag color={typeInfo?.color ?? 'default'}>{typeInfo?.text ?? record.type}</Tag>
      },
    },
    { title: '值', dataIndex: 'value', width: 200 },
    { title: '原因', dataIndex: 'reason', width: 240, search: false, ellipsis: true },
    { title: '操作人', dataIndex: 'createdBy', width: 120, search: false },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      fixed: 'right',
      render: (_, record) => [
        <Popconfirm
          key="remove"
          title="确认从黑名单移除？"
          onConfirm={() => handleRemove(record.type, record.value)}
        >
          <Button type="link" size="small" danger>移除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<BlacklistRecord>
        headerTitle="黑名单管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1100 }}
        request={async (params) => {
          return safeProTableRequest<BlacklistRecord>(() =>
            getBlacklist({
              page: params.current,
              pageSize: params.pageSize,
              type: params.type,
              value: params.value,
            })
          )
        }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setAddModalVisible(true)}
          >
            添加黑名单
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title="添加黑名单"
        open={addModalVisible}
        onOpenChange={createHandleOpenChange(setAddModalVisible)}
        onFinish={handleAdd}
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={520}
      >
        <ProFormSelect
          name="type"
          label="类型"
          placeholder="请选择黑名单类型"
          rules={[{ required: true, message: '请选择类型' }]}
          options={Object.entries(BLACKLIST_TYPE_MAP).map(([key, val]) => ({
            label: val.text,
            value: key,
          }))}
        />
        <ProFormText
          name="value"
          label="值"
          placeholder="请输入用户ID / IP地址 / 设备标识"
          rules={[{ required: true, message: '请输入值' }]}
        />
        <ProFormTextArea
          name="reason"
          label="原因"
          placeholder="请输入加入黑名单的原因"
          rules={[{ required: true, message: '请输入原因' }]}
          fieldProps={{ rows: 3, maxLength: 500, showCount: true }}
        />
      </ModalForm>

      <Card title="黑名单检查" style={{ marginTop: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Space wrap>
            <Select
              value={checkType}
              onChange={(val: string) => {
                setCheckType(val)
                setCheckResult(null)
              }}
              style={{ width: 120 }}
              options={Object.entries(BLACKLIST_TYPE_MAP).map(([key, val]) => ({
                label: val.text,
                value: key,
              }))}
            />
            <Input
              placeholder="输入要检查的值"
              value={checkValue}
              onChange={(e) => {
                setCheckValue(e.target.value)
                setCheckResult(null)
              }}
              style={{ width: 280 }}
              onPressEnter={handleCheck}
            />
            <Button
              type="primary"
              icon={<SearchOutlined />}
              loading={checkLoading}
              onClick={handleCheck}
            >
              检查
            </Button>
          </Space>
          {checkResult && (
            <Result
              status={checkResult.isBlacklisted ? 'error' : 'success'}
              title={checkResult.isBlacklisted ? '该对象在黑名单中' : '该对象不在黑名单中'}
              subTitle={checkResult.isBlacklisted && checkResult.reason ? `原因：${checkResult.reason}` : undefined}
            />
          )}
        </Space>
      </Card>
    </>
  )
}
