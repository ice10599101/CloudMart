import { useEffect } from 'react'
import { useLaunch } from '@tarojs/taro'
import './app.scss'
import { useThemeStore } from '@/store/theme'
import { reportTimezoneIfNeeded } from '@/utils/wish-timezone'

function App({ children }: { children: React.ReactNode }) {
  const { mode } = useThemeStore()

  useLaunch(() => {
    // B12：时区上报（变更时才发；失败静默）
    reportTimezoneIfNeeded()
  })

  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.body.setAttribute('data-theme', mode)
      document.documentElement.setAttribute('data-theme', mode)
    }
  }, [mode])

  return <>{children}</>
}

export default App
