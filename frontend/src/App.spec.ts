import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from './api/client'
import App from './App.vue'

const authMocks = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('./api/auth', () => authMocks)

const adminUser = {
  id: 1,
  username: 'admin',
  displayName: '系统管理员',
  roles: ['ADMIN'],
  permissions: ['user:manage'],
}

describe('App', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('shows the login form when no server session exists', async () => {
    authMocks.getCurrentUser.mockRejectedValue(
      new ApiError(401, 'AUTHENTICATION_REQUIRED', '请先登录'),
    )

    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('把项目过程留在团队里')
    expect(wrapper.find('input[name="username"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('请先登录')
  })

  it('logs in and opens user management for an authorized administrator', async () => {
    authMocks.getCurrentUser.mockRejectedValue(
      new ApiError(401, 'AUTHENTICATION_REQUIRED', '请先登录'),
    )
    authMocks.login.mockResolvedValue(adminUser)

    const wrapper = mount(App, {
      global: {
        stubs: {
          UserManagement: {
            props: ['currentUser'],
            template: '<section data-test="user-management">用户管理</section>',
          },
        },
      },
    })
    await flushPromises()

    await wrapper.get('input[name="username"]').setValue(' admin ')
    await wrapper.get('input[name="password"]').setValue('test-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(authMocks.login).toHaveBeenCalledWith({
      username: 'admin',
      password: 'test-password',
    })
    expect(wrapper.get('[data-test="user-management"]').text()).toBe('用户管理')
    expect(wrapper.text()).toContain('系统管理员')
  })

  it('restores an existing session and logs out', async () => {
    authMocks.getCurrentUser.mockResolvedValue(adminUser)
    authMocks.logout.mockResolvedValue(undefined)

    const wrapper = mount(App, {
      global: {
        stubs: {
          UserManagement: { template: '<section>用户管理</section>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('系统管理员')
    await wrapper.get('.account-area button').trigger('click')
    await flushPromises()

    expect(authMocks.logout).toHaveBeenCalledOnce()
    expect(wrapper.find('input[name="username"]').exists()).toBe(true)
  })
})
