import { useEffect } from 'react'
import { Stack } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
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
