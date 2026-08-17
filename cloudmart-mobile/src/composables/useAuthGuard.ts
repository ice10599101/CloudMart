import { useEffect } from 'react'
import Taro from '@tarojs/taro'
import { useAuthStore, authStore } from '@/store/auth'

const PUBLIC_PAGES = [
  '/pages/login/index',
  '/pages/register/index',
  '/pages/home/index',
  '/pages/mall/index',
  '/pages/search/index',
  '/pages/productDetail/index',
  '/pages/postDetail/index',
  '/pages/userProfile/index',
  '/pages/topicDetail/index',
  '/pages/seckill/index',
  '/pages/groupBuy/index',
  '/pages/live/index',
  '/pages/liveRoom/index',
]

export function useAuthGuard() {
  const { isLoggedIn } = useAuthStore()

  useEffect(() => {
    if (isLoggedIn) return

    const currentPath = Taro.getCurrentInstance().router?.path || ''
    const isPublic = PUBLIC_PAGES.some(
      (p) => currentPath === p || currentPath.startsWith(p.split('?')[0])
    )

    if (!isPublic) {
      Taro.redirectTo({ url: '/pages/login/index' })
    }
  }, [isLoggedIn])
}

export function requireLogin(callback: () => void) {
  const { isLoggedIn } = authStore.getState()
  if (isLoggedIn) {
    callback()
  } else {
    Taro.navigateTo({ url: '/pages/login/index' })
  }
}
