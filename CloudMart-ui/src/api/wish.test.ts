import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  getDriftBottleQuota,
  listDriftBottleCandidateWishes,
  throwDriftBottle,
  fishDriftBottle,
  listMyDriftBottles,
  returnDriftBottle,
  collectDriftBottle,
  updateDriftBottlePickerAnonymity,
  interactDriftBottle,
  listDriftBottleComments,
  addDriftBottleComment,
} from './wish'

describe('wish API 漂流瓶', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getDriftBottleQuota() calls GET /wish/drift-bottles/quota', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await getDriftBottleQuota()

    expect(request.get).toHaveBeenCalledWith('/wish/drift-bottles/quota')
  })

  it('listDriftBottleCandidateWishes() calls GET candidate-wishes', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await listDriftBottleCandidateWishes()

    expect(request.get).toHaveBeenCalledWith('/wish/drift-bottles/candidate-wishes')
  })

  it('throwDriftBottle() posts text content with anonymity', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await throwDriftBottle({ content: '<p>你好</p>', isAnonymous: true })

    expect(request.post).toHaveBeenCalledWith('/wish/drift-bottles', {
      content: '<p>你好</p>',
      isAnonymous: true,
    })
  })

  it('throwDriftBottle() posts wishId for wish-linked bottles', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await throwDriftBottle({ wishId: 42, isAnonymous: false })

    expect(request.post).toHaveBeenCalledWith('/wish/drift-bottles', {
      wishId: 42,
      isAnonymous: false,
    })
  })

  it('fishDriftBottle() calls POST /wish/drift-bottles/fish', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await fishDriftBottle()

    expect(request.post).toHaveBeenCalledWith('/wish/drift-bottles/fish')
  })

  it('listMyDriftBottles() calls GET /wish/drift-bottles/mine', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await listMyDriftBottles()

    expect(request.get).toHaveBeenCalledWith('/wish/drift-bottles/mine')
  })

  it('returnDriftBottle() calls POST /{id}/return', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await returnDriftBottle(2001)

    expect(request.post).toHaveBeenCalledWith('/wish/drift-bottles/2001/return')
  })

  it('collectDriftBottle() calls POST /{id}/collect', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await collectDriftBottle(2001)

    expect(request.post).toHaveBeenCalledWith('/wish/drift-bottles/2001/collect')
  })

  it('updateDriftBottlePickerAnonymity() puts picker-anonymity body', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as never)

    await updateDriftBottlePickerAnonymity(2001, false)

    expect(request.put).toHaveBeenCalledWith('/wish/drift-bottles/2001/picker-anonymity', {
      isAnonymous: false,
    })
  })

  it('interactDriftBottle() posts interaction type', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await interactDriftBottle(2001, 'BLESS')

    expect(request.post).toHaveBeenCalledWith('/wish/drift-bottles/2001/interactions', { type: 'BLESS' })
  })

  it('listDriftBottleComments() calls GET comments with params', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as never)

    await listDriftBottleComments(2001, { cursor: '100', pageSize: 10 })

    expect(request.get).toHaveBeenCalledWith('/wish/drift-bottles/2001/comments', {
      params: { cursor: '100', pageSize: 10 },
    })
  })

  it('addDriftBottleComment() posts comment body', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as never)

    await addDriftBottleComment(2001, { content: '你好', parentId: 10, isAnonymous: true })

    expect(request.post).toHaveBeenCalledWith('/wish/drift-bottles/2001/comments', {
      content: '你好',
      parentId: 10,
      isAnonymous: true,
    })
  })
})
