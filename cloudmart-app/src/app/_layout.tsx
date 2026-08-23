import { useEffect } from 'react'
import { Platform } from 'react-native'
import { Stack, router } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import * as Notifications from 'expo-notifications'
import { useThemeStore } from '@/store/theme'
import { useAuthStore } from '@/store/auth'
import { ThemeProvider } from '@/hooks/use-theme-context'
import { storage } from '@/utils/storage'

export default function RootLayout() {
  const hydrate = useThemeStore((s) => s.hydrate)
  const fetchUser = useAuthStore((s) => s.fetchUser)

  useEffect(() => {
    hydrate()
    storage.getItem('access_token').then((token) => {
      if (token) fetchUser()
    })
  }, [])

  // 胶囊到期本地推送：点击通知直达胶囊详情（web 无本地推送，跳过注册）
  useEffect(() => {
    if (Platform.OS === 'web') return
    const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
      const data = response.notification.request.content.data as { capsuleId?: number; type?: string }
      if (data?.type === 'WISH_CAPSULE_AVAILABLE' && data.capsuleId) {
        router.push(`/capsule/${data.capsuleId}`)
      }
    })
    return () => subscription.remove()
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
