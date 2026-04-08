import { apiClient } from './client'
import type { PtSession } from '@/types/domain'

export async function getPtSessions(status: string, page = 0, size = 20): Promise<PtSession[]> {
  const { data } = await apiClient.get<PtSession[]>('/coach/pt/sessions', {
    params: { status, page, size },
  })
  return data
}

export async function markPtAttendance(sessionId: string, status: 'attended' | 'missed'): Promise<PtSession> {
  const { data } = await apiClient.patch<PtSession>(`/coach/pt/sessions/${sessionId}/attendance`, { status })
  return data
}
