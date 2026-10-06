import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import KnowledgeArticlePanel from './KnowledgeArticlePanel.vue'

const knowledgeMocks = vi.hoisted(() => ({
  listKnowledgeArticles: vi.fn(),
  getKnowledgeArticle: vi.fn(),
  createKnowledgeArticle: vi.fn(),
  updateKnowledgeArticle: vi.fn(),
  submitKnowledgeArticle: vi.fn(),
  reviewKnowledgeArticle: vi.fn(),
  deleteKnowledgeArticle: vi.fn(),
}))
const fileMocks = vi.hoisted(() => ({ listProjectFiles: vi.fn() }))

vi.mock('../api/knowledge-articles', () => knowledgeMocks)
vi.mock('../api/files', () => fileMocks)

const summary = {
  id: 3, title: '部署端口冲突', scenario: 'Linux 单机部署', tags: ['Linux'],
  status: 'PENDING_REVIEW', createdBy: 2, createdByDisplayName: '实施人员',
  attachmentCount: 1, updatedAt: '2026-09-24T10:00:00Z',
}
const detail = {
  ...summary,
  projectId: 7,
  symptom: '端口已占用',
  cause: '旧进程未退出',
  solution: '停止旧进程',
  applicableConditions: 'Linux',
  reviewComment: null,
  reviewedBy: null,
  reviewedByDisplayName: null,
  reviewedAt: null,
  createdAt: '2026-09-24T09:00:00Z',
  attachments: [{ id: 9, originalName: 'solution.md', mediaType: 'text/markdown', sizeBytes: 100 }],
}

describe('KnowledgeArticlePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    knowledgeMocks.listKnowledgeArticles.mockResolvedValue({
      data: [summary],
      pagination: { page: 1, pageSize: 20, totalItems: 1, totalPages: 1 },
    })
    knowledgeMocks.getKnowledgeArticle.mockResolvedValue(detail)
    fileMocks.listProjectFiles.mockResolvedValue({ data: [], pagination: {} })
  })

  it('loads articles and opens reviewable details', async () => {
    const wrapper = mount(KnowledgeArticlePanel, {
      props: { projectId: 7, currentUserId: 1, canWrite: true, canReview: true },
    })
    await flushPromises()

    expect(knowledgeMocks.listKnowledgeArticles).toHaveBeenCalledWith(7, {
      page: 1, pageSize: 20, keyword: undefined, status: undefined, tag: undefined,
    })
    expect(wrapper.text()).toContain('部署端口冲突')
    expect(wrapper.find('[data-test="create-knowledge-article"]').exists()).toBe(true)

    wrapper.findComponent({ name: 'ElTable' }).vm.$emit('current-change', summary)
    await flushPromises()
    expect(knowledgeMocks.getKnowledgeArticle).toHaveBeenCalledWith(7, 3)
    expect(wrapper.text()).toContain('停止旧进程')
    expect(wrapper.find('[data-test="approve-knowledge-article"]').exists()).toBe(true)
  })

  it('hides writing and review controls for read-only users', async () => {
    const wrapper = mount(KnowledgeArticlePanel, {
      props: { projectId: 7, currentUserId: 8, canWrite: false, canReview: false },
    })
    await flushPromises()
    expect(wrapper.find('[data-test="create-knowledge-article"]').exists()).toBe(false)
    wrapper.findComponent({ name: 'ElTable' }).vm.$emit('current-change', summary)
    await flushPromises()
    expect(wrapper.find('[data-test="approve-knowledge-article"]').exists()).toBe(false)
  })
})
