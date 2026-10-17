// R30：自 app/pet.tsx 原样拆出（行为不变）；共享常量/基元在 ../shared
import { useCallback, useEffect, useState } from 'react'
import { ActivityIndicator, Alert, Image, Switch, Text, TextInput, TouchableOpacity, View } from 'react-native'
import { useTheme } from '@/hooks/use-theme-context'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { PetCreamTheme } from '@/constants/pet-cream'
import { fileApi, createUploadForm } from '@/api/file'
import { errorMessage } from '@/utils/request'
import * as ImagePicker from 'expo-image-picker'
import { petApi, type PetAnniversary, type PetPersona, type PetFriendFeedItem, type PetDiaryPage, type PetDiaryEntry, type PetMemory, type PetNotifyPref, type PetOnboardingProgress, type PetAlbumAssetView, type PetContentVisibility } from '@/api/pet'
import { ChipButton } from '../shared'

// ==================== 陪伴面板（方案 B：纪念日/人设卡/好友动态/日记·相册/记忆/引导与通知） ====================

type CompanionTab = 'anniversary' | 'persona' | 'feed' | 'diary' | 'memory' | 'settings'

const COMPANION_TABS: { key: CompanionTab; label: string }[] = [
  { key: 'anniversary', label: '纪念日' },
  { key: 'persona', label: '人设卡' },
  { key: 'feed', label: '动态' },
  { key: 'diary', label: '日记·相册' },
  { key: 'memory', label: '记忆' },
  { key: 'settings', label: '引导·通知' },
]

const MEMORY_TYPE_LABEL: Record<string, string> = { FAVORITE: '喜好', HABIT: '习惯', FACT: '事实' }
const FEED_EVENT_LABEL: Record<string, string> = {
  LEVEL_UP: '升级', WORK_COMPLETED: '打工完成', STUDY_COMPLETED: '读书完成', BATTLE_WIN: '对战获胜',
}

function fmtCompanionSeconds(seconds: number): string {
  const total = Math.max(0, Math.round(seconds))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  if (h > 0) return `${h} 小时 ${m} 分`
  return m > 0 ? `${m} 分钟` : `${total} 秒`
}

