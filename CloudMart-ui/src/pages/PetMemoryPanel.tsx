import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Empty, Image, Input, InputNumber, Modal, Popconfirm, Space, Switch, Tabs, Tag, Upload, message } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import {
  listPetDiary,
  uploadPetAlbumAsset,
  deletePetAlbumAsset,
  listPetMemories,
  editPetMemory,
  deletePetMemory,
  clearPetMemories,
  setPetMemorySettings,
  getPetNotifyPrefs,
  updatePetNotifyPrefs,
  type PetDiaryEntry,
  type PetMemory,
  type PetNotifyPref,
} from '@/api/pet'
import { uploadFileAsset } from '@/api/file'

/**
 * 宠物陪伴功能面板（N01/N02/N03/B19，对齐 PetCompanionFeatureController）：
 * 成长日记时间线（游标分页）、相册（FILE-01 资产 fileId 引用，每用户 100 张）、
 * 结构化记忆管理（USER 编辑优先于 AUTO 抽取）与通知偏好（免打扰/日常问候）。
 */

type MemoryTab = 'diary' | 'album' | 'memories' | 'prefs'

const PAGE_SIZE = 20

function formatDateTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

export default function PetMemoryPanel({ petId }: { petId: number | string | null }) {
  const [tab, setTab] = useState<MemoryTab>('diary')

  // ---- 日记（游标分页） ----
  const [diary, setDiary] = useState<PetDiaryEntry[]>([])
  const [diaryCursor, setDiaryCursor] = useState<string | null>(null)
  const [diaryHasMore, setDiaryHasMore] = useState(false)
  const [diaryLoading, setDiaryLoading] = useState(false)

  const loadDiary = useCallback(
    async (reset: boolean) => {
      if (!petId) return
      setDiaryLoading(true)
      try {
        const res = await listPetDiary(petId, reset ? undefined : (diaryCursor ?? undefined), PAGE_SIZE)
        const page = res.data.data
        setDiary((prev) => (reset ? page.items : [...prev, ...page.items]))
        setDiaryCursor(page.nextCursor ?? null)
        setDiaryHasMore(Boolean(page.hasMore))
      } finally {
        setDiaryLoading(false)
      }
    },
    [petId, diaryCursor],
  )

  // ---- 相册（后端无独立列表端点：由日记条目 assetIds 聚合，upload 时绑定 diaryEntryId） ----
  const [album, setAlbum] = useState<Array<{ assetId: number; auditStatus: string; entryId: number }>>([])
  const [albumLoading, setAlbumLoading] = useState(false)
  const [uploading, setUploading] = useState(false)

  const loadAlbum = useCallback(async () => {
    if (!petId) return
    setAlbumLoading(true)
    try {
      const collected: Array<{ assetId: number; auditStatus: string; entryId: number }> = []
      let cursor: string | null = null
      // 相册资源随日记分页聚合（上限 5 页，防止大相册长时间拉取）
      for (let page = 0; page < 5; page += 1) {
        const res = await listPetDiary(petId, cursor ?? undefined, PAGE_SIZE)
        const pageData = res.data.data
        const items = pageData?.items ?? []
        items.forEach((entry) => {
          ;(entry.assetIds ?? []).forEach((assetId) => {
            collected.push({ assetId, auditStatus: 'PENDING', entryId: entry.id })
          })
        })
        cursor = pageData?.nextCursor ?? null
        if (!cursor || items.length === 0) break
      }
      setAlbum(collected)
    } finally {
      setAlbumLoading(false)
    }
  }, [petId])

  // ---- 记忆 ----
  const [memories, setMemories] = useState<PetMemory[]>([])
  const [memoriesLoading, setMemoriesLoading] = useState(false)
  const [editing, setEditing] = useState<PetMemory | null>(null)
  const [editValue, setEditValue] = useState('')
  const [editImportance, setEditImportance] = useState(3)

  // ---- 通知偏好 ----
  const [prefs, setPrefs] = useState<PetNotifyPref | null>(null)
  const [extractOn, setExtractOn] = useState(true)
  const [injectOn, setInjectOn] = useState(true)

  const loadMemories = useCallback(async () => {
    if (!petId) return
    setMemoriesLoading(true)
    try {
      const res = await listPetMemories(petId)
      setMemories(res.data.data ?? [])
    } finally {
      setMemoriesLoading(false)
    }
  }, [petId])

  const loadPrefs = useCallback(async () => {
    const res = await getPetNotifyPrefs()
    setPrefs(res.data.data ?? null)
  }, [])

  useEffect(() => {
    if (!petId) return
    if (tab === 'diary') loadDiary(true)
    else if (tab === 'memories') loadMemories()
    else if (tab === 'prefs') loadPrefs()
  }, [tab, petId, loadDiary, loadMemories, loadPrefs])

  const handleUpload = async (file: File) => {
    if (!petId) return
    setUploading(true)
    try {
      const assetRes = await uploadFileAsset(file, 'PUBLIC')
      const fileId = assetRes.data.data?.fileId
      if (!fileId) {
        message.error('资产上传失败：未返回 fileId')
        return
      }
      await uploadPetAlbumAsset(petId, fileId)
      message.success('已上传到相册（待审核展示）')
      loadAlbum()
    } catch {
      message.error('上传失败（JPEG/PNG/WebP ≤5MB，每用户 100 张）')
    } finally {
      setUploading(false)
    }
  }

  const saveMemoryEdit = async () => {
    if (!petId || !editing) return
    if (!editValue.trim()) {
      message.warning('记忆内容不能为空')
      return
    }
    await editPetMemory(petId, editing.id, { memoryValue: editValue.trim(), importance: editImportance })
    message.success('记忆已更新（USER 编辑优先于自动抽取）')
    setEditing(null)
    loadMemories()
  }

  if (!petId) {
    return (
      <Card>
        <Empty description="请先领养一只宠物" />
      </Card>
    )
  }

  return (
    <Card
      title="📖 成长回忆"
      extra={
        <Button icon={<ReloadOutlined />} size="small" onClick={() => (tab === 'diary' ? loadDiary(true) : tab === 'memories' ? loadMemories() : loadPrefs())}>
          刷新
        </Button>
      }
    >
      <Tabs
        activeKey={tab}
        onChange={(key) => setTab(key as MemoryTab)}
        items={[
          { key: 'diary', label: '成长日记' },
          { key: 'album', label: '相册' },
          { key: 'memories', label: '记忆' },
          { key: 'prefs', label: '偏好' },
        ]}
      />

      {tab === 'diary' && (
        <div>
          {diary.length === 0 && !diaryLoading ? (
            <Empty description="还没有日记，和宠物互动后会自动记录哦" />
          ) : (
            <div style={{ display: 'grid', gap: 12 }}>
              {diary.map((entry) => (
                <Card key={entry.id} size="small">
                  <Space direction="vertical" style={{ width: '100%' }} size={4}>
                    <Space>
                      <Tag color={entry.visibility === 'PUBLIC' ? 'green' : 'default'}>
                        {entry.visibility === 'PUBLIC' ? '公开' : '私密'}
                      </Tag>
                      <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>{formatDateTime(entry.createdAt)}</span>
                    </Space>
                    <span>{entry.content}</span>
                  </Space>
                </Card>
              ))}
            </div>
          )}
          {diaryHasMore && (
            <Button block style={{ marginTop: 12 }} loading={diaryLoading} onClick={() => loadDiary(false)}>
              加载更多
            </Button>
          )}
        </div>
      )}

      {tab === 'album' && (
        <div>
          <Upload
            accept="image/jpeg,image/png,image/webp"
            showUploadList={false}
            beforeUpload={(file) => {
              void handleUpload(file)
              return false
            }}
          >
            <Button type="primary" icon={<PlusOutlined />} loading={uploading}>
              上传照片（≤5MB，每用户 100 张）
            </Button>
          </Upload>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: 12, marginTop: 16 }}>
            {album.map((asset) => (
              <Card
                key={asset.assetId}
                size="small"
                cover={
                  <Image
                    alt="宠物相册"
                    src={`/file/assets/${asset.assetId}/download`}
                    style={{ objectFit: 'cover', height: 120 }}
                    fallback="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNDAiIGhlaWdodD0iMTIwIj48cmVjdCB3aWR0aD0iMTQwIiBoZWlnaHQ9IjEyMCIgZmlsbD0iI2VlZSIvPjx0ZXh0IHg9IjcwIiB5PSI2NSIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZmlsbD0iIzk5OSIgZm9udC1zaXplPSIxMiI+5peg5p2R5Zmo56S6PC90ZXh0Pjwvc3ZnPg=="
                  />
                }
                actions={[
                  <Popconfirm key="del" title="删除这张照片？" onConfirm={() => petId && deletePetAlbumAsset(petId, asset.assetId).then(loadAlbum)}>
                    <DeleteOutlined />
                  </Popconfirm>,
                ]}
              >
                <Card.Meta description={<Tag>资产 #{asset.assetId}</Tag>} />
              </Card>
            ))}
          </div>
          {album.length === 0 && !albumLoading && <Empty description="相册还是空的" style={{ marginTop: 24 }} />}
        </div>
      )}

      {tab === 'memories' && (
        <div>
          <Space style={{ marginBottom: 12 }} wrap>
            <Button size="small" onClick={() => setPetMemorySettings(petId, { extract: extractOn, use: injectOn }).then(() => message.success('记忆开关已保存'))}>
              保存记忆开关
            </Button>
            <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>自动抽取</span>
            <Switch size="small" checked={extractOn} onChange={setExtractOn} />
            <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>注入对话</span>
            <Switch size="small" checked={injectOn} onChange={setInjectOn} />
            <Popconfirm title="清空全部记忆？全部软删且防复活。" onConfirm={() => clearPetMemories(petId).then(() => { message.success('已清空'); loadMemories() })}>
              <Button size="small" danger>清空全部</Button>
            </Popconfirm>
          </Space>
          {memories.length === 0 && !memoriesLoading ? (
            <Empty description="暂无记忆；聊天中聊到喜好/习惯会自动抽取" />
          ) : (
            <div style={{ display: 'grid', gap: 8 }}>
              {memories.map((memory) => (
                <Card key={memory.id} size="small">
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Space size={8} wrap>
                      <Tag>{memory.memoryType}</Tag>
                      <span style={{ fontWeight: 600 }}>{memory.memoryKey}</span>
                      <span>：{memory.memoryValue}</span>
                      <Tag color={memory.source === 'USER' ? 'blue' : 'default'}>{memory.source === 'USER' ? '我的编辑' : '自动'}</Tag>
                      <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>重要度 {memory.importance}</span>
                    </Space>
                    <Space>
                      <Button
                        size="small"
                        icon={<EditOutlined />}
                        onClick={() => {
                          setEditing(memory)
                          setEditValue(memory.memoryValue)
                          setEditImportance(memory.importance)
                        }}
                      />
                      <Popconfirm title="删除这条记忆？" onConfirm={() => petId && deletePetMemory(petId, memory.id).then(loadMemories)}>
                        <Button size="small" danger icon={<DeleteOutlined />} />
                      </Popconfirm>
                    </Space>
                  </Space>
                </Card>
              ))}
            </div>
          )}
          <Modal
            title="编辑记忆"
            open={Boolean(editing)}
            onOk={saveMemoryEdit}
            onCancel={() => setEditing(null)}
            destroyOnClose
          >
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <Input value={editValue} onChange={(e) => setEditValue(e.target.value)} placeholder="记忆内容" />
              <Space>
                <span>重要度</span>
                <InputNumber min={1} max={5} value={editImportance} onChange={(v) => setEditImportance(v ?? 3)} />
              </Space>
            </Space>
          </Modal>
        </div>
      )}

      {tab === 'prefs' && (
        <div style={{ display: 'grid', gap: 16, maxWidth: 480 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>日常问候</span>
            <Switch
              checked={prefs?.dailyGreetingEnabled ?? true}
              onChange={(checked) =>
                updatePetNotifyPrefs({
                  muteDailyGreeting: prefs?.muteDailyGreeting ?? false,
                  dailyGreetingEnabled: checked,
                }).then(() => {
                  message.success('已更新')
                  loadPrefs()
                })
              }
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>免打扰</span>
            <Switch
              checked={prefs?.muteDailyGreeting ?? false}
              onChange={(checked) =>
                updatePetNotifyPrefs({
                  muteDailyGreeting: checked,
                  dailyGreetingEnabled: prefs?.dailyGreetingEnabled ?? true,
                }).then(() => {
                  message.success('已更新')
                  loadPrefs()
                })
              }
            />
          </div>
          <p style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
            仅作用于日常问候类主动消息；喂食完成等重要业务通知不受影响。
          </p>
        </div>
      )}
    </Card>
  )
}
