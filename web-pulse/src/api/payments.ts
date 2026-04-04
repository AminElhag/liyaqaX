import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type { Payment } from '@/types/domain'

export interface PaymentListParams {
  page?: number
  size?: number
}

export async function getMemberPayments(
  memberId: string,
  params: PaymentListParams = {},
): Promise<PageResponse<Payment>> {
  const { data } = await apiClient.get<PageResponse<Payment>>(
    `/members/${memberId}/payments`,
    { params },
  )
  return data
}

export async function getAllPayments(
  params: PaymentListParams & { branchId?: string } = {},
): Promise<PageResponse<Payment>> {
  const { data } = await apiClient.get<PageResponse<Payment>>(
    '/payments',
    { params },
  )
  return data
}

// ── Query key factories for TanStack Query ──────────────────────────────────

export const paymentKeys = {
  all: ['payments'] as const,
  lists: () => [...paymentKeys.all, 'list'] as const,
  list: (params: PaymentListParams & { branchId?: string }) =>
    [...paymentKeys.lists(), params] as const,
  memberPayments: (memberId: string, params: PaymentListParams) =>
    [...paymentKeys.all, 'member', memberId, params] as const,
}
