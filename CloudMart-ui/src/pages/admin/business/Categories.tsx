import { useRef, useState, useEffect } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormDigit,
  ProFormSwitch,
  ProFormTreeSelect,
  ProFormSelect,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tag } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  createCategory,
  updateCategory,
  deleteCategory,
  getCategories,
} from '@/api/admin/business'
import type { ApiResponse } from '@/types/api'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'
import { convertToTreeSelect } from '@/utils/tree'
import type { TreeSelectNode } from '@/utils/tree'

interface CategoryRecord {
  id: number
  parentId: number
  name: string
  icon: string
  sortOrder: number
  status: number
  level: number
  children?: CategoryRecord[]
  createdAt: string
  updatedAt: string
}

const CATEGORY_ICON_OPTIONS = [
  { label: 'AppstoreOutlined', value: 'AppstoreOutlined' },
  { label: 'ShoppingOutlined', value: 'ShoppingOutlined' },
  { label: 'LaptopOutlined', value: 'LaptopOutlined' },
  { label: 'MobileOutlined', value: 'MobileOutlined' },
  { label: 'SkinOutlined', value: 'SkinOutlined' },
  { label: 'HomeOutlined', value: 'HomeOutlined' },
  { label: 'CarOutlined', value: 'CarOutlined' },
  { label: 'GiftOutlined', value: 'GiftOutlined' },
  { label: 'BookOutlined', value: 'BookOutlined' },
  { label: 'ToolOutlined', value: 'ToolOutlined' },
  { label: 'HeartOutlined', value: 'HeartOutlined' },
  { label: 'StarOutlined', value: 'StarOutlined' },
  { label: 'SafetyOutlined', value: 'SafetyOutlined' },
  { label: 'TrophyOutlined', value: 'TrophyOutlined' },
  { label: 'ThunderboltOutlined', value: 'ThunderboltOutlined' },
  { label: 'FireOutlined', value: 'FireOutlined' },
]

export default function Categories() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<CategoryRecord | null>(null)
  const [categoryTree, setCategoryTree] = useState<CategoryRecord[]>([])
  const [treeSelectData, setTreeSelectData] = useState<TreeSelectNode[]>([])
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  useEffect(() => {
    fetchCategoryTree()
  }, [])

  async function fetchCategoryTree() {
    const { data: res } = await getCategories()
    const response = res as ApiResponse<CategoryRecord[]>
    const tree = response.data ?? []
    setCategoryTree(tree)
    setTreeSelectData([
      { title: '顶级分类', value: 0, key: 0, children: convertToTreeSelect(tree, 'name') },
    ])
  }

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      const payload = {
        ...values,
        status: values.status ? 1 : 0,
      }
      if (editingRecord) {
        await updateCategory(editingRecord.id, payload)
        message.success('更新成功')
      } else {
        await createCategory(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      fetchCategoryTree()
      actionRef.current?.reload()
    })
  }

  const handleDelete = async (id: number) => {
    await deleteCategory(id)
    message.success('删除成功')
    fetchCategoryTree()
    actionRef.current?.reload()
  }

  const columns: ProColumns<CategoryRecord>[] = [
    { title: '分类名称', dataIndex: 'name', width: 200 },
    {
      title: '图标',
      dataIndex: 'icon',
      width: 100,
      search: false,
      render: (_, record) => record.icon || '-',
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      width: 80,
      search: false,
    },
    {
      title: '层级',
      dataIndex: 'level',
      width: 80,
      search: false,
      render: (_, record) => `第${record.level}级`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Tag color={record.status === 1 ? 'success' : 'default'}>
          {record.status === 1 ? '正常' : '禁用'}
        </Tag>
      ),
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime', search: false },
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
          新增子分类
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
          title="确认删除该分类？删除后不可恢复"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<CategoryRecord>
        headerTitle="分类管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 900 }}
        request={async () => {
          return {
            data: categoryTree,
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
            新增分类
          </Button>,
        ]}
        columns={columns}
        pagination={false}
        search={false}
        expandable={{ defaultExpandAllRows: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑分类' : '新增分类'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? {
                ...editingRecord,
                status: editingRecord.status === 1,
              }
            : { status: true, sortOrder: 0, parentId: 0 }
        }
        modalProps={{ destroyOnHidden: true, maskClosable: false, keyboard: false }}
        width={520}
      >
        <ProFormTreeSelect
          name="parentId"
          label="上级分类"
          fieldProps={{
            treeData: treeSelectData,
            placeholder: '请选择上级分类',
            treeDefaultExpandAll: true,
            allowClear: true,
          }}
        />
        <ProFormText
          name="name"
          label="分类名称"
          placeholder="请输入分类名称"
          rules={[{ required: true, message: '请输入分类名称' }]}
        />
        <ProFormSelect
          name="icon"
          label="图标"
          options={CATEGORY_ICON_OPTIONS}
          showSearch
          fieldProps={{ allowClear: true, placeholder: '请选择图标' }}
        />
        <ProFormDigit
          name="sortOrder"
          label="排序"
          min={0}
          fieldProps={{ precision: 0 }}
        />
        <ProFormSwitch name="status" label="状态" />
      </ModalForm>
    </>
  )
}
