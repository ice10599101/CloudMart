import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { ShippingAddress, CreateAddressRequest, UpdateAddressRequest } from '@/types'

export interface RegisterData {
  password: string
  email: string
  nickname: string
  code: string
}

export interface RegisterResult {
  id: number
  username: string
  nickname: string
  email: string
}

/** 注册验证码发送结果（与注销发码响应结构一致） */
export interface RegisterCodeResult {
  /** 验证码是否真实下发（通道未配置/发送失败时为 false，message 带原因） */
  sent: boolean
  expiresInSeconds: number
  /** 开发/测试回显模式的验证码，生产为空串 */
  devCode: string
  message: string
}

export interface UserProfile {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  signature: string
  gender: string
  birthday: string
  constellation: string
  occupation: string
  school: string
  location: string
  hobbies: string
  nicknameUpdatedAt: string
  createdAt: string
}

export function register(data: RegisterData) {
  return request.post<ApiResponse<RegisterResult>>('/user/users/register', data)
}

/** 发送注册验证码（匿名可调用；sent=false 时用 message 向用户如实提示） */
export function sendRegisterCode(email: string) {
  return request.post<ApiResponse<RegisterCodeResult>>('/user/users/register/code', { email })
}

export function getUserProfile() {
  return request.get<ApiResponse<UserProfile>>('/user/users/me')
}

/** 他人用户资料（需登录；含小答号/性别/生日/职业/学校/地区/爱好/加入时间，邮箱仅本人可见语义由后端控制） */
export function getUserPublicProfile(userId: number | string) {
  return request.get<ApiResponse<UserProfile>>(`/user/users/${userId}`)
}

export function updateProfile(data: Partial<UserProfile>) {
  return request.put<ApiResponse<UserProfile>>('/user/users/profile', data)
}

export function changeNickname(nickname: string) {
  return request.put<ApiResponse<UserProfile>>('/user/users/nickname', { nickname })
}

export function changePassword(oldPassword: string, newPassword: string) {
  return request.put<ApiResponse<void>>('/user/users/password', { oldPassword, newPassword })
}

export function listAddresses() {
  return request.get<ApiResponse<ShippingAddress[]>>('/user/users/addresses')
}

export function getDefaultAddress() {
  return request.get<ApiResponse<ShippingAddress>>('/user/users/addresses/default')
}

export function createAddress(data: CreateAddressRequest) {
  return request.post<ApiResponse<ShippingAddress>>('/user/users/addresses', data)
}

export function updateAddress(addressId: number, data: UpdateAddressRequest) {
  return request.put<ApiResponse<ShippingAddress>>(`/user/users/addresses/${addressId}`, data)
}

export function deleteAddress(addressId: number) {
  return request.delete<ApiResponse<void>>(`/user/users/addresses/${addressId}`)
}

export function setDefaultAddress(addressId: number) {
  return request.put<ApiResponse<ShippingAddress>>(`/user/users/addresses/${addressId}/default`)
}
