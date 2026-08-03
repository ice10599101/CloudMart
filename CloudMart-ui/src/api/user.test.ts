import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import request from '@/utils/request'
import {
  register,
  getUserProfile,
  updateProfile,
  changeNickname,
  changePassword,
  listAddresses,
  getDefaultAddress,
  createAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress,
} from './user'

describe('user API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('register() calls POST /user/users/register', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await register({ password: 'pass123', email: 'test@example.com', nickname: 'Tester' })

    expect(request.post).toHaveBeenCalledWith('/user/users/register', {
      password: 'pass123',
      email: 'test@example.com',
      nickname: 'Tester',
    })
  })

  it('getUserProfile() calls GET /user/users/me', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getUserProfile()

    expect(request.get).toHaveBeenCalledWith('/user/users/me')
  })

  it('updateProfile() calls PUT /user/users/profile', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await updateProfile({ avatar: 'new.png', signature: 'Hello!' })

    expect(request.put).toHaveBeenCalledWith('/user/users/profile', {
      avatar: 'new.png',
      signature: 'Hello!',
    })
  })

  it('changeNickname() calls PUT /user/users/nickname', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await changeNickname('NewNick')

    expect(request.put).toHaveBeenCalledWith('/user/users/nickname', { nickname: 'NewNick' })
  })

  it('changePassword() calls PUT /user/users/password', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await changePassword('oldPwd', 'newPwd')

    expect(request.put).toHaveBeenCalledWith('/user/users/password', {
      oldPassword: 'oldPwd',
      newPassword: 'newPwd',
    })
  })

  it('listAddresses() calls GET /user/users/addresses', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await listAddresses()

    expect(request.get).toHaveBeenCalledWith('/user/users/addresses')
  })

  it('getDefaultAddress() calls GET /user/users/addresses/default', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: {} } as any)

    await getDefaultAddress()

    expect(request.get).toHaveBeenCalledWith('/user/users/addresses/default')
  })

  it('createAddress() calls POST /user/users/addresses', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: {} } as any)

    await createAddress({ receiverName: '张三', receiverPhone: '13800138000', province: '广东省', city: '深圳市', district: '南山区', detailAddress: '科技园1号', isDefault: true })

    expect(request.post).toHaveBeenCalledWith('/user/users/addresses', {
      receiverName: '张三',
      receiverPhone: '13800138000',
      province: '广东省',
      city: '深圳市',
      district: '南山区',
      detailAddress: '科技园1号',
      isDefault: true,
    })
  })

  it('updateAddress() calls PUT /user/users/addresses/:id', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await updateAddress(1, { receiverName: '李四' })

    expect(request.put).toHaveBeenCalledWith('/user/users/addresses/1', { receiverName: '李四' })
  })

  it('deleteAddress() calls DELETE /user/users/addresses/:id', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: {} } as any)

    await deleteAddress(1)

    expect(request.delete).toHaveBeenCalledWith('/user/users/addresses/1')
  })

  it('setDefaultAddress() calls PUT /user/users/addresses/:id/default', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: {} } as any)

    await setDefaultAddress(1)

    expect(request.put).toHaveBeenCalledWith('/user/users/addresses/1/default')
  })
})
