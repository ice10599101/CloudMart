import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormDigit,
  ProFormSelect,
  ProFormDateTimePicker,
  ProFormDependency,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Tag, Popconfirm } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getCoupons,
  createCoupon,
  updateCoupon,
  enableCoupon,
  disableCoupon,
  deleteCoupon,
} from '@/api/admin/business'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface CouponRecord {
  id: number
  name: string
  type: string
  discountAmount: number
  discountRate: number
  thresholdAmount: number
  totalQuantity: number
  remainingQuantity: number
  perUserLimit: number
  validityType: string
  startTime: string
  endTime: string
  validDays: number
  status: string
  createdAt: string
  updatedAt: string
}

const COUPON_TYPE_MAP: Record<string, { label: string; color: string }> = {
  AMOUNT_OFF: { label: '满减', color: 'blue' },
  PERCENT_OFF: { label: '折扣', color: 'green' },
}

const COUPON_STATUS_MAP: Record<string, { label: string; color: string }> = {
  ENABLED: { label: '已启用', color: 'success' },
  DISABLED: { label: '已禁用', color: 'default' },
}

export default function Coupons() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<CouponRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleEnable = async (id: number) => {
    await enableCoupon(id)
    message.success('启用成功')
    actionRef.current?.reload()
  }

  const handleDisable = async (id: number) => {
    await disableCoupon(id)
    message.success('禁用成功')
    actionRef.current?.reload()
  }

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      const payload: Record<string, any> = {
        name: values.name,
        type: values.type,
        thresholdAmount: values.thresholdAmount,
        totalQuantity: values.totalQuantity,
        perUserLimit: values.perUserLimit,
        validityType: values.validityType,
      }
      if (values.type === 'AMOUNT_OFF') {
        payload.discountAmount = values.discountAmount
        payload.discountRate = null
      } else {
        payload.discountRate = values.discountRate
        payload.discountAmount = null
      }
      if (values.validityType === 'FIXED_DATE') {
        payload.startTime = values.startTime?.format('YYYY-MM-DD HH:mm:ss')
        payload.endTime = values.endTime?.format('YYYY-MM-DD HH:mm:ss')
        payload.validDays = null
      } else {
        payload.validDays = values.validDays
        payload.startTime = null
        payload.endTime = null
      }
      if (editingRecord) {
        await updateCoupon(editingRecord.id!, payload)
        message.success('编辑成功')
      } else {
        await createCoupon(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const renderValue = (_: unknown, record: CouponRecord) => {
    if (record.type === 'AMOUNT_OFF') {
      return `满${record.thresholdAmount}减${record.discountAmount}`
    }
    if (record.type === 'PERCENT_OFF') {
      return `${((1 - (record.discountRate ?? 1)) * 100).toFixed(0)}折`
    }
    return '-'
  }

  const columns: ProColumns<CouponRecord>[] = [
    { title: '优惠券ID', dataIndex: 'id', width: 90, search: false },
    { title: '优惠券名称', dataIndex: 'name', width: 180, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      render: (_, record) => {
        const typeInfo = COUPON_TYPE_MAP[record.type] ?? { label: '未知', color: 'default' }
        return <Tag color={typeInfo.color}>{typeInfo.label}</Tag>
      },
    },
    {
      title: '优惠内容',
      dataIndex: 'discountAmount',
      width: 150,
      search: false,
      render: renderValue,
    },
    {
      title: '发放/剩余',
      dataIndex: 'totalQuantity',
      width: 120,
      search: false,
      render: (_, record) => `${record.totalQuantity === -1 ? '不限' : record.totalQuantity} / ${record.remainingQuantity}`,
    },
    {
      title: '每人限领',
      dataIndex: 'perUserLimit',
      width: 100,
      search: false,
      render: (_, record) => `${record.perUserLimit}张`,
    },
    {
      title: '有效期',
      dataIndex: 'startTime',
      width: 180,
      search: false,
      render: (_, record) => `${record.startTime?.slice(0, 10) ?? ''} ~ ${record.endTime?.slice(0, 10) ?? ''}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const statusInfo = COUPON_STATUS_MAP[record.status] ?? { label: '未知', color: 'default' }
        return <Tag color={statusInfo.color}>{statusInfo.label}</Tag>
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      render: (_, record) => {
        const actions: React.ReactNode[] = []
        actions.push(
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
        )
        if (record.status === 'ENABLED') {
          actions.push(
            <Popconfirm
              key="disable"
              title="确认禁用该优惠券？"
              onConfirm={() => handleDisable(record.id)}
            >
              <Button type="link" size="small" danger>禁用</Button>
            </Popconfirm>,
          )
        } else if (record.status === 'DISABLED') {
          actions.push(
            <Popconfirm
              key="enable"
              title="确认启用该优惠券？"
              onConfirm={() => handleEnable(record.id)}
            >
              <Button type="link" size="small">启用</Button>
            </Popconfirm>,
          )
        }
        actions.push(
          <Popconfirm
            key="delete"
            title="确定删除吗？"
            onConfirm={async () => {
              await deleteCoupon(record.id!)
              message.success('删除成功')
              actionRef.current?.reload()
            }}
          >
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>,
        )
        return actions
      },
    },
  ]

  return (
    <>
      <ProTable<CouponRecord>
        headerTitle="优惠券管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1400 }}
        request={async (params) => {
          return safeProTableRequest<CouponRecord>(() =>
            getCoupons({
              page: params.current,
              pageSize: params.pageSize,
              name: params.name,
              type: params.type,
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
            新增优惠券
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑优惠券' : '新增优惠券'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? {
                ...editingRecord,
                startTime: undefined,
                endTime: undefined,
              }
            : { type: 'AMOUNT_OFF', perUserLimit: 1, totalQuantity: 100, thresholdAmount: 0, validityType: 'FIXED_DATE' }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={560}
      >
        <ProFormText
          name="name"
          label="优惠券名称"
          placeholder="请输入优惠券名称"
          rules={[{ required: true, message: '请输入优惠券名称' }]}
        />
        <ProFormSelect
          name="type"
          label="优惠类型"
          options={[
            { label: '满减', value: 'AMOUNT_OFF' },
            { label: '折扣', value: 'PERCENT_OFF' },
          ]}
          rules={[{ required: true, message: '请选择优惠类型' }]}
        />
        <ProFormDependency name={['type']}>
          {({ type }) => {
            if (type === 'PERCENT_OFF') {
              return (
                <ProFormDigit
                  name="discountRate"
                  label="折扣率"
                  min={0.1}
                  max={0.99}
                  fieldProps={{ precision: 2, step: 0.01 }}
                  rules={[{ required: true, message: '请输入折扣率' }]}
                  extra="0.80表示8折，0.90表示9折"
                />
              )
            }
            return (
              <ProFormDigit
                name="discountAmount"
                label="优惠金额（元）"
                min={0.01}
                fieldProps={{ precision: 2 }}
                rules={[{ required: true, message: '请输入优惠金额' }]}
              />
            )
          }}
        </ProFormDependency>
        <ProFormDigit
          name="thresholdAmount"
          label="使用门槛（元）"
          min={0}
          fieldProps={{ precision: 2 }}
          extra="满0元表示无门槛"
        />
        <ProFormDigit
          name="totalQuantity"
          label="发放总量"
          min={1}
          fieldProps={{ precision: 0 }}
        />
        <ProFormDigit
          name="perUserLimit"
          label="每人限领"
          min={1}
          fieldProps={{ precision: 0 }}
        />
        <ProFormSelect
          name="validityType"
          label="有效期类型"
          options={[
            { label: '固定时间段', value: 'FIXED_DATE' },
            { label: '领取后固定天数', value: 'FIXED_DAYS' },
          ]}
          rules={[{ required: true, message: '请选择有效期类型' }]}
        />
        <ProFormDependency name={['validityType']}>
          {({ validityType }) => {
            if (validityType === 'FIXED_DATE') {
              return (
                <>
                  <ProFormDateTimePicker
                    name="startTime"
                    label="开始时间"
                    rules={[{ required: true, message: '请选择开始时间' }]}
                  />
                  <ProFormDateTimePicker
                    name="endTime"
                    label="结束时间"
                    rules={[{ required: true, message: '请选择结束时间' }]}
                  />
                </>
              )
            }
            return (
              <ProFormDigit
                name="validDays"
                label="有效天数"
                min={1}
                fieldProps={{ precision: 0 }}
                rules={[{ required: true, message: '请输入有效天数' }]}
                extra="领取后多少天内有效"
              />
            )
          }}
        </ProFormDependency>
      </ModalForm>
    </>
  )
}
