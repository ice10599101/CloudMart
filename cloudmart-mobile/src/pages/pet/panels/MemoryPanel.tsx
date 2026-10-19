import { useCallback, useEffect, useState } from 'react'
import Taro from '@tarojs/taro'
import { Button, Image, Text, Textarea, View } from '@tarojs/components'
import {
  petApi,
  petCompanionApi,
  type PetAlbumAssetView,
  type PetContentVisibility,
  type PetDiaryEntry,
  type PetMemory,
  type PetNotifyPref,
} from '@/api/pet'
import { fileApi } from '@/api/file'
import { errorMessage } from '@/utils/request'
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

/** R18：后端能力清单关闭时的显式不可用态（区别于空列表与加载失败） */
const FEATURE_DISABLED_NOTE: Record<'diary' | 'album', string> = {
  diary: '成长日记功能暂未开放',
  album: '相册绑定功能暂未开放',
}

const VISIBILITY_OPTIONS: Array<{ label: string; value: PetContentVisibility }> = [
  { label: '公开', value: 'PUBLIC' },
  { label: '仅主人可见', value: 'OWNER_ONLY' },
  { label: '私密', value: 'PRIVATE' },
]

function visibilityLabel(value: PetContentVisibility | string | null | undefined): string {
  return VISIBILITY_OPTIONS.find((item) => item.value === value)?.label ?? '私密'
}

