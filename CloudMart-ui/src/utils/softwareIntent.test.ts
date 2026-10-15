import { describe, it, expect, beforeEach } from 'vitest'
import { softwareWriteConfig } from './softwareIntent'

/**
 * 软件模块写操作意图键（手册 6.14/7.3 契约：Idempotency-Key）：
 * 复用窗口内同载荷收敛为同一键；终态清理后不复用旧 key 创建新实体。
 */
describe('softwareWriteConfig', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('显式键：原样透传且不登记清理键位（生命周期由调用方管理）', () => {
    const config = softwareWriteConfig('POST', '/software', { a: 1 }, 'explicit-1')
    expect(config.headers['Idempotency-Key']).toBe('explicit-1')
    expect(config._intentStoreKey).toBeUndefined()
  })

  it('未显式传键：生成并复用同一键，同时占用通用拦截器键位', () => {
    const first = softwareWriteConfig('POST', '/software', { a: 1 })
    const second = softwareWriteConfig('POST', '/software', { a: 1 })

    expect(first.headers['Idempotency-Key']).toBeTruthy()
    expect(second.headers['Idempotency-Key']).toBe(first.headers['Idempotency-Key'])
    expect(second._intentStoreKey).toBe(first._intentStoreKey)
    // 阻止通用拦截器再生成 X-Idempotency-Key 并接管清理
    expect(second.headers['X-Idempotency-Key']).toBe(first.headers['Idempotency-Key'])
  })

  it('不同方法/路径/载荷分布到不同键', () => {
    const base = softwareWriteConfig('POST', '/software', { a: 1 })
    const otherPayload = softwareWriteConfig('POST', '/software', { a: 2 })
    const otherUrl = softwareWriteConfig('PUT', '/software', { a: 1 })

    expect(otherPayload.headers['Idempotency-Key']).not.toBe(base.headers['Idempotency-Key'])
    expect(otherUrl.headers['Idempotency-Key']).not.toBe(base.headers['Idempotency-Key'])
  })

  it('终态清理（存储键被移除）后生成新键：页面对已完成操作不复用旧 key', () => {
    const first = softwareWriteConfig('POST', '/software', { a: 1 })
    localStorage.removeItem(first._intentStoreKey as string)

    const second = softwareWriteConfig('POST', '/software', { a: 1 })
    expect(second.headers['Idempotency-Key']).not.toBe(first.headers['Idempotency-Key'])
  })
})
