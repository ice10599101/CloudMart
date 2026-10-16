import { useEffect, useState } from 'react'
import {
  ActivityIndicator,
  Image,
  ScrollView,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  Alert,
} from 'react-native'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import * as ImagePicker from 'expo-image-picker'
import {
  softwarePublishApi,
  softwareApi,
  textToDescriptionHtml,
  descriptionHtmlToText,
  type SoftwareEditor,
  type SoftwareEditorRelease,
  type SoftwareEditorSource,
  type SoftwareCategory,
  type SoftwareContact,
} from '@/api/software'
import { fileApi } from '@/api/file'
import { useTheme } from '@/hooks/use-theme-context'
import { FontSize, Spacing, BorderRadius } from '@/constants/theme'

const LICENSES = ['FREE', 'OPEN_SOURCE', 'TRIAL', 'COMMERCIAL'] as const
const LICENSE_LABEL: Record<string, string> = {
  FREE: '免费', OPEN_SOURCE: '开源', TRIAL: '试用', COMMERCIAL: '商业',
}
const ATTRIBUTIONS = ['ORIGINAL', 'AUTHORIZED', 'OFFICIAL_PUBLIC'] as const
const ATTRIBUTION_LABEL: Record<string, string> = {
  ORIGINAL: '原创', AUTHORIZED: '授权转载', OFFICIAL_PUBLIC: '官方公开',
}
const ACCESS_MODES = ['REGISTERED', 'COMMUNITY_LEVEL', 'ADMIN_PAID'] as const
const ACCESS_LABEL: Record<string, string> = {
  REGISTERED: '登录即可见', COMMUNITY_LEVEL: '社区等级门槛', ADMIN_PAID: '联系管理员付款',
}
const SOURCE_TYPES = ['NETDISK', 'MAGNET', 'TORRENT_URL'] as const
const SOURCE_TYPE_LABEL: Record<string, string> = {
  NETDISK: '网盘链接', MAGNET: '磁力链接', TORRENT_URL: '种子文件(https)',
}
const PLATFORMS = ['WINDOWS', 'MACOS', 'LINUX', 'ANDROID', 'IOS', 'CROSS_PLATFORM'] as const
const ARCHES = ['X64', 'X86', 'ARM64', 'UNIVERSAL', 'OTHER'] as const
const CHANNELS = ['STABLE', 'BETA'] as const

function emptySource(): SoftwareEditorSource {
  return {
    sourceType: 'NETDISK',
    platform: 'WINDOWS',
    arch: 'X64',
    label: '',
    url: '',
    extractionCode: null,
    archivePassword: null,
    sizeLabel: null,
  }
}

function emptyRelease(): SoftwareEditorRelease {
  return {
    versionName: '1.0.0',
    channel: 'STABLE',
    recommended: true,
    changelog: '',
    sources: [emptySource()],
  }
}

/** 简易下拉：循环切换选项（RN 原生 Picker 依赖不同，保持零依赖实现） */
function CyclePicker({
  options, labels, value, onChange,
}: {
  options: readonly string[]
  labels: Record<string, string>
  value: string
  onChange: (v: string) => void
}) {
  const idx = Math.max(options.indexOf(value), 0)
  return (
    <TouchableOpacity
      onPress={() => onChange(options[(idx + 1) % options.length])}
      style={{
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        backgroundColor: '#f4f6fa', borderRadius: BorderRadius.md, padding: Spacing.sm,
      }}
    >
      <Text style={{ fontSize: FontSize.sm, color: '#1c2333' }}>{labels[value] ?? value}</Text>
      <Text style={{ color: '#a8b0c0' }}>切换 ›</Text>
    </TouchableOpacity>
  )
}

