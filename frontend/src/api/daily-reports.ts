import type { DailyReport, DailyReportQuery, SaveDailyReportInput } from '../types'
import { apiRequest } from './client'

export function listDailyReports(
  projectId: number,
  query: DailyReportQuery = {},
): Promise<DailyReport[]> {
  const parameters = new URLSearchParams()
  if (query.from) parameters.set('from', query.from)
  if (query.to) parameters.set('to', query.to)
  if (query.reporterId !== undefined) parameters.set('reporterId', String(query.reporterId))
  const suffix = parameters.size ? `?${parameters}` : ''
  return apiRequest<DailyReport[]>(`/api/v1/projects/${projectId}/daily-reports${suffix}`)
}

export function createDailyReport(
  projectId: number,
  input: SaveDailyReportInput,
): Promise<DailyReport> {
  return apiRequest(`/api/v1/projects/${projectId}/daily-reports`, jsonRequest('POST', input))
}

export function updateDailyReport(
  projectId: number,
  reportId: number,
  input: SaveDailyReportInput,
): Promise<DailyReport> {
  return apiRequest(
    `/api/v1/projects/${projectId}/daily-reports/${reportId}`,
    jsonRequest('PUT', input),
  )
}

export function polishDailyReport(projectId: number, reportId: number): Promise<DailyReport> {
  return apiRequest(`/api/v1/projects/${projectId}/daily-reports/${reportId}/polish`, { method: 'POST' })
}

export function confirmDailyReport(projectId: number, reportId: number): Promise<DailyReport> {
  return apiRequest(`/api/v1/projects/${projectId}/daily-reports/${reportId}/confirm`, { method: 'POST' })
}

function jsonRequest(method: 'POST' | 'PUT', input: object): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) }
}
