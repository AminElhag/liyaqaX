import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type {
  Membership,
  MembershipSummary,
  AssignMembershipRequest,
  RenewMembershipRequest,
  FreezeMembershipRequest,
  UnfreezeMembershipRequest,
  TerminateMembershipRequest,
  ExpiringMembership,
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

// ── Lifecycle operations ────────────────────────────────────────────────────

export async function renewMembership(
  memberId: string,
  membershipId: string,
  request: RenewMembershipRequest,
): Promise<Membership> {
  const { data } = await apiClient.post<Membership>(
    `/members/${memberId}/memberships/${membershipId}/renew`,
    request,
  )
  return data
}

export async function freezeMembership(
  memberId: string,
  membershipId: string,
  request: FreezeMembershipRequest,
): Promise<Membership> {
  const { data } = await apiClient.post<Membership>(
    `/members/${memberId}/memberships/${membershipId}/freeze`,
    request,
  )
  return data
}

export async function unfreezeMembership(
  memberId: string,
  membershipId: string,
  request?: UnfreezeMembershipRequest,
): Promise<Membership> {
  const { data } = await apiClient.post<Membership>(
    `/members/${memberId}/memberships/${membershipId}/unfreeze`,
    request ?? {},
  )
  return data
}

export async function terminateMembership(
  memberId: string,
  membershipId: string,
  request: TerminateMembershipRequest,
): Promise<Membership> {
  const { data } = await apiClient.post<Membership>(
    `/members/${memberId}/memberships/${membershipId}/terminate`,
    request,
  )
  return data
}

export interface ExpiringMembershipsParams {
  days?: number
  page?: number
  size?: number
}

export async function getExpiringMemberships(
  params: ExpiringMembershipsParams = {},
): Promise<PageResponse<ExpiringMembership>> {
  const { data } = await apiClient.get<PageResponse<ExpiringMembership>>(
    '/memberships/expiring',
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
  expiring: (params: ExpiringMembershipsParams) =>
    [...membershipKeys.all, 'expiring', params] as const,
}
