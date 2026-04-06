import { apiClient } from './client'
import type { AuditLogPage } from '@/types/domain'

export async function getAuditLog(params: {
  page?: number
  size?: number
}): Promise<AuditLogPage> {
  const { data } = await apiClient.get<AuditLogPage>('/nexus/audit', {
    params,
  })
  return data
}
