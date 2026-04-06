import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type {
  Lead,
  LeadSummary,
  LeadNote,
  CreateLeadRequest,
  UpdateLeadRequest,
  StageTransitionRequest,
  ConvertLeadRequest,
  CreateLeadNoteRequest,
} from '@/types/domain'

export interface LeadListParams {
  page?: number
  size?: number
  sort?: string
  order?: 'asc' | 'desc'
  stage?: string
  leadSourceId?: string
  assignedStaffId?: string
  branchId?: string
  search?: string
}

export async function getLeadList(
  params: LeadListParams = {},
): Promise<PageResponse<LeadSummary>> {
  const { data } = await apiClient.get<PageResponse<LeadSummary>>(
    '/leads',
    { params },
  )
  return data
}

export async function getLead(id: string): Promise<Lead> {
  const { data } = await apiClient.get<Lead>(`/leads/${id}`)
  return data
}

export async function createLead(
  request: CreateLeadRequest,
): Promise<Lead> {
  const { data } = await apiClient.post<Lead>('/leads', request)
  return data
}

export async function updateLead(
  id: string,
  request: UpdateLeadRequest,
): Promise<Lead> {
  const { data } = await apiClient.patch<Lead>(`/leads/${id}`, request)
  return data
}

export async function moveLeadStage(
  id: string,
  request: StageTransitionRequest,
): Promise<Lead> {
  const { data } = await apiClient.patch<Lead>(
    `/leads/${id}/stage`,
    request,
  )
  return data
}

export async function convertLead(
  id: string,
  request: ConvertLeadRequest,
): Promise<Lead> {
  const { data } = await apiClient.post<Lead>(
    `/leads/${id}/convert`,
    request,
  )
  return data
}

export async function deleteLead(id: string): Promise<void> {
  await apiClient.delete(`/leads/${id}`)
}

// ── Notes ───────────────────────────────────────────────────────────────────

export async function getLeadNotes(leadId: string): Promise<LeadNote[]> {
  const { data } = await apiClient.get<LeadNote[]>(
    `/leads/${leadId}/notes`,
  )
  return data
}

export async function addLeadNote(
  leadId: string,
  request: CreateLeadNoteRequest,
): Promise<LeadNote> {
  const { data } = await apiClient.post<LeadNote>(
    `/leads/${leadId}/notes`,
    request,
  )
  return data
}

// ── Query key factories for TanStack Query ──────────────────────────────────

export const leadKeys = {
  all: ['leads'] as const,
  lists: () => [...leadKeys.all, 'list'] as const,
  list: (params: LeadListParams) => [...leadKeys.lists(), params] as const,
  details: () => [...leadKeys.all, 'detail'] as const,
  detail: (id: string) => [...leadKeys.details(), id] as const,
  notes: (leadId: string) =>
    [...leadKeys.detail(leadId), 'notes'] as const,
}
