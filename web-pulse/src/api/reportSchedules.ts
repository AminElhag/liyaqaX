import { apiClient } from './client'

// ── Types ────────────────────────────────────────────────────

export interface ReportScheduleResponse {
  id: string
  templateId: string
  templateName: string
  frequency: 'daily' | 'weekly' | 'monthly'
  recipients: string[]
  isActive: boolean
  lastRunAt: string | null
  lastRunStatus: 'success' | 'failed' | null
  lastError: string | null
  nextRunAt: string
  createdAt: string
}

export interface CreateReportScheduleRequest {
  frequency: string
  recipients: string[]
  isActive?: boolean
}

export interface UpdateReportScheduleRequest {
  frequency?: string
  recipients?: string[]
  isActive?: boolean
}

// ── API functions ────────────────────────────────────────────

export async function getSchedule(templateId: string): Promise<ReportScheduleResponse> {
  const { data } = await apiClient.get<ReportScheduleResponse>(
    `/report-templates/${templateId}/schedule`,
  )
  return data
}

export async function createSchedule(
  templateId: string,
  request: CreateReportScheduleRequest,
): Promise<ReportScheduleResponse> {
  const { data } = await apiClient.post<ReportScheduleResponse>(
    `/report-templates/${templateId}/schedule`,
    request,
  )
  return data
}

export async function updateSchedule(
  templateId: string,
  request: UpdateReportScheduleRequest,
): Promise<ReportScheduleResponse> {
  const { data } = await apiClient.patch<ReportScheduleResponse>(
    `/report-templates/${templateId}/schedule`,
    request,
  )
  return data
}

export async function deleteSchedule(templateId: string): Promise<void> {
  await apiClient.delete(`/report-templates/${templateId}/schedule`)
}

export function exportPdfUrl(templateId: string): string {
  const baseUrl = apiClient.defaults.baseURL || '/api/v1'
  return `${baseUrl}/report-templates/${templateId}/export/pdf`
}

// ── Query keys ───────────────────────────────────────────────

export const reportScheduleKeys = {
  all: ['report-schedule'] as const,
  schedule: (templateId: string) => [...reportScheduleKeys.all, templateId] as const,
}
