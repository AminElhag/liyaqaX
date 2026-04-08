import { apiClient } from './client'

export interface ZatcaClubStatus {
  status: string
  environment: string | null
  csidExpiresAt: string | null
  onboardingStatus: string
}

export async function getMyClubZatcaStatus(): Promise<ZatcaClubStatus> {
  const { data } = await apiClient.get<ZatcaClubStatus>('/pulse/zatca/status')
  return data
}
