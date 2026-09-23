import type {
  EnvironmentFingerprint,
  EnvironmentFingerprintQuery,
  PageResponse,
  SaveEnvironmentFingerprintInput,
} from '../types'
import { apiRequest } from './client'

export function listEnvironmentFingerprints(
  projectId: number,
  query: EnvironmentFingerprintQuery,
): Promise<PageResponse<EnvironmentFingerprint>> {
  const parameters = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) })
  const optional: Array<[string, string | undefined]> = [
    ['keyword', query.keyword], ['environment', query.environment],
    ['operatingSystem', query.operatingSystem], ['architecture', query.architecture],
    ['databaseName', query.databaseName], ['middleware', query.middleware], ['tag', query.tag],
  ]
  optional.forEach(([key, value]) => { if (value) parameters.set(key, value) })
  return apiRequest(`/api/v1/projects/${projectId}/environment-fingerprints?${parameters}`)
}

export function createEnvironmentFingerprint(
  projectId: number, input: SaveEnvironmentFingerprintInput,
): Promise<EnvironmentFingerprint> {
  return apiRequest(`/api/v1/projects/${projectId}/environment-fingerprints`, jsonRequest('POST', input))
}

export function updateEnvironmentFingerprint(
  projectId: number, fingerprintId: number, input: SaveEnvironmentFingerprintInput,
): Promise<EnvironmentFingerprint> {
  return apiRequest(`/api/v1/projects/${projectId}/environment-fingerprints/${fingerprintId}`, jsonRequest('PUT', input))
}

export function deleteEnvironmentFingerprint(projectId: number, fingerprintId: number): Promise<void> {
  return apiRequest(`/api/v1/projects/${projectId}/environment-fingerprints/${fingerprintId}`, { method: 'DELETE' })
}

function jsonRequest(method: 'POST' | 'PUT', input: SaveEnvironmentFingerprintInput): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) }
}
