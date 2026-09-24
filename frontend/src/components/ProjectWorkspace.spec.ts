import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ProjectWorkspace from './ProjectWorkspace.vue'

const projectMocks = vi.hoisted(() => ({
  listProjects: vi.fn(),
  createProject: vi.fn(),
  updateProject: vi.fn(),
  deleteProject: vi.fn(),
}))

vi.mock('../api/projects', () => projectMocks)

const currentUser = {
  id: 1,
  username: 'manager',
  displayName: '项目经理',
  roles: ['PROJECT_MANAGER'],
  permissions: ['project:read', 'project:create', 'project:update', 'project:delete'],
}

const project = {
  id: 7,
  code: 'PM-007',
  name: '实施项目',
  customerName: '示例客户',
  description: '项目说明',
  status: 'ACTIVE' as const,
  owner: { id: 1, displayName: '项目经理' },
  startDate: '2026-09-20',
  endDate: '2026-12-31',
  tags: ['重点'],
  createdAt: '2026-09-19T10:00:00+08:00',
  updatedAt: '2026-09-19T10:00:00+08:00',
}

describe('ProjectWorkspace', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    projectMocks.listProjects.mockResolvedValue({
      data: [project],
      pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
  })

  it('loads visible projects and opens the create form for authorized users', async () => {
    const wrapper = mount(ProjectWorkspace, {
      props: { currentUser },
      global: {
        stubs: {
          ProjectEditorDialog: {
            props: ['open'],
            template: '<div v-if="open" data-test="project-editor">项目表单</div>',
          },
        },
      },
    })
    await flushPromises()

    expect(projectMocks.listProjects).toHaveBeenCalledWith({
      page: 1,
      pageSize: 20,
      keyword: undefined,
      status: undefined,
    })
    expect(wrapper.text()).toContain('实施项目')

    await wrapper.get('[data-test="create-project"]').trigger('click')
    expect(wrapper.get('[data-test="project-editor"]').text()).toBe('项目表单')
  })

  it('shows the server module and owner credential actions from permissions', async () => {
    const wrapper = mount(ProjectWorkspace, {
      props: {
        currentUser: {
          ...currentUser,
          permissions: [
            ...currentUser.permissions,
            'server:read',
            'server:write',
            'server_credential:read',
            'server_credential:manage',
          ],
        },
      },
      global: {
        stubs: {
          ServerInventoryPanel: {
            props: ['projectId', 'canWrite', 'canViewCredential', 'canManageCredential'],
            template: '<div data-test="server-panel" :data-project-id="projectId" :data-can-write="canWrite" :data-can-view="canViewCredential" :data-can-manage="canManageCredential">服务器模块</div>',
          },
        },
      },
    })
    await flushPromises()

    wrapper.findComponent({ name: 'ElTable' }).vm.$emit('current-change', project)
    await flushPromises()
    await wrapper.get('[data-test="project-module-servers"]').trigger('click')
    const panel = wrapper.get('[data-test="server-panel"]')
    expect(panel.attributes()).toMatchObject({
      'data-project-id': '7',
      'data-can-write': 'true',
      'data-can-view': 'true',
      'data-can-manage': 'true',
    })
  })

  it('shows environment and solution modules with independent write permissions', async () => {
    const wrapper = mount(ProjectWorkspace, {
      props: {
        currentUser: {
          ...currentUser,
          permissions: [
            ...currentUser.permissions,
            'environment_fingerprint:read',
            'environment_fingerprint:write',
            'deployment_solution:read',
          ],
        },
      },
      global: {
        stubs: {
          EnvironmentFingerprintPanel: {
            props: ['projectId', 'canWrite'],
            template: '<div data-test="fingerprint-panel" :data-project-id="projectId" :data-can-write="canWrite" />',
          },
          DeploymentSolutionPanel: {
            props: ['projectId', 'canWrite'],
            template: '<div data-test="solution-panel" :data-project-id="projectId" :data-can-write="canWrite" />',
          },
        },
      },
    })
    await flushPromises()
    wrapper.findComponent({ name: 'ElTable' }).vm.$emit('current-change', project)
    await flushPromises()

    await wrapper.get('[data-test="project-module-fingerprints"]').trigger('click')

    expect(wrapper.get('[data-test="fingerprint-panel"]').attributes()).toMatchObject({
      'data-project-id': '7', 'data-can-write': 'true',
    })
    expect(wrapper.find('[data-test="solution-panel"]').exists()).toBe(false)
    await wrapper.get('[data-test="project-module-solutions"]').trigger('click')
    expect(wrapper.get('[data-test="solution-panel"]').attributes()).toMatchObject({
      'data-project-id': '7', 'data-can-write': 'false',
    })
  })

  it('shows deployment records with independent read and write permissions', async () => {
    const wrapper = mount(ProjectWorkspace, {
      props: {
        currentUser: {
          ...currentUser,
          permissions: [...currentUser.permissions, 'deployment_record:read'],
        },
      },
      global: {
        stubs: {
          DeploymentRecordPanel: {
            props: ['projectId', 'canWrite'],
            template: '<div data-test="record-panel" :data-project-id="projectId" :data-can-write="canWrite" />',
          },
        },
      },
    })
    await flushPromises()
    wrapper.findComponent({ name: 'ElTable' }).vm.$emit('current-change', project)
    await flushPromises()
    await wrapper.get('[data-test="project-module-records"]').trigger('click')
    expect(wrapper.get('[data-test="record-panel"]').attributes()).toMatchObject({
      'data-project-id': '7', 'data-can-write': 'false',
    })
  })

  it('shows the work item module with independent read, write and delete permissions', async () => {
    const wrapper = mount(ProjectWorkspace, {
      props: {
        currentUser: {
          ...currentUser,
          roles: ['ADMIN'],
          permissions: [
            ...currentUser.permissions,
            'work_item:read',
            'work_item:write',
            'work_item:delete',
          ],
        },
      },
      global: {
        stubs: {
          WorkItemPanel: {
            props: ['projectId', 'currentUserId', 'isAdministrator', 'canWrite', 'canDelete'],
            template: '<div data-test="work-item-panel" :data-project-id="projectId" :data-user-id="currentUserId" :data-admin="isAdministrator" :data-can-write="canWrite" :data-can-delete="canDelete" />',
          },
        },
      },
    })
    await flushPromises()
    wrapper.findComponent({ name: 'ElTable' }).vm.$emit('current-change', project)
    await flushPromises()
    await wrapper.get('[data-test="project-module-work-items"]').trigger('click')

    expect(wrapper.get('[data-test="work-item-panel"]').attributes()).toMatchObject({
      'data-project-id': '7',
      'data-user-id': '1',
      'data-admin': 'true',
      'data-can-write': 'true',
      'data-can-delete': 'true',
    })
  })
})
