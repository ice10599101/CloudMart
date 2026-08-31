import { useRef, useState } from 'react'
import { ProTable, ModalForm, ProFormText, ProFormDigit } from '@ant-design/pro-components'
import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { Button, Popconfirm, Tag, Typography, Upload } from 'antd'
import { CustomerServiceOutlined, PauseOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons'
import type { UploadFile } from 'antd'
import {
  getAdminWishBgmSongs,
  createAdminWishBgmSong,
  updateAdminWishBgmSong,
  updateAdminWishBgmSongStatus,
  deleteAdminWishBgmSong,
} from '@/api/admin/wish'
import type { AdminBgmSongRecord } from '@/api/admin/wish'
import { uploadFile } from '@/api/file'
import { safeProTableRequest } from '@/utils/proTable'
import { useMessage } from '@/utils/useMessage'
import { useModalConfirm } from '@/utils/useModalConfirm'

/**
 * 心愿宇宙背景音乐曲库管理（Sprint 2.3：上传歌曲 + 勾选播放列表）。
 *
 * 上传链路：先调 mall-file /file/upload 传 mp3（白名单已含，上限 50MB）
 * 拿到 OSS URL，再登记曲库（默认未加入播放列表，需启停勾选）。
 * 播放列表语义：active=true 的歌曲按 sort 升序顺序循环播放；
 * 空列表时四端播放器回退内置默认曲。
 */

/** 新增弹窗表单值（上传成功后 url/fileSize 注入） */
interface SongFormValues {
  title: string
  sort: number
}

/** 文件大小展示（MB 保留 1 位） */
function formatFileSize(bytes: number): string {
  if (!bytes) return '-'
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** 上传大小上限（与 mall-file 后端 mp3 白名单上限一致） */
const MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024

export default function WishBgm() {
  const message = useMessage()
  const actionRef = useRef<ActionType>(null)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingRecord, setEditingRecord] = useState<AdminBgmSongRecord | null>(null)
  const { confirmSubmit, createHandleOpenChange } = useModalConfirm()

  // 新增弹窗内的上传状态
  const [uploadedUrl, setUploadedUrl] = useState<string | null>(null)
  const [uploadedSize, setUploadedSize] = useState<number | null>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)

  // 行内试听（单实例：切歌自动停上一首）
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const [playingId, setPlayingId] = useState<number | null>(null)

  const stopPreview = () => {
    audioRef.current?.pause()
    audioRef.current = null
    setPlayingId(null)
  }

  const togglePreview = (record: AdminBgmSongRecord) => {
    if (playingId === record.id) {
      stopPreview()
      return
    }
    stopPreview()
    const audio = new Audio(record.url)
    audio.volume = 0.8
    audio.onended = () => setPlayingId(null)
    audio.onerror = () => {
      message.error('试听失败（音源不可用）')
      setPlayingId(null)
    }
    audioRef.current = audio
    setPlayingId(record.id)
    audio.play().catch(() => {
      message.error('试听失败（音源不可用）')
      setPlayingId(null)
    })
  }

  const handleCreate = async (values: SongFormValues) => {
    if (!uploadedUrl) {
      message.error('请先上传音频文件')
      return false
    }
    return confirmSubmit(async () => {
      await createAdminWishBgmSong({
        title: values.title,
        url: uploadedUrl,
        fileSize: uploadedSize ?? undefined,
        sort: values.sort,
      })
      message.success('登记成功（默认未加入播放列表，请勾选启用）')
      actionRef.current?.reload()
    })
  }

  const handleUpdate = async (values: SongFormValues) => {
    if (!editingRecord) return false
    return confirmSubmit(async () => {
      await updateAdminWishBgmSong(editingRecord.id, {
        title: values.title,
        sort: values.sort,
      })
      message.success('更新成功')
      setEditingRecord(null)
      actionRef.current?.reload()
    })
  }

  const handleToggleStatus = async (record: AdminBgmSongRecord) => {
    await updateAdminWishBgmSongStatus(record.id, !record.active)
    message.success(
      record.active
        ? '已移出播放列表（四端播放列表即时生效）'
        : '已加入播放列表（按顺序排序循环播放）'
    )
    actionRef.current?.reload()
  }

  const handleDelete = async (record: AdminBgmSongRecord) => {
    if (playingId === record.id) stopPreview()
    await deleteAdminWishBgmSong(record.id)
    message.success('已删除（OSS 音频文件保留，误删可重新登记同 URL 恢复）')
    actionRef.current?.reload()
  }

  /**
   * 上传前校验：仅 mp3，≤50MB。
   * 校验通过必须返回 true 才会触发 customRequest（customUpload）执行上传；
   * 返回 false 会阻止上传，导致 uploadedUrl 恒为空。
   */
  const beforeUpload = (file: UploadFile) => {
    if (file.name && !file.name.toLowerCase().endsWith('.mp3')) {
      message.error('仅支持 mp3 格式')
      return Upload.LIST_IGNORE
    }
    if (file.size && file.size > MAX_FILE_SIZE_BYTES) {
      message.error(`文件 ${(file.size / 1024 / 1024).toFixed(1)} MB 超过 50MB 上限`)
      return Upload.LIST_IGNORE
    }
    return true
  }

  const customUpload = async (options: { file: unknown; onSuccess?: (body: unknown) => void; onError?: (e: Error) => void }) => {
    const file = options.file as File
    setUploading(true)
    setUploadProgress(0)
    try {
      const response = await uploadFile(file, { onProgress: setUploadProgress })
      const result = response.data
      if (result?.success && result.data?.url) {
        setUploadedUrl(result.data.url)
        setUploadedSize(result.data.fileSize ?? null)
        options.onSuccess?.(result)
      } else {
        throw new Error(result?.error?.message ?? '上传失败')
      }
    } catch (error) {
      options.onError?.(error as Error)
      message.error('上传失败，请重试')
    } finally {
      setUploading(false)
    }
  }

  const columns: ProColumns<AdminBgmSongRecord>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '标题', dataIndex: 'title', width: 180, search: false, ellipsis: true },
    {
      title: '播放顺序',
      dataIndex: 'sort',
      width: 90,
      search: false,
      render: (_, record) => (
        <Tag color={record.active ? 'blue' : 'default'}>{record.sort}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'active',
      width: 100,
      search: false,
      render: (_, record) =>
        record.active ? <Tag color="success">播放中</Tag> : <Tag color="default">未播放</Tag>,
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 90,
      search: false,
      render: (_, record) => formatFileSize(record.fileSize),
    },
    {
      title: '上传时间',
      dataIndex: 'createdAt',
      width: 150,
      search: false,
      render: (_, record) => new Date(record.createdAt).toLocaleString(),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 260,
      fixed: 'right',
      render: (_, record) => [
        <Button
          key="preview"
          type="link"
          size="small"
          icon={playingId === record.id ? <PauseOutlined /> : <CustomerServiceOutlined />}
          onClick={() => togglePreview(record)}
        >
          {playingId === record.id ? '停止' : '试听'}
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
          key="toggle"
          title={
            record.active
              ? '确认移出播放列表？四端即时生效'
              : '确认加入播放列表？将按顺序循环播放'
          }
          onConfirm={() => handleToggleStatus(record)}
        >
          <Button type="link" size="small" danger={record.active}>
            {record.active ? '停播' : '播放'}
          </Button>
        </Popconfirm>,
        <Popconfirm
          key="delete"
          title="确认删除？OSS 音频文件将保留"
          onConfirm={() => handleDelete(record)}
        >
          <Button type="link" size="small" danger>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <>
      <ProTable<AdminBgmSongRecord>
        headerTitle="背景音乐曲库"
        actionRef={actionRef}
        rowKey="id"
        search={false}
        scroll={{ x: 1000 }}
        request={async () => {
          return safeProTableRequest<AdminBgmSongRecord>(() => getAdminWishBgmSongs())
        }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingRecord(null)
              setUploadedUrl(null)
              setUploadedSize(null)
              setUploadProgress(0)
              setModalVisible(true)
            }}
          >
            上传歌曲
          </Button>,
        ]}
        columns={columns}
        pagination={false}
      />

      <ModalForm<SongFormValues>
        title={editingRecord ? `编辑歌曲：${editingRecord.title}` : '上传歌曲'}
        open={modalVisible}
        onOpenChange={createHandleOpenChange(setModalVisible, () => {
          setEditingRecord(null)
          setUploadedUrl(null)
          setUploadedSize(null)
        })}
        onFinish={editingRecord ? handleUpdate : handleCreate}
        initialValues={
          editingRecord
            ? { title: editingRecord.title, sort: editingRecord.sort }
            : { sort: 0 }
        }
        modalProps={{ destroyOnHidden: true, mask: { closable: false }, keyboard: false }}
        width={520}
      >
        {!editingRecord && (
          <>
            <Upload
              accept=".mp3"
              maxCount={1}
              beforeUpload={beforeUpload}
              customRequest={customUpload}
              onRemove={() => {
                setUploadedUrl(null)
                setUploadedSize(null)
              }}
            >
              <Button icon={<UploadOutlined />} loading={uploading}>
                {uploading ? `上传中 ${uploadProgress}%` : '选择 mp3 文件'}
              </Button>
            </Upload>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              仅支持 mp3 格式，单文件不超过 50MB；上传成功后自动填充文件名
            </Typography.Text>
          </>
        )}
        <ProFormText
          name="title"
          label="歌曲标题"
          placeholder="播放器曲名展示"
          rules={[
            { required: true, message: '请输入歌曲标题' },
            { max: 128, message: '标题不能超过128字符' },
          ]}
        />
        <ProFormDigit
          name="sort"
          label="播放顺序"
          placeholder="升序播放，同序按上传先后"
          min={0}
          max={9999}
          fieldProps={{ precision: 0 }}
          extra="播放列表内按此数字从小到大顺序循环播放"
        />
      </ModalForm>
    </>
  )
}
