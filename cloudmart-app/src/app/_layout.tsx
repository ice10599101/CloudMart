import { useEffect } from 'react'
import { Stack, router } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import { useThemeStore } from '@/store/theme'
import { useAuthStore } from '@/store/auth'
import { reportTimezoneIfNeeded } from '@/utils/wish-timezone'
import { ThemeProvider } from '@/hooks/use-theme-context'
import { storage } from '@/utils/storage'
import { initCapsuleNotifications, subscribeCapsuleNotificationTap } from '@/utils/capsule-notifications'

export default function RootLayout() {
  const hydrate = useThemeStore((s) => s.hydrate)
  const fetchUser = useAuthStore((s) => s.fetchUser)

  useEffect(() => {
    hydrate()
    storage.getItem('access_token').then((token) => {
      if (token) fetchUser()
    })
  }, [])

  // 胶囊到期本地推送：初始化 + 点击通知直达详情
  // （web/Expo Go Android 不支持环境由 util 内部降级为无推送，不阻断 App）
  useEffect(() => {
    initCapsuleNotifications()
    const unsubscribe = subscribeCapsuleNotificationTap((capsuleId) => {
      router.push(`/capsule/${capsuleId}`)
    })
    return () => unsubscribe?.()
  }, [])

  return (
      <ThemeProvider>
        <StatusBar style="light" />
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="(tabs)" />
          <Stack.Screen name="login" options={{ presentation: 'modal' }} />
          <Stack.Screen name="register" options={{ presentation: 'modal' }} />
        </Stack>
      </ThemeProvider>
  )
}
