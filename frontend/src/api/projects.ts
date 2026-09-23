import type {
  AddProjectMemberInput,
  EditableProjectMemberRole,
  PageResponse,
  Project,
  ProjectCreateInput,
  ProjectMember,
  ProjectMemberCandidate,
  ProjectMemberQuery,
  ProjectQuery,
  ProjectUpdateInput,
} from '../types'
import { apiRequest } from './client'

export function listProjects(query: ProjectQuery): Promise<PageResponse<Project>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  if (query.keyword) parameters.set('keyword', query.keyword)
  if (query.status) parameters.set('status', query.status)
  return apiRequest<PageResponse<Project>>(`/api/v1/projects?${parameters}`)
}

export function getProject(projectId: number): Promise<Project> {
  return apiRequest<Project>(`/api/v1/projects/${projectId}`)
}

export function createProject(input: ProjectCreateInput): Promise<Project> {
  return apiRequest<Project>('/api/v1/projects', jsonRequest('POST', input))
}

export function updateProject(projectId: number, input: ProjectUpdateInput): Promise<Project> {
  return apiRequest<Project>(`/api/v1/projects/${projectId}`, jsonRequest('PUT', input))
}

export function deleteProject(projectId: number): Promise<void> {
  return apiRequest<void>(`/api/v1/projects/${projectId}`, { method: 'DELETE' })
}

export function listProjectMembers(
  projectId: number,
  query: ProjectMemberQuery,
): Promise<PageResponse<ProjectMember>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  if (query.keyword) parameters.set('keyword', query.keyword)
  return apiRequest<PageResponse<ProjectMember>>(
    `/api/v1/projects/${projectId}/members?${parameters}`,
  )
}

export function listProjectMemberCandidates(
  projectId: number,
  query: ProjectMemberQuery,
): Promise<PageResponse<ProjectMemberCandidate>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  if (query.keyword) parameters.set('keyword', query.keyword)
  return apiRequest<PageResponse<ProjectMemberCandidate>>(
    `/api/v1/projects/${projectId}/members/candidates?${parameters}`,
  )
}

export function addProjectMember(
  projectId: number,
  input: AddProjectMemberInput,
): Promise<ProjectMember> {
  return apiRequest<ProjectMember>(
    `/api/v1/projects/${projectId}/members`,
    jsonRequest('POST', input),
  )
}

export function updateProjectMemberRole(
  projectId: number,
  userId: number,
  role: EditableProjectMemberRole,
): Promise<ProjectMember> {
  return apiRequest<ProjectMember>(
    `/api/v1/projects/${projectId}/members/${userId}`,
    jsonRequest('PUT', { role }),
  )
}

export function removeProjectMember(projectId: number, userId: number): Promise<void> {
  return apiRequest<void>(`/api/v1/projects/${projectId}/members/${userId}`, {
    method: 'DELETE',
  })
}

function jsonRequest(method: 'POST' | 'PUT', body: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}
