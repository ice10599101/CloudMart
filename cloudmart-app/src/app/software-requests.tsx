import { useCallback, useEffect, useState } from 'react'
import {
  ActivityIndicator,
  ScrollView,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native'
import { router, useLocalSearchParams } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import {
  createSoftwareRequest,
  listMySoftwareRequests,
} from '@/api/software'
import { useTheme } from '@/hooks/use-theme-context'
import { FontSize, Spacing, BorderRadius } from '@/constants/theme'

const STATUS_LABEL: Record<string, string> = {
  OPEN: '寻找中',
  ANSWERED: '已找到',
  CLOSED: '已关闭',
  PENDING: '审核中',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
}

/** UX06：找软件需求（App 端）——搜索无结果回跳保留关键词；提交进审核 */
export default function SoftwareRequestsScreen() {
  const insets = useSafeAreaInsets()
  const theme = useTheme()
  const { source } = useLocalSearchParams<{ source?: string }>()
  const [mine, setMine] = useState<Array<{
    id: string
    title: string
    usageDesc: string
    status: string
    auditStatus: string
    auditReason: string | null
  }>>([])
  const [loading, setLoading] = useState(true)
  const [title, setTitle] = useState('')
  const [usage, setUsage] = useState(source ? `我搜索"${source}"没有找到合适的……` : '')
  const [features, setFeatures] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await listMySoftwareRequests()
      setMine(res.data.data ?? [])
    } catch {
      setMine([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const submit = async () => {
    setError('')
    if (!title.trim()) {
      setError('请填写一句话需求')
      return
    }
    setSubmitting(true)
    try {
      await createSoftwareRequest({
        title: title.trim(),
        usageDesc: usage.trim(),
        mustHaveFeatures: features
          .split(/[,，]/)
          .map((v) => v.trim())
          .filter(Boolean),
        sourceKeyword: source || undefined,
      })
      setTitle('')
      setUsage('')
      setFeatures('')
      await load()
    } catch {
      setError('提交失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: theme.bgPage, paddingTop: insets.top + Spacing.sm }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm, paddingHorizontal: Spacing.md }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.lg, color: theme.text }}>‹</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: theme.text, flex: 1 }}>
          找软件需求
        </Text>
      </View>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ padding: Spacing.md }}>
        {source ? (
          <Text style={{ fontSize: FontSize.sm, color: theme.textSecondary }}>
            没有找到与"{source}"相关的软件？描述你的需求，运营会帮你找。
          </Text>
        ) : null}
        <Text style={{ fontSize: FontSize.sm, fontWeight: '700', color: theme.text, marginTop: Spacing.md }}>
          一句话需求
        </Text>
        <TextInput
          value={title}
          onChangeText={setTitle}
          maxLength={80}
          placeholder="如：找一款按拍摄日期批量重命名照片的工具"
          placeholderTextColor={theme.textTertiary}
          style={{
            marginTop: Spacing.sm,
            padding: Spacing.md,
            borderRadius: BorderRadius.md,
            backgroundColor: theme.bgContainer,
            color: theme.text,
            fontSize: FontSize.sm,
          }}
        />
        <Text style={{ fontSize: FontSize.sm, fontWeight: '700', color: theme.text, marginTop: Spacing.md }}>
          用途描述（20~500 字）
        </Text>
        <TextInput
          value={usage}
          onChangeText={setUsage}
          multiline
          maxLength={500}
          placeholder="要完成什么任务、什么场景、多少量级……"
          placeholderTextColor={theme.textTertiary}
          style={{
            marginTop: Spacing.sm,
            padding: Spacing.md,
            borderRadius: BorderRadius.md,
            backgroundColor: theme.bgContainer,
            color: theme.text,
            fontSize: FontSize.sm,
            minHeight: 96,
            textAlignVertical: 'top',
          }}
        />
        <Text style={{ fontSize: FontSize.sm, fontWeight: '700', color: theme.text, marginTop: Spacing.md }}>
          必须功能（逗号分隔，可选）
        </Text>
        <TextInput
          value={features}
          onChangeText={setFeatures}
          placeholder="如：批量处理,预览,撤销"
          placeholderTextColor={theme.textTertiary}
          style={{
            marginTop: Spacing.sm,
            padding: Spacing.md,
            borderRadius: BorderRadius.md,
            backgroundColor: theme.bgContainer,
            color: theme.text,
            fontSize: FontSize.sm,
          }}
        />
        {error ? (
          <Text style={{ fontSize: FontSize.xs, color: '#FF4D4F', marginTop: Spacing.sm }}>{error}</Text>
        ) : null}
        <TouchableOpacity
          onPress={() => void submit()}
          disabled={submitting}
          style={{
            marginTop: Spacing.md,
            padding: Spacing.md,
            borderRadius: BorderRadius.md,
            backgroundColor: submitting ? '#91CAFF' : '#1677FF',
            alignItems: 'center',
          }}
        >
          <Text style={{ color: '#fff', fontSize: FontSize.md, fontWeight: '600' }}>
            {submitting ? '提交中…' : '提交需求（进入审核）'}
          </Text>
        </TouchableOpacity>

        {loading ? (
          <ActivityIndicator style={{ marginTop: Spacing.xl }} />
        ) : mine.length > 0 ? (
          <View style={{ marginTop: Spacing.lg }}>
            <Text style={{ fontSize: FontSize.md, fontWeight: '700', color: theme.text }}>我的需求</Text>
            {mine.map((item) => (
              <View
                key={item.id}
                style={{
                  marginTop: Spacing.sm,
                  padding: Spacing.md,
                  borderRadius: BorderRadius.md,
                  backgroundColor: theme.bgContainer,
                }}
              >
                <Text style={{ fontSize: FontSize.sm, fontWeight: '600', color: theme.text }}>
                  {item.title}
                </Text>
                <Text style={{ fontSize: FontSize.xs, color: theme.textSecondary, marginTop: 2 }}>
                  {STATUS_LABEL[item.auditStatus] ?? item.auditStatus} · {STATUS_LABEL[item.status] ?? item.status}
                </Text>
                {item.auditStatus === 'REJECTED' && item.auditReason ? (
                  <Text style={{ fontSize: FontSize.xs, color: '#FF4D4F', marginTop: 2 }}>
                    未通过：{item.auditReason}
                  </Text>
                ) : null}
              </View>
            ))}
          </View>
        ) : null}
      </ScrollView>
    </View>
  )
}
