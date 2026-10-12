/**
 * R25：统一业务异常（三端一致：code 面向机器、message 面向用户、requestId 可追踪）。
 *
 * axios 默认仅在传输层失败时 reject；旧封装对 HTTP 200 信封 success=false 不做处理，
 * 页面靠 res.data.success 守卫，漏守即"假成功"。request 层现在对 success=false
 * 及携带错误信封的 4xx/5xx 统一拒绝为本异常。
 */
export class BusinessError extends Error {
  /** 稳定机器可读错误码（服务端 error.code；网络层为 NETWORK_ERROR，非信封 HTTP 为 HTTP_{status}） */
  readonly code: string
  /** HTTP 状态码（网络层失败无 status） */
  readonly status?: number
  /** 服务端 X-Request-Id，问题追踪用 */
  readonly requestId?: string

  constructor(
    code: string,
    message: string,
    options?: { status?: number; requestId?: string },
  ) {
    super(message)
    this.name = 'BusinessError'
    this.code = code
    this.status = options?.status
    this.requestId = options?.requestId
  }
}

export function isBusinessError(error: unknown): error is BusinessError {
  return error instanceof BusinessError
}

/**
 * 从捕获的异常提取用户可读文案：
 * 业务异常/普通 Error 用其 message（已是服务端可读文案），否则用兜底文案。
 */
export function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}
