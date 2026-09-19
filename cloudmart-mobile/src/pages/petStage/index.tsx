import { useRouter } from '@tarojs/taro'
import { View, Text, WebView } from '@tarojs/components'
import CustomNavBar, { getNavBarMetrics } from '@/components/CustomNavBar'
import { useThemeClass } from '@/composables/useThemeClass'
import { PET_STAGE_URL } from '@/api/pet'
import styles from './index.module.scss'

/**
 * Cocos 宠物舞台页（实施文档 §2.4/§5）。
 *
 * - H5：Taro WebView 即 iframe，postMessage 完整双向桥；
 * - 微信小程序：web-view 为原生全屏组件，只能观赏 + 状态展示；
 *   游戏内操作按钮经 wx.miniProgram.navigateTo('/pages/pet/index?intent=xxx')
 *   实时回落到原生管理页执行（web-view 的 postMessage 仅在回退/分享时机可收，
 *   不满足操作实时性）。
 *
 * 产物未部署时（加载失败）Fail-Open 提示回退，管理功能在原生页完整可用。
 */
export default function PetStagePage() {
  const { dataTheme, themeStyle } = useThemeClass()
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()
  const { params } = useRouter()
  const isWeapp = process.env.TARO_ENV === 'weapp'
  // 携带宿主会话（游戏内如需展示可扩展；当前产物不读取 token，业务全走宿主）
  const src = PET_STAGE_URL

  if (isWeapp) {
    return (
      <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
        <WebView src={src} />
      </View>
    )
  }
  return (
    <View className={`${styles.page} ${dataTheme}`} style={themeStyle}>
      <CustomNavBar title="宠物舞台" />
      <View className={styles.body} style={{ paddingTop: statusBarHeight + navBarHeight }}>
        <iframe src={src} title="宠物舞台" className={styles.frame} />
        <View className={styles.hintRow}>
          <Text className={styles.hint}>
            {params.intent ? '操作已在管理页执行' : '游戏内的按钮操作会落到「我的宠物」管理页执行'}
          </Text>
        </View>
      </View>
    </View>
  )
}
