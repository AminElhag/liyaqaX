import { apiClient } from './client'
import type {
  LeadSource,
  CreateLeadSourceRequest,
  UpdateLeadSourceRequest,
} from '@/types/domain'

export async function getLeadSources(): Promise<LeadSource[]> {
  const { data } = await apiClient.get<LeadSource[]>('/lead-sources')
  return data
}

export async function createLeadSource(
  request: CreateLeadSourceRequest,
): Promise<LeadSource> {
  const { data } = await apiClient.post<LeadSource>(
    '/lead-sources',
    request,
  )
  return data
}

export async function updateLeadSource(
  id: string,
  request: UpdateLeadSourceRequest,
): Promise<LeadSource> {
  const { data } = await apiClient.patch<LeadSource>(
    `/lead-sources/${id}`,
    request,
  )
  return data
}

export async function toggleLeadSource(id: string): Promise<LeadSource> {
  const { data } = await apiClient.patch<LeadSource>(
    `/lead-sources/${id}/toggle`,
  )
  return data
}

// ── Query key factories for TanStack Query ──────────────────────────────────

export const leadSourceKeys = {
  all: ['lead-sources'] as const,
  list: () => [...leadSourceKeys.all, 'list'] as const,
}
