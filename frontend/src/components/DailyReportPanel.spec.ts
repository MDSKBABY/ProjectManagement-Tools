import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DailyReportPanel from './DailyReportPanel.vue'

const reportMocks = vi.hoisted(() => ({
  listDailyReports: vi.fn(), createDailyReport: vi.fn(), updateDailyReport: vi.fn(),
  polishDailyReport: vi.fn(), confirmDailyReport: vi.fn(),
}))
vi.mock('../api/daily-reports', () => reportMocks)
vi.mock('../api/work-items', () => ({ listWorkItems: vi.fn().mockResolvedValue({ data: [], pagination: {} }) }))
vi.mock('../api/deployment-records', () => ({ listDeploymentRecords: vi.fn().mockResolvedValue({ data: [], pagination: {} }) }))
vi.mock('../api/delivery-records', () => ({ listMeetingRecords: vi.fn().mockResolvedValue([]) }))

describe('DailyReportPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    reportMocks.listDailyReports.mockResolvedValue([{
      id: 1, projectId: 7, reportDate: '2026-09-24', reporterId: 9,
      reporterDisplayName: '实施人员', originalContent: '完成部署', polishedContent: null,
      workHours: 8, status: 'DRAFT', confirmedBy: null, confirmedByDisplayName: null,
      confirmedAt: null, workItems: [], meetingRecords: [], deploymentRecords: [],
      createdAt: '2026-09-24T09:00:00+08:00', updatedAt: '2026-09-24T09:00:00+08:00',
    }])
  })

  it('shows create, polish and confirm actions from permissions', async () => {
    const wrapper = mount(DailyReportPanel, {
      props: { projectId: 7, currentUserId: 9, canWrite: true, canConfirm: true },
      global: { stubs: { teleport: true } },
    })
    await flushPromises()
    expect(reportMocks.listDailyReports).toHaveBeenCalledWith(7, expect.any(Object))
    expect(wrapper.find('[data-test="create-daily-report"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="polish-daily-report"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="confirm-daily-report"]').exists()).toBe(true)
  })

  it('keeps read-only users read-only', async () => {
    const wrapper = mount(DailyReportPanel, {
      props: { projectId: 7, currentUserId: 10, canWrite: false, canConfirm: false },
      global: { stubs: { teleport: true } },
    })
    await flushPromises()
    expect(wrapper.find('[data-test="create-daily-report"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="polish-daily-report"]').exists()).toBe(false)
  })
})
