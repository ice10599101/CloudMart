import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormSwitch,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tag } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import {
  getConfigs,
  createConfig,
  updateConfig,
  deleteConfig,
  refreshConfigCache,
} from '@/api/admin/system'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface ConfigRecord {
  id: number
  configName: string
  configKey: string
  configValue: string
  configType: number
  remark: string
  createdAt: string
}

export default function Config() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<ConfigRecord | null>(null)

  const handleRefreshCache = async () => {
    await refreshConfigCache()
    message.success('缓存刷新成功')
  }

  const handleSubmit = async (values: Record<string, any>) => {
    const payload = { ...values, configType: values.configType ? 1 : 0 }
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateConfig(editingRecord.id, payload)
        message.success('更新成功')
      } else {
        await createConfig(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteConfig(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const columns: ProColumns<ConfigRecord>[] = [
    { title: '参数ID', dataIndex: 'id', width: 80, search: false },
    { title: '参数名称', dataIndex: 'configName', width: 160 },
    { title: '参数键名', dataIndex: 'configKey', width: 180 },
    { title: '参数键值', dataIndex: 'configValue', width: 160, search: false },
    {
      title: '系统内置',
      dataIndex: 'configType',
      width: 100,
      render: (_, record) => (
        <Tag color={record.configType === 1 ? 'orange' : 'default'}>
          {record.configType === 1 ? '是' : '否'}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', width: 200, search: false, ellipsis: true },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
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
        <Popconfirm
          key="delete"
          title="确认删除该参数？"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<ConfigRecord>
        headerTitle="参数设置"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1200 }}
        request={async (params) => {
          return safeProTableRequest<ConfigRecord>(() =>
            getConfigs({
              page: params.current,
              pageSize: params.pageSize,
              configName: params.configName,
              configKey: params.configKey,
              configType: params.configType,
            })
          )
        }}
        toolBarRender={() => [
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={handleRefreshCache}
          >
            刷新缓存
          </Button>,
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingRecord(null)
              setModalVisible(true)
            }}
          >
            新增参数
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑参数' : '新增参数'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { ...editingRecord, configType: editingRecord.configType === 1 }
            : { configType: false }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={520}
      >
        <ProFormText
          name="configName"
          label="参数名称"
          placeholder="请输入参数名称"
          rules={[{ required: true, message: '请输入参数名称' }]}
        />
        <ProFormText
          name="configKey"
          label="参数键名"
          placeholder="请输入参数键名"
          rules={[{ required: true, message: '请输入参数键名' }]}
        />
        <ProFormText
          name="configValue"
          label="参数键值"
          placeholder="请输入参数键值"
          rules={[{ required: true, message: '请输入参数键值' }]}
        />
        <ProFormSwitch name="configType" label="系统内置" />
        <ProFormText
          name="remark"
          label="备注"
          placeholder="请输入备注"
        />
      </ModalForm>
    </>
  )
}
