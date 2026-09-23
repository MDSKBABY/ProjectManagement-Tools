import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DeploymentSolutionPanel from './DeploymentSolutionPanel.vue'

const mocks = vi.hoisted(() => ({
  listDeploymentSolutions: vi.fn(), getDeploymentSolution: vi.fn(),
  createDeploymentSolution: vi.fn(), updateDeploymentSolution: vi.fn(), deleteDeploymentSolution: vi.fn(),
  listEnvironmentFingerprints: vi.fn(), listDeploymentAssets: vi.fn(),
}))
vi.mock('../api/deployment-solutions', () => mocks)
vi.mock('../api/environment-fingerprints', () => ({ listEnvironmentFingerprints: mocks.listEnvironmentFingerprints }))
vi.mock('../api/deployment-assets', () => ({ listDeploymentAssets: mocks.listDeploymentAssets }))

describe('DeploymentSolutionPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.listDeploymentSolutions.mockResolvedValue({
      data: [{ id: 5, projectId: 7, name: '生产双机方案', scenario: '标准发布', fingerprintId: 3,
        fingerprintName: '生产指纹', status: 'ACTIVE', stepCount: 2, updatedAt: '2026-09-22T00:00:00Z' }],
      pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
    mocks.getDeploymentSolution.mockResolvedValue({
      id: 5, projectId: 7, name: '生产双机方案', scenario: '标准发布', fingerprintId: 3,
      fingerprintName: '生产指纹', status: 'ACTIVE', architectureDescription: '双节点',
      prerequisites: '先备份', rollbackSteps: '恢复旧版', riskNotes: '可能短时中断',
      steps: [{ id: 8, stepOrder: 1, title: '发布', instructions: '执行发布', assetId: 11,
        assetName: '安装包', assetVersion: 2, assetVersionLabel: '2.0.0', parametersTemplate: '--verify' }],
      createdAt: '2026-09-22T00:00:00Z', updatedAt: '2026-09-22T00:00:00Z',
    })
  })

  it('loads solutions and renders exact asset versions in details', async () => {
    const wrapper = mount(DeploymentSolutionPanel, { props: { projectId: 7, canWrite: true } })
    await flushPromises()
    expect(wrapper.text()).toContain('生产双机方案')
    await wrapper.get('[data-test="view-solution"]').trigger('click')
    await flushPromises()
    expect(mocks.getDeploymentSolution).toHaveBeenCalledWith(7, 5)
    expect(wrapper.text()).toContain('安装包 · 2.0.0')
    expect(wrapper.text()).toContain('恢复旧版')
  })

  it('hides solution editing actions for read-only users', async () => {
    const wrapper = mount(DeploymentSolutionPanel, { props: { projectId: 7, canWrite: false } })
    await flushPromises()
    expect(wrapper.find('[data-test="create-solution"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-solution"]').exists()).toBe(false)
  })

  it('shows clear placeholders before fingerprint and asset versions are selected', async () => {
    mocks.listEnvironmentFingerprints.mockResolvedValue({ data: [], pagination: {} })
    mocks.listDeploymentAssets.mockResolvedValue({ data: [], pagination: {} })
    const wrapper = mount(DeploymentSolutionPanel, {
      attachTo: document.body,
      props: { projectId: 7, canWrite: true },
    })
    await flushPromises()

    await wrapper.get('[data-test="create-solution"]').trigger('click')
    await flushPromises()

    const dialogText = document.querySelector('[role="dialog"]')?.textContent ?? ''
    expect(dialogText).toContain('请选择环境指纹')
    expect(dialogText).toContain('请选择资产版本')
    wrapper.unmount()
  })
})
