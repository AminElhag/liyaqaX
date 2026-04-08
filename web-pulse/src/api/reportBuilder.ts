import { apiClient } from './client'

// ── Types ────────────────────────────────────────────────────

export interface MetricMeta {
  code: string
  label: string
  labelAr: string
  unit: string
  scope: string
  description: string
}

export interface DimensionMeta {
  code: string
  label: string
  labelAr: string
  compatibleMetricScopes: string[]
}

export interface FilterMeta {
  code: string
  label: string
  labelAr: string
}

export interface ReportTemplateResponse {
  id: string
  name: string
  description: string | null
  metrics: string[]
  dimensions: string[]
  filters: Record<string, string | null> | null
  metricScope: string | null
  isSystem: boolean
  lastRunAt: string | null
  createdAt: string
}

export interface CreateReportTemplateRequest {
  name: string
  description?: string
  metrics: string[]
  dimensions: string[]
  filters?: Record<string, string | null>
}

export interface UpdateReportTemplateRequest {
  name?: string
  description?: string
  metrics?: string[]
  dimensions?: string[]
  filters?: Record<string, string | null>
}

export interface RunReportRequest {
  dateFrom: string
  dateTo: string
  filters?: Record<string, string | null>
}

export interface ReportResultResponse {
  templateId: string
  runAt: string
  dateFrom: string
  dateTo: string
  columns: string[]
  rows: Record<string, unknown>[]
  rowCount: number
  truncated: boolean
  fromCache: boolean
}

// ── API functions ────────────────────────────────────────────

export async function getMetrics(): Promise<MetricMeta[]> {
  const { data } = await apiClient.get<MetricMeta[]>('/reports/meta/metrics')
  return data
}

export async function getDimensions(): Promise<DimensionMeta[]> {
  const { data } = await apiClient.get<DimensionMeta[]>('/reports/meta/dimensions')
  return data
}

export async function getFilters(): Promise<FilterMeta[]> {
  const { data } = await apiClient.get<FilterMeta[]>('/reports/meta/filters')
  return data
}

export async function listTemplates(): Promise<ReportTemplateResponse[]> {
  const { data } = await apiClient.get<ReportTemplateResponse[]>('/report-templates')
  return data
}

export async function getTemplate(id: string): Promise<ReportTemplateResponse> {
  const { data } = await apiClient.get<ReportTemplateResponse>(`/report-templates/${id}`)
  return data
}

export async function createTemplate(
  request: CreateReportTemplateRequest,
): Promise<ReportTemplateResponse> {
  const { data } = await apiClient.post<ReportTemplateResponse>('/report-templates', request)
  return data
}

export async function updateTemplate(
  id: string,
  request: UpdateReportTemplateRequest,
): Promise<ReportTemplateResponse> {
  const { data } = await apiClient.patch<ReportTemplateResponse>(
    `/report-templates/${id}`,
    request,
  )
  return data
}

export async function deleteTemplate(id: string): Promise<void> {
  await apiClient.delete(`/report-templates/${id}`)
}

export async function runReport(
  id: string,
  request: RunReportRequest,
): Promise<ReportResultResponse> {
  const { data } = await apiClient.post<ReportResultResponse>(
    `/report-templates/${id}/run`,
    request,
  )
  return data
}

export async function getLastResult(id: string): Promise<ReportResultResponse> {
  const { data } = await apiClient.get<ReportResultResponse>(`/report-templates/${id}/result`)
  return data
}

export function exportCsvUrl(id: string): string {
  const baseUrl = apiClient.defaults.baseURL || '/api/v1'
  return `${baseUrl}/report-templates/${id}/export`
}

// ── Query keys ───────────────────────────────────────────────

export const reportBuilderKeys = {
  all: ['report-builder'] as const,
  meta: () => [...reportBuilderKeys.all, 'meta'] as const,
  metrics: () => [...reportBuilderKeys.meta(), 'metrics'] as const,
  dimensions: () => [...reportBuilderKeys.meta(), 'dimensions'] as const,
  filters: () => [...reportBuilderKeys.meta(), 'filters'] as const,
  templates: () => [...reportBuilderKeys.all, 'templates'] as const,
  template: (id: string) => [...reportBuilderKeys.templates(), id] as const,
  result: (id: string) => [...reportBuilderKeys.all, 'result', id] as const,
}
