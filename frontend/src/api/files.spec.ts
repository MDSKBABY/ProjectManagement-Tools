import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  completeProjectFileUpload,
  cancelProjectFileUpload,
  listProjectFiles,
  listProjectFileVersions,
  projectFileDownloadUrl,
  reserveProjectFileMetadata,
  uploadProjectFileChunk,
} from './files'

const clientMocks = vi.hoisted(() => ({ apiRequest: vi.fn() }))

vi.mock('./client', () => clientMocks)

describe('file API', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    clientMocks.apiRequest.mockResolvedValue({})
  })

  it('serializes project file pagination and keyword', async () => {
    await listProjectFiles(7, { page: 2, pageSize: 20, keyword: '部署 手册' })

    expect(clientMocks.apiRequest).toHaveBeenCalledWith(
      '/api/v1/projects/7/files?page=2&pageSize=20&keyword=%E9%83%A8%E7%BD%B2+%E6%89%8B%E5%86%8C',
    )
  })

  it('reserves metadata through the nested project route', async () => {
    const input = {
      originalName: '部署手册.pdf',
      mediaType: 'application/pdf',
      sizeBytes: 1024,
    }
    await reserveProjectFileMetadata(7, input)

    expect(clientMocks.apiRequest).toHaveBeenCalledWith('/api/v1/projects/7/files/metadata', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
  })

  it('targets chunk, completion, version and download routes', async () => {
    const chunk = new Blob(['hello'])
    await uploadProjectFileChunk(7, 11, 2, chunk)
    await completeProjectFileUpload(7, 11)
    await cancelProjectFileUpload(7, 11)
    await listProjectFileVersions(7, 'file-group')

    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(
      1,
      '/api/v1/projects/7/files/11/chunks/2',
      expect.objectContaining({ method: 'PUT', body: chunk }),
    )
    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(
      2,
      '/api/v1/projects/7/files/11/complete',
      { method: 'POST' },
    )
    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(
      3,
      '/api/v1/projects/7/files/11/upload',
      { method: 'DELETE' },
    )
    expect(clientMocks.apiRequest).toHaveBeenNthCalledWith(
      4,
      '/api/v1/projects/7/files/groups/file-group/versions',
    )
    expect(projectFileDownloadUrl(7, 11)).toBe('/api/v1/projects/7/files/11/download')
  })
})
