import { useState } from 'react'
import { Upload, Button, Table, Popconfirm, Space, Card, Image } from 'antd'
import { UploadOutlined, DeleteOutlined, FileOutlined, EyeOutlined } from '@ant-design/icons'
import { uploadFile, deleteFile } from '@/api/admin/business'
import { useMessage } from '@/utils/useMessage'

interface FileRecord {
  uid: string
  name: string
  url: string
  size: number
  type: string
  status: 'uploading' | 'done' | 'error'
}

const FILE_TYPE_PREVIEW_MAP: Record<string, boolean> = {
  'image/jpeg': true,
  'image/png': true,
  'image/gif': true,
  'image/webp': true,
  'image/svg+xml': true,
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

export default function FileUpload() {
  const message = useMessage()
  const [fileList, setFileList] = useState<FileRecord[]>([])
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewUrl, setPreviewUrl] = useState('')

  const handleUpload = async (file: File) => {
    const uid = `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
    const tempRecord: FileRecord = {
      uid,
      name: file.name,
      url: '',
      size: file.size,
      type: file.type,
      status: 'uploading',
    }
    setFileList((prev) => [tempRecord, ...prev])

    const formData = new FormData()
    formData.append('file', file)

    try {
      const { data: res } = await uploadFile(formData)
      const response = res as { success: boolean; data: { url: string; name: string } }
      setFileList((prev) =>
        prev.map((item) =>
          item.uid === uid
            ? { ...item, url: response.data.url, name: response.data.name ?? file.name, status: 'done' }
            : item,
        ),
      )
      message.success('上传成功')
    } catch {
      setFileList((prev) =>
        prev.map((item) => (item.uid === uid ? { ...item, status: 'error' } : item)),
      )
      message.error('上传失败')
    }

    return false
  }

  const handleDelete = async (record: FileRecord) => {
    try {
      await deleteFile(record.url)
      setFileList((prev) => prev.filter((item) => item.uid !== record.uid))
      message.success('删除成功')
    } catch {
      message.error('删除失败')
    }
  }

  const handlePreview = (record: FileRecord) => {
    setPreviewUrl(record.url)
    setPreviewOpen(true)
  }

  const handleRemoveError = (record: FileRecord) => {
    setFileList((prev) => prev.filter((item) => item.uid !== record.uid))
  }

  const columns = [
    {
      title: '文件名',
      dataIndex: 'name',
      width: 260,
      ellipsis: true,
      render: (name: string, record: FileRecord) => (
        <Space>
          <FileOutlined />
          {FILE_TYPE_PREVIEW_MAP[record.type] ? (
            <Button type="link" size="small" onClick={() => handlePreview(record)}>
              {name}
            </Button>
          ) : (
            name
          )}
        </Space>
      ),
    },
    {
      title: '预览',
      dataIndex: 'url',
      width: 80,
      render: (url: string, record: FileRecord) =>
        FILE_TYPE_PREVIEW_MAP[record.type] && url ? (
          <Image src={url} width={40} height={40} style={{ objectFit: 'cover' }} />
        ) : (
          '-'
        ),
    },
    {
      title: '大小',
      dataIndex: 'size',
      width: 100,
      render: (size: number) => formatFileSize(size),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 140,
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => {
        const map: Record<string, { text: string; color: string }> = {
          uploading: { text: '上传中', color: 'processing' },
          done: { text: '已完成', color: 'success' },
          error: { text: '失败', color: 'error' },
        }
        const info = map[status]
        return info ? <span style={{ color: info.color === 'success' ? '#2ED573' : info.color === 'error' ? '#FF4757' : 'var(--color-primary)' }}>{info.text}</span> : status
      },
    },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, record: FileRecord) => (
        <Space>
          {FILE_TYPE_PREVIEW_MAP[record.type] && record.url && (
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handlePreview(record)}
            >
              预览
            </Button>
          )}
          {record.status === 'error' ? (
            <Button
              type="link"
              size="small"
              danger
              onClick={() => handleRemoveError(record)}
            >
              移除
            </Button>
          ) : (
            <Popconfirm
              title="确认删除该文件？删除后不可恢复"
              onConfirm={() => handleDelete(record)}
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <>
      <Card title="文件上传">
        <Upload
          beforeUpload={(file) => {
            handleUpload(file)
            return false
          }}
          showUploadList={false}
          multiple
        >
          <Button type="primary" icon={<UploadOutlined />}>
            选择文件上传
          </Button>
        </Upload>
      </Card>

      <Card title="文件列表" style={{ marginTop: 16 }}>
        <Table
          rowKey="uid"
          dataSource={fileList}
          columns={columns}
          pagination={{ defaultPageSize: 10, showSizeChanger: true }}
        />
      </Card>

      {previewUrl && (
        <Image
          style={{ display: 'none' }}
          preview={{
            open: previewOpen,
            onOpenChange: (open) => {
              setPreviewOpen(open)
            },
          }}
          src={previewUrl}
        />
      )}
    </>
  )
}
