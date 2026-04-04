import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type { BranchSummary } from '@/types/domain'

export async function getBranches(
  organizationId: string,
  clubId: string,
): Promise<PageResponse<BranchSummary>> {
  const { data } = await apiClient.get<PageResponse<BranchSummary>>(
    `/organizations/${organizationId}/clubs/${clubId}/branches`,
    { params: { size: 100 } },
  )
  return data
}

export const branchKeys = {
  all: ['branches'] as const,
  list: (orgId: string, clubId: string) =>
    [...branchKeys.all, 'list', orgId, clubId] as const,
}
