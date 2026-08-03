import { View, Text } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { useThemeStore } from '@/store/theme'

interface CustomNavBarProps {
  title?: string
  back?: boolean
}

let cachedStatusBarHeight: number | null = null
let cachedNavBarHeight: number | null = null

function getNavBarMetrics() {
  if (cachedStatusBarHeight !== null && cachedNavBarHeight !== null) {
    return { statusBarHeight: cachedStatusBarHeight, navBarHeight: cachedNavBarHeight }
  }
  const env = Taro.getEnv()
  if (env === Taro.ENV_TYPE.WEAPP) {
    try {
      const menuButtonInfo = Taro.getMenuButtonBoundingClientRect()
      const sysInfo = Taro.getSystemInfoSync()
      cachedStatusBarHeight = sysInfo.statusBarHeight || 20
      cachedNavBarHeight = (menuButtonInfo.bottom - cachedStatusBarHeight) + (menuButtonInfo.top - cachedStatusBarHeight)
    } catch {
      cachedStatusBarHeight = 20
      cachedNavBarHeight = 44
    }
  } else {
    // H5: no status bar, fixed navbar height
    cachedStatusBarHeight = 0
    cachedNavBarHeight = 48
  }
  return { statusBarHeight: cachedStatusBarHeight, navBarHeight: cachedNavBarHeight }
}

export default function CustomNavBar({ title, back }: CustomNavBarProps) {
  const { mode } = useThemeStore()
  const isSakura = mode === 'sakura'
  const { statusBarHeight, navBarHeight } = getNavBarMetrics()

  const bgColor = isSakura ? '#FFF5F6' : '#0B1220'
  const textColor = isSakura ? '#1A1A2E' : '#FFFFFF'

  const handleBack = () => {
    Taro.navigateBack({ delta: 1 })
  }

  return (
    <View
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        zIndex: 999,
        backgroundColor: bgColor,
        paddingTop: `${statusBarHeight}px`,
      }}
    >
      <View
        style={{
          height: `${navBarHeight}px`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          position: 'relative',
          padding: '0 16px',
        }}
      >
        {back && (
          <View
            style={{
              position: 'absolute',
              left: '12px',
              padding: '8px',
              color: textColor,
              fontSize: '18px',
            }}
            onClick={handleBack}
          >
            <Text style={{ color: textColor }}>{'<'}</Text>
          </View>
        )}
        <Text style={{ fontSize: '17px', fontWeight: 600, color: textColor }}>
          {title || ''}
        </Text>
      </View>
    </View>
  )
}

export { getNavBarMetrics }
