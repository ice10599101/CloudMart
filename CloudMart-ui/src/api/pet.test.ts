import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  attackPveRun,
  cancelPveRun,
  claimSeasonPassTier,
  createPet,
  feedPet,
  getLinkedProductEntitlement,
  getLinkedProductOperation,
  getMyPet,
  getPetPublicCard,
  getPveRun,
  getSeasonPass,
  joinPveRun,
  listMyPveRuns,
  listOpenPveRuns,
  listPveBosses,
  redeemLinkedProduct,
  sendPetChat,
  startPetBottle,
  startPetWork,
  claimPetWork,
  startPveRun,
  challengePetBattle,
  acceptPetBattle,
} from './pet'

describe('pet API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getMyPet() calls GET /pet/me', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getMyPet()

    expect(request.get).toHaveBeenCalledWith('/pet/me')
  })

  it('createPet() calls POST /pet/create with adoption payload', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await createPet({ name: '小莓', species: 'STRAWBERRY', personality: 'LIVELY' })

    expect(request.post).toHaveBeenCalledWith('/pet/create', {
      name: '小莓',
      species: 'STRAWBERRY',
      personality: 'LIVELY',
    })
  })

  it('feedPet() posts intent only — no numeric payload (server-authoritative)', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await feedPet()

    expect(request.post).toHaveBeenCalledWith('/pet/feed')
  })

  it('startPetWork() calls POST /pet/work/start with configId', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await startPetWork(9001002)

    expect(request.post).toHaveBeenCalledWith('/pet/work/start', { configId: 9001002 })
  })

  it('claimPetWork() posts /pet/work/claim', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await claimPetWork()

    expect(request.post).toHaveBeenCalledWith('/pet/work/claim')
  })

  it('startPetBottle() posts /pet/bottle/start', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await startPetBottle()

    expect(request.post).toHaveBeenCalledWith('/pet/bottle/start')
  })

  it('challengePetBattle() posts mode + defenderPetId', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await challengePetBattle({ mode: 'PVP', defenderPetId: 77 })

    expect(request.post).toHaveBeenCalledWith('/pet/battle/challenge', {
      mode: 'PVP',
      defenderPetId: 77,
    })
  })

  it('acceptPetBattle() posts battle accept endpoint', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await acceptPetBattle(42)

    expect(request.post).toHaveBeenCalledWith('/pet/battle/42/accept')
  })

  it('sendPetChat() posts message string', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await sendPetChat('你今天怎么样？')

    // R25：聊天为权益类写操作，携带持久意图标记（断网/重启按原键查终态）
    expect(request.post).toHaveBeenCalledWith('/pet/chat', { message: '你今天怎么样？' }, { persistentIntent: true })
  })

  it('getPetPublicCard() calls GET /pet/public/:userId', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getPetPublicCard(1001)

    expect(request.get).toHaveBeenCalledWith('/pet/public/1001')
  })
})

describe('R19 协作 PVE API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listPveBosses() calls GET /pet/pve/bosses', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await listPveBosses()

    expect(request.get).toHaveBeenCalledWith('/pet/pve/bosses')
  })

  it('startPveRun() posts bossCode only', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await startPveRun('forest_ogre')

    expect(request.post).toHaveBeenCalledWith('/pet/pve', { bossCode: 'forest_ogre' })
  })

  it('listOpenPveRuns() calls GET /pet/pve/open with backend paging params page/size', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await listOpenPveRuns({ page: 1, size: 20 })

    expect(request.get).toHaveBeenCalledWith('/pet/pve/open', { params: { page: 1, size: 20 } })
  })

  it('joinPveRun() posts /pet/pve/:id/join', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await joinPveRun('9001002')

    expect(request.post).toHaveBeenCalledWith('/pet/pve/9001002/join')
  })

  it('attackPveRun() posts explicit requestKey body for replay idempotency', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await attackPveRun('9001002', 'fixed-key-1')

    expect(request.post).toHaveBeenCalledWith('/pet/pve/9001002/attack', { requestKey: 'fixed-key-1' })
  })

  it('attackPveRun() auto-generates a 32-char hex requestKey when omitted', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await attackPveRun('9001002')

    expect(request.post).toHaveBeenCalledWith(
      '/pet/pve/9001002/attack',
      { requestKey: expect.stringMatching(/^[0-9a-f]{32}$/) },
    )
  })

  it('cancelPveRun() posts /pet/pve/:id/cancel', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await cancelPveRun('9001002')

    expect(request.post).toHaveBeenCalledWith('/pet/pve/9001002/cancel')
  })

  it('listMyPveRuns() calls GET /pet/pve/mine with paging params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await listMyPveRuns({ page: 2, size: 10 })

    expect(request.get).toHaveBeenCalledWith('/pet/pve/mine', { params: { page: 2, size: 10 } })
  })

  it('getPveRun() calls GET /pet/pve/:id and forwards silentError for polling', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getPveRun('9001002')
    await getPveRun('9001002', { silentError: true })

    expect(request.get).toHaveBeenNthCalledWith(1, '/pet/pve/9001002', undefined)
    expect(request.get).toHaveBeenNthCalledWith(2, '/pet/pve/9001002', { silentError: true })
  })
})

describe('R19 赛季通行证 API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getSeasonPass() calls GET /pet/season/pass', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getSeasonPass()

    expect(request.get).toHaveBeenCalledWith('/pet/season/pass')
  })

  it('claimSeasonPassTier() posts tier and requestKey as query params', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await claimSeasonPassTier(3, 'fixed-key-2')

    expect(request.post).toHaveBeenCalledWith(
      '/pet/season/pass/claim',
      undefined,
      { params: { tier: 3, requestKey: 'fixed-key-2' } },
    )
  })

  it('claimSeasonPassTier() auto-generates requestKey when omitted', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await claimSeasonPassTier(3)

    expect(request.post).toHaveBeenCalledWith(
      '/pet/season/pass/claim',
      undefined,
      { params: { tier: 3, requestKey: expect.stringMatching(/^[0-9a-f]{32}$/) } },
    )
  })
})

describe('R19 实物商品联动 API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('redeemLinkedProduct() posts redemptionCode with explicit requestKey', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await redeemLinkedProduct('CODE-123', 'fixed-key-3')

    expect(request.post).toHaveBeenCalledWith('/pet/linked-product/redeem', {
      redemptionCode: 'CODE-123',
      requestKey: 'fixed-key-3',
    })
  })

  it('redeemLinkedProduct() auto-generates requestKey when omitted', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await redeemLinkedProduct('CODE-123')

    expect(request.post).toHaveBeenCalledWith('/pet/linked-product/redeem', {
      redemptionCode: 'CODE-123',
      requestKey: expect.stringMatching(/^[0-9a-f]{32}$/),
    })
  })

  it('getLinkedProductOperation() calls GET /pet/linked-product/operations/:id', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getLinkedProductOperation('op-1')

    expect(request.get).toHaveBeenCalledWith(
      '/pet/linked-product/operations/op-1', undefined)
  })

  it('getLinkedProductEntitlement() calls GET /pet/linked-product/entitlement', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getLinkedProductEntitlement()

    expect(request.get).toHaveBeenCalledWith('/pet/linked-product/entitlement')
  })
})
