import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import WorkItemReminderPanel from './WorkItemReminderPanel.vue'

const mocks = vi.hoisted(() => ({
  listWorkItems: vi.fn(),
  listWorkItemReminders: vi.fn(),
  createWorkItemReminder: vi.fn(),
  dismissWorkItemReminder: vi.fn(),
  deleteWorkItemReminder: vi.fn(),
}))
vi.mock('../api/work-items', () => mocks)

describe('WorkItemReminderPanel', () => {
  beforeEach(() => {
    mocks.listWorkItems.mockResolvedValue({ data: [], pagination: { page: 1, pageSize: 100, totalItems: 0, totalPages: 0 } })
    mocks.listWorkItemReminders.mockResolvedValue({
      data: [{ id: 5, projectId: 7, workItem: { id: 3, title: '准备上线', type: 'TASK', status: 'IN_PROGRESS' }, remindAt: '2026-10-02T01:00:00Z', message: '检查发布清单', status: 'PENDING', createdAt: '2026-09-24T01:00:00Z', dismissedAt: null }],
      pagination: { page: 1, pageSize: 100, totalItems: 1, totalPages: 1 },
    })
  })

  it('loads only the current users reminders and shows create controls when writable', async () => {
    const wrapper = mount(WorkItemReminderPanel, { props: { projectId: 7, canWrite: true } })
    await flushPromises()

    expect(mocks.listWorkItemReminders).toHaveBeenCalledWith(7, { page: 1, pageSize: 100 })
    expect(wrapper.text()).toContain('检查发布清单')
    expect(wrapper.text()).toContain('准备上线')
    expect(wrapper.find('[data-test="create-reminder"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="dismiss-reminder-5"]').exists()).toBe(true)

    await wrapper.get('[data-test="dismiss-reminder-5"]').trigger('click')
    await flushPromises()
    expect(mocks.dismissWorkItemReminder).toHaveBeenCalledWith(7, 5)
    expect(mocks.listWorkItemReminders).toHaveBeenCalledTimes(2)
  })
})
