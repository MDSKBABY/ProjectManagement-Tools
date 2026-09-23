import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DeploymentRecordPanel from './DeploymentRecordPanel.vue'

const mocks = vi.hoisted(() => ({
  listDeploymentRecords: vi.fn(), getDeploymentRecord: vi.fn(), createDeploymentRecord: vi.fn(),
  updateDeploymentBaseline: vi.fn(), findSimilarDeployments: vi.fn(), listServers: vi.fn(),
  listDeploymentSolutions: vi.fn(), listEnvironmentFingerprints: vi.fn(),
}))
vi.mock('../api/deployment-records', () => ({
  listDeploymentRecords: mocks.listDeploymentRecords,
  getDeploymentRecord: mocks.getDeploymentRecord,
  createDeploymentRecord: mocks.createDeploymentRecord,
  updateDeploymentBaseline: mocks.updateDeploymentBaseline,
  findSimilarDeployments: mocks.findSimilarDeployments,
}))
vi.mock('../api/servers', () => ({ listServers: mocks.listServers }))
vi.mock('../api/deployment-solutions', () => ({ listDeploymentSolutions: mocks.listDeploymentSolutions }))
vi.mock('../api/environment-fingerprints', () => ({ listEnvironmentFingerprints: mocks.listEnvironmentFingerprints }))

const summary = {
  id: 5, projectId: 7, serverId: 9, serverName: '生产服务器', solutionId: 3,
  solutionName: '生产方案', environmentFingerprintId: 8, result: 'SUCCESS', executedBy: 1,
  executedByName: '项目经理', executedAt: '2026-09-22T02:00:00Z', exceptionNotes: null,
  notes: '健康检查正常', baseline: false, createdAt: '2026-09-22T02:01:00Z',
}

describe('DeploymentRecordPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.listDeploymentRecords.mockResolvedValue({
      data: [summary], pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
    mocks.getDeploymentRecord.mockResolvedValue({
      ...summary,
      serverSnapshot: { ...summary, name: '生产服务器' },
      environmentSnapshot: { operatingSystem: 'Linux', osVersion: '9', runtimeName: 'Java', runtimeVersion: '21', databaseName: 'PostgreSQL', databaseVersion: '17' },
      solutionSnapshot: { name: '生产方案', steps: [{ id: 1, stepOrder: 1, title: '发布', instructions: '执行发布', assetName: '安装包', assetVersionLabel: '9.0.0' }] },
    })
    mocks.updateDeploymentBaseline.mockResolvedValue({ ...summary, baseline: true })
    mocks.listServers.mockResolvedValue({ data: [], pagination: {} })
    mocks.listDeploymentSolutions.mockResolvedValue({ data: [], pagination: {} })
    mocks.listEnvironmentFingerprints.mockResolvedValue({ data: [], pagination: {} })
  })

  it('loads records and renders immutable snapshot details', async () => {
    const wrapper = mount(DeploymentRecordPanel, { props: { projectId: 7, canWrite: true } })
    await flushPromises()
    expect(wrapper.text()).toContain('生产服务器')
    await wrapper.get('[data-test="view-record"]').trigger('click')
    await flushPromises()
    expect(mocks.getDeploymentRecord).toHaveBeenCalledWith(7, 5)
    expect(wrapper.text()).toContain('安装包 · 9.0.0')
    expect(wrapper.text()).toContain('PostgreSQL 17')
  })

  it('marks a successful record as a reusable baseline', async () => {
    const wrapper = mount(DeploymentRecordPanel, { props: { projectId: 7, canWrite: true } })
    await flushPromises()
    await wrapper.get('[data-test="toggle-baseline"]').trigger('click')
    await flushPromises()
    expect(mocks.updateDeploymentBaseline).toHaveBeenCalledWith(7, 5, true)
    expect(mocks.listDeploymentRecords).toHaveBeenCalledTimes(2)
  })

  it('keeps history readable while hiding write actions from read-only users', async () => {
    const wrapper = mount(DeploymentRecordPanel, { props: { projectId: 7, canWrite: false } })
    await flushPromises()
    expect(wrapper.find('[data-test="create-record"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="toggle-baseline"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="find-similar"]').exists()).toBe(true)
  })
})
