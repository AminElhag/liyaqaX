import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type {
  Membership,
  MembershipSummary,
  AssignMembershipRequest,
} from '@/types/domain'

export async function assignMembership(
  memberId: string,
  request: AssignMembershipRequest,
): Promise<Membership> {
  const { data } = await apiClient.post<Membership>(
    `/members/${memberId}/memberships`,
    request,
  )
  return data
}

export async function getActiveMembership(
  memberId: string,
): Promise<Membership | null> {
  const response = await apiClient.get<Membership>(
    `/members/${memberId}/memberships/active`,
    { validateStatus: (status) => status === 200 || status === 204 },
  )
  if (response.status === 204) return null
  return response.data
}

export interface MembershipHistoryParams {
  page?: number
  size?: number
}

export async function getMembershipHistory(
  memberId: string,
  params: MembershipHistoryParams = {},
): Promise<PageResponse<MembershipSummary>> {
  const { data } = await apiClient.get<PageResponse<MembershipSummary>>(
    `/members/${memberId}/memberships`,
    { params },
  )
  return data
}

// ── Query key factories for TanStack Query ──────────────────────────────────

export const membershipKeys = {
  all: ['memberships'] as const,
  active: (memberId: string) =>
    [...membershipKeys.all, 'active', memberId] as const,
  histories: () => [...membershipKeys.all, 'history'] as const,
  history: (memberId: string, params: MembershipHistoryParams) =>
    [...membershipKeys.histories(), memberId, params] as const,
}
