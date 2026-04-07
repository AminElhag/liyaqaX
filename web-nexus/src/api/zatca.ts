import { apiClient } from './client'

export interface ZatcaClubStatus {
  status: string
  environment: string | null
  csidExpiresAt: string | null
  onboardingStatus: string
}

export interface ZatcaHealthSummary {
  totalActiveCsids: number
  csidsExpiringSoon: number
  clubsNotOnboarded: number
  invoicesPending: number
  invoicesFailed: number
  invoicesDeadlineAtRisk: number
}

export interface ZatcaFailedInvoice {
  invoicePublicId: string
  invoiceNumber: string | null
  clubName: string
  memberName: string
  amountSar: string
  createdAt: string
  zatcaRetryCount: number
  zatcaLastError: string | null
  zatcaStatus: string
}

export async function listClubsZatcaStatus(): Promise<ZatcaClubStatus[]> {
  const { data } = await apiClient.get<ZatcaClubStatus[]>('/zatca/clubs')
  return data
}

export async function getClubZatcaStatus(
  clubPublicId: string,
): Promise<ZatcaClubStatus> {
  const { data } = await apiClient.get<ZatcaClubStatus>(
    `/zatca/clubs/${clubPublicId}/status`,
  )
  return data
}

export async function onboardClub(
  clubPublicId: string,
  otp: string,
): Promise<{ message: string }> {
  const { data } = await apiClient.post<{ message: string }>(
    `/zatca/clubs/${clubPublicId}/onboard`,
    { otp },
  )
  return data
}

export async function renewClubCsid(
  clubPublicId: string,
  otp: string,
): Promise<{ message: string }> {
  const { data } = await apiClient.post<{ message: string }>(
    `/zatca/clubs/${clubPublicId}/renew`,
    { otp },
  )
  return data
}

export async function getHealthSummary(): Promise<ZatcaHealthSummary> {
  const { data } = await apiClient.get<ZatcaHealthSummary>('/zatca/health')
  return data
}

export async function getFailedInvoices(): Promise<ZatcaFailedInvoice[]> {
  const { data } = await apiClient.get<ZatcaFailedInvoice[]>(
    '/zatca/invoices/failed',
  )
  return data
}

export async function retryInvoice(
  invoicePublicId: string,
): Promise<{ message: string }> {
  const { data } = await apiClient.post<{ message: string }>(
    `/zatca/invoices/${invoicePublicId}/retry`,
  )
  return data
}

export async function retryAllFailedForClub(
  clubPublicId: string,
): Promise<{ message: string }> {
  const { data } = await apiClient.post<{ message: string }>(
    `/zatca/clubs/${clubPublicId}/retry-all`,
  )
  return data
}
