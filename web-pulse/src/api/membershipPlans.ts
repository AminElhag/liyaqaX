import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type {
  MembershipPlan,
  MembershipPlanSummary,
  CreateMembershipPlanRequest,
  UpdateMembershipPlanRequest,
} from '@/types/domain'

export interface MembershipPlanListParams {
  page?: number
  size?: number
  sort?: string
  order?: 'asc' | 'desc'
}

export async function getMembershipPlanList(
  params: MembershipPlanListParams = {},
): Promise<PageResponse<MembershipPlanSummary>> {
  const { data } = await apiClient.get<PageResponse<MembershipPlanSummary>>(
    '/membership-plans',
    { params },
  )
  return data
}

export async function getMembershipPlan(
  id: string,
): Promise<MembershipPlan> {
  const { data } = await apiClient.get<MembershipPlan>(
    `/membership-plans/${id}`,
  )
  return data
}

export async function createMembershipPlan(
  request: CreateMembershipPlanRequest,
): Promise<MembershipPlan> {
  const { data } = await apiClient.post<MembershipPlan>(
    '/membership-plans',
    request,
  )
  return data
}

export async function updateMembershipPlan(
  id: string,
  request: UpdateMembershipPlanRequest,
): Promise<MembershipPlan> {
  const { data } = await apiClient.patch<MembershipPlan>(
    `/membership-plans/${id}`,
    request,
  )
  return data
}

export async function deleteMembershipPlan(id: string): Promise<void> {
  await apiClient.delete(`/membership-plans/${id}`)
}

// ── Query key factories for TanStack Query ──────────────────────────────────

export const membershipPlanKeys = {
  all: ['membership-plans'] as const,
  lists: () => [...membershipPlanKeys.all, 'list'] as const,
  list: (params: MembershipPlanListParams) =>
    [...membershipPlanKeys.lists(), params] as const,
  details: () => [...membershipPlanKeys.all, 'detail'] as const,
  detail: (id: string) => [...membershipPlanKeys.details(), id] as const,
}
