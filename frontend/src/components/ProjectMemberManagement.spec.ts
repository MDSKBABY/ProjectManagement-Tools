import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ProjectMemberManagement from './ProjectMemberManagement.vue'

const projectMocks = vi.hoisted(() => ({
  listProjectMembers: vi.fn(),
  listProjectMemberCandidates: vi.fn(),
  addProjectMember: vi.fn(),
  updateProjectMemberRole: vi.fn(),
  removeProjectMember: vi.fn(),
}))

vi.mock('../api/projects', () => projectMocks)

describe('ProjectMemberManagement', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    projectMocks.listProjectMembers.mockResolvedValue({
      data: [
        {
          userId: 1,
          username: 'owner',
          displayName: '项目负责人',
          status: 'ACTIVE',
          role: 'OWNER',
          joinedAt: '2026-09-19T10:00:00+08:00',
        },
      ],
      pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
  })

  it('loads project members and exposes management controls only with permission', async () => {
    const wrapper = mount(ProjectMemberManagement, {
      props: {
        projectId: 7,
        canManage: true,
      },
    })
    await flushPromises()

    expect(projectMocks.listProjectMembers).toHaveBeenCalledWith(7, {
      page: 1,
      pageSize: 20,
      keyword: undefined,
    })
    expect(wrapper.text()).toContain('项目负责人')
    expect(wrapper.find('[data-test="candidate-search"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('OWNER')
  })
})
