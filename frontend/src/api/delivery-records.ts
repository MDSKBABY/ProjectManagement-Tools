import { apiRequest } from './client'
import type { MeetingRecordSummary } from '../types'

const json = (body: object): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const listVendors = (projectId: number): Promise<any[]> =>
  apiRequest(`/api/v1/projects/${projectId}/vendors`)

export const createVendor = (projectId: number, input: object): Promise<any> =>
  apiRequest(`/api/v1/projects/${projectId}/vendors`, json(input))

export const listInterfaces = (projectId: number): Promise<any[]> =>
  apiRequest(`/api/v1/projects/${projectId}/interfaces`)

export const createInterface = (projectId: number, input: object): Promise<any> =>
  apiRequest(`/api/v1/projects/${projectId}/interfaces`, json(input))

export const submitInterface = (projectId: number, id: number): Promise<any> =>
  apiRequest(`/api/v1/projects/${projectId}/interfaces/${id}/submit`, { method: 'POST' })

export const listMeetingRecords = (projectId: number): Promise<MeetingRecordSummary[]> =>
  apiRequest(`/api/v1/projects/${projectId}/meeting-records`)

export const createMeetingRecord = (projectId: number, input: object): Promise<any> =>
  apiRequest(`/api/v1/projects/${projectId}/meeting-records`, json(input))

export const submitMeetingRecord = (projectId: number, id: number): Promise<any> =>
  apiRequest(`/api/v1/projects/${projectId}/meeting-records/${id}/submit`, { method: 'POST' })

export const listDesignAssets = (projectId: number): Promise<any[]> =>
  apiRequest(`/api/v1/projects/${projectId}/design-assets`)

export const createDesignAsset = (projectId: number, input: object): Promise<any> =>
  apiRequest(`/api/v1/projects/${projectId}/design-assets`, json(input))
