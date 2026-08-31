import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormTextArea,
  ProFormSelect,
  ProFormDigit,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getRiskRules,
  createRiskRule,
  updateRiskRule,
  deleteRiskRule,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface RiskRuleRecord {
  id: number
  ruleName: string
  ruleType: string
  threshold: number
  description: string
  status: number
  createdAt: string
  updatedAt: string
}

const RULE_TYPE_MAP: Record<string, { text: string; color: string }> = {
  FREQUENCY: { text: '频率限制', color: 'blue' },
  AMOUNT: { text: '金额限制', color: 'orange' },
  BEHAVIOR: { text: '行为检测', color: 'purple' },
  IP: { text: 'IP 限制', color: 'cyan' },
  DEVICE: { text: '设备限制', color: 'geekblue' },
}

export default function RiskRules() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<RiskRuleRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      const payload = { ...values }
      if (editingRecord) {
        await updateRiskRule(editingRecord.id, payload)
        message.success('更新成功')
      } else {
        await createRiskRule(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteRiskRule(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const columns: ProColumns<RiskRuleRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '规则名称', dataIndex: 'ruleName', width: 160 },
    {
      title: '规则类型',
      dataIndex: 'ruleType',
      width: 120,
      render: (_, record) => {
        const typeInfo = RULE_TYPE_MAP[record.ruleType]
        return <Tag color={typeInfo?.color ?? 'default'}>{typeInfo?.text ?? record.ruleType}</Tag>
      },
    },
    {
      title: '阈值',
      dataIndex: 'threshold',
      width: 100,
      search: false,
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 220,
      search: false,
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Tag color={record.status === 1 ? 'success' : 'default'}>
          {record.status === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
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
          key="toggle"
          title={record.status === 1 ? '确认禁用该规则？' : '确认启用该规则？'}
          onConfirm={async () => {
            await updateRiskRule(record.id, { status: record.status === 1 ? 0 : 1 })
            message.success(record.status === 1 ? '已禁用' : '已启用')
            actionRef.current?.reload()
          }}
        >
          <Button type="link" size="small" danger={record.status === 1}>
            {record.status === 1 ? '禁用' : '启用'}
          </Button>
        </Popconfirm>,
        <Popconfirm
          key="delete"
          title="确认删除该规则？删除后不可恢复"
          onConfirm={() => handleDelete(record.id)}
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
      <ProTable<RiskRuleRecord>
        headerTitle="风险规则管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1200 }}
        request={async (params) => {
          return safeProTableRequest<RiskRuleRecord>(() =>
            getRiskRules({
              page: params.current,
              pageSize: params.pageSize,
              ruleName: params.ruleName,
              ruleType: params.ruleType,
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
            新增规则
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑规则' : '新增规则'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { ...editingRecord }
            : { status: 1, threshold: 10 }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={560}
      >
        <ProFormText
          name="ruleName"
          label="规则名称"
          placeholder="请输入规则名称"
          rules={[{ required: true, message: '请输入规则名称' }]}
        />
        <ProFormSelect
          name="ruleType"
          label="规则类型"
          placeholder="请选择规则类型"
          rules={[{ required: true, message: '请选择规则类型' }]}
          options={Object.entries(RULE_TYPE_MAP).map(([key, val]) => ({
            label: val.text,
            value: key,
          }))}
        />
        <ProFormDigit
          name="threshold"
          label="阈值"
          min={0}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入阈值' }]}
        />
        <ProFormTextArea
          name="description"
          label="规则描述"
          placeholder="请输入规则描述"
          fieldProps={{ rows: 3, maxLength: 500, showCount: true }}
        />
        <ProFormSelect
          name="status"
          label="状态"
          options={[
            { label: '启用', value: 1 },
            { label: '禁用', value: 0 },
          ]}
        />
      </ModalForm>
    </>
  )
}
