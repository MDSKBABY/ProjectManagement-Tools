import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from './client'
import {
  createDeploymentAsset,
  listDeploymentAssets,
  listDeploymentAssetVersions,
} from './deployment-assets'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))

describe('deployment asset api', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())

  it('serializes all supported list filters', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ data: [], pagination: {} })
    await listDeploymentAssets(7, {
      page: 2,
      pageSize: 20,
      assetType: 'SCRIPT',
      operatingSystem: 'Linux',
      architecture: 'amd64',
      environment: 'PRODUCTION',
      riskLevel: 'HIGH',
      tag: '核心',
    })
    expect(apiRequest).toHaveBeenCalledWith(
      '/api/v1/projects/7/deployment-assets?page=2&pageSize=20&assetType=SCRIPT&operatingSystem=Linux&architecture=amd64&environment=PRODUCTION&riskLevel=HIGH&tag=%E6%A0%B8%E5%BF%83',
    )
  })

  it('creates a new immutable version and reads its history', async () => {
    vi.mocked(apiRequest).mockResolvedValue({})
    const input = {
      assetGroupId: '11111111-1111-1111-1111-111111111111',
      name: '部署脚本',
      assetType: 'SCRIPT' as const,
      versionLabel: '1.1.0',
      fileAssetId: 9,
      environment: 'PRODUCTION' as const,
      riskLevel: 'HIGH' as const,
      tags: ['核心'],
      prerequisites: 'Java 21',
      executionInstructions: './deploy.sh',
      rollbackInstructions: './rollback.sh',
    }
    await createDeploymentAsset(7, input)
    expect(apiRequest).toHaveBeenCalledWith('/api/v1/projects/7/deployment-assets', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
    await listDeploymentAssetVersions(7, input.assetGroupId)
    expect(apiRequest).toHaveBeenLastCalledWith(
      `/api/v1/projects/7/deployment-assets/groups/${input.assetGroupId}/versions`,
    )
  })
})
