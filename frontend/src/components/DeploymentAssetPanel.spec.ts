import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DeploymentAssetPanel from './DeploymentAssetPanel.vue'

const assetMocks = vi.hoisted(() => ({
  listDeploymentAssets: vi.fn(),
  createDeploymentAsset: vi.fn(),
  listDeploymentAssetVersions: vi.fn(),
}))
const fileMocks = vi.hoisted(() => ({ listProjectFiles: vi.fn() }))

vi.mock('../api/deployment-assets', () => assetMocks)
vi.mock('../api/files', () => fileMocks)

const asset = {
  id: 11,
  assetGroupId: '11111111-1111-1111-1111-111111111111',
  version: 1,
  name: '核心服务安装包',
  assetType: 'INSTALLATION_PACKAGE',
  versionLabel: '1.0.0',
  file: { id: 9, originalName: 'service.zip', sizeBytes: 1024, status: 'AVAILABLE' },
  operatingSystem: 'Linux',
  architecture: 'amd64',
  environment: 'PRODUCTION',
  riskLevel: 'HIGH',
  tags: ['核心'],
  description: '生产安装包',
  prerequisites: null,
  executionInstructions: null,
  rollbackInstructions: null,
  createdBy: { id: 1, displayName: '项目经理' },
  createdAt: '2026-09-20T00:00:00+08:00',
}

describe('DeploymentAssetPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    assetMocks.listDeploymentAssets.mockResolvedValue({
      data: [asset],
      pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
    fileMocks.listProjectFiles.mockResolvedValue({ data: [], pagination: {} })
  })

  it('loads and renders project deployment assets', async () => {
    const wrapper = mount(DeploymentAssetPanel, { props: { projectId: 7, canWrite: true } })
    await flushPromises()

    expect(assetMocks.listDeploymentAssets).toHaveBeenCalledWith(7, {
      page: 1,
      pageSize: 20,
      keyword: undefined,
      assetType: undefined,
      operatingSystem: undefined,
      architecture: undefined,
      environment: undefined,
      riskLevel: undefined,
      tag: undefined,
    })
    expect(wrapper.text()).toContain('核心服务安装包')
    expect(wrapper.text()).toContain('service.zip')
    expect(wrapper.get('[data-test="create-deployment-asset"]')).toBeTruthy()
  })

  it('hides the create action for read-only users and loads version history', async () => {
    assetMocks.listDeploymentAssetVersions.mockResolvedValue([asset])
    const wrapper = mount(DeploymentAssetPanel, { props: { projectId: 7, canWrite: false } })
    await flushPromises()

    expect(wrapper.find('[data-test="create-deployment-asset"]').exists()).toBe(false)
    const versionButton = wrapper.findAll('button').find((button) => button.text() === '版本')
    await versionButton?.trigger('click')
    await flushPromises()
    expect(assetMocks.listDeploymentAssetVersions).toHaveBeenCalledWith(7, asset.assetGroupId)
    expect(wrapper.text()).toContain('资产版本')
  })
})
