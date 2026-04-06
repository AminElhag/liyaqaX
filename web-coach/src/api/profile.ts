import { apiClient } from './client'
import type { TrainerProfile } from '@/types/domain'

export async function getMe(): Promise<TrainerProfile> {
  const { data } = await apiClient.get<TrainerProfile>('/coach/me')
  return data
}
