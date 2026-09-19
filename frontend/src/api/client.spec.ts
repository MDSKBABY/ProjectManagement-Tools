import { afterEach, describe, expect, it, vi } from 'vitest'

import { login } from './auth'
import { ApiError, apiRequest, clearCsrfToken } from './client'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('API client', () => {
  afterEach(() => {
    clearCsrfToken()
    vi.restoreAllMocks()
  })

  it('loads CSRF before a write request and sends the session cookie', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'token-1' }),
      )
      .mockResolvedValueOnce(jsonResponse({ id: 1 }))

    await apiRequest<{ id: number }>('/api/example', { method: 'POST' })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'same-origin',
    })
    const headers = fetchMock.mock.calls[1]?.[1]?.headers as Headers
    expect(headers.get('X-CSRF-TOKEN')).toBe('token-1')
  })

  it('refreshes CSRF after login because the server rotates the token', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'before-login' }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ id: 1, username: 'admin', displayName: '管理员', roles: [], permissions: [] }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'after-login' }),
      )

    await login({ username: 'admin', password: 'test-password' })

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/auth/csrf')
  })

  it('converts backend errors into a stable ApiError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse({ error: { code: 'AUTHENTICATION_REQUIRED', message: '请先登录' } }, 401),
    )

    await expect(apiRequest('/api/auth/me')).rejects.toEqual(
      new ApiError(401, 'AUTHENTICATION_REQUIRED', '请先登录'),
    )
  })
})
