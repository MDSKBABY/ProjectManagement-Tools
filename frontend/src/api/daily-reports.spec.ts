import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from './client'
import {
  confirmDailyReport,
  createDailyReport,
  listDailyReports,
  polishDailyReport,
  updateDailyReport,
} from './daily-reports'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))

describe('daily report api', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())

  it('builds date filters and write actions', async () => {
    vi.mocked(apiRequest).mockResolvedValue([])
    await listDailyReports(7, { from: '2026-09-01', to: '2026-09-07', reporterId: 9 })
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/daily-reports?from=2026-09-01&to=2026-09-07&reporterId=9',
    )

    const input = {
      reportDate: '2026-09-24', originalContent: '完成联调', workHours: 8,
      workItemIds: [3], meetingRecordIds: [4], deploymentRecordIds: [],
    }
    await createDailyReport(7, input)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/daily-reports', expect.objectContaining({ method: 'POST' }))
    await updateDailyReport(7, 2, input)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/daily-reports/2', expect.objectContaining({ method: 'PUT' }))
    await polishDailyReport(7, 2)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/daily-reports/2/polish', { method: 'POST' })
    await confirmDailyReport(7, 2)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/daily-reports/2/confirm', { method: 'POST' })
  })
})
