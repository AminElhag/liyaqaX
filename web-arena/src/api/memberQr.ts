import { apiClient } from './client'

export interface QrValueResponse {
  qrValue: string
}

export async function getMemberQr(): Promise<QrValueResponse> {
  const { data } = await apiClient.get<QrValueResponse>('/arena/me/qr')
  return data
}

export const memberQrKeys = {
  qr: ['member-qr'] as const,
}