export default function SoftwareEditScreen() {
  const insets = useSafeAreaInsets()
  const theme = useTheme()
  const params = useLocalSearchParams<{ id?: string }>()
  const softwareId = params.id || ''

  const [editor, setEditor] = useState<SoftwareEditor | null>(null)
  const [categories, setCategories] = useState<SoftwareCategory[]>([])
  const [contacts, setContacts] = useState<SoftwareContact[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const [name, setName] = useState('')
  const [summary, setSummary] = useState('')
  const [categoryId, setCategoryId] = useState<string | null>(null)
  const [licenseType, setLicenseType] = useState<string>('FREE')
  const [attributionType, setAttributionType] = useState<string>('ORIGINAL')
  const [developerName, setDeveloperName] = useState('')
  const [officialUrl, setOfficialUrl] = useState('')
  const [descriptionText, setDescriptionText] = useState('')
  const [accessMode, setAccessMode] = useState<string>('REGISTERED')
  const [minCommunityLevel, setMinCommunityLevel] = useState('1')
  const [accessContactId, setAccessContactId] = useState<string | null>(null)
  const [iconAssetId, setIconAssetId] = useState<string | null>(null)
  const [iconUrl, setIconUrl] = useState<string | null>(null)
  const [releases, setReleases] = useState<SoftwareEditorRelease[]>([emptyRelease()])
  const [reviewReason, setReviewReason] = useState<string | null>(null)

  useEffect(() => {
    if (!softwareId) return
    let cancelled = false
    setLoading(true)
    Promise.all([
      softwarePublishApi.workingRevision(softwareId),
      softwareApi.categories(),
      softwarePublishApi.contacts().catch(() => null),
    ])
      .then(([ed, cats, cts]) => {
        if (cancelled) return
        applyEditor(ed.data.data)
        setCategories(cats.data.data ?? [])
        setContacts(cts?.data.data ?? [])
      })
      .catch(() => Alert.alert('提示', '加载编辑器失败'))
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [softwareId])

  const applyEditor = (ed: SoftwareEditor) => {
    setEditor(ed)
    setName(ed.name || '')
    setSummary(ed.summary || '')
    setCategoryId(ed.categoryId)
    setLicenseType(ed.licenseType || 'FREE')
    setAttributionType(ed.attributionType || 'ORIGINAL')
    setDeveloperName(ed.developerName || '')
    setOfficialUrl(ed.officialUrl || '')
    setDescriptionText(descriptionHtmlToText(ed.descriptionHtml))
    setAccessMode(ed.accessMode || 'REGISTERED')
    setMinCommunityLevel(String(ed.minCommunityLevel ?? 1))
    setAccessContactId(ed.accessContactId)
    setIconAssetId(ed.iconAssetId)
    setIconUrl(ed.iconAssetId ? resolveIcon(ed.iconAssetId) : null)
    setReleases(
      ed.releases && ed.releases.length > 0
        ? ed.releases.map((r) => ({ ...r, sources: r.sources.length ? r.sources : [emptySource()] }))
        : [emptyRelease()],
    )
    if (ed.reviewStatus === 'REJECTED' && ed.reviewReason) setReviewReason(`退回原因：${ed.reviewReason}`)
  }

  const resolveIcon = (assetId: string) => `/file/assets/${assetId}/raw`

  const chooseIcon = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.8,
    })
    if (result.canceled || !result.assets?.[0]) return
    const asset = result.assets[0]
    const formData = new FormData()
    formData.append('file', {
      uri: asset.uri,
      name: 'icon.jpg',
      type: 'image/jpeg',
    } as any)
    try {
      const res = await fileApi.upload(formData, 'PUBLIC')
      const data = (res.data as any)?.data
      if (data?.fileId) {
        setIconAssetId(String(data.fileId))
        setIconUrl(data.url ?? null)
      }
    } catch {
      Alert.alert('提示', '图标上传失败')
    }
  }

  const buildSnapshot = () => ({
    name: name.trim(),
    summary: summary.trim(),
    categoryId,
    iconAssetId,
    descriptionHtml: textToDescriptionHtml(descriptionText.trim()),
    licenseType,
    developerName: developerName.trim() || null,
    officialUrl: officialUrl.trim() || null,
    attributionType,
    attributionNote: null,
    systemRequirements: null,
    languages: [],
    accessMode,
    minCommunityLevel: accessMode === 'COMMUNITY_LEVEL' ? Number(minCommunityLevel) || 1 : null,
    accessContactId: accessMode === 'ADMIN_PAID' ? accessContactId : null,
    accessNote: null,
    tagIds: [],
    screenshots: [],
    releases: releases.map((r, ri) => ({
      versionName: r.versionName.trim(),
      channel: r.channel,
      recommended: r.recommended ?? false,
      changelog: r.changelog?.trim() || null,
      displayOrder: ri + 1,
      sources: r.sources.map((s2, si) => ({
        sourceType: s2.sourceType,
        platform: s2.platform,
        arch: s2.arch,
        label: s2.label?.trim() || null,
        url: s2.url?.trim() || null,
        extractionCode: s2.extractionCode?.trim() || null,
        archivePassword: s2.archivePassword?.trim() || null,
        sizeLabel: s2.sizeLabel?.trim() || null,
        displayOrder: si + 1,
      })),
    })),
  })

  const validateForSubmit = (): string | null => {
    const n = name.trim()
    if (n.length < 2 || n.length > 80) return '软件名须为 2~80 字'
    const sm = summary.trim()
    if (sm.length < 10 || sm.length > 160) return '简介须为 10~160 字'
    if (!categoryId) return '请选择分类'
    if (!licenseType) return '请选择授权类型'
    if (descriptionText.trim().length === 0) return '请填写软件介绍'
    if (releases.length === 0) return '至少需要一个版本'
    for (const r of releases) {
      if (!r.versionName.trim()) return '版本号不能为空'
      for (const s2 of r.sources) {
        if (!s2.url?.trim()) return '来源链接不能为空'
        if (s2.sourceType === 'MAGNET' && !s2.url.toLowerCase().startsWith('magnet:?')) {
          return '磁力链接须以 magnet:? 开头'
        }
      }
    }
    if (accessMode === 'COMMUNITY_LEVEL' && !(Number(minCommunityLevel) >= 1)) return '请设置社区等级门槛'
    if (accessMode === 'ADMIN_PAID' && !accessContactId) return '请选择付款联系入口'
    return null
  }

  const doSave = async (): Promise<SoftwareEditor | null> => {
    if (!editor) return null
    const res = await softwarePublishApi.saveDraft(
      editor.softwareId, editor.revisionId, editor.lockVersion, buildSnapshot(),
    )
    const ed = res.data.data
    setEditor(ed)
    return ed
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      await doSave()
      Alert.alert('提示', '草稿已保存')
    } catch {
      Alert.alert('提示', '保存失败，请稍后再试')
    } finally {
      setSaving(false)
    }
  }

  const handleSubmit = () => {
    const problem = validateForSubmit()
    if (problem) {
      Alert.alert('提示', problem)
      return
    }
    Alert.alert('提交审核', '提交后进入人工审核，期间不可编辑（可撤回）。确认提交？', [
      { text: '取消', style: 'cancel' },
      {
        text: '确认',
        onPress: () => {
          void (async () => {
            setSaving(true)
            try {
              const saved = await doSave()
              if (!saved) return
              await softwarePublishApi.submit(saved.softwareId, saved.revisionId, saved.lockVersion)
              Alert.alert('提示', '已提交审核')
              router.replace('/software-mine?refreshed=1')
            } catch {
              Alert.alert('提示', '提交失败，请稍后再试')
            } finally {
              setSaving(false)
            }
          })()
        },
      },
    ])
  }

  const handleWithdraw = async () => {
    if (!editor) return
    setSaving(true)
    try {
      const res = await softwarePublishApi.withdraw(
        editor.softwareId, editor.revisionId, editor.lockVersion,
      )
      applyEditor(res.data.data)
      setReviewReason(null)
      Alert.alert('提示', '已撤回，可继续编辑')
    } catch {
      Alert.alert('提示', '撤回失败，请稍后再试')
    } finally {
      setSaving(false)
    }
  }

  const patchRelease = (idx: number, patch: Partial<SoftwareEditorRelease>) =>
    setReleases((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)))
  const patchSource = (ri: number, si: number, patch: Partial<SoftwareEditorSource>) =>
    setReleases((prev) =>
      prev.map((r, i) =>
        i === ri
          ? { ...r, sources: r.sources.map((s2, j) => (j === si ? { ...s2, ...patch } : s2)) }
          : r,
      ),
    )

  const inputStyle = {
    backgroundColor: '#f4f6fa',
    borderRadius: BorderRadius.md,
    padding: Spacing.sm,
    fontSize: FontSize.sm,
    color: '#1c2333',
  }

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.bgPage, paddingTop: insets.top }}>
        <ActivityIndicator style={{ marginTop: 64 }} />
      </View>
    )
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgPage, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: Spacing.md, paddingVertical: Spacing.sm }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: theme.text }}>‹ 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '600', color: theme.text }}>软件编辑器</Text>
        <View style={{ width: 40 }} />
      </View>
      <ScrollView contentContainerStyle={{ padding: Spacing.md, paddingBottom: 140 }}>
        {!!reviewReason && (
          <View style={{ backgroundColor: '#fff4e0', borderRadius: BorderRadius.md, padding: Spacing.sm, marginBottom: Spacing.sm }}>
            <Text style={{ color: '#c07b12', fontSize: FontSize.xs }}>{reviewReason}</Text>
          </View>
        )}

        <View style={{ backgroundColor: theme.bgElevated ?? '#fff', borderRadius: BorderRadius.lg, padding: Spacing.md, marginBottom: Spacing.sm }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text, marginBottom: Spacing.sm }}>基本信息</Text>
          <Text style={{ fontSize: FontSize.xs, color: '#5b6478', marginBottom: 4 }}>软件名（2~80 字）*</Text>
          <TextInput style={inputStyle} value={name} maxLength={80} onChangeText={setName} placeholder="如：效率工具箱" />
          <Text style={{ fontSize: FontSize.xs, color: '#5b6478', marginTop: Spacing.sm, marginBottom: 4 }}>一句话简介（10~160 字）*</Text>
          <TextInput style={inputStyle} value={summary} maxLength={160} onChangeText={setSummary} placeholder="一句话说明它解决什么问题" />
          <Text style={{ fontSize: FontSize.xs, color: '#5b6478', marginTop: Spacing.sm, marginBottom: 4 }}>分类 *</Text>
          <CyclePicker options={categories.map((c) => String(c.id))} labels={Object.fromEntries(categories.map((c) => [String(c.id), c.name]))} value={categoryId ? String(categoryId) : ''} onChange={(v) => setCategoryId(v)} />
          <Text style={{ fontSize: FontSize.xs, color: '#5b6478', marginTop: Spacing.sm, marginBottom: 4 }}>授权类型 *</Text>
          <CyclePicker options={LICENSES} labels={LICENSE_LABEL} value={licenseType} onChange={setLicenseType} />
          <Text style={{ fontSize: FontSize.xs, color: '#5b6478', marginTop: Spacing.sm, marginBottom: 4 }}>来源声明 *</Text>
          <CyclePicker options={ATTRIBUTIONS} labels={ATTRIBUTION_LABEL} value={attributionType} onChange={setAttributionType} />
          <Text style={{ fontSize: FontSize.xs, color: '#5b6478', marginTop: Spacing.sm, marginBottom: 4 }}>开发者名称（可选）</Text>
          <TextInput style={inputStyle} value={developerName} maxLength={80} onChangeText={setDeveloperName} />
          <Text style={{ fontSize: FontSize.xs, color: '#5b6478', marginTop: Spacing.sm, marginBottom: 4 }}>官网（可选，https）</Text>
          <TextInput style={inputStyle} value={officialUrl} onChangeText={setOfficialUrl} placeholder="https://…" autoCapitalize="none" />
          <Text style={{ fontSize: FontSize.xs, color: '#5b6478', marginTop: Spacing.sm, marginBottom: 4 }}>软件介绍 *</Text>
          <TextInput
            style={[inputStyle, { minHeight: 120, textAlignVertical: 'top' }]}
            value={descriptionText}
            onChangeText={setDescriptionText}
            multiline
            placeholder="功能、特性、使用说明。纯文本即可，换行会保留"
          />
          <Text style={{ fontSize: FontSize.xs, color: '#5b6478', marginTop: Spacing.sm, marginBottom: 4 }}>图标（可选）</Text>
          <TouchableOpacity onPress={() => void chooseIcon()} style={{ width: 72, height: 72, borderRadius: BorderRadius.md, backgroundColor: '#f4f6fa', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
            {iconUrl ? (
              <Image source={{ uri: iconUrl }} style={{ width: '100%', height: '100%' }} />
            ) : (
              <Text style={{ fontSize: 32, color: '#a8b0c0' }}>+</Text>
            )}
          </TouchableOpacity>
        </View>

        <View style={{ backgroundColor: theme.bgElevated ?? '#fff', borderRadius: BorderRadius.lg, padding: Spacing.md, marginBottom: Spacing.sm }}>
          <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text, marginBottom: Spacing.sm }}>查看条件</Text>
          <CyclePicker options={ACCESS_MODES} labels={ACCESS_LABEL} value={accessMode} onChange={setAccessMode} />
          {accessMode === 'COMMUNITY_LEVEL' && (
            <TextInput style={[inputStyle, { marginTop: Spacing.sm }]} value={minCommunityLevel} onChangeText={setMinCommunityLevel} keyboardType="number-pad" placeholder="最低社区等级" />
          )}
          {accessMode === 'ADMIN_PAID' && (
            contacts.length === 0 ? (
              <Text style={{ marginTop: Spacing.sm, fontSize: FontSize.xs, color: '#c07b12' }}>暂无可用联系入口，请联系管理员配置</Text>
            ) : (
              <View style={{ marginTop: Spacing.sm }}>
                <CyclePicker options={contacts.map((c) => String(c.id))} labels={Object.fromEntries(contacts.map((c) => [String(c.id), c.name]))} value={accessContactId ?? ''} onChange={setAccessContactId} />
              </View>
            )
          )}
        </View>

        <View style={{ backgroundColor: theme.bgElevated ?? '#fff', borderRadius: BorderRadius.lg, padding: Spacing.md, marginBottom: Spacing.sm }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: Spacing.sm }}>
            <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: theme.text }}>版本与来源</Text>
            <TouchableOpacity onPress={() => setReleases((prev) => [...prev, emptyRelease()])}>
              <Text style={{ fontSize: FontSize.sm, color: '#3d5afe', fontWeight: '600' }}>＋ 增加版本</Text>
            </TouchableOpacity>
          </View>
          {releases.map((r, ri) => (
            <View key={ri} style={{ backgroundColor: '#f8f9fc', borderRadius: BorderRadius.md, padding: Spacing.sm, marginBottom: Spacing.sm }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: '#3d5afe' }}>版本 {ri + 1}</Text>
                {releases.length > 1 && (
                  <TouchableOpacity onPress={() => setReleases((prev) => prev.filter((_, i) => i !== ri))}>
                    <Text style={{ fontSize: FontSize.xs, color: '#d3392f' }}>删除</Text>
                  </TouchableOpacity>
                )}
              </View>
              <TextInput style={[inputStyle, { marginTop: Spacing.xs }]} value={r.versionName} onChangeText={(v) => patchRelease(ri, { versionName: v })} placeholder="版本号，如 1.0.0" />
              <View style={{ marginTop: Spacing.xs }}>
                <CyclePicker options={CHANNELS} labels={{ STABLE: '正式版', BETA: '测试版' }} value={r.channel} onChange={(v) => patchRelease(ri, { channel: v as SoftwareEditorRelease['channel'] })} />
              </View>
              <TextInput style={[inputStyle, { marginTop: Spacing.xs, minHeight: 56, textAlignVertical: 'top' }]} value={r.changelog || ''} onChangeText={(v) => patchRelease(ri, { changelog: v })} multiline placeholder="更新说明" />
              {r.sources.map((s2, si) => (
                <View key={si} style={{ backgroundColor: '#fff', borderRadius: BorderRadius.md, padding: Spacing.sm, marginTop: Spacing.xs }}>
                  <CyclePicker options={SOURCE_TYPES} labels={SOURCE_TYPE_LABEL} value={s2.sourceType} onChange={(v) => patchSource(ri, si, { sourceType: v as SoftwareEditorSource['sourceType'] })} />
                  <View style={{ marginTop: Spacing.xs }}>
                    <CyclePicker options={PLATFORMS} labels={{ WINDOWS: 'Windows', MACOS: 'macOS', LINUX: 'Linux', ANDROID: 'Android', IOS: 'iOS', CROSS_PLATFORM: '跨平台' }} value={s2.platform} onChange={(v) => patchSource(ri, si, { platform: v as SoftwareEditorSource['platform'] })} />
                  </View>
                  <TextInput style={[inputStyle, { marginTop: Spacing.xs }]} value={s2.label || ''} onChangeText={(v) => patchSource(ri, si, { label: v })} placeholder="名称标签，如：百度网盘" />
                  <TextInput style={[inputStyle, { marginTop: Spacing.xs, minHeight: 44, textAlignVertical: 'top' }]} value={s2.url || ''} onChangeText={(v) => patchSource(ri, si, { url: v })} multiline autoCapitalize="none" placeholder={s2.sourceType === 'MAGNET' ? 'magnet:?xt=urn:btih:…' : 'https://…'} />
                  {s2.sourceType === 'NETDISK' && (
                    <View style={{ flexDirection: 'row', gap: Spacing.xs, marginTop: Spacing.xs }}>
                      <TextInput style={[inputStyle, { flex: 1 }]} value={s2.extractionCode || ''} onChangeText={(v) => patchSource(ri, si, { extractionCode: v })} placeholder="提取码" />
                      <TextInput style={[inputStyle, { flex: 1 }]} value={s2.sizeLabel || ''} onChangeText={(v) => patchSource(ri, si, { sizeLabel: v })} placeholder="大小标签" />
                    </View>
                  )}
                  {r.sources.length > 1 && (
                    <TouchableOpacity onPress={() => setReleases((prev) => prev.map((rr, i) => (i === ri ? { ...rr, sources: rr.sources.filter((_, j) => j !== si) } : rr)))}>
                      <Text style={{ marginTop: Spacing.xs, fontSize: FontSize.xs, color: '#d3392f' }}>删除此来源</Text>
                    </TouchableOpacity>
                  )}
                </View>
              ))}
              <TouchableOpacity onPress={() => setReleases((prev) => prev.map((rr, i) => (i === ri ? { ...rr, sources: [...rr.sources, emptySource()] } : rr)))}>
                <Text style={{ fontSize: FontSize.sm, color: '#3d5afe', fontWeight: '600' }}>＋ 增加来源</Text>
              </TouchableOpacity>
            </View>
          ))}
        </View>
      </ScrollView>

      {editor?.reviewStatus === 'PENDING' && (
        <View style={{ position: 'absolute', left: Spacing.md, right: Spacing.md, bottom: 96, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#fff4e0', borderRadius: BorderRadius.md, padding: Spacing.sm }}>
          <Text style={{ flex: 1, fontSize: FontSize.xs, color: '#c07b12' }}>该版本正在审核中，可撤回后继续编辑</Text>
          <TouchableOpacity onPress={() => void handleWithdraw()} style={{ paddingHorizontal: Spacing.md, paddingVertical: 6, borderRadius: BorderRadius.full, backgroundColor: '#c07b12' }}>
            <Text style={{ fontSize: FontSize.xs, color: '#fff' }}>撤回审核</Text>
          </TouchableOpacity>
        </View>
      )}

      <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0, flexDirection: 'row', gap: Spacing.sm, padding: Spacing.md, backgroundColor: theme.bgElevated ?? '#fff' }}>
        <TouchableOpacity disabled={saving} onPress={() => void handleSave()} style={{ flex: 1, padding: Spacing.sm, borderRadius: BorderRadius.full, backgroundColor: '#f4f6fa', alignItems: 'center' }}>
          <Text style={{ color: '#5b6478', fontSize: FontSize.sm, fontWeight: '600' }}>{saving ? '保存中…' : '保存草稿'}</Text>
        </TouchableOpacity>
        <TouchableOpacity disabled={saving} onPress={handleSubmit} style={{ flex: 1.4, padding: Spacing.sm, borderRadius: BorderRadius.full, backgroundColor: '#3d5afe', alignItems: 'center' }}>
          <Text style={{ color: '#fff', fontSize: FontSize.sm, fontWeight: '600' }}>
            {editor?.reviewStatus === 'PENDING' ? '审核中…' : '提交审核'}
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  )
}
