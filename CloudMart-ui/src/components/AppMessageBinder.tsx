import { useEffect } from 'react'
import { App } from 'antd'
import { bindAppMessage } from '@/utils/appMessage'

/** 挂载在 antd <App> 内部，把 App 级 message 实例提供给非组件代码（axios 拦截器等） */
export default function AppMessageBinder() {
  const { message } = App.useApp()

  useEffect(() => {
    bindAppMessage(message)
    return () => bindAppMessage(null)
  }, [message])

  return null
}
