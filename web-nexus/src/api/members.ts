import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type { MemberSearchItem, MemberDetailNexus } from '@/types/domain'

export async function searchMembers(
  q: string,
  page: number = 0,
): Promise<PageResponse<MemberSearchItem>> {
  const { data } = await apiClient.get<PageResponse<MemberSearchItem>>(
    '/nexus/members',
    { params: { q, page } },
  )
  return data
}

export async function getMember(id: string): Promise<MemberDetailNexus> {
  const { data } = await apiClient.get<MemberDetailNexus>(
    `/nexus/members/${id}`,
  )
  return data
}
