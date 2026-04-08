import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type { Invoice } from '@/types/domain'

export interface InvoiceListParams {
  page?: number
  size?: number
}

export async function getMemberInvoices(
  memberId: string,
  params: InvoiceListParams = {},
): Promise<PageResponse<Invoice>> {
  const { data } = await apiClient.get<PageResponse<Invoice>>(
    `/members/${memberId}/invoices`,
    { params },
  )
  return data
}

export async function getMemberInvoice(
  memberId: string,
  invoiceId: string,
): Promise<Invoice> {
  const { data } = await apiClient.get<Invoice>(
    `/members/${memberId}/invoices/${invoiceId}`,
  )
  return data
}

export async function getInvoiceQrCode(
  invoiceId: string,
): Promise<string> {
  const { data } = await apiClient.get<{ qrCode: string }>(
    `/invoices/${invoiceId}/qr-code`,
  )
  return data.qrCode
}

// ── Query key factories for TanStack Query ──────────────────────────────────

export const invoiceKeys = {
  all: ['invoices'] as const,
  lists: () => [...invoiceKeys.all, 'list'] as const,
  memberInvoices: (memberId: string, params: InvoiceListParams) =>
    [...invoiceKeys.all, 'member', memberId, params] as const,
  detail: (memberId: string, invoiceId: string) =>
    [...invoiceKeys.all, 'detail', memberId, invoiceId] as const,
}
