import type {
  PageResponse,
  SaveServerCredentialInput,
  SaveServerInput,
  ServerCredential,
  ServerQuery,
  ServerRecord,
} from '../types'
import { apiRequest } from './client'

export function listServers(
  projectId: number,
  query: ServerQuery,
): Promise<PageResponse<ServerRecord>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  if (query.keyword) parameters.set('keyword', query.keyword)
  if (query.environment) parameters.set('environment', query.environment)
  if (query.status) parameters.set('status', query.status)
  return apiRequest<PageResponse<ServerRecord>>(
    `/api/v1/projects/${projectId}/servers?${parameters}`,
  )
}

export function createServer(projectId: number, input: SaveServerInput): Promise<ServerRecord> {
  return apiRequest<ServerRecord>(`/api/v1/projects/${projectId}/servers`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateServer(
  projectId: number,
  serverId: number,
  input: SaveServerInput,
): Promise<ServerRecord> {
  return apiRequest<ServerRecord>(`/api/v1/projects/${projectId}/servers/${serverId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function deleteServer(projectId: number, serverId: number): Promise<void> {
  return apiRequest<void>(`/api/v1/projects/${projectId}/servers/${serverId}`, {
    method: 'DELETE',
  })
}

export function saveServerCredential(
  projectId: number,
  serverId: number,
  input: SaveServerCredentialInput,
): Promise<void> {
  return apiRequest<void>(`/api/v1/projects/${projectId}/servers/${serverId}/credential`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

/** 此响应含明文凭据，调用组件关闭弹窗时必须立即清除引用。 */
export function viewServerCredential(
  projectId: number,
  serverId: number,
): Promise<ServerCredential> {
  return apiRequest<ServerCredential>(
    `/api/v1/projects/${projectId}/servers/${serverId}/credential`,
  )
}
