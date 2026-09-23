import type {
  DeploymentSolution,
  DeploymentSolutionQuery,
  DeploymentSolutionSummary,
  PageResponse,
  SaveDeploymentSolutionInput,
} from '../types'
import { apiRequest } from './client'

export function listDeploymentSolutions(
  projectId: number, query: DeploymentSolutionQuery,
): Promise<PageResponse<DeploymentSolutionSummary>> {
  const parameters = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) })
  if (query.keyword) parameters.set('keyword', query.keyword)
  if (query.status) parameters.set('status', query.status)
  if (query.fingerprintId) parameters.set('fingerprintId', String(query.fingerprintId))
  return apiRequest(`/api/v1/projects/${projectId}/deployment-solutions?${parameters}`)
}

export function getDeploymentSolution(projectId: number, solutionId: number): Promise<DeploymentSolution> {
  return apiRequest(`/api/v1/projects/${projectId}/deployment-solutions/${solutionId}`)
}

export function createDeploymentSolution(
  projectId: number, input: SaveDeploymentSolutionInput,
): Promise<DeploymentSolution> {
  return apiRequest(`/api/v1/projects/${projectId}/deployment-solutions`, jsonRequest('POST', input))
}

export function updateDeploymentSolution(
  projectId: number, solutionId: number, input: SaveDeploymentSolutionInput,
): Promise<DeploymentSolution> {
  return apiRequest(`/api/v1/projects/${projectId}/deployment-solutions/${solutionId}`, jsonRequest('PUT', input))
}

export function deleteDeploymentSolution(projectId: number, solutionId: number): Promise<void> {
  return apiRequest(`/api/v1/projects/${projectId}/deployment-solutions/${solutionId}`, { method: 'DELETE' })
}

function jsonRequest(method: 'POST' | 'PUT', input: SaveDeploymentSolutionInput): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) }
}
