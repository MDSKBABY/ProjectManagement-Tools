import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import WorkItemPanel from './WorkItemPanel.vue'

const workItemMocks = vi.hoisted(() => ({
  listWorkItems: vi.fn(),
  getWorkItem: vi.fn(),
  createWorkItem: vi.fn(),
  updateWorkItem: vi.fn(),
  deleteWorkItem: vi.fn(),
  transitionWorkItemStatus: vi.fn(),
  listWorkItemStatusHistory: vi.fn(),
  listWorkItemRelations: vi.fn(),
  createWorkItemRelation: vi.fn(),
  deleteWorkItemRelation: vi.fn(),
}))
const projectMocks = vi.hoisted(() => ({ listProjectMembers: vi.fn() }))

vi.mock('../api/work-items', () => workItemMocks)
vi.mock('../api/projects', () => projectMocks)

const item = {
  id: 3,
  projectId: 7,
  type: 'TASK' as const,
  title: '准备生产发布',
  description: '核对发布清单',
  status: 'IN_PROGRESS' as const,
  priority: 'HIGH' as const,
  assignee: { id: 1, displayName: '项目经理' },
  plannedStartDate: '2026-10-01',
  plannedEndDate: '2026-10-03',
  actualStartDate: '2026-10-01',
  actualEndDate: null,
  createdAt: '2026-09-24T10:00:00+08:00',
  updatedAt: '2026-09-24T11:00:00+08:00',
}
const member = {
  userId: 1,
  username: 'manager',
  displayName: '项目经理',
  status: 'ACTIVE' as const,
  role: 'MANAGER' as const,
  joinedAt: '2026-09-01T00:00:00+08:00',
}
const relation = {
  id: 8,
  projectId: 7,
  type: 'BLOCKS' as const,
  source: { id: 3, title: '准备生产发布', type: 'TASK' as const, status: 'IN_PROGRESS' as const },
  target: { id: 4, title: '上线验收', type: 'MILESTONE' as const, status: 'TODO' as const },
  createdBy: { id: 1, displayName: '项目经理' },
  createdAt: '2026-09-24T11:00:00+08:00',
}

describe('WorkItemPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    workItemMocks.listWorkItems.mockResolvedValue({
      data: [item],
      pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
    projectMocks.listProjectMembers.mockResolvedValue({
      data: [member],
      pagination: { page: 1, pageSize: 100, totalItems: 1, totalPages: 1 },
    })
    workItemMocks.getWorkItem.mockResolvedValue(item)
    workItemMocks.listWorkItemStatusHistory.mockResolvedValue([
      {
        id: 10,
        fromStatus: 'TODO',
        toStatus: 'IN_PROGRESS',
        comment: '开始执行',
        changedBy: { id: 1, displayName: '项目经理' },
        changedAt: '2026-09-24T11:00:00+08:00',
      },
    ])
    workItemMocks.listWorkItemRelations.mockResolvedValue({
      data: [relation],
      pagination: { page: 1, pageSize: 100, totalItems: 1, totalPages: 1 },
    })
  })

  it('loads work items and opens detail with history and relations', async () => {
    const wrapper = mount(WorkItemPanel, {
      props: { projectId: 7, currentUserId: 1, isAdministrator: false, canWrite: true, canDelete: true },
    })
    await flushPromises()

    expect(workItemMocks.listWorkItems).toHaveBeenCalledWith(7, expect.objectContaining({
      page: 1, pageSize: 20,
    }))
    expect(projectMocks.listProjectMembers).toHaveBeenCalledWith(7, {
      page: 1, pageSize: 100,
    })
    expect(wrapper.text()).toContain('准备生产发布')

    wrapper.findComponent({ name: 'ElTable' }).vm.$emit('current-change', item)
    await flushPromises()

    expect(workItemMocks.getWorkItem).toHaveBeenCalledWith(7, 3)
    expect(workItemMocks.listWorkItemStatusHistory).toHaveBeenCalledWith(7, 3)
    expect(workItemMocks.listWorkItemRelations).toHaveBeenCalledWith(7, {
      page: 1, pageSize: 100, workItemId: 3,
    })
    expect(wrapper.text()).toContain('开始执行')
    expect(wrapper.text()).toContain('上线验收')
    expect(wrapper.find('[data-test="edit-work-item"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="transition-work-item"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="create-work-item-relation"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="delete-work-item"]').exists()).toBe(true)
  })

  it('keeps every mutation hidden for a read-only project member', async () => {
    projectMocks.listProjectMembers.mockResolvedValue({
      data: [{ ...member, role: 'VIEWER' }],
      pagination: { page: 1, pageSize: 100, totalItems: 1, totalPages: 1 },
    })
    const wrapper = mount(WorkItemPanel, {
      props: { projectId: 7, currentUserId: 1, isAdministrator: false, canWrite: false, canDelete: false },
    })
    await flushPromises()
    wrapper.findComponent({ name: 'ElTable' }).vm.$emit('current-change', item)
    await flushPromises()

    expect(wrapper.find('[data-test="create-work-item"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-work-item"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="transition-work-item"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="create-work-item-relation"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="delete-work-item"]').exists()).toBe(false)
  })
})
