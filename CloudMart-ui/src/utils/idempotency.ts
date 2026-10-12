/**
 * R25：幂等意图键纯逻辑（无平台依赖，可单测）。
 *
 * 两类意图：
 * - 普通写意图（managed）：同一方法+路径+参数+载荷+账号指纹，TTL 内复用同一键；
 *   TTL 过期后再次动作视为新意图（两次明确的购买得到不同键）。
 * - 持久意图（persistent）：资金/权益操作由页面"确认一次"后创建，不因本地 TTL
 *   过期轮换新键——未知结果必须先按原键查询终态（先查原单，不重复表达意图）。
 */

export const WRITE_METHODS = ['POST', 'PUT', 'DELETE', 'PATCH'] as const

/** 普通写意图的复用窗口：窗口内的双击/刷新重放/401 重放收敛为同一笔 */
export const MANAGED_INTENT_TTL_MS = 10 * 60 * 1000

/** 持久意图的存储保留期（仅用于陈旧数据清理；服务端结算回放窗口内绝不轮换新键） */
export const PERSISTENT_INTENT_RETENTION_MS = 30 * 24 * 60 * 60 * 1000

/**
 * 瞬态业务错误码：结果未知或尚未终态，必须保留原意图键——
 * 清键会让重试换新键，丢失与原意图的收敛锚点。
 */
export const TRANSIENT_ERROR_CODES: ReadonlySet<string> = new Set([
  'PET_REQUEST_IN_PROGRESS',
  'PET_SETTLEMENT_PENDING',
  'PET_TEMPORARILY_UNAVAILABLE',
  'PET_FEATURE_DISABLED',
  'PET_RATE_LIMITED',
  'PET_QUOTA_EXCEEDED',
  // R25：wish 幂等加速层"处理中"与通用切面"重复请求"同样是瞬态（按原键查询/重放收敛）
  'WISH_OPERATION_IN_PROGRESS',
  'IDEMPOTENT_ERROR',
  'UNAUTHORIZED',
])

/**
 * 终态判定（按业务 outcome 而非 HTTP<500）：
 * 无 status（断网/超时）→ 未知，非终态；5xx/429 → 瞬态；
 * 瞬态错误码（处理中/结算中/限流/可刷新 401）→ 非终态；其余为终态。
 * 注意：IDEMPOTENCY_KEY_REUSED（同键异载荷）是终态——调用方必须换新键，保留旧键无意义。
 */
export function intentTerminal(
  status: number | undefined,
  errorCode: string | undefined,
): boolean {
  if (status === undefined) {
    return false
  }
  if (status >= 500 || status === 429) {
    return false
  }
  if (TRANSIENT_ERROR_CODES.has(errorCode ?? '')) {
    return false
  }
  return true
}

/** FNV-1a 指纹：同一组语义输入稳定收敛，不同意图分布到不同存储键 */
export function hashFingerprint(input: string): string {
  let hash = 0x811c9dc5
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193)
  }
  return `${(hash >>> 0).toString(16)}-${input.length.toString(36)}`
}

export interface IntentEntry {
  key: string
  createdAt: number
  /** 持久意图：资金/权益操作，过期不轮换（超过保留期才清理） */
  persistent?: boolean
}

export function parseIntentEntry(raw: string | null | undefined): IntentEntry | null {
  if (!raw) {
    return null
  }
  try {
    const entry = JSON.parse(raw) as IntentEntry
    return typeof entry?.key === 'string' && entry.key ? entry : null
  } catch {
    return null
  }
}

/**
 * 读取仍可复用的意图键。
 * - 持久意图：仅认 persistent 条目，保留期内无视 TTL（未知状态先查原单的锚点）；
 * - 普通意图：TTL 内的条目可用。
 */
export function readUsableIntent(
  raw: string | null | undefined,
  now: number,
  persistent: boolean,
): IntentEntry | null {
  const entry = parseIntentEntry(raw)
  if (!entry) {
    return null
  }
  const age = now - entry.createdAt
  if (persistent || entry.persistent) {
    return age < PERSISTENT_INTENT_RETENTION_MS ? entry : null
  }
  return age < MANAGED_INTENT_TTL_MS ? entry : null
}

export type IntentDecision =
  | { action: 'reuse'; key: string }
  | { action: 'create'; key: string; entry: IntentEntry }

/**
 * 决策本次写请求使用的意图键：
 * 已有可复用条目 → 原键（同一意图收敛）；否则生成新键并落条目（由调用方持久化）。
 * 一旦条目是持久意图，后续同指纹请求始终复用，即便本次未声明 persistent。
 */
export function decideIntent(
  raw: string | null | undefined,
  now: number,
  persistent: boolean,
  generateKey: () => string,
): IntentDecision {
  const existing = readUsableIntent(raw, now, persistent)
  if (existing) {
    return { action: 'reuse', key: existing.key }
  }
  const entry: IntentEntry = { key: generateKey(), createdAt: now, persistent }
  return { action: 'create', key: entry.key, entry }
}
