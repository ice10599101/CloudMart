import { useState, useEffect } from 'react'
import { View, Text, Image } from '@tarojs/components'
import Taro from '@tarojs/taro'
import { useThemeStore } from '@/store/theme'
import { ICON_BASE64 } from '@/components/Icon'
import './index.scss'

const IS_WEAPP = Taro.getEnv() === Taro.ENV_TYPE.WEAPP

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
  const [selected, setSelected] = useState(0)
  const { mode, toggleTheme } = useThemeStore()

  const isSakura = mode === 'sakura'
  const bgColor = isSakura ? '#FFF5F6' : '#0B1220'
  const textColor = '#86909C'
  const activeColor = isSakura ? '#FF4D6A' : '#00D4FF'
  const borderColor = isSakura ? 'rgba(0,0,0,0.08)' : 'rgba(255,255,255,0.08)'
  const gradientStart = isSakura ? '#FF4D6A' : '#00D4FF'
  const gradientEnd = isSakura ? '#FF8FA3' : '#9370DB'
  const glowColor = isSakura ? 'rgba(255,77,106,0.4)' : 'rgba(0,212,255,0.4)'

  useEffect(() => {
    if (!IS_WEAPP) return
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

  // 监听页面切换，更新选中状态
  useEffect(() => {
    const updateSelected = () => {
      const pages = Taro.getCurrentPages()
      const currentPage = pages[pages.length - 1]
      if (currentPage) {
        const path = '/' + currentPage.route
        const index = TAB_LIST.findIndex((tab) => tab.pagePath === path)
        if (index >= 0) setSelected(index)
      }
    }
    updateSelected()
  })

  const handleSwitch = (path: string, index: number) => {
    setSelected(index)
    Taro.switchTab({ url: path })
  }

  return (
    <View
      className='custom-tab-bar'
      style={{
        backgroundColor: bgColor,
        borderTopColor: borderColor,
      }}
    >
      {TAB_LIST.map((tab, index) => {
        const isActive = selected === index
        const iconSrc = ICON_BASE64[tab.iconKey]?.[isActive ? 'active' : 'default'] || ''

        if (tab.isCenter) {
          return (
            <View key={tab.pagePath} className='custom-tab-bar__center' onClick={() => handleSwitch(tab.pagePath, index)}>
              <View
                className='custom-tab-bar__center-icon'
                style={{
                  background: `linear-gradient(135deg, ${gradientStart}, ${gradientEnd})`,
                  boxShadow: `0 4rpx 20rpx ${glowColor}`,
                }}
              >
                <Image src={ICON_BASE64.plus.default} style={{ width: '22px', height: '22px' }} mode='aspectFit' />
              </View>
            </View>
          )
        }

        return (
          <View key={tab.pagePath} className='custom-tab-bar__item' onClick={() => handleSwitch(tab.pagePath, index)}>
            <Image src={iconSrc} style={{ width: '22px', height: '22px' }} mode='aspectFit' />
            <Text
              className='custom-tab-bar__text'
              style={{ color: isActive ? activeColor : textColor }}
            >
              {tab.text}
            </Text>
          </View>
        )
      })}
      <View className='custom-tab-bar__theme' onClick={toggleTheme}>
        <Text className='custom-tab-bar__theme-icon'>{isSakura ? '🌊' : '🌸'}</Text>
      </View>
    </View>
  )
}
