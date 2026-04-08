import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type { BranchListItem, BranchDetailNexus } from '@/types/domain'

export async function listBranches(
  orgId: string,
  clubId: string,
  page: number = 0,
): Promise<PageResponse<BranchListItem>> {
  const { data } = await apiClient.get<PageResponse<BranchListItem>>(
    `/nexus/organizations/${orgId}/clubs/${clubId}/branches`,
    { params: { page } },
  )
  return data
}

export async function getBranch(
  orgId: string,
  clubId: string,
  branchId: string,
): Promise<BranchDetailNexus> {
  const { data } = await apiClient.get<BranchDetailNexus>(
    `/nexus/organizations/${orgId}/clubs/${clubId}/branches/${branchId}`,
  )
  return data
}
