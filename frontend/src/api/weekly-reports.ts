import type { GenerateWeeklyReportInput, WeeklyReport } from '../types'
import { apiRequest } from './client'

const json = (method: 'POST' | 'PUT', body: object): RequestInit => ({
  method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
})
export const listWeeklyReports = (projectId: number): Promise<WeeklyReport[]> =>
  apiRequest(`/api/v1/projects/${projectId}/weekly-reports`)
export const generateWeeklyReport = (projectId: number, input: GenerateWeeklyReportInput): Promise<WeeklyReport> =>
  apiRequest(`/api/v1/projects/${projectId}/weekly-reports/generate`, json('POST', input))
export const updateWeeklyReport = (projectId: number, reportId: number, input: { content: string; nextWeekPlan?: string }): Promise<WeeklyReport> =>
  apiRequest(`/api/v1/projects/${projectId}/weekly-reports/${reportId}`, json('PUT', input))
export const confirmWeeklyReport = (projectId: number, reportId: number): Promise<WeeklyReport> =>
  apiRequest(`/api/v1/projects/${projectId}/weekly-reports/${reportId}/confirm`, { method: 'POST' })
