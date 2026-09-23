import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from './client'
import {
  createDeploymentRecord,
  findSimilarDeployments,
  getDeploymentRecord,
  listDeploymentRecords,
  updateDeploymentBaseline,
} from './deployment-records'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))

describe('deployment records api', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())

  it('serializes list filters and immutable record endpoints', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ data: [], pagination: {} })
    await listDeploymentRecords(7, {
      page: 2, pageSize: 20, result: 'SUCCESS', serverId: 9, baseline: true,
      executedFrom: '2026-09-01T00:00:00Z', executedTo: '2026-09-30T23:59:59Z',
    })
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/deployment-records?page=2&pageSize=20&result=SUCCESS&serverId=9&baseline=true&executedFrom=2026-09-01T00%3A00%3A00Z&executedTo=2026-09-30T23%3A59%3A59Z',
    )
    await getDeploymentRecord(7, 5)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/deployment-records/5')
  })

  it('creates records, changes baseline status, and searches by fingerprint', async () => {
    vi.mocked(apiRequest).mockResolvedValue({})
    await createDeploymentRecord(7, {
      serverId: 9, solutionId: 3, executedAt: '2026-09-22T02:00:00Z', result: 'SUCCESS',
    })
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/deployment-records', expect.objectContaining({ method: 'POST' }),
    )
    await updateDeploymentBaseline(7, 5, true)
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/deployment-records/5/baseline', expect.objectContaining({ method: 'PUT' }),
    )
    await findSimilarDeployments(7, 3)
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/deployment-records/similar?fingerprintId=3&limit=10',
    )
  })
})
