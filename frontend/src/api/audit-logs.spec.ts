import { beforeEach, describe, expect, it, vi } from 'vitest'

import { listAuditLogs } from './audit-logs'

const clientMocks = vi.hoisted(() => ({ apiRequest: vi.fn() }))

vi.mock('./client', () => clientMocks)

describe('audit log API', () => {
  beforeEach(() => vi.resetAllMocks())

  it('serializes pagination and all optional filters', async () => {
    clientMocks.apiRequest.mockResolvedValue({ data: [], pagination: {} })

    await listAuditLogs({
      page: 2,
      pageSize: 20,
      actorId: 7,
      action: 'PROJECT_CREATED',
      resourceType: 'PROJECT',
      outcome: 'SUCCESS',
      createdFrom: '2026-09-20T00:00:00.000Z',
      createdTo: '2026-09-21T00:00:00.000Z',
    })

    expect(clientMocks.apiRequest).toHaveBeenCalledWith(
      '/api/v1/admin/audit-logs?page=2&pageSize=20&actorId=7&action=PROJECT_CREATED&resourceType=PROJECT&outcome=SUCCESS&createdFrom=2026-09-20T00%3A00%3A00.000Z&createdTo=2026-09-21T00%3A00%3A00.000Z',
    )
  })
})
