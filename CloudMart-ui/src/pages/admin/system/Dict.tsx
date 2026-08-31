import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormDigit,
  ProFormSwitch,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Card, Row, Col, Switch, Tag } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import {
  getDictTypes,
  createDictType,
  updateDictType,
  deleteDictType,
  updateDictTypeStatus,
  refreshDictCache,
  getDictData,
  createDictData,
  updateDictData,
  deleteDictData,
  updateDictDataStatus,
} from '@/api/admin/system'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface DictTypeRecord {
  id: number
  dictName: string
  dictType: string
  status: number
  remark: string
  createdAt: string
}

interface DictDataRecord {
  id: number
  dictType: string
  dictLabel: string
  dictValue: string
  dictSort: number
  cssClass: string
  listClass: string
  isDefault: number
  status: number
  remark: string
  createdAt: string
}

export default function Dict() {
  const message = useMessage()
  const typeActionRef = useRef<ActionType>(null)
  const dataActionRef = useRef<ActionType>(null)
  const { confirmSubmit: confirmSubmit1, createHandleOpenChange: createHandleOpenChange1 } = useModalConfirm()
  const { confirmSubmit: confirmSubmit2, createHandleOpenChange: createHandleOpenChange2 } = useModalConfirm()
  const [typeModalVisible, setTypeModalVisible] = useState(false)
  const [editingType, setEditingType] = useState<DictTypeRecord | null>(null)
  const [dataModalVisible, setDataModalVisible] = useState(false)
  const [editingData, setEditingData] = useState<DictDataRecord | null>(null)
  const [selectedDictType, setSelectedDictType] = useState<string>('')
  const [selectedDictName, setSelectedDictName] = useState<string>('')

  const handleRefreshCache = async () => {
    await refreshDictCache()
    message.success('缓存刷新成功')
  }

  const handleTypeSubmit = async (values: Record<string, any>) => {
    const payload = { ...values, status: values.status ? 1 : 0 }
    return confirmSubmit1(async () => {
      if (editingType) {
        await updateDictType(editingType.id, payload)
        message.success('更新成功')
      } else {
        await createDictType(payload)
        message.success('创建成功')
      }
      setEditingType(null)
      typeActionRef.current?.reload()
    })
  }

  const handleDeleteType = async (id: number) => {
    await deleteDictType(id)
    message.success('删除成功')
    if (selectedDictType) {
      setSelectedDictType('')
      setSelectedDictName('')
      dataActionRef.current?.reload()
    }
    typeActionRef.current?.reload()
  }

  const handleTypeStatusChange = async (id: number, newStatus: number) => {
    try {
      await updateDictTypeStatus(id, { status: newStatus })
      message.success('状态更新成功')
      typeActionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const handleSelectType = (record: DictTypeRecord) => {
    setSelectedDictType(record.dictType)
    setSelectedDictName(record.dictName)
    dataActionRef.current?.reload()
  }

  const handleDataSubmit = async (values: Record<string, any>) => {
    const payload = {
      ...values,
      dictType: selectedDictType,
      status: values.status ? 1 : 0,
      isDefault: values.isDefault ? 1 : 0,
    }
    return confirmSubmit2(async () => {
      if (editingData) {
        await updateDictData(editingData.id, payload)
        message.success('更新成功')
      } else {
        await createDictData(payload)
        message.success('创建成功')
      }
      setEditingData(null)
      dataActionRef.current?.reload()
    })
  }

  const handleDeleteData = async (id: number) => {
    await deleteDictData(id)
    message.success('删除成功')
    dataActionRef.current?.reload()
  }

  const handleDataStatusChange = async (id: number, newStatus: number) => {
    try {
      await updateDictDataStatus(id, { status: newStatus })
      message.success('状态更新成功')
      dataActionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const typeColumns: ProColumns<DictTypeRecord>[] = [
    { title: '字典ID', dataIndex: 'id', width: 70, search: false },
    { title: '字典名称', dataIndex: 'dictName', width: 140 },
    { title: '字典类型', dataIndex: 'dictType', width: 140 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (_, record) => (
        <Popconfirm
          title={`确定${Number(record.status) === 1 ? '停用' : '启用'}吗？`}
          onConfirm={() => handleTypeStatusChange(record.id, Number(record.status) === 1 ? 0 : 1)}
        >
          <Switch checked={Number(record.status) === 1} size="small" />
        </Popconfirm>
      ),
    },
    { title: '备注', dataIndex: 'remark', width: 160, search: false, ellipsis: true },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="select"
          type="link"
          size="small"
          onClick={() => handleSelectType(record)}
        >
          字典数据
        </Button>,
        <Button
          key="edit"
          type="link"
          size="small"
          onClick={() => {
            setEditingType(record)
            setTypeModalVisible(true)
          }}
        >
          编辑
        </Button>,
        <Popconfirm
          key="delete"
          title="确认删除该字典类型？"
          onConfirm={() => handleDeleteType(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  const dataColumns: ProColumns<DictDataRecord>[] = [
    { title: '字典编码', dataIndex: 'id', width: 80, search: false },
    { title: '字典标签', dataIndex: 'dictLabel', width: 120 },
    { title: '字典键值', dataIndex: 'dictValue', width: 120, search: false },
    { title: '排序', dataIndex: 'dictSort', width: 80, search: false },
    {
      title: '样式属性',
      dataIndex: 'cssClass',
      width: 100,
      search: false,
      render: (_, record) => {
        if (!record.listClass) return '-'
        const colorMap: Record<string, string> = {
          default: 'default',
          primary: 'blue',
          success: 'green',
          warning: 'orange',
          danger: 'red',
        }
        return <Tag color={colorMap[record.listClass] ?? 'default'}>{record.dictLabel}</Tag>
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (_, record) => (
        <Popconfirm
          title={`确定${Number(record.status) === 1 ? '停用' : '启用'}吗？`}
          onConfirm={() => handleDataStatusChange(record.id, Number(record.status) === 1 ? 0 : 1)}
        >
          <Switch checked={Number(record.status) === 1} size="small" />
        </Popconfirm>
      ),
    },
    { title: '备注', dataIndex: 'remark', width: 160, search: false, ellipsis: true },
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
            setEditingData(record)
            setDataModalVisible(true)
          }}
        >
          编辑
        </Button>,
        <Popconfirm
          key="delete"
          title="确认删除该字典数据？"
          onConfirm={() => handleDeleteData(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <div>
      <Row gutter={16}>
        <Col span={10}>
          <Card
            title="字典类型"
            extra={
              <Button
                icon={<ReloadOutlined />}
                size="small"
                onClick={handleRefreshCache}
              >
                刷新缓存
              </Button>
            }
          >
            <ProTable<DictTypeRecord>
              actionRef={typeActionRef}
              rowKey="id"
              scroll={{ x: 800 }}
              search={{ span: 12 }}
              request={async (params) => {
                return safeProTableRequest<DictTypeRecord>(() =>
                  getDictTypes({
                    page: params.current,
                    pageSize: params.pageSize,
                    dictName: params.dictName,
                    dictType: params.dictType,
                    status: params.status,
                  })
                )
              }}
              toolBarRender={() => [
                <Button
                  key="add"
                  type="primary"
                  icon={<PlusOutlined />}
                  size="small"
                  onClick={() => {
                    setEditingType(null)
                    setTypeModalVisible(true)
                  }}
                >
                  新增
                </Button>,
              ]}
              columns={typeColumns}
              pagination={{ defaultPageSize: 10 }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={14}>
          <Card title={selectedDictName ? `字典数据 - ${selectedDictName}` : '字典数据（请先选择字典类型）'}>
            <ProTable<DictDataRecord>
              actionRef={dataActionRef}
              rowKey="id"
              scroll={{ x: 900 }}
              search={false}
              request={async (params) => {
                if (!selectedDictType) {
                  return { data: [], success: true, total: 0 }
                }
                return safeProTableRequest<DictDataRecord>(() =>
                  getDictData(selectedDictType, {
                    page: params.current,
                    pageSize: params.pageSize,
                  })
                )
              }}
              toolBarRender={() => [
                <Button
                  key="add"
                  type="primary"
                  icon={<PlusOutlined />}
                  size="small"
                  disabled={!selectedDictType}
                  onClick={() => {
                    setEditingData(null)
                    setDataModalVisible(true)
                  }}
                >
                  新增
                </Button>,
              ]}
              columns={dataColumns}
              pagination={{ defaultPageSize: 10 }}
              size="small"
            />
          </Card>
        </Col>
      </Row>

      <ModalForm
        title={editingType ? '编辑字典类型' : '新增字典类型'}
        open={typeModalVisible}
        onOpenChange={createHandleOpenChange1(setTypeModalVisible, () => setEditingType(null))}
        onFinish={handleTypeSubmit}
        initialValues={
          editingType
            ? { ...editingType, status: editingType.status === 1 }
            : { status: true }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={480}
      >
        <ProFormText
          name="dictName"
          label="字典名称"
          placeholder="请输入字典名称"
          rules={[{ required: true, message: '请输入字典名称' }]}
        />
        <ProFormText
          name="dictType"
          label="字典类型"
          placeholder="请输入字典类型"
          rules={[{ required: true, message: '请输入字典类型' }]}
          disabled={!!editingType}
        />
        <ProFormSwitch name="status" label="状态" />
        <ProFormText
          name="remark"
          label="备注"
          placeholder="请输入备注"
        />
      </ModalForm>

      <ModalForm
        title={editingData ? '编辑字典数据' : '新增字典数据'}
        open={dataModalVisible}
        onOpenChange={createHandleOpenChange2(setDataModalVisible, () => setEditingData(null))}
        onFinish={handleDataSubmit}
        initialValues={
          editingData
            ? {
                ...editingData,
                status: editingData.status === 1,
                isDefault: editingData.isDefault === 1,
              }
            : { status: true, isDefault: false, dictSort: 0 }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={480}
      >
        <ProFormText
          name="dictLabel"
          label="字典标签"
          placeholder="请输入字典标签"
          rules={[{ required: true, message: '请输入字典标签' }]}
        />
        <ProFormText
          name="dictValue"
          label="字典键值"
          placeholder="请输入字典键值"
          rules={[{ required: true, message: '请输入字典键值' }]}
        />
        <ProFormDigit
          name="dictSort"
          label="排序"
          min={0}
          fieldProps={{ precision: 0 }}
        />
        <ProFormSelect
          name="listClass"
          label="样式属性"
          options={[
            { label: '默认', value: 'default' },
            { label: '主要', value: 'primary' },
            { label: '成功', value: 'success' },
            { label: '警告', value: 'warning' },
            { label: '危险', value: 'danger' },
          ]}
          placeholder="请选择样式"
        />
        <ProFormSwitch name="isDefault" label="是否默认" />
        <ProFormSwitch name="status" label="状态" />
        <ProFormText
          name="remark"
          label="备注"
          placeholder="请输入备注"
        />
      </ModalForm>
    </div>
  )
}
