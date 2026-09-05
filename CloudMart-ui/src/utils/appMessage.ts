import type { MessageInstance } from 'antd/es/message/interface'
import { message as antdStaticMessage } from 'antd'

/**
 * antd v6 的静态 message 无法消费 App 上下文（dynamic theme 警告）。
 * axios 拦截器等非组件代码通过此桥使用最近挂载的 App 级 message 实例；
 * 桥未挂载时回退 antd 静态实例（极端时序下保底可用）。
 */
let appMessageInstance: MessageInstance | null = null

export function bindAppMessage(instance: MessageInstance | null) {
  appMessageInstance = instance
}

export function getAppMessage(): MessageInstance | null {
  return appMessageInstance
}

function delegate(kind: 'success' | 'error' | 'warning' | 'info' | 'loading', content: string, duration?: number): void {
  const api = appMessageInstance
  if (api) {
    api[kind](content, duration)
    return
  }
  ;(antdStaticMessage[kind] as (c: string, d?: number) => void)(content, duration)
}

/** 与 antd 静态 message 同签名；非组件代码统一从本模块导入 */
export const message = {
  success: (content: string, duration?: number) => delegate('success', content, duration),
  error: (content: string, duration?: number) => delegate('error', content, duration),
  warning: (content: string, duration?: number) => delegate('warning', content, duration),
  info: (content: string, duration?: number) => delegate('info', content, duration),
  loading: (content: string, duration?: number) => delegate('loading', content, duration),
}
