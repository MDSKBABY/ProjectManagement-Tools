import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from './client'
import {
  createEnvironmentFingerprint,
  deleteEnvironmentFingerprint,
  listEnvironmentFingerprints,
  updateEnvironmentFingerprint,
} from './environment-fingerprints'
import {
  createDeploymentSolution,
  deleteDeploymentSolution,
  getDeploymentSolution,
  listDeploymentSolutions,
  updateDeploymentSolution,
} from './deployment-solutions'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))

describe('deployment planning api', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())

  it('serializes normalized fingerprint filters and CRUD endpoints', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ data: [], pagination: {} })
    await listEnvironmentFingerprints(7, {
      page: 2, pageSize: 20, environment: 'PRODUCTION', operatingSystem: 'Linux',
      architecture: 'amd64', databaseName: 'PostgreSQL', middleware: 'Nginx', tag: '核心',
    })
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/environment-fingerprints?page=2&pageSize=20&environment=PRODUCTION&operatingSystem=Linux&architecture=amd64&databaseName=PostgreSQL&middleware=Nginx&tag=%E6%A0%B8%E5%BF%83',
    )
    const input = {
      name: '生产指纹', environment: 'PRODUCTION' as const, operatingSystem: 'Linux',
      architecture: 'amd64', middlewares: ['Nginx'], tags: ['核心'],
    }
    await createEnvironmentFingerprint(7, input)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/environment-fingerprints', expect.objectContaining({ method: 'POST' }))
    await updateEnvironmentFingerprint(7, 9, input)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/environment-fingerprints/9', expect.objectContaining({ method: 'PUT' }))
    await deleteEnvironmentFingerprint(7, 9)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/environment-fingerprints/9', { method: 'DELETE' })
  })

  it('uses aggregate solution CRUD endpoints', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ data: [], pagination: {} })
    await listDeploymentSolutions(7, { page: 1, pageSize: 20, status: 'ACTIVE', fingerprintId: 3 })
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/deployment-solutions?page=1&pageSize=20&status=ACTIVE&fingerprintId=3')
    const input = {
      name: '生产方案', scenario: '标准部署', fingerprintId: 3,
      architectureDescription: '双节点', prerequisites: '先备份', rollbackSteps: '恢复旧版',
      riskNotes: '可能短时中断', status: 'DRAFT' as const,
      steps: [{ stepOrder: 1, title: '发布', instructions: '执行发布', assetId: 11 }],
    }
    await createDeploymentSolution(7, input)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/deployment-solutions', expect.objectContaining({ method: 'POST' }))
    await getDeploymentSolution(7, 5)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/deployment-solutions/5')
    await updateDeploymentSolution(7, 5, input)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/deployment-solutions/5', expect.objectContaining({ method: 'PUT' }))
    await deleteDeploymentSolution(7, 5)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/deployment-solutions/5', { method: 'DELETE' })
  })
})
