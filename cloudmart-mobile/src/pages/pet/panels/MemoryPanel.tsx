import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Image, Text, Textarea, View } from '@tarojs/components'
import { petCompanionApi, type PetDiaryEntry, type PetMemory, type PetNotifyPref } from '@/api/pet'
import { fileApi } from '@/api/file'
import styles from '../index.module.scss'
import { CreamToggle } from '@/components/pet-cream'

/**
 * 回忆面板（N01/N02/N03/B19 对齐 Web 端 PetMemoryPanel）：
 * 成长日记（游标分页）/ 相册（FILE-01 fileId 引用，每用户 100 张）/
 * 结构化记忆管理（USER 编辑优先）/ 通知偏好（免打扰/日常问候）。
 */

type MemoryTab = 'diary' | 'album' | 'memories' | 'prefs'

const PAGE_SIZE = 20

function formatDateTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

export function MemoryPanel({ petId, onRefresh }: { petId: number | string | null; onRefresh?: () => void }) {
  const [tab, setTab] = useState<MemoryTab>('diary')

  const [diary, setDiary] = useState<PetDiaryEntry[]>([])
  const [diaryCursor, setDiaryCursor] = useState<string | null>(null)
  const [diaryHasMore, setDiaryHasMore] = useState(false)
  const [diaryLoading, setDiaryLoading] = useState(false)

  const [album, setAlbum] = useState<Array<{ assetId: string; entryId: string | null;
    bindStatus?: string; previewUrl?: string | null }>>([])
  const [albumLoading, setAlbumLoading] = useState(false)
  const [uploading, setUploading] = useState(false)

  const [memories, setMemories] = useState<PetMemory[]>([])
  const [memoriesLoading, setMemoriesLoading] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [editValue, setEditValue] = useState('')

  const [prefs, setPrefs] = useState<PetNotifyPref | null>(null)

  const loadDiary = useCallback(
    async (reset: boolean) => {
      if (!petId) return
      setDiaryLoading(true)
      try {
        const { data: res } = await petCompanionApi.listDiary(petId, {
          cursor: reset ? undefined : (diaryCursor ?? undefined),
          size: PAGE_SIZE,
        })
        if (res.success && res.data) {
          setDiary((prev) => (reset ? res.data.items : [...prev, ...res.data.items]))
          setDiaryCursor(res.data.nextCursor ?? null)
          setDiaryHasMore(Boolean(res.data.hasMore))
        }
      } finally {
        setDiaryLoading(false)
      }
    },
    [petId, diaryCursor],
  )

  /** PET-13/T31：相册改走服务端独立列表（含 BINDING/FAILED 状态与重试入口） */
  const loadAlbum = useCallback(async () => {
    if (!petId) return
    setAlbumLoading(true)
    try {
      const { data: res } = await petCompanionApi.listAlbumAssets(petId)
      if (res.success && res.data) {
        setAlbum(res.data.map((item) => ({
          assetId: item.assetId,
          entryId: item.diaryEntryId,
          bindStatus: item.bindStatus,
          previewUrl: item.previewUrl,
        })))
      }
    } finally {
      setAlbumLoading(false)
    }
  }, [petId])

  /** PET-13/T32：BINDING/FAILED 条目重试绑定（远端幂等） */
  const retryBinding = useCallback(async (assetId: string) => {
    try {
      await petCompanionApi.retryAlbumAsset(petId!, assetId)
      await loadAlbum()
      Taro.showToast({ title: '已重试绑定', icon: 'success' })
    } catch {
      Taro.showToast({ title: '重试未成功，稍后再试', icon: 'none' })
    }
  }, [petId, loadAlbum])

  const loadMemories = useCallback(async () => {
    if (!petId) return
    setMemoriesLoading(true)
    try {
      const { data: res } = await petCompanionApi.listMemories(petId)
      if (res.success) setMemories(res.data ?? [])
    } finally {
      setMemoriesLoading(false)
    }
  }, [petId])

  const loadPrefs = useCallback(async () => {
    try {
      const { data: res } = await petCompanionApi.getNotifyPrefs()
      if (res.success && res.data) setPrefs(res.data)
    } catch {
      // 展示型数据
    }
  }, [])

  useEffect(() => {
    if (!petId) return
    if (tab === 'diary') loadDiary(true)
    else if (tab === 'album') loadAlbum()
    else if (tab === 'memories') loadMemories()
    else if (tab === 'prefs') loadPrefs()
  }, [tab, petId, loadDiary, loadAlbum, loadMemories, loadPrefs])

  const handleUpload = async () => {
    if (!petId) return
    try {
      const choose = await Taro.chooseImage({ count: 1, sizeType: ['compressed'] })
      const filePath = choose.tempFilePaths[0]
      if (!filePath) return
      setUploading(true)
      // PET-13/T31：相册资产上传即 PRIVATE（绑定要求 PRIVATE；原 PUBLIC 上传导致绑定失败或隐私风险）
      const assetRes = await fileApi.uploadAsset(filePath, 'PRIVATE')
      const fileId = assetRes.data.data?.fileId
      if (!fileId) {
        Taro.showToast({ title: '资产上传失败', icon: 'none' })
        return
      }
      const { data: res } = await petCompanionApi.uploadAlbumAsset(petId, fileId)
      if (res.success) {
        Taro.showToast({ title: '已上传（待审核展示）', icon: 'success' })
        loadAlbum()
      }
    } catch {
      Taro.showToast({ title: '上传失败（≤5MB，每用户 100 张）', icon: 'none' })
    } finally {
      setUploading(false)
    }
  }

  const saveMemoryEdit = async (memory: PetMemory) => {
    if (!petId || !editValue.trim()) {
      Taro.showToast({ title: '记忆内容不能为空', icon: 'none' })
      return
    }
    const { data: res } = await petCompanionApi.editMemory(petId, memory.id, { value: editValue.trim() })
    if (res.success) {
      Taro.showToast({ title: '已更新（编辑优先于自动抽取）', icon: 'success' })
      setEditId(null)
      loadMemories()
    }
  }

  return (
    <View className={styles.panelWrap}>
      <View className={styles.memoryTabs}>
        {(
          [
            { key: 'diary', label: '📖 日记' },
            { key: 'album', label: '🖼️ 相册' },
            { key: 'memories', label: '🧠 记忆' },
            { key: 'prefs', label: '🔔 偏好' },
          ] as const
        ).map((item) => (
          <Text
            key={item.key}
            className={`${styles.memoryTab} ${tab === item.key ? styles.memoryTabActive : ''}`}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </Text>
        ))}
      </View>

      {!petId ? (
        <View className={styles.memoryEmpty}>
          <Text>请先领养一只宠物</Text>
        </View>
      ) : tab === 'diary' ? (
        <View>
          {diary.length === 0 && !diaryLoading ? (
            <View className={styles.memoryEmpty}>
              <Text>还没有日记，和宠物互动后会自动记录哦</Text>
            </View>
          ) : (
            diary.map((entry) => (
              <View key={entry.id} className={styles.memoryCard}>
                <View className={styles.memoryCardHead}>
                  <Text className={entry.visibility === 'PUBLIC' ? styles.memoryPublic : styles.memoryPrivate}>
                    {entry.visibility === 'PUBLIC' ? '公开' : '私密'}
                  </Text>
                  <Text className={styles.memoryTime}>{formatDateTime(entry.createdAt)}</Text>
                </View>
                <Text className={styles.memoryContent}>{entry.content}</Text>
              </View>
            ))
          )}
          {diaryHasMore && (
            <Button size='mini' loading={diaryLoading} onClick={() => loadDiary(false)}>
              加载更多
            </Button>
          )}
        </View>
      ) : tab === 'album' ? (
        <View>
          <Button size='mini' type='primary' loading={uploading} onClick={handleUpload}>
            上传照片（≤5MB，每用户 100 张）
          </Button>
          <View className={styles.albumGrid}>
            {album.map((asset) => (
              <View key={asset.assetId} className={styles.albumCell}>
                {asset.bindStatus === 'BOUND' ? (
                  <Image
                    className={styles.albumImage}
                    src={asset.previewUrl ?? `/file/assets/${asset.assetId}/download`}
                    mode='aspectFill'
                    onClick={() =>
                      Taro.previewImage({ urls: [asset.previewUrl ?? `/file/assets/${asset.assetId}/download`] })
                    }
                  />
                ) : (
                  <View className={styles.albumImage} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Text style={{ fontSize: 10, color: '#999' }}>
                      {asset.bindStatus === 'FAILED' ? '绑定失败' : '绑定中'}
                    </Text>
                  </View>
                )}
                {asset.bindStatus && asset.bindStatus !== 'BOUND' ? (
                  <Text
                    className={styles.albumDelete}
                    onClick={() => {
                      if (asset.bindStatus === 'FAILED' && petId) void retryBinding(asset.assetId)
                    }}
                  >
                    {asset.bindStatus === 'FAILED' ? '重试绑定' : '处理中…'}
                  </Text>
                ) : null}
                <Text
                  className={styles.albumDelete}
                  onClick={() =>
                    petId &&
                    petCompanionApi.deleteAlbumAsset(petId, asset.assetId).then(() => {
                      Taro.showToast({ title: '已删除', icon: 'none' })
                      loadAlbum()
                    })
                  }
                >
                  删除
                </Text>
              </View>
            ))}
          </View>
          {album.length === 0 && !albumLoading && (
            <View className={styles.memoryEmpty}>
              <Text>相册还是空的</Text>
            </View>
          )}
        </View>
      ) : tab === 'memories' ? (
        <View>
          <View className={styles.memoryActions}>
            <Button
              size='mini'
              onClick={() =>
                petId &&
                petCompanionApi.setMemorySettings(petId, { extract: true, use: true }).then(() => {
                  Taro.showToast({ title: '记忆开关已保存', icon: 'none' })
                })
              }
            >
              开启抽取+注入
            </Button>
            <Button
              size='mini'
              onClick={() =>
                petId &&
                petCompanionApi
                  .setMemorySettings(petId, { extract: false, use: false })
                  .then(() => {
                    Taro.showToast({ title: '已关闭抽取与注入', icon: 'none' })
                  })
              }
            >
              全部关闭
            </Button>
            <Button
              size='mini'
              type='warn'
              onClick={() =>
                petId &&
                Taro.showModal({ title: '清空记忆', content: '全部软删且防复活，确定？' }).then(({ confirm }) => {
                  if (confirm) {
                    petCompanionApi.clearMemories(petId).then(() => {
                      Taro.showToast({ title: '已清空', icon: 'none' })
                      loadMemories()
                    })
                  }
                })
              }
            >
              清空全部
            </Button>
          </View>
          {memories.length === 0 && !memoriesLoading ? (
            <View className={styles.memoryEmpty}>
              <Text>暂无记忆；聊天中聊到喜好/习惯会自动抽取</Text>
            </View>
          ) : (
            memories.map((memory) => (
              <View key={memory.id} className={styles.memoryCard}>
                <View className={styles.memoryCardHead}>
                  <Text className={styles.memoryKey}>
                    {memory.memoryKey}：{memory.memoryValue}
                  </Text>
                  <Text className={styles.memorySource}>{memory.source === 'USER' ? '我的编辑' : '自动'}</Text>
                </View>
                {editId === memory.id ? (
                  <View>
                    <Textarea className={styles.memoryEditInput} value={editValue} onInput={(e) => setEditValue(e.detail.value)} />
                    <View className={styles.memoryActions}>
                      <Button size='mini' type='primary' onClick={() => saveMemoryEdit(memory)}>
                        保存
                      </Button>
                      <Button size='mini' onClick={() => setEditId(null)}>
                        取消
                      </Button>
                    </View>
                  </View>
                ) : (
                  <View className={styles.memoryActions}>
                    <Text className={styles.memoryTime}>重要度 {memory.importance}</Text>
                    <Button
                      size='mini'
                      onClick={() => {
                        setEditId(memory.id)
                        setEditValue(memory.memoryValue)
                      }}
                    >
                      编辑
                    </Button>
                    <Button
                      size='mini'
                      type='warn'
                      onClick={() =>
                        petId &&
                        petCompanionApi.deleteMemory(petId, memory.id).then(() => {
                          Taro.showToast({ title: '已删除', icon: 'none' })
                          loadMemories()
                        })
                      }
                    >
                      删除
                    </Button>
                  </View>
                )}
              </View>
            ))
          )}
        </View>
      ) : (
        <View>
          <View className={styles.prefRow}>
            <Text>日常问候</Text>
            <CreamToggle
              on={prefs?.dailyGreetingEnabled ?? true}
              onChange={(next) => {
                petCompanionApi
                  .updateNotifyPrefs({ muteDailyGreeting: prefs?.muteDailyGreeting ?? false, dailyGreetingEnabled: next })
                  .then(() => {
                    Taro.showToast({ title: '已更新', icon: 'success' })
                    loadPrefs()
                    onRefresh?.()
                  })
              }}
            />
          </View>
          <View className={styles.prefRow}>
            <Text>免打扰</Text>
            <CreamToggle
              on={prefs?.muteDailyGreeting ?? false}
              onChange={(next) => {
                petCompanionApi
                  .updateNotifyPrefs({ muteDailyGreeting: next, dailyGreetingEnabled: prefs?.dailyGreetingEnabled ?? true })
                  .then(() => {
                    Taro.showToast({ title: '已更新', icon: 'success' })
                    loadPrefs()
                  })
              }}
            />
          </View>
          <Text className={styles.memoryHint}>仅作用于日常问候类主动消息；重要业务通知不受影响。</Text>
        </View>
      )}
    </View>
  )
}
