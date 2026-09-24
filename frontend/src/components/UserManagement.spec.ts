import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PasswordResetDialog from './PasswordResetDialog.vue'
import UserManagement from './UserManagement.vue'

const userMocks = vi.hoisted(() => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUserStatus: vi.fn(),
  resetUserPassword: vi.fn(),
}))

vi.mock('../api/users', () => userMocks)

describe('UserManagement', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    userMocks.listUsers.mockResolvedValue({
      data: [
        {
          id: 2,
          username: 'target-user',
          displayName: '目标用户',
          email: null,
          mobile: null,
          status: 'ACTIVE',
          createdAt: '2026-09-24T10:00:00+08:00',
          updatedAt: '2026-09-24T10:00:00+08:00',
        },
      ],
      pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
  })

  it('resets another users password through the explicit dialog', async () => {
    userMocks.resetUserPassword.mockResolvedValue(undefined)
    const wrapper = mount(UserManagement, {
      props: {
        currentUser: {
          id: 1,
          username: 'admin',
          displayName: '管理员',
          roles: ['ADMIN'],
          permissions: ['user:manage'],
        },
      },
    })
    await flushPromises()

    await wrapper.get('[data-test="reset-password-2"]').trigger('click')
    const dialog = wrapper.findComponent(PasswordResetDialog)
    expect(dialog.props('open')).toBe(true)
    expect(dialog.props('user')?.username).toBe('target-user')
    dialog.vm.$emit('submit', 'Reset-password-123!')
    await flushPromises()

    expect(userMocks.resetUserPassword).toHaveBeenCalledWith(2, 'Reset-password-123!')
    expect(dialog.props('open')).toBe(false)
  })
})
