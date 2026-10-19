import { afterEach, expect, it, vi } from 'vitest'
import { createUuid } from './uuid'
import { softwareWriteConfig } from './softwareIntent'
import { newInboundReceiptId } from '@/api/admin/business'
import { attackPveRun } from '@/api/pet'
import request from '@/utils/request'

vi.mock('@/utils/request', () => ({ default: { post: vi.fn() } }))

const browserCrypto = globalThis.crypto

afterEach(() => {
  Object.defineProperty(globalThis, 'crypto', { value: browserCrypto, configurable: true })
  localStorage.clear()
  vi.clearAllMocks()
})

it('公网 HTTP 下软件、后台入库和宠物操作使用兼容的请求键', () => {
  vi.stubGlobal('crypto', { getRandomValues: browserCrypto.getRandomValues.bind(browserCrypto) })
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
  expect(createUuid()).toMatch(uuid)
  expect(softwareWriteConfig('POST', '/software', {}).headers['Idempotency-Key']).toMatch(uuid)
  expect(newInboundReceiptId()).toMatch(/^RCP[0-9a-f]{28}$/)
  attackPveRun('1')
  expect(request.post).toHaveBeenCalledWith(
    '/pet/pve/1/attack',
    expect.objectContaining({ requestKey: expect.stringMatching(/^[0-9a-f]{32}$/) }),
  )
})
