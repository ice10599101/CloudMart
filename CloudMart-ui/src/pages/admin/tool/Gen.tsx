import { useRef, useState } from 'react'
import { ProTable } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Modal, Tabs, Tag } from 'antd'
import { EyeOutlined, DownloadOutlined } from '@ant-design/icons'
import {
  getGenTables,
  previewGenCode,
  downloadGenCode,
} from '@/api/admin/tool'
import type { ApiResponse } from '@/types/api'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'

interface GenTableRecord {
  tableName: string
  tableComment: string
  engine: string
  tableCollation: string
  createTime: string
  updateTime: string
}

interface PreviewFile {
  fileName: string
  content: string
  type: string
}

const FILE_TYPE_LABELS: Record<string, string> = {
  controller: 'Controller',
  service: 'Service',
  serviceImpl: 'ServiceImpl',
  mapper: 'Mapper',
  entity: 'Entity',
  dto: 'DTO',
  vo: 'VO',
}

function getFileType(fileName: string): string {
  const lower = fileName.toLowerCase()
  if (lower.includes('controller')) return 'controller'
  if (lower.includes('serviceimpl')) return 'serviceImpl'
  if (lower.includes('service')) return 'service'
  if (lower.includes('mapper')) return 'mapper'
  if (lower.includes('entity') || lower.includes('domain')) return 'entity'
  if (lower.includes('dto')) return 'dto'
  if (lower.includes('vo')) return 'vo'
  return 'other'
}

export default function Gen() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [previewVisible, setPreviewVisible] = useState(false)
  const [previewFiles, setPreviewFiles] = useState<PreviewFile[]>([])
  const [previewLoading, setPreviewLoading] = useState(false)

  const handlePreview = async (tableName: string) => {
    setPreviewLoading(true)
    setPreviewVisible(true)
    try {
      const { data: res } = await previewGenCode({ tableName })
      const response = res as ApiResponse<PreviewFile[]>
      setPreviewFiles(response.data ?? [])
    } finally {
      setPreviewLoading(false)
    }
  }

  const handleDownload = async (tableName: string) => {
    try {
      const response = await downloadGenCode({ tableName })
      const blob = new Blob([response.data as BlobPart], { type: 'application/zip' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${tableName}.zip`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      message.success('下载成功')
    } catch {
      message.error('下载失败')
    }
  }

  const groupedFiles = previewFiles.reduce<Record<string, PreviewFile[]>>((acc, file) => {
    const type = getFileType(file.fileName)
    if (!acc[type]) acc[type] = []
    acc[type].push(file)
    return acc
  }, {})

  const columns: ProColumns<GenTableRecord>[] = [
    { title: '表名', dataIndex: 'tableName', width: 200 },
    { title: '表注释', dataIndex: 'tableComment', width: 200, search: false, ellipsis: true },
    {
      title: '引擎',
      dataIndex: 'engine',
      width: 100,
      search: false,
      render: (_, record) => <Tag>{record.engine}</Tag>,
    },
    { title: '字符集', dataIndex: 'tableCollation', width: 180, search: false },
    { title: '创建时间', dataIndex: 'createTime', width: 180, valueType: 'dateTime', search: false },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="preview"
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handlePreview(record.tableName)}
        >
          预览
        </Button>,
        <Button
          key="download"
          type="link"
          size="small"
          icon={<DownloadOutlined />}
          onClick={() => handleDownload(record.tableName)}
        >
          下载
        </Button>,
      ],
    },
  ]

  const tabItems = Object.entries(groupedFiles).map(([type, files]) => ({
    key: type,
    label: FILE_TYPE_LABELS[type] ?? type,
    children: (
      <div style={{ maxHeight: 500, overflow: 'auto' }}>
        {files.map((file) => (
          <div key={file.fileName} style={{ marginBottom: 16 }}>
            <div style={{ fontWeight: 600, marginBottom: 8, color: 'var(--color-primary)' }}>
              {file.fileName}
            </div>
            <pre
              style={{
                background: 'var(--color-bg-input)',
                padding: 16,
                borderRadius: 6,
                fontSize: 13,
                lineHeight: 1.6,
                overflow: 'auto',
                margin: 0,
              }}
            >
              <code>{file.content}</code>
            </pre>
          </div>
        ))}
      </div>
    ),
  }))

  return (
    <>
      <ProTable<GenTableRecord>
        headerTitle="代码生成"
        actionRef={actionRef}
        rowKey="tableName"
        scroll={{ x: 1000 }}
        request={async (params) => {
          return safeProTableRequest<GenTableRecord>(() =>
            getGenTables({
              page: params.current,
              pageSize: params.pageSize,
              tableName: params.tableName,
            })
          )
        }}
        columns={columns}
        pagination={{ defaultPageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title="代码预览"
        open={previewVisible}
        onCancel={() => {
          setPreviewVisible(false)
          setPreviewFiles([])
        }}
        footer={null}
        width={900}
        destroyOnHidden
      >
        {previewLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
        ) : (
          <Tabs items={tabItems} />
        )}
      </Modal>
    </>
  )
}
