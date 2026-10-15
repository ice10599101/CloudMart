import { intentTerminal } from '@/utils/idempotency'

/**
 * 软件模块写操作幂等键（手册 6.14/7.3：软件域契约头部为不带前缀的 `Idempotency-Key`）。
 *
 * 与 request.ts 通用拦截器的 `X-Idempotency-Key` 是两条并行机制：这里显式生成并
 * 托管生命周期——同一「方法+路径+载荷」的重复提交复用同一键（双击/断网重试收敛
 * 为一次），收到终态响应（成功/确定性业务失败）后清键，下一次动作生成新键；
 * 同时占用 X- 键位，阻止通用拦截器再生成一把无关键。
 */
const pendingKeys = new Map<string, string>()

function fingerprint(method: string, url: string, payload: unknown): string {
  return `${method.toUpperCase()}:${url}:${JSON.stringify(payload ?? null)}`
}

export function softwareWriteHeader(
  method: string,
  url: string,
  payload: unknown,
): Record<string, string> {
  const fp = fingerprint(method, url, payload)
  let key = pendingKeys.get(fp)
  if (!key) {
    key = `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
    pendingKeys.set(fp, key)
  }
  return { 'Idempotency-Key': key, 'X-Idempotency-Key': key }
}

/** 终态（成功或确定性业务失败）清键；未确认状态（断网/5xx/429/处理中）保留供重试收敛 */
export function settleSoftwareKey(
  method: string,
  url: string,
  payload: unknown,
  error?: unknown,
): void {
  if (!error) {
    pendingKeys.delete(fingerprint(method, url, payload))
    return
  }
  const status = (error as { status?: number }).status
  const code = (error as { code?: string }).code
  if (intentTerminal(status, code)) {
    pendingKeys.delete(fingerprint(method, url, payload))
  }
}
