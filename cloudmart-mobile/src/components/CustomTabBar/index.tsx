import { useState } from 'react'
import { View, Text, Image } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useThemeStore } from '@/store/theme'
import { ICON_BASE64 } from '@/components/Icon'

const TAB_LIST = [
  {
    pagePath: '/pages/home/index',
    text: '首页',
    iconKey: 'home' as const,
  },
  {
    pagePath: '/pages/mall/index',
    text: '商城',
    iconKey: 'mall' as const,
  },
  {
    pagePath: '/pages/publish/index',
    text: '发布',
    isCenter: true,
    iconKey: 'plus' as const,
  },
  {
    pagePath: '/pages/message/index',
    text: '消息',
    iconKey: 'message' as const,
  },
  {
    pagePath: '/pages/mine/index',
    text: '我的',
    iconKey: 'user' as const,
  },
]

export default function CustomTabBar() {
  const [currentPath, setCurrentPath] = useState('/pages/home/index')
  const { mode, toggleTheme } = useThemeStore()

  const isSakura = mode === 'sakura'

  useDidShow(() => {
    const instance = Taro.getCurrentInstance()
    if (instance?.router?.path) {
      setCurrentPath(instance.router.path)
    }
    Taro.hideTabBar({ animation: false }).catch(() => {})
  })

  const handleSwitch = (path: string) => {
    setCurrentPath(path)
    Taro.switchTab({ url: path })
  }

  const bgColor = isSakura ? '#FFF5F6' : '#0B1220'
  const borderColor = isSakura ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)'
  const textColor = '#86909C'
  const activeColor = isSakura ? '#FF4D6A' : '#00D4FF'
  const gradientStart = isSakura ? '#FF4D6A' : '#00D4FF'
  const gradientEnd = isSakura ? '#FF8FA3' : '#9370DB'
  const glowColor = isSakura ? 'rgba(255,77,106,0.4)' : 'rgba(0,212,255,0.4)'

  return (
    <View
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        display: 'flex',
        alignItems: 'center',
        height: '110rpx',
        backgroundColor: bgColor,
        borderTop: `1px solid ${borderColor}`,
        paddingBottom: 'env(safe-area-inset-bottom)',
        zIndex: 999,
      }}
    >
      {TAB_LIST.map((tab) => {
        const isActive = currentPath === tab.pagePath
        const iconSrc = ICON_BASE64[tab.iconKey]?.[isActive ? 'active' : 'default'] || ''

        if (tab.isCenter) {
          return (
            <View
              key={tab.pagePath}
              style={{
                flex: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '8rpx 0',
              }}
              onClick={() => handleSwitch(tab.pagePath)}
            >
              <View
                style={{
                  width: '88rpx',
                  height: '88rpx',
                  borderRadius: '50%',
                  background: `linear-gradient(135deg, ${gradientStart}, ${gradientEnd})`,
                  boxShadow: `0 4rpx 20rpx ${glowColor}`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginTop: '-40rpx',
                }}
              >
                <Image src={ICON_BASE64.plus.default} style={{ width: '22px', height: '22px' }} mode='aspectFit' />
              </View>
            </View>
          )
        }

        return (
          <View
            key={tab.pagePath}
            style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '8rpx 0',
              position: 'relative',
            }}
            onClick={() => handleSwitch(tab.pagePath)}
          >
            <View style={{ width: '48rpx', height: '48rpx', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '4rpx' }}>
              <Image src={iconSrc} style={{ width: '22px', height: '22px' }} mode='aspectFit' />
            </View>
            <Text style={{ fontSize: '20rpx', color: isActive ? activeColor : textColor, fontWeight: isActive ? 600 : 400 }}>
              {tab.text}
            </Text>
            {isActive && (
              <View style={{
                position: 'absolute',
                bottom: '8rpx',
                width: '16rpx',
                height: '6rpx',
                borderRadius: '3rpx',
                background: `linear-gradient(135deg, ${gradientStart}, ${gradientEnd})`,
                boxShadow: `0 0 12rpx ${glowColor}`,
              }} />
            )}
          </View>
        )
      })}
      <View
        style={{
          position: 'absolute',
          right: '16rpx',
          top: '8rpx',
          width: '56rpx',
          height: '56rpx',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderRadius: '50%',
          border: `1px solid ${borderColor}`,
        }}
        onClick={toggleTheme}
      >
        <Text style={{ fontSize: '28rpx' }}>{isSakura ? '🌊' : '🌸'}</Text>
      </View>
    </View>
  )
}
