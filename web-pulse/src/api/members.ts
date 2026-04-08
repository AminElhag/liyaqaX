import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type {
  MemberSummary,
  Member,
  CreateMemberRequest,
  UpdateMemberRequest,
  EmergencyContact,
  CreateEmergencyContactRequest,
  WaiverStatus,
} from '@/types/domain'

export interface MemberListParams {
  page?: number
  size?: number
  sort?: string
  order?: 'asc' | 'desc'
}

export async function getMemberList(
  params: MemberListParams = {},
): Promise<PageResponse<MemberSummary>> {
  const { data } = await apiClient.get<PageResponse<MemberSummary>>(
    '/members',
    { params },
  )
  return data
}

export async function getMember(id: string): Promise<Member> {
  const { data } = await apiClient.get<Member>(`/members/${id}`)
  return data
}

export async function createMember(
  request: CreateMemberRequest,
): Promise<Member> {
  const { data } = await apiClient.post<Member>('/members', request)
  return data
}

export async function updateMember(
  id: string,
  request: UpdateMemberRequest,
): Promise<Member> {
  const { data } = await apiClient.patch<Member>(`/members/${id}`, request)
  return data
}

export async function deleteMember(id: string): Promise<void> {
  await apiClient.delete(`/members/${id}`)
}

// ── Emergency contacts ───────────────────────────────────────────────────────

export async function getEmergencyContacts(
  memberId: string,
): Promise<EmergencyContact[]> {
  const { data } = await apiClient.get<EmergencyContact[]>(
    `/members/${memberId}/emergency-contacts`,
  )
  return data
}

export async function addEmergencyContact(
  memberId: string,
  request: CreateEmergencyContactRequest,
): Promise<EmergencyContact> {
  const { data } = await apiClient.post<EmergencyContact>(
    `/members/${memberId}/emergency-contacts`,
    request,
  )
  return data
}

export async function deleteEmergencyContact(
  memberId: string,
  contactId: string,
): Promise<void> {
  await apiClient.delete(`/members/${memberId}/emergency-contacts/${contactId}`)
}

// ── Waiver ───────────────────────────────────────────────────────────────────

export async function getWaiverStatus(
  memberId: string,
): Promise<WaiverStatus> {
  const { data } = await apiClient.get<WaiverStatus>(
    `/members/${memberId}/waiver-status`,
  )
  return data
}

export async function signWaiver(
  memberId: string,
): Promise<WaiverStatus> {
  const { data } = await apiClient.post<WaiverStatus>(
    `/members/${memberId}/waiver-sign`,
  )
  return data
}

// ── Pending members (self-registration) ────────────────────────────────────

export interface PendingMemberIntent {
  planId: string | null
  planNameEn: string | null
  planNameAr: string | null
  planPriceSar: string | null
}

export interface PendingMember {
  id: string
  nameEn: string
  nameAr: string
  phone: string
  email: string | null
  dateOfBirth: string | null
  gender: string | null
  registeredAt: string
  intent: PendingMemberIntent | null
}

export async function getPendingMembers(
  params: { page?: number; size?: number } = {},
): Promise<PageResponse<PendingMember>> {
  const { data } = await apiClient.get<PageResponse<PendingMember>>(
    '/members/pending',
    { params },
  )
  return data
}

export async function activateMember(
  id: string,
  request: { membershipPlanId?: string },
): Promise<void> {
  await apiClient.post(`/members/${id}/activate`, request)
}

export async function rejectMember(
  id: string,
  reason: string,
): Promise<void> {
  await apiClient.post(`/members/${id}/reject`, { reason })
}

// ── Query key factories for TanStack Query ───────────────────────────────────

export const memberKeys = {
  all: ['members'] as const,
  lists: () => [...memberKeys.all, 'list'] as const,
  list: (params: MemberListParams) => [...memberKeys.lists(), params] as const,
  pending: (params: { page?: number; size?: number }) =>
    [...memberKeys.all, 'pending', params] as const,
  pendingCount: () => [...memberKeys.all, 'pending-count'] as const,
  details: () => [...memberKeys.all, 'detail'] as const,
  detail: (id: string) => [...memberKeys.details(), id] as const,
  emergencyContacts: (memberId: string) =>
    [...memberKeys.detail(memberId), 'emergency-contacts'] as const,
  waiverStatus: (memberId: string) =>
    [...memberKeys.detail(memberId), 'waiver-status'] as const,
}
