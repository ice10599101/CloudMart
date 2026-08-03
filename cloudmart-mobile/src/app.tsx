import { useEffect } from 'react'
import { useLaunch } from '@tarojs/taro'
import './app.scss'
import { useThemeStore } from '@/store/theme'

function App({ children }: { children: React.ReactNode }) {
  const { mode } = useThemeStore()

  useLaunch(() => {
    console.log('App launched.')
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
