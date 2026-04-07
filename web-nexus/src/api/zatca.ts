import { apiClient } from './client'

export interface ZatcaClubStatus {
  status: string
  environment: string | null
  csidExpiresAt: string | null
  onboardingStatus: string
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
