import type {
  CreateDeploymentAssetInput,
  DeploymentAsset,
  DeploymentAssetQuery,
  PageResponse,
} from '../types'
import { apiRequest } from './client'

export function listDeploymentAssets(
  projectId: number,
  query: DeploymentAssetQuery,
): Promise<PageResponse<DeploymentAsset>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  const optional: Array<[string, string | undefined]> = [
    ['keyword', query.keyword],
    ['assetType', query.assetType],
    ['operatingSystem', query.operatingSystem],
    ['architecture', query.architecture],
    ['environment', query.environment],
    ['riskLevel', query.riskLevel],
    ['tag', query.tag],
  ]
  optional.forEach(([key, value]) => {
    if (value) parameters.set(key, value)
  })
  return apiRequest<PageResponse<DeploymentAsset>>(
    `/api/v1/projects/${projectId}/deployment-assets?${parameters}`,
  )
}

export function getDeploymentAsset(projectId: number, assetId: number): Promise<DeploymentAsset> {
  return apiRequest<DeploymentAsset>(
    `/api/v1/projects/${projectId}/deployment-assets/${assetId}`,
  )
}

export function createDeploymentAsset(
  projectId: number,
  input: CreateDeploymentAssetInput,
): Promise<DeploymentAsset> {
  return apiRequest<DeploymentAsset>(`/api/v1/projects/${projectId}/deployment-assets`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function listDeploymentAssetVersions(
  projectId: number,
  assetGroupId: string,
): Promise<DeploymentAsset[]> {
  return apiRequest<DeploymentAsset[]>(
    `/api/v1/projects/${projectId}/deployment-assets/groups/${assetGroupId}/versions`,
  )
}
