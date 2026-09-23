import type {
  CreateFileMetadataInput,
  FileAsset,
  FileAssetQuery,
  PageResponse,
} from '../types'
import { apiRequest } from './client'

export function listProjectFiles(
  projectId: number,
  query: FileAssetQuery,
): Promise<PageResponse<FileAsset>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  if (query.keyword) parameters.set('keyword', query.keyword)
  return apiRequest<PageResponse<FileAsset>>(
    `/api/v1/projects/${projectId}/files?${parameters}`,
  )
}

/** 先预留不可下载的文件元数据，再使用返回的分片参数上传内容。 */
export function reserveProjectFileMetadata(
  projectId: number,
  input: CreateFileMetadataInput,
): Promise<FileAsset> {
  return apiRequest<FileAsset>(`/api/v1/projects/${projectId}/files/metadata`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function uploadProjectFileChunk(
  projectId: number,
  fileId: number,
  chunkIndex: number,
  content: Blob,
  signal?: AbortSignal,
): Promise<void> {
  return apiRequest<void>(
    `/api/v1/projects/${projectId}/files/${fileId}/chunks/${chunkIndex}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: content,
      signal,
    },
  )
}

export function completeProjectFileUpload(projectId: number, fileId: number): Promise<FileAsset> {
  return apiRequest<FileAsset>(`/api/v1/projects/${projectId}/files/${fileId}/complete`, {
    method: 'POST',
  })
}

export function cancelProjectFileUpload(projectId: number, fileId: number): Promise<void> {
  return apiRequest<void>(`/api/v1/projects/${projectId}/files/${fileId}/upload`, {
    method: 'DELETE',
  })
}

export function listProjectFileVersions(
  projectId: number,
  fileGroupId: string,
): Promise<FileAsset[]> {
  return apiRequest<FileAsset[]>(
    `/api/v1/projects/${projectId}/files/groups/${fileGroupId}/versions`,
  )
}

export function projectFileDownloadUrl(projectId: number, fileId: number): string {
  return `/api/v1/projects/${projectId}/files/${fileId}/download`
}
