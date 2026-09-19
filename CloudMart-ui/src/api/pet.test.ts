import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  getMyPet,
  createPet,
  feedPet,
  startPetWork,
  claimPetWork,
  startPetBottle,
  challengePetBattle,
  acceptPetBattle,
  sendPetChat,
  getPetPublicCard,
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

    await createPet({ name: '小橘', species: 'CAT', personality: 'LIVELY' })

    expect(request.post).toHaveBeenCalledWith('/pet/create', {
      name: '小橘',
      species: 'CAT',
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

    expect(request.post).toHaveBeenCalledWith('/pet/chat', { message: '你今天怎么样？' })
  })

  it('getPetPublicCard() calls GET /pet/public/:userId', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getPetPublicCard(1001)

    expect(request.get).toHaveBeenCalledWith('/pet/public/1001')
  })
})
