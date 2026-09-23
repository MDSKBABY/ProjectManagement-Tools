import type { AuditLogEntry, AuditLogQuery, PageResponse } from '../types'
import { apiRequest } from './client'

export function listAuditLogs(query: AuditLogQuery): Promise<PageResponse<AuditLogEntry>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  if (query.actorId !== undefined) parameters.set('actorId', String(query.actorId))
  if (query.action) parameters.set('action', query.action)
  if (query.resourceType) parameters.set('resourceType', query.resourceType)
  if (query.outcome) parameters.set('outcome', query.outcome)
  if (query.createdFrom) parameters.set('createdFrom', query.createdFrom)
  if (query.createdTo) parameters.set('createdTo', query.createdTo)
  return apiRequest<PageResponse<AuditLogEntry>>(`/api/v1/admin/audit-logs?${parameters}`)
}
