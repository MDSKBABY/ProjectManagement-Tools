import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  addProjectMember,
  createProject,
  deleteProject,
  getProject,
  listProjectMembers,
  listProjectMemberCandidates,
  listProjects,
  removeProjectMember,
  updateProject,
  updateProjectMemberRole,
} from './projects'

const clientMocks = vi.hoisted(() => ({ apiRequest: vi.fn() }))

vi.mock('./client', () => clientMocks)

describe('project API', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    clientMocks.apiRequest.mockResolvedValue({})
  })

  it('serializes project pagination and filters', async () => {
    await listProjects({ page: 2, pageSize: 20, keyword: '内部 平台', status: 'ACTIVE' })

    expect(clientMocks.apiRequest).toHaveBeenCalledWith(
      '/api/v1/projects?page=2&pageSize=20&keyword=%E5%86%85%E9%83%A8+%E5%B9%B3%E5%8F%B0&status=ACTIVE',
    )
  })

  it('uses stable project resource routes for create, detail, update and delete', async () => {
    const input = {
      code: 'PM-001',
      name: '内部项目管理平台',
      customerName: '示例客户',
      status: 'PLANNING' as const,
      startDate: '2026-09-20',
      endDate: '2026-12-31',
      tags: ['平台'],
      description: '项目说明',
    }
    const { code: _code, ...updateInput } = input

    await createProject(input)
    await getProject(7)
    await updateProject(7, updateInput)
    await deleteProject(7)

    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(1, '/api/v1/projects', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(2, '/api/v1/projects/7')
    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(3, '/api/v1/projects/7', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(updateInput),
    })
    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(4, '/api/v1/projects/7', {
      method: 'DELETE',
    })
  })

  it('serializes member list filters', async () => {
    await listProjectMembers(9, { page: 1, pageSize: 20, keyword: '张 三' })

    expect(clientMocks.apiRequest).toHaveBeenCalledWith(
      '/api/v1/projects/9/members?page=1&pageSize=20&keyword=%E5%BC%A0+%E4%B8%89',
    )
  })

  it('searches addable member candidates without using the administrator user API', async () => {
    await listProjectMemberCandidates(9, { page: 1, pageSize: 20, keyword: '李 四' })

    expect(clientMocks.apiRequest).toHaveBeenCalledWith(
      '/api/v1/projects/9/members/candidates?page=1&pageSize=20&keyword=%E6%9D%8E+%E5%9B%9B',
    )
  })

  it('targets nested member resources for add, role update and removal', async () => {
    await addProjectMember(9, { userId: 12, role: 'MEMBER' })
    await updateProjectMemberRole(9, 12, 'MANAGER')
    await removeProjectMember(9, 12)

    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(1, '/api/v1/projects/9/members', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: 12, role: 'MEMBER' }),
    })
    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(2, '/api/v1/projects/9/members/12', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role: 'MANAGER' }),
    })
    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(3, '/api/v1/projects/9/members/12', {
      method: 'DELETE',
    })
  })
})
