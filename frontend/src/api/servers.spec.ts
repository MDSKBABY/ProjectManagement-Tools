import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from './client'
import {
  createServer,
  deleteServer,
  listServers,
  saveServerCredential,
  updateServer,
  viewServerCredential,
} from './servers'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))

describe('server api', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())

  it('serializes server list filters', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ data: [], pagination: {} })
    await listServers(7, {
      page: 2,
      pageSize: 20,
      keyword: 'app-01',
      environment: 'PRODUCTION',
      status: 'ACTIVE',
    })
    expect(apiRequest).toHaveBeenCalledWith(
      '/api/v1/projects/7/servers?page=2&pageSize=20&keyword=app-01&environment=PRODUCTION&status=ACTIVE',
    )
  })

  it('uses stable CRUD and credential endpoints', async () => {
    vi.mocked(apiRequest).mockResolvedValue({})
    const input = {
      name: '生产节点',
      host: 'app-01.internal',
      port: 22,
      environment: 'PRODUCTION' as const,
      status: 'ACTIVE' as const,
    }
    await createServer(7, input)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/servers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
    await updateServer(7, 9, input)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/servers/9', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
    await deleteServer(7, 9)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/servers/9', {
      method: 'DELETE',
    })
    await saveServerCredential(7, 9, { username: 'deploy', password: 'secret' })
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/servers/9/credential', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'deploy', password: 'secret' }),
    })
    await viewServerCredential(7, 9)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/servers/9/credential')
  })
})
