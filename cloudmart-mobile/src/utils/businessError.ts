/**
 * R25：统一业务异常（三端一致：code 面向机器、message 面向用户、requestId 可追踪）。
 *
 * Taro.request 对 4xx/5xx 也会 resolve，旧封装直接把 ApiResponse 返回页面，
 * 页面只在 catch 提示错误时，业务 400/500 会被静默吞掉（"假成功"）。
 * request 层现在对 success=false / HTTP 4xx/5xx / 网络失败统一抛出本异常。
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
