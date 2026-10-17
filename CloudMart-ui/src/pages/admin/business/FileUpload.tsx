import { useCallback, useEffect, useState } from 'react'
import { Upload, Button, Table, Popconfirm, Space, Card, Image, Select, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { UploadOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons'
import { deleteAdminFileAsset, pageAdminFileAssets, type AdminFileAssetRow } from '@/api/admin/file'
import { uploadAdminFile } from '@/api/file'
import { useMessage } from '@/utils/useMessage'

/**
 * ADMIN-04：文件资产管理。
 *
 * <p>列表是 mall-file 台账中的真实管理员域资产（owner_type=ADMIN），不再是页面内存；
 * 上传统一按管理员令牌域（adminAuth）直传 /file/assets 并声明业务用途，服务端记录
 * 操作者、用途与可见性；删除走 mall-admin 代理，受引用保护（被引用 409）。</p>
 */

const PURPOSE_OPTIONS = [
  { value: 'ADMIN_ASSET', label: '运营素材' },
  { value: 'GIFT_IMAGE', label: '礼物图片' },
  { value: 'WISH_BGM', label: '心愿 BGM' },
  { value: 'SOFTWARE_ASSET', label: '软件素材' },
]

const STATUS_META: Record<string, { text: string; color: string }> = {
  READY: { text: '可用', color: 'green' },
  DELETING: { text: '删除中', color: 'orange' },
  DELETED: { text: '已删除', color: 'default' },
}

function formatFileSize(bytes: number | null | undefined): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / Math.pow(1024, index)).toFixed(1)} ${units[index]}`
}

export default function FileUpload() {
  const message = useMessage()
  const [rows, setRows] = useState<AdminFileAssetRow[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [loading, setLoading] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [purpose, setPurpose] = useState('ADMIN_ASSET')
  const [previewUrl, setPreviewUrl] = useState('')
  const [previewOpen, setPreviewOpen] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data: res } = await pageAdminFileAssets({ page, size: pageSize })
      if (res.success) {
        setRows(res.data?.items ?? [])
        setTotal(res.data?.total ?? 0)
      } else {
        message.error(res.error?.message ?? '资产列表加载失败')
      }
    } catch {
      // 拦截器已统一提示
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, message])

  useEffect(() => {
    void load()
  }, [load])

  const handleUpload = async (file: File) => {
    setUploading(true)
    try {
      const { data: res } = await uploadAdminFile(file, purpose)
      if (!res.success) {
        message.error(res.error?.message ?? '上传失败')
        return
      }
      message.success('上传成功')
      await load()
    } catch (error) {
      message.error((error as Error).message || '上传失败')
    } finally {
      setUploading(false)
    }
  }

  const handleDelete = async (row: AdminFileAssetRow) => {
    try {
      const { data: res } = await deleteAdminFileAsset(row.fileId)
      if (!res.success) {
        message.error(res.error?.message ?? '删除失败')
        return
      }
      message.success('已删除')
      await load()
    } catch (error) {
      // 被引用时服务端 409，message 为明确原因
      message.error((error as Error).message || '删除失败（文件可能仍被业务引用）')
    }
  }

  const columns: ColumnsType<AdminFileAssetRow> = [
    { title: '文件 ID', dataIndex: 'fileId', width: 190, ellipsis: true },
    {
      title: '文件名',
      dataIndex: 'originalName',
      width: 240,
      ellipsis: true,
      render: (name: string, row) =>
        row.url ? (
          <Button
            type="link"
            size="small"
            style={{ padding: 0 }}
            onClick={() => {
              setPreviewUrl(row.url as string)
              setPreviewOpen(true)
            }}
          >
            {name}
          </Button>
        ) : (
          name
        ),
    },
    {
      title: '预览',
      dataIndex: 'url',
      width: 80,
      render: (url: string | null) =>
        url ? <Image src={url} width={40} height={40} style={{ objectFit: 'cover' }} /> : '-',
    },
    {
      title: '用途',
      dataIndex: 'purpose',
      width: 130,
      render: (value: string | null) => (value ? <Tag color="blue">{value}</Tag> : '-'),
    },
    { title: '类型', dataIndex: 'mime', width: 150, ellipsis: true },
    { title: '大小', dataIndex: 'sizeBytes', width: 100, render: (value: number) => formatFileSize(value) },
    {
      title: '可见性',
      dataIndex: 'visibility',
      width: 90,
      render: (value: string) => (value === 'PRIVATE' ? <Tag>私有</Tag> : <Tag color="green">公开</Tag>),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' }
        return <Tag color={meta.color}>{meta.text}</Tag>
      },
    },
    {
      title: '引用数',
      dataIndex: 'refCount',
      width: 90,
      render: (value: number) => (value > 0 ? <Tag color="orange">{value}</Tag> : '0'),
    },
    {
      title: '上传人',
      dataIndex: 'createdBy',
      width: 110,
      render: (value: number | string | null) => value ?? '-',
    },
    { title: '上传时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_, row) => (
        <Popconfirm
          title="确认删除该文件？被业务引用的文件无法删除"
          onConfirm={() => handleDelete(row)}
        >
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            disabled={row.status !== 'READY'}
          >
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <>
      <Card title="文件上传（管理员域）">
        <Space wrap>
          <Select
            value={purpose}
            onChange={setPurpose}
            style={{ width: 180 }}
            options={PURPOSE_OPTIONS}
          />
          <Upload
            beforeUpload={(file) => {
              void handleUpload(file)
              return false
            }}
            showUploadList={false}
            multiple
          >
            <Button type="primary" icon={<UploadOutlined />} loading={uploading}>
              选择文件上传
            </Button>
          </Upload>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          <Typography.Text type="secondary">
            运营资产按管理员域记录（用途/操作者/可见性），用户上传不在此列表
          </Typography.Text>
        </Space>
      </Card>

      <Card title="资产列表" style={{ marginTop: 16 }}>
        <Table
          rowKey="fileId"
          loading={loading}
          dataSource={rows}
          columns={columns}
          scroll={{ x: 1500 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (value) => `共 ${value} 条`,
            onChange: (nextPage, nextSize) => {
              setPage(nextPage)
              setPageSize(nextSize)
            },
          }}
        />
      </Card>

      {previewUrl && (
        <Image
          style={{ display: 'none' }}
          preview={{ open: previewOpen, onOpenChange: setPreviewOpen }}
          src={previewUrl}
        />
      )}
    </>
  )
}
