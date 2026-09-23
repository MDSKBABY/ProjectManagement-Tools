import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import EnvironmentFingerprintPanel from './EnvironmentFingerprintPanel.vue'

const mocks = vi.hoisted(() => ({
  listEnvironmentFingerprints: vi.fn(), createEnvironmentFingerprint: vi.fn(),
  updateEnvironmentFingerprint: vi.fn(), deleteEnvironmentFingerprint: vi.fn(),
}))
vi.mock('../api/environment-fingerprints', () => mocks)

const fingerprint = {
  id: 3, projectId: 7, name: '生产 Java 集群', environment: 'PRODUCTION',
  operatingSystem: 'Linux', osVersion: 'Rocky 9', kernelVersion: '5.14', architecture: 'amd64',
  runtimeName: 'Java', runtimeVersion: '21', databaseName: 'PostgreSQL', databaseVersion: '17',
  middlewares: ['Nginx', 'Valkey'], networkZone: 'production-dmz', tags: ['核心'], notes: '生产环境',
  createdAt: '2026-09-22T00:00:00Z', updatedAt: '2026-09-22T00:00:00Z',
}

describe('EnvironmentFingerprintPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.listEnvironmentFingerprints.mockResolvedValue({
      data: [fingerprint], pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
  })

  it('loads normalized fingerprints and exposes structured details', async () => {
    const wrapper = mount(EnvironmentFingerprintPanel, { props: { projectId: 7, canWrite: true } })
    await flushPromises()
    expect(mocks.listEnvironmentFingerprints).toHaveBeenCalledWith(7, expect.objectContaining({ page: 1, pageSize: 20 }))
    expect(wrapper.text()).toContain('生产 Java 集群')
    expect(wrapper.text()).toContain('PostgreSQL 17')
    expect(wrapper.text()).toContain('Nginx、Valkey')
    expect(wrapper.get('[data-test="create-fingerprint"]')).toBeTruthy()
  })

  it('hides write actions for read-only users', async () => {
    const wrapper = mount(EnvironmentFingerprintPanel, { props: { projectId: 7, canWrite: false } })
    await flushPromises()
    expect(wrapper.find('[data-test="create-fingerprint"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-fingerprint"]').exists()).toBe(false)
  })
})
