import { apiClient } from './client'

export interface MemberImportAcceptedResponse {
  jobId: string
  status: string
  fileName: string
  message: string
}

export interface MemberImportJobResponse {
  jobId: string
  status: string
  fileName: string
  totalRows: number | null
  importedCount: number | null
  skippedCount: number | null
  errorCount: number | null
  errorDetail: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export async function uploadMemberCsv(
  clubPublicId: string,
  file: File,
): Promise<MemberImportAcceptedResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await apiClient.post<MemberImportAcceptedResponse>(
    `/nexus/clubs/${clubPublicId}/members/import`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  return data
}

export async function getImportJob(
  jobPublicId: string,
): Promise<MemberImportJobResponse> {
  const { data } = await apiClient.get<MemberImportJobResponse>(
    `/nexus/member-import-jobs/${jobPublicId}`,
  )
  return data
}

export async function cancelImportJob(
  jobPublicId: string,
): Promise<{ message: string }> {
  const { data } = await apiClient.delete<{ message: string }>(
    `/nexus/member-import-jobs/${jobPublicId}`,
  )
  return data
}

export async function rollbackImportJob(
  jobPublicId: string,
): Promise<{ message: string }> {
  const { data } = await apiClient.post<{ message: string }>(
    `/nexus/member-import-jobs/${jobPublicId}/rollback`,
  )
  return data
}
