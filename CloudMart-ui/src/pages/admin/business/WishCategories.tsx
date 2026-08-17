import { useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Image, Popconfirm, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getAdminWishCategories,
  createAdminWishCategory,
  updateAdminWishCategory,
  deleteAdminWishCategory,
} from '@/api/admin/wish'
import type { AdminWishCategoryRecord } from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

/**
 * 心愿分类管理（管理后台）。
 *
 * 系统预设分类（seed 数据）不可删除，仅可编辑名称/排序/图标。
 */
export default function WishCategories() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<AdminWishCategoryRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  const handleSubmit = async (values: { code?: string; name: string; sort?: number; icon?: string }) => {
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateAdminWishCategory(editingRecord.id, {
          name: values.name,
          sort: values.sort,
          icon: values.icon,
        })
        message.success('更新成功')
      } else {
        await createAdminWishCategory({
          code: values.code!,
          name: values.name,
          sort: values.sort,
          icon: values.icon,
        })
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteAdminWishCategory(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const columns: ProColumns<AdminWishCategoryRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '分类编码', dataIndex: 'code', width: 140, search: false },
    { title: '分类名称', dataIndex: 'name', width: 160, search: false },
    {
      title: '图标',
      dataIndex: 'icon',
      width: 90,
      search: false,
      render: (_, record) =>
        record.icon ? (
          record.icon.startsWith('http') ? (
            <Image src={record.icon} width={32} height={32} style={{ objectFit: 'cover' }} />
          ) : (
            <span style={{ fontSize: 24 }}>{record.icon}</span>
          )
        ) : (
          '-'
        ),
    },
    { title: '排序', dataIndex: 'sortOrder', width: 90, search: false },
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      search: false,
      render: (_, record) =>
        record.id < 2000 ? <Tag color="blue">系统预设</Tag> : <Tag color="green">自定义</Tag>,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
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
        ...(record.id >= 2000
          ? [
              <Popconfirm
                key="delete"
                title="确认删除该分类？删除后不可恢复"
                onConfirm={() => handleDelete(record.id)}
              >
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>,
            ]
          : []),
      ],
    },
  ]

  return (
    <>
      <ProTable<AdminWishCategoryRecord>
        headerTitle="心愿分类管理"
        actionRef={actionRef}
        rowKey="id"
        search={false}
        scroll={{ x: 900 }}
        request={async () => {
          return safeProTableRequest<AdminWishCategoryRecord>(() => getAdminWishCategories())
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
            新增分类
          </Button>,
        ]}
        columns={columns}
        pagination={false}
      />

      <ModalForm
        title={editingRecord ? '编辑分类' : '新增分类'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { name: editingRecord.name, sort: editingRecord.sortOrder, icon: editingRecord.icon ?? undefined }
            : { sort: 0 }
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={480}
      >
        {!editingRecord && (
          <ProFormText
            name="code"
            label="分类编码"
            placeholder="唯一编码，如 career、health"
            rules={[
              { required: true, message: '请输入分类编码' },
              { max: 30, message: '编码不能超过30字符' },
              { pattern: /^[a-z][a-z0-9_]*$/, message: '仅支持小写字母、数字、下划线' },
            ]}
          />
        )}
        <ProFormText
          name="name"
          label="分类名称"
          placeholder="请输入分类名称"
          rules={[
            { required: true, message: '请输入分类名称' },
            { max: 60, message: '名称不能超过60字符' },
          ]}
        />
        <ProFormDigit
          name="sort"
          label="排序值"
          placeholder="升序排列，越小越靠前"
          min={0}
          max={9999}
          fieldProps={{ precision: 0 }}
        />
        <ProFormText name="icon" label="图标" placeholder="图标URL或Emoji" />
      </ModalForm>
    </>
  )
}