export function CompanionPanel(
  { petId, onRefresh, diaryEnabled = true, albumEnabled = true }: {
    petId: number | string
    onRefresh: () => void
    /** R18：bootstrap.capabilities.diary —— 关闭时显示不可用说明而非假入口 */
    diaryEnabled?: boolean
    /** R18：bootstrap.capabilities.albumBinding */
    albumEnabled?: boolean
  },
) {
  const colors = { ...useTheme(), ...PetCreamTheme }
  const [tab, setTab] = useState<CompanionTab>('anniversary')
  const [pending, setPending] = useState<string | null>(null)
  const [ann, setAnn] = useState<PetAnniversary | null>(null)
  const [persona, setPersona] = useState<PetPersona | null>(null)
  const [feed, setFeed] = useState<PetFriendFeedItem[]>([])
  const [feedUnread, setFeedUnread] = useState(0)
  const [diary, setDiary] = useState<PetDiaryPage | null>(null)
  const [diaryCursor, setDiaryCursor] = useState<string | null>(null)
  const [assets, setAssets] = useState<Array<{ id: number | string; url: string; diaryEntryId: number | string | null; bindStatus?: string; version?: number; caption?: string | null; visibility?: PetContentVisibility | null }>>([])
  // R18：相册说明行内编辑（PATCH 带 expectedVersion）
  const [captionEdit, setCaptionEdit] = useState<{ assetId: string; version: number; value: string } | null>(null)
  const [memories, setMemories] = useState<PetMemory[]>([])
  const [editId, setEditId] = useState<number | null>(null)
  const [editValue, setEditValue] = useState('')
  // 记忆开关草稿：服务端无读取端点，默认取库表默认值 1/1（界面已注明）
  const [extractDraft, setExtractDraft] = useState(true)
  const [useDraft, setUseDraft] = useState(true)
  const [onboarding, setOnboarding] = useState<PetOnboardingProgress | null>(null)
  const [pref, setPref] = useState<PetNotifyPref | null>(null)

  const run = useCallback(async <T,>(
    key: string,
    task: () => Promise<{ data: { data?: T } }>,
  ): Promise<{ data?: T } | null> => {
    setPending(key)
    try {
      const { data: res } = await task()
      return res
    } catch (error) {
      Alert.alert('暂时不能这么做', errorMessage(error, '请稍后再试'))
      return null
    } finally {
      setPending(null)
    }
  }, [])

  const loadAnn = useCallback(async () => {
    try {
      const { data: res } = await petApi.getAnniversaries()
      setAnn(res.data)
    } catch {
      // 展示型数据：失败保持空态
    }
  }, [])

  const loadPersona = useCallback(async () => {
    try {
      const { data: res } = await petApi.getChatPersona()
      setPersona(res.data)
    } catch {
      // 展示型数据：失败保持空态
    }
  }, [])

  const loadFeed = useCallback(async () => {
    try {
      const [{ data: list }, { data: unread }] = await Promise.all([
        petApi.listFriendFeed({ size: 20 }),
        petApi.getFriendFeedUnreadCount(),
      ])
      setFeed(list.data ?? [])
      setFeedUnread(Number(unread.data ?? 0))
    } catch {
      // 展示型数据：忽略
    }
  }, [])

  const loadDiary = useCallback(async (reset: boolean) => {
    try {
      const { data: res } = await petApi.listDiary(petId, {
        cursor: reset ? undefined : (diaryCursor ?? undefined),
        size: 20,
      })
      setDiary((prev) => reset
        ? res.data
        : { items: [...(prev?.items ?? []), ...res.data.items], nextCursor: res.data.nextCursor, hasMore: res.data.hasMore })
      setDiaryCursor(res.data.nextCursor)
    } catch {
      // 展示型数据：失败保留已加载页
    }
  }, [diaryCursor, petId])

  const loadMemories = useCallback(async () => {
    try {
      const { data: res } = await petApi.listMemories(petId)
      setMemories(res.data ?? [])
    } catch {
      // 展示型数据：失败保持空态
    }
  }, [petId])

  const loadSettings = useCallback(async () => {
    try {
      const [{ data: prefRes }, { data: onboardingRes }] = await Promise.all([
        petApi.getNotifyPrefs(),
        petApi.getOnboarding(),
      ])
      setPref(prefRes.data)
      setOnboarding(onboardingRes.data)
    } catch {
      // 展示型数据：忽略
    }
  }, [])

  useEffect(() => {
    // 各页签懒加载：切换到对应页签时拉取一次（数据量小，不做缓存失效）
    async function loadActiveTab() {
      if (tab === 'anniversary' && !ann) await loadAnn()
      if (tab === 'persona' && !persona) await loadPersona()
      if (tab === 'feed') await loadFeed()
      if (tab === 'diary' && !diary) await loadDiary(true)
      if (tab === 'memory') await loadMemories()
      if (tab === 'settings') await loadSettings()
    }
    void loadActiveTab()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab])

  const readFeed = useCallback(async () => {
    const res = await run('feed', () => petApi.markFriendFeedRead())
    if (res) setFeedUnread(0)
  }, [run])

  /** PET-13/T31：独立相册列表（服务端权威；BINDING/FAILED 照实展示并给重试入口） */
  const loadAlbum = useCallback(async () => {
    if (!petId) return
    try {
      const { data: res } = await petApi.listAlbumAssets(petId)
      setAssets(res.data
        .filter((item: PetAlbumAssetView) => item.bindStatus !== 'DELETED')
        .map((item) => ({
          id: item.assetId,
          url: item.bindStatus === 'BOUND' ? (item.previewUrl ?? '') : '',
          diaryEntryId: item.diaryEntryId,
          bindStatus: item.bindStatus,
          // R18：保留版本/说明/可见性，供 PATCH CAS 编辑
          version: item.version,
          caption: item.caption,
          visibility: item.visibility,
        })))
    } catch {
      // 展示型数据：忽略
    }
  }, [petId])

  // PET-13/T34：previewUrl 为 60 秒短效签名——相册数据所在页签可见时每 45 秒静默刷新
  useEffect(() => {
    if (tab !== 'feed') return
    const timer = setInterval(() => void loadAlbum(), 45_000)
    return () => clearInterval(timer)
  }, [tab, loadAlbum])

  const uploadPhoto = useCallback(async (diaryEntryId?: number) => {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync()
    if (!perm.granted) {
      Alert.alert('需要相册权限', '请在系统设置里允许访问相册')
      return
    }
    const picked = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.8 })
    if (picked.canceled || !picked.assets?.[0]) return
    const asset = picked.assets[0]
    setPending('album')
    try {
      const form = await createUploadForm({ uri: asset.uri, name: asset.fileName || asset.uri.split('/').pop()?.split('?')[0] || 'upload.jpg', type: asset.mimeType || 'image/jpeg' })
      // PET-13/T31：相册资产上传即 PRIVATE（绑定要求 PRIVATE，缺省 PUBLIC 会导致绑定失败/隐私泄露）
      const { data: up } = await fileApi.upload(form, 'PRIVATE')
      if (!up.data) {
        Alert.alert('上传未成功', up.error?.message ?? '请稍后再试')
        return
      }
      const res = await run('album', () => petApi.uploadAlbumAsset(petId, up.data!.fileId, diaryEntryId))
      if (res?.data) {
        setAssets((prev) => [{ id: res.data!.id, url: up.data!.url ?? asset.uri, diaryEntryId: res.data!.diaryEntryId }, ...prev])
        // PET-13/T31：以服务端相册列表校准（重启/刷新后仍在，本地数组不再是唯一事实）
        void loadAlbum()
        Alert.alert('成功', '照片已存入相册')
      }
    } catch {
      Alert.alert('上传未成功', '请稍后再试')
    } finally {
      setPending(null)
    }
  }, [petId, run, loadAlbum])

  /** PET-13/T32：BINDING/FAILED 条目重试绑定（远端幂等；成功后刷新列表） */
  const retryAssetBinding = useCallback(async (assetId: number | string) => {
    try {
      await petApi.retryAlbumAsset(petId, assetId)
      await loadAlbum()
    } catch {
      Alert.alert('重试未成功', '稍后再试或删除该条目')
    }
  }, [petId, loadAlbum])

  const removeAsset = useCallback(async (assetId: number | string) => {
    const res = await run(`album-del:${assetId}`, () => petApi.deleteAlbumAsset(petId, assetId))
    if (res) setAssets((prev) => prev.filter((item) => item.id !== assetId))
  }, [petId, run])

  /** R18：日记可见性选项（与后端 DiaryVisibility 三态一致） */
  const VISIBILITY_CHOICES: Array<{ label: string; value: PetContentVisibility }> = [
    { label: '公开', value: 'PUBLIC' },
    { label: '仅主人可见', value: 'OWNER_ONLY' },
    { label: '私密', value: 'PRIVATE' },
  ]
  const visibilityText = (value: string | null | undefined) =>
    VISIBILITY_CHOICES.find((item) => item.value === value)?.label ?? '私密'

  /** R18：日记可见性 CAS 更新（expectedVersion 来自列表；409 冲突提示刷新） */
  const changeDiaryVisibility = useCallback(async (entry: PetDiaryEntry) => {
    Alert.alert(
      '修改可见性',
      '选择该日记的可见范围',
      VISIBILITY_CHOICES.map((choice) => ({
        text: choice.label,
        onPress: async () => {
          if (choice.value === entry.visibility) return
          const res = await run(`diary-vis:${entry.id}`, () => petApi.updateDiaryVisibility(petId, entry.id, {
            visibility: choice.value,
            expectedVersion: entry.version,
          }))
          if (res) void loadDiary(true)
        },
      })),
    )
  }, [loadDiary, petId, run])

  /** R18：相册编辑入口（说明行内/可见性操作表；version 缺失拒绝猜测） */
  const editAsset = useCallback((asset: typeof assets[number]) => {
    if (typeof asset.version !== 'number') return
    Alert.alert(
      '编辑照片',
      undefined,
      [
        { text: '编辑说明', onPress: () => setCaptionEdit({ assetId: String(asset.id), version: asset.version as number, value: asset.caption ?? '' }) },
        {
          text: '修改可见性',
          onPress: () => Alert.alert(
            '修改可见性',
            undefined,
            VISIBILITY_CHOICES.map((choice) => ({
              text: choice.label,
              onPress: async () => {
                if (choice.value === asset.visibility) return
                const res = await run(`album-vis:${asset.id}`, () => petApi.updateAlbumAsset(petId, asset.id, {
                  visibility: choice.value,
                  expectedVersion: asset.version as number,
                }))
                if (res) void loadAlbum()
              },
            })),
          ),
        },
        { text: '取消', style: 'cancel' },
      ],
    )
  }, [loadAlbum, petId, run])

  /** R18：保存照片说明 */
  const saveCaptionEdit = useCallback(async () => {
    if (!captionEdit) return
    const res = await run('album-caption', () => petApi.updateAlbumAsset(petId, captionEdit.assetId, {
      caption: captionEdit.value.trim(),
      expectedVersion: captionEdit.version,
    }))
    if (res) {
      setCaptionEdit(null)
      void loadAlbum()
    }
  }, [captionEdit, loadAlbum, petId, run])

  const saveMemory = useCallback(async (memory: PetMemory) => {
    const value = editValue.trim()
    if (!value) {
      Alert.alert('提示', '记忆内容不能为空')
      return
    }
    const res = await run(`memory:${memory.id}`,
      () => petApi.editMemory(petId, memory.id, { value: value }))
    if (res) {
      setEditId(null)
      void loadMemories()
    }
  }, [editValue, loadMemories, petId, run])

  const removeMemory = useCallback(async (memoryId: number) => {
    const res = await run(`memory-del:${memoryId}`, () => petApi.deleteMemory(petId, memoryId))
    if (res) void loadMemories()
  }, [loadMemories, petId, run])

  const clearAllMemories = useCallback(() => {
    Alert.alert('清空全部记忆？', '所有结构化记忆会被移除（含自动抽取的），且不可恢复。', [
      { text: '再想想', style: 'cancel' },
      {
        text: '清空',
        style: 'destructive',
        onPress: () => {
          void (async () => {
            const res = await run('memory-clear', () => petApi.clearMemories(petId))
            if (res) void loadMemories()
          })()
        },
      },
    ])
  }, [loadMemories, petId, run])

  const toggleMemorySetting = useCallback(async (extract: boolean, use: boolean) => {
    const res = await run('memory-setting', () => petApi.setMemorySettings(petId, { extract, use }))
    if (res) Alert.alert('成功', '记忆设置已更新')
  }, [petId, run])

  const togglePref = useCallback(async (next: { muteDailyGreeting: boolean; dailyGreetingEnabled: boolean }) => {
    // §7.2：带当前 version 做 CAS，冲突由错误链路提示
    const res = await run('pref', () => petApi.updateNotifyPrefs({ ...next, expectedVersion: pref?.version }))
    if (res?.data && pref) {
      setPref({ ...pref, ...res.data })
    }
  }, [pref, run])

  const skipOnboarding = useCallback(async () => {
    const res = await run('onboarding', () => petApi.skipOnboarding())
    if (res) {
      const { data: onboardingRes } = await petApi.getOnboarding()
      setOnboarding(onboardingRes.data)
      onRefresh()
    }
  }, [onRefresh, run])

  const milestoneText = (daysToGo: number): string => {
    if (daysToGo === 0) return '就是今天！'
    return daysToGo > 0 ? `还有 ${daysToGo} 天` : `已达成 ${-daysToGo} 天`
  }

  const cardStyle = {
    padding: Spacing.sm, borderRadius: 16, borderWidth: 1, borderColor: 'rgba(200, 155, 90, 0.35)',
    backgroundColor: '#FFFDF8', gap: 4,
  } as const

  return (
    <View style={{ gap: Spacing.sm }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.xs }}>
        {COMPANION_TABS.map((item) => (
          <TouchableOpacity
            key={item.key}
            onPress={() => setTab(item.key)}
            style={{
              paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999,
              backgroundColor: tab === item.key ? colors.primary : colors.bgBase,
              borderWidth: 1, borderColor: tab === item.key ? colors.primary : colors.border,
            }}
          >
            <Text style={{ color: tab === item.key ? '#fff' : colors.textSecondary, fontSize: FontSize.xs }}>{item.label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {tab === 'anniversary' && (
        !ann ? <ActivityIndicator color={colors.primary} /> : (
          <View style={{ gap: Spacing.sm }}>
            <View style={[cardStyle, { alignItems: 'center', paddingVertical: Spacing.md }]}>
              <Text style={{ fontSize: 26, color: colors.text, fontWeight: '600' }}>
                {`${ann.adoptionDays} 天`}
              </Text>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                {`领养至今 · 连续陪伴 ${ann.companionStreak} 天`}
              </Text>
            </View>
            <View style={cardStyle}>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                {`当前里程碑：${ann.currentMilestone ? `${ann.currentMilestone.title}（${milestoneText(ann.currentMilestone.daysToGo)}）` : '暂无'}`}
              </Text>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                {`下一个：${ann.nextMilestone ? `${ann.nextMilestone.title}（${milestoneText(ann.nextMilestone.daysToGo)}）` : '全部达成'}`}
              </Text>
            </View>
          </View>
        )
      )}

      {tab === 'persona' && (
        !persona ? <ActivityIndicator color={colors.primary} /> : (
          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{`${persona.name} 的人设卡`}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{persona.personalityText}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
              {`职业：${persona.careerName ?? '无业游民'} · 亲密度：${persona.intimacyLevelName ?? `Lv.${persona.intimacyLevel}`}`}
            </Text>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
              {`${persona.ownerTitle ? `它叫你「${persona.ownerTitle}」` : '它还没学会怎么叫你'}${persona.phrase ? ` · 口头禅：“${persona.phrase}”` : ''}`}
            </Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>与注入 AI 的身份信息同源（F8）</Text>
          </View>
        )
      )}

      {tab === 'feed' && (
        <View style={{ gap: Spacing.xs }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>{`未读 ${feedUnread} 条`}</Text>
            {feedUnread > 0 && <ChipButton label="标记已读" onPress={() => void readFeed()} />}
          </View>
          {feed.length === 0 ? (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>暂无动态</Text>
          ) : feed.map((item) => (
            <View key={item.feedId} style={cardStyle}>
              <Text style={{ color: colors.primary, fontSize: 10 }}>
                {FEED_EVENT_LABEL[item.eventType] ?? item.eventType}
              </Text>
              <Text style={{ color: colors.text, fontSize: FontSize.xs }}>{item.text}</Text>
              <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                {item.createdAt ? item.createdAt.slice(5, 16).replace('T', ' ') : ''}
              </Text>
            </View>
          ))}
        </View>
      )}

      {tab === 'diary' && (
        <View style={{ gap: Spacing.sm }}>
          {!diaryEnabled ? (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>成长日记功能暂未开放</Text>
          ) : (
          <View style={{ gap: Spacing.xs }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>成长日记</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>由宠物的大事自动生成，客户端不能代写</Text>
            {(diary?.items.length ?? 0) === 0 ? (
              <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有日记，陪它做点什么吧</Text>
            ) : diary!.items.map((entry) => (
              <View key={entry.id} style={cardStyle}>
                <TouchableOpacity onPress={() => void changeDiaryVisibility(entry)}>
                  <Text style={{ color: colors.primary, fontSize: 10 }}>
                    {`${entry.type} · ${visibilityText(entry.visibility)}（修改）${entry.assetIds?.length ? ` · 含 ${entry.assetIds.length} 张照片` : ''} · ${entry.createdAt.slice(5, 16).replace('T', ' ')}`}
                  </Text>
                </TouchableOpacity>
                <Text style={{ color: colors.text, fontSize: FontSize.xs, lineHeight: 18 }}>{entry.content}</Text>
              </View>
            ))}
            {diary?.hasMore && (
              <ChipButton label="加载更多" onPress={() => void loadDiary(false)} />
            )}
          </View>
          )}

          {!albumEnabled ? (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>相册绑定功能暂未开放</Text>
          ) : (
          <View style={{ gap: Spacing.xs }}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>相册</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
              每只宠物 100 张；照片与说明服务端持久化，BOUND 后可编辑说明与可见性
            </Text>
            <ChipButton
              label={pending === 'album' ? '上传中…' : '上传照片'}
              disabled={pending === 'album'}
              onPress={() => void uploadPhoto()}
            />
            {captionEdit && (
              <View style={cardStyle}>
                <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>
                  {`编辑照片说明（${captionEdit.assetId}）`}
                </Text>
                <TextInput
                  value={captionEdit.value}
                  onChangeText={(value) => setCaptionEdit((prev) => prev ? { ...prev, value } : prev)}
                  maxLength={200}
                  placeholder="给这张照片写句说明"
                  placeholderTextColor={colors.textTertiary}
                  style={{
                    borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm,
                    color: colors.text, padding: Spacing.xs, fontSize: FontSize.xs,
                  }}
                />
                <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
                  <ChipButton label="保存" primary disabled={pending === 'album-caption'} onPress={() => void saveCaptionEdit()} />
                  <ChipButton label="取消" onPress={() => setCaptionEdit(null)} />
                </View>
              </View>
            )}
            {assets.length > 0 && assets.map((item) => (
              <View key={item.id} style={[cardStyle, { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }]}>
                {item.bindStatus === 'BOUND' ? (
                  <Image
                    source={{ uri: item.url }}
                    style={{ width: 64, height: 64, borderRadius: BorderRadius.sm, backgroundColor: colors.bgBase }}
                  />
                ) : (
                  <View style={{ width: 64, height: 64, borderRadius: BorderRadius.sm, backgroundColor: colors.bgBase, alignItems: 'center', justifyContent: 'center' }}>
                    <Text style={{ color: colors.textTertiary, fontSize: 9, textAlign: 'center' }}>
                      {item.bindStatus === 'FAILED' ? '绑定失败' : '绑定中'}
                    </Text>
                  </View>
                )}
                <View style={{ flex: 1, gap: 2 }}>
                  <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                    {item.caption ? item.caption : (item.diaryEntryId ? `日记 #${item.diaryEntryId}` : '未挂日记')}
                    {` · ${visibilityText(item.visibility)}`}
                  </Text>
                  {item.bindStatus === 'BOUND' && typeof item.version === 'number' ? (
                    <ChipButton label="编辑" onPress={() => editAsset(item)} />
                  ) : null}
                  {item.bindStatus && item.bindStatus !== 'BOUND' ? (
                    <ChipButton label={item.bindStatus === 'FAILED' ? '重试绑定' : '绑定处理中…'}
                      onPress={() => item.bindStatus === 'FAILED' ? void retryAssetBinding(item.id) : undefined} />
                  ) : null}
                  <ChipButton label="删除" onPress={() => void removeAsset(item.id)} />
                </View>
              </View>
            ))}
          </View>
          )}
        </View>
      )}

      {tab === 'memory' && (
        <View style={{ gap: Spacing.sm }}>
          <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
            {`共 ${memories.length} 条 · USER 编辑优先于 AUTO 抽取`}
          </Text>
          {memories.length === 0 ? (
            <Text style={{ color: colors.textTertiary, fontSize: FontSize.xs }}>还没有记忆，多陪它聊聊就会慢慢积累</Text>
          ) : memories.map((item) => (
            <View key={item.id} style={cardStyle}>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}>
                <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>{item.memoryKey}</Text>
                <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                  {`${MEMORY_TYPE_LABEL[item.memoryType] ?? item.memoryType} · ${item.source === 'USER' ? '我写的' : '自动抽取'} · 重要度 ${item.importance}`}
                </Text>
              </View>
              {editId === item.id ? (
                <View style={{ gap: Spacing.xs }}>
                  <TextInput
                    value={editValue}
                    onChangeText={setEditValue}
                    maxLength={200}
                    placeholder="记忆内容"
                    placeholderTextColor={colors.textTertiary}
                    style={{
                      borderWidth: 1, borderColor: colors.border, borderRadius: BorderRadius.sm,
                      color: colors.text, padding: Spacing.xs, fontSize: FontSize.xs,
                    }}
                  />
                  <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
                    <ChipButton label="保存" primary disabled={pending === `memory:${item.id}`} onPress={() => void saveMemory(item)} />
                    <ChipButton label="取消" onPress={() => setEditId(null)} />
                  </View>
                </View>
              ) : (
                <>
                  <Text style={{ color: colors.text, fontSize: FontSize.xs, lineHeight: 18 }}>{item.memoryValue}</Text>
                  <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
                    {`置信度 ${item.confidence} · 更新于 ${item.updatedAt.slice(5, 16).replace('T', ' ')}`}
                  </Text>
                  <View style={{ flexDirection: 'row', gap: Spacing.xs }}>
                    <ChipButton label="编辑" onPress={() => { setEditId(item.id); setEditValue(item.memoryValue) }} />
                    <ChipButton label="删除" disabled={pending === `memory-del:${item.id}`} onPress={() => void removeMemory(item.id)} />
                  </View>
                </>
              )}
            </View>
          ))}

          {memories.length > 0 && (
            <ChipButton label="清空全部记忆" disabled={pending === 'memory-clear'} onPress={clearAllMemories} />
          )}

          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>记忆开关</Text>
            <Text style={{ color: colors.textTertiary, fontSize: 10 }}>
              两开关相互独立（默认都开启）；服务端没有查询端点，保存后即以本次设置为准
            </Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>自动抽取</Text>
              <Switch value={extractDraft} onValueChange={setExtractDraft} />
            </View>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>注入上下文</Text>
              <Switch value={useDraft} onValueChange={setUseDraft} />
            </View>
            <ChipButton label="保存记忆开关" primary disabled={pending === 'memory-setting'} onPress={() => void toggleMemorySetting(extractDraft, useDraft)} />
          </View>
        </View>
      )}

      {tab === 'settings' && (
        <View style={{ gap: Spacing.sm }}>
          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>通知偏好</Text>
            {pref ? (
              <>
                <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                    {`每日问候（${pref.dailyGreetingEnabled ? '已开启' : '已关闭'}）`}
                  </Text>
                  <Switch
                    value={pref.dailyGreetingEnabled}
                    onValueChange={(value) => void togglePref({ muteDailyGreeting: pref.muteDailyGreeting, dailyGreetingEnabled: value })}
                  />
                </View>
                <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                    {`免打扰（${pref.muteDailyGreeting ? '已开启' : '正常提醒'}）`}
                  </Text>
                  <Switch
                    value={pref.muteDailyGreeting}
                    onValueChange={(value) => void togglePref({ muteDailyGreeting: value, dailyGreetingEnabled: pref.dailyGreetingEnabled })}
                  />
                </View>
              </>
            ) : <ActivityIndicator color={colors.primary} />}
          </View>

          <View style={cardStyle}>
            <Text style={{ color: colors.text, fontSize: FontSize.sm, fontWeight: '600' }}>新手引导</Text>
            {onboarding ? (
              <>
                <Text style={{ color: colors.textSecondary, fontSize: FontSize.xs }}>
                  {`进度 ${onboarding.currentStep}/${onboarding.totalSteps}${onboarding.completed ? ' · 已完成' : ''}`}
                </Text>
                <ChipButton
                  label="跳过引导"
                  disabled={!onboarding.skippable || onboarding.completed || pending === 'onboarding'}
                  onPress={() => void skipOnboarding()}
                />
              </>
            ) : <ActivityIndicator color={colors.primary} />}
          </View>
        </View>
      )}
    </View>
  )
}

// ==================== R19 面板：协作 PVE / 赛季通行证 / 实物商品双倍喂食 ====================

/** R19 写操作幂等键（去横线 UUID，项目惯例：payment/starlight-transfer 同口径） */
