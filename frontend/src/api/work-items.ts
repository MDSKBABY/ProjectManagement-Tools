import type {
  CreateWorkItemInput,
  CreateWorkItemReminderInput,
  CreateWorkItemRelationInput,
  PageResponse,
  TransitionWorkItemStatusInput,
  UpdateWorkItemInput,
  WorkItem,
  WorkItemQuery,
  WorkItemRelation,
  WorkItemRelationQuery,
  WorkItemReminder,
  WorkItemReminderQuery,
  WorkItemStatusLog,
} from '../types'
import { apiRequest } from './client'

export function listWorkItems(
  projectId: number,
  query: WorkItemQuery,
): Promise<PageResponse<WorkItem>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  const optional: Array<[string, string | number | undefined]> = [
    ['keyword', query.keyword],
    ['type', query.type],
    ['status', query.status],
    ['priority', query.priority],
    ['assigneeId', query.assigneeId],
    ['plannedFrom', query.plannedFrom],
    ['plannedTo', query.plannedTo],
  ]
  optional.forEach(([key, value]) => {
    if (value !== undefined && value !== '') parameters.set(key, String(value))
  })
  return apiRequest<PageResponse<WorkItem>>(
    `/api/v1/projects/${projectId}/work-items?${parameters}`,
  )
}

export function getWorkItem(projectId: number, workItemId: number): Promise<WorkItem> {
  return apiRequest<WorkItem>(`/api/v1/projects/${projectId}/work-items/${workItemId}`)
}

export function createWorkItem(projectId: number, input: CreateWorkItemInput): Promise<WorkItem> {
  return apiRequest<WorkItem>(
    `/api/v1/projects/${projectId}/work-items`,
    jsonRequest('POST', input),
  )
}

export function updateWorkItem(
  projectId: number,
  workItemId: number,
  input: UpdateWorkItemInput,
): Promise<WorkItem> {
  return apiRequest<WorkItem>(
    `/api/v1/projects/${projectId}/work-items/${workItemId}`,
    jsonRequest('PATCH', input),
  )
}

export function deleteWorkItem(projectId: number, workItemId: number): Promise<void> {
  return apiRequest<void>(`/api/v1/projects/${projectId}/work-items/${workItemId}`, {
    method: 'DELETE',
  })
}

export function transitionWorkItemStatus(
  projectId: number,
  workItemId: number,
  input: TransitionWorkItemStatusInput,
): Promise<WorkItem> {
  return apiRequest<WorkItem>(
    `/api/v1/projects/${projectId}/work-items/${workItemId}/status-transitions`,
    jsonRequest('POST', input),
  )
}

export function listWorkItemStatusHistory(
  projectId: number,
  workItemId: number,
): Promise<WorkItemStatusLog[]> {
  return apiRequest<WorkItemStatusLog[]>(
    `/api/v1/projects/${projectId}/work-items/${workItemId}/status-history`,
  )
}

export function listWorkItemRelations(
  projectId: number,
  query: WorkItemRelationQuery,
): Promise<PageResponse<WorkItemRelation>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  if (query.workItemId !== undefined) parameters.set('workItemId', String(query.workItemId))
  if (query.type) parameters.set('type', query.type)
  return apiRequest<PageResponse<WorkItemRelation>>(
    `/api/v1/projects/${projectId}/work-item-relations?${parameters}`,
  )
}

export function createWorkItemRelation(
  projectId: number,
  input: CreateWorkItemRelationInput,
): Promise<WorkItemRelation> {
  return apiRequest<WorkItemRelation>(
    `/api/v1/projects/${projectId}/work-item-relations`,
    jsonRequest('POST', input),
  )
}

export function deleteWorkItemRelation(projectId: number, relationId: number): Promise<void> {
  return apiRequest<void>(`/api/v1/projects/${projectId}/work-item-relations/${relationId}`, {
    method: 'DELETE',
  })
}

export function listWorkItemReminders(
  projectId: number,
  query: WorkItemReminderQuery,
): Promise<PageResponse<WorkItemReminder>> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  })
  const optional: Array<[string, string | number | undefined]> = [
    ['workItemId', query.workItemId],
    ['status', query.status],
    ['from', query.from],
    ['to', query.to],
  ]
  optional.forEach(([key, value]) => {
    if (value !== undefined && value !== '') parameters.set(key, String(value))
  })
  return apiRequest<PageResponse<WorkItemReminder>>(
    `/api/v1/projects/${projectId}/work-item-reminders?${parameters}`,
  )
}

export function createWorkItemReminder(
  projectId: number,
  input: CreateWorkItemReminderInput,
): Promise<WorkItemReminder> {
  return apiRequest<WorkItemReminder>(
    `/api/v1/projects/${projectId}/work-item-reminders`,
    jsonRequest('POST', input),
  )
}

export function dismissWorkItemReminder(
  projectId: number,
  reminderId: number,
): Promise<WorkItemReminder> {
  return apiRequest<WorkItemReminder>(
    `/api/v1/projects/${projectId}/work-item-reminders/${reminderId}/dismiss`,
    jsonRequest('POST'),
  )
}

export function deleteWorkItemReminder(projectId: number, reminderId: number): Promise<void> {
  return apiRequest<void>(
    `/api/v1/projects/${projectId}/work-item-reminders/${reminderId}`,
    { method: 'DELETE' },
  )
}

function jsonRequest(method: 'POST' | 'PATCH', body?: unknown): RequestInit {
  return {
    method,
    ...(body === undefined ? {} : {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),
  }
}
