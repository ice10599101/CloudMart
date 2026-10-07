import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator } from 'react-native'
import { useState, useEffect, useCallback } from 'react'
import { router } from 'expo-router'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { wishApi, type PrivacyOverview } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import { Spacing, FontSize, BorderRadius } from '@/constants/theme'
import { WishColors } from '@/constants/wish-theme'
import { Alert } from 'react-native'

/**
 * P1-17 统一隐私中心（对齐 Web N05 /wish/privacy 与 Taro wishPrivacy）：
 * 单一数据源聚合视图，只读呈现 AI 授权 / 数据导出 / 账号注销阶段 / 默认关闭项，
 * 管理动作跳转既有页面（AI 授权→ai-assistant，导出→data-export）。
 */

const DELETION_LABEL: Record<string, string> = {
  NONE: '未申请',
  PENDING: '宽限期内（30 天）',
  EXECUTING: '正在执行数据清理',
  EXECUTED: '已完成注销',
  CANCELED: '已取消',
}

function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function StatusTag({ text, danger }: { text: string; danger?: boolean }) {
  return (
    <Text
      style={{
        fontSize: FontSize.xs,
        color: danger ? PetColors.danger : PetColors.ok,
        backgroundColor: danger ? 'rgba(233,69,96,0.1)' : 'rgba(82,196,26,0.1)',
        paddingHorizontal: Spacing.sm,
        paddingVertical: 2,
        borderRadius: BorderRadius.full,
        overflow: 'hidden',
      }}
    >
      {text}
    </Text>
  )
}

const PetColors = { danger: '#E94560', ok: '#52c41a' }

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: Spacing.md, borderTopWidth: 1, borderTopColor: WishColors.border }}>
      <Text style={{ fontSize: FontSize.sm, color: WishColors.textSecondary }}>{label}</Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: Spacing.sm }}>{children}</View>
    </View>
  )
}

export default function PrivacyCenterScreen() {
  const insets = useSafeAreaInsets()
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const [view, setView] = useState<PrivacyOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [cancelling, setCancelling] = useState(false)

  const load = useCallback(async () => {
    if (!isLoggedIn) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const res = await wishApi.getMyPrivacyOverview()
      if (res.data?.success && res.data.data) setView(res.data.data as PrivacyOverview)
    } finally {
      setLoading(false)
    }
  }, [isLoggedIn])

  useEffect(() => {
    load()
  }, [load])

  const cancelDeletion = () => {
    if (cancelling) return
    Alert.alert('取消注销申请', '确认撤回全账号注销申请吗？账号将恢复正常使用。', [
      { text: '再想想', style: 'cancel' },
      {
        text: '确认取消',
        onPress: async () => {
          setCancelling(true)
          try {
            const res = await wishApi.cancelAccountDeletion()
            if (res.data?.success) {
              Alert.alert('提示', '注销已取消')
              await load()
            }
          } catch {
            Alert.alert('提示', '网络异常，请稍后重试')
          } finally {
            setCancelling(false)
          }
        },
      },
    ])
  }

  const Card = ({ title, children }: { title: string; children: React.ReactNode }) => (
    <View style={{ backgroundColor: WishColors.bgContainer, borderRadius: BorderRadius.lg, borderWidth: 1, borderColor: WishColors.border, padding: Spacing.lg, marginBottom: Spacing.md }}>
      <Text style={{ fontSize: FontSize.md, fontWeight: '600', color: WishColors.text, marginBottom: Spacing.sm }}>{title}</Text>
      {children}
    </View>
  )

  return (
    <View style={{ flex: 1, backgroundColor: WishColors.bgBase, paddingTop: insets.top }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: Spacing.lg, paddingBottom: Spacing.sm }}>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ fontSize: FontSize.md, color: WishColors.accentCyan }}>← 返回</Text>
        </TouchableOpacity>
        <Text style={{ fontSize: FontSize.lg, fontWeight: '700', color: WishColors.text }}>隐私中心</Text>
        <View style={{ width: 48 }} />
      </View>

      <ScrollView contentContainerStyle={{ padding: Spacing.lg, paddingBottom: Spacing.xxl }}>
        {!isLoggedIn ? (
          <Text style={{ textAlign: 'center', color: WishColors.textTertiary, marginTop: Spacing.xxl }}>请先登录</Text>
        ) : loading && !view ? (
          <ActivityIndicator color={WishColors.accentCyan} style={{ marginTop: Spacing.xxl }} />
        ) : !view ? (
          <Text style={{ textAlign: 'center', color: WishColors.textTertiary, marginTop: Spacing.xxl }}>
            暂时无法加载隐私数据，请稍后重试
          </Text>
        ) : (
          <>
            <Card title="账号与授权">
              <Row label="AI 数据处理授权">
                <StatusTag text={view.aiDataProcessing.granted ? `已授权（v${view.aiDataProcessing.version}）` : '未授权'} />
                <TouchableOpacity onPress={() => router.push('/ai-assistant')}>
                  <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan }}>管理授权 ›</Text>
                </TouchableOpacity>
              </Row>
              <Row label="授权更新时间">
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary }}>{formatDateTime(view.aiDataProcessing.updatedAt)}</Text>
              </Row>
              <Row label="数据导出">
                <StatusTag text={view.dataExport.status || 'NONE'} />
                {view.dataExport.status === 'SUCCESS' && (
                  <TouchableOpacity onPress={() => router.push('/data-export')}>
                    <Text style={{ fontSize: FontSize.xs, color: WishColors.accentCyan }}>去下载 ›</Text>
                  </TouchableOpacity>
                )}
              </Row>
              <Row label="全账号注销">
                <StatusTag
                  text={DELETION_LABEL[view.accountDeletion.status] ?? view.accountDeletion.status}
                  danger={view.accountDeletion.status === 'PENDING'}
                />
                {view.accountDeletion.status === 'PENDING' && (
                  <TouchableOpacity onPress={cancelDeletion} disabled={cancelling}>
                    <Text style={{ fontSize: FontSize.xs, color: PetColors.danger }}>{cancelling ? '取消中...' : '取消注销'}</Text>
                  </TouchableOpacity>
                )}
              </Row>
              {view.accountDeletion.status === 'PENDING' && view.accountDeletion.executeAfter && (
                <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, marginTop: Spacing.sm, lineHeight: 18 }}>
                  宽限期至 {formatDateTime(view.accountDeletion.executeAfter)}，逾期后数据清理不可恢复
                </Text>
              )}
            </Card>

            <Card title="默认关闭项">
              <Row label="位置共享">
                <StatusTag text={view.defaults.locationSharing ? '开启' : '关闭（默认）'} />
              </Row>
              <Row label="还愿自动分享到社区">
                <StatusTag text={view.defaults.fulfillmentAutoShare ? '开启' : '关闭（默认）'} />
              </Row>
              <Row label="人民币支付">
                <StatusTag text={view.defaults.rmbPayment ? '开启' : '关闭（默认）'} />
              </Row>
            </Card>

            <Text style={{ fontSize: FontSize.xs, color: WishColors.textTertiary, lineHeight: 18, textAlign: 'center' }}>
              以上为全账号隐私与数据控制总览；具体授权与导出操作在对应功能页完成。
            </Text>
          </>
        )}
      </ScrollView>
    </View>
  )
}
