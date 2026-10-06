import type {
  KnowledgeArticle,
  KnowledgeArticleQuery,
  KnowledgeArticleSummary,
  PageResponse,
  ReviewKnowledgeArticleInput,
  SaveKnowledgeArticleInput,
} from '../types'
import { apiRequest } from './client'

export function listKnowledgeArticles(
  projectId: number,
  query: KnowledgeArticleQuery,
): Promise<PageResponse<KnowledgeArticleSummary>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  const optional: Array<[string, string | undefined]> = [
    ['keyword', query.keyword],
    ['status', query.status],
    ['tag', query.tag],
  ]
  optional.forEach(([key, value]) => {
    if (value) parameters.set(key, value)
  })
  return apiRequest<PageResponse<KnowledgeArticleSummary>>(
    `/api/v1/projects/${projectId}/knowledge-articles?${parameters}`,
  )
}
export function getKnowledgeArticle(
  projectId: number,
  articleId: number,
): Promise<KnowledgeArticle> {
  return apiRequest<KnowledgeArticle>(
    `/api/v1/projects/${projectId}/knowledge-articles/${articleId}`,
  )
}

export function createKnowledgeArticle(
  projectId: number,
  input: SaveKnowledgeArticleInput,
): Promise<KnowledgeArticle> {
  return apiRequest<KnowledgeArticle>(`/api/v1/projects/${projectId}/knowledge-articles`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateKnowledgeArticle(
  projectId: number,
  articleId: number,
  input: SaveKnowledgeArticleInput,
): Promise<KnowledgeArticle> {
  return apiRequest<KnowledgeArticle>(
    `/api/v1/projects/${projectId}/knowledge-articles/${articleId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  )
}

export function submitKnowledgeArticle(
  projectId: number,
  articleId: number,
): Promise<KnowledgeArticle> {
  return apiRequest<KnowledgeArticle>(
    `/api/v1/projects/${projectId}/knowledge-articles/${articleId}/submit`,
    { method: 'POST' },
  )
}

export function reviewKnowledgeArticle(
  projectId: number,
  articleId: number,
  input: ReviewKnowledgeArticleInput,
): Promise<KnowledgeArticle> {
  return apiRequest<KnowledgeArticle>(
    `/api/v1/projects/${projectId}/knowledge-articles/${articleId}/review`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  )
}

export function deleteKnowledgeArticle(
  projectId: number,
  articleId: number,
): Promise<void> {
  return apiRequest<void>(
    `/api/v1/projects/${projectId}/knowledge-articles/${articleId}`,
    { method: 'DELETE' },
  )
}
