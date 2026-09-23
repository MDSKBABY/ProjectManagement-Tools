import type {
  CreateDeploymentRecordInput,
  DeploymentRecord,
  DeploymentRecordQuery,
  DeploymentRecordSummary,
  PageResponse,
  SimilarDeployment,
} from '../types'
import { apiRequest } from './client'

export function listDeploymentRecords(
  projectId: number, query: DeploymentRecordQuery,
): Promise<PageResponse<DeploymentRecordSummary>> {
  const parameters = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) })
  if (query.result) parameters.set('result', query.result)
  if (query.serverId) parameters.set('serverId', String(query.serverId))
  if (query.baseline !== undefined) parameters.set('baseline', String(query.baseline))
  if (query.executedFrom) parameters.set('executedFrom', query.executedFrom)
  if (query.executedTo) parameters.set('executedTo', query.executedTo)
  return apiRequest(`/api/v1/projects/${projectId}/deployment-records?${parameters}`)
}

export function getDeploymentRecord(projectId: number, recordId: number): Promise<DeploymentRecord> {
  return apiRequest(`/api/v1/projects/${projectId}/deployment-records/${recordId}`)
}

export function createDeploymentRecord(
  projectId: number, input: CreateDeploymentRecordInput,
): Promise<DeploymentRecord> {
  return apiRequest(`/api/v1/projects/${projectId}/deployment-records`, jsonRequest('POST', input))
}

export function updateDeploymentBaseline(
  projectId: number, recordId: number, baseline: boolean,
): Promise<DeploymentRecord> {
  return apiRequest(
    `/api/v1/projects/${projectId}/deployment-records/${recordId}/baseline`,
    jsonRequest('PUT', { baseline }),
  )
}

export function findSimilarDeployments(
  projectId: number, fingerprintId: number, limit = 10,
): Promise<SimilarDeployment[]> {
  const parameters = new URLSearchParams({ fingerprintId: String(fingerprintId), limit: String(limit) })
  return apiRequest(`/api/v1/projects/${projectId}/deployment-records/similar?${parameters}`)
}

function jsonRequest(method: 'POST' | 'PUT', input: object): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) }
}