export function MemoryPanel(
  { petId, onRefresh, diaryEnabled = true, albumEnabled = true }: {
    petId: number | string | null
    onRefresh?: () => void
    /** R18：bootstrap.capabilities.diary —— 关闭时显示不可用说明而非假入口 */
    diaryEnabled?: boolean
    /** R18：bootstrap.capabilities.albumBinding */
    albumEnabled?: boolean
  },
) {
  const [tab, setTab] = useState<MemoryTab>('diary')

  const [diary, setDiary] = useState<PetDiaryEntry[]>([])
  const [diaryCursor, setDiaryCursor] = useState<string | null>(null)
  const [diaryHasMore, setDiaryHasMore] = useState(false)
  const [diaryLoading, setDiaryLoading] = useState(false)
  const [diaryPatchingId, setDiaryPatchingId] = useState<number | string | null>(null)

  const [album, setAlbum] = useState<PetAlbumAssetView[]>([])
  const [albumLoading, setAlbumLoading] = useState(false)
  const [uploading, setUploading] = useState(false)
  // R18：相册说明行内编辑（Taro showModal 不保证支持输入框）
  const [captionEdit, setCaptionEdit] = useState<{ assetId: string; version: number; value: string } | null>(null)
  const [captionSaving, setCaptionSaving] = useState(false)

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
        if (res.data) {
          setDiary((prev) => (reset ? res.data.items : [...prev, ...res.data.items]))
          setDiaryCursor(res.data.nextCursor ?? null)
          setDiaryHasMore(Boolean(res.data.hasMore))
        }
      } catch {
        // 后台分页加载失败保留已有列表
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
      if (res.data) {
        // R18：保留 caption/visibility/version，供编辑 PATCH 做 CAS
        setAlbum(res.data)
      }
    } catch {
      // 相册后台加载失败保留已有列表
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
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '重试未成功，稍后再试'), icon: 'none' })
    }
  }, [petId, loadAlbum])

  const loadMemories = useCallback(async () => {
    if (!petId) return
    setMemoriesLoading(true)
    try {
      const { data: res } = await petCompanionApi.listMemories(petId)
      setMemories(res.data ?? [])
    } catch {
      // 记忆列表后台加载失败保持空态
    } finally {
      setMemoriesLoading(false)
    }
  }, [petId])

  const loadPrefs = useCallback(async () => {
    try {
      const { data: res } = await petCompanionApi.getNotifyPrefs()
      if (res.data) setPrefs(res.data)
    } catch {
      // 展示型数据
    }
  }, [])

  // PET-13/T34：previewUrl 短效签名——相册页签期间 45 秒静默刷新防裂图
  useEffect(() => {
    if (!petId || tab !== 'album') return
    const timer = setInterval(() => void loadAlbum(), 45_000)
    return () => {
      if (timer) clearInterval(timer)
    }
  }, [petId, tab, loadAlbum])

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
      await petCompanionApi.uploadAlbumAsset(petId, fileId)
      Taro.showToast({ title: '已上传（默认仅自己可见）', icon: 'success' })
      loadAlbum()
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '上传失败（≤5MB，每用户 100 张）'), icon: 'none' })
    } finally {
      setUploading(false)
    }
  }

  /** R18：日记可见性 CAS 更新（expectedVersion 用列表回传版本；409 冲突提示刷新） */
  const changeDiaryVisibility = async (entry: PetDiaryEntry) => {
    if (!petId) return
    try {
      const chosen = await Taro.showActionSheet({ itemList: VISIBILITY_OPTIONS.map((item) => item.label) })
      const visibility = VISIBILITY_OPTIONS[chosen.tapIndex]?.value
      if (!visibility || visibility === entry.visibility) return
      setDiaryPatchingId(entry.id)
      await petApi.updateDiaryVisibility(petId, entry.id, {
        visibility,
        expectedVersion: entry.version,
      })
      Taro.showToast({ title: '可见性已更新', icon: 'success' })
      await loadDiary(true)
    } catch (err) {
      const code = (err as { code?: string }).code
      if (code && code !== 'ERR_CANCEL') {
        Taro.showToast({
          title: code === 'OPTIMISTIC_LOCK_CONFLICT' || code === 'VERSION_CONFLICT'
            ? '内容已被修改，请刷新后重试'
            : errorMessage(err, '更新失败，请稍后再试'),
          icon: 'none',
        })
      }
    } finally {
      setDiaryPatchingId(null)
    }
  }

  /** R18：相册说明保存（行内编辑；PATCH 带 expectedVersion） */
  const saveCaptionEdit = async () => {
    if (!petId || !captionEdit) return
    setCaptionSaving(true)
    try {
      await petApi.updateAlbumAsset(petId, captionEdit.assetId, {
        caption: captionEdit.value.trim(),
        expectedVersion: captionEdit.version,
      })
      Taro.showToast({ title: '说明已更新', icon: 'success' })
      setCaptionEdit(null)
      await loadAlbum()
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '保存失败，请刷新后重试'), icon: 'none' })
    } finally {
      setCaptionSaving(false)
    }
  }

  /** R18：相册编辑入口：编辑说明（行内）或修改可见性（操作表）；version 缺失拒绝猜测 */
  const editAlbumAsset = async (asset: PetAlbumAssetView) => {
    if (!petId || typeof asset.version !== 'number') return
    try {
      const action = await Taro.showActionSheet({ itemList: ['编辑说明', '修改可见性'] })
      if (action.tapIndex === 0) {
        setCaptionEdit({ assetId: asset.assetId, version: asset.version, value: asset.caption ?? '' })
        return
      }
      const chosen = await Taro.showActionSheet({ itemList: VISIBILITY_OPTIONS.map((item) => item.label) })
      const visibility = VISIBILITY_OPTIONS[chosen.tapIndex]?.value
      if (!visibility || visibility === asset.visibility) return
      await petApi.updateAlbumAsset(petId, asset.assetId, {
        visibility,
        expectedVersion: asset.version,
      })
      Taro.showToast({ title: '可见性已更新', icon: 'success' })
      await loadAlbum()
    } catch (err) {
      const code = (err as { code?: string }).code
      if (code && code !== 'ERR_CANCEL') {
        Taro.showToast({ title: errorMessage(err, '更新失败，请稍后再试'), icon: 'none' })
      }
    }
  }

  const saveMemoryEdit = async (memory: PetMemory) => {
    if (!petId || !editValue.trim()) {
      Taro.showToast({ title: '记忆内容不能为空', icon: 'none' })
      return
    }
    try {
      await petCompanionApi.editMemory(petId, memory.id, { value: editValue.trim() })
      Taro.showToast({ title: '已更新（编辑优先于自动抽取）', icon: 'success' })
      setEditId(null)
      loadMemories()
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '保存失败，请稍后再试'), icon: 'none' })
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
          {!diaryEnabled ? (
            <View className={styles.memoryEmpty}>
              <Text>{FEATURE_DISABLED_NOTE.diary}</Text>
            </View>
          ) : diary.length === 0 && !diaryLoading ? (
            <View className={styles.memoryEmpty}>
              <Text>还没有日记，和宠物互动后会自动记录哦</Text>
            </View>
          ) : (
            diary.map((entry) => (
              <View key={entry.id} className={styles.memoryCard}>
                <View className={styles.memoryCardHead}>
                  <Text
                    className={entry.visibility === 'PUBLIC' ? styles.memoryPublic : styles.memoryPrivate}
                    onClick={diaryPatchingId === entry.id ? undefined : () => void changeDiaryVisibility(entry)}
                  >
                    {visibilityLabel(entry.visibility)}
                    {' · 修改'}
                  </Text>
                  <Text className={styles.memoryTime}>{formatDateTime(entry.createdAt)}</Text>
                </View>
                <Text className={styles.memoryContent}>{entry.content}</Text>
              </View>
            ))
          )}
          {diaryHasMore && diaryEnabled && (
            <Button size='mini' loading={diaryLoading} onClick={() => loadDiary(false)}>
              加载更多
            </Button>
          )}
        </View>
      ) : tab === 'album' ? (
        <View>
          {!albumEnabled ? (
            <View className={styles.memoryEmpty}>
              <Text>{FEATURE_DISABLED_NOTE.album}</Text>
            </View>
          ) : (
            <>
              <Button size='mini' type='primary' loading={uploading} onClick={handleUpload}>
                上传照片（≤5MB，每用户 100 张）
              </Button>
              {captionEdit && (
                <View className={styles.memoryCard}>
                  <Text className={styles.memoryKey}>编辑照片说明（{captionEdit.assetId}）</Text>
                  <Textarea
                    className={styles.memoryEditInput}
                    value={captionEdit.value}
                    maxlength={200}
                    onInput={(e) => setCaptionEdit((prev) => prev ? { ...prev, value: e.detail.value } : prev)}
                  />
                  <View className={styles.memoryActions}>
                    <Button size='mini' type='primary' loading={captionSaving} onClick={() => void saveCaptionEdit()}>
                      保存
                    </Button>
                    <Button size='mini' disabled={captionSaving} onClick={() => setCaptionEdit(null)}>
                      取消
                    </Button>
                  </View>
                </View>
              )}
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
                    {asset.bindStatus === 'BOUND' && typeof asset.version === 'number' ? (
                      <Text
                        className={styles.albumDelete}
                        onClick={() => void editAlbumAsset(asset)}
                      >
                        编辑{asset.visibility ? ` · ${visibilityLabel(asset.visibility)}` : ''}
                      </Text>
                    ) : null}
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
                        petCompanionApi.deleteAlbumAsset(petId, asset.assetId)
                          .then(() => {
                            Taro.showToast({ title: '已删除', icon: 'none' })
                            loadAlbum()
                          })
                          .catch((err) => Taro.showToast({ title: errorMessage(err, '删除失败'), icon: 'none' }))
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
            </>
          )}
        </View>
      ) : tab === 'memories' ? (
        <View>
          <View className={styles.memoryActions}>
            <Button
              size='mini'
              onClick={() =>
                petId &&
                petCompanionApi.setMemorySettings(petId, { extract: true, use: true })
                  .then(() => {
                    Taro.showToast({ title: '记忆开关已保存', icon: 'none' })
                  })
                  .catch((err) => Taro.showToast({ title: errorMessage(err, '操作失败'), icon: 'none' }))
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
                  .catch((err) => Taro.showToast({ title: errorMessage(err, '操作失败'), icon: 'none' }))
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
                    petCompanionApi.clearMemories(petId)
                      .then(() => {
                        Taro.showToast({ title: '已清空', icon: 'none' })
                        loadMemories()
                      })
                      .catch((err) => Taro.showToast({ title: errorMessage(err, '操作失败'), icon: 'none' }))
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
                        petCompanionApi.deleteMemory(petId, memory.id)
                          .then(() => {
                            Taro.showToast({ title: '已删除', icon: 'none' })
                            loadMemories()
                          })
                          .catch((err) => Taro.showToast({ title: errorMessage(err, '删除失败'), icon: 'none' }))
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
                  .catch((err) => Taro.showToast({ title: errorMessage(err, '操作失败'), icon: 'none' }))
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
                  .catch((err) => Taro.showToast({ title: errorMessage(err, '操作失败'), icon: 'none' }))
              }}
            />
          </View>
          <Text className={styles.memoryHint}>仅作用于日常问候类主动消息；重要业务通知不受影响。</Text>
        </View>
      )}
    </View>
  )
}
