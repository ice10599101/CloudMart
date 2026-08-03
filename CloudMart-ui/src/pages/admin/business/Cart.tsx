import { useState } from 'react'
import {
  ProTable,
} from '@ant-design/pro-components'
import type { ProColumns } from '@ant-design/pro-components'
import { Button, Card, Input, Popconfirm, Empty, Space, Tag } from 'antd'
import { DeleteOutlined, ClearOutlined, SearchOutlined } from '@ant-design/icons'
import {
  getCart,
  removeCartItem,
  clearCart,
} from '@/api/admin/business'
import type { ApiResponse } from '@/types/api'
import { useMessage } from '@/utils/useMessage'

interface CartItem {
  id: number
  userId: number
  skuId: number
  productId: number
  productName: string
  skuName: string
  price: number
  quantity: number
  image: string
  checked: number
  createdAt: string
  updatedAt: string
}

export default function Cart() {
  const message = useMessage()
  const [userId, setUserId] = useState('')
  const [cartItems, setCartItems] = useState<CartItem[]>([])
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)

  async function fetchCart() {
    if (!userId.trim()) {
      message.warning('请输入用户ID')
      return
    }
    setLoading(true)
    setSearched(true)
    try {
      const { data: res } = await getCart(userId.trim())
      const response = res as ApiResponse<CartItem[]>
      setCartItems(response.data ?? [])
    } catch {
      setCartItems([])
    } finally {
      setLoading(false)
    }
  }

  const handleRemoveItem = async (skuId: number) => {
    await removeCartItem(userId, skuId)
    message.success('商品已移除')
    fetchCart()
  }

  const handleClearCart = async () => {
    await clearCart(userId)
    message.success('购物车已清空')
    setCartItems([])
  }

  const columns: ProColumns<CartItem>[] = [
    {
      title: '商品图片',
      dataIndex: 'image',
      width: 80,
      render: (_, record) =>
        record.image ? (
          <img src={record.image} alt="" style={{ width: 50, height: 50, objectFit: 'cover', borderRadius: 4 }} />
        ) : (
          '-'
        ),
    },
    { title: '商品名称', dataIndex: 'productName', width: 200, ellipsis: true },
    { title: '规格', dataIndex: 'skuName', width: 120 },
    {
      title: '单价',
      dataIndex: 'price',
      width: 100,
      render: (_, record) => `¥${Number(record.price).toFixed(2)}`,
    },
    { title: '数量', dataIndex: 'quantity', width: 80 },
    {
      title: '小计',
      dataIndex: 'subtotal',
      width: 100,
      render: (_, record) => (
        <span style={{ color: '#FF4757' }}>
          ¥{(Number(record.price) * Number(record.quantity)).toFixed(2)}
        </span>
      ),
    },
    {
      title: '选中状态',
      dataIndex: 'checked',
      width: 100,
      render: (_, record) => (
        <Tag color={record.checked === 1 ? 'success' : 'default'}>
          {record.checked === 1 ? '已选中' : '未选中'}
        </Tag>
      ),
    },
    { title: '添加时间', dataIndex: 'createdAt', width: 180, valueType: 'dateTime' },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      fixed: 'right',
      render: (_, record) => [
        <Popconfirm
          key="remove"
          title="确认移除该商品？"
          onConfirm={() => handleRemoveItem(record.skuId)}
        >
          <Button type="link" size="small" danger icon={<DeleteOutlined />}>
            移除
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  const totalAmount = cartItems.reduce(
    (sum, item) => sum + Number(item.price) * Number(item.quantity),
    0,
  )

  return (
    <Card>
      <Space style={{ marginBottom: 16 }} size="middle">
        <Input
          placeholder="请输入用户ID"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          onPressEnter={fetchCart}
          style={{ width: 240 }}
          prefix={<SearchOutlined />}
        />
        <Button type="primary" onClick={fetchCart} loading={loading}>
          查询购物车
        </Button>
        {cartItems.length > 0 && (
          <Popconfirm
            title="确认清空该用户购物车？此操作不可恢复"
            onConfirm={handleClearCart}
          >
            <Button danger icon={<ClearOutlined />}>
              清空购物车
            </Button>
          </Popconfirm>
        )}
      </Space>

      {searched && cartItems.length > 0 && (
        <div style={{ marginBottom: 12, color: 'var(--color-text-tertiary)' }}>
          共 {cartItems.length} 件商品，合计：
          <span style={{ color: '#FF4757', fontWeight: 'bold', fontSize: 16 }}>
            ¥{totalAmount.toFixed(2)}
          </span>
        </div>
      )}

      {searched ? (
        <ProTable<CartItem>
          headerTitle={`用户 ${userId} 的购物车`}
          rowKey="id"
          scroll={{ x: 1000 }}
          dataSource={cartItems}
          columns={columns}
          loading={loading}
          pagination={false}
          search={false}
          toolBarRender={false}
        />
      ) : (
        <Empty description="请输入用户ID查询购物车" style={{ padding: 60 }} />
      )}
    </Card>
  )
}
