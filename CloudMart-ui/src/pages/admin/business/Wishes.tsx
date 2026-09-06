import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Descriptions, Image, Modal, Popconfirm, Tag } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import DOMPurify from 'dompurify'
import {
  getAdminWishes,
  getAdminWishDetail,
  getAdminWishCategories,
  updateWishVisibility,
  toggleWishTop,
  deleteAdminWish,
  WISH_STATUS_MAP,
  VISIBILITY_MAP,
  FRUIT_TYPE_MAP,
} from '@/api/admin/wish'
import type { AdminWishRecord } from '@/api/admin/wish'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

/** 与 WishDetail 保持一致：历史数据为纯文本，新版编辑器产出富文本 HTML，按是否含 HTML 标签分流渲染 */
const RICH_TEXT_PATTERN = /<\/?(p|div|br|h[1-6]|ul|ol|li|blockquote|pre|img|table|strong|em|u|s|span|a|code)\b/i

/**
 * 心愿管理（管理后台）：对齐帖子管理模式。
 *
 * 发布免审（新心愿默认 APPROVED 直接上架显示），管理端操作为
 * 查看详情 / 隐藏(下架) / 置顶 / 删除(软删)。
 */
export default function Wishes() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [detail, setDetail] = useState<AdminWishRecord | null>(null)

  const loadDetail = async (id: number) => {
    try {
      const res = await getAdminWishDetail(id)
      setDetail(res.data.data)
    } catch {
      message.error('加载详情失败')
    }
  }

  const handleToggleVisibility = async (record: AdminWishRecord) => {
    try {
      await updateWishVisibility(record.id, { visible: !record.isVisible })
      message.success(record.isVisible ? '下架成功' : '上架成功')
      actionRef.current?.reload()
    } catch {
      message.error(record.isVisible ? '下架失败' : '上架失败')
    }
  }

  const handleToggleTop = async (record: AdminWishRecord) => {
    try {
      await toggleWishTop(record.id, { isTop: !record.isTop })
      message.success(record.isTop ? '取消置顶成功' : '置顶成功')
      actionRef.current?.reload()
    } catch {
      message.error('置顶操作失败')
    }
  }

  const handleDelete = async (record: AdminWishRecord) => {
    try {
      await deleteAdminWish(record.id)
      message.success('删除成功')
      actionRef.current?.reload()
    } catch {
      message.error('删除失败')
    }
  }

  const columns: ProColumns<AdminWishRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 90, search: false },
    { title: '标题', dataIndex: 'title', width: 200, ellipsis: true },
    { title: '作者ID', dataIndex: 'userId', width: 90, search: false },
    {
      title: '分类',
      dataIndex: 'categoryId',
      width: 120,
      valueType: 'select',
      fieldProps: { showSearch: true },
      request: async () => {
        try {
          const res = await getAdminWishCategories()
          return (res.data.data ?? []).map((c) => ({ label: c.name, value: c.id }))
        } catch {
          return []
        }
      },
      render: (_, record) => record.categoryName ?? '-',
    },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 90,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(VISIBILITY_MAP).map(([value, info]) => [value, { text: info.label }]),
      ),
      render: (_, record) => {
        const info = VISIBILITY_MAP[record.visibility]
        return info ? <Tag color={info.color}>{info.label}</Tag> : record.visibility
      },
    },
    {
      title: '心愿状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(WISH_STATUS_MAP).map(([value, info]) => [value, { text: info.label }]),
      ),
      render: (_, record) => {
        const info = WISH_STATUS_MAP[record.status]
        return info ? <Tag color={info.color}>{info.label}</Tag> : record.status
      },
    },
    {
      title: '状态',
      dataIndex: 'isVisible',
      width: 90,
      search: false,
      render: (_, record) => {
        if (record.deletedAt) return <Tag color="error">已删除</Tag>
        return record.isVisible ? <Tag color="success">上架</Tag> : <Tag color="warning">已下架</Tag>
      },
    },
    {
      title: '置顶',
      dataIndex: 'isTop',
      width: 70,
      search: false,
      render: (_, record) => (record.isTop ? <Tag color="blue">置顶</Tag> : <Tag>否</Tag>),
    },
    { title: '互动数', dataIndex: 'supportCount', width: 85, search: false, sorter: true },
    { title: '点亮数', dataIndex: 'lightCount', width: 85, search: false },
    { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 160, search: false },
    {
      title: '标题搜索',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '请输入心愿标题或描述' },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 260,
      fixed: 'right',
      render: (_, record) => {
        const actions: React.ReactNode[] = [
          <Button key="detail" type="link" size="small" onClick={() => loadDetail(record.id)}>
            查看详情
          </Button>,
        ]
        if (!record.deletedAt) {
          actions.push(
            <Popconfirm
              key="visibility"
              title={record.isVisible ? '确认下架该心愿？用户端将不可见' : '确认上架该心愿？'}
              onConfirm={() => handleToggleVisibility(record)}
            >
              <Button type="link" size="small">
                {record.isVisible ? '隐藏' : '上架'}
              </Button>
            </Popconfirm>,
            <Popconfirm
              key="top"
              title={record.isTop ? '确认取消置顶？' : '确认置顶该心愿？置顶后将优先展示在广场'}
              onConfirm={() => handleToggleTop(record)}
            >
              <Button type="link" size="small">
                {record.isTop ? '取消置顶' : '顶置'}
              </Button>
            </Popconfirm>,
            <Popconfirm
              key="delete"
              title="确定删除该心愿吗？"
              onConfirm={() => handleDelete(record)}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>,
          )
        }
        return actions
      },
    },
  ]

  return (
    <>
      <ProTable<AdminWishRecord>
        headerTitle="心愿管理"
        actionRef={actionRef}
        rowKey="id"
        scroll={{ x: 1400 }}
        toolBarRender={() => [
          <Button
            key="export"
            icon={<DownloadOutlined />}
            onClick={async () => {
              try {
                const res = await getAdminWishes({ page: 1, pageSize: 100 })
                const data = res.data.data ?? []
                if (data.length === 0) {
                  message.warning('暂无数据可导出')
                  return
                }
                const bom = '\uFEFF'
                const headers = ['ID', '标题', '作者ID', '分类', '状态', '置顶', '互动数', '创建时间']
                const rows = data.map((r) => [
                  String(r.id),
                  r.title ?? '',
                  String(r.userId),
                  r.categoryName ?? '-',
                  r.deletedAt ? '已删除' : r.isVisible ? '上架' : '已下架',
                  r.isTop ? '是' : '否',
                  String(r.supportCount ?? 0),
                  r.createdAt ?? '',
                ])
                const csv = [headers.join(','), ...rows.map((row) => row.map((c) => `"${c.replace(/"/g, '""')}"`).join(','))].join('\n')
                const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8;' })
                const link = document.createElement('a')
                link.href = URL.createObjectURL(blob)
                link.download = '心愿数据.csv'
                link.click()
                URL.revokeObjectURL(link.href)
                message.success('导出成功')
              } catch {
                message.error('导出失败')
              }
            }}
          >
            导出CSV
          </Button>,
        ]}
        request={async (params) => {
          return safeProTableRequest<AdminWishRecord>(() =>
            getAdminWishes({
              page: params.current,
              pageSize: params.pageSize,
              keyword: params.keyword || undefined,
              categoryId: params.categoryId,
              status: params.status,
              visibility: params.visibility,
            }),
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
      />

      <Modal
        title={`心愿详情 #${detail?.id ?? ''}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={680}
      >
        {detail && (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="标题" span={2}>
              {detail.title}
            </Descriptions.Item>
            <Descriptions.Item label="作者ID">{detail.userId}</Descriptions.Item>
            <Descriptions.Item label="分类">{detail.categoryName}</Descriptions.Item>
            <Descriptions.Item label="果实类型">
              {FRUIT_TYPE_MAP[detail.fruitType]?.emoji} {FRUIT_TYPE_MAP[detail.fruitType]?.label ?? detail.fruitType}
            </Descriptions.Item>
            <Descriptions.Item label="可见性">
              {VISIBILITY_MAP[detail.visibility]?.label ?? detail.visibility}
            </Descriptions.Item>
            <Descriptions.Item label="心愿状态">
              {WISH_STATUS_MAP[detail.status]?.label ?? detail.status}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              {detail.deletedAt ? '已删除' : detail.isVisible ? '上架' : '已下架'}
            </Descriptions.Item>
            <Descriptions.Item label="置顶">{detail.isTop ? '是' : '否'}</Descriptions.Item>
            <Descriptions.Item label="点亮/同愿/祝福">
              {detail.lightCount} / {detail.sameWishCount} / {detail.blessCount}
            </Descriptions.Item>
            <Descriptions.Item label="总互动数">{detail.supportCount}</Descriptions.Item>
            <Descriptions.Item label="预计完成" span={2}>
              {detail.expectedAt ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="描述" span={2}>
              {detail.description
                ? (RICH_TEXT_PATTERN.test(detail.description)
                  ? (
                      <div
                        style={{ maxHeight: 280, overflowY: 'auto', wordBreak: 'break-word', whiteSpace: 'normal' }}
                        dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(detail.description) }}
                      />
                    )
                  : detail.description)
                : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="媒体" span={2}>
              {detail.mediaUrls?.length ? (
                <Image.PreviewGroup>
                  {detail.mediaUrls.map((url) => (
                    <Image key={url} src={url} width={80} height={80} style={{ objectFit: 'cover', marginRight: 8 }} />
                  ))}
                </Image.PreviewGroup>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="标签" span={2}>
              {detail.tags?.length ? detail.tags.join('、') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">{detail.createdAt}</Descriptions.Item>
            <Descriptions.Item label="软删时间">{detail.deletedAt ?? '未删除'}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </>
  )
}
