import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ProjectFilePanel from './ProjectFilePanel.vue'

const fileMocks = vi.hoisted(() => ({
  listProjectFiles: vi.fn(),
  reserveProjectFileMetadata: vi.fn(),
  uploadProjectFileChunk: vi.fn(),
  completeProjectFileUpload: vi.fn(),
  cancelProjectFileUpload: vi.fn(),
  listProjectFileVersions: vi.fn(),
  projectFileDownloadUrl: vi.fn((projectId: number, fileId: number) =>
    `/api/v1/projects/${projectId}/files/${fileId}/download`),
}))

vi.mock('../api/files', () => fileMocks)

describe('ProjectFilePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    fileMocks.listProjectFiles.mockResolvedValue({
      data: [
        {
          id: 1,
          fileGroupId: 'f9d2f6ae-238a-4ad5-9ef0-65a166b66da7',
          version: 1,
          originalName: '部署手册.pdf',
          mediaType: 'application/pdf',
          sizeBytes: 2048,
          sha256: null,
          status: 'RESERVED',
          uploadedBy: { id: 1, displayName: '项目负责人' },
          createdAt: '2026-09-19T10:00:00+08:00',
        },
      ],
      pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
  })

  it('loads project files and exposes the authorized upload entry', async () => {
    const wrapper = mount(ProjectFilePanel, {
      props: { projectId: 7, canWrite: true },
    })
    await flushPromises()

    expect(fileMocks.listProjectFiles).toHaveBeenCalledWith(7, {
      page: 1,
      pageSize: 20,
      keyword: undefined,
    })
    expect(wrapper.text()).toContain('部署手册.pdf')
    expect(wrapper.text()).toContain('等待上传')
    expect(wrapper.find('[data-test="upload-entry"]').exists()).toBe(true)
  })

  it('uploads fixed chunks, retries a transient failure and completes', async () => {
    fileMocks.reserveProjectFileMetadata.mockResolvedValue({
      id: 9,
      chunkSizeBytes: 6,
      totalChunks: 2,
    })
    fileMocks.uploadProjectFileChunk
      .mockRejectedValueOnce(new Error('temporary'))
      .mockResolvedValue(undefined)
    fileMocks.completeProjectFileUpload.mockResolvedValue({ status: 'AVAILABLE' })

    const wrapper = mount(ProjectFilePanel, {
      props: { projectId: 7, canWrite: true },
    })
    await flushPromises()
    const input = wrapper.find('input[type="file"]')
    const file = new File(['hello world'], 'hello.txt', { type: 'text/plain' })
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await flushPromises()

    expect(fileMocks.reserveProjectFileMetadata).toHaveBeenCalledWith(7, {
      originalName: 'hello.txt',
      mediaType: 'text/plain',
      sizeBytes: 11,
      fileGroupId: undefined,
    })
    expect(fileMocks.uploadProjectFileChunk).toHaveBeenCalledTimes(3)
    expect(fileMocks.completeProjectFileUpload).toHaveBeenCalledWith(7, 9)
    expect(wrapper.text()).toContain('上传完成')
  })
})
