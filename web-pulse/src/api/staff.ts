import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type { StaffMemberSummary, StaffMember } from '@/types/domain'

export interface StaffListParams {
  page?: number
  size?: number
}

export async function getStaffList(
  params: StaffListParams = {},
): Promise<PageResponse<StaffMemberSummary>> {
  const { data } = await apiClient.get<PageResponse<StaffMemberSummary>>(
    '/staff',
    { params },
  )
  return data
}

export async function getStaffMember(id: string): Promise<StaffMember> {
  const { data } = await apiClient.get<StaffMember>(`/staff/${id}`)
  return data
}

// Query key factories for TanStack Query
export const staffKeys = {
  all: ['staff'] as const,
  lists: () => [...staffKeys.all, 'list'] as const,
  list: (params: StaffListParams) => [...staffKeys.lists(), params] as const,
  details: () => [...staffKeys.all, 'detail'] as const,
  detail: (id: string) => [...staffKeys.details(), id] as const,
}
