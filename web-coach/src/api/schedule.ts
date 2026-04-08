import { apiClient } from './client'
import type { ScheduleItem } from '@/types/domain'

export async function getSchedule(date?: string): Promise<ScheduleItem[]> {
  const params = date ? { date } : {}
  const { data } = await apiClient.get<ScheduleItem[]>('/coach/schedule', { params })
  return data
}
