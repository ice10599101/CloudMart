import { describe, it, expect } from 'vitest'
import {
  MANAGED_INTENT_TTL_MS,
  PERSISTENT_INTENT_RETENTION_MS,
  decideIntent,
  hashFingerprint,
  intentTerminal,
  parseIntentEntry,
} from './idempotency'

describe('R25 intentTerminal 终态判定', () => {
  it('断网/超时（无 status）结果未知：非终态，保留原键', () => {
    expect(intentTerminal(undefined, undefined)).toBe(false)
  })

  it('5xx / 429：瞬态非终态', () => {
    expect(intentTerminal(500, undefined)).toBe(false)
    expect(intentTerminal(503, 'ORDER_SERVICE_UNAVAILABLE')).toBe(false)
    expect(intentTerminal(429, 'PET_RATE_LIMITED')).toBe(false)
  })

  it('409 处理中（PET/WISH/通用幂等切面）：瞬态非终态', () => {
    expect(intentTerminal(409, 'PET_REQUEST_IN_PROGRESS')).toBe(false)
    expect(intentTerminal(409, 'WISH_OPERATION_IN_PROGRESS')).toBe(false)
    expect(intentTerminal(409, 'IDEMPOTENT_ERROR')).toBe(false)
  })

  it('401 可刷新：非终态；但 TOKEN_REUSE_DETECTED 等明确业务 401 由调用方决定（终态）', () => {
    expect(intentTerminal(401, 'UNAUTHORIZED')).toBe(false)
    expect(intentTerminal(401, 'TOKEN_REUSE_DETECTED')).toBe(true)
  })

  it('确定性业务拒绝（400/403/404/普通409）：终态，清键', () => {
    expect(intentTerminal(400, 'VALIDATION_ERROR')).toBe(true)
    expect(intentTerminal(403, 'FORBIDDEN')).toBe(true)
    expect(intentTerminal(404, 'RESOURCE_NOT_FOUND')).toBe(true)
    expect(intentTerminal(409, 'ORDER_STATUS_CONFLICT')).toBe(true)
  })

  it('同键异载荷 IDEMPOTENCY_KEY_REUSED：终态（必须换新键，保留无意义）', () => {
    expect(intentTerminal(409, 'IDEMPOTENCY_KEY_REUSED')).toBe(true)
  })
})

describe('R25 decideIntent 意图键决策', () => {
  const now = 1_700_000_000_000
  const gen = () => 'fresh-key'

  it('无记录：创建普通意图条目', () => {
    const decision = decideIntent(null, now, false, gen)
    expect(decision.action).toBe('create')
    expect(decision.key).toBe('fresh-key')
    if (decision.action !== 'create') throw new Error('expected create decision')
    expect(decision.entry).toMatchObject({ key: 'fresh-key', createdAt: now, persistent: false })
  })

  it('TTL 内复用同一普通意图键（双击/刷新重放/401 重放收敛）', () => {
    const first = decideIntent(null, now, false, () => 'key-1')
    const stored = JSON.stringify(first.action === 'create' ? first.entry : {})
    const second = decideIntent(stored, now + 5000, false, gen)
    expect(second.action).toBe('reuse')
    expect(second.key).toBe('key-1')
  })

  it('普通意图超过 TTL：轮换为新键（两次明确的不同购买得到不同键）', () => {
    const first = decideIntent(null, now, false, () => 'old-key')
    const stored = JSON.stringify(first.action === 'create' ? first.entry : {})
    const second = decideIntent(stored, now + MANAGED_INTENT_TTL_MS + 1, false, gen)
    expect(second.action).toBe('create')
    expect(second.key).toBe('fresh-key')
  })

  it('持久意图超过 10 分钟 TTL 仍复用原键（未知状态先查原单，不轮换）', () => {
    const first = decideIntent(null, now, true, () => 'durable-key')
    const stored = JSON.stringify(first.action === 'create' ? first.entry : {})
    const later = decideIntent(stored, now + MANAGED_INTENT_TTL_MS + 1, true, gen)
    expect(later.action).toBe('reuse')
    expect(later.key).toBe('durable-key')
  })

  it('条目一旦是持久意图，后续同指纹请求（即使未声明 persistent）也始终复用', () => {
    const first = decideIntent(null, now, true, () => 'durable-key')
    const stored = JSON.stringify(first.action === 'create' ? first.entry : {})
    const later = decideIntent(stored, now + MANAGED_INTENT_TTL_MS + 1, false, gen)
    expect(later.action).toBe('reuse')
    expect(later.key).toBe('durable-key')
  })

  it('持久意图超过保留期才清理轮换', () => {
    const first = decideIntent(null, now, true, () => 'ancient-key')
    const stored = JSON.stringify(first.action === 'create' ? first.entry : {})
    const later = decideIntent(stored, now + PERSISTENT_INTENT_RETENTION_MS + 1, true, gen)
    expect(later.action).toBe('create')
    expect(later.key).toBe('fresh-key')
  })

  it('损坏的存储记录视为无记录', () => {
    expect(decideIntent('not-json', now, false, gen).action).toBe('create')
    expect(decideIntent(JSON.stringify({ key: '' }), now, false, gen).action).toBe('create')
  })
})

describe('R25 指纹', () => {
  it('同输入稳定、异输入发散', () => {
    expect(hashFingerprint('a|b|c')).toBe(hashFingerprint('a|b|c'))
    expect(hashFingerprint('a|b|c')).not.toBe(hashFingerprint('a|b|d'))
  })

  it('parseIntentEntry 拒绝非对象/缺键记录', () => {
    expect(parseIntentEntry(null)).toBeNull()
    expect(parseIntentEntry('null')).toBeNull()
    expect(parseIntentEntry(JSON.stringify({ createdAt: 1 }))).toBeNull()
    expect(parseIntentEntry(JSON.stringify({ key: 'k', createdAt: 1 }))?.key).toBe('k')
  })
})
