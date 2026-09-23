import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AuditLogPanel from './AuditLogPanel.vue'

const auditMocks = vi.hoisted(() => ({ listAuditLogs: vi.fn() }))

vi.mock('../api/audit-logs', () => auditMocks)

const response = {
  data: [
    {
      id: 9,
      actor: { id: 1, username: 'admin', displayName: '系统管理员' },
      action: 'DEPLOYMENT_RECORD_CREATED',
      resourceType: 'DEPLOYMENT_RECORD',
      resourceId: '203',
      outcome: 'SUCCESS' as const,
      ipAddress: '127.0.0.1',
      userAgent: 'Vitest',
      requestId: 'request-203',
      details: { projectId: 101 },
      createdAt: '2026-09-22T10:00:00+08:00',
    },
  ],
  pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
}

describe('AuditLogPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    auditMocks.listAuditLogs.mockResolvedValue(response)
  })

  it('loads newest audit events and exposes trace details', async () => {
    const wrapper = mount(AuditLogPanel)
    await flushPromises()

    expect(auditMocks.listAuditLogs).toHaveBeenCalledWith({ page: 1, pageSize: 20 })
    expect(wrapper.text()).toContain('DEPLOYMENT_RECORD_CREATED')
    expect(wrapper.text()).toContain('系统管理员')
    expect(wrapper.text()).toContain('request-203')
    expect(wrapper.text()).toContain('"projectId": 101')
  })

  it('submits normalized filters and returns to the first page', async () => {
    const wrapper = mount(AuditLogPanel)
    await flushPromises()

    await wrapper.get('input[aria-label="审计动作"]').setValue(' deployment_failed ')
    await wrapper.get('input[aria-label="资源类型"]').setValue(' server ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(auditMocks.listAuditLogs).toHaveBeenLastCalledWith({
      page: 1,
      pageSize: 20,
      action: 'DEPLOYMENT_FAILED',
      resourceType: 'SERVER',
    })
  })
})
