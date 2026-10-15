import { decideIntent, hashFingerprint } from '@/utils/idempotency'

/**
 * 软件模块写操作幂等键（手册 6.14/7.3/7.5：请求携带 `Idempotency-Key`）。
 *
 * 与 request.ts 通用拦截器的 `X-Idempotency-Key` 是两条并行机制：软件域契约是
 * 不带前缀的 `Idempotency-Key`，这里显式生成并托管其生命周期——
 * - 同一「方法+路径+载荷」在复用窗口内重复提交沿用同一键（双击/断网重试收敛为一次）；
 * - 终态响应（成功/业务拒绝）后由响应拦截器按 `_intentStoreKey` 清理，
 *   下一次动作生成新键，满足手册「页面对已完成操作不复用旧 key 创建新实体」；
 * - 瞬态失败（断网/5xx/429）保留原键，重试命中服务端 software_operation 收敛。
 *
 * 同时占用 `X-Idempotency-Key` 键位，阻止通用拦截器再生成一把无关键并接管清理。
 */
export interface SoftwareWriteConfig {
  headers: Record<string, string>
  /** 由本模块登记时存在；响应拦截器终态清理用（显式键不登记） */
  _intentStoreKey?: string
}

export function softwareWriteConfig(
  method: string,
  url: string,
  payload: unknown,
  explicitKey?: string,
): SoftwareWriteConfig {
  if (explicitKey) {
    // 显式键：生命周期完全由调用方管理（拦截器不覆盖、不清理）
    return { headers: { 'Idempotency-Key': explicitKey } }
  }
  const fingerprint = hashFingerprint(
    [method.toUpperCase(), url, JSON.stringify(payload ?? null), 'software'].join('|'),
  )
  const storeKey = `idem:software:${fingerprint}`
  const decision = decideIntent(localStorage.getItem(storeKey), Date.now(), false, () =>
    crypto.randomUUID(),
  )
  if (decision.action === 'create') {
    localStorage.setItem(storeKey, JSON.stringify(decision.entry))
  }
  return {
    headers: {
      'Idempotency-Key': decision.key,
      'X-Idempotency-Key': decision.key,
    },
    _intentStoreKey: storeKey,
  }
}
