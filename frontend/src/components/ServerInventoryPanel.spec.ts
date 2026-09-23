import { flushPromises, mount } from '@vue/test-utils'
import { ElDialog } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ServerInventoryPanel from './ServerInventoryPanel.vue'

const serverMocks = vi.hoisted(() => ({
  listServers: vi.fn(),
  createServer: vi.fn(),
  updateServer: vi.fn(),
  deleteServer: vi.fn(),
  saveServerCredential: vi.fn(),
  viewServerCredential: vi.fn(),
}))

vi.mock('../api/servers', () => serverMocks)

const server = {
  id: 9,
  projectId: 7,
  name: '生产应用节点',
  host: 'app-01.internal',
  port: 22,
  environment: 'PRODUCTION',
  status: 'ACTIVE',
  operatingSystem: 'Linux',
  architecture: 'amd64',
  purpose: '应用服务',
  description: '核心生产节点',
  credentialConfigured: true,
  createdAt: '2026-09-20T00:00:00+08:00',
  updatedAt: '2026-09-20T00:00:00+08:00',
}

describe('ServerInventoryPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    serverMocks.listServers.mockResolvedValue({
      data: [server],
      pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
  })

  it('loads server inventory and masks credentials by default', async () => {
    const wrapper = mount(ServerInventoryPanel, {
      props: {
        projectId: 7,
        canWrite: true,
        canViewCredential: true,
        canManageCredential: true,
      },
    })
    await flushPromises()

    expect(serverMocks.listServers).toHaveBeenCalledWith(7, {
      page: 1,
      pageSize: 20,
      keyword: undefined,
      environment: undefined,
      status: undefined,
    })
    expect(wrapper.text()).toContain('生产应用节点')
    expect(wrapper.text()).toContain('已配置（默认隐藏）')
    expect(wrapper.text()).not.toContain('test-only-visible-password')
    expect(wrapper.get('[data-test="create-server"]')).toBeTruthy()
  })

  it('only decrypts after explicit action and clears plaintext when the dialog closes', async () => {
    serverMocks.viewServerCredential.mockResolvedValue({
      username: 'deploy_user',
      password: 'test-only-visible-password',
    })
    const wrapper = mount(ServerInventoryPanel, {
      props: {
        projectId: 7,
        canWrite: false,
        canViewCredential: true,
        canManageCredential: false,
      },
    })
    await flushPromises()

    expect(serverMocks.viewServerCredential).not.toHaveBeenCalled()
    await wrapper.get('[data-test="view-server-credential"]').trigger('click')
    await flushPromises()
    expect(serverMocks.viewServerCredential).toHaveBeenCalledWith(7, 9)
    expect(wrapper.text()).toContain('deploy_user')
    expect(wrapper.text()).toContain('test-only-visible-password')

    const credentialDialog = wrapper
      .findAllComponents(ElDialog)
      .find((dialog) => dialog.props('title') === '查看服务器凭据')
    credentialDialog?.vm.$emit('update:modelValue', false)
    await flushPromises()
    expect(wrapper.text()).not.toContain('test-only-visible-password')
  })

  it('hides every credential action from ordinary project members', async () => {
    const wrapper = mount(ServerInventoryPanel, {
      props: {
        projectId: 7,
        canWrite: false,
        canViewCredential: false,
        canManageCredential: false,
      },
    })
    await flushPromises()

    expect(wrapper.find('[data-test="view-server-credential"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="edit-server-credential"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="create-server"]').exists()).toBe(false)
  })

  it('clears unsaved credential input when the editor closes', async () => {
    const wrapper = mount(ServerInventoryPanel, {
      props: {
        projectId: 7,
        canWrite: false,
        canViewCredential: false,
        canManageCredential: true,
      },
    })
    await flushPromises()

    await wrapper.get('[data-test="edit-server-credential"]').trigger('click')
    const credentialForm = wrapper.get('form[aria-label="服务器凭据表单"]')
    const fields = credentialForm.findAll('input')
    const username = fields.find((field) => field.attributes('autocomplete') === 'off')
    const password = fields.find((field) => field.attributes('autocomplete') === 'new-password')
    await username?.setValue('unsaved-user')
    await password?.setValue('unsaved-password')

    const editor = wrapper
      .findAllComponents(ElDialog)
      .find((dialog) => dialog.props('title') === '配置服务器凭据')
    editor?.vm.$emit('update:modelValue', false)
    await flushPromises()
    await wrapper.get('[data-test="edit-server-credential"]').trigger('click')

    const reopenedFields = wrapper.get('form[aria-label="服务器凭据表单"]').findAll('input')
    const reopenedUsername = reopenedFields.find((field) => field.attributes('autocomplete') === 'off')
    const reopenedPassword = reopenedFields.find((field) => field.attributes('autocomplete') === 'new-password')
    expect(reopenedUsername?.element.value).toBe('')
    expect(reopenedPassword?.element.value).toBe('')
  })
})
