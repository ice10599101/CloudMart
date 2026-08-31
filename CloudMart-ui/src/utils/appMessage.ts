import type { MessageInstance } from 'antd/es/message/interface'

/**
 * antd v6 的静态 message 无法消费 App 上下文（dynamic theme 警告）。
 * axios 拦截器等非组件代码通过此桥使用最近挂载的 App 级 message 实例。
 */
let appMessageInstance: MessageInstance | null = null

export function bindAppMessage(instance: MessageInstance | null) {
  appMessageInstance = instance
}

export function getAppMessage(): MessageInstance | null {
  return appMessageInstance
}
