import { apiClient } from './client'

export interface InitiatePaymentRequest {
  membershipPublicId: string
}

export interface InitiatePaymentResponse {
  transactionId: string
  hostedUrl: string
  amountSar: string
  planName: string
}

export interface PaymentStatusResponse {
  moyasarId: string
  status: string
  paymentMethod: string | null
  amountSar: string
  paidAt: string | null
}

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

export async function initiatePayment(
  request: InitiatePaymentRequest,
): Promise<InitiatePaymentResponse> {
  const { data } = await apiClient.post<InitiatePaymentResponse>(
    '/arena/payments/initiate',
    request,
  )
  return data
}

export async function getPaymentStatus(
  moyasarId: string,
): Promise<PaymentStatusResponse> {
  const { data } = await apiClient.get<PaymentStatusResponse>(
    `/arena/payments/${moyasarId}/status`,
  )
  return data
}

export async function getPaymentHistory(): Promise<TransactionHistoryResponse> {
  const { data } = await apiClient.get<TransactionHistoryResponse>(
    '/arena/payments/history',
  )
  return data
}

export const paymentKeys = {
  all: ['online-payments'] as const,
  status: (moyasarId: string) =>
    [...paymentKeys.all, 'status', moyasarId] as const,
  history: () => [...paymentKeys.all, 'history'] as const,
}
