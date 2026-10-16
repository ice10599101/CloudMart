import { useEffect, useState } from 'react'
import { View, Text, Input, Textarea, Picker, Switch, Image, ScrollView } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
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
import { errorMessage } from '@/utils/request'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

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
const PLATFORM_LABEL: Record<string, string> = {
  WINDOWS: 'Windows', MACOS: 'macOS', LINUX: 'Linux',
  ANDROID: 'Android', IOS: 'iOS', CROSS_PLATFORM: '跨平台',
}
const ARCHES = ['X64', 'X86', 'ARM64', 'UNIVERSAL', 'OTHER'] as const
const CHANNELS = ['STABLE', 'BETA'] as const
const CHANNEL_LABEL: Record<string, string> = { STABLE: '正式版', BETA: '测试版' }

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

export default function SoftwareEditPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const router = useRouter()
  const softwareId = router.params.id || ''

  const [editor, setEditor] = useState<SoftwareEditor | null>(null)
  const [categories, setCategories] = useState<SoftwareCategory[]>([])
  const [contacts, setContacts] = useState<SoftwareContact[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  // 表单态（从 editor 展平）
  const [name, setName] = useState('')
  const [summary, setSummary] = useState('')
  const [categoryId, setCategoryId] = useState<string | null>(null)
  const [licenseType, setLicenseType] = useState<string>('FREE')
  const [attributionType, setAttributionType] = useState<string>('ORIGINAL')
  const [developerName, setDeveloperName] = useState('')
  const [officialUrl, setOfficialUrl] = useState('')
  const [descriptionText, setDescriptionText] = useState('')
  const [accessMode, setAccessMode] = useState<string>('REGISTERED')
  const [minCommunityLevel, setMinCommunityLevel] = useState<number>(1)
  const [accessContactId, setAccessContactId] = useState<string | null>(null)
  const [iconAssetId, setIconAssetId] = useState<string | null>(null)
  const [iconUrl, setIconUrl] = useState<string | null>(null)
  const [releases, setReleases] = useState<SoftwareEditorRelease[]>([emptyRelease()])
  const [withdrawTip, setWithdrawTip] = useState<string | null>(null)

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
      .catch((err) => {
        Taro.showToast({ title: errorMessage(err, '加载编辑器失败'), icon: 'none' })
      })
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
    setMinCommunityLevel(ed.minCommunityLevel ?? 1)
    setAccessContactId(ed.accessContactId)
    setIconAssetId(ed.iconAssetId)
    setIconUrl(ed.iconAssetId ? resolveIcon(ed.iconAssetId) : null)
    setReleases(
      ed.releases && ed.releases.length > 0
        ? ed.releases.map((r) => ({ ...r, sources: r.sources.length ? r.sources : [emptySource()] }))
        : [emptyRelease()],
    )
    if (ed.reviewStatus === 'REJECTED' && ed.reviewReason) setWithdrawTip(`退回原因：${ed.reviewReason}`)
  }

  // 图标展示：本地素材 URL 经 /files 反代（与 Web 一致由 file 服务提供）
  const resolveIcon = (assetId: string) => `/file/assets/${assetId}/raw`

  const chooseIcon = async () => {
    try {
      const res = await Taro.chooseImage({ count: 1, sizeType: ['compressed'] })
      const path = res.tempFiles[0]?.path
      if (!path) return
      const up = await fileApi.uploadAsset(path, 'PUBLIC')
      setIconAssetId(up.data.data.fileId)
      setIconUrl(up.data.data.url)
      Taro.showToast({ title: '图标已上传', icon: 'success' })
    } catch {
      // 用户取消
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
    minCommunityLevel: accessMode === 'COMMUNITY_LEVEL' ? minCommunityLevel : null,
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

  /** 提交前的客户端校验（与服务端 validateForSubmit 同口径） */
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
    if (accessMode === 'COMMUNITY_LEVEL' && (!minCommunityLevel || minCommunityLevel < 1)) {
      return '请设置社区等级门槛'
    }
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
      Taro.showToast({ title: '草稿已保存', icon: 'success' })
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '保存失败'), icon: 'none' })
    } finally {
      setSaving(false)
    }
  }

  const handleSubmit = async () => {
    const problem = validateForSubmit()
    if (problem) {
      Taro.showToast({ title: problem, icon: 'none' })
      return
    }
    Taro.showModal({
      title: '提交审核',
      content: '提交后进入人工审核，期间不可编辑（可撤回）。确认提交？',
      success: async (r) => {
        if (!r.confirm) return
        setSubmitting(true)
        try {
          const saved = await doSave()
          if (!saved) return
          await softwarePublishApi.submit(saved.softwareId, saved.revisionId, saved.lockVersion)
          Taro.showToast({ title: '已提交审核', icon: 'success' })
          setTimeout(() => Taro.redirectTo({ url: '/pages/softwareMine/index?refreshed=1' }), 900)
        } catch (err) {
          Taro.showToast({ title: errorMessage(err, '提交失败'), icon: 'none' })
        } finally {
          setSubmitting(false)
        }
      },
    })
  }

  const handleWithdraw = async () => {
    if (!editor) return
    setSubmitting(true)
    try {
      const res = await softwarePublishApi.withdraw(
        editor.softwareId, editor.revisionId, editor.lockVersion,
      )
      applyEditor(res.data.data)
      setWithdrawTip(null)
      Taro.showToast({ title: '已撤回，可继续编辑', icon: 'success' })
    } catch (err) {
      Taro.showToast({ title: errorMessage(err, '撤回失败'), icon: 'none' })
    } finally {
      setSubmitting(false)
    }
  }

  // ---- 版本/来源编辑 ----
  const patchRelease = (idx: number, patch: Partial<SoftwareEditorRelease>) => {
    setReleases((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)))
  }
  const addRelease = () => setReleases((prev) => [...prev, emptyRelease()])
  const removeRelease = (idx: number) => setReleases((prev) => prev.filter((_, i) => i !== idx))
  const patchSource = (ri: number, si: number, patch: Partial<SoftwareEditorSource>) => {
    setReleases((prev) =>
      prev.map((r, i) =>
        i === ri ? { ...r, sources: r.sources.map((s2, j) => (j === si ? { ...s2, ...patch } : s2)) } : r,
      ),
    )
  }
  const addSource = (ri: number) =>
    setReleases((prev) =>
      prev.map((r, i) => (i === ri ? { ...r, sources: [...r.sources, emptySource()] } : r)),
    )
  const removeSource = (ri: number, si: number) =>
    setReleases((prev) =>
      prev.map((r, i) =>
        i === ri ? { ...r, sources: r.sources.filter((_, j) => j !== si) } : r,
      ),
    )

  const catIndex = categories.findIndex((c) => String(c.id) === String(categoryId))
  const licenseIndex = LICENSES.indexOf(licenseType as (typeof LICENSES)[number])
  const attributionIndex = ATTRIBUTIONS.indexOf(attributionType as (typeof ATTRIBUTIONS)[number])
  const accessIndex = ACCESS_MODES.indexOf(accessMode as (typeof ACCESS_MODES)[number])

  if (loading) {
    return (
      <View style={{ paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
        <CustomNavBar title='软件编辑器' back />
        <Text className={styles.hint}>加载中…</Text>
      </View>
    )
  }

  return (
    <View style={{ paddingTop: `${statusBarHeight + navBarHeight}px`, minHeight: '100vh' }}>
      <CustomNavBar title='软件编辑器' back />
      <ScrollView scrollY className={styles.scroll}>
        {withdrawTip && <View className={styles.warnBar}><Text className={styles.warnText}>{withdrawTip}</Text></View>}

        {/* 基本信息 */}
        <View className={styles.section}>
          <Text className={styles.sectionTitle}>基本信息</Text>
          <View className={styles.field}>
            <Text className={styles.label}>软件名（2~80 字）*</Text>
            <Input className={styles.input} value={name} maxlength={80}
              onInput={(e) => setName(e.detail.value)} placeholder='如：效率工具箱' />
          </View>
          <View className={styles.field}>
            <Text className={styles.label}>一句话简介（10~160 字）*</Text>
            <Textarea className={styles.textarea} value={summary} maxlength={160} autoHeight
              onInput={(e) => setSummary(e.detail.value)} placeholder='一句话说明它解决什么问题' />
            <Text className={styles.count}>{summary.length}/160</Text>
          </View>
          <View className={styles.field}>
            <Text className={styles.label}>分类 *</Text>
            <Picker
              mode='selector'
              range={categories.map((c) => c.name)}
              value={catIndex >= 0 ? catIndex : 0}
              onChange={(e) => setCategoryId(String(categories[Number(e.detail.value)]?.id ?? ''))}
            >
              <View className={styles.pickerValue}>
                <Text className={catIndex >= 0 ? styles.pickerText : styles.pickerPlaceholder}>
                  {catIndex >= 0 ? categories[catIndex].name : '选择分类'}
                </Text>
                <Text className={styles.arrow}>›</Text>
              </View>
            </Picker>
          </View>
          <View className={styles.field}>
            <Text className={styles.label}>授权类型 *</Text>
            <Picker mode='selector' range={LICENSES.map((l) => LICENSE_LABEL[l])}
              value={licenseIndex >= 0 ? licenseIndex : 0}
              onChange={(e) => setLicenseType(LICENSES[Number(e.detail.value)])}>
              <View className={styles.pickerValue}>
                <Text className={styles.pickerText}>{LICENSE_LABEL[licenseType]}</Text>
                <Text className={styles.arrow}>›</Text>
              </View>
            </Picker>
          </View>
          <View className={styles.field}>
            <Text className={styles.label}>来源声明 *</Text>
            <Picker mode='selector' range={ATTRIBUTIONS.map((a) => ATTRIBUTION_LABEL[a])}
              value={attributionIndex >= 0 ? attributionIndex : 0}
              onChange={(e) => setAttributionType(ATTRIBUTIONS[Number(e.detail.value)])}>
              <View className={styles.pickerValue}>
                <Text className={styles.pickerText}>{ATTRIBUTION_LABEL[attributionType]}</Text>
                <Text className={styles.arrow}>›</Text>
              </View>
            </Picker>
          </View>
          <View className={styles.field}>
            <Text className={styles.label}>开发者名称（可选）</Text>
            <Input className={styles.input} value={developerName} maxlength={80}
              onInput={(e) => setDeveloperName(e.detail.value)} placeholder='区别于分享者昵称' />
          </View>
          <View className={styles.field}>
            <Text className={styles.label}>官网（可选，https）</Text>
            <Input className={styles.input} value={officialUrl}
              onInput={(e) => setOfficialUrl(e.detail.value)} placeholder='https://…' />
          </View>
          <View className={styles.field}>
            <Text className={styles.label}>软件介绍 *</Text>
            <Textarea className={styles.textareaTall} value={descriptionText} autoHeight
              onInput={(e) => setDescriptionText(e.detail.value)}
              placeholder='功能、特性、使用说明。纯文本即可，换行会保留' />
          </View>
          <View className={styles.field}>
            <Text className={styles.label}>图标（可选）</Text>
            <View className={styles.iconRow}>
              {iconUrl ? (
                <View className={styles.iconBox}>
                  <Image className={styles.iconImg} src={iconUrl} mode='aspectFill' />
                  <View className={styles.iconRemove} onClick={() => { setIconAssetId(null); setIconUrl(null) }}>
                    <Text className={styles.iconRemoveText}>×</Text>
                  </View>
                </View>
              ) : (
                <View className={styles.iconTrigger} onClick={chooseIcon}>
                  <Text className={styles.plusIcon}>+</Text>
                </View>
              )}
            </View>
          </View>
        </View>

        {/* 查看条件 */}
        <View className={styles.section}>
          <Text className={styles.sectionTitle}>查看条件</Text>
          <Picker mode='selector' range={ACCESS_MODES.map((m) => ACCESS_LABEL[m])}
            value={accessIndex >= 0 ? accessIndex : 0}
            onChange={(e) => setAccessMode(ACCESS_MODES[Number(e.detail.value)])}>
            <View className={styles.pickerValue}>
              <Text className={styles.pickerText}>{ACCESS_LABEL[accessMode]}</Text>
              <Text className={styles.arrow}>›</Text>
            </View>
          </Picker>
          {accessMode === 'COMMUNITY_LEVEL' && (
            <View className={styles.field}>
              <Text className={styles.label}>最低社区等级</Text>
              <Input className={styles.input} type='number' value={String(minCommunityLevel)}
                onInput={(e) => setMinCommunityLevel(Number(e.detail.value) || 1)} />
            </View>
          )}
          {accessMode === 'ADMIN_PAID' && (
            <View className={styles.field}>
              <Text className={styles.label}>付款联系入口 *</Text>
              {contacts.length === 0 ? (
                <Text className={styles.hintSmall}>暂无可用联系入口，请联系管理员配置后重试</Text>
              ) : (
                <Picker mode='selector' range={contacts.map((c) => c.name)}
                  onChange={(e) => setAccessContactId(String(contacts[Number(e.detail.value)].id))}>
                  <View className={styles.pickerValue}>
                    <Text className={accessContactId ? styles.pickerText : styles.pickerPlaceholder}>
                      {contacts.find((c) => String(c.id) === String(accessContactId))?.name || '选择联系入口'}
                    </Text>
                    <Text className={styles.arrow}>›</Text>
                  </View>
                </Picker>
              )}
            </View>
          )}
        </View>

        {/* 版本与来源 */}
        <View className={styles.section}>
          <View className={styles.sectionHead}>
            <Text className={styles.sectionTitle}>版本与来源</Text>
            <Text className={styles.sectionAction} onClick={addRelease}>＋ 增加版本</Text>
          </View>
          {releases.map((r, ri) => (
            <View key={ri} className={styles.releaseCard}>
              <View className={styles.releaseHead}>
                <Text className={styles.releaseTitle}>版本 {ri + 1}</Text>
                {releases.length > 1 && (
                  <Text className={styles.releaseRemove} onClick={() => removeRelease(ri)}>删除</Text>
                )}
              </View>
              <View className={styles.fieldRow}>
                <View className={styles.fieldHalf}>
                  <Text className={styles.label}>版本号 *</Text>
                  <Input className={styles.input} value={r.versionName}
                    onInput={(e) => patchRelease(ri, { versionName: e.detail.value })} placeholder='1.0.0' />
                </View>
                <View className={styles.fieldHalf}>
                  <Text className={styles.label}>渠道</Text>
                  <Picker mode='selector' range={CHANNELS.map((c) => CHANNEL_LABEL[c])}
                    value={CHANNELS.indexOf(r.channel)}
                    onChange={(e) => patchRelease(ri, { channel: CHANNELS[Number(e.detail.value)] })}>
                    <View className={styles.pickerValue}>
                      <Text className={styles.pickerText}>{CHANNEL_LABEL[r.channel]}</Text>
                      <Text className={styles.arrow}>›</Text>
                    </View>
                  </Picker>
                </View>
              </View>
              <View className={styles.fieldRow}>
                <Text className={styles.label}>推荐版本</Text>
                <Switch checked={!!r.recommended}
                  onChange={(e) => patchRelease(ri, { recommended: e.detail.value })} />
              </View>
              <View className={styles.field}>
                <Text className={styles.label}>更新说明</Text>
                <Textarea className={styles.textarea} value={r.changelog || ''} autoHeight
                  onInput={(e) => patchRelease(ri, { changelog: e.detail.value })}
                  placeholder='本次更新的内容' />
              </View>
              <Text className={styles.label}>下载来源</Text>
              {r.sources.map((s2, si) => (
                <View key={si} className={styles.sourceCard}>
                  <View className={styles.fieldRow}>
                    <View className={styles.fieldHalf}>
                      <Text className={styles.label}>类型</Text>
                      <Picker mode='selector' range={SOURCE_TYPES.map((t) => SOURCE_TYPE_LABEL[t])}
                        value={SOURCE_TYPES.indexOf(s2.sourceType)}
                        onChange={(e) => patchSource(ri, si, { sourceType: SOURCE_TYPES[Number(e.detail.value)] })}>
                        <View className={styles.pickerValue}>
                          <Text className={styles.pickerText}>{SOURCE_TYPE_LABEL[s2.sourceType]}</Text>
                          <Text className={styles.arrow}>›</Text>
                        </View>
                      </Picker>
                    </View>
                    <View className={styles.fieldHalf}>
                      <Text className={styles.label}>平台</Text>
                      <Picker mode='selector' range={PLATFORMS.map((p) => PLATFORM_LABEL[p])}
                        value={PLATFORMS.indexOf(s2.platform)}
                        onChange={(e) => patchSource(ri, si, { platform: PLATFORMS[Number(e.detail.value)] })}>
                        <View className={styles.pickerValue}>
                          <Text className={styles.pickerText}>{PLATFORM_LABEL[s2.platform]}</Text>
                          <Text className={styles.arrow}>›</Text>
                        </View>
                      </Picker>
                    </View>
                  </View>
                  <View className={styles.fieldRow}>
                    <View className={styles.fieldHalf}>
                      <Text className={styles.label}>架构</Text>
                      <Picker mode='selector' range={[...ARCHES]}
                        value={ARCHES.indexOf(s2.arch)}
                        onChange={(e) => patchSource(ri, si, { arch: ARCHES[Number(e.detail.value)] })}>
                        <View className={styles.pickerValue}>
                          <Text className={styles.pickerText}>{s2.arch}</Text>
                          <Text className={styles.arrow}>›</Text>
                        </View>
                      </Picker>
                    </View>
                    <View className={styles.fieldHalf}>
                      <Text className={styles.label}>名称标签</Text>
                      <Input className={styles.input} value={s2.label || ''}
                        onInput={(e) => patchSource(ri, si, { label: e.detail.value })} placeholder='如：百度网盘' />
                    </View>
                  </View>
                  <View className={styles.field}>
                    <Text className={styles.label}>链接 *</Text>
                    <Textarea className={styles.textarea} value={s2.url || ''} autoHeight
                      onInput={(e) => patchSource(ri, si, { url: e.detail.value })}
                      placeholder={s2.sourceType === 'MAGNET' ? 'magnet:?xt=urn:btih:…' : 'https://…'} />
                  </View>
                  {s2.sourceType === 'NETDISK' && (
                    <View className={styles.fieldRow}>
                      <View className={styles.fieldHalf}>
                        <Text className={styles.label}>提取码</Text>
                        <Input className={styles.input} value={s2.extractionCode || ''}
                          onInput={(e) => patchSource(ri, si, { extractionCode: e.detail.value })} />
                      </View>
                      <View className={styles.fieldHalf}>
                        <Text className={styles.label}>大小标签</Text>
                        <Input className={styles.input} value={s2.sizeLabel || ''}
                          onInput={(e) => patchSource(ri, si, { sizeLabel: e.detail.value })} placeholder='如：9MB' />
                      </View>
                    </View>
                  )}
                  {r.sources.length > 1 && (
                    <Text className={styles.releaseRemove} onClick={() => removeSource(ri, si)}>删除此来源</Text>
                  )}
                </View>
              ))}
              <Text className={styles.sectionAction} onClick={() => addSource(ri)}>＋ 增加来源</Text>
            </View>
          ))}
        </View>

        <View style={{ height: '220rpx' }} />
      </ScrollView>

      {/* 底部操作栏 */}
      <View className={styles.bottomBar}>
        <View className={`${styles.cancelBtn} ${saving ? styles.btnDisabled : ''}`} onClick={() => {
          if (saving) return
          void handleSave()
        }}>
          <Text className={styles.cancelBtnText}>{saving ? '保存中…' : '保存草稿'}</Text>
        </View>
        <View
          className={`${styles.submitBtn} ${submitting ? styles.btnDisabled : ''}`}
          onClick={() => {
            if (submitting) return
            void handleSubmit()
          }}
        >
          <Text className={styles.submitBtnText}>
            {editor?.reviewStatus === 'PENDING' ? '审核中…' : submitting ? '提交中…' : '提交审核'}
          </Text>
        </View>
      </View>

      {editor?.reviewStatus === 'PENDING' && (
        <View className={styles.pendingOverlay}>
          <Text className={styles.pendingText}>该版本正在审核中，可撤回后继续编辑</Text>
          <View className={styles.pendingBtn} onClick={() => { void handleWithdraw() }}>
            <Text className={styles.pendingBtnText}>撤回审核</Text>
          </View>
        </View>
      )}
    </View>
  )
}
