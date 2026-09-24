import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from './client'
import {
  createWorkItem,
  createWorkItemReminder,
  createWorkItemRelation,
  deleteWorkItem,
  deleteWorkItemRelation,
  deleteWorkItemReminder,
  dismissWorkItemReminder,
  getWorkItem,
  listWorkItemRelations,
  listWorkItemReminders,
  listWorkItemStatusHistory,
  listWorkItems,
  transitionWorkItemStatus,
  updateWorkItem,
} from './work-items'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))

describe('work item api', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())

  it('serializes supported list filters', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ data: [], pagination: {} })
    await listWorkItems(7, {
      page: 2,
      pageSize: 20,
      keyword: '上线',
      type: 'TASK',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      assigneeId: 9,
      plannedFrom: '2026-10-01',
      plannedTo: '2026-10-31',
    })

    expect(apiRequest).toHaveBeenCalledWith(
      '/api/v1/projects/7/work-items?page=2&pageSize=20&keyword=%E4%B8%8A%E7%BA%BF&type=TASK&status=IN_PROGRESS&priority=HIGH&assigneeId=9&plannedFrom=2026-10-01&plannedTo=2026-10-31',
    )
  })

  it('uses the work item detail and mutation endpoints', async () => {
    vi.mocked(apiRequest).mockResolvedValue({})
    const input = {
      type: 'TASK' as const,
      title: '准备发布',
      priority: 'HIGH' as const,
      assigneeId: 9,
      plannedStartDate: '2026-10-01',
      plannedEndDate: '2026-10-03',
    }

    await getWorkItem(7, 3)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/work-items/3')
    await createWorkItem(7, input)
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/work-items',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(input) }),
    )
    await updateWorkItem(7, 3, { title: '准备生产发布', assigneeId: null })
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/work-items/3',
      expect.objectContaining({ method: 'PATCH' }),
    )
    await transitionWorkItemStatus(7, 3, { status: 'IN_PROGRESS', comment: '开始执行' })
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/work-items/3/status-transitions',
      expect.objectContaining({ method: 'POST' }),
    )
    await listWorkItemStatusHistory(7, 3)
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/work-items/3/status-history',
    )
    await deleteWorkItem(7, 3)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/work-items/3', {
      method: 'DELETE',
    })
  })

  it('creates, filters and deletes directed relations', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ data: [], pagination: {} })
    await listWorkItemRelations(7, {
      page: 1,
      pageSize: 100,
      workItemId: 3,
      type: 'BLOCKS',
    })
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/work-item-relations?page=1&pageSize=100&workItemId=3&type=BLOCKS',
    )
    const input = { sourceWorkItemId: 3, targetWorkItemId: 4, type: 'BLOCKS' as const }
    await createWorkItemRelation(7, input)
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/work-item-relations',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(input) }),
    )
    await deleteWorkItemRelation(7, 8)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/work-item-relations/8', {
      method: 'DELETE',
    })
  })

  it('lists and mutates personal work item reminders', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ data: [], pagination: {} })
    await listWorkItemReminders(7, {
      page: 1,
      pageSize: 100,
      workItemId: 3,
      status: 'PENDING',
      from: '2026-10-01T00:00:00.000Z',
    })
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/work-item-reminders?page=1&pageSize=100&workItemId=3&status=PENDING&from=2026-10-01T00%3A00%3A00.000Z',
    )

    const input = {
      workItemId: 3,
      remindAt: '2026-10-02T01:00:00.000Z',
      message: '检查发布清单',
    }
    await createWorkItemReminder(7, input)
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/work-item-reminders',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(input) }),
    )
    await dismissWorkItemReminder(7, 5)
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/work-item-reminders/5/dismiss',
      { method: 'POST' },
    )
    await deleteWorkItemReminder(7, 5)
    expect(apiRequest).toHaveBeenLastCalledWith(
      '/api/v1/projects/7/work-item-reminders/5',
      { method: 'DELETE' },
    )
  })
})
