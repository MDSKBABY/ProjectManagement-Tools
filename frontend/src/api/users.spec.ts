import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createUser, listUsers, updateUserStatus } from './users'

const clientMocks = vi.hoisted(() => ({ apiRequest: vi.fn() }))

vi.mock('./client', () => clientMocks)

describe('user administration API', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('serializes pagination and optional filters', async () => {
    clientMocks.apiRequest.mockResolvedValue({
      data: [],
      pagination: { page: 2, pageSize: 20, totalItems: 0, totalPages: 0 },
    })

    await listUsers({ page: 2, pageSize: 20, keyword: '张 三', status: 'ACTIVE' })

    expect(clientMocks.apiRequest).toHaveBeenCalledWith(
      '/api/v1/admin/users?page=2&pageSize=20&keyword=%E5%BC%A0+%E4%B8%89&status=ACTIVE',
    )
  })

  it('sends the complete create-user payload', async () => {
    const input = {
      username: 'zhangsan',
      initialPassword: 'Initial-password-123!',
      displayName: '张三',
      email: 'zhangsan@example.com',
    }
    clientMocks.apiRequest.mockResolvedValue({ id: 2 })

    await createUser(input)

    expect(clientMocks.apiRequest).toHaveBeenCalledWith('/api/v1/admin/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
  })

  it('targets one user when changing status', async () => {
    clientMocks.apiRequest.mockResolvedValue({ id: 7, status: 'DISABLED' })

    await updateUserStatus(7, 'DISABLED')

    expect(clientMocks.apiRequest).toHaveBeenCalledWith('/api/v1/admin/users/7/status', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'DISABLED' }),
    })
  })
})
