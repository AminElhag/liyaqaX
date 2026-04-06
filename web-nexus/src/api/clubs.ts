import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type { ClubListItem, ClubDetailNexus } from '@/types/domain'

export async function listClubs(
  orgId: string,
  page: number = 0,
): Promise<PageResponse<ClubListItem>> {
  const { data } = await apiClient.get<PageResponse<ClubListItem>>(
    `/nexus/organizations/${orgId}/clubs`,
    { params: { page } },
  )
  return data
}

export async function getClub(
  orgId: string,
  clubId: string,
): Promise<ClubDetailNexus> {
  const { data } = await apiClient.get<ClubDetailNexus>(
    `/nexus/organizations/${orgId}/clubs/${clubId}`,
  )
  return data
}

export async function createClub(
  orgId: string,
  payload: { nameEn: string; nameAr: string },
): Promise<ClubDetailNexus> {
  const { data } = await apiClient.post<ClubDetailNexus>(
    `/nexus/organizations/${orgId}/clubs`,
    payload,
  )
  return data
}

export async function updateClub(
  orgId: string,
  clubId: string,
  payload: { nameEn: string; nameAr: string },
): Promise<ClubDetailNexus> {
  const { data } = await apiClient.put<ClubDetailNexus>(
    `/nexus/organizations/${orgId}/clubs/${clubId}`,
    payload,
  )
  return data
}
