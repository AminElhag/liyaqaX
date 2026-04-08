import { apiClient } from './client'

export interface PortalSettingsResponse {
  gxBookingEnabled: boolean
  ptViewEnabled: boolean
  invoiceViewEnabled: boolean
  onlinePaymentEnabled: boolean
  portalMessage: string | null
  selfRegistrationEnabled: boolean
  logoUrl: string | null
  primaryColorHex: string | null
  secondaryColorHex: string | null
  portalTitle: string | null
}

export interface UpdatePortalSettingsRequest {
  gxBookingEnabled?: boolean
  ptViewEnabled?: boolean
  invoiceViewEnabled?: boolean
  onlinePaymentEnabled?: boolean
  portalMessage?: string
  selfRegistrationEnabled?: boolean
  logoUrl?: string
  primaryColorHex?: string
  secondaryColorHex?: string
  portalTitle?: string
}

export async function getPortalSettings(): Promise<PortalSettingsResponse> {
  const { data } = await apiClient.get<PortalSettingsResponse>('/portal-settings')
  return data
}

export async function updatePortalSettings(
  request: UpdatePortalSettingsRequest,
): Promise<PortalSettingsResponse> {
  const { data } = await apiClient.patch<PortalSettingsResponse>('/portal-settings', request)
  return data
}

export const portalSettingsKeys = {
  all: ['portal-settings'] as const,
  detail: () => [...portalSettingsKeys.all, 'detail'] as const,
}
