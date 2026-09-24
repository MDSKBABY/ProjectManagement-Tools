import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import WorkItemBoard from './WorkItemBoard.vue'

const mocks = vi.hoisted(() => ({ listWorkItems: vi.fn() }))
vi.mock('../api/work-items', () => mocks)

const items = [
  { id: 1, projectId: 7, type: 'TASK', title: '设计方案', description: null, status: 'TODO', priority: 'HIGH', assignee: null, plannedStartDate: null, plannedEndDate: null, actualStartDate: null, actualEndDate: null, createdAt: '', updatedAt: '' },
  { id: 2, projectId: 7, type: 'TASK', title: '开发接口', description: null, status: 'IN_PROGRESS', priority: 'NORMAL', assignee: { id: 1, displayName: '小王' }, plannedStartDate: null, plannedEndDate: null, actualStartDate: null, actualEndDate: null, createdAt: '', updatedAt: '' },
]

describe('WorkItemBoard', () => {
  beforeEach(() => mocks.listWorkItems.mockResolvedValue({ data: items, pagination: { page: 1, pageSize: 100, totalItems: 2, totalPages: 1 } }))

  it('groups work items into four status columns and emits selection', async () => {
    const wrapper = mount(WorkItemBoard, { props: { projectId: 7, canTransition: true } })
    await flushPromises()

    expect(mocks.listWorkItems).toHaveBeenCalledWith(7, { page: 1, pageSize: 100 })
    expect(wrapper.text()).toContain('待处理')
    expect(wrapper.text()).toContain('进行中')
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('已取消')
    await wrapper.get('[data-test="board-item-1"]').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([items[0]])

    await wrapper.setProps({ refreshKey: 1 })
    await flushPromises()
    expect(mocks.listWorkItems).toHaveBeenCalledTimes(2)
  })
})
