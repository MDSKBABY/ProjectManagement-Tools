import type { ChangePasswordInput, CurrentUser, LoginInput } from '../types'
import { apiRequest, clearCsrfToken, refreshCsrfToken } from './client'

export async function login(input: LoginInput): Promise<CurrentUser> {
  const user = await apiRequest<CurrentUser>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  await refreshCsrfToken()
  return user
}

export function getCurrentUser(): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/api/auth/me')
}

export async function logout(): Promise<void> {
  await apiRequest<void>('/api/auth/logout', { method: 'POST' })
  clearCsrfToken()
}

export async function changePassword(input: ChangePasswordInput): Promise<void> {
  await apiRequest<void>('/api/auth/password', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  // 服务端会使用户的全部现有会话失效，旧 CSRF 令牌也不再可用。
  clearCsrfToken()
}
