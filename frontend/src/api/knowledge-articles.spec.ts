import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest } from './client'
import {
  createKnowledgeArticle,
  listKnowledgeArticles,
  reviewKnowledgeArticle,
  submitKnowledgeArticle,
  updateKnowledgeArticle,
} from './knowledge-articles'

vi.mock('./client', () => ({ apiRequest: vi.fn() }))

describe('knowledge article api', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())

  it('serializes list filters', async () => {
    vi.mocked(apiRequest).mockResolvedValue({ data: [], pagination: {} })
    await listKnowledgeArticles(7, {
      page: 2,
      pageSize: 20,
      keyword: '端口冲突',
      status: 'APPROVED',
      tag: 'Linux',
    })
    expect(apiRequest).toHaveBeenCalledWith(
      '/api/v1/projects/7/knowledge-articles?page=2&pageSize=20&keyword=%E7%AB%AF%E5%8F%A3%E5%86%B2%E7%AA%81&status=APPROVED&tag=Linux',
    )
  })

  it('creates, updates, submits and reviews an article', async () => {
    vi.mocked(apiRequest).mockResolvedValue({})
    const input = {
      title: '部署端口冲突',
      scenario: 'Linux 单机部署',
      symptom: '端口已占用',
      cause: '旧进程未退出',
      solution: '停止旧进程',
      tags: ['Linux'],
      attachmentIds: [9],
    }
    await createKnowledgeArticle(7, input)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/knowledge-articles', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
    })
    await updateKnowledgeArticle(7, 3, input)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/knowledge-articles/3', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
    })
    await submitKnowledgeArticle(7, 3)
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/knowledge-articles/3/submit', {
      method: 'POST',
    })
    await reviewKnowledgeArticle(7, 3, { status: 'APPROVED', comment: '可复用' })
    expect(apiRequest).toHaveBeenLastCalledWith('/api/v1/projects/7/knowledge-articles/3/review', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: 'APPROVED', comment: '可复用' }),
    })
  })
})
