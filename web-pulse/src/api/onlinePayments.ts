import { apiClient } from './client'

export interface TransactionHistoryResponse {
  transactions: TransactionItem[]
}

export interface TransactionItem {
  transactionId: string
  moyasarId: string
  planName: string
  amountSar: string
  status: string
  paymentMethod: string | null
  createdAt: string
}

export async function getMemberOnlinePayments(
  memberId: string,
): Promise<TransactionHistoryResponse> {
  const { data } = await apiClient.get<TransactionHistoryResponse>(
    `/pulse/members/${memberId}/online-payments`,
  )
  return data
}

export const onlinePaymentKeys = {
  all: ['online-payments'] as const,
  member: (memberId: string) =>
    [...onlinePaymentKeys.all, 'member', memberId] as const,
}
