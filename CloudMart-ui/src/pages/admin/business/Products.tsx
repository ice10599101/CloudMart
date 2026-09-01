import { useRef, useState, useEffect } from 'react'
import {
  ProTable,
  ModalForm,
  ProFormText,
  ProFormTextArea,
  ProFormDigit,
  ProFormSwitch,
  ProFormTreeSelect,
  ProFormItem,
} from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Switch, Image, Popconfirm } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import {
  getProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  getCategories,
} from '@/api/admin/business'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'
import { convertToTreeSelect } from '@/utils/tree'
import TiptapEditor from '@/components/TiptapEditor'

interface ProductRecord {
  id: number
  name: string
  description: string
  categoryId: number
  categoryName: string
  brand: string
  price: number
  originalPrice: number
  stock: number
  sales: number
  status: number
  mainImage: string
  images: string[]
  createdAt: string
  updatedAt: string
}

interface CategoryTreeNode {
  id: number
  name: string
  parentId: number
  children?: CategoryTreeNode[]
}

export default function Products() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<ProductRecord | null>(null)
  const [categoryTree, setCategoryTree] = useState<CategoryTreeNode[]>([])
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  async function fetchCategoryTree() {
    const { data: res } = await getCategories()
    const response = res as ApiResponse<CategoryTreeNode[]>
    setCategoryTree(response.data ?? [])
  }

  useEffect(() => {
    fetchCategoryTree()
  }, [])

  const handleStatusChange = async (record: ProductRecord, newStatus: number) => {
    try {
      await updateProduct(record.id, { status: newStatus })
      message.success(newStatus === 1 ? '上架成功' : '下架成功')
      actionRef.current?.reload()
    } catch {
      message.error('状态更新失败')
    }
  }

  const handleSubmit = async (values: Record<string, any>) => {
    return confirmSubmit(async () => {
      const payload = {
        ...values,
        status: values.status ? 1 : 0,
        images: values.images
          ? typeof values.images === 'string'
            ? values.images.split(',').map((s: string) => s.trim()).filter(Boolean)
            : values.images
          : [],
      }
      if (editingRecord) {
        await updateProduct(editingRecord.id, payload)
        message.success('更新成功')
      } else {
        await createProduct(payload)
        message.success('创建成功')
      }
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const columns: ProColumns<ProductRecord>[] = [
    { title: '商品ID', dataIndex: 'id', width: 80, search: false },
    {
      title: '主图',
      dataIndex: 'mainImage',
      width: 80,
      search: false,
      render: (_, record) =>
        record.mainImage ? (
          <Image src={record.mainImage} width={50} height={50} style={{ objectFit: 'cover' }} />
        ) : (
          '-'
        ),
    },
    { title: '商品名称', dataIndex: 'name', width: 180, ellipsis: true },
    {
      title: '分类',
      dataIndex: 'categoryName',
      width: 120,
      search: false,
    },
    {
      title: '分类',
      dataIndex: 'categoryId',
      hideInTable: true,
      valueType: 'treeSelect',
      fieldProps: {
        treeData: convertToTreeSelect(categoryTree, 'name'),
        placeholder: '请选择分类',
        treeDefaultExpandAll: true,
        allowClear: true,
      },
    },
    { title: '品牌', dataIndex: 'brand', width: 100, search: false },
    {
      title: '价格',
      dataIndex: 'price',
      width: 100,
      search: false,
      render: (_, record) => `¥${Number(record.price).toFixed(2)}`,
    },
    {
      title: '库存',
      dataIndex: 'stock',
      width: 80,
      search: false,
      render: (_, record) => (
        <span style={{ color: record.stock <= 10 ? '#FF4757' : undefined }}>
          {record.stock}
        </span>
      ),
    },
    { title: '销量', dataIndex: 'sales', width: 80, search: false },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => (
        <Popconfirm
          title={Number(record.status) === 1 ? '确认下架该商品？' : '确认上架该商品？'}
          onConfirm={() => handleStatusChange(record, Number(record.status) === 1 ? 0 : 1)}
        >
          <Switch
            checked={Number(record.status) === 1}
            checkedChildren="上架"
            unCheckedChildren="下架"
          />
        </Popconfirm>
      ),
    },
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
          title="确定删除吗？"
          onConfirm={async () => {
            await deleteProduct(record.id!)
            message.success('删除成功')
            actionRef.current?.reload()
          }}
        >
          <Button type="link" size="small" danger>删除</Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<ProductRecord>
        headerTitle="商品管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1300 }}
        request={async (params) => {
          return safeProTableRequest<ProductRecord>(() =>
            getProducts({
              page: params.current,
              pageSize: params.pageSize,
              name: params.name,
              categoryId: params.categoryId,
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
            新增商品
          </Button>,
        ]}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <ModalForm
        title={editingRecord ? '编辑商品' : '新增商品'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => setEditingRecord(null))}
        onFinish={handleSubmit}
        initialValues={
          editingRecord
            ? {
                ...editingRecord,
                status: editingRecord.status === 1,
                images: editingRecord.images?.join(', ') ?? '',
              }
            : { status: true, price: 0, originalPrice: 0, stock: 0 }
        }
        modalProps={{
          destroyOnHidden: true,
          mask: { closable: false },
          keyboard: false,
        }}
        width={640}
      >
        <ProFormText
          name="name"
          label="商品名称"
          placeholder="请输入商品名称"
          rules={[{ required: true, message: '请输入商品名称' }]}
        />
        <ProFormItem name="description" label="商品描述">
          <TiptapEditor placeholder="请输入商品描述" />
        </ProFormItem>
        <ProFormTreeSelect
          name="categoryId"
          label="商品分类"
          rules={[{ required: true, message: '请选择商品分类' }]}
          fieldProps={{
            treeData: convertToTreeSelect(categoryTree, 'name'),
            placeholder: '请选择分类',
            treeDefaultExpandAll: true,
            allowClear: true,
          }}
        />
        <ProFormText
          name="brand"
          label="品牌"
          placeholder="请输入品牌"
        />
        <ProFormDigit
          name="price"
          label="售价"
          min={0}
          fieldProps={{ precision: 2, prefix: '¥' }}
          rules={[{ required: true, message: '请输入售价' }]}
        />
        <ProFormDigit
          name="originalPrice"
          label="原价"
          min={0}
          fieldProps={{ precision: 2, prefix: '¥' }}
        />
        <ProFormDigit
          name="stock"
          label="库存"
          min={0}
          fieldProps={{ precision: 0 }}
          rules={[{ required: true, message: '请输入库存' }]}
        />
        <ProFormText
          name="mainImage"
          label="主图URL"
          placeholder="请输入主图URL"
        />
        <ProFormTextArea
          name="images"
          label="图片URL（逗号分隔）"
          placeholder="多张图片URL用英文逗号分隔"
          fieldProps={{ rows: 2 }}
        />
        <ProFormSwitch name="status" label="上架状态" />
      </ModalForm>
    </>
  )
}
