import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import WorkItemCalendar from './WorkItemCalendar.vue'

const mocks = vi.hoisted(() => ({ listWorkItems: vi.fn() }))
vi.mock('../api/work-items', () => mocks)

describe('WorkItemCalendar', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-15T08:00:00+08:00'))
    mocks.listWorkItems.mockResolvedValue({
      data: [{ id: 3, projectId: 7, type: 'MILESTONE', title: '上线', description: null, status: 'TODO', priority: 'HIGH', assignee: null, plannedStartDate: '2026-10-20', plannedEndDate: '2026-10-20', actualStartDate: null, actualEndDate: null, createdAt: '', updatedAt: '' }],
      pagination: { page: 1, pageSize: 100, totalItems: 1, totalPages: 1 },
    })
  })
  afterEach(() => vi.useRealTimers())

  it('loads the visible month by planned date and renders scheduled items', async () => {
    const wrapper = mount(WorkItemCalendar, { props: { projectId: 7 } })
    await flushPromises()

    expect(mocks.listWorkItems).toHaveBeenCalledWith(7, {
      page: 1,
      pageSize: 100,
      plannedFrom: '2026-10-01',
      plannedTo: '2026-10-31',
    })
    expect(wrapper.text()).toContain('2026 年 10 月')
    expect(wrapper.text()).toContain('上线')
  })
})
