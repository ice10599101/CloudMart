import { useRef, useState, useEffect } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormDigit,
  ProFormTreeSelect,
  ProFormSwitch,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Switch, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getDeptTree,
  createDept,
  updateDept,
  deleteDept,
  updateDeptStatus,
} from '@/api/admin/system'
import type { ApiResponse } from '@/types/api'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'
import { convertToTreeSelect } from '@/utils/tree'

interface DeptRecord {
  id: number
  parentId: number
  deptName: string
  leader: string
  phone: string
  email: string
  orderNum: number
  status: number
  children?: DeptRecord[]
}

export default function Depts() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<DeptRecord | null>(null)
  const [deptTree, setDeptTree] = useState<DeptRecord[]>([])

  useEffect(() => {
    fetchDeptTree()
  }, [])

  async function fetchDeptTree() {
    const { data: res } = await getDeptTree()
    const response = res as ApiResponse<DeptRecord[]>
    setDeptTree(response.data ?? [])
  }

  const handleSubmit = async (values: Record<string, any>) => {
    const payload = { ...values, status: values.status ? 1 : 0 }
    return confirmSubmit(async () => {
      if (editingRecord) {
        await updateDept(editingRecord.id, payload)
        message.success('更新成功')
      } else {
        await createDept(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      fetchDeptTree()
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteDept(id)
    message.success('删除成功')
    fetchDeptTree()
    actionRef.current?.reload()
  }

  const handleStatusChange = async (id: number, newStatus: number) => {
    try {
      await updateDeptStatus(id, { status: newStatus })
      message.success('状态更新成功')
      fetchDeptTree()
      actionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const columns: ProColumns<DeptRecord>[] = [
    { title: '部门名称', dataIndex: 'deptName', width: 200 },
    { title: '排序', dataIndex: 'orderNum', width: 80, search: false },
    { title: '负责人', dataIndex: 'leader', width: 120, search: false },
    { title: '联系电话', dataIndex: 'phone', width: 140, search: false },
    { title: '邮箱', dataIndex: 'email', width: 180, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      search: false,
      render: (_, record) => (
        <Popconfirm
      title={`确定${Number(record.status) === 1 ? '停用' : '启用'}吗？`}
      onConfirm={() => handleStatusChange(record.id, Number(record.status) === 1 ? 0 : 1)}
    >
      <Switch checked={Number(record.status) === 1} size="small" />
    </Popconfirm>
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="add"
          type="link"
          size="small"
          onClick={() => {
            setEditingRecord(null)
            setModalVisible(true)
          }}
        >
          新增
        </Button>,
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
          title="确认删除该部门？"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  const treeSelectData = [
    { title: '顶级部门', value: 0, key: 0, children: convertToTreeSelect(deptTree, 'deptName') },
  ]

  return (
    <>
      <ProTable<DeptRecord>
        headerTitle="部门管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1000 }}
        request={async () => {
          return {
            data: deptTree,
            success: true,
          }
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
            新增部门
          </Button>,
        ]}
        columns={columns}
        pagination={false}
        search={false}
        expandable={{ defaultExpandAllRows: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑部门' : '新增部门'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? { ...editingRecord, status: editingRecord.status === 1 }
            : { status: true, orderNum: 0, parentId: 0 }
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={520}
      >
        <ProFormTreeSelect
          name="parentId"
          label="上级部门"
          fieldProps={{
            treeData: treeSelectData,
            placeholder: '请选择上级部门',
            treeDefaultExpandAll: true,
            allowClear: true,
          }}
        />
        <ProFormText
          name="deptName"
          label="部门名称"
          placeholder="请输入部门名称"
          rules={[{ required: true, message: '请输入部门名称' }]}
        />
        <ProFormDigit
          name="orderNum"
          label="排序"
          min={0}
          fieldProps={{ precision: 0 }}
        />
        <ProFormText
          name="leader"
          label="负责人"
          placeholder="请输入负责人"
        />
        <ProFormText
          name="phone"
          label="联系电话"
          placeholder="请输入联系电话"
        />
        <ProFormText
          name="email"
          label="邮箱"
          placeholder="请输入邮箱"
          rules={[{ type: 'email', message: '请输入正确的邮箱' }]}
        />
        <ProFormSwitch name="status" label="状态" />
      </ModalForm>
    </>
  )
}
