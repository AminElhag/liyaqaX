import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type { OrgListItem, OrgDetail } from '@/types/domain'

export async function listOrgs(
  q?: string,
  page: number = 0,
): Promise<PageResponse<OrgListItem>> {
  const { data } = await apiClient.get<PageResponse<OrgListItem>>(
    '/nexus/organizations',
    { params: { q, page } },
  )
  return data
}

export async function getOrg(id: string): Promise<OrgDetail> {
  const { data } = await apiClient.get<OrgDetail>(
    `/nexus/organizations/${id}`,
  )
  return data
}

export async function createOrg(payload: {
  nameEn: string
  nameAr: string
  vatNumber?: string
}): Promise<OrgDetail> {
  const { data } = await apiClient.post<OrgDetail>(
    '/nexus/organizations',
    payload,
  )
  return data
}

export async function updateOrg(
  id: string,
  payload: { nameEn: string; nameAr: string; vatNumber?: string },
): Promise<OrgDetail> {
  const { data } = await apiClient.put<OrgDetail>(
    `/nexus/organizations/${id}`,
    payload,
  )
  return data
}
