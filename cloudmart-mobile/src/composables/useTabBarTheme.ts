import { useEffect } from 'react'
import Taro from '@tarojs/taro'
import { useThemeStore } from '@/store/theme'

const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP

export function useTabBarTheme() {
  const { mode } = useThemeStore()
  const isSakura = mode === 'sakura'

  useEffect(() => {
    if (!IS_WEAPP) return

    const bgColor = isSakura ? '#FFF5F6' : '#0B1220'
    const activeColor = isSakura ? '#FF4D6A' : '#00D4FF'
    const color = '#86909C'
    const borderStyle = isSakura ? 'white' : 'black'

    Taro.setTabBarStyle({
      color,
      selectedColor: activeColor,
      backgroundColor: bgColor,
      borderStyle,
    }).catch(() => {})

    // 更新导航栏颜色
    if (isSakura) {
      Taro.setNavigationBarColor({
        frontColor: '#000000',
        backgroundColor: '#FFF5F6',
        animation: { duration: 300, timingFunc: 'easeIn' },
      }).catch(() => {})
    } else {
      Taro.setNavigationBarColor({
        frontColor: '#ffffff',
        backgroundColor: '#0B1220',
        animation: { duration: 300, timingFunc: 'easeIn' },
      }).catch(() => {})
    }
  }, [isSakura])
}
