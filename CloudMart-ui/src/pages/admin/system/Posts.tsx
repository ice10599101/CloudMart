import { useRef, useState } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormDigit,
  ProFormSwitch,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getPosts,
  createPost,
  updatePost,
  deletePost,
  updatePostStatus,
} from '@/api/admin/system'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

interface PostRecord {
  id: number
  postCode: string
  postName: string
  orderNum: number
  status: number
  remark: string
  createdAt: string
}

export default function Posts() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<PostRecord | null>(null)

  const handleSubmit = async (values: Record<string, any>) => {
    const payload = { ...values, status: values.status ? 1 : 0 }
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updatePost(editingRecord.id, payload)
        message.success('更新成功')
      } else {
        await createPost(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deletePost(id)
    message.success('删除成功')
    actionRef.current?.reload()
  }

  const handleStatusChange = async (id: number, newStatus: number) => {
    try {
      await updatePostStatus(id, { status: newStatus })
      message.success('状态更新成功')
      actionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const columns: ProColumns<PostRecord>[] = [
    { title: '岗位ID', dataIndex: 'id', width: 80, search: false },
    { title: '岗位编码', dataIndex: 'postCode', width: 140 },
    { title: '岗位名称', dataIndex: 'postName', width: 140 },
    { title: '排序', dataIndex: 'orderNum', width: 80, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Popconfirm
      title={`确定${Number(record.status) === 1 ? '停用' : '启用'}吗？`}
      onConfirm={() => handleStatusChange(record.id, Number(record.status) === 1 ? 0 : 1)}
    >
      <Switch checked={Number(record.status) === 1} size="small" />
    </Popconfirm>
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
          title="确认删除该岗位？"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<PostRecord>
        headerTitle="岗位管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1000 }}
        request={async (params) => {
          return safeProTableRequest<PostRecord>(() =>
            getPosts({
              page: params.current,
              pageSize: params.pageSize,
              postCode: params.postCode,
              postName: params.postName,
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
            新增岗位
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑岗位' : '新增岗位'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { ...editingRecord, status: editingRecord.status === 1 }
            : { status: true, orderNum: 0 }
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={520}
      >
        <ProFormText
          name="postCode"
          label="岗位编码"
          placeholder="请输入岗位编码"
          rules={[{ required: true, message: '请输入岗位编码' }]}
        />
        <ProFormText
          name="postName"
          label="岗位名称"
          placeholder="请输入岗位名称"
          rules={[{ required: true, message: '请输入岗位名称' }]}
        />
        <ProFormDigit
          name="orderNum"
          label="排序"
          min={0}
          fieldProps={{ precision: 0 }}
        />
        <ProFormSwitch name="status" label="状态" />
        <ProFormText
          name="remark"
          label="备注"
          placeholder="请输入备注"
        />
      </ModalForm>
    </>
  )
}
