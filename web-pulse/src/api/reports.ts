import { apiClient } from './client'
import type {
  RevenueReportResponse,
  RetentionReportResponse,
  LeadReportResponse,
  CashDrawerReportResponse,
} from '@/types/domain'

export interface ReportParams {
  from: string
  to: string
  branchId?: string
  groupBy?: 'day' | 'week' | 'month'
}

function buildQuery(params: ReportParams): string {
  const q = new URLSearchParams({ from: params.from, to: params.to })
  if (params.branchId) q.set('branchId', params.branchId)
  if (params.groupBy) q.set('groupBy', params.groupBy)
  return q.toString()
}

export async function getRevenueReport(params: ReportParams): Promise<RevenueReportResponse> {
  const { data } = await apiClient.get<RevenueReportResponse>(`/reports/revenue?${buildQuery(params)}`)
  return data
}

export async function getRetentionReport(params: ReportParams): Promise<RetentionReportResponse> {
  const { data } = await apiClient.get<RetentionReportResponse>(`/reports/retention?${buildQuery(params)}`)
  return data
}

export async function getLeadReport(params: ReportParams): Promise<LeadReportResponse> {
  const { data } = await apiClient.get<LeadReportResponse>(`/reports/leads?${buildQuery(params)}`)
  return data
}

export async function getCashDrawerReport(params: ReportParams): Promise<CashDrawerReportResponse> {
  const { data } = await apiClient.get<CashDrawerReportResponse>(`/reports/cash-drawer?${buildQuery(params)}`)
  return data
}

export function exportReportCsvUrl(type: string, params: ReportParams): string {
  const baseUrl = apiClient.defaults.baseURL || '/api/v1'
  return `${baseUrl}/reports/${type}/export?${buildQuery(params)}`
}

export const reportKeys = {
  all: ['reports'] as const,
  revenue: (params: ReportParams) => [...reportKeys.all, 'revenue', params] as const,
  retention: (params: ReportParams) => [...reportKeys.all, 'retention', params] as const,
  leads: (params: ReportParams) => [...reportKeys.all, 'leads', params] as const,
  cashDrawer: (params: ReportParams) => [...reportKeys.all, 'cash-drawer', params] as const,
}
