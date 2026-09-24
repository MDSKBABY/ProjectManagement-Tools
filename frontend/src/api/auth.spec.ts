import { beforeEach, describe, expect, it, vi } from 'vitest'

import { changePassword } from './auth'

const clientMocks = vi.hoisted(() => ({
  apiRequest: vi.fn(),
  clearCsrfToken: vi.fn(),
  refreshCsrfToken: vi.fn(),
}))

vi.mock('./client', () => clientMocks)

describe('authentication API', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('changes the password and clears the expired sessions csrf token', async () => {
    clientMocks.apiRequest.mockResolvedValue(undefined)
    const input = {
      currentPassword: 'Current-password-123!',
      newPassword: 'New-password-123!',
    }

    await changePassword(input)

    expect(clientMocks.apiRequest).toHaveBeenCalledWith('/api/auth/password', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
    expect(clientMocks.clearCsrfToken).toHaveBeenCalledOnce()
  })
})
