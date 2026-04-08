import { apiClient } from './client'
import type { PlatformStats } from '@/types/domain'

export async function getStats(): Promise<PlatformStats> {
  const { data } = await apiClient.get<PlatformStats>('/nexus/stats')
  return data
}
