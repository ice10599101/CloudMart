import { useState, useEffect, useCallback } from 'react'
import { View, Text } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { wishApi, type PrivacyOverview } from '@/api/wish'
import { useAuthStore } from '@/store/auth'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import styles from './index.module.scss'

/**
 * 统一隐私中心（对齐 Web N05 /wish/privacy）：单一数据源聚合视图。
 * 只读呈现 AI 授权 / 数据导出 / 账号注销阶段 / 默认关闭项，
 * 管理动作跳转既有页面（AI 授权→aiAssistant，导出→dataExport）。
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

export default function WishPrivacyPage() {
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { isLoggedIn } = useAuthStore()
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
      if (res.data.success && res.data.data) setView(res.data.data)
    } finally {
      setLoading(false)
    }
  }, [isLoggedIn])

  useEffect(() => {
    load()
  }, [load])

  const goAiAssistant = () => {
    Taro.navigateTo({ url: '/pages/aiAssistant/index' })
  }

  const goDataExport = () => {
    Taro.navigateTo({ url: '/pages/dataExport/index' })
  }

  const cancelDeletion = async () => {
    if (cancelling) return
    const { confirm } = await Taro.showModal({
      title: '取消注销申请',
      content: '确认撤回全账号注销申请吗？账号将恢复正常使用。',
    })
    if (!confirm) return
    setCancelling(true)
    try {
      const res = await wishApi.cancelAccountDeletion()
      if (res.data.success) {
        Taro.showToast({ title: '注销已取消', icon: 'success' })
        await load()
      } else {
        Taro.showToast({ title: res.data.error?.message ?? '取消失败', icon: 'none' })
      }
    } catch {
      Taro.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
    } finally {
      setCancelling(false)
    }
  }

  const renderRow = (label: string, valueNode: React.ReactNode) => (
    <View key={label} className={styles.row}>
      <Text className={styles.rowLabel}>{label}</Text>
      <View className={styles.rowValue}>{valueNode}</View>
    </View>
  )

  const statusTag = (text: string, danger = false) => (
    <Text className={`${styles.tag} ${danger ? styles.tagDanger : ''}`}>{text}</Text>
  )

  return (
    <View className={styles.page} style={{ paddingTop: statusBarHeight + navBarHeight }}>
      <CustomNavBar title='隐私中心' back />

      {!isLoggedIn ? (
        <View className={styles.empty}>
          <Text>请先登录</Text>
        </View>
      ) : loading && !view ? (
        <View className={styles.empty}>
          <Text>加载中...</Text>
        </View>
      ) : !view ? (
        <View className={styles.empty}>
          <Text>暂时无法加载隐私数据，请稍后重试</Text>
        </View>
      ) : (
        <>
          <View className={styles.card}>
            <Text className={styles.cardTitle}>账号与授权</Text>
            {renderRow(
              'AI 数据处理授权',
              <View className={styles.inlineActions}>
                {view.aiDataProcessing.granted
                  ? statusTag(`已授权（v${view.aiDataProcessing.version}）`)
                  : statusTag('未授权')}
                <Text className={styles.actionLink} onClick={goAiAssistant}>
                  管理授权 ›
                </Text>
              </View>,
            )}
            {renderRow(
              '授权更新时间',
              <Text className={styles.plainText}>{formatDateTime(view.aiDataProcessing.updatedAt)}</Text>,
            )}
            {renderRow(
              '数据导出',
              <View className={styles.inlineActions}>
                {statusTag(view.dataExport.status || 'NONE')}
                {view.dataExport.status === 'SUCCESS' && (
                  <Text className={styles.actionLink} onClick={goDataExport}>
                    去下载 ›
                  </Text>
                )}
              </View>,
            )}
            {renderRow(
              '全账号注销',
              <View className={styles.inlineActions}>
                {statusTag(
                  DELETION_LABEL[view.accountDeletion.status] ?? view.accountDeletion.status,
                  view.accountDeletion.status === 'PENDING',
                )}
                {view.accountDeletion.status === 'PENDING' && (
                  <Text
                    className={`${styles.actionLink} ${styles.actionDanger}`}
                    onClick={cancelDeletion}
                  >
                    {cancelling ? '取消中...' : '取消注销'}
                  </Text>
                )}
              </View>,
            )}
            {view.accountDeletion.status === 'PENDING' && view.accountDeletion.executeAfter && (
              <Text className={styles.hint}>
                宽限期至 {formatDateTime(view.accountDeletion.executeAfter)}，逾期后数据清理不可恢复
              </Text>
            )}
          </View>

          <View className={styles.card}>
            <Text className={styles.cardTitle}>默认关闭项</Text>
            {renderRow(
              '位置共享',
              statusTag(view.defaults.locationSharing ? '开启' : '关闭（默认）'),
            )}
            {renderRow(
              '还愿自动分享到社区',
              statusTag(view.defaults.fulfillmentAutoShare ? '开启' : '关闭（默认）'),
            )}
            {renderRow('人民币支付', statusTag(view.defaults.rmbPayment ? '开启' : '关闭（默认）'))}
          </View>

          <Text className={styles.footNote}>
            以上为全账号隐私与数据控制总览；具体授权与导出操作在对应功能页完成。
          </Text>
        </>
      )}
    </View>
  )
}
