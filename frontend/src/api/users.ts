import type { CreateUserInput, PageResponse, UserQuery, UserStatus, UserSummary } from '../types'
import { apiRequest } from './client'

export function listUsers(query: UserQuery): Promise<PageResponse<UserSummary>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  if (query.keyword) parameters.set('keyword', query.keyword)
  if (query.status) parameters.set('status', query.status)
  return apiRequest<PageResponse<UserSummary>>(`/api/v1/admin/users?${parameters}`)
}

export function createUser(input: CreateUserInput): Promise<UserSummary> {
  return apiRequest<UserSummary>('/api/v1/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateUserStatus(id: number, status: Extract<UserStatus, 'ACTIVE' | 'DISABLED'>) {
  return apiRequest<UserSummary>(`/api/v1/admin/users/${id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  })
}
