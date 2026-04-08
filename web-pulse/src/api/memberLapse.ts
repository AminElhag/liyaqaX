import { apiClient } from './client'

export interface LapsedMember {
  memberPublicId: string
  nameAr: string
  nameEn: string
  phone: string
  lastMembershipPlan: string
  expiredOn: string
  daysSinceLapse: number
  hasOpenFollowUp: boolean
}

export interface LapsedMembersPage {
  total: number
  page: number
  pageSize: number
  members: LapsedMember[]
}

export interface RenewalOfferResult {
  noteId: string
  followUpAt: string
  message: string
}

export interface BulkRenewalOfferResult {
  created: number
  skipped: number
}

export async function getLapsedMembers(
  page = 1,
  pageSize = 20,
): Promise<LapsedMembersPage> {
  const { data } = await apiClient.get<LapsedMembersPage>(
    '/pulse/memberships/lapsed',
    { params: { page, pageSize } },
  )
  return data
}

export async function sendRenewalOffer(
  memberPublicId: string,
): Promise<RenewalOfferResult> {
  const { data } = await apiClient.post<RenewalOfferResult>(
    `/pulse/members/${memberPublicId}/renewal-offer`,
  )
  return data
}

export async function sendBulkRenewalOffers(
  memberPublicIds: string[],
): Promise<BulkRenewalOfferResult> {
  const { data } = await apiClient.post<BulkRenewalOfferResult>(
    '/pulse/memberships/lapsed/renewal-offer-bulk',
    { memberPublicIds },
  )
  return data
}

export const lapsedKeys = {
  all: ['lapsed'] as const,
  list: (page: number) => [...lapsedKeys.all, 'list', page] as const,
  count: () => [...lapsedKeys.all, 'count'] as const,
}
