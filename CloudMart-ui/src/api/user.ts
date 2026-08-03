import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { ShippingAddress, CreateAddressRequest, UpdateAddressRequest } from '@/types'

export interface RegisterData {
  password: string
  email: string
  nickname: string
}

export interface RegisterResult {
  id: number
  username: string
  nickname: string
  email: string
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

export function getUserProfile() {
  return request.get<ApiResponse<UserProfile>>('/user/users/me')
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
